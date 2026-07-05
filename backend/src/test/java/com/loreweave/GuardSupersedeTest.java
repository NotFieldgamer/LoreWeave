package com.loreweave;

import com.loreweave.world.Fact;
import com.loreweave.world.Session;
import com.loreweave.world.WorldStateService;
import com.loreweave.world.WorldStateService.SeedEntity;
import com.loreweave.world.WorldStateService.SeedFact;
import com.loreweave.world.repo.SessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guard's *resolution* step, tested deterministically (Neo4j only, no LLM): when a new fact
 * supersedes an old one, the old fact leaves the ACTIVE set but stays in the graph behind a
 * SUPERSEDES edge — consistent world, memory intact ("never forgets what *was* true").
 */
@SpringBootTest
class GuardSupersedeTest {

    @Autowired WorldStateService world;
    @Autowired SessionRepository sessions;
    @Autowired Neo4jClient neo4j;

    @Test
    void newFactSupersedesTheStaleOneWithoutDeletingIt() {
        String sid = "sup-" + UUID.randomUUID();
        String stale = "The gate is locked.";
        String fresh = "The gate stands wide open.";
        try {
            sessions.save(new Session(sid, "owner", "Guard Test", "Test"));

            // Turn 1 establishes the stale fact; turn 2 introduces the contradicting one.
            world.applyDelta(sid, 1,
                List.of(new SeedEntity("Gate", "PLACE", "the iron gate of the keep")),
                List.of(new SeedFact(stale, List.of("Gate"), 0.9)), List.of());
            world.applyDelta(sid, 2,
                List.of(),
                List.of(new SeedFact(fresh, List.of("Gate"), 0.9)), List.of());

            // Both are ACTIVE until the guard reconciles them.
            assertThat(activeTexts(sid)).contains(stale, fresh);

            Optional<String> superseded = world.supersede(sid, fresh, stale);

            assertThat(superseded).as("supersede reports the fact it retired").contains(stale);
            assertThat(activeTexts(sid)).as("stale fact leaves ACTIVE, fresh stays")
                .contains(fresh).doesNotContain(stale);
            assertThat(statusOf(sid, stale)).isEqualTo("SUPERSEDED");     // retained, not deleted
            assertThat(supersedesEdges(sid)).isEqualTo(1L);              // (fresh)-[:SUPERSEDES]->(stale)

            // Idempotent: the stale fact is no longer ACTIVE, so a repeat is a no-op.
            assertThat(world.supersede(sid, fresh, stale)).isEmpty();
        } finally {
            neo4j.query("MATCH (n {sessionId:$sid}) DETACH DELETE n").bindAll(Map.of("sid", sid)).run();
            sessions.deleteById(sid);
        }
    }

    private List<String> activeTexts(String sid) {
        return world.activeFactsOf(sid).stream().map(Fact::getText).toList();
    }

    private String statusOf(String sid, String text) {
        return neo4j.query("MATCH (f:Fact {sessionId:$sid}) WHERE f.text = $t RETURN f.status AS s")
            .bindAll(Map.of("sid", sid, "t", text))
            .fetchAs(String.class).mappedBy((ts, rec) -> rec.get("s").asString()).one().orElse(null);
    }

    private long supersedesEdges(String sid) {
        return neo4j.query("MATCH (:Fact {sessionId:$sid})-[r:SUPERSEDES]->(:Fact {sessionId:$sid}) RETURN count(r) AS c")
            .bindAll(Map.of("sid", sid))
            .fetchAs(Long.class).mappedBy((ts, rec) -> rec.get("c").asLong()).one().orElse(0L);
    }
}
