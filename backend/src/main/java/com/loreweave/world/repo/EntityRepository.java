package com.loreweave.world.repo;

import com.loreweave.world.WorldEntity;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.List;

public interface EntityRepository extends Neo4jRepository<WorldEntity, String> {
    List<WorldEntity> findBySessionId(String sessionId);
}
