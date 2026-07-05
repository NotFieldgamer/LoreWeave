package com.loreweave;

import com.loreweave.game.SessionService;
import com.loreweave.game.dto.Dtos.SessionDto;
import com.loreweave.game.dto.Dtos.WorldStateDto;
import com.loreweave.world.EntityType;
import com.loreweave.world.repo.EntityRepository;
import com.loreweave.world.repo.FactRepository;
import com.loreweave.world.repo.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end M2 (needs Neo4j + a real GEMINI_API_KEY, else skipped): SessionService.create makes a
 * real Gemini call to write the opening scene and seed the world, and we assert the graph is populated.
 * This is the exact code path GameController.create runs (it only adds ownerId = jwt.getSubject()).
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = "AIza.+")
class SessionCreationIT {

    @Autowired SessionService sessions;
    @Autowired SessionRepository sessionRepo;
    @Autowired EntityRepository entityRepo;
    @Autowired FactRepository factRepo;

    @Test
    void createSeedsAWorldFromGemini() {
        String owner = "it-owner-" + UUID.randomUUID();
        SessionDto dto = sessions.create(owner, "Dark fantasy",
            "A stranger arrives at a rain-soaked port at dusk, carrying a sealed letter.");

        System.out.println("[M2-IT] session=" + dto.id() + "  title=\"" + dto.title() + "\"");
        assertThat(dto.id()).isNotBlank();
        assertThat(dto.title()).isNotBlank();
        assertThat(dto.turnCount()).isZero();
        assertThat(dto.genre()).isEqualTo("Dark fantasy");

        assertThat(sessionRepo.findById(dto.id())).isPresent();
        var ents = entityRepo.findBySessionId(dto.id());
        var facts = factRepo.findBySessionIdAndStatus(dto.id(), "ACTIVE");

        System.out.println("[M2-IT] entities=" + ents.size() + "  facts=" + facts.size());
        ents.forEach(e -> System.out.println("   [" + e.getType() + "] " + e.getName() + " — " + e.getSummary()));
        facts.forEach(f -> System.out.println("   · " + f.getText()));

        assertThat(ents).isNotEmpty();
        assertThat(facts).isNotEmpty();
        assertThat(ents).anyMatch(e -> e.getType() == EntityType.PLACE);

        WorldStateDto world = sessions.worldState(owner, dto.id());
        assertThat(world.entities()).hasSameSizeAs(ents);
        assertThat(world.facts()).hasSameSizeAs(facts);

        // Ownership guard: a different user must NOT be able to read this world (404 -> ResponseStatusException).
        assertThatThrownBy(() -> sessions.worldState("intruder-" + UUID.randomUUID(), dto.id()))
            .isInstanceOf(ResponseStatusException.class);

        // Intentionally left in the graph so it can be inspected in Neo4j Browser (the M2 manual test).
    }
}
