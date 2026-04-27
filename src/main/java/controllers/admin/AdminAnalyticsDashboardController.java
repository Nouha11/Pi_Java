package controllers.admin;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import services.studysession.AnalyticsService;
import services.studysession.PdfExportService;
import services.studysession.TutorPerformanceRow;
import utils.MyConnection;

import java.io.File;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controller for the Admin Analytics Dashboard.
 *
 * Loads all chart data asynchronously on a background thread and populates
 * the view via {@code Platform.runLater()}. Spinners are shown during loading
 * and hidden with a 300 ms {@code FadeTransition} once data is ready.
 *
 * Requirements: 1.3, 1.4, 1.5, 2.2, 2.3, 2.5, 2.6, 3.3, 3.4, 3.5,
 *               4.2, 4.4, 4.5, 4.6, 5.2, 5.4, 5.5, 5.6, 6.3, 6.4, 6.6,
 *               7.5, 7.6, 7.7, 15.3, 15.5
 */
public class AdminAnalyticsDashboardController implements Initializable {

    // ── Difficulty color constants ────────────────────────────────────────────
    private static final String COLOR_BEGINNER     = "#22c55e";
    private static final String COLOR_INTERMEDIATE = "#f59e0b";
    private static final String COLOR_ADVANCED     = "#ef4444";

    // ── Stat card labels ──────────────────────────────────────────────────────
    @FXML private Label lblTotalSessions;
    @FXML private Label lblTotalStudents;
    @FXML private Label lblTotalCourses;
    @FXML private Label lblTotalPlannings;

    // ── Charts ────────────────────────────────────────────────────────────────
    @FXML private LineChart<String, Number> chartSessionsOverTime;
    @FXML private PieChart                  chartDifficulty;
    @FXML private BarChart<String, Number>  chartTimeByCourse;
    @FXML private LineChart<String, Number> chartProgress;

    // ── Spinners ──────────────────────────────────────────────────────────────
    @FXML private ProgressIndicator spinnerSessions;
    @FXML private ProgressIndicator spinnerDifficulty;
    @FXML private ProgressIndicator spinnerTimeCourse;
    @FXML private ProgressIndicator spinnerProgress;
    @FXML private ProgressIndicator spinnerTutor;

    // ── Sessions toggle buttons ───────────────────────────────────────────────
    @FXML private ToggleButton btnSessionsWeekly;
    @FXML private ToggleButton btnSessionsMonthly;

    // ── Progress toggle buttons ───────────────────────────────────────────────
    @FXML private ToggleButton btnProgressDaily;
    @FXML private ToggleButton btnProgressWeekly;
    @FXML private ToggleButton btnProgressMonthly;

    // ── Date pickers ──────────────────────────────────────────────────────────
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;

    // ── Tutor performance table ───────────────────────────────────────────────
    @FXML private TableView<TutorPerformanceRow>  tablePerformance;
    @FXML private TableColumn<TutorPerformanceRow, String>  colTutorName;
    @FXML private TableColumn<TutorPerformanceRow, Integer> colEnrolledStudents;
    @FXML private TableColumn<TutorPerformanceRow, Double>  colAvgCompletion;
    @FXML private TableColumn<TutorPerformanceRow, Integer> colActiveCourses;
    @FXML private TableColumn<TutorPerformanceRow, Double>  colAvgDuration;

    // ── Popularity list ───────────────────────────────────────────────────────
    @FXML private VBox popularityList;

    // ── Export button ─────────────────────────────────────────────────────────
    @FXML private Button btnExportPdf;

    // ── No-data labels (defined in FXML) ─────────────────────────────────────
    @FXML private Label lblNoDataSessions;
    @FXML private Label lblNoDataDifficulty;
    @FXML private Label lblNoDataTimeCourse;
    @FXML private Label lblNoDataProgress;

    // ── Services ──────────────────────────────────────────────────────────────
    private final AnalyticsService analyticsService = new AnalyticsService();

    // ── Debounce for date pickers ─────────────────────────────────────────────
    private PauseTransition dateFilterDebounce;

    // ─────────────────────────────────────────────────────────────────────────
    //  Initializable
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Show all spinners immediately
        showAllSpinners();

        // Wire toggle buttons
        wireSessionsToggle();
        wireProgressToggle();

        // Wire date pickers with 500 ms debounce
        wireDatePickers();

        // Configure table columns
        configureTableColumns();

        // Load all data asynchronously
        loadAllDataAsync();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Spinner helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void showAllSpinners() {
        for (ProgressIndicator pi : new ProgressIndicator[]{
                spinnerSessions, spinnerDifficulty, spinnerTimeCourse,
                spinnerProgress, spinnerTutor}) {
            pi.setVisible(true);
            pi.setManaged(true);
            pi.setOpacity(1.0);
        }
    }

    /** Fades out a spinner over 300 ms, then hides it. */
    private void hideSpinner(ProgressIndicator spinner) {
        FadeTransition ft = new FadeTransition(Duration.millis(300), spinner);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.setOnFinished(e -> {
            spinner.setVisible(false);
            spinner.setManaged(false);
        });
        ft.play();
    }

    /**
     * Fades in a chart/content node over 300 ms (opacity 0 → 1).
     * Call this after populating a chart to animate its appearance.
     * Requirements: 15.5
     */
    private void fadeInNode(Node node) {
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(300), node);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Async data loading
    // ─────────────────────────────────────────────────────────────────────────

    private void loadAllDataAsync() {
        Task<Void> task = new Task<>() {
            // Fetched data holders
            private long totalSessions;
            private long totalStudents;
            private long totalCourses;
            private long totalPlannings;

            private List<Object[]> sessionsByWeek;
            private Map<String, Integer> difficultyData;
            private List<Object[]> timeByCourse;
            private List<Object[]> progressData;
            private List<TutorPerformanceRow> tutorRows;
            private List<Object[]> popularityData;

            @Override
            protected Void call() throws Exception {
                // Stat card counts
                totalSessions  = queryCount("SELECT COUNT(*) FROM study_session");
                totalStudents  = queryCount("SELECT COUNT(*) FROM user WHERE role = 'ROLE_STUDENT'");
                totalCourses   = queryCount("SELECT COUNT(*) FROM course WHERE is_published = 1");
                totalPlannings = queryCount("SELECT COUNT(*) FROM planning");

                // Chart data
                sessionsByWeek = analyticsService.getSessionCountByWeek(12);
                difficultyData = analyticsService.getSessionCountByDifficulty();
                timeByCourse   = analyticsService.getTimeSpentByCourse(null, null);
                progressData   = analyticsService.getAverageProgressByPeriod("WEEKLY", 12);
                tutorRows      = analyticsService.getTutorPerformanceStats();
                popularityData = analyticsService.getCoursePopularityRanking(5);

                Platform.runLater(() -> {
                    // Stat cards
                    lblTotalSessions.setText(String.valueOf(totalSessions));
                    lblTotalStudents.setText(String.valueOf(totalStudents));
                    lblTotalCourses.setText(String.valueOf(totalCourses));
                    lblTotalPlannings.setText(String.valueOf(totalPlannings));

                    // Charts
                    populateSessionsChart(sessionsByWeek);
                    populateDifficultyChart(difficultyData);
                    populateTimeByCourseChart(timeByCourse);
                    populateProgressChart(progressData);
                    populateTutorTable(tutorRows);
                    populatePopularityList(popularityData);

                    // Hide all spinners
                    hideSpinner(spinnerSessions);
                    hideSpinner(spinnerDifficulty);
                    hideSpinner(spinnerTimeCourse);
                    hideSpinner(spinnerProgress);
                    hideSpinner(spinnerTutor);
                });
                return null;
            }
        };

        task.setOnFailed(e -> Platform.runLater(() -> {
            Throwable ex = task.getException();
            System.err.println("[AdminAnalyticsDashboard] Data load failed: " +
                    (ex != null ? ex.getMessage() : "unknown error"));
            // Hide spinners even on failure
            hideSpinner(spinnerSessions);
            hideSpinner(spinnerDifficulty);
            hideSpinner(spinnerTimeCourse);
            hideSpinner(spinnerProgress);
            hideSpinner(spinnerTutor);
        }));

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Chart population helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Populates the Sessions Over Time line chart. */
    private void populateSessionsChart(List<Object[]> rows) {
        chartSessionsOverTime.getData().clear();
        if (rows == null || rows.isEmpty()) {
            showNoData(lblNoDataSessions, chartSessionsOverTime);
            return;
        }
        hideNoData(lblNoDataSessions, chartSessionsOverTime);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Sessions");
        for (Object[] row : rows) {
            series.getData().add(new XYChart.Data<>((String) row[0], (Integer) row[1]));
        }
        chartSessionsOverTime.getData().add(series);
        installTooltipsOnSeries(series);
        fadeInNode(chartSessionsOverTime);
    }

    /** Populates the Difficulty pie chart with DifficultyColor slice fills. */
    private void populateDifficultyChart(Map<String, Integer> data) {
        chartDifficulty.getData().clear();
        if (data == null || data.isEmpty()) {
            showNoData(lblNoDataDifficulty, chartDifficulty);
            return;
        }
        hideNoData(lblNoDataDifficulty, chartDifficulty);

        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            pieData.add(new PieChart.Data(entry.getKey() + " (" + entry.getValue() + ")", entry.getValue()));
        }
        chartDifficulty.setData(pieData);
        fadeInNode(chartDifficulty);

        // Apply DifficultyColor fills after the chart has been laid out
        Platform.runLater(() -> {
            for (PieChart.Data slice : chartDifficulty.getData()) {
                String difficulty = slice.getName().split(" ")[0].toUpperCase();
                String color = difficultyColor(difficulty);
                slice.getNode().setStyle("-fx-pie-color: " + color + ";");
                // Install tooltip on each pie slice
                Tooltip tooltip = new Tooltip(slice.getName() + ": " + (int) slice.getPieValue());
                Tooltip.install(slice.getNode(), tooltip);
            }
        });
    }

    /** Populates the Time Spent by Course bar chart. */
    private void populateTimeByCourseChart(List<Object[]> rows) {
        chartTimeByCourse.getData().clear();
        if (rows == null || rows.isEmpty()) {
            showNoData(lblNoDataTimeCourse, chartTimeByCourse);
            return;
        }
        hideNoData(lblNoDataTimeCourse, chartTimeByCourse);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Minutes");
        for (Object[] row : rows) {
            String courseName = (String) row[0];
            if (courseName == null) courseName = "(Unknown)";
            // Truncate to 20 chars per requirement 4.4
            if (courseName.length() > 20) {
                courseName = courseName.substring(0, 20);
            }
            series.getData().add(new XYChart.Data<>(courseName, (Integer) row[1]));
        }
        chartTimeByCourse.getData().add(series);
        installTooltipsOnSeries(series);
        fadeInNode(chartTimeByCourse);
        // Add value labels above each bar (requirement 4.5)
        // Use nested runLater to ensure the chart has been laid out before accessing bar bounds
        Platform.runLater(() -> Platform.runLater(() -> addBarValueLabels(chartTimeByCourse, series)));
    }

    /** Populates the Student Progress line chart. */
    private void populateProgressChart(List<Object[]> rows) {
        chartProgress.getData().clear();
        if (rows == null || rows.isEmpty()) {
            showNoData(lblNoDataProgress, chartProgress);
            return;
        }
        hideNoData(lblNoDataProgress, chartProgress);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Avg Progress (%)");
        for (Object[] row : rows) {
            series.getData().add(new XYChart.Data<>((String) row[0], (Double) row[1]));
        }
        chartProgress.getData().add(series);
        installTooltipsOnSeries(series);
        fadeInNode(chartProgress);
    }

    /** Populates the Tutor Performance table with row highlighting. */
    private void populateTutorTable(List<TutorPerformanceRow> rows) {
        if (rows == null || rows.isEmpty()) {
            tablePerformance.setItems(FXCollections.emptyObservableList());
            return;
        }

        // Find the tutor with the highest enrolled students
        int maxEnrolled = rows.stream()
                .mapToInt(TutorPerformanceRow::getEnrolledStudents)
                .max()
                .orElse(-1);

        tablePerformance.setItems(FXCollections.observableArrayList(rows));

        // Row factory: highlight top row with #fef9c3
        tablePerformance.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(TutorPerformanceRow item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (item.getEnrolledStudents() == maxEnrolled && maxEnrolled >= 0) {
                    setStyle("-fx-background-color: #fef9c3;");
                } else {
                    setStyle("");
                }
            }
        });
    }

    /** Populates the Course Popularity ranked list. */
    private void populatePopularityList(List<Object[]> rows) {
        popularityList.getChildren().clear();
        if (rows == null || rows.isEmpty()) {
            Label noData = new Label("No popularity data available.");
            noData.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
            popularityList.getChildren().add(noData);
            return;
        }

        int rank = 1;
        for (Object[] row : rows) {
            String courseName = (String) row[0];
            int count = (Integer) row[1];
            Label lbl = new Label(rank + ". " + courseName + " (" + count + " students)");
            lbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #0f172a; -fx-padding: 4 0;");
            popularityList.getChildren().add(lbl);
            rank++;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Toggle button wiring
    // ─────────────────────────────────────────────────────────────────────────

    private void wireSessionsToggle() {
        btnSessionsWeekly.setOnAction(e -> {
            if (!btnSessionsWeekly.isSelected()) {
                btnSessionsWeekly.setSelected(true);
                return;
            }
            btnSessionsMonthly.setSelected(false);
            reloadSessionsChart("WEEKLY");
        });

        btnSessionsMonthly.setOnAction(e -> {
            if (!btnSessionsMonthly.isSelected()) {
                btnSessionsMonthly.setSelected(true);
                return;
            }
            btnSessionsWeekly.setSelected(false);
            reloadSessionsChart("MONTHLY");
        });
    }

    private void wireProgressToggle() {
        btnProgressDaily.setOnAction(e -> {
            if (!btnProgressDaily.isSelected()) {
                btnProgressDaily.setSelected(true);
                return;
            }
            btnProgressWeekly.setSelected(false);
            btnProgressMonthly.setSelected(false);
            reloadProgressChart("DAILY", 30);
        });

        btnProgressWeekly.setOnAction(e -> {
            if (!btnProgressWeekly.isSelected()) {
                btnProgressWeekly.setSelected(true);
                return;
            }
            btnProgressDaily.setSelected(false);
            btnProgressMonthly.setSelected(false);
            reloadProgressChart("WEEKLY", 12);
        });

        btnProgressMonthly.setOnAction(e -> {
            if (!btnProgressMonthly.isSelected()) {
                btnProgressMonthly.setSelected(true);
                return;
            }
            btnProgressDaily.setSelected(false);
            btnProgressWeekly.setSelected(false);
            reloadProgressChart("MONTHLY", 12);
        });
    }

    private void reloadSessionsChart(String mode) {
        spinnerSessions.setVisible(true);
        spinnerSessions.setManaged(true);
        spinnerSessions.setOpacity(1.0);

        Task<List<Object[]>> task = new Task<>() {
            @Override
            protected List<Object[]> call() throws Exception {
                if ("WEEKLY".equals(mode)) {
                    return analyticsService.getSessionCountByWeek(12);
                } else {
                    return analyticsService.getSessionCountByMonth(12);
                }
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            populateSessionsChart(task.getValue());
            hideSpinner(spinnerSessions);
        }));
        task.setOnFailed(e -> Platform.runLater(() -> hideSpinner(spinnerSessions)));
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void reloadProgressChart(String period, int count) {
        spinnerProgress.setVisible(true);
        spinnerProgress.setManaged(true);
        spinnerProgress.setOpacity(1.0);

        Task<List<Object[]>> task = new Task<>() {
            @Override
            protected List<Object[]> call() throws Exception {
                return analyticsService.getAverageProgressByPeriod(period, count);
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            populateProgressChart(task.getValue());
            hideSpinner(spinnerProgress);
        }));
        task.setOnFailed(e -> Platform.runLater(() -> hideSpinner(spinnerProgress)));
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Date picker wiring (500 ms debounce)
    // ─────────────────────────────────────────────────────────────────────────

    private void wireDatePickers() {
        dateFilterDebounce = new PauseTransition(Duration.millis(500));
        dateFilterDebounce.setOnFinished(e -> reloadTimeByCourseChart());

        dpFrom.valueProperty().addListener((obs, oldVal, newVal) -> dateFilterDebounce.playFromStart());
        dpTo.valueProperty().addListener((obs, oldVal, newVal) -> dateFilterDebounce.playFromStart());
    }

    private void reloadTimeByCourseChart() {
        LocalDate from = dpFrom.getValue();
        LocalDate to   = dpTo.getValue();

        spinnerTimeCourse.setVisible(true);
        spinnerTimeCourse.setManaged(true);
        spinnerTimeCourse.setOpacity(1.0);

        Task<List<Object[]>> task = new Task<>() {
            @Override
            protected List<Object[]> call() throws Exception {
                return analyticsService.getTimeSpentByCourse(from, to);
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            populateTimeByCourseChart(task.getValue());
            hideSpinner(spinnerTimeCourse);
        }));
        task.setOnFailed(e -> Platform.runLater(() -> hideSpinner(spinnerTimeCourse)));
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Table column configuration
    // ─────────────────────────────────────────────────────────────────────────

    private void configureTableColumns() {
        colTutorName.setCellValueFactory(new PropertyValueFactory<>("tutorName"));
        colEnrolledStudents.setCellValueFactory(new PropertyValueFactory<>("enrolledStudents"));
        colAvgCompletion.setCellValueFactory(new PropertyValueFactory<>("averageCompletionRate"));
        colActiveCourses.setCellValueFactory(new PropertyValueFactory<>("activeCourseCount"));
        colAvgDuration.setCellValueFactory(new PropertyValueFactory<>("averageSessionDuration"));

        // Format double columns to 1 decimal place
        colAvgCompletion.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%.1f", item));
            }
        });
        colAvgDuration.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%.1f", item));
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PDF Export
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void handleExportPdf() {
        // Build default filename
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String defaultName = "nova_analytics_report_" + dateStr + ".pdf";

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Analytics Report");
        fileChooser.setInitialFileName(defaultName);
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

        File outputFile = fileChooser.showSaveDialog(btnExportPdf.getScene().getWindow());
        if (outputFile == null) {
            return; // User cancelled
        }

        // Collect chart nodes (must be on FX thread for snapshot)
        List<Node> chartNodes = List.of(
                chartSessionsOverTime,
                chartDifficulty,
                chartTimeByCourse,
                chartProgress
        );

        // Collect stat card values
        Map<String, String> statCards = new LinkedHashMap<>();
        statCards.put("Total Study Sessions",  lblTotalSessions.getText());
        statCards.put("Total Students",         lblTotalStudents.getText());
        statCards.put("Published Courses",      lblTotalCourses.getText());
        statCards.put("Total Planning Events",  lblTotalPlannings.getText());

        // Collect tutor rows from table
        List<TutorPerformanceRow> tutorRows = new ArrayList<>(tablePerformance.getItems());

        // Run export on background thread (snapshots already taken on FX thread above)
        // Note: PdfExportService.exportReport calls node.snapshot() internally,
        // so we must call it on the FX thread or pass pre-captured images.
        // Here we call it directly since we are already on the FX thread.
        PdfExportService exportService = new PdfExportService();
        final File finalOutputFile = outputFile;

        try {
            exportService.exportReport(chartNodes, statCards, tutorRows, finalOutputFile);
            showAlert(Alert.AlertType.INFORMATION,
                    "Export Successful",
                    "✅ Report exported successfully to " + finalOutputFile.getAbsolutePath());
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR,
                    "Export Failed",
                    "❌ Export failed: " + ex.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Utility helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns the DifficultyColor hex string for a given difficulty label. */
    private String difficultyColor(String difficulty) {
        if (difficulty == null) return "#94a3b8";
        return switch (difficulty.toUpperCase()) {
            case "BEGINNER"     -> COLOR_BEGINNER;
            case "INTERMEDIATE" -> COLOR_INTERMEDIATE;
            case "ADVANCED"     -> COLOR_ADVANCED;
            default             -> "#94a3b8";
        };
    }

    /** Shows the no-data label and hides the chart. */
    private void showNoData(Label noDataLabel, Node chart) {
        noDataLabel.setVisible(true);
        noDataLabel.setManaged(true);
        chart.setVisible(false);
        chart.setManaged(false);
    }

    /** Hides the no-data label and shows the chart. */
    private void hideNoData(Label noDataLabel, Node chart) {
        noDataLabel.setVisible(false);
        noDataLabel.setManaged(false);
        chart.setVisible(true);
        chart.setManaged(true);
    }

    /** Installs tooltips on all data points in an XYChart series. */
    private <X, Y> void installTooltipsOnSeries(XYChart.Series<X, Y> series) {
        for (XYChart.Data<X, Y> data : series.getData()) {
            Tooltip tooltip = new Tooltip(data.getXValue() + ": " + data.getYValue());
            if (data.getNode() != null) {
                Tooltip.install(data.getNode(), tooltip);
            }
        }
        // Also install after chart renders (nodes may not exist yet)
        Platform.runLater(() -> {
            for (XYChart.Data<X, Y> data : series.getData()) {
                if (data.getNode() != null) {
                    Tooltip tooltip = new Tooltip(data.getXValue() + ": " + data.getYValue());
                    Tooltip.install(data.getNode(), tooltip);
                }
            }
        });
    }

    /**
     * Adds a value {@link Label} above each bar in the given {@link BarChart} series.
     *
     * <p>The label shows the exact integer minute value. It is positioned by
     * converting the bar node's bounds to the chart's coordinate space and
     * placing a {@link Label} at the top-center of each bar.
     *
     * <p>Must be called via {@code Platform.runLater()} after the chart has been
     * rendered so that bar node bounds are available.
     *
     * Requirements: 4.5
     *
     * @param chart  the BarChart whose bars should be labelled
     * @param series the data series whose bars to label
     */
    private void addBarValueLabels(BarChart<String, Number> chart,
                                   XYChart.Series<String, Number> series) {
        for (XYChart.Data<String, Number> data : series.getData()) {
            Node barNode = data.getNode();
            if (barNode == null) continue;

            Number yValue = data.getYValue();
            if (yValue == null) continue;

            // Build the label
            Label lbl = new Label(String.valueOf(yValue.intValue()) + " min");
            lbl.setStyle("-fx-font-size: 9px; -fx-text-fill: #374151; -fx-font-weight: bold;");

            // Add label to the chart's plot area (the Pane that holds bar nodes)
            if (barNode.getParent() instanceof Pane plotArea) {
                plotArea.getChildren().add(lbl);

                // Position the label once the bar node has been laid out
                barNode.boundsInParentProperty().addListener((obs, oldBounds, newBounds) -> {
                    if (newBounds.getWidth() <= 0) return;
                    double labelX = newBounds.getMinX()
                            + (newBounds.getWidth() - lbl.prefWidth(-1)) / 2.0;
                    double labelY = newBounds.getMinY() - lbl.prefHeight(-1) - 2;
                    lbl.setLayoutX(labelX);
                    lbl.setLayoutY(labelY);
                });

                // Also set initial position immediately
                javafx.geometry.Bounds b = barNode.getBoundsInParent();
                if (b.getWidth() > 0) {
                    double labelX = b.getMinX() + (b.getWidth() - lbl.prefWidth(-1)) / 2.0;
                    double labelY = b.getMinY() - lbl.prefHeight(-1) - 2;
                    lbl.setLayoutX(labelX);
                    lbl.setLayoutY(labelY);
                }
            }
        }
    }

    /** Executes a COUNT query and returns the result. */
    private long queryCount(String sql) {
        try {
            Connection cnx = MyConnection.getInstance().getCnx();
            try (PreparedStatement ps = cnx.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[AdminAnalyticsDashboard] Count query failed: " + e.getMessage());
        }
        return 0;
    }

    /** Shows a simple alert dialog. */
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
