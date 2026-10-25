package com.mechalikh.qforge.observer;

import com.mechalikh.qforge.core.SimulationContext;
import com.mechalikh.qforge.core.SimulationResult;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.StateVector;
import com.mechalikh.qforge.state.DensityMatrix;
import com.mechalikh.qforge.math.Complex;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

/**
 * Observer that captures state and event history and renders Java2D images.
 *
 * <p>During a run, {@link #onStateUpdate(QuantumState, QuantumEvent)} stores a
 * {@link StateSnapshot} and, when an event is supplied, an {@link EventRecord}.
 * When the simulation completes, {@link #onCompleted(SimulationResult)} writes
 * {@code final_state.png}, {@code event_timeline.png}, and
 * {@code metrics_plot.png} to the output directory.
 * {@link #generateAnimation(String, int)} additionally writes an animated GIF
 * named after its argument.</p>
 *
 * <p>The default constructor keeps at most 1000 history entries and writes to
 * {@code ./visualizations/}. Directories are created on demand. Rendered images
 * are RGB {@link BufferedImage} instances sized 800 by 600 for state vectors,
 * 600 by 600 for density matrices, and 400 by 300 for unsupported states.</p>
 */
public class VisualizationEngine implements SimulationObserver {
    private final List<StateSnapshot> stateHistory;
    private final List<EventRecord> eventHistory;
    private final int maxHistorySize;
    private boolean captureHistory;
    private String outputDirectory;
    
    /**
     * Creates an engine that keeps up to 1000 history entries, captures history, and
     * writes images to {@code ./visualizations/}.
     */
    public VisualizationEngine() {
        this.stateHistory = Collections.synchronizedList(new ArrayList<>());
        this.eventHistory = Collections.synchronizedList(new ArrayList<>());
        this.maxHistorySize = 1000;
        this.captureHistory = true;
        this.outputDirectory = "./visualizations/";
    }
    
    /**
     * Creates an engine with the supplied history limit and output directory.
     *
     * @param maxHistorySize the maximum number of state and event entries kept per
     *                       history list
     * @param outputDirectory the directory for generated images and animations
     */
    public VisualizationEngine(int maxHistorySize, String outputDirectory) {
        this.stateHistory = Collections.synchronizedList(new ArrayList<>());
        this.eventHistory = Collections.synchronizedList(new ArrayList<>());
        this.maxHistorySize = maxHistorySize;
        this.captureHistory = true;
        this.outputDirectory = outputDirectory;
    }
    
    @Override
    /**
     * Captures a snapshot of the state, records the event when it is non-null, and
     * removes the oldest entry from a history that exceeds the maximum size. Nothing
     * is captured when history capture is disabled.
     *
     * @param state the state after the update
     * @param event the event that produced the update, or {@code null}
     */
    public void onStateUpdate(QuantumState state, QuantumEvent event) {
        if (captureHistory) {
            captureSnapshot(state);
            
            if (event != null) {
                recordEvent(event);
            }
            
            // Limit history size
            if (stateHistory.size() > maxHistorySize) {
                stateHistory.remove(0);
            }
            if (eventHistory.size() > maxHistorySize) {
                eventHistory.remove(0);
            }
        }
    }
    
    @Override
    /**
     * Writes the summary images for the completed simulation. Failures are printed to
     * {@link System#err} and are not propagated.
     *
     * @param result the completed simulation result
     */
    public void onCompleted(SimulationResult result) {
        // Generate final visualizations
        try {
            generateSummaryVisualizations(result);
        } catch (Exception e) {
            System.err.println("Failed to generate visualizations: " + e.getMessage());
        }
    }
    
    /**
     * Appends a snapshot of the state with the current time. The snapshot type is
     * {@link StateType#STATE_VECTOR} for a {@link StateVector} and
     * {@link StateType#DENSITY_MATRIX} for any other state.
     *
     * @param state the state to capture
     */
    public void captureSnapshot(QuantumState state) {
        StateSnapshot snapshot = new StateSnapshot(
            Instant.now(),
            state,
            state instanceof StateVector ? StateType.STATE_VECTOR : StateType.DENSITY_MATRIX
        );
        stateHistory.add(snapshot);
    }
    
    /**
     * Appends an event record. The operation name is the name of the event operation
     * when one is present, otherwise the event type name.
     *
     * @param event the event to record
     */
    public void recordEvent(QuantumEvent event) {
        EventRecord record = new EventRecord(
            Instant.now(),
            event.getType(),
            event.getOperation() != null ? event.getOperation().getName() : event.getType().name(),
            event.getTargetQubits()
        );
        eventHistory.add(record);
    }
    
    /**
     * Renders a state as an image, choosing the renderer from the state type.
     *
     * @param state the state to render
     * @return an 800 by 600 state vector image, a 600 by 600 density matrix heatmap,
     *         or a 400 by 300 placeholder when the type is not supported
     */
    public BufferedImage renderState(QuantumState state) {
        if (state instanceof StateVector) {
            return renderStateVector((StateVector) state);
        } else if (state instanceof DensityMatrix) {
            return renderDensityMatrix((DensityMatrix) state);
        }
        return createPlaceholderImage("Unsupported state type");
    }
    
    /**
     * Renders state vector amplitudes as vertical bars.
     *
     * <p>The image is 800 by 600 pixels. At most the first 50 amplitudes are drawn,
     * each bar height is proportional to the basis state probability, and the bar
     * color encodes the amplitude phase. No text labels are drawn.</p>
     *
     * @param state the state vector to render
     * @return the rendered image
     */
    public BufferedImage renderStateVector(StateVector state) {
        int width = 800;
        int height = 600;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        // Draw state vector visualization
        // This is a simplified visualization - real implementation would be more sophisticated
        Complex[] amplitudes = state.getAmplitudes();
        int numAmplitudes = Math.min(amplitudes.length, 50); // Limit for visualization
        
        // Draw amplitude bars
        int barWidth = width / (numAmplitudes + 2);
        for (int i = 0; i < numAmplitudes; i++) {
            double probability = amplitudes[i].absSquare();
            int barHeight = (int) (probability * height * 0.8);
            
            // Draw bar
            int x = (i + 1) * barWidth;
            int y = height - barHeight;
            
            // Color based on phase
            double phase = amplitudes[i].phase();
            int color = phaseToColor(phase);
            
            for (int bx = x; bx < x + barWidth - 2; bx++) {
                for (int by = y; by < height; by++) {
                    image.setRGB(bx, by, color);
                }
            }
            
            // Draw basis state label
            String basis = Integer.toBinaryString(i);
            // Simple text rendering (in real implementation, use Graphics2D)
        }
        
        return image;
    }
    
    /**
     * Renders a density matrix as a heatmap.
     *
     * <p>The image is 600 by 600 pixels. At most the leading 32 by 32 block is drawn.
     * Cell brightness is the element magnitude normalized by the largest magnitude in
     * that block, and cell hue encodes the element phase.</p>
     *
     * @param densityMatrix the density matrix to render
     * @return the rendered image
     */
    public BufferedImage renderDensityMatrix(DensityMatrix densityMatrix) {
        int width = 600;
        int height = 600;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        // Render density matrix as heatmap
        // This is a simplified visualization
        int matrixSize = Math.min(densityMatrix.getMatrix().rows(), 32); // Limit size for visualization
        int cellSize = Math.min(width, height) / matrixSize;
        
        // Find max magnitude for normalization
        double maxMagnitude = 0.0;
        for (int i = 0; i < matrixSize; i++) {
            for (int j = 0; j < matrixSize; j++) {
                double magnitude = densityMatrix.getMatrix().get(i, j).abs();
                maxMagnitude = Math.max(maxMagnitude, magnitude);
            }
        }
        
        // Draw heatmap
        for (int i = 0; i < matrixSize; i++) {
            for (int j = 0; j < matrixSize; j++) {
                Complex element = densityMatrix.getMatrix().get(i, j);
                double magnitude = element.abs();
                double phase = element.phase();
                
                // Normalize magnitude
                double normalized = maxMagnitude > 0 ? magnitude / maxMagnitude : 0;
                
                // Convert to color (brightness = magnitude, hue = phase)
                int color = magnitudePhaseToColor(normalized, phase);
                
                // Draw cell
                int x = j * cellSize;
                int y = i * cellSize;
                
                for (int dx = 0; dx < cellSize; dx++) {
                    for (int dy = 0; dy < cellSize; dy++) {
                        if (x + dx < width && y + dy < height) {
                            image.setRGB(x + dx, y + dy, color);
                        }
                    }
                }
            }
        }
        
        return image;
    }
    
    /**
     * Writes an image as a PNG file in the output directory, creating the directory
     * when needed.
     *
     * @param image the image to write
     * @param filename the file name, which should end in {@code .png}
     * @throws IOException if the image cannot be written
     */
    public void saveImage(BufferedImage image, String filename) throws IOException {
        File outputDir = new File(outputDirectory);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        File outputFile = new File(outputDir, filename);
        ImageIO.write(image, "PNG", outputFile);
    }
    
    /**
     * Writes an animated GIF built from sampled state history entries.
     *
     * <p>At most about 50 frames are sampled by taking every
     * {@code max(1, historySize / 50)}th snapshot. The GIF uses the supplied frame
     * delay (converted from milliseconds to centiseconds with a minimum of one) and
     * loops indefinitely. The file is written to the output directory as
     * {@code <animationName>.gif}. The method returns without writing a file when the
     * state history is empty.</p>
     *
     * @param animationName the output file name without the {@code .gif} suffix
     * @param frameDelay the delay between frames in milliseconds
     * @throws IOException if the GIF cannot be written
     */
    public void generateAnimation(String animationName, int frameDelay) throws IOException {
        if (stateHistory.isEmpty()) {
            return;
        }
        
        List<BufferedImage> frames = new ArrayList<>();
        int step = Math.max(1, stateHistory.size() / 50);
        for (int i = 0; i < stateHistory.size(); i += step) {
            frames.add(renderState(stateHistory.get(i).getState()));
        }
        if (frames.isEmpty()) {
            return;
        }
        
        File outputDir = new File(outputDirectory);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        ImageWriter writer = ImageIO.getImageWritersBySuffix("gif").next();
        File outputFile = new File(outputDir, animationName + ".gif");
        try (ImageOutputStream output = ImageIO.createImageOutputStream(outputFile)) {
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            
            for (BufferedImage frame : frames) {
                ImageWriteParam params = writer.getDefaultWriteParam();
                ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(frame);
                IIOMetadata metadata = writer.getDefaultImageMetadata(type, params);
                configureGifMetadata(metadata, frameDelay);
                writer.writeToSequence(new IIOImage(frame, null, metadata), params);
            }
            
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        
        System.out.println("Animation saved to: " + outputFile.getAbsolutePath());
    }
    
    /**
     * Sets GIF frame delay, disposal, and infinite looping metadata.
     *
     * @param metadata the image metadata to configure
     * @param frameDelayMs the frame delay in milliseconds, converted to centiseconds
     * @throws IOException if the native metadata format cannot be read
     */
    private void configureGifMetadata(IIOMetadata metadata, int frameDelayMs) throws IOException {
        String format = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
        
        IIOMetadataNode control = getMetadataNode(root, "GraphicControlExtension");
        control.setAttribute("disposalMethod", "none");
        control.setAttribute("userInputFlag", "FALSE");
        control.setAttribute("transparentColorFlag", "FALSE");
        control.setAttribute("delayTime", String.valueOf(Math.max(1, frameDelayMs / 10)));
        control.setAttribute("transparentColorIndex", "0");
        
        IIOMetadataNode applicationExtensions = getMetadataNode(root, "ApplicationExtensions");
        IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
        loop.setAttribute("applicationID", "NETSCAPE");
        loop.setAttribute("authenticationCode", "2.0");
        loop.setUserObject(new byte[]{0x1, 0x0, 0x0});
        applicationExtensions.appendChild(loop);
        
        metadata.setFromTree(format, root);
    }
    
    /**
     * Finds a direct child metadata node by name, appending a new node when none
     * exists.
     *
     * @param root the parent node
     * @param name the node name to find
     * @return the existing or newly appended node
     */
    private IIOMetadataNode getMetadataNode(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equalsIgnoreCase(name)) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }
    
    /**
     * Writes the final state image, the event timeline, and the metrics plot.
     *
     * <p>The final state image is written only when the result has a non-null final
     * state. The timeline and metrics plot are written only when the corresponding
     * history is non-empty.</p>
     *
     * @param result the completed simulation result
     * @throws IOException if an image cannot be written
     */
    public void generateSummaryVisualizations(SimulationResult result) throws IOException {
        System.out.println("Generating summary visualizations...");
        
        if (result.getFinalState() != null) {
            BufferedImage finalStateImg = renderState(result.getFinalState());
            saveImage(finalStateImg, "final_state.png");
        }
        
        generateEventTimeline();
        generateMetricsPlot();
        
        System.out.println("Visualizations saved to: " + outputDirectory);
    }
    
    /**
     * Writes {@code event_timeline.png} with one labeled row per recorded event. The
     * method returns without writing a file when the event history is empty.
     *
     * @throws IOException if the image cannot be written
     */
    private void generateEventTimeline() throws IOException {
        if (eventHistory.isEmpty()) {
            return;
        }
        
        int width = 1000;
        int rowHeight = 24;
        int height = 100 + eventHistory.size() * rowHeight;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.DARK_GRAY);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 20));
        graphics.drawString("Event Timeline", 30, 32);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 12));
        
        long start = eventHistory.get(0).getTimestamp().toEpochMilli();
        long end = eventHistory.get(eventHistory.size() - 1).getTimestamp().toEpochMilli();
        long span = Math.max(1, end - start);
        
        graphics.setColor(new Color(220, 220, 220));
        graphics.drawLine(180, 55, 180, height - 20);
        
        for (int i = 0; i < eventHistory.size(); i++) {
            EventRecord event = eventHistory.get(i);
            int y = 72 + i * rowHeight;
            long offset = event.getTimestamp().toEpochMilli() - start;
            int markerY = 55 + (int) ((height - 75) * (offset / (double) span));
            
            graphics.setColor(new Color(70, 110, 180));
            graphics.fillOval(175, Math.max(55, markerY), 10, 10);
            graphics.setColor(Color.BLACK);
            graphics.drawString(
                String.format(Locale.ROOT, "%s  %s  (%d target%s, +%d ms)",
                    event.getType(),
                    event.getOperationName(),
                    event.getTargetQubits().length,
                    event.getTargetQubits().length == 1 ? "" : "s",
                    offset),
                210, y);
        }
        
        graphics.dispose();
        saveImage(image, "event_timeline.png");
    }
    
    /**
     * Writes {@code metrics_plot.png} showing state purity per snapshot. Purity values
     * are clamped to the range 0 to 1 for plotting. The method returns without writing
     * a file when the state history is empty.
     *
     * @throws IOException if the image cannot be written
     */
    private void generateMetricsPlot() throws IOException {
        if (stateHistory.isEmpty()) {
            return;
        }
        
        int width = 900;
        int height = 420;
        int left = 70;
        int right = 30;
        int top = 50;
        int bottom = 60;
        int plotWidth = width - left - right;
        int plotHeight = height - top - bottom;
        
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        
        graphics.setColor(Color.DARK_GRAY);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 18));
        graphics.drawString("State Purity over Time", left, 30);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 11));
        
        graphics.setColor(Color.GRAY);
        graphics.drawLine(left, top, left, top + plotHeight);
        graphics.drawLine(left, top + plotHeight, left + plotWidth, top + plotHeight);
        for (int tick = 0; tick <= 5; tick++) {
            int y = top + plotHeight - tick * plotHeight / 5;
            graphics.drawLine(left - 5, y, left, y);
            graphics.drawString(String.format(Locale.ROOT, "%.1f", tick / 5.0), 35, y + 4);
        }
        
        graphics.setColor(new Color(70, 110, 180));
        graphics.setStroke(new BasicStroke(2f));
        int points = stateHistory.size();
        int previousX = left;
        int previousY = top + plotHeight
            - (int) (stateHistory.get(0).getState().getPurity() * plotHeight);
        for (int i = 0; i < points; i++) {
            int x = points == 1 ? left : left + i * plotWidth / (points - 1);
            double purity = Math.max(0.0, Math.min(1.0,
                stateHistory.get(i).getState().getPurity()));
            int y = top + plotHeight - (int) (purity * plotHeight);
            if (i > 0) {
                graphics.drawLine(previousX, previousY, x, y);
            }
            graphics.fillOval(x - 2, y - 2, 5, 5);
            previousX = x;
            previousY = y;
        }
        
        graphics.setColor(Color.BLACK);
        graphics.drawString("snapshot index", left + plotWidth / 2 - 40, height - 20);
        graphics.drawString("purity", 10, top - 10);
        
        graphics.dispose();
        saveImage(image, "metrics_plot.png");
    }
    
    /**
     * Maps a phase angle to an RGB color with fixed saturation and value.
     *
     * @param phase the phase angle in radians
     * @return the packed RGB value
     */
    private int phaseToColor(double phase) {
        // Convert phase angle to RGB color (HSV to RGB)
        double hue = (phase + Math.PI) / (2 * Math.PI); // Normalize to [0, 1]
        return hsvToRgb(hue, 0.8, 0.9);
    }
    
    /**
     * Maps a normalized magnitude and a phase angle to an RGB color.
     *
     * @param magnitude the normalized magnitude, used to scale brightness
     * @param phase the phase angle in radians
     * @return the packed RGB value
     */
    private int magnitudePhaseToColor(double magnitude, double phase) {
        // Convert magnitude and phase to color
        double hue = (phase + Math.PI) / (2 * Math.PI); // Normalize to [0, 1]
        double saturation = 0.8;
        double value = 0.3 + 0.7 * magnitude; // Brightness proportional to magnitude
        
        return hsvToRgb(hue, saturation, value);
    }
    
    /**
     * Converts an HSV color to a packed RGB value.
     *
     * @param h the hue, normally in the range 0 to 1
     * @param s the saturation
     * @param v the value (brightness)
     * @return the packed RGB value
     */
    private int hsvToRgb(double h, double s, double v) {
        // Convert HSV to RGB
        int r, g, b;
        
        int i = (int) (h * 6);
        double f = h * 6 - i;
        double p = v * (1 - s);
        double q = v * (1 - f * s);
        double t = v * (1 - (1 - f) * s);
        
        switch (i % 6) {
            case 0: r = (int) (v * 255); g = (int) (t * 255); b = (int) (p * 255); break;
            case 1: r = (int) (q * 255); g = (int) (v * 255); b = (int) (p * 255); break;
            case 2: r = (int) (p * 255); g = (int) (v * 255); b = (int) (t * 255); break;
            case 3: r = (int) (p * 255); g = (int) (q * 255); b = (int) (v * 255); break;
            case 4: r = (int) (t * 255); g = (int) (p * 255); b = (int) (v * 255); break;
            case 5: r = (int) (v * 255); g = (int) (p * 255); b = (int) (q * 255); break;
            default: r = 0; g = 0; b = 0;
        }
        
        return (r << 16) | (g << 8) | b;
    }
    
    /**
     * Creates a white placeholder image.
     *
     * @param text the text that would be shown; the current implementation does not
     *             draw it
     * @return a 400 by 300 white image
     */
    private BufferedImage createPlaceholderImage(String text) {
        int width = 400;
        int height = 300;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        // Fill with background
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, 0xFFFFFF); // White background
            }
        }
        
        // In real implementation, draw text using Graphics2D
        return image;
    }
    
    /**
     * Returns the captured state snapshots.
     *
     * @return a new list containing the snapshots
     */
    public List<StateSnapshot> getStateHistory() {
        return new ArrayList<>(stateHistory);
    }
    
    /**
     * Returns the captured event records.
     *
     * @return a new list containing the event records
     */
    public List<EventRecord> getEventHistory() {
        return new ArrayList<>(eventHistory);
    }
    
    /**
     * Enables or disables history capture during state update callbacks.
     *
     * @param capture {@code true} to capture history, {@code false} to stop capturing
     */
    public void setCaptureHistory(boolean capture) {
        this.captureHistory = capture;
    }
    
    /**
     * Sets the directory for generated images and animations.
     *
     * @param directory the output directory; it is created on demand when an image is
     *                  written
     */
    public void setOutputDirectory(String directory) {
        this.outputDirectory = directory;
    }
    
    /**
     * Removes all captured state snapshots and event records.
     */
    public void clearHistory() {
        stateHistory.clear();
        eventHistory.clear();
    }
    
    // Inner classes
    /**
     * Captured state with the time it was recorded.
     *
     * <p>The snapshot keeps a reference to the supplied {@link QuantumState}; it does
     * not copy the state.</p>
     */
    public static class StateSnapshot {
        private final Instant timestamp;
        private final QuantumState state;
        private final StateType type;
        
        /**
         * Creates a state snapshot.
         *
         * @param timestamp the capture time
         * @param state the captured state
         * @param type the state type
         */
        public StateSnapshot(Instant timestamp, QuantumState state, StateType type) {
            this.timestamp = timestamp;
            this.state = state;
            this.type = type;
        }
        
        /**
         * Returns the capture time.
         *
         * @return the timestamp
         */
        public Instant getTimestamp() { return timestamp; }
        /**
         * Returns the captured state.
         *
         * @return the state
         */
        public QuantumState getState() { return state; }
        /**
         * Returns the state type.
         *
         * @return the type
         */
        public StateType getType() { return type; }
        
        /**
         * Returns a formatted description of the snapshot.
         *
         * @return a string with the timestamp, type, and qubit count
         */
        @Override
        public String toString() {
            return String.format(Locale.ROOT, 
                "StateSnapshot[time=%s, type=%s, qubits=%d]",
                timestamp, type, state.getNumQubits()
            );
        }
    }
    
    /**
     * Captured event with the time it was recorded.
     *
     * <p>The target qubit array is copied on construction and on each call to
     * {@link #getTargetQubits()}.</p>
     */
    public static class EventRecord {
        private final Instant timestamp;
        private final com.mechalikh.qforge.events.EventType type;
        private final String operationName;
        private final com.mechalikh.qforge.state.Qubit[] targetQubits;
        
        /**
         * Creates an event record.
         *
         * @param timestamp the capture time
         * @param type the event type
         * @param operationName the operation name, or the event type name
         * @param targetQubits the target qubits, which are copied
         */
        public EventRecord(Instant timestamp, com.mechalikh.qforge.events.EventType type,
                          String operationName, com.mechalikh.qforge.state.Qubit[] targetQubits) {
            this.timestamp = timestamp;
            this.type = type;
            this.operationName = operationName;
            this.targetQubits = targetQubits.clone();
        }
        
        /**
         * Returns the capture time.
         *
         * @return the timestamp
         */
        public Instant getTimestamp() { return timestamp; }
        /**
         * Returns the event type.
         *
         * @return the event type
         */
        public com.mechalikh.qforge.events.EventType getType() { return type; }
        /**
         * Returns the operation name.
         *
         * @return the operation name or the event type name
         */
        public String getOperationName() { return operationName; }
        /**
         * Returns the target qubits.
         *
         * @return a copy of the target qubit array
         */
        public com.mechalikh.qforge.state.Qubit[] getTargetQubits() { return targetQubits.clone(); }
        
        /**
         * Returns a formatted description of the record.
         *
         * @return a string with the timestamp, type, operation name, and target count
         */
        @Override
        public String toString() {
            return String.format(Locale.ROOT, 
                "EventRecord[time=%s, type=%s, operation='%s', targets=%d]",
                timestamp, type, operationName, targetQubits.length
            );
        }
    }
    
    /**
     * Kind of state held by a {@link StateSnapshot}.
     */
    public enum StateType {
        /**
         * A {@link StateVector} pure state.
         */
        STATE_VECTOR,
        /**
         * A {@link DensityMatrix}, used for any state that is not a state vector.
         */
        DENSITY_MATRIX
    }
}