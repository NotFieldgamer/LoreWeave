package com.loreweave.llm.eval;

import com.loreweave.llm.ContradictionChecker;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The honest-ML angle (ARCHITECTURE.md §5, Golden rule #4): score the contradiction guard on a
 * hand-labeled set of fact pairs and report precision/recall/F1.
 *
 * The set is deliberately TINY (12 pairs) and the run is PACED — the Gemini free tier throttles hard
 * (a small per-minute burst, ~20 requests/day), so we space calls out and stop at a wall-clock
 * deadline rather than burst-and-fail. A pair whose call fails or is skipped by the deadline is
 * reported as UNSCORED, never silently counted as "ok". It's balanced (half genuine contradictions,
 * half compatible) and every pair shares a subject entity, so it exercises the NLI on the hard case.
 */
@Component
public class ContradictionEvaluator {

    /** ~space between model calls, to stay under the free tier's per-minute burst. */
    private static final long DEFAULT_PACE_MS = 8_000L;
    /** hard stop so a throttled run can never hang; unscored pairs are reported honestly. */
    private static final long DEFAULT_DEADLINE_MS = 240_000L;

    public record Pair(String a, String b, boolean contradicts) {}

    /** One pair's outcome: {@code predicted == null} means we couldn't score it (quota/deadline). */
    public record PairResult(Pair pair, Boolean predicted) {
        public boolean scored() { return predicted != null; }
        public boolean miss() { return scored() && predicted != pair.contradicts(); }
    }

    public record Result(ConfusionMatrix matrix, int scored, int unscored, List<PairResult> details) {
        public int total() { return scored + unscored; }
        public String report() {
            return matrix.report()
                + String.format("%n  coverage  = %d/%d pairs scored (%d unscored — quota/deadline)",
                                scored, total(), unscored);
        }
    }

    public static final List<Pair> DATASET = List.of(
        // ── genuine contradictions: A and B cannot both hold at the same moment ──
        new Pair("Kaelen is alive.", "Kaelen is dead.", true),
        new Pair("The vault door is locked tight.", "The vault door hangs wide open.", true),
        new Pair("Sera Vane has piercing green eyes.", "Sera Vane has dull brown eyes.", true),
        new Pair("Doran owes you fifty gold coins.", "Doran owes you nothing at all.", true),
        new Pair("The river Ashen flows east to the sea.", "The river Ashen flows west into the mountains.", true),
        new Pair("Elira trusts you completely.", "Elira has sworn to kill you.", true),

        // ── compatible / neutral: both can be true (same subject, different or supporting claim) ──
        new Pair("Kaelen keeps the Salty Siren tavern.", "Kaelen has a jagged scar across his cheek.", false),
        new Pair("Marek is the captain of the city guard.", "Marek commands the men of the city watch.", false),
        new Pair("The vault holds the royal treasury.", "The vault lies deep beneath the keep.", false),
        new Pair("You carry a silver blade.", "The silver blade was a gift from Kaelen.", false),
        new Pair("Sera Vane owes you a silver debt.", "Sera Vane fled toward the eastern docks.", false),
        new Pair("The river Ashen flows east to the sea.", "Fishing boats crowd the river Ashen at dawn.", false)
    );

    private final ContradictionChecker checker;

    public ContradictionEvaluator(ContradictionChecker checker) { this.checker = checker; }

    public Result evaluate() { return evaluate(DATASET, DEFAULT_PACE_MS, DEFAULT_DEADLINE_MS); }

    /**
     * Run the guard's real decision over every pair, paced to respect the free-tier throttle and
     * bounded by a wall-clock deadline. Pairs we can't score (quota error, or deadline reached) are
     * recorded as unscored — never as "no contradiction".
     */
    public Result evaluate(List<Pair> pairs, long paceMs, long deadlineMs) {
        List<PairResult> details = new ArrayList<>();
        List<Boolean> predicted = new ArrayList<>();
        List<Boolean> gold = new ArrayList<>();
        long start = System.currentTimeMillis();
        boolean deadlineReached = false;

        for (int i = 0; i < pairs.size(); i++) {
            Pair p = pairs.get(i);
            if (deadlineReached || System.currentTimeMillis() - start > deadlineMs) {
                deadlineReached = true;
                details.add(new PairResult(p, null));
                continue;
            }
            if (i > 0 && paceMs > 0) sleep(paceMs);
            try {
                boolean pred = checker.flagsContradiction(p.a(), p.b());
                details.add(new PairResult(p, pred));
                predicted.add(pred);
                gold.add(p.contradicts());
            } catch (RuntimeException e) {
                details.add(new PairResult(p, null));   // e.g. a 429 — cannot honestly score this pair
            }
        }

        ConfusionMatrix matrix = ConfusionMatrix.of(toArray(predicted), toArray(gold));
        return new Result(matrix, predicted.size(), pairs.size() - predicted.size(), details);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static boolean[] toArray(List<Boolean> xs) {
        boolean[] a = new boolean[xs.size()];
        for (int i = 0; i < xs.size(); i++) a[i] = xs.get(i);
        return a;
    }
}
