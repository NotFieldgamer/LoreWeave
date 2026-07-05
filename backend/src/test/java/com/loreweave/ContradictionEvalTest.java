package com.loreweave;

import com.loreweave.llm.eval.ContradictionEvaluator;
import com.loreweave.llm.eval.ContradictionEvaluator.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The honest evaluation (needs a real GEMINI_API_KEY, else skipped): score the contradiction guard on
 * the hand-labeled fact-pair set and PRINT precision/recall/F1 (Golden rule #4). Every miss/unscored
 * pair is logged so failure modes — and quota starvation — are visible.
 *
 * The run is paced and deadline-bounded (see {@link ContradictionEvaluator}); if the free tier
 * throttles too hard the pairs that couldn't be scored are counted, and the test SKIPS (not fails)
 * when coverage is too low — a quota-starved run must never be mistaken for a good or bad classifier.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = "AIza.+")
class ContradictionEvalTest {

    @Autowired ContradictionEvaluator evaluator;

    @Test
    void guardScoresWellOnLabeledPairs() {
        System.out.println("[M4-EVAL] scoring " + ContradictionEvaluator.DATASET.size()
            + " labeled fact pairs through the live guard (paced for the free tier)...");

        Result r = evaluator.evaluate();

        r.details().forEach(d -> {
            if (!d.scored())
                System.out.printf("   UNSCORED       A=\"%s\"  B=\"%s\"%n", d.pair().a(), d.pair().b());
            else if (d.miss())
                System.out.printf("   MISS  gold=%-8s pred=%-8s  A=\"%s\"  B=\"%s\"%n",
                    d.pair().contradicts() ? "conflict" : "ok",
                    d.predicted() ? "conflict" : "ok", d.pair().a(), d.pair().b());
        });
        System.out.println(r.report());

        // A quota-starved run is inconclusive, not a failure — skip rather than report a fake number.
        assumeTrue(r.scored() >= 8,
            "Only " + r.scored() + "/" + r.total() + " pairs scored (Gemini free-tier throttle) — inconclusive, skipping asserts.");

        // With enough coverage: not degenerate (both flags and clears), and clears a quality floor.
        assertThat(r.matrix().precision()).as("precision").isGreaterThan(0.0);
        assertThat(r.matrix().recall()).as("recall").isGreaterThan(0.0);
        assertThat(r.matrix().f1()).as("F1 on the labeled set").isGreaterThanOrEqualTo(0.6);
    }
}
