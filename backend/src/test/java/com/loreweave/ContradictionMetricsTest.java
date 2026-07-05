package com.loreweave;

import com.loreweave.llm.eval.ConfusionMatrix;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The honest-metrics math itself, tested deterministically (no LLM). If precision/recall/F1 are the
 * numbers we stake the guard's credibility on (Golden rule #4), the formulas had better be right.
 */
class ContradictionMetricsTest {

    @Test
    void computesPrecisionRecallAndF1FromAConfusionMatrix() {
        // 8 pairs. gold positives at 0..3; predictions get 0,1,3 right, miss 2 (FN), false-alarm 5 (FP).
        boolean[] gold      = { true,  true,  true,  true,  false, false, false, false };
        boolean[] predicted = { true,  true,  false, true,  false, true,  false, false };

        ConfusionMatrix cm = ConfusionMatrix.of(predicted, gold);

        assertThat(cm.truePositives()).isEqualTo(3);   // 0,1,3
        assertThat(cm.falseNegatives()).isEqualTo(1);  // 2
        assertThat(cm.falsePositives()).isEqualTo(1);  // 5
        assertThat(cm.trueNegatives()).isEqualTo(3);   // 4,6,7

        assertThat(cm.precision()).isCloseTo(0.75, within(1e-9)); // 3 / (3+1)
        assertThat(cm.recall()).isCloseTo(0.75, within(1e-9));    // 3 / (3+1)
        assertThat(cm.f1()).isCloseTo(0.75, within(1e-9));
        assertThat(cm.accuracy()).isCloseTo(0.75, within(1e-9));  // (3+3)/8
    }

    @Test
    void precisionAndRecallAreZeroRatherThanNaNAtTheEdges() {
        // A "predict nothing" classifier: high accuracy, but it caught no real conflicts.
        boolean[] gold      = { true, true, false, false, false, false, false, false, false, false };
        boolean[] predicted = { false, false, false, false, false, false, false, false, false, false };

        ConfusionMatrix cm = ConfusionMatrix.of(predicted, gold);

        assertThat(cm.precision()).isEqualTo(0.0);       // nothing flagged → 0, not NaN
        assertThat(cm.recall()).isEqualTo(0.0);          // caught none of the 2 real conflicts
        assertThat(cm.f1()).isEqualTo(0.0);
        assertThat(cm.accuracy()).isEqualTo(0.8);        // 8/10 — exactly why accuracy alone lies
    }

    @Test
    void reportMentionsPrecisionAndRecall() {
        String report = ConfusionMatrix.of(new boolean[]{true}, new boolean[]{true}).report();
        assertThat(report).contains("precision").contains("recall").contains("F1");
    }
}
