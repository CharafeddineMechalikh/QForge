package example.algorithms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;

import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.math.Complex;
import com.quantum.simulator.math.ComplexMatrix;
import com.quantum.simulator.operations.MultiQubitGate;
import com.quantum.simulator.operations.QuantumGate;
import com.quantum.simulator.operations.SingleQubitGate;
import com.quantum.simulator.state.Qubit;

import example.EventBuilderHelper; 
class Graph {
    private final int vertices;
    private final List<List<Integer>> adjacencyList;
    private final Map<String, Object> properties;
    
    public Graph(int vertices) {
        this.vertices = vertices;
        this.adjacencyList = new ArrayList<>(vertices);
        this.properties = new HashMap<>();
        
        for (int i = 0; i < vertices; i++) {
            adjacencyList.add(new ArrayList<>());
        }
    }
    
    public void addEdge(int u, int v) {
        if (u < 0 || u >= vertices || v < 0 || v >= vertices) {
            throw new IllegalArgumentException("Vertex index out of bounds");
        }
        adjacencyList.get(u).add(v);
        adjacencyList.get(v).add(u); // Undirected graph
    }
    
    public void addDirectedEdge(int u, int v) {
        if (u < 0 || u >= vertices || v < 0 || v >= vertices) {
            throw new IllegalArgumentException("Vertex index out of bounds");
        }
        adjacencyList.get(u).add(v);
    }
    
    public void addWeightedEdge(int u, int v, double weight) {
        addEdge(u, v);
        // Store weight in properties
        String key = u + "_" + v;
        properties.put("weight_" + key, weight);
        properties.put("weight_" + v + "_" + u, weight);
    }
    
    public List<Integer> getNeighbors(int vertex) {
        if (vertex < 0 || vertex >= vertices) {
            throw new IllegalArgumentException("Vertex index out of bounds");
        }
        return new ArrayList<>(adjacencyList.get(vertex));
    }
    
    public int getDegree(int vertex) {
        return adjacencyList.get(vertex).size();
    }
    
    public int getVertices() {
        return vertices;
    }
    
    public int getEdges() {
        int count = 0;
        for (List<Integer> neighbors : adjacencyList) {
            count += neighbors.size();
        }
        return count / 2; // Undirected graph counts edges twice
    }
    
    public double getWeight(int u, int v) {
        String key = u + "_" + v;
        return (double) properties.getOrDefault("weight_" + key, 1.0);
    }
    
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }
    
    public Object getProperty(String key) {
        return properties.get(key);
    }
    
    // Graph factory methods
    public static Graph createLineGraph(int n) {
        Graph graph = new Graph(n);
        for (int i = 0; i < n - 1; i++) {
            graph.addEdge(i, i + 1);
        }
        graph.setProperty("type", "line");
        graph.setProperty("name", "Line graph with " + n + " vertices");
        return graph;
    }
    
    public static Graph createCycleGraph(int n) {
        Graph graph = new Graph(n);
        for (int i = 0; i < n; i++) {
            graph.addEdge(i, (i + 1) % n);
        }
        graph.setProperty("type", "cycle");
        graph.setProperty("name", "Cycle graph C" + n);
        return graph;
    }
    
    public static Graph createCompleteGraph(int n) {
        Graph graph = new Graph(n);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                graph.addEdge(i, j);
            }
        }
        graph.setProperty("type", "complete");
        graph.setProperty("name", "Complete graph K" + n);
        return graph;
    }
    
    public static Graph createStarGraph(int n) {
        Graph graph = new Graph(n);
        int center = 0;
        for (int i = 1; i < n; i++) {
            graph.addEdge(center, i);
        }
        graph.setProperty("type", "star");
        graph.setProperty("name", "Star graph S" + n);
        return graph;
    }
    
    public static Graph createBinaryTree(int levels) {
        int vertices = (1 << levels) - 1;
        Graph graph = new Graph(vertices);
        for (int i = 0; i < vertices; i++) {
            int leftChild = 2 * i + 1;
            int rightChild = 2 * i + 2;
            if (leftChild < vertices) {
                graph.addEdge(i, leftChild);
            }
            if (rightChild < vertices) {
                graph.addEdge(i, rightChild);
            }
        }
        graph.setProperty("type", "binary_tree");
        graph.setProperty("name", "Binary tree with " + levels + " levels");
        return graph;
    }
    
    public static Graph createRandomGraph(int vertices, double edgeProbability) {
        Graph graph = new Graph(vertices);
        Random random = new Random();
        
        for (int i = 0; i < vertices; i++) {
            for (int j = i + 1; j < vertices; j++) {
                if (random.nextDouble() < edgeProbability) {
                    graph.addEdge(i, j);
                }
            }
        }
        
        graph.setProperty("type", "random");
        graph.setProperty("edge_probability", edgeProbability);
        graph.setProperty("name", "Random graph with " + vertices + " vertices");
        return graph;
    }
    
    public double[][] getAdjacencyMatrix() {
        double[][] matrix = new double[vertices][vertices];
        for (int i = 0; i < vertices; i++) {
            for (int j : adjacencyList.get(i)) {
                matrix[i][j] = getWeight(i, j);
            }
        }
        return matrix;
    }
    
    public double[][] getLaplacianMatrix() {
        double[][] laplacian = new double[vertices][vertices];
        
        // Diagonal: degrees
        for (int i = 0; i < vertices; i++) {
            laplacian[i][i] = getDegree(i);
        }
        
        // Off-diagonal: -1 for edges
        for (int i = 0; i < vertices; i++) {
            for (int j : adjacencyList.get(i)) {
                laplacian[i][j] = -getWeight(i, j);
            }
        }
        
        return laplacian;
    }
    
    public boolean isConnected() {
        if (vertices == 0) return true;
        
        boolean[] visited = new boolean[vertices];
        Queue<Integer> queue = new LinkedList<>();
        queue.add(0);
        visited[0] = true;
        
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int neighbor : getNeighbors(current)) {
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    queue.add(neighbor);
                }
            }
        }
        
        for (boolean v : visited) {
            if (!v) return false;
        }
        return true;
    }
    
    public List<Integer> bfs(int start) {
        List<Integer> traversal = new ArrayList<>();
        boolean[] visited = new boolean[vertices];
        Queue<Integer> queue = new LinkedList<>();
        
        queue.add(start);
        visited[start] = true;
        
        while (!queue.isEmpty()) {
            int current = queue.poll();
            traversal.add(current);
            
            for (int neighbor : getNeighbors(current)) {
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    queue.add(neighbor);
                }
            }
        }
        
        return traversal;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Graph with ").append(vertices).append(" vertices and ")
          .append(getEdges()).append(" edges\n");
        sb.append("Type: ").append(properties.getOrDefault("type", "custom")).append("\n");
        
        if (vertices <= 10) {
            sb.append("Adjacency list:\n");
            for (int i = 0; i < vertices; i++) {
                sb.append(i).append(": ").append(adjacencyList.get(i)).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    // Inner class for graph edges with weights
    public static class Edge {
        public final int from;
        public final int to;
        public final double weight;
        
        public Edge(int from, int to, double weight) {
            this.from = from;
            this.to = to;
            this.weight = weight;
        }
        
        public Edge(int from, int to) {
            this(from, to, 1.0);
        }
    }
}
public class QuantumWalk {
    
	
    public static QuantumEvent[] createContinuousTimeWalk(
        Qubit[] positionQubits, // Position register
        Qubit coinQubit,        // Coin qubit (for discrete-time)
        Graph graph,
        double time
    ) {
        // |ψ(t)> = e^{-iHt}|ψ(0)> with H the graph adjacency (or a line when graph is null)
        List<QuantumEvent> circuit = new ArrayList<>();
        int n = positionQubits.length;
        if (n <= 0 || n > 8) {
            throw new IllegalArgumentException("Continuous-time walk supports 1 to 8 position qubits");
        }
        int dimension = 1 << n;
        
        ComplexMatrix adjacency = new ComplexMatrix(dimension, dimension);
        if (graph != null) {
            int graphDimension = Math.min(graph.getVertices(), dimension);
            for (int i = 0; i < graphDimension; i++) {
                for (int j : graph.getNeighbors(i)) {
                    if (j < graphDimension) {
                        adjacency.set(i, j, new Complex(graph.getWeight(i, j), 0));
                    }
                }
            }
        } else {
            for (int i = 0; i < dimension - 1; i++) {
                adjacency.set(i, i + 1, Complex.ONE);
                adjacency.set(i + 1, i, Complex.ONE);
            }
        }
        
        ComplexMatrix unitary = expMinusIHt(adjacency, time);
        QuantumGate walkGate;
        if (n == 1) {
            walkGate = new SingleQubitGate("CTQW", unitary, positionQubits[0]);
        } else {
            walkGate = new MultiQubitGate("CTQW", unitary, positionQubits, 1.0);
        }
        
        circuit.add(EventBuilderHelper.createEvent(
            "CTQW", walkGate, positionQubits, new Qubit[0]));
        
        if (coinQubit != null) {
            circuit.add(EventBuilderHelper.createSingleQubitGate(
                "H_coin", SingleQubitGate.createHadamard(coinQubit), coinQubit));
        }
        
        return circuit.toArray(new QuantumEvent[0]);
    }
    
    private static ComplexMatrix expMinusIHt(ComplexMatrix hamiltonian, double time) {
        int dimension = hamiltonian.rows();
        ComplexMatrix[] eigen = hamiltonian.eigenDecomposition();
        ComplexMatrix eigenvalues = eigen[0];
        ComplexMatrix eigenvectors = eigen[1];
        
        ComplexMatrix phases = new ComplexMatrix(dimension, dimension);
        for (int i = 0; i < dimension; i++) {
            double energy = eigenvalues.get(i, i).real();
            phases.set(i, i, new Complex(Math.cos(-energy * time), Math.sin(-energy * time)));
        }
        
        return eigenvectors.multiply(phases).multiply(eigenvectors.adjoint());
    }
    
    public static QuantumEvent[] createDiscreteTimeWalk(
        Qubit[] positionQubits,
        Qubit coinQubit,
        int steps
    ) {
        // Each step: |ψ'> = S(CxI)|ψ> on a cycle of 2^n positions.
        if (positionQubits.length <= 0 || positionQubits.length > 8) {
            throw new IllegalArgumentException("Discrete-time walk supports 1 to 8 position qubits");
        }
        if (steps < 0) {
            throw new IllegalArgumentException("Step count cannot be negative");
        }
        
        List<QuantumEvent> circuit = new ArrayList<>();
        int dimension = 1 << positionQubits.length;
        
        circuit.add(EventBuilderHelper.createSingleQubitGate(
            "H_coin_init", SingleQubitGate.createHadamard(coinQubit), coinQubit));
        
        for (int step = 0; step < steps; step++) {
            circuit.add(EventBuilderHelper.createSingleQubitGate(
                "Coin_H_" + step, SingleQubitGate.createHadamard(coinQubit), coinQubit));
            
            QuantumGate shift = createCycleShiftGate(positionQubits, coinQubit, dimension);
            circuit.add(EventBuilderHelper.createEvent(
                "Shift_" + step, shift, positionQubits, new Qubit[]{coinQubit}));
        }
        
        return circuit.toArray(new QuantumEvent[0]);
    }
    
    private static QuantumGate createCycleShiftGate(
        Qubit[] positions, Qubit coin, int dimension) {
        int fullDimension = dimension * 2;
        ComplexMatrix matrix = new ComplexMatrix(fullDimension, fullDimension);
        
        for (int coinValue = 0; coinValue < 2; coinValue++) {
            for (int position = 0; position < dimension; position++) {
                int shifted = coinValue == 0
                    ? (position + 1) % dimension
                    : (position - 1 + dimension) % dimension;
                matrix.set(coinValue * dimension + shifted, coinValue * dimension + position, Complex.ONE);
            }
        }
        
        return new MultiQubitGate("CycleShift", matrix, positions, new Qubit[]{coin}, 1.0);
    }
}