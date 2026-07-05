package com.loreweave.world;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.Instant;

/** One adventure. Scoped to an owner (Clerk user id). */
@Node("Session")
public class Session {
    @Id private String id;
    private String ownerId;
    private String title;
    private String genre;
    private String openingScene;   // turn-0 narration written when the world is seeded (M2)
    private Instant createdAt;
    private int turnCount;

    public Session() {}

    public Session(String id, String ownerId, String title, String genre) {
        this.id = id; this.ownerId = ownerId; this.title = title; this.genre = genre;
        this.createdAt = Instant.now(); this.turnCount = 0;
    }

    public String getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public String getTitle() { return title; }
    public String getGenre() { return genre; }
    public String getOpeningScene() { return openingScene; }
    public Instant getCreatedAt() { return createdAt; }
    public int getTurnCount() { return turnCount; }
    public void setTitle(String t) { this.title = t; }
    public void setOpeningScene(String s) { this.openingScene = s; }
    public void setTurnCount(int c) { this.turnCount = c; }
}
