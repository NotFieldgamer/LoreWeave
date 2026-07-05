package com.loreweave.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loreweave.llm.prompt.GameMasterPrompts;
import com.loreweave.world.Fact;
import com.loreweave.world.Session;
import com.loreweave.world.WorldStateService;
import com.loreweave.world.WorldStateService.SeedEntity;
import com.loreweave.world.WorldStateService.SeedFact;
import com.loreweave.world.WorldStateService.SeedRelation;
import com.loreweave.world.repo.SessionRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * THE TURN LOOP (ARCHITECTURE.md §3): resolve scene → retrieve relevant facts → prompt → stream
 * narration over SSE → extract new facts → persist → return the graph delta. Long-term recall comes
 * from the Fact graph (retrieval), so the GM "never forgets" even beyond the recent-story window.
 *
 * Everything a turn tells the client travels as an SSE event ({@code token} / {@code delta} /
 * {@code done} / {@code error}). We NEVER complete the emitter with an exception: that would ask the
 * container to render a JSON error body onto a {@code text/event-stream} response — for which there
 * is no converter — surfacing as a spurious 500. Client disconnects are swallowed; server-side
 * failures become an {@code error} event the UI can show.
 */
@Service
public class GameMasterService {

    private static final Logger log = LoggerFactory.getLogger(GameMasterService.class);
    private static final int RECENT_TURNS = 6;   // how much prose continuity to feed the prompt

    private final StreamingChatLanguageModel streamingModel;
    private final ChatLanguageModel structuredModel;
    private final WorldStateService world;
    private final ContradictionChecker guard;
    private final SessionRepository sessions;
    private final ObjectMapper json;

    public GameMasterService(StreamingChatLanguageModel streamingModel, ChatLanguageModel structuredModel,
                             WorldStateService world, ContradictionChecker guard,
                             SessionRepository sessions, ObjectMapper json) {
        this.streamingModel = streamingModel; this.structuredModel = structuredModel;
        this.world = world; this.guard = guard; this.sessions = sessions; this.json = json;
    }

    /** The structured extraction shape (reuses the seed records the world layer already understands). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Extract(List<SeedEntity> entities, List<SeedFact> facts, List<SeedRelation> relations) {}

    /**
     * Runs one turn on a background thread (so the SseEmitter streams token-by-token instead of
     * buffering until the controller returns). Tokens are JSON-encoded so newlines/spaces survive
     * the SSE framing and the frontend's line-based parser.
     */
    @Async
    public void streamTurn(String sessionId, String playerAction, SseEmitter emitter) {
        int turnIndex = 0;
        try {
            Session session = sessions.findById(sessionId).orElse(null);
            String opening = session == null ? null : session.getOpeningScene();
            turnIndex = (session == null ? 0 : session.getTurnCount()) + 1;

            // 1–2. RESOLVE scene + RETRIEVE relevant facts (RAG).
            String recentStory = world.recentStory(sessionId, opening, RECENT_TURNS);
            List<String> facts = world.relevantFacts(sessionId, playerAction);

            // 3. PROMPT.
            List<ChatMessage> messages = List.of(
                SystemMessage.from(GameMasterPrompts.SYSTEM),
                UserMessage.from(GameMasterPrompts.turnUserMessage(facts, recentStory, playerAction))
            );

            // 4. GENERATE — stream tokens to the browser as they arrive.
            final int turn = turnIndex;
            streamingModel.generate(messages, new StreamingResponseHandler<>() {
                @Override public void onNext(String token) { send(emitter, "token", token); }
                @Override public void onComplete(Response<AiMessage> response) {
                    finishTurn(sessionId, playerAction, response.content().text(), turn, emitter);
                }
                @Override public void onError(Throwable t) {
                    log.warn("Generation failed for session {} turn {}: {}", sessionId, turn, t.toString());
                    fail(emitter, "The game master lost their train of thought. Try that again.");
                }
            });
        } catch (Exception e) {
            log.warn("Turn {} setup failed for session {}: {}", turnIndex, sessionId, e.toString());
            fail(emitter, "The game master could not begin the turn. Try again.");
        }
    }

    /** 5–8. EXTRACT new world state → GUARD → PERSIST → emit the graph delta, then done. */
    private void finishTurn(String sessionId, String action, String narration, int turnIndex, SseEmitter emitter) {
        List<String> newFacts = List.of();
        List<String> superseded = new ArrayList<>();
        try {
            Extract ex = extract(narration);
            // Snapshot the world's ACTIVE facts BEFORE this turn's are added, so the guard compares
            // the new claims against established canon (not against themselves).
            List<String> priorActive = world.activeFactsOf(sessionId).stream().map(Fact::getText).toList();
            newFacts = world.applyDelta(sessionId, turnIndex, ex.entities(), ex.facts(), ex.relations());

            // 6. GUARD — reconcile contradictions: the newer narration supersedes the stale fact
            //    (history is kept via SUPERSEDES). Best-effort: a guard failure must not lose the turn.
            try {
                List<String> candidates = ex.facts() == null ? List.of()
                    : ex.facts().stream()
                        .filter(f -> f != null && f.text() != null && !f.text().isBlank())
                        .map(SeedFact::text).toList();
                for (ContradictionChecker.Verdict v : guard.findConflicts(candidates, priorActive))
                    world.supersede(sessionId, v.candidateFact(), v.conflictsWith()).ifPresent(superseded::add);
                if (!superseded.isEmpty())
                    log.info("Turn {} guard superseded {} stale fact(s) in session {}", turnIndex, superseded.size(), sessionId);
            } catch (Exception e) {
                log.warn("Turn {} contradiction guard failed for session {}: {}", turnIndex, sessionId, e.toString());
            }
        } catch (Exception e) {
            // A bad extraction must never lose the narration the player already read — log and move on.
            log.warn("Turn {} extraction/persist failed for session {}: {}", turnIndex, sessionId, e.toString());
        }
        try {
            world.commitTurn(sessionId, turnIndex, action, narration);
        } catch (Exception e) {
            // The narration is already on screen; a failed commit shouldn't error the stream.
            log.warn("Turn {} commit failed for session {}: {}", turnIndex, sessionId, e.toString());
        }
        if (send(emitter, "delta", Map.of("turn", turnIndex, "newFacts", newFacts, "superseded", superseded)))
            send(emitter, "done", Map.of());
        quietly(emitter::complete);
    }

    /**
     * Send one SSE event, JSON-encoding the payload so newlines/spaces survive the framing.
     * Returns {@code false} if the client has disconnected (broken pipe) or the stream is already
     * finished — in which case we quietly close and give up. Spring's {@code
     * AsyncRequestNotUsableException} (thrown on a dead connection) extends {@link IOException}, so
     * it is caught here too.
     */
    private boolean send(SseEmitter emitter, String event, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(event).data(json.writeValueAsString(payload)));
            return true;
        } catch (IOException | IllegalStateException e) {
            quietly(emitter::complete);   // client gone or emitter already done — nothing to report
            return false;
        }
    }

    /** Report a server-side failure to the client as an SSE {@code error} event, then close cleanly. */
    private void fail(SseEmitter emitter, String message) {
        send(emitter, "error", Map.of("error", message));
        quietly(emitter::complete);
    }

    private void quietly(Runnable r) { try { r.run(); } catch (RuntimeException ignored) { /* already closed */ } }

    private Extract extract(String narration) throws IOException {
        String raw = structuredModel.generate(GameMasterPrompts.extractionMessage(narration));
        return json.readerFor(Extract.class)
                   .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                   .readValue(extractJson(raw));
    }

    /** LLMs sometimes wrap JSON in prose or code fences — take the outermost {...}. */
    private static String extractJson(String s) {
        if (s == null) return "{}";
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        return (a >= 0 && b > a) ? s.substring(a, b + 1) : s;
    }
}
