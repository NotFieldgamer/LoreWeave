package com.loreweave.world;

import com.loreweave.world.repo.EntityRepository;
import com.loreweave.world.repo.FactRepository;
import com.loreweave.world.repo.SessionRepository;
import com.loreweave.world.repo.TurnRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The memory layer. Two jobs:
 *  1. RETRIEVAL (RAG): given the player's action, return only the relevant ACTIVE facts.
 *  2. WRITE: upsert entities/facts/relationships produced by a turn.
 *
 * This is where the "never forgets" claim is made to *scale* — we retrieve, we don't dump.
 */
@Service
public class WorldStateService {

    private final Neo4jClient neo4j;
    private final SessionRepository sessions;
    private final EntityRepository entities;
    private final FactRepository facts;
    private final TurnRepository turns;

    @Value("${loreweave.retrieval.max-facts:24}")
    private int maxFacts;

    public WorldStateService(Neo4jClient neo4j, SessionRepository sessions,
                             EntityRepository entities, FactRepository facts, TurnRepository turns) {
        this.neo4j = neo4j; this.sessions = sessions; this.entities = entities; this.facts = facts; this.turns = turns;
    }

    /**
     * Retrieval strategy (ARCHITECTURE.md §4):
     * full-text match the player's action against entity names/summaries → gather ACTIVE facts
     * about those entities + a 1-hop expansion → rank by recency × confidence → cap to maxFacts.
     */
    public List<String> relevantFacts(String sessionId, String playerAction) {
        String q = sanitize(playerAction);
        List<String> found = q.isBlank() ? List.of() : queryRelevant(sessionId, q);
        // Cold start / no textual entity match → give the GM the newest facts so it's never blind.
        return found.isEmpty() ? newestFacts(sessionId) : found;
    }

    private List<String> queryRelevant(String sessionId, String q) {
        String cypher = """
            CALL db.index.fulltext.queryNodes('entityText', $q) YIELD node, score
            WHERE node.sessionId = $sid
            WITH collect(node) AS seeds
            UNWIND seeds AS s
            MATCH (s)<-[:ABOUT]-(f:Fact {sessionId:$sid, status:'ACTIVE'})
            OPTIONAL MATCH (s)--(nbr:Entity {sessionId:$sid})<-[:ABOUT]-(nf:Fact {status:'ACTIVE'})
            WITH collect(DISTINCT f) + collect(DISTINCT nf) AS fs
            UNWIND fs AS fact
            WITH DISTINCT fact
            ORDER BY fact.turn DESC, fact.confidence DESC
            RETURN fact.text AS text
            LIMIT $limit
            """;
        try {
            return neo4j.query(cypher)
                .bindAll(Map.of("q", q, "sid", sessionId, "limit", maxFacts))
                .fetchAs(String.class).mappedBy((ts, rec) -> rec.get("text").asString())
                .all().stream().toList();
        } catch (RuntimeException e) {
            return List.of();   // e.g. a Lucene syntax edge case → fall back to newest facts
        }
    }

    /** Fallback / cold-start retrieval: the newest ACTIVE facts, capped to the token budget. */
    private List<String> newestFacts(String sessionId) {
        return neo4j.query("""
                MATCH (f:Fact {sessionId:$sid, status:'ACTIVE'})
                RETURN f.text AS text ORDER BY f.turn DESC, f.confidence DESC LIMIT $limit
                """)
            .bindAll(Map.of("sid", sessionId, "limit", maxFacts))
            .fetchAs(String.class).mappedBy((ts, rec) -> rec.get("text").asString())
            .all().stream().toList();
    }

    /** Lucene full-text is picky about symbols — keep only word characters; blank means "no query". */
    private String sanitize(String s) {
        return s == null ? "" : s.replaceAll("[^\\p{L}\\p{N} ]", " ").trim().replaceAll("\\s+", " ");
    }

    // ─────────────────────────────── WRITE (M2): seed a new world ───────────────────────────────

    /** LLM-shaped seed inputs (deserialized straight from the opening-scene JSON). */
    public record SeedEntity(String name, String type, String summary) {}
    public record SeedFact(String text, List<String> about, Double confidence) {}
    public record SeedRelation(String from, String type, String to) {}

    /** Directed entity→entity relationship types allowed in the graph (whitelist — no dynamic injection). */
    private static final Set<String> REL_TYPES =
        Set.of("KNOWS", "ALLY_OF", "ENEMY_OF", "LOCATED_IN", "OWNS", "MEMBER_OF");

    /**
     * Persist the initial world of a session: Entity + Fact nodes, and HAS_ENTITY / ABOUT / entity-entity
     * edges. The Session node must already exist. Names in facts/relations are resolved to entity ids;
     * dangling references are skipped so a slightly-off LLM response never breaks creation.
     */
    public void seedWorld(String sessionId, List<SeedEntity> seedEntities,
                          List<SeedFact> seedFacts, List<SeedRelation> seedRelations) {
        Map<String, String> nameToId = new HashMap<>();
        if (seedEntities != null) for (SeedEntity e : seedEntities) {
            if (e == null || e.name() == null || e.name().isBlank()) continue;
            String id = UUID.randomUUID().toString();
            entities.save(new WorldEntity(id, sessionId, e.name(), parseType(e.type()), e.summary(), 0));
            nameToId.put(e.name(), id);
            neo4j.query("MATCH (s:Session {id:$sid}), (e:Entity {id:$eid}) MERGE (s)-[:HAS_ENTITY]->(e)")
                 .bindAll(Map.of("sid", sessionId, "eid", id)).run();
        }
        if (seedFacts != null) for (SeedFact f : seedFacts) {
            if (f == null || f.text() == null || f.text().isBlank()) continue;
            String id = UUID.randomUUID().toString();
            double conf = f.confidence() == null ? 0.9 : f.confidence();
            facts.save(new Fact(id, sessionId, f.text(), 0, conf, "ACTIVE"));
            if (f.about() != null) for (String name : f.about()) {
                String eid = nameToId.get(name);
                if (eid != null) neo4j.query("MATCH (f:Fact {id:$fid}), (e:Entity {id:$eid}) MERGE (f)-[:ABOUT]->(e)")
                     .bindAll(Map.of("fid", id, "eid", eid)).run();
            }
        }
        if (seedRelations != null) for (SeedRelation r : seedRelations) {
            if (r == null) continue;
            String from = nameToId.get(r.from()), to = nameToId.get(r.to());
            String type = r.type() == null ? "" : r.type().trim().toUpperCase();
            if (from == null || to == null || from.equals(to) || !REL_TYPES.contains(type)) continue;
            neo4j.query("MATCH (a:Entity {id:$from}), (b:Entity {id:$to}) MERGE (a)-[:" + type + " {turn:0}]->(b)")
                 .bindAll(Map.of("from", from, "to", to)).run();
        }
    }

    private EntityType parseType(String t) {
        try { return EntityType.valueOf(t == null ? "CHARACTER" : t.trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { return EntityType.CHARACTER; }
    }

    // ─────────────────────── WRITE (M3): apply a turn's extracted delta ───────────────────────

    /**
     * Upsert the entities/facts/relationships a turn produced. Entities are MERGEd by (session, name)
     * so the graph GROWS without duplicating people/places already known. Returns the new fact texts
     * (for the SSE delta); turnIndex tags new facts so retrieval can rank by recency.
     */
    public List<String> applyDelta(String sessionId, int turnIndex, List<SeedEntity> newEntities,
                                   List<SeedFact> newFacts, List<SeedRelation> newRelations) {
        Map<String, String> nameToId = new HashMap<>();
        for (WorldEntity e : entities.findBySessionId(sessionId)) nameToId.put(e.getName(), e.getId());

        if (newEntities != null) for (SeedEntity e : newEntities) {
            if (e == null || e.name() == null || e.name().isBlank()) continue;
            String existing = nameToId.get(e.name());
            String id = existing != null ? existing : UUID.randomUUID().toString();
            Map<String, Object> p = new HashMap<>();
            p.put("id", id); p.put("sid", sessionId); p.put("name", e.name());
            p.put("type", parseType(e.type()).name());
            p.put("summary", e.summary() == null ? "" : e.summary());
            p.put("turn", turnIndex);
            neo4j.query("""
                MATCH (s:Session {id:$sid})
                MERGE (e:Entity {sessionId:$sid, name:$name})
                  ON CREATE SET e.id=$id, e.type=$type, e.summary=$summary, e.firstSeenTurn=$turn
                  ON MATCH  SET e.summary = CASE WHEN $summary <> '' THEN $summary ELSE e.summary END
                MERGE (s)-[:HAS_ENTITY]->(e)
                """).bindAll(p).run();
            nameToId.putIfAbsent(e.name(), id);
        }

        List<String> added = new ArrayList<>();
        if (newFacts != null) for (SeedFact f : newFacts) {
            if (f == null || f.text() == null || f.text().isBlank()) continue;
            String id = UUID.randomUUID().toString();
            double conf = f.confidence() == null ? 0.8 : f.confidence();
            facts.save(new Fact(id, sessionId, f.text(), turnIndex, conf, "ACTIVE"));
            added.add(f.text());
            if (f.about() != null) for (String name : f.about()) {
                String eid = nameToId.get(name);
                if (eid != null) neo4j.query("MATCH (f:Fact {id:$fid}), (e:Entity {id:$eid}) MERGE (f)-[:ABOUT]->(e)")
                     .bindAll(Map.of("fid", id, "eid", eid)).run();
            }
        }

        if (newRelations != null) for (SeedRelation r : newRelations) {
            if (r == null) continue;
            String from = nameToId.get(r.from()), to = nameToId.get(r.to());
            String type = r.type() == null ? "" : r.type().trim().toUpperCase();
            if (from == null || to == null || from.equals(to) || !REL_TYPES.contains(type)) continue;
            Map<String, Object> p = new HashMap<>();
            p.put("from", from); p.put("to", to); p.put("turn", turnIndex);
            neo4j.query("MATCH (a:Entity {id:$from}), (b:Entity {id:$to}) MERGE (a)-[rel:" + type + "]->(b) ON CREATE SET rel.turn=$turn")
                 .bindAll(p).run();
        }
        return added;
    }

    /** Record a played turn (Turn node + HAS_TURN edge) and advance the session's turn counter. */
    public void commitTurn(String sessionId, int index, String action, String narration) {
        String id = UUID.randomUUID().toString();
        turns.save(new Turn(id, sessionId, index, action, narration));
        neo4j.query("MATCH (s:Session {id:$sid}), (t:Turn {id:$tid}) MERGE (s)-[:HAS_TURN]->(t)")
             .bindAll(Map.of("sid", sessionId, "tid", id)).run();
        sessions.findById(sessionId).ifPresent(s -> { s.setTurnCount(index); sessions.save(s); });
    }

    /**
     * Reconcile a contradiction the guard found (M4): the newer narration wins, so mark the stale
     * ACTIVE fact SUPERSEDED and record {@code (new)-[:SUPERSEDES]->(old)}. History is kept, not
     * deleted — the world "never forgets" what *was* true, it just stops treating it as current.
     * Returns the superseded fact's text if a matching pair was found (else empty — a no-op).
     */
    public Optional<String> supersede(String sessionId, String newFactText, String oldFactText) {
        return neo4j.query("""
                MATCH (nw:Fact {sessionId:$sid}) WHERE nw.text = $newText
                MATCH (od:Fact {sessionId:$sid, status:'ACTIVE'}) WHERE od.text = $oldText AND od.id <> nw.id
                WITH nw, od LIMIT 1
                SET od.status = 'SUPERSEDED'
                MERGE (nw)-[:SUPERSEDES]->(od)
                RETURN od.text AS text
                """)
            .bindAll(Map.of("sid", sessionId, "newText", newFactText, "oldText", oldFactText))
            .fetchAs(String.class).mappedBy((ts, rec) -> rec.get("text").asString())
            .first();
    }

    /** "Story so far": the opening scene + the last {@code maxTurns} turns, for local continuity. */
    public String recentStory(String sessionId, String openingScene, int maxTurns) {
        StringBuilder sb = new StringBuilder();
        if (openingScene != null && !openingScene.isBlank()) sb.append(openingScene).append("\n\n");
        List<Turn> all = turns.findBySessionIdOrderByIndexAsc(sessionId);
        for (int i = Math.max(0, all.size() - maxTurns); i < all.size(); i++) {
            Turn t = all.get(i);
            sb.append("> ").append(t.getAction()).append("\n").append(t.getNarration()).append("\n\n");
        }
        return sb.toString().trim();
    }

    // ─────────────────────────────── READ (M2): full world state ───────────────────────────────

    public record Relation(String from, String type, String to) {}

    public List<WorldEntity> entitiesOf(String sessionId) { return entities.findBySessionId(sessionId); }

    /** Every played turn, in order — used to restore the transcript when the play screen reloads. */
    public List<Turn> turnsOf(String sessionId) { return turns.findBySessionIdOrderByIndexAsc(sessionId); }

    public List<Fact> activeFactsOf(String sessionId) {
        return facts.findBySessionIdAndStatus(sessionId, "ACTIVE");
    }

    // ─────────────────────────────── READ (M5): dashboard aggregates ───────────────────────────────

    /** Per-adventure counts for the dashboard, newest first (one aggregate query, no N+1). */
    public record AdventureCount(String id, String title, String genre,
                                 int turnCount, int entityCount, int factCount) {}

    /** A fact for the "live world monitor" feed, tagged with its adventure title. */
    public record RecentFact(String sessionId, String title, String text, int turn) {}

    public List<AdventureCount> adventureCountsOf(String ownerId) {
        return neo4j.query("""
                MATCH (s:Session {ownerId:$owner})
                RETURN s.id AS id, s.title AS title, s.genre AS genre, s.turnCount AS turnCount,
                       COUNT { (e:Entity {sessionId: s.id}) } AS entityCount,
                       COUNT { (f:Fact {sessionId: s.id, status: 'ACTIVE'}) } AS factCount
                ORDER BY s.createdAt DESC
                """)
            .bindAll(Map.of("owner", ownerId))
            .fetchAs(AdventureCount.class)
            .mappedBy((ts, rec) -> new AdventureCount(
                rec.get("id").asString(), rec.get("title").asString(),
                rec.get("genre").asString(null), rec.get("turnCount").asInt(0),
                rec.get("entityCount").asInt(0), rec.get("factCount").asInt(0)))
            .all().stream().toList();
    }

    /**
     * The owner's newest ACTIVE facts across all their adventures — the live activity feed. Ordered by
     * createdAt (a global clock), because {@code turn} is a per-adventure counter and would let an old,
     * high-turn adventure crowd out a brand-new one. {@code IS NOT NULL DESC} keeps any legacy facts
     * without a timestamp from sorting to the top.
     */
    public List<RecentFact> recentFactsOf(String ownerId, int limit) {
        return neo4j.query("""
                MATCH (s:Session {ownerId:$owner})
                MATCH (f:Fact {sessionId: s.id, status: 'ACTIVE'})
                RETURN f.text AS text, f.turn AS turn, s.title AS title, s.id AS sessionId
                ORDER BY f.createdAt IS NOT NULL DESC, f.createdAt DESC, f.turn DESC
                LIMIT $limit
                """)
            .bindAll(Map.of("owner", ownerId, "limit", limit))
            .fetchAs(RecentFact.class)
            .mappedBy((ts, rec) -> new RecentFact(
                rec.get("sessionId").asString(), rec.get("title").asString(null),
                rec.get("text").asString(), rec.get("turn").asInt(0)))
            .all().stream().toList();
    }

    public List<Relation> relationsOf(String sessionId) {
        return neo4j.query("""
                MATCH (a:Entity {sessionId:$sid})-[r]->(b:Entity {sessionId:$sid})
                WHERE type(r) IN ['KNOWS','ALLY_OF','ENEMY_OF','LOCATED_IN','OWNS','MEMBER_OF']
                RETURN a.name AS from, type(r) AS type, b.name AS to
                """)
            .bindAll(Map.of("sid", sessionId))
            .fetchAs(Relation.class)
            .mappedBy((ts, rec) -> new Relation(rec.get("from").asString(), rec.get("type").asString(), rec.get("to").asString()))
            .all().stream().toList();
    }
}
