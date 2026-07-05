package com.loreweave.game;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loreweave.game.dto.Dtos.AdventureStatDto;
import com.loreweave.game.dto.Dtos.DashboardDto;
import com.loreweave.game.dto.Dtos.EntityDto;
import com.loreweave.game.dto.Dtos.FactDto;
import com.loreweave.game.dto.Dtos.RecentFactDto;
import com.loreweave.game.dto.Dtos.RelationDto;
import com.loreweave.game.dto.Dtos.SessionDetailDto;
import com.loreweave.game.dto.Dtos.SessionDto;
import com.loreweave.game.dto.Dtos.TurnDto;
import com.loreweave.game.dto.Dtos.WorldStateDto;
import com.loreweave.llm.prompt.GameMasterPrompts;
import com.loreweave.world.Session;
import com.loreweave.world.WorldStateService;
import com.loreweave.world.WorldStateService.SeedEntity;
import com.loreweave.world.WorldStateService.SeedFact;
import com.loreweave.world.WorldStateService.SeedRelation;
import com.loreweave.world.repo.SessionRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Creates &amp; reads adventures (M2). Creation makes ONE Gemini call to write the opening scene and
 * seed the initial world, then persists Session + entities/facts/relations to the graph and returns
 * a {@link SessionDto}. Sessions are always scoped to the owner id derived from the Clerk JWT.
 */
@Service
public class SessionService {

    private final ChatLanguageModel llm;   // structured (blocking) Gemini model from LlmConfig
    private final WorldStateService world;
    private final SessionRepository sessions;
    private final ObjectMapper json;

    public SessionService(ChatLanguageModel llm, WorldStateService world,
                          SessionRepository sessions, ObjectMapper json) {
        this.llm = llm; this.world = world; this.sessions = sessions; this.json = json;
    }

    /** The exact JSON shape the opening-scene prompt asks Gemini to return. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Opening(String title, String opening,
                           List<SeedEntity> entities, List<SeedFact> facts, List<SeedRelation> relations) {}

    /** POST /api/sessions — write the opening scene, seed the graph, return the new session. */
    public SessionDto create(String ownerId, String genre, String premise) {
        Opening seed = generateOpening(genre, premise);
        String id = UUID.randomUUID().toString();
        String title = (seed.title() == null || seed.title().isBlank()) ? "Untitled adventure" : seed.title().trim();

        Session s = new Session(id, ownerId, title, genre);
        s.setOpeningScene(seed.opening());
        sessions.save(s);                                            // Session node first — HAS_ENTITY edges need it
        world.seedWorld(id, seed.entities(), seed.facts(), seed.relations());

        return new SessionDto(id, title, genre, s.getCreatedAt(), s.getTurnCount());
    }

    /** GET /api/sessions — the signed-in user's adventures, newest first. */
    public List<SessionDto> listForOwner(String ownerId) {
        return sessions.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
            .map(s -> new SessionDto(s.getId(), s.getTitle(), s.getGenre(), s.getCreatedAt(), s.getTurnCount()))
            .toList();
    }

    /** GET /api/sessions/{id} — one adventure: title, opening scene and the story so far. */
    public SessionDetailDto detail(String ownerId, String sessionId) {
        Session s = owned(sessionId, ownerId);
        List<TurnDto> turns = world.turnsOf(sessionId).stream()
            .map(t -> new TurnDto(t.getIndex(), t.getAction(), t.getNarration()))
            .toList();
        return new SessionDetailDto(s.getId(), s.getTitle(), s.getGenre(),
                                    s.getOpeningScene(), s.getTurnCount(), turns);
    }

    /** GET /api/sessions/{id}/world — the full seeded world (ownership-checked). */
    public WorldStateDto worldState(String ownerId, String sessionId) {
        owned(sessionId, ownerId);
        List<EntityDto> entities = world.entitiesOf(sessionId).stream()
            .map(e -> new EntityDto(e.getId(), e.getName(),
                                    e.getType() == null ? null : e.getType().name(), e.getSummary()))
            .toList();
        List<FactDto> facts = world.activeFactsOf(sessionId).stream()
            .map(f -> new FactDto(f.getId(), f.getText(), f.getTurn(), f.getStatus()))
            .toList();
        List<RelationDto> relations = world.relationsOf(sessionId).stream()
            .map(r -> new RelationDto(r.from(), r.type(), r.to()))
            .toList();
        return new WorldStateDto(entities, relations, facts);
    }

    /** GET /api/sessions/stats — the dashboard: real per-adventure counts, totals, and a recent-facts feed. */
    public DashboardDto dashboard(String ownerId) {
        List<AdventureStatDto> adventures = world.adventureCountsOf(ownerId).stream()
            .map(a -> new AdventureStatDto(a.id(), a.title(), a.genre(), a.turnCount(), a.entityCount(), a.factCount()))
            .toList();
        int worlds = adventures.size();
        int turns = adventures.stream().mapToInt(AdventureStatDto::turnCount).sum();
        int entities = adventures.stream().mapToInt(AdventureStatDto::entityCount).sum();
        int facts = adventures.stream().mapToInt(AdventureStatDto::factCount).sum();
        List<RecentFactDto> recent = world.recentFactsOf(ownerId, 12).stream()
            .map(r -> new RecentFactDto(r.sessionId(), r.title(), r.text(), r.turn()))
            .toList();
        return new DashboardDto(worlds, turns, entities, facts, adventures, recent);
    }

    /** Load a session, asserting the caller owns it. 404 (not 403) so we never leak that it exists. */
    public Session owned(String sessionId, String ownerId) {
        Session s = sessions.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such adventure"));
        if (!ownerId.equals(s.getOwnerId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such adventure");
        return s;
    }

    private Opening generateOpening(String genre, String premise) {
        String raw = llm.generate(GameMasterPrompts.openingSceneMessage(genre, premise));
        try {
            return json.readerFor(Opening.class)
                       .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                       .readValue(extractJson(raw));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "The game master returned an unreadable opening — please try again.");
        }
    }

    /** LLMs sometimes wrap JSON in prose or ```code fences``` — take the outermost {...}. */
    private static String extractJson(String s) {
        if (s == null) return "{}";
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        return (a >= 0 && b > a) ? s.substring(a, b + 1) : s;
    }
}
