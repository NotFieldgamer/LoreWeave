package com.loreweave.world;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.Instant;

/**
 * One played turn: the player's action and the GM's narration, in order. This is the "story so far"
 * that gives the next turn local continuity (long-term recall comes from the Fact graph, not here).
 */
@Node("Turn")
public class Turn {
    @Id private String id;
    private String sessionId;
    private int index;          // 1-based turn number
    private String action;      // what the player did
    private String narration;   // what the GM narrated back
    private Instant createdAt;

    public Turn() {}

    public Turn(String id, String sessionId, int index, String action, String narration) {
        this.id = id; this.sessionId = sessionId; this.index = index;
        this.action = action; this.narration = narration; this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getSessionId() { return sessionId; }
    public int getIndex() { return index; }
    public String getAction() { return action; }
    public String getNarration() { return narration; }
    public Instant getCreatedAt() { return createdAt; }
}
