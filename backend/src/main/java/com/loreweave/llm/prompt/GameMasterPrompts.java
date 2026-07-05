package com.loreweave.llm.prompt;

import java.util.List;

/** All prompt text in one place, so it can be versioned and tuned (see Golden rule #4). */
public final class GameMasterPrompts {

    private GameMasterPrompts() {}

    public static final String SYSTEM = """
        You are the Game Master of an interactive-fiction adventure.
        Rules:
        - Continue the story in vivid second person ("You ...").
        - You MUST stay consistent with the ESTABLISHED FACTS provided. Never contradict them.
        - Advance one beat per turn; end on a hook or a light choice. 2–4 short paragraphs.
        - Introduce new characters/items/places naturally when the story calls for it.
        """;

    /** The narration prompt: established facts + recent story + the player's action. */
    public static String turnUserMessage(List<String> establishedFacts, String recentStory, String playerAction) {
        String facts = establishedFacts.isEmpty() ? "(none yet)" : String.join("\n- ", establishedFacts);
        return """
            ESTABLISHED FACTS (do not contradict):
            - %s

            STORY SO FAR (most recent):
            %s

            THE PLAYER DOES:
            %s

            Write the next part of the story.
            """.formatted(facts, recentStory, playerAction);
    }

    /**
     * Session bootstrap (M2): the LLM writes the opening scene AND seeds the initial world,
     * returned as one compact JSON object so we can persist entities/facts/relations in one call.
     */
    public static String openingSceneMessage(String genre, String premise) {
        return """
            You are the Game Master starting a BRAND NEW interactive-fiction adventure.
            Genre: %s
            Opening premise: %s

            Write the opening beat and seed the starting world. Return ONLY compact JSON of this EXACT
            shape — no prose, no markdown, no code fences:
            {
              "title": "a short, evocative title for this adventure (3-5 words)",
              "opening": "2-3 short paragraphs of vivid second-person opening narration that sets the scene and ends on a hook or a light choice",
              "entities": [
                {"name": "a unique proper name", "type": "PLACE|CHARACTER|ITEM|FACTION|EVENT", "summary": "one concise sentence"}
              ],
              "facts": [
                {"text": "an atomic, currently-true statement about the world", "about": ["EntityName"], "confidence": 0.9}
              ],
              "relations": [
                {"from": "EntityName", "type": "KNOWS|ALLY_OF|ENEMY_OF|LOCATED_IN|OWNS|MEMBER_OF", "to": "EntityName"}
              ]
            }

            Rules:
            - Seed 4-6 entities: EXACTLY ONE PLACE (the current location), 2-4 CHARACTERs, and 0-2 ITEM/FACTION.
            - Every name used in "about", "from", and "to" MUST exactly match an entity "name".
            - Provide 4-8 starting facts, each grounded in the opening.
            - Keep everything internally consistent — this is the seed of a world that must never contradict itself.
            """.formatted(genre, premise);
    }

    /**
     * The contradiction guard's NLI-style check (M4): how does statement B relate to statement A?
     * Kept deliberately small and JSON-only so it's cheap on the free tier and easy to score.
     */
    public static String contradictionMessage(String a, String b) {
        return """
            You are a strict consistency checker for a single fictional world. Assume STATEMENT A and
            STATEMENT B describe the world at the SAME moment, unless one explicitly names a different time.
            Classify how B relates to A:
            - "contradicts": A and B cannot both be true at once. This INCLUDES opposite states or
              mutually exclusive attributes — e.g. locked vs open, alive vs dead, armed vs unarmed,
              east vs west, owes money vs owes nothing, trusts vs wants to kill, black vs white.
            - "supports": B restates A or clearly follows from it.
            - "neutral": both can be true at once (independent, unrelated details).
            Be decisive: if two claims about the same thing cannot hold together, label "contradicts".
            Return ONLY compact JSON, no prose or code fences:
            {"label":"contradicts|supports|neutral","confidence":0.0-1.0}

            Examples:
            A: "The cellar door is locked." B: "The cellar door is wide open." -> {"label":"contradicts","confidence":0.97}
            A: "Bram is a blacksmith." B: "Bram forges swords in his smithy." -> {"label":"supports","confidence":0.85}
            A: "Wren has red hair." B: "Wren carries a wooden staff." -> {"label":"neutral","confidence":0.8}

            Now judge:
            A: "%s"
            B: "%s"
            """.formatted(escapeQuotes(a), escapeQuotes(b));
    }

    private static String escapeQuotes(String s) { return s == null ? "" : s.replace('"', '\''); }

    /** The cheap structured extraction call: pull new/changed world state as JSON. */
    public static String extractionMessage(String narration) {
        return """
            From the narration below, extract any NEW or CHANGED world state.
            Return ONLY compact JSON of this exact shape (no prose, no code fences):
            {
              "entities": [{"name": "...", "type": "CHARACTER|ITEM|PLACE|FACTION|EVENT", "summary": "..."}],
              "facts":    [{"text": "...", "about": ["EntityName", "..."], "confidence": 0.0}],
              "relations":[{"from": "EntityName", "type": "KNOWS|ALLY_OF|ENEMY_OF|LOCATED_IN|OWNS|MEMBER_OF", "to": "EntityName"}]
            }

            NARRATION:
            %s
            """.formatted(narration);
    }
}
