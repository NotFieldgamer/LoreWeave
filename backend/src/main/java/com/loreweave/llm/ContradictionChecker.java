package com.loreweave.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loreweave.llm.prompt.GameMasterPrompts;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guards world consistency (ARCHITECTURE.md §5). For each candidate new fact, compare against the
 * ACTIVE facts and decide supports | contradicts | neutral. Layered so LLM calls are spent only where
 * they can matter:
 *   1) cheap lexical pre-filter — skip pairs that share too few content words to possibly conflict;
 *   2) NLI-style prompt to the model — {contradicts|supports|neutral} + confidence;
 *   3) keep confident contradictions as Verdicts for the caller to supersede.
 *
 * HONEST METRICS: {@link com.loreweave.llm.eval.ContradictionEvaluator} scores {@link #flagsContradiction}
 * on a hand-labeled fact-pair set and reports precision/recall/F1 — never "accuracy" alone (Golden rule #4).
 */
@Component
public class ContradictionChecker {

    private static final Logger log = LoggerFactory.getLogger(ContradictionChecker.class);

    private final ChatLanguageModel model;
    private final ObjectMapper json;

    /** A "contradicts" label below this confidence is not treated as actionable. */
    @Value("${loreweave.guard.min-confidence:0.5}")
    private double minConfidence;

    /** Jaccard content-word overlap below this means the pair is too unrelated to bother the LLM. */
    @Value("${loreweave.guard.min-overlap:0.12}")
    private double minOverlap;

    /** Hard cap on NLI calls per findConflicts() call, so a fact-heavy turn can't run away on quota. */
    @Value("${loreweave.guard.max-checks:16}")
    private int maxChecks;

    /** Retries on a transient rate-limit (HTTP 429) — the free tier throttles per minute. */
    @Value("${loreweave.guard.max-retries:3}")
    private int maxRetries;

    /** Base backoff for a rate-limit retry; doubles each attempt (capped at 20s). */
    @Value("${loreweave.guard.retry-backoff-ms:10000}")
    private long retryBackoffMs;

    public ContradictionChecker(@Qualifier("guardModel") ChatLanguageModel model, ObjectMapper json) {
        this.model = model;
        this.json = json;
    }

    public record Judgment(String label, double confidence) {
        public boolean contradicts() { return "contradicts".equals(label); }
    }

    public record Verdict(String candidateFact, String conflictsWith, String label, double confidence) {}

    /**
     * NLI-style pairwise check: how does B relate to A? This is the exact path the evaluator scores.
     * THROWS on a model/quota/parse failure rather than pretending "neutral" — a swallowed error would
     * silently corrupt the honest metrics (a rate-limited call would masquerade as "no contradiction").
     * Callers that must not block a turn (the turn loop) catch it and fail open; the evaluator counts
     * it as an unscored pair.
     */
    public Judgment classifyPair(String a, String b) {
        String raw = generateWithRetry(GameMasterPrompts.contradictionMessage(a, b));   // may throw (quota/network)
        try {
            JsonNode n = json.readTree(extractJson(raw));
            String label = n.path("label").asText("neutral").trim().toLowerCase();
            if (!label.equals("contradicts") && !label.equals("supports")) label = "neutral";
            return new Judgment(label, clamp(n.path("confidence").asDouble(0.0)));
        } catch (Exception e) {
            throw new IllegalStateException("Unparseable NLI response: " + truncate(raw), e);
        }
    }

    /** Call the model, backing off and retrying only on a transient rate-limit (429). Other errors fly. */
    private String generateWithRetry(String prompt) {
        long backoff = retryBackoffMs;
        for (int attempt = 0; ; attempt++) {
            try {
                return model.generate(prompt);
            } catch (RuntimeException e) {
                if (attempt >= maxRetries || !isRateLimited(e)) throw e;
                log.debug("Rate-limited (attempt {}/{}); backing off {}ms", attempt + 1, maxRetries, backoff);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                backoff = Math.min(backoff * 2, 20_000);
            }
        }
    }

    /** A 429 / RESOURCE_EXHAUSTED anywhere in the cause chain — the throttle we should wait out. */
    private static boolean isRateLimited(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (m == null) continue;
            String lower = m.toLowerCase();
            if (m.contains("429") || lower.contains("resource_exhausted") || lower.contains("rate limit")) return true;
        }
        return false;
    }

    /** True if A and B are a confident contradiction under the guard's full policy (pre-filter + NLI + threshold). */
    public boolean flagsContradiction(String a, String b) {
        if (!overlaps(a, b)) return false;
        Judgment j = classifyPair(a, b);
        return j.contradicts() && j.confidence() >= minConfidence;
    }

    /**
     * For each candidate new fact, find the pre-existing ACTIVE facts it contradicts. Returns confident
     * contradictions as Verdicts (candidate supersedes the fact it conflicts with).
     */
    public List<Verdict> findConflicts(List<String> candidateFacts, List<String> activeFacts) {
        List<Verdict> verdicts = new ArrayList<>();
        if (candidateFacts == null || activeFacts == null) return verdicts;
        int checks = 0;
        for (String cand : candidateFacts) {
            if (cand == null || cand.isBlank()) continue;
            for (String active : activeFacts) {
                if (active == null || active.isBlank() || active.equals(cand)) continue;
                if (!overlaps(cand, active)) continue;                       // (1) cheap pre-filter
                if (checks >= maxChecks) {
                    log.debug("Contradiction guard hit its {}-check budget; skipping the rest.", maxChecks);
                    return verdicts;
                }
                checks++;
                try {
                    Judgment j = classifyPair(cand, active);                 // (2) NLI
                    if (j.contradicts() && j.confidence() >= minConfidence)  // (3) confident conflict
                        verdicts.add(new Verdict(cand, active, j.label(), j.confidence()));
                } catch (Exception e) {
                    // Fail-open: a checker error (quota, network) must never block or lose a turn.
                    log.warn("NLI check failed for a fact pair (treating as no-conflict): {}", e.toString());
                }
            }
        }
        return verdicts;
    }

    // ─────────────────────────── lexical pre-filter ───────────────────────────
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Set<String> STOP = Set.of(
        "the","a","an","of","to","is","are","was","were","and","or","in","on","at","it","its","you","your",
        "with","for","this","that","has","have","had","he","she","they","his","her","their","them","as","by","not");

    /** Jaccard overlap of content words; true when two facts share enough vocabulary to possibly conflict. */
    boolean overlaps(String a, String b) {
        Set<String> sa = contentWords(a), sb = contentWords(b);
        if (sa.isEmpty() || sb.isEmpty()) return false;
        Set<String> inter = new HashSet<>(sa); inter.retainAll(sb);
        if (inter.isEmpty()) return false;
        Set<String> union = new HashSet<>(sa); union.addAll(sb);
        return (double) inter.size() / union.size() >= minOverlap;
    }

    private static Set<String> contentWords(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) return out;
        Matcher m = WORD.matcher(s.toLowerCase());
        while (m.find()) {
            String w = m.group();
            if (w.length() > 2 && !STOP.contains(w)) out.add(w);
        }
        return out;
    }

    private static double clamp(double c) { return c < 0 ? 0 : (c > 1 ? 1 : c); }

    private static String truncate(String s) {
        if (s == null) return "null";
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }

    /** LLMs sometimes wrap JSON in prose or code fences — take the outermost {...}. */
    private static String extractJson(String s) {
        if (s == null) return "{}";
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        return (a >= 0 && b > a) ? s.substring(a, b + 1) : s;
    }
}
