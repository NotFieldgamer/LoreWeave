package com.loreweave.world;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

/** A character / item / place / faction / event the world remembers. Label = "Entity". */
@Node("Entity")
public class WorldEntity {
    @Id private String id;
    private String sessionId;
    private String name;
    private EntityType type;
    private String summary;
    private int firstSeenTurn;

    // TODO(loreweave M3): model @Relationship links to other entities with a `turn` property
    //   (KNOWS, ALLY_OF, ENEMY_OF, LOCATED_IN, OWNS, MEMBER_OF). See ARCHITECTURE.md §2.

    public WorldEntity() {}

    public WorldEntity(String id, String sessionId, String name, EntityType type, String summary, int firstSeenTurn) {
        this.id = id; this.sessionId = sessionId; this.name = name;
        this.type = type; this.summary = summary; this.firstSeenTurn = firstSeenTurn;
    }

    public String getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getName() { return name; }
    public EntityType getType() { return type; }
    public String getSummary() { return summary; }
    public int getFirstSeenTurn() { return firstSeenTurn; }
    public void setSummary(String s) { this.summary = s; }
}
