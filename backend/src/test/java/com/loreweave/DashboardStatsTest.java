package com.loreweave;

import com.loreweave.game.SessionService;
import com.loreweave.game.dto.Dtos.DashboardDto;
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
 * The dashboard aggregate (M5), tested deterministically (Neo4j only, no LLM): real per-adventure
 * counts + owner totals + the recent-facts feed, all scoped to the owner. This is the exact query
 * path GameController.stats runs.
 */
@SpringBootTest
class DashboardStatsTest {

    @Autowired SessionService sessions;
    @Autowired WorldStateService world;
    @Autowired SessionRepository sessionRepo;
    @Autowired Neo4jClient neo4j;

    @Test
    void dashboardAggregatesRealPerAdventureStatsForTheOwner() {
        String owner = "dash-" + UUID.randomUUID();
        String a = "adv-a-" + UUID.randomUUID();
        String b = "adv-b-" + UUID.randomUUID();
        try {
            sessionRepo.save(new Session(a, owner, "The Salt Road", "Dark fantasy"));
            sessionRepo.save(new Session(b, owner, "Ashfall", "Seafaring myth"));

            // Adventure A: two turns → 3 entities, 3 facts, turnCount 2.
            world.applyDelta(a, 1,
                List.of(new SeedEntity("Kaelen", "CHARACTER", "keeper"), new SeedEntity("The Siren", "PLACE", "a tavern")),
                List.of(new SeedFact("Kaelen keeps the Siren", List.of("Kaelen"), 0.9),
                        new SeedFact("The Siren sits on the docks", List.of("The Siren"), 0.9)), List.of());
            world.commitTurn(a, 1, "enter", "You enter.");
            world.applyDelta(a, 2, List.of(new SeedEntity("Sera", "CHARACTER", "a smuggler")),
                List.of(new SeedFact("Sera owes you silver", List.of("Sera"), 0.9)), List.of());
            world.commitTurn(a, 2, "ask", "You ask.");

            // Adventure B: one turn → 1 entity, 1 fact, turnCount 1.
            world.applyDelta(b, 1, List.of(new SeedEntity("Rourke", "CHARACTER", "a captain")),
                List.of(new SeedFact("Rourke commands the harbor watch", List.of("Rourke"), 0.9)), List.of());
            world.commitTurn(b, 1, "sail", "You sail.");

            // Pin createdAt so the global-recency ordering is deterministic: B is newer than A, even
            // though B's fact is turn 1 and A's newest is turn 2 (this is exactly the case that a
            // per-turn ordering would get wrong).
            neo4j.query("MATCH (f:Fact {sessionId:$a}) SET f.createdAt = datetime('2020-01-01T00:00:00Z')").bindAll(Map.of("a", a)).run();
            neo4j.query("MATCH (f:Fact {sessionId:$b}) SET f.createdAt = datetime('2020-06-01T00:00:00Z')").bindAll(Map.of("b", b)).run();

            DashboardDto d = sessions.dashboard(owner);

            assertThat(d.worlds()).isEqualTo(2);
            assertThat(d.turns()).isEqualTo(3);       // 2 + 1
            assertThat(d.entities()).isEqualTo(4);     // 3 + 1
            assertThat(d.facts()).isEqualTo(4);        // 3 + 1

            assertThat(d.adventures()).hasSize(2)
                .anySatisfy(x -> { assertThat(x.title()).isEqualTo("The Salt Road");
                                   assertThat(x.turnCount()).isEqualTo(2);
                                   assertThat(x.entityCount()).isEqualTo(3);
                                   assertThat(x.factCount()).isEqualTo(3); })
                .anySatisfy(x -> { assertThat(x.title()).isEqualTo("Ashfall");
                                   assertThat(x.entityCount()).isEqualTo(1); });

            assertThat(d.recent()).isNotEmpty().hasSizeLessThanOrEqualTo(12)
                .allSatisfy(f -> { assertThat(f.text()).isNotBlank(); assertThat(f.title()).isNotBlank(); });
            // Global recency: B's newer turn-1 fact leads the feed, ahead of A's older turn-2 fact.
            assertThat(d.recent().get(0).text()).isEqualTo("Rourke commands the harbor watch");
            assertThat(d.recent().get(0).title()).isEqualTo("Ashfall");

            // Scoped to the owner: a different user sees an empty dashboard.
            DashboardDto intruder = sessions.dashboard("intruder-" + UUID.randomUUID());
            assertThat(intruder.worlds()).isZero();
            assertThat(intruder.facts()).isZero();
            assertThat(intruder.adventures()).isEmpty();
        } finally {
            neo4j.query("MATCH (n) WHERE n.sessionId IN [$a,$b] DETACH DELETE n").bindAll(Map.of("a", a, "b", b)).run();
            sessionRepo.deleteById(a);
            sessionRepo.deleteById(b);
        }
    }
}
