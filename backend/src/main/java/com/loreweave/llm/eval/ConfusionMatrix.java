package com.loreweave.llm.eval;

/**
 * Binary-classifier scores for the contradiction guard ("contradiction" = the positive class).
 *
 * We report precision, recall AND F1 — never accuracy alone (Golden rule #4). Contradictions are the
 * rare, costly class; on an imbalanced set a "predict nothing" model scores high accuracy while
 * catching zero real conflicts. Precision (of what we flagged, how much was real) and recall (of the
 * real conflicts, how many we caught) are the numbers that actually describe the guard.
 */
public record ConfusionMatrix(int truePositives, int falsePositives, int trueNegatives, int falseNegatives) {

    public int total() { return truePositives + falsePositives + trueNegatives + falseNegatives; }
    public int predictedPositive() { return truePositives + falsePositives; }
    public int actualPositive() { return truePositives + falseNegatives; }

    /** Of the pairs we flagged as contradictions, the fraction that really were. 0 if we flagged none. */
    public double precision() {
        int denom = predictedPositive();
        return denom == 0 ? 0.0 : (double) truePositives / denom;
    }

    /** Of the real contradictions, the fraction we caught. 0 if there were none. */
    public double recall() {
        int denom = actualPositive();
        return denom == 0 ? 0.0 : (double) truePositives / denom;
    }

    public double f1() {
        double p = precision(), r = recall();
        return (p + r == 0.0) ? 0.0 : (2 * p * r) / (p + r);
    }

    /** Secondary only — never reported without precision/recall beside it. */
    public double accuracy() {
        return total() == 0 ? 0.0 : (double) (truePositives + trueNegatives) / total();
    }

    /** Score predictions against gold labels (parallel arrays, same length). */
    public static ConfusionMatrix of(boolean[] predicted, boolean[] gold) {
        if (predicted.length != gold.length)
            throw new IllegalArgumentException("predicted/gold length mismatch");
        int tp = 0, fp = 0, tn = 0, fn = 0;
        for (int i = 0; i < gold.length; i++) {
            if (gold[i]) { if (predicted[i]) tp++; else fn++; }
            else         { if (predicted[i]) fp++; else tn++; }
        }
        return new ConfusionMatrix(tp, fp, tn, fn);
    }

    public String report() {
        return String.format(
            "Contradiction guard — evaluation on %d labeled fact pairs%n"
          + "  confusion:  TP=%d  FP=%d  TN=%d  FN=%d%n"
          + "  precision = %.3f   (of %d flagged, how many were real conflicts)%n"
          + "  recall    = %.3f   (of %d real conflicts, how many we caught)%n"
          + "  F1        = %.3f%n"
          + "  accuracy  = %.3f   (secondary — shown only beside precision/recall)",
            total(), truePositives, falsePositives, trueNegatives, falseNegatives,
            precision(), predictedPositive(),
            recall(), actualPositive(),
            f1(), accuracy());
    }
}
