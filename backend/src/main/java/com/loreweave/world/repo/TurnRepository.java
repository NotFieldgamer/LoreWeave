package com.loreweave.world.repo;

import com.loreweave.world.Turn;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.List;

public interface TurnRepository extends Neo4jRepository<Turn, String> {
    List<Turn> findBySessionIdOrderByIndexAsc(String sessionId);
}
