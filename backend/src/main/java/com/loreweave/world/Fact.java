package com.loreweave.world;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.Instant;

/** An atomic truth about the world. status: ACTIVE | SUPERSEDED | CONFLICTED. */
@Node("Fact")
public class Fact {
    @Id private String id;
    private String sessionId;
    private String text;
    private int turn;
    private double confidence;
    private String status;
    private Instant createdAt;   // a global clock: `turn` is per-adventure, so the cross-adventure feed sorts on this

    // TODO(loreweave M3): @Relationship ABOUT -> WorldEntity ; SUPERSEDES -> Fact

    public Fact() {}

    public Fact(String id, String sessionId, String text, int turn, double confidence, String status) {
        this.id = id; this.sessionId = sessionId; this.text = text;
        this.turn = turn; this.confidence = confidence; this.status = status;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getText() { return text; }
    public int getTurn() { return turn; }
    public double getConfidence() { return confidence; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setStatus(String s) { this.status = s; }
}
