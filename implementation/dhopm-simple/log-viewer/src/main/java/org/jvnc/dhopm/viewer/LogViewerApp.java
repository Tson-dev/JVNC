package org.jvnc.dhopm.viewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LogViewerApp extends Application {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final ComboBox<Integer> phaseSelector = new ComboBox<>();
    private final Label fileLabel = new Label("No log selected");
    private final Label phaseDetails = new Label();
    private final Label supplementStatus = new Label();
    private final Label duboSummary = new Label();
    private final Label dopStatus = new Label();
    private final Button openButton = new Button("Open log...");
    private final Button recomputeButton = new Button("List pruned DUBO descendants");
    private final Button exportSupplementalButton = new Button("Export supplemental results...");
    private final TableView<LogDocument.PhaseEvent> summaryTable = new TableView<>();
    private final TableView<String[]> transactionTable = new TableView<>();
    private final TableView<String[]> duboTable = new TableView<>();
    private final TableView<String[]> dopTable = new TableView<>();
    private final TableView<DhoListReconstructor.Node> dhoListTable = new TableView<>();
    private final TableView<String[]> dhoEntryTable = new TableView<>();
    private final VBox charts = new VBox(10);
    private LogDocument document;
    private Path sourcePath;
    private List<DuboSupplementer.PrunedPattern> supplementalResults = List.of();
    private Stage owner;
    private Task<List<DhoListReconstructor.Node>> dhoListTask;
    private int selectedPhaseGeneration;

    @Override
    public void start(Stage stage) {
        owner = stage;
        openButton.setOnAction(event -> openLog(stage));
        phaseSelector.setDisable(true);
        phaseSelector.setOnAction(event -> showSelectedPhase());

        HBox toolbar = new HBox(10, openButton, fileLabel, new Label("Selected phase:"), phaseSelector);
        toolbar.setPadding(new Insets(10));

        TabPane tabs = new TabPane(
                new Tab("Overview", createOverview()),
                new Tab("Graphs", createGraphsPane()),
                new Tab("Snapshot", createTablePane(transactionTable, List.of("TID", "State", "Items"))),
                new Tab("DHOList", createDhoListPane()),
                new Tab("DUBO comparisons", createDuboPane()),
                new Tab("DOP results", createDopPane()));
        tabs.getTabs().forEach(tab -> tab.setClosable(false));

        BorderPane root = new BorderPane(tabs);
        root.setTop(toolbar);
        stage.setTitle("DHOPM Log Viewer");
        stage.setScene(new Scene(root, 1100, 760));
        stage.show();
    }

    private VBox createOverview() {
        configureSummaryTable();
        VBox box = new VBox(10, summaryTable, phaseDetails);
        box.setPadding(new Insets(10));
        VBox.setVgrow(summaryTable, Priority.ALWAYS);
        return box;
    }

    private VBox createGraphsPane() {
        ScrollPane scrollPane = new ScrollPane(charts);
        scrollPane.setFitToWidth(true);
        VBox pane = new VBox(scrollPane);
        pane.setPadding(new Insets(10));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        return pane;
    }

    private static VBox createTablePane(TableView<String[]> table, List<String> headers) {
        configureStringTable(table, headers);
        VBox pane = new VBox(table);
        pane.setPadding(new Insets(10));
        VBox.setVgrow(table, Priority.ALWAYS);
        return pane;
    }

    private void configureSummaryTable() {
        TableColumn<LogDocument.PhaseEvent, String> phase = column("Phase", event -> event.phase());
        TableColumn<LogDocument.PhaseEvent, String> dataset = column("Dataset", event -> event.datasetName());
        TableColumn<LogDocument.PhaseEvent, String> minSup = column("minSup", event -> format(event.minSup()));
        TableColumn<LogDocument.PhaseEvent, String> wall = column("Time (ms)",
                event -> formatMillis(event.computeWallNanos()));
        TableColumn<LogDocument.PhaseEvent, String> heapStart = column("Heap start (MB)",
                event -> formatMegabytes(event.heapUsedStartBytes()));
        TableColumn<LogDocument.PhaseEvent, String> heapEnd = column("Heap end (MB)",
                event -> formatMegabytes(event.heapUsedEndBytes()));
        TableColumn<LogDocument.PhaseEvent, String> peak = column("Heap peak (MB)",
                event -> formatMegabytes(event.heapPeakBytes()));
        TableColumn<LogDocument.PhaseEvent, String> cpu = column("Worker CPU (ms)",
                event -> formatMillis(event.workerCpuNanos()));
        TableColumn<LogDocument.PhaseEvent, String> rssStart = column("RSS start (MB)",
                event -> formatNullableMegabytes(event.rssStartBytes()));
        TableColumn<LogDocument.PhaseEvent, String> rssEnd = column("RSS end (MB)",
                event -> formatNullableMegabytes(event.rssEndBytes()));
        TableColumn<LogDocument.PhaseEvent, String> rssPeak = column("RSS sampled peak (MB)",
                event -> formatNullableMegabytes(event.rssPeakSampledBytes()));
        summaryTable.getColumns().setAll(List.of(phase, dataset, minSup, wall, heapStart, heapEnd,
                peak, cpu, rssStart, rssEnd, rssPeak));
        summaryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        summaryTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, selected) -> {
                    if (selected != null && document != null) {
                        phaseSelector.getSelectionModel().select(document.events().indexOf(selected));
                    }
                });
    }

    private static TableColumn<LogDocument.PhaseEvent, String> column(
            String header, java.util.function.Function<LogDocument.PhaseEvent, String> value) {
        TableColumn<LogDocument.PhaseEvent, String> column = new TableColumn<>(header);
        column.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private static void configureStringTable(TableView<String[]> table, List<String> headers) {
        List<TableColumn<String[], String>> columns = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            int index = i;
            TableColumn<String[], String> column = new TableColumn<>(headers.get(i));
            column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                    index < cell.getValue().length ? cell.getValue()[index] : ""));
            columns.add(column);
        }
        table.getColumns().setAll(columns);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void openLog(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select a DHOPM log");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Log files", "*.txt", "*.json"));
        Path initialDirectory = initialProjectDirectory();
        if (java.nio.file.Files.isDirectory(initialDirectory)) {
            chooser.setInitialDirectory(initialDirectory.toFile());
        }
        java.io.File selected = chooser.showOpenDialog(stage);
        if (selected == null) {
            return;
        }
        try {
            document = LogDocument.read(selected.toPath());
            sourcePath = selected.toPath();
            fileLabel.setText(selected.getAbsolutePath() + "  [" + document.engineVersion() + "]");
            phaseSelector.setItems(FXCollections.observableArrayList(
                    java.util.stream.IntStream.range(0, document.events().size()).boxed().toList()));
            phaseSelector.setDisable(false);
            phaseSelector.getSelectionModel().select(0);
            summaryTable.setItems(FXCollections.observableArrayList(document.events()));
            buildCharts();
            showSelectedPhase();
        } catch (IOException | RuntimeException ex) {
            showError(stage, "Could not open log: " + ex.getMessage());
        }
    }

    private void showSelectedPhase() {
        if (document == null || phaseSelector.getValue() == null) {
            return;
        }
        int index = phaseSelector.getValue();
        if (index < 0 || index >= document.events().size()) {
            return;
        }
        LogDocument.PhaseEvent event = document.events().get(index);
        JsonObject snapshot = event.snapshot();
        JsonObject parameters = event.parameters();
        phaseDetails.setText("Phase " + (index + 1) + "/" + document.events().size()
                + " | completed " + event.completedAt()
                + " | TL=" + value(snapshot, "lastTid")
                + " | transactions total/active=" + value(snapshot, "transactionCount")
                + "/" + value(snapshot, "activeTransactionCount")
                + " | items=" + value(snapshot, "itemCount")
                + " | parameters: partial=" + value(parameters, "partial")
                + ", f=" + value(parameters, "factor")
                + ", minDO=" + value(parameters, "minDo")
                + ", epsilon=" + value(parameters, "epsilon")
                + ", limit=" + value(parameters, "limit")
                + " | heap start/end/peak=" + formatMegabytes(event.heapUsedStartBytes()) + "/"
                + formatMegabytes(event.heapUsedEndBytes()) + "/" + formatMegabytes(event.heapPeakBytes())
                + " MB | worker PID=" + (event.workerPid() < 0 ? "n/a" : event.workerPid())
                + ", startup=" + formatMillis(event.workerStartupNanos()) + " ms"
                + ", GC count delta=" + event.gcCountDelta()
                + " | RSS start/end/sampled peak=" + formatNullableMegabytes(event.rssStartBytes()) + "/"
                + formatNullableMegabytes(event.rssEndBytes()) + "/"
                + formatNullableMegabytes(event.rssPeakSampledBytes()) + " MB"
                + " | samples=" + event.rssSampleCount()
                + " at " + event.rssSamplingIntervalMs() + " ms"
                + " | " + event.rssMetric() + " (" + event.rssStatus() + ")");
        phaseDetails.setWrapText(true);
        recomputeButton.setDisable(false);
        supplementalResults = List.of();
        exportSupplementalButton.setDisable(true);
        supplementStatus.setText("");
        showTransactions(snapshot);
        showDubo(event);
        showDop(event);
        showDhoList(event, index);
        summaryTable.getSelectionModel().select(event);
    }

    private void showTransactions(JsonObject snapshot) {
        JsonArray transactions = array(snapshot, "transactions");
        List<String[]> rows = new ArrayList<>();
        for (JsonElement element : transactions) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject transaction = element.getAsJsonObject();
            rows.add(new String[]{value(transaction, "tid"),
                    boolValue(transaction, "active") ? "active" : "inactive",
                    joined(array(transaction, "items"))});
        }
        transactionTable.setItems(FXCollections.observableArrayList(rows));
    }

    private void showDubo(LogDocument.PhaseEvent event) {
        List<String[]> rows = new ArrayList<>();
        JsonArray observations = event.duboCompared();
        for (JsonElement element : observations) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject observation = element.getAsJsonObject();
            double value = number(observation, "value");
            double minSup = number(observation, "minSup");
            String comparison;
            if (observation.has("passesMinSup")) {
                comparison = observation.get("passesMinSup").getAsBoolean()
                        ? ">= minSup" : "< minSup (pruned)";
            } else if (Double.isFinite(value) && Double.isFinite(minSup)) {
                comparison = value >= minSup ? ">= minSup" : "< minSup (pruned)";
            } else {
                comparison = "n/a";
            }
            rows.add(new String[]{value(observation, "patternKey"), "computed",
                    value(observation, "value"), comparison,
                    value(observation, "support"), value(observation, "dampedOccupancy"),
                    value(observation, "minSup")});
        }
        duboTable.setItems(FXCollections.observableArrayList(rows));
        duboSummary.setText(observations.size() + " DUBO calculation(s) recorded by the engine.");
    }

    private VBox createDopPane() {
        configureStringTable(dopTable, List.of("Pattern", "Support", "DO"));
        VBox pane = new VBox(10, dopStatus, dopTable);
        pane.setPadding(new Insets(10));
        VBox.setVgrow(dopTable, Priority.ALWAYS);
        return pane;
    }

    private void showDop(LogDocument.PhaseEvent event) {
        List<String[]> rows = new ArrayList<>();
        for (JsonElement element : event.patterns()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject pattern = element.getAsJsonObject();
            rows.add(new String[]{value(pattern, "key"), value(pattern, "support"),
                    value(pattern, "dampedOccupancy")});
        }
        dopTable.setItems(FXCollections.observableArrayList(rows));
        dopStatus.setText(rows.isEmpty()
                ? "No final DOP results are recorded for this phase. Older logs may not contain a separate DOP list."
                : rows.size() + " final DOP result(s) recorded by the engine.");
    }

    private VBox createDuboPane() {
        configureStringTable(duboTable,
                List.of("Pattern", "Source", "DUBO", "Comparison with minSup",
                        "Support", "DO", "minSup"));
        recomputeButton.setDisable(true);
        exportSupplementalButton.setDisable(true);
        recomputeButton.setTooltip(new javafx.scene.control.Tooltip(
                "List descendants known to be pruned by a logged DUBO comparison. "
                        + "No DUBO, DO, or support is recalculated for these rows."));
        recomputeButton.setOnAction(event -> recomputeMissingDubo());
        exportSupplementalButton.setOnAction(event -> exportSupplemental());
        HBox actions = new HBox(10, recomputeButton, exportSupplementalButton,
                duboSummary, supplementStatus);
        VBox pane = new VBox(10, actions, duboTable);
        pane.setPadding(new Insets(10));
        VBox.setVgrow(duboTable, Priority.ALWAYS);
        return pane;
    }

    private VBox createDhoListPane() {
        TableColumn<DhoListReconstructor.Node, String> rank =
                dhoColumn("Rank", node -> Integer.toString(node.rank()));
        TableColumn<DhoListReconstructor.Node, String> item =
                dhoColumn("Item", DhoListReconstructor.Node::item);
        TableColumn<DhoListReconstructor.Node, String> support =
                dhoColumn("Support", node -> Integer.toString(node.support()));
        TableColumn<DhoListReconstructor.Node, String> occupancy =
                dhoColumn("DO", node -> format(node.dampedOccupancy()));
        TableColumn<DhoListReconstructor.Node, String> dubo =
                dhoColumn("DUBO", node -> format(node.dubo()));
        TableColumn<DhoListReconstructor.Node, String> duboSource =
                dhoColumn("DUBO source", DhoListReconstructor.Node::duboSource);
        dhoListTable.getColumns().setAll(List.of(rank, item, support, occupancy, dubo, duboSource));
        dhoListTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        configureStringTable(dhoEntryTable, List.of("TID", "Transaction length", "Contribution to DO"));
        dhoListTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, node) -> {
            if (node == null) {
                dhoEntryTable.getItems().clear();
                return;
            }
            List<String[]> rows = node.entries().stream()
                    .map(entry -> new String[]{Integer.toString(entry.tid()),
                            Integer.toString(entry.transactionLength()), format(entry.contribution())})
                    .toList();
            dhoEntryTable.setItems(FXCollections.observableArrayList(rows));
        });
        Label explanation = new Label("DHOList is reconstructed from the selected phase snapshot; "
                + "it is not stored as a serialized DHOList object. DUBO source distinguishes engine log "
                + "from viewer reconstruction.");
        explanation.setWrapText(true);
        Label status = new Label();
        dhoListTable.setUserData(status);
        VBox pane = new VBox(8, explanation, status, new Label("Item nodes"),
                dhoListTable, new Label("Entries for selected item"), dhoEntryTable);
        pane.setPadding(new Insets(10));
        VBox.setVgrow(dhoListTable, Priority.ALWAYS);
        VBox.setVgrow(dhoEntryTable, Priority.ALWAYS);
        return pane;
    }

    private static TableColumn<DhoListReconstructor.Node, String> dhoColumn(
            String header, java.util.function.Function<DhoListReconstructor.Node, String> value) {
        TableColumn<DhoListReconstructor.Node, String> column = new TableColumn<>(header);
        column.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private void showDhoList(LogDocument.PhaseEvent event, int phaseIndex) {
        selectedPhaseGeneration++;
        int generation = selectedPhaseGeneration;
        if (dhoListTask != null) {
            dhoListTask.cancel();
        }
        dhoListTable.getItems().clear();
        dhoEntryTable.getItems().clear();
        Label status = (Label) dhoListTable.getUserData();
        status.setText("Reconstructing DHOList from phase snapshot...");
        dhoListTask = new Task<>() {
            @Override
            protected List<DhoListReconstructor.Node> call() {
                return DhoListReconstructor.reconstruct(event);
            }
        };
        dhoListTask.setOnSucceeded(done -> {
            if (generation != selectedPhaseGeneration) {
                return;
            }
            List<DhoListReconstructor.Node> nodes = dhoListTask.getValue();
            dhoListTable.setItems(FXCollections.observableArrayList(nodes));
            status.setText("Phase " + (phaseIndex + 1) + ": reconstructed "
                    + nodes.size() + " nodes from the logged snapshot.");
            if (!nodes.isEmpty()) {
                dhoListTable.getSelectionModel().select(0);
            }
        });
        dhoListTask.setOnFailed(failed -> {
            if (generation != selectedPhaseGeneration) {
                return;
            }
            status.setText("DHOList reconstruction failed.");
            showError(owner, "Could not reconstruct DHOList: "
                    + dhoListTask.getException().getMessage());
        });
        Thread worker = new Thread(dhoListTask, "dholist-reconstruction-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void recomputeMissingDubo() {
        if (document == null || phaseSelector.getValue() == null) {
            return;
        }
        int index = phaseSelector.getValue();
        LogDocument.PhaseEvent event = document.events().get(index);
        recomputeButton.setDisable(true);
        openButton.setDisable(true);
        phaseSelector.setDisable(true);
        summaryTable.setDisable(true);
        showDubo(event);
        supplementalResults = List.of();
        exportSupplementalButton.setDisable(true);
        supplementStatus.setText("Identifying descendants pruned by recorded DUBO comparisons...");
        Task<List<DuboSupplementer.PrunedPattern>> task = new Task<>() {
            @Override
            protected List<DuboSupplementer.PrunedPattern> call() {
                return DuboSupplementer.findPrunedDescendants(event);
            }
        };
        task.setOnSucceeded(done -> {
            supplementalResults = task.getValue();
            for (DuboSupplementer.PrunedPattern result : supplementalResults) {
                duboTable.getItems().add(new String[]{result.patternKey(), result.source(),
                        "n/a", "pruned by ancestor", "n/a", "n/a", "n/a"});
            }
            supplementStatus.setText(supplementalResults.size()
                    + " pruned descendant placeholder(s); no metric was recalculated.");
            exportSupplementalButton.setDisable(supplementalResults.isEmpty());
            recomputeButton.setDisable(false);
            openButton.setDisable(false);
            phaseSelector.setDisable(false);
            summaryTable.setDisable(false);
        });
        task.setOnFailed(failed -> {
            supplementStatus.setText("Could not identify pruned descendants.");
            recomputeButton.setDisable(false);
            openButton.setDisable(false);
            phaseSelector.setDisable(false);
            summaryTable.setDisable(false);
            showError(owner, "Could not list pruned DUBO descendants: "
                    + task.getException().getMessage());
        });
        Thread worker = new Thread(task, "dubo-supplement-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void exportSupplemental() {
        if (supplementalResults.isEmpty() || document == null || phaseSelector.getValue() == null) {
            return;
        }
        int index = phaseSelector.getValue();
        LogDocument.PhaseEvent event = document.events().get(index);
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export supplemental DUBO results");
        chooser.setInitialFileName(sourcePath.getFileName() + "_phase-" + (index + 1)
                + "_supplemental-dubo.json");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files", "*.json"));
        java.io.File selected = chooser.showSaveDialog(owner);
        if (selected == null) {
            return;
        }
        JsonObject export = new JsonObject();
        export.addProperty("schemaVersion", "1.0-supplemental");
        export.addProperty("sourceLog", sourcePath.toAbsolutePath().toString());
        export.addProperty("engineVersion", document.engineVersion());
        export.addProperty("sessionId", document.sessionId());
        export.addProperty("phaseIndex", index);
        export.addProperty("phase", event.phase());
        export.addProperty("completedAt", event.completedAt());
        export.add("parameters", event.parameters());
        export.addProperty("snapshotLastTid", value(event.snapshot(), "lastTid"));
        export.addProperty("computedDuboCount", event.duboCompared().size());
        export.add("prunedDescendants", GSON.toJsonTree(supplementalResults));
        try {
            java.nio.file.Files.writeString(selected.toPath(), GSON.toJson(export));
            supplementStatus.setText("Supplemental results exported: " + selected.getAbsolutePath());
        } catch (IOException ex) {
            showError(owner, "Could not export supplemental DUBO: " + ex.getMessage());
        }
    }

    private static Path initialProjectDirectory() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        if (workingDirectory.getFileName() != null
                && "log-viewer".equalsIgnoreCase(workingDirectory.getFileName().toString())) {
            return workingDirectory;
        }
        try {
            Path location = Path.of(LogViewerApp.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            for (Path directory = java.nio.file.Files.isDirectory(location)
                    ? location : location.getParent();
                 directory != null; directory = directory.getParent()) {
                if (directory.getFileName() != null
                        && "log-viewer".equalsIgnoreCase(directory.getFileName().toString())
                        && java.nio.file.Files.isDirectory(directory)) {
                    return directory;
                }
            }
        } catch (java.net.URISyntaxException | SecurityException | NullPointerException ignored) {
            return workingDirectory;
        }
        return workingDirectory;
    }

    private void buildCharts() {
        charts.getChildren().clear();
        CategoryAxis timeCategories = new CategoryAxis();
        NumberAxis timeValues = new NumberAxis();
        timeValues.setLabel("Milliseconds");
        BarChart<String, Number> timeChart = new BarChart<>(timeCategories, timeValues);
        timeChart.setTitle("Phase runtime");
        timeChart.setLegendVisible(false);
        XYChart.Series<String, Number> timeSeries = new XYChart.Series<>();

        CategoryAxis heapCategories = new CategoryAxis();
        NumberAxis heapValues = new NumberAxis();
        heapValues.setLabel("MB");
        BarChart<String, Number> heapChart = new BarChart<>(heapCategories, heapValues);
        heapChart.setTitle("Heap used and sampled peak");
        XYChart.Series<String, Number> usedSeries = new XYChart.Series<>();
        usedSeries.setName("Used");
        XYChart.Series<String, Number> peakSeries = new XYChart.Series<>();
        peakSeries.setName("Sampled peak");
        CategoryAxis rssCategories = new CategoryAxis();
        NumberAxis rssValues = new NumberAxis();
        rssValues.setLabel("MB");
        BarChart<String, Number> rssChart = new BarChart<>(rssCategories, rssValues);
        rssChart.setTitle("Worker RSS: start, end, sampled peak");
        XYChart.Series<String, Number> rssStartSeries = new XYChart.Series<>();
        rssStartSeries.setName("Start");
        XYChart.Series<String, Number> rssEndSeries = new XYChart.Series<>();
        rssEndSeries.setName("End");
        XYChart.Series<String, Number> rssPeakSeries = new XYChart.Series<>();
        rssPeakSeries.setName("Sampled peak");
        for (int i = 0; i < document.events().size(); i++) {
            LogDocument.PhaseEvent event = document.events().get(i);
            String phase = (i + 1) + " " + event.phase();
            Number time = event.computeWallNanos() < 0
                    ? null : event.computeWallNanos() / 1_000_000.0;
            Number used = event.heapUsedBytes() < 0 ? null : megabytes(event.heapUsedBytes());
            Number peak = event.heapPeakBytes() < 0 ? null : megabytes(event.heapPeakBytes());
            Number rssStart = event.rssStartBytes() == null ? null : megabytes(event.rssStartBytes());
            Number rssEnd = event.rssEndBytes() == null ? null : megabytes(event.rssEndBytes());
            Number rssPeak = event.rssPeakSampledBytes() == null
                    ? null : megabytes(event.rssPeakSampledBytes());
            timeSeries.getData().add(new XYChart.Data<>(phase, time));
            usedSeries.getData().add(new XYChart.Data<>(phase, used));
            peakSeries.getData().add(new XYChart.Data<>(phase, peak));
            rssStartSeries.getData().add(new XYChart.Data<>(phase, rssStart));
            rssEndSeries.getData().add(new XYChart.Data<>(phase, rssEnd));
            rssPeakSeries.getData().add(new XYChart.Data<>(phase, rssPeak));
        }
        timeChart.getData().add(timeSeries);
        heapChart.getData().addAll(List.of(usedSeries, peakSeries));
        rssChart.getData().addAll(List.of(rssStartSeries, rssEndSeries, rssPeakSeries));
        charts.getChildren().addAll(List.of(timeChart, heapChart, rssChart));
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static String joined(JsonArray values) {
        List<String> parts = new ArrayList<>();
        for (JsonElement value : values) {
            parts.add(value.getAsString());
        }
        return String.join(" ", parts);
    }

    private static boolean boolValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() || value.getAsBoolean();
    }

    private static String value(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "n/a" : value.getAsString();
    }

    private static double number(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? Double.NaN : value.getAsDouble();
    }

    private static String format(double value) {
        return Double.isFinite(value) ? String.format("%.6f", value) : "n/a";
    }

    private static String formatMillis(long nanos) {
        return nanos < 0 ? "n/a" : String.format("%.3f", nanos / 1_000_000.0);
    }

    private static String formatMegabytes(long bytes) {
        return bytes < 0 ? "n/a" : String.format("%.2f", megabytes(bytes));
    }

    private static String formatNullableMegabytes(Long bytes) {
        return bytes == null ? "n/a" : formatMegabytes(bytes);
    }

    private static double megabytes(long bytes) {
        return bytes < 0 ? 0.0 : bytes / (1024.0 * 1024.0);
    }

    private static void showError(Stage stage, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle("Log error");
        alert.setHeaderText("Unable to load log");
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
