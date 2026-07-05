package com.loreweave.world.repo;

import com.loreweave.world.Fact;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.List;

public interface FactRepository extends Neo4jRepository<Fact, String> {
    List<Fact> findBySessionIdAndStatus(String sessionId, String status);
}
