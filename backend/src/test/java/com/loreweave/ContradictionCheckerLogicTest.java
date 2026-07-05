package com.loreweave;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loreweave.llm.ContradictionChecker;
import com.loreweave.llm.ContradictionChecker.Verdict;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The guard's DECISION LOGIC, tested deterministically with a scripted model (no real LLM, no quota):
 * pre-filter → NLI → confidence threshold → verdict, plus fail-open on a model error. This isolates
 * the part we own; the model's *judgment quality* (precision/recall) is what {@link ContradictionEvalTest}
 * measures live. Together they cover the guard without leaning on the flaky/quota-limited network path.
 */
class ContradictionCheckerLogicTest {

    private ContradictionChecker checkerReturning(String cannedJson) {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyString())).thenReturn(cannedJson);
        return wire(model);
    }

    private ContradictionChecker wire(ChatLanguageModel model) {
        ContradictionChecker c = new ContradictionChecker(model, new ObjectMapper());
        // @Value fields aren't injected in a plain unit test — set the production defaults by hand.
        ReflectionTestUtils.setField(c, "minConfidence", 0.5);
        ReflectionTestUtils.setField(c, "minOverlap", 0.12);
        ReflectionTestUtils.setField(c, "maxChecks", 16);
        return c;
    }

    @Test
    void flagsAConfidentContradictionBetweenOverlappingFacts() {
        ContradictionChecker c = checkerReturning("{\"label\":\"contradicts\",\"confidence\":0.9}");
        assertThat(c.flagsContradiction("The gate is locked.", "The gate is open.")).isTrue();

        List<Verdict> v = c.findConflicts(List.of("The gate is open."), List.of("The gate is locked."));
        assertThat(v).singleElement().satisfies(x -> {
            assertThat(x.candidateFact()).isEqualTo("The gate is open.");
            assertThat(x.conflictsWith()).isEqualTo("The gate is locked.");
            assertThat(x.label()).isEqualTo("contradicts");
        });
    }

    @Test
    void ignoresAContradictionBelowTheConfidenceThreshold() {
        ContradictionChecker c = checkerReturning("{\"label\":\"contradicts\",\"confidence\":0.3}");
        assertThat(c.flagsContradiction("The gate is locked.", "The gate is open.")).isFalse();
        assertThat(c.findConflicts(List.of("The gate is open."), List.of("The gate is locked."))).isEmpty();
    }

    @Test
    void doesNotFlagANeutralOrSupportingPair() {
        ContradictionChecker c = checkerReturning("{\"label\":\"neutral\",\"confidence\":0.95}");
        assertThat(c.flagsContradiction("Kaelen keeps the tavern.", "Kaelen has a scar.")).isFalse();
    }

    @Test
    void skipsTheModelEntirelyWhenFactsDoNotOverlap() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyString())).thenReturn("{\"label\":\"contradicts\",\"confidence\":0.99}");
        ContradictionChecker c = wire(model);

        // No shared content words → the cheap pre-filter rejects the pair before any LLM call.
        assertThat(c.findConflicts(List.of("The river flows west."), List.of("Kaelen owns a sword."))).isEmpty();
        verify(model, never()).generate(anyString());
    }

    @Test
    void failsOpenWhenTheModelThrows() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyString())).thenThrow(new RuntimeException("429 quota exhausted"));
        ContradictionChecker c = wire(model);

        // A model error must never abort the turn — the pair is simply not flagged.
        assertThat(c.findConflicts(List.of("The gate is open."), List.of("The gate is locked."))).isEmpty();
    }
}
