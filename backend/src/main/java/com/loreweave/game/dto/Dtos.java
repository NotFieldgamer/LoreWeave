package com.loreweave.game.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

/** API-boundary types. Never serialize @Node entities directly (Golden rule / conventions). */
public final class Dtos {
    private Dtos() {}

    public record CreateSessionRequest(@NotBlank String genre, @NotBlank String premise) {}

    public record SessionDto(String id, String title, String genre, Instant createdAt, int turnCount) {}

    /** One played turn, for restoring the transcript when the play screen loads. */
    public record TurnDto(int index, String action, String narration) {}

    /** A single adventure with enough to render the play screen: opening scene + the story so far. */
    public record SessionDetailDto(String id, String title, String genre, String openingScene,
                                   int turnCount, List<TurnDto> turns) {}

    public record TurnRequest(@NotBlank String action) {}

    public record EntityDto(String id, String name, String type, String summary) {}

    public record RelationDto(String from, String type, String to) {}

    public record FactDto(String id, String text, int turn, String status) {}

    public record WorldStateDto(List<EntityDto> entities, List<RelationDto> relations, List<FactDto> facts) {}

    // ── Dashboard (M5): real per-adventure stats + aggregate totals + a live activity feed ──
    public record AdventureStatDto(String id, String title, String genre,
                                   int turnCount, int entityCount, int factCount) {}

    public record RecentFactDto(String sessionId, String title, String text, int turn) {}

    public record DashboardDto(int worlds, int turns, int entities, int facts,
                               List<AdventureStatDto> adventures, List<RecentFactDto> recent) {}
}
