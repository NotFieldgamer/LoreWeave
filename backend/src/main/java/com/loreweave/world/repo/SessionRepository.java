package com.loreweave.world.repo;

import com.loreweave.world.Session;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.List;

public interface SessionRepository extends Neo4jRepository<Session, String> {
    List<Session> findByOwnerIdOrderByCreatedAtDesc(String ownerId);
}
