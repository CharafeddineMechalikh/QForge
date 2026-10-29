package com.mechalikh.qforge.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import example.algorithms.AmplitudeAmplificationComplete;
import example.algorithms.AmplitudeAmplificationComplete.AmplificationResult;
import example.algorithms.AmplitudeAmplificationComplete.SignCheck;
import example.algorithms.QaoaMaxCut;
import example.algorithms.QaoaMaxCut.BruteForceResult;
import example.algorithms.QaoaMaxCut.QaoaResult;
import example.algorithms.QuantumArithmeticComplete;
import example.algorithms.QuantumArithmeticComplete.AdderCase;
import example.algorithms.QuantumClassifierDemo;
import example.algorithms.QuantumClassifierDemo.ClassifierResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Validates the research examples against closed form or brute force references.
 *
 * <p>The tests run the same entry points that the example programs use, so a failure here means
 * the printed numbers in the companion README no longer reproduce.</p>
 */
class AlgorithmValidationTest {

    private static final double TOLERANCE = 1e-9;

    /**
     * Checks the in-place adder on every input pair for two and three bit registers.
     *
     * @throws Exception if a simulation fails
     */
    @Test
    void adderMatchesEveryTruthTableRow() throws Exception {
        assertAdderExhaustive(2);
        assertAdderExhaustive(3);
    }

    private void assertAdderExhaustive(int n) throws Exception {
        List<AdderCase> cases = QuantumArithmeticComplete.verifyAdder(n);
        int modulus = 1 << n;
        assertEquals(modulus * modulus, cases.size(),
            "Expected every pair of " + n + " bit registers");
        for (AdderCase adderCase : cases) {
            String description = "a=" + adderCase.getA() + " b=" + adderCase.getB();
            assertTrue(adderCase.isPassed(), "Adder case failed for " + description);
            assertEquals((adderCase.getA() + adderCase.getB()) % modulus,
                adderCase.getExpected(), "Wrong expectation for " + description);
            assertEquals(adderCase.getExpected(), adderCase.getMeasured(),
                "Wrong measured sum for " + description);
            assertEquals(0, adderCase.getCarry(), "Carry leaked for " + description);
            assertEquals(1.0, adderCase.getAmplitude(), TOLERANCE,
                "Amplitude is not one for " + description);
        }
    }

    /**
     * Compares the amplified success probability with the analytic rotation formula.
     *
     * @throws Exception if a simulation fails
     */
    @Test
    void amplificationMatchesAnalyticProbability() throws Exception {
        int numQubits = 4;
        int space = 1 << numQubits;
        int[][] markedSets = {{5}, {3, 12}, {1, 7, 14}};
        for (int[] marked : markedSets) {
            int iterations = AmplitudeAmplificationComplete.optimalIterations(
                numQubits, marked.length);
            int expectedIterations = (int) Math.floor(Math.PI / 4.0
                * Math.sqrt((double) space / marked.length));
            assertEquals(expectedIterations, iterations, "Wrong iteration count");

            SignCheck signs = AmplitudeAmplificationComplete.checkOracleSigns(numQubits, marked);
            assertTrue(signs.isPassed(), "Oracle marked the wrong basis states");
            assertEquals(marked.length, signs.getNegativeMarked(),
                "Wrong number of negative marked amplitudes");
            assertEquals(space - marked.length, signs.getPositiveUnmarked(),
                "Wrong number of positive unmarked amplitudes");

            double theta = Math.asin(Math.sqrt((double) marked.length / space));
            double analytic = Math.sin((2 * iterations + 1) * theta);
            analytic *= analytic;

            AmplificationResult result = AmplitudeAmplificationComplete.amplify(
                numQubits, marked, iterations);
            assertEquals(iterations, result.getIterations(), "Wrong measured iteration count");
            assertEquals(analytic, result.getSuccessProbability(), TOLERANCE,
                "Amplification deviates from the analytic formula");

            double sum = 0.0;
            for (double probability : result.getMarkedProbabilities()) {
                sum += probability;
            }
            assertEquals(result.getSuccessProbability(), sum, TOLERANCE,
                "Marked probabilities do not add up to the success probability");
        }
    }

    /**
     * Checks that one QAOA layer already reaches half of the brute force optimum.
     *
     * @throws Exception if a simulation fails
     */
    @Test
    void qaoaApproximationRatioReachesOneHalf() throws Exception {
        BruteForceResult bruteForce = QaoaMaxCut.bruteForceMaxCut();
        QaoaResult result = QaoaMaxCut.optimize(1, new double[] {0.5, 0.5});
        assertEquals(1, result.getDepth(), "Wrong depth");
        assertEquals(1, result.getGamma().length, "Wrong number of problem angles");
        assertEquals(1, result.getBeta().length, "Wrong number of mixer angles");
        double ratio = result.getExpectedCut() / bruteForce.getMaxCut();
        assertTrue(ratio >= 0.5,
            "QAOA approximation ratio " + ratio + " is below one half");
        assertTrue(result.getExpectedCut() <= bruteForce.getMaxCut() + TOLERANCE,
            "QAOA expectation exceeds the brute force maximum");
    }

    /**
     * Checks that the trained classifier beats the majority class on the test split.
     *
     * @throws Exception if a simulation fails
     */
    @Test
    void classifierBeatsChanceOnTestSplit() throws Exception {
        ClassifierResult result = QuantumClassifierDemo.trainAndEvaluate();
        assertTrue(result.getTestAccuracy() > 0.5,
            "Test accuracy " + result.getTestAccuracy() + " is not above chance");
        assertTrue(result.getTrainingAccuracy() > 0.5,
            "Training accuracy " + result.getTrainingAccuracy() + " is not above chance");

        int[][] confusion = result.getConfusionMatrix();
        assertEquals(2, confusion.length, "Confusion matrix must have two rows");
        int total = 0;
        for (int[] row : confusion) {
            assertEquals(2, row.length, "Confusion matrix must have two columns");
            for (int count : row) {
                assertTrue(count >= 0, "Confusion matrix count is negative");
                total += count;
            }
        }
        assertEquals(result.getTestIndices().length, total,
            "Confusion matrix does not cover the test split");
        int correct = confusion[0][0] + confusion[1][1];
        assertEquals(result.getTestAccuracy(), (double) correct / total, TOLERANCE,
            "Test accuracy disagrees with the confusion matrix");
    }
}
