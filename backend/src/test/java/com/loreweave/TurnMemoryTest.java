package com.loreweave;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The "never forgets" claim, tested deterministically (Neo4j only, no LLM): a fact established on an
 * early turn is still retrieved on a later turn when the action references it — even after other
 * turns pile on. This is retrieval (RAG) doing the remembering, not a long prompt.
 */
@SpringBootTest
class TurnMemoryTest {

    @Autowired WorldStateService world;
    @Autowired SessionRepository sessions;
    @Autowired Neo4jClient neo4j;

    @Test
    void factFromTurnOneIsRecalledAtTurnFive() {
        String sid = "mem-" + UUID.randomUUID();
        try {
            sessions.save(new Session(sid, "owner", "Test Adventure", "Test"));

            // Turn 1 — establish a character and a memorable fact.
            world.applyDelta(sid, 1,
                List.of(new SeedEntity("Kaelen", "CHARACTER", "the stoic keeper of The Salty Siren")),
                List.of(new SeedFact("Kaelen gave you a silver blade wrapped in oilcloth", List.of("Kaelen"), 0.95)),
                List.of());
            world.commitTurn(sid, 1, "enter the tavern and speak with the keeper", "You meet Kaelen behind the bar...");

            // Turns 2-4 — bury it under unrelated activity.
            for (int t = 2; t <= 4; t++) {
                world.applyDelta(sid, t,
                    List.of(new SeedEntity("Dock Guard " + t, "CHARACTER", "a bored guard on the night watch")),
                    List.of(new SeedFact("A dock guard watches the pier on night " + t, List.of("Dock Guard " + t), 0.5)),
                    List.of());
                world.commitTurn(sid, t, "walk the piers", "You pace the wet boards...");
            }

            // Turn 5 — reference Kaelen. Retrieval must surface the turn-1 fact.
            List<String> facts = world.relevantFacts(sid, "ask Kaelen about the silver blade");
            assertThat(facts).as("turn-1 fact recalled at turn 5").anyMatch(f -> f.contains("silver blade"));

            // "story so far" carries the opening + recent turns for local continuity.
            String story = world.recentStory(sid, "You arrive at a rain-soaked port.", 6);
            assertThat(story).contains("You arrive at a rain-soaked port.")
                             .contains("enter the tavern and speak with the keeper");

            // turnCount advanced to the latest committed turn.
            assertThat(sessions.findById(sid)).get().extracting(Session::getTurnCount).isEqualTo(4);
        } finally {
            neo4j.query("MATCH (n {sessionId:$sid}) DETACH DELETE n").bindAll(Map.of("sid", sid)).run();
            sessions.deleteById(sid);
        }
    }
}
