package com.loreweave;

import com.loreweave.world.EntityType;
import com.loreweave.world.Fact;
import com.loreweave.world.Session;
import com.loreweave.world.WorldEntity;
import com.loreweave.world.repo.EntityRepository;
import com.loreweave.world.repo.FactRepository;
import com.loreweave.world.repo.SessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test (needs a running Neo4j): proves Spring Data Neo4j {@code save()} actually CREATES
 * nodes for entities with an externally-assigned String {@code @Id}. This is the assumption the M2
 * world-seeding relies on, so we pin it down.
 */
@SpringBootTest
class WorldPersistenceTest {

    @Autowired SessionRepository sessions;
    @Autowired EntityRepository entities;
    @Autowired FactRepository facts;

    @Test
    void saveCreatesNodesWithAssignedStringIds() {
        String sid = "itest-" + UUID.randomUUID();
        String eid = UUID.randomUUID().toString();
        String fid = UUID.randomUUID().toString();
        try {
            sessions.save(new Session(sid, "owner-itest", "Test Adventure", "Test"));
            entities.save(new WorldEntity(eid, sid, "Rain-soaked Port", EntityType.PLACE, "a wet harbour", 0));
            facts.save(new Fact(fid, sid, "The port smells of salt and tar", 0, 0.9, "ACTIVE"));

            assertThat(sessions.findById(sid)).isPresent();
            assertThat(entities.findBySessionId(sid)).extracting(WorldEntity::getName).contains("Rain-soaked Port");
            assertThat(facts.findBySessionIdAndStatus(sid, "ACTIVE")).extracting(Fact::getText)
                .contains("The port smells of salt and tar");
        } finally {
            sessions.deleteById(sid);
            entities.deleteById(eid);
            facts.deleteById(fid);
        }
    }
}
