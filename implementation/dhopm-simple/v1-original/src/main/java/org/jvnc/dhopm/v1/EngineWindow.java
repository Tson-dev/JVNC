package org.jvnc.dhopm.v1;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

final class EngineWindow extends JFrame {
    private static final Path DATASET_DIRECTORY = Path.of("D:\\JVNC\\JVNC\\dataset");
    private final JComboBox<Path> datasets = new JComboBox<>();
    private final JTextField partial = new JTextField("0.25");
    private final JTextField factor = new JTextField("0.9");
    private final JTextField minDo = new JTextField("1e-4");
    private final JTextField epsilon = new JTextField("1e-6");
    private final JTextField limit = new JTextField("0");
    private final JTextArea output = new JTextArea();
    private final JButton construction = new JButton("Construction");
    private final JButton reconstruction = new JButton("Reconstruction");
    private final JButton mining = new JButton("Mining");
    private final JButton addTransaction = new JButton("Add transaction");
    private final JButton resetLog = new JButton("Reset log");
    private final JButton exportLog = new JButton("Export log");

    private ComputeWorkerClient worker;
    private LogSession log = new LogSession();
    private boolean constructed;
    private boolean reconstructed;
    private double lastPartial;
    private double lastFactor;
    private Path activeDataset;
    private int activeLimit;

    EngineWindow() {
        super("DHOPM - V1 Original");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(850, 620);
        setLocationRelativeTo(null);
        buildUi();
        loadDatasets();
        try {
            worker = new ComputeWorkerClient();
            append("Compute worker started. PID=" + worker.processId());
        } catch (IOException ex) {
            append("ERROR: Could not start compute worker: " + ex.getMessage());
            showError(ex);
        }
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                if (worker != null) {
                    worker.close();
                }
            }
        });
        updateButtons();
    }

    private void buildUi() {
        JPanel parameters = new JPanel(new GridLayout(2, 6, 8, 6));
        parameters.setBorder(BorderFactory.createTitledBorder("Dataset and parameters"));
        parameters.add(new JLabel("Dataset"));
        parameters.add(new JLabel("partial"));
        parameters.add(new JLabel("factor (f)"));
        parameters.add(new JLabel("minDO (metadata only)"));
        parameters.add(new JLabel("epsilon (metadata only)"));
        parameters.add(new JLabel("Transaction limit"));
        parameters.add(datasets);
        parameters.add(partial);
        parameters.add(factor);
        parameters.add(minDo);
        parameters.add(epsilon);
        parameters.add(limit);

        JPanel actions = new JPanel(new GridLayout(1, 6, 8, 6));
        actions.add(construction);
        actions.add(reconstruction);
        actions.add(mining);
        actions.add(addTransaction);
        actions.add(resetLog);
        actions.add(exportLog);

        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);
        add(parameters, BorderLayout.NORTH);
        add(new JScrollPane(output), BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        construction.addActionListener(event -> startConstruction());
        reconstruction.addActionListener(event -> startReconstruction());
        mining.addActionListener(event -> startMining());
        addTransaction.addActionListener(event -> addTransaction());
        resetLog.addActionListener(event -> resetLog());
        exportLog.addActionListener(event -> exportLog());
        for (JTextField field : List.of(partial, factor, minDo, epsilon, limit)) {
            field.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent event) {
                    updateButtons();
                }

                @Override
                public void removeUpdate(DocumentEvent event) {
                    updateButtons();
                }

                @Override
                public void changedUpdate(DocumentEvent event) {
                    updateButtons();
                }
            });
        }
    }

    private void loadDatasets() {
        try {
            if (!Files.isDirectory(DATASET_DIRECTORY)) {
                throw new IOException("Dataset directory does not exist: " + DATASET_DIRECTORY);
            }
            try (var files = Files.list(DATASET_DIRECTORY)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".dat"))
                        .sorted()
                        .forEach(datasets::addItem);
            }
        } catch (IOException ex) {
            showError(ex);
        }
    }

    private void startConstruction() {
        Path selected = (Path) datasets.getSelectedItem();
        if (selected == null) {
            showError(new IllegalStateException("Select a .dat dataset first."));
            return;
        }
        int maxTransactions;
        try {
            maxTransactions = Integer.parseInt(limit.getText().trim());
            if (maxTransactions < 0) {
                throw new IllegalArgumentException("Transaction limit cannot be negative.");
            }
        } catch (RuntimeException ex) {
            showError(ex);
            return;
        }
        DhopmEngine.Parameters params;
        try {
            params = parameters();
        } catch (RuntimeException ex) {
            showError(ex);
            return;
        }
        boolean newDataEpoch = constructed
                && (!Objects.equals(selected, activeDataset) || maxTransactions != activeLimit);
        if (worker == null) {
            showError(new IllegalStateException("Compute worker is unavailable."));
            return;
        }
        if (newDataEpoch && worker.pendingCount() > 0) {
            int discard = JOptionPane.showConfirmDialog(this,
                    "Pending transactions belong to the previous dataset/limit. Discard them?",
                    "Change data epoch", JOptionPane.YES_NO_OPTION);
            if (discard != JOptionPane.YES_OPTION) {
                return;
            }
        }
        if (constructed && !newDataEpoch && worker.pendingCount() == 0) {
            showError(new IllegalStateException("There are no new queued transactions to construct."));
            return;
        }
        if (log.hasUnprocessedLogs()) {
            int choice = JOptionPane.showOptionDialog(this,
                    "Bạn còn log cũ, có muốn lưu lại không?",
                    "Log cũ", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, new Object[]{"Yes", "No"}, "Yes");
            if (choice == JOptionPane.YES_OPTION) {
                try {
                    Path exported = log.export();
                    log.markHandled();
                    append("Exported prior log: " + exported);
                } catch (IOException ex) {
                    showError(ex);
                    return;
                }
            } else if (choice == JOptionPane.NO_OPTION) {
                log.removeUnprocessedMiningPhases();
            } else {
                return;
            }
        }
        boolean firstConstruction = !constructed || newDataEpoch;
        if (newDataEpoch) {
            constructed = false;
            reconstructed = false;
            activeDataset = null;
        }
        runPhase("Construction", () -> {
            if (newDataEpoch) {
                if (worker.pendingCount() > 0) {
                    worker.discardPendingTransactions();
                }
                worker.resetEpoch();
            }
            ComputeWorkerClient.WorkerResult result = worker.construct(
                    firstConstruction ? selected : null, maxTransactions, params);
            constructed = true;
            activeDataset = selected;
            activeLimit = maxTransactions;
            reconstructed = false;
            lastPartial = Double.NaN;
            lastFactor = Double.NaN;
            return result;
        }, params);
    }

    private void startReconstruction() {
        try {
            DhopmEngine.Parameters params = parameters();
            if (params.factor() == 1.0) {
                JOptionPane.showMessageDialog(this,
                        "f=1 has no decay over time. This is an extended boundary case.",
                        "Parameter warning", JOptionPane.WARNING_MESSAGE);
            }
            reconstructed = false;
            runPhase("Reconstruction", () -> worker.reconstruct(params), params);
        } catch (RuntimeException ex) {
            showError(ex);
        }
    }

    private void startMining() {
        try {
            runPhase("Mining", worker::mine, parameters());
        } catch (RuntimeException ex) {
            showError(ex);
        }
    }

    private void addTransaction() {
        String transaction = JOptionPane.showInputDialog(this, "Items separated by spaces:");
        if (transaction == null) {
            return;
        }
        try {
            worker.addPendingTransaction(transaction);
            reconstructed = false;
            append("Queued transaction for next Construction.");
            updateButtons();
        } catch (RuntimeException ex) {
            showError(ex);
        }
    }

    private void resetLog() {
        log = new LogSession();
        output.setText("");
        append("In-memory log reset. Algorithm data is unchanged.");
        updateButtons();
    }

    private void exportLog() {
        if (log.events().isEmpty()) {
            return;
        }
        try {
            Path exported = log.export();
            log.markHandled();
            append("Exported log: " + exported);
        } catch (IOException ex) {
            showError(ex);
        }
        updateButtons();
    }

    private void runPhase(String label, PhaseAction action, DhopmEngine.Parameters parameters) {
        setBusy(true);
        new SwingWorker<ComputeWorkerClient.WorkerResult, Void>() {
            @Override
            protected ComputeWorkerClient.WorkerResult doInBackground() throws Exception {
                return action.run();
            }

            @Override
            protected void done() {
                try {
                    ComputeWorkerClient.WorkerResult workerResult = get();
                    DhopmEngine.PhaseResult result = workerResult.result();
                    record(result, parameters, workerResult.metrics());
                    if ("RECONSTRUCTION".equals(result.phase())) {
                        lastPartial = parameters.partial();
                        lastFactor = parameters.factor();
                    }
                    reconstructed = "RECONSTRUCTION".equals(result.phase())
                            || ("MINING".equals(result.phase()) && reconstructed);
                    append(label + " complete in "
                            + String.format("%.3f", result.elapsedNanos() / 1_000_000.0)
                            + " ms. TL=" + result.snapshot().lastTid()
                            + (result.patterns().isEmpty() ? "" : ", DOP count=" + result.patterns().size()));
                    if (!result.patterns().isEmpty()) {
                        result.patterns().stream().limit(100)
                                .forEach(pattern -> append(pattern.key() + " DO="
                                        + String.format("%.6f", pattern.dampedOccupancy())));
                    }
                } catch (Exception ex) {
                    showError(ex.getCause() == null ? ex : ex.getCause());
                } finally {
                    setBusy(false);
                    updateButtons();
                }
            }
        }.execute();
    }

    private void record(DhopmEngine.PhaseResult result, DhopmEngine.Parameters parameters,
                        ComputeWorkerClient.Metrics metrics) {
        String datasetName = datasets.getSelectedItem() == null ? "" :
                ((Path) datasets.getSelectedItem()).getFileName().toString();
        log.add(LogSession.event(result, parameters, datasetName, metrics));
        append(result.phase() + " snapshot: " + result.snapshot().transactionCount()
                + " transactions, " + result.snapshot().itemCount() + " items.");
        updateButtons();
    }

    private double parseFinite(JTextField field, String name) {
        double value = Double.parseDouble(field.getText().trim());
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite.");
        }
        return value;
    }

    private int parseInt(JTextField field, String name) {
        return Integer.parseInt(field.getText().trim());
    }

    private DhopmEngine.Parameters parameters() {
        int transactionLimit = parseInt(limit, "limit");
        if (transactionLimit < 0) {
            throw new IllegalArgumentException("limit must be non-negative.");
        }
        return new DhopmEngine.Parameters(parseFinite(partial, "partial"),
                parseFinite(factor, "factor"), parseFinite(minDo, "minDO"),
                parseFinite(epsilon, "epsilon"), transactionLimit);
    }

    private void setBusy(boolean busy) {
        construction.setEnabled(!busy);
        reconstruction.setEnabled(!busy && constructed);
        mining.setEnabled(!busy && reconstructed);
        addTransaction.setEnabled(!busy);
        resetLog.setEnabled(!busy);
        exportLog.setEnabled(!busy && !log.events().isEmpty());
        datasets.setEnabled(!busy);
        partial.setEnabled(!busy);
        factor.setEnabled(!busy);
        minDo.setEnabled(!busy);
        epsilon.setEnabled(!busy);
        limit.setEnabled(!busy);
    }

    private void updateButtons() {
        boolean workerAvailable = worker != null && worker.available();
        construction.setEnabled(workerAvailable);
        reconstruction.setEnabled(workerAvailable && constructed);
        mining.setEnabled(workerAvailable && reconstructed && parametersUnchanged());
        addTransaction.setEnabled(workerAvailable);
        resetLog.setEnabled(!log.events().isEmpty());
        exportLog.setEnabled(!log.events().isEmpty());
        datasets.setEnabled(true);
        partial.setEnabled(true);
        factor.setEnabled(true);
        minDo.setEnabled(true);
        epsilon.setEnabled(true);
        limit.setEnabled(true);
    }

    private boolean parametersUnchanged() {
        try {
            return Double.compare(parseFinite(partial, "partial"), lastPartial) == 0
                    && Double.compare(parseFinite(factor, "factor"), lastFactor) == 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void append(String message) {
        output.append(message + System.lineSeparator());
        output.setCaretPosition(output.getDocument().getLength());
    }

    private void showError(Throwable error) {
        String message = error.getMessage() == null ? error.toString() : error.getMessage();
        append("ERROR: " + message);
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    @FunctionalInterface
    private interface PhaseAction {
        ComputeWorkerClient.WorkerResult run() throws Exception;
    }
}
