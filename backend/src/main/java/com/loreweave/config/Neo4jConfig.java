package com.loreweave.config;

import org.neo4j.driver.Driver;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Creates the indexes the retrieval layer relies on (see ARCHITECTURE.md §2), on startup.
 */
@Configuration
public class Neo4jConfig {

    @Bean
    CommandLineRunner initIndexes(Driver driver) {
        return args -> {
            List<String> stmts = List.of(
                "CREATE INDEX entity_session IF NOT EXISTS FOR (e:Entity) ON (e.sessionId)",
                "CREATE INDEX fact_session   IF NOT EXISTS FOR (f:Fact)   ON (f.sessionId)",
                "CREATE FULLTEXT INDEX entityText IF NOT EXISTS FOR (e:Entity) ON EACH [e.name, e.summary]"
            );
            try (var session = driver.session()) {
                stmts.forEach(s -> session.run(s).consume());
            }
        };
    }
}
