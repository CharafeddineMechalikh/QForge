package example.algorithms;

import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.core.SimulationResult;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StateVector;

import example.EventBuilderHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleValueChecker;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;

/**
 * Trains a small variational quantum classifier on a hard-coded Iris data set.
 *
 * <p>The data are thirty flowers from Fisher's Iris data set: fifteen Iris versicolor and fifteen
 * Iris virginica, described by sepal length and petal width in centimetres. The rows are
 * transcribed by hand from the UCI Machine Learning Repository copy of the data and the source is
 * cited in the code. Every third row is held out for testing, which gives twenty training samples
 * and ten test samples with five flowers of each class on both sides. The two features overlap,
 * so no single threshold on either feature separates the classes.</p>
 *
 * <p>Two features are angle encoded with one RY rotation per qubit. Three variational layers
 * follow, each with one RY rotation per qubit and a CNOT from qubit one to qubit zero, for six
 * trainable angles. The CNOT is required because a diagonal entangler leaves the marginal
 * distribution of qubit zero unchanged, so the prediction would depend on one feature only. The
 * prediction is the probability that qubit zero is measured in one. The
 * training objective is the mean squared error on the training split, minimized with the
 * Nelder-Mead simplex from commons-math3. Training and test accuracy and the confusion matrix are
 * reported separately.</p>
 *
 * <p>The limitation is the size and the model. Thirty samples with two features cannot establish a
 * general claim about Iris classification, the circuit has two qubits and six parameters, and the
 * split is a fixed hold-out rather than cross validation. The reported numbers are the measured
 * values of this run, not a published benchmark.</p>
 */
public final class QuantumClassifierDemo {

    // Fisher's Iris data (R. A. Fisher, 1936), as distributed by the UCI Machine Learning
    // Repository (iris.data). Each row is {sepal length cm, petal width cm, label}, where
    // label 0 is Iris versicolor and label 1 is Iris virginica. The rows are transcribed by hand.
    private static final double[][] DATA = {
        {7.0, 1.4, 0.0},
        {6.4, 1.5, 0.0},
        {6.9, 1.5, 0.0},
        {5.5, 1.3, 0.0},
        {6.5, 1.5, 0.0},
        {5.7, 1.3, 0.0},
        {6.3, 1.6, 0.0},
        {4.9, 1.0, 0.0},
        {6.6, 1.3, 0.0},
        {5.2, 1.4, 0.0},
        {6.0, 1.0, 0.0},
        {6.1, 1.4, 0.0},
        {5.6, 1.3, 0.0},
        {5.9, 1.8, 0.0},
        {6.0, 1.6, 0.0},
        {6.3, 2.5, 1.0},
        {5.8, 1.9, 1.0},
        {6.3, 1.8, 1.0},
        {6.5, 2.2, 1.0},
        {7.6, 2.1, 1.0},
        {4.9, 1.7, 1.0},
        {6.7, 1.8, 1.0},
        {7.2, 2.5, 1.0},
        {6.5, 2.0, 1.0},
        {6.4, 1.9, 1.0},
        {5.7, 2.0, 1.0},
        {6.1, 1.4, 1.0},
        {6.0, 1.5, 1.0},
        {6.3, 1.5, 1.0},
        {5.8, 2.4, 1.0}
    };

    private static final int PARAMETERS = 6;
    private static final int LAYERS = 3;
    private static final int NUM_QUBITS = 2;
    private static final double THRESHOLD = 0.5;

    private QuantumClassifierDemo() {
    }

    /**
     * Trains the classifier, prints the split, the learned angles, the accuracies, and the
     * confusion matrix.
     *
     * @param args ignored
     * @throws Exception if a simulation fails
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== Variational quantum classifier on a hard-coded Iris subset ===");
        System.out.println("Source: Fisher (1936), UCI Machine Learning Repository iris.data");
        System.out.println("Features: sepal length and petal width in centimetres");
        System.out.println("Classes: 0 = Iris versicolor, 1 = Iris virginica");

        ClassifierResult result = trainAndEvaluate();
        int[] trainIndices = result.trainIndices;
        int[] testIndices = result.testIndices;
        System.out.printf(Locale.ROOT,
            "Samples: %d total, %d training, %d test (every third row is test)%n",
            DATA.length, trainIndices.length, testIndices.length);
        System.out.printf(Locale.ROOT,
            "Training class counts: %d versicolor, %d virginica%n",
            countLabels(trainIndices, 0), countLabels(trainIndices, 1));
        System.out.printf(Locale.ROOT,
            "Test class counts: %d versicolor, %d virginica%n",
            countLabels(testIndices, 0), countLabels(testIndices, 1));

        System.out.println("Learned angles:");
        for (int layer = 0; layer < LAYERS; layer++) {
            System.out.printf(Locale.ROOT,
                "  layer %d: RY q0 = %.6f, RY q1 = %.6f%n", layer,
                result.parameters[2 * layer], result.parameters[2 * layer + 1]);
        }
        System.out.printf(Locale.ROOT, "Final training mean squared error = %.6f%n",
            result.trainingMse);
        System.out.printf(Locale.ROOT, "Training accuracy = %.4f (%d/%d)%n",
            result.trainingAccuracy, result.correctTrain, trainIndices.length);
        System.out.printf(Locale.ROOT, "Test accuracy = %.4f (%d/%d)%n",
            result.testAccuracy, result.correctTest, testIndices.length);
        System.out.println("Confusion matrix (rows actual, columns predicted):");
        System.out.println("                 predicted 0  predicted 1");
        System.out.printf(Locale.ROOT, "  actual 0            %2d           %2d%n",
            result.confusion[0][0], result.confusion[0][1]);
        System.out.printf(Locale.ROOT, "  actual 1            %2d           %2d%n",
            result.confusion[1][0], result.confusion[1][1]);
        System.out.println("Test predictions:");
        for (int i = 0; i < testIndices.length; i++) {
            int index = testIndices[i];
            System.out.printf(Locale.ROOT,
                "  row %2d: sepal %.1f, petal %.1f, label %d, score %.4f, predicted %d%n",
                index, DATA[index][0], DATA[index][1], (int) DATA[index][2],
                result.testScores[i], result.testScores[i] >= THRESHOLD ? 1 : 0);
        }
        if (result.testAccuracy == 1.0) {
            System.out.println("The measured test split reached 100 percent accuracy.");
        } else {
            System.out.println("The measured test split did not reach 100 percent accuracy.");
        }
    }

    /**
     * Trains the classifier on the fixed split and returns the measured metrics.
     *
     * <p>The features are standardized with the training mean and standard deviation. The
     * optimizer minimizes the mean squared error over the twenty training rows with a
     * Nelder-Mead simplex and a deterministic starting point.</p>
     *
     * @return the learned parameters, accuracies, confusion matrix, and test scores
     * @throws Exception if a simulation fails
     */
    public static ClassifierResult trainAndEvaluate() throws Exception {
        int[] trainIndices = new int[20];
        int[] testIndices = new int[10];
        int trainPosition = 0;
        int testPosition = 0;
        for (int i = 0; i < DATA.length; i++) {
            if (i % 3 == 0) {
                testIndices[testPosition++] = i;
            } else {
                trainIndices[trainPosition++] = i;
            }
        }

        double[] means = new double[2];
        double[] deviations = new double[2];
        for (int feature = 0; feature < 2; feature++) {
            double sum = 0.0;
            for (int index : trainIndices) {
                sum += DATA[index][feature];
            }
            means[feature] = sum / trainIndices.length;
            double variance = 0.0;
            for (int index : trainIndices) {
                double difference = DATA[index][feature] - means[feature];
                variance += difference * difference;
            }
            deviations[feature] = Math.sqrt(variance / trainIndices.length);
            if (deviations[feature] < 1e-12) {
                deviations[feature] = 1.0;
            }
        }

        double[][] trainFeatures = new double[trainIndices.length][2];
        double[] trainLabels = new double[trainIndices.length];
        for (int i = 0; i < trainIndices.length; i++) {
            trainFeatures[i][0] = (DATA[trainIndices[i]][0] - means[0]) / deviations[0];
            trainFeatures[i][1] = (DATA[trainIndices[i]][1] - means[1]) / deviations[1];
            trainLabels[i] = DATA[trainIndices[i]][2];
        }
        double[][] testFeatures = new double[testIndices.length][2];
        double[] testLabels = new double[testIndices.length];
        for (int i = 0; i < testIndices.length; i++) {
            testFeatures[i][0] = (DATA[testIndices[i]][0] - means[0]) / deviations[0];
            testFeatures[i][1] = (DATA[testIndices[i]][1] - means[1]) / deviations[1];
            testLabels[i] = DATA[testIndices[i]][2];
        }

        QuantumModel model = new QuantumModel();
        double[] initial = {0.20, -0.15, 0.30, 0.10, 0.20, -0.25};
        SimplexOptimizer optimizer = new SimplexOptimizer(
            new SimpleValueChecker(1e-9, 1e-9));
        PointValuePair optimum = optimizer.optimize(
            new MaxEval(2500),
            new InitialGuess(initial),
            new ObjectiveFunction(parameters -> {
                try {
                    return model.meanSquaredError(trainFeatures, trainLabels, parameters);
                } catch (Exception exception) {
                    throw new IllegalStateException("Classifier objective failed", exception);
                }
            }),
            GoalType.MINIMIZE,
            new NelderMeadSimplex(PARAMETERS));

        double[] parameters = optimum.getPoint();
        double trainingMse = model.meanSquaredError(trainFeatures, trainLabels, parameters);
        double[] trainScores = model.predict(trainFeatures, parameters);
        double[] testScores = model.predict(testFeatures, parameters);
        int correctTrain = 0;
        for (int i = 0; i < trainScores.length; i++) {
            if ((trainScores[i] >= THRESHOLD ? 1 : 0) == (int) trainLabels[i]) {
                correctTrain++;
            }
        }
        int correctTest = 0;
        int[][] confusion = new int[2][2];
        for (int i = 0; i < testScores.length; i++) {
            int predicted = testScores[i] >= THRESHOLD ? 1 : 0;
            int actual = (int) testLabels[i];
            confusion[actual][predicted]++;
            if (predicted == actual) {
                correctTest++;
            }
        }
        return new ClassifierResult(parameters, trainingMse,
            correctTrain / (double) trainScores.length, correctTest / (double) testScores.length,
            correctTrain, correctTest, confusion, trainIndices, testIndices, testScores);
    }

    private static int countLabels(int[] indices, int label) {
        int count = 0;
        for (int index : indices) {
            if ((int) DATA[index][2] == label) {
                count++;
            }
        }
        return count;
    }

    /**
     * Runs the two-qubit circuit for one or more feature vectors.
     */
    private static final class QuantumModel {

        private final QuantumSimulator simulator;
        private final Qubit[] qubits;

        private QuantumModel() throws Exception {
            SimulationConfig config = new SimulationConfig();
            config.setNumQubits(NUM_QUBITS);
            config.setBackend(SimulationBackend.STATE_VECTOR);
            simulator = new QuantumSimulator();
            simulator.initialize(config);
            qubits = new Qubit[NUM_QUBITS];
            for (int i = 0; i < NUM_QUBITS; i++) {
                qubits[i] = new Qubit(i, "q" + i);
            }
        }

        private double meanSquaredError(double[][] features, double[] labels, double[] parameters)
                throws Exception {
            double sum = 0.0;
            for (int i = 0; i < features.length; i++) {
                double difference = score(features[i], parameters) - labels[i];
                sum += difference * difference;
            }
            return sum / features.length;
        }

        private double[] predict(double[][] features, double[] parameters) throws Exception {
            double[] scores = new double[features.length];
            for (int i = 0; i < features.length; i++) {
                scores[i] = score(features[i], parameters);
            }
            return scores;
        }

        private double score(double[] features, double[] parameters) throws Exception {
            simulator.reset();
            List<QuantumEvent> circuit = new ArrayList<>();
            double angle0 = features[0] * Math.PI / 2.0;
            double angle1 = features[1] * Math.PI / 2.0;
            circuit.add(EventBuilderHelper.createSingleQubitGate(
                "Encode_RY0", SingleQubitGate.createRY(angle0, qubits[0]), qubits[0]));
            circuit.add(EventBuilderHelper.createSingleQubitGate(
                "Encode_RY1", SingleQubitGate.createRY(angle1, qubits[1]), qubits[1]));
            for (int layer = 0; layer < LAYERS; layer++) {
                int offset = 2 * layer;
                circuit.add(EventBuilderHelper.createSingleQubitGate(
                    "L" + layer + "_RY0",
                    SingleQubitGate.createRY(parameters[offset], qubits[0]), qubits[0]));
                circuit.add(EventBuilderHelper.createSingleQubitGate(
                    "L" + layer + "_RY1",
                    SingleQubitGate.createRY(parameters[offset + 1], qubits[1]), qubits[1]));
                circuit.add(EventBuilderHelper.createCNOT(
                    "L" + layer + "_CNOT", qubits[1], qubits[0]));
            }
            for (QuantumEvent event : circuit) {
                simulator.scheduleEvent(event);
            }
            SimulationResult result = simulator.run();
            StateVector state = (StateVector) result.getFinalState();
            double[] probabilities = state.getProbabilities();
            // Qubit zero is the most significant bit, so outcomes 2 and 3 have it equal to one.
            return probabilities[2] + probabilities[3];
        }
    }

    /**
     * Holds the measured training result.
     */
    public static final class ClassifierResult {

        private final double[] parameters;
        private final double trainingMse;
        private final double trainingAccuracy;
        private final double testAccuracy;
        private final int correctTrain;
        private final int correctTest;
        private final int[][] confusion;
        private final int[] trainIndices;
        private final int[] testIndices;
        private final double[] testScores;

        private ClassifierResult(double[] parameters, double trainingMse,
                double trainingAccuracy, double testAccuracy, int correctTrain, int correctTest,
                int[][] confusion, int[] trainIndices, int[] testIndices, double[] testScores) {
            this.parameters = parameters.clone();
            this.trainingMse = trainingMse;
            this.trainingAccuracy = trainingAccuracy;
            this.testAccuracy = testAccuracy;
            this.correctTrain = correctTrain;
            this.correctTest = correctTest;
            this.confusion = confusion;
            this.trainIndices = trainIndices.clone();
            this.testIndices = testIndices.clone();
            this.testScores = testScores.clone();
        }

        /**
         * Returns the six learned angles.
         *
         * @return a copy of the parameter vector
         */
        public double[] getParameters() {
            return parameters.clone();
        }

        /**
         * Returns the final training mean squared error.
         *
         * @return the mean squared error on the training split
         */
        public double getTrainingMse() {
            return trainingMse;
        }

        /**
         * Returns the fraction of training samples classified correctly.
         *
         * @return the training accuracy
         */
        public double getTrainingAccuracy() {
            return trainingAccuracy;
        }

        /**
         * Returns the fraction of test samples classified correctly.
         *
         * @return the test accuracy
         */
        public double getTestAccuracy() {
            return testAccuracy;
        }

        /**
         * Returns the confusion matrix with actual class as the first index.
         *
         * @return a copy of the two by two matrix
         */
        public int[][] getConfusionMatrix() {
            int[][] copy = new int[confusion.length][];
            for (int i = 0; i < confusion.length; i++) {
                copy[i] = confusion[i].clone();
            }
            return copy;
        }

        /**
         * Returns the test sample indices in the hard-coded data array.
         *
         * @return a copy of the test indices
         */
        public int[] getTestIndices() {
            return testIndices.clone();
        }

        /**
         * Returns the predicted score of each test sample.
         *
         * @return a copy of the test scores
         */
        public double[] getTestScores() {
            return testScores.clone();
        }
    }
}
