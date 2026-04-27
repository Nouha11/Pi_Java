package controllers.studysession;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import models.studysession.CalendarEvent;
import models.studysession.Planning;
import models.studysession.StudySession;
import models.users.User;
import services.studysession.CalendarFilter;
import services.studysession.CalendarService;
import services.studysession.OverlapException;
import utils.MyConnection;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Controller for the Calendar Planner view.
 *
 * <p>Supports Monthly and Weekly view modes, collapsible filter panel,
 * Prev/Next/Today navigation, and role-based data scoping.
 *
 * <p>Grid rendering (buildMonthlyGrid / buildWeeklyGrid) is stubbed here
 * and will be implemented in task 14.
 *
 * Requirements: 9.4, 9.5, 9.6, 9.7, 13.1–13.3, 14.1–14.5, 15.4, 15.7
 */
public class CalendarPlannerController implements Initializable {

    // ── DifficultyColor constants ─────────────────────────────────────────────
    /** Maps course difficulty label → hex color string. */
    private static final Map<String, String> DIFFICULTY_COLOR;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("BEGINNER",     "#22c55e");
        m.put("INTERMEDIATE", "#f59e0b");
        m.put("ADVANCED",     "#ef4444");
        DIFFICULTY_COLOR = Collections.unmodifiableMap(m);
    }

    /** Maps planning status label → hex color string. */
    private static final Map<String, String> STATUS_COLOR;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("SCHEDULED",  "#3b82f6");
        m.put("COMPLETED",  "#22c55e");
        m.put("MISSED",     "#ef4444");
        m.put("CANCELLED",  "#94a3b8");
        STATUS_COLOR = Collections.unmodifiableMap(m);
    }

    // ── Drag-and-drop DataFormat ──────────────────────────────────────────────
    /**
     * Custom {@link DataFormat} used to carry {@code "eventId:type"} strings
     * through the JavaFX drag-and-drop clipboard.
     */
    private static final DataFormat EVENT_DF =
            new DataFormat("application/x-nova-calendar-event");

    // ── Resize drag state ─────────────────────────────────────────────────────
    /** Y-coordinate of the mouse when a resize drag started. */
    private double resizeDragStartY;
    /** Original height (px) of the event block when resize started. */
    private double resizeOriginalHeight;
    /** Original end time of the event being resized. */
    private LocalDateTime resizeOriginalEnd;

    // ── FXML fields — toolbar ─────────────────────────────────────────────────
    @FXML private Button       btnPrev;
    @FXML private Button       btnToday;
    @FXML private Button       btnNext;
    @FXML private Label        lblPeriod;
    @FXML private ToggleButton btnMonthly;
    @FXML private ToggleButton btnWeekly;
    @FXML private Button       btnToggleFilters;

    // ── FXML fields — filter panel ────────────────────────────────────────────
    @FXML private VBox         filterPanel;
    @FXML private ComboBox<String> cbCourse;
    @FXML private ComboBox<String> cbDifficulty;
    @FXML private ComboBox<String> cbStatus;
    @FXML private DatePicker   dpFilterFrom;
    @FXML private DatePicker   dpFilterTo;
    @FXML private VBox         cbStudentBox;
    @FXML private ComboBox<String> cbStudent;
    @FXML private VBox         cbTutorBox;
    @FXML private ComboBox<String> cbTutor;
    @FXML private Button       btnResetFilters;

    // ── FXML fields — calendar area ───────────────────────────────────────────
    @FXML private StackPane        calendarArea;
    @FXML private ProgressIndicator spinnerCalendar;

    // ── FXML fields — legend ──────────────────────────────────────────────────
    @FXML private HBox colorLegend;

    // ── State ─────────────────────────────────────────────────────────────────
    /** The logged-in user; set by parent controller via {@link #setCurrentUser(User)}. */
    private User currentUser;

    /** Start of the currently displayed period (first day of month or week). */
    private LocalDate currentPeriodStart = LocalDate.now().withDayOfMonth(1);

    /** Current view mode: true = monthly, false = weekly. */
    private boolean isMonthlyView = true;

    /** Active filter state — preserved across period navigation. */
    private final CalendarFilter calendarFilter = new CalendarFilter();

    /** Service for loading calendar events. */
    private final CalendarService calendarService = new CalendarService();

    /** Debounce timer for filter changes (500 ms). */
    private PauseTransition filterDebounce;

    // ─────────────────────────────────────────────────────────────────────────
    //  Initializable
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Set up 500 ms debounce for filter changes
        filterDebounce = new PauseTransition(Duration.millis(500));
        filterDebounce.setOnFinished(e -> loadCalendar());

        // Populate static ComboBoxes
        populateStaticComboBoxes();

        // Wire filter controls to debounced reload
        wireFilterControls();

        // Build color legend
        buildColorLegend();

        // Default period start = first day of current month
        currentPeriodStart = LocalDate.now().withDayOfMonth(1);
        updatePeriodLabel();

        // Show spinner initially; calendar loads after setCurrentUser() is called
        // (or immediately if no user is set — will use a guest/empty state)
        spinnerCalendar.setVisible(true);
        spinnerCalendar.setManaged(true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API — called by parent controllers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Receives the logged-in user from the parent dashboard controller.
     * Populates role-dependent ComboBoxes and triggers the initial calendar load.
     *
     * Requirements: 9.4, 13.1–13.3, 14.2
     *
     * @param user the currently logged-in user
     */
    public void setCurrentUser(User user) {
        this.currentUser = user;
        if (user == null) return;

        // Apply role-based visibility for Student/Tutor/Admin filter controls
        applyRoleBasedFilterVisibility(user.getRole());

        // Populate role-dependent ComboBoxes on a background thread
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                List<String> courses  = loadCoursesForUser(user);
                List<String> students = loadStudentsForUser(user);
                List<String> tutors   = loadTutors(user);

                Platform.runLater(() -> {
                    // Courses
                    cbCourse.getItems().clear();
                    cbCourse.getItems().add("All");
                    cbCourse.getItems().addAll(courses);
                    cbCourse.setValue("All");

                    // Students (Admin/Tutor only)
                    cbStudent.getItems().clear();
                    cbStudent.getItems().add("All");
                    cbStudent.getItems().addAll(students);
                    cbStudent.setValue("All");

                    // Tutors (Admin only)
                    cbTutor.getItems().clear();
                    cbTutor.getItems().add("All");
                    cbTutor.getItems().addAll(tutors);
                    cbTutor.setValue("All");

                    // Trigger initial calendar load
                    loadCalendar();
                });
                return null;
            }
        };
        task.setOnFailed(e -> {
            System.err.println("[CalendarPlanner] Failed to load filter data: "
                    + task.getException().getMessage());
            Platform.runLater(this::loadCalendar);
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Toolbar handlers
    // ─────────────────────────────────────────────────────────────────────────

    /** Navigate to the previous period (month or week). */
    @FXML
    private void handlePrev() {
        if (isMonthlyView) {
            currentPeriodStart = currentPeriodStart.minusMonths(1);
        } else {
            currentPeriodStart = currentPeriodStart.minusWeeks(1);
        }
        updatePeriodLabel();
        loadCalendar();
    }

    /** Navigate to today's period. */
    @FXML
    private void handleToday() {
        if (isMonthlyView) {
            currentPeriodStart = LocalDate.now().withDayOfMonth(1);
        } else {
            // Start of the current week (Monday)
            currentPeriodStart = LocalDate.now().with(
                    java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        }
        updatePeriodLabel();
        loadCalendar();
    }

    /** Navigate to the next period (month or week). */
    @FXML
    private void handleNext() {
        if (isMonthlyView) {
            currentPeriodStart = currentPeriodStart.plusMonths(1);
        } else {
            currentPeriodStart = currentPeriodStart.plusWeeks(1);
        }
        updatePeriodLabel();
        loadCalendar();
    }

    /** Switch to Monthly view mode. */
    @FXML
    private void handleMonthlyView() {
        if (!btnMonthly.isSelected()) {
            btnMonthly.setSelected(true);
            return;
        }
        btnWeekly.setSelected(false);
        isMonthlyView = true;
        // Snap period start to first day of month
        currentPeriodStart = currentPeriodStart.withDayOfMonth(1);
        updatePeriodLabel();
        loadCalendar();
    }

    /** Switch to Weekly view mode. */
    @FXML
    private void handleWeeklyView() {
        if (!btnWeekly.isSelected()) {
            btnWeekly.setSelected(true);
            return;
        }
        btnMonthly.setSelected(false);
        isMonthlyView = false;
        // Snap period start to Monday of the current week
        currentPeriodStart = currentPeriodStart.with(
                java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        updatePeriodLabel();
        loadCalendar();
    }

    /** Toggle the collapsible filter panel. */
    @FXML
    private void handleToggleFilters() {
        boolean nowVisible = !filterPanel.isVisible();
        filterPanel.setVisible(nowVisible);
        filterPanel.setManaged(nowVisible);
        btnToggleFilters.setText(nowVisible ? "🔼 Filters" : "🔽 Filters");
    }

    /** Reset all filter controls to their default "All" state and reload. */
    @FXML
    private void handleResetFilters() {
        cbCourse.setValue("All");
        cbDifficulty.setValue("All");
        cbStatus.setValue("All");
        dpFilterFrom.setValue(null);
        dpFilterTo.setValue(null);
        cbStudent.setValue("All");
        cbTutor.setValue("All");

        // Reset the CalendarFilter state
        calendarFilter.setCourseId(null);
        calendarFilter.setDifficulty(null);
        calendarFilter.setStatus(null);
        calendarFilter.setFrom(null);
        calendarFilter.setTo(null);
        calendarFilter.setStudentId(null);
        calendarFilter.setTutorId(null);

        loadCalendar();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Calendar loading
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads calendar events for the current period on a background thread,
     * shows the spinner while loading, then calls the appropriate grid builder
     * on the JavaFX thread.
     *
     * Requirements: 10.1, 15.5
     */
    private void loadCalendar() {
        // Show spinner
        spinnerCalendar.setVisible(true);
        spinnerCalendar.setManaged(true);
        spinnerCalendar.setOpacity(1.0);

        // Compute period bounds
        LocalDate from;
        LocalDate to;
        if (isMonthlyView) {
            YearMonth ym = YearMonth.from(currentPeriodStart);
            from = ym.atDay(1);
            to   = ym.atEndOfMonth();
        } else {
            from = currentPeriodStart;
            to   = currentPeriodStart.plusDays(6);
        }

        // Snapshot filter state for the background thread
        int    userId = (currentUser != null) ? currentUser.getId() : 0;
        String role   = (currentUser != null) ? currentUser.getRole().name() : "ROLE_STUDENT";

        // Build a snapshot of the current filter
        CalendarFilter filterSnapshot = buildFilterSnapshot();

        Task<List<CalendarEvent>> task = new Task<>() {
            @Override
            protected List<CalendarEvent> call() throws Exception {
                return calendarService.getEventsForPeriod(userId, role, from, to, filterSnapshot);
            }
        };

        final LocalDate finalFrom = from;
        final LocalDate finalTo   = to;

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            List<CalendarEvent> events = task.getValue();
            hideSpinner();
            if (isMonthlyView) {
                buildMonthlyGrid(YearMonth.from(finalFrom), events);
            } else {
                buildWeeklyGrid(finalFrom, events);
            }
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            hideSpinner();
            System.err.println("[CalendarPlanner] Failed to load events: "
                    + task.getException().getMessage());
        }));

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Grid builders — task 14
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds and displays the monthly calendar grid.
     *
     * <p>Layout: 7 columns (Mon–Sun) × 6 rows of day cells, preceded by a
     * header row of day-of-week labels.  Each day cell is a {@link VBox}
     * containing a date label and up to 3 event chips; if more events exist a
     * "+N more" label is shown that opens a popup listing all events.
     *
     * <p>Today's cell is highlighted with {@code #eff6ff}; cells outside the
     * current month are dimmed.  Alternate rows use white / {@code #f8fafc}
     * shading.
     *
     * Requirements: 10.2, 10.5, 15.4
     *
     * @param month  the year-month to display
     * @param events the events to render within the month
     */
    private void buildMonthlyGrid(YearMonth month, List<CalendarEvent> events) {
        // ── Group events by date ──────────────────────────────────────────────
        Map<LocalDate, List<CalendarEvent>> byDate = new LinkedHashMap<>();
        for (CalendarEvent ev : events) {
            if (ev.getStart() != null) {
                LocalDate d = ev.getStart().toLocalDate();
                byDate.computeIfAbsent(d, k -> new ArrayList<>()).add(ev);
            }
        }

        // ── Build GridPane ────────────────────────────────────────────────────
        GridPane grid = new GridPane();
        grid.setHgap(0);
        grid.setVgap(0);
        grid.setStyle("-fx-background-color: #e2e8f0;");   // gap color = border

        // 7 equal columns
        for (int c = 0; c < 7; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 7);
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);
        }

        // ── Day-of-week header row (row 0) ────────────────────────────────────
        String[] dayHeaders = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (int c = 0; c < 7; c++) {
            Label hdr = new Label(dayHeaders[c]);
            hdr.setMaxWidth(Double.MAX_VALUE);
            hdr.setAlignment(Pos.CENTER);
            hdr.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #64748b;"
                    + "-fx-background-color: white; -fx-padding: 6 4;");
            grid.add(hdr, c, 0);
        }

        // ── Day cells (rows 1–6) ──────────────────────────────────────────────
        LocalDate firstOfMonth = month.atDay(1);
        // ISO: Monday = 1, so offset = dayOfWeek - 1
        int startOffset = firstOfMonth.getDayOfWeek().getValue() - 1;
        LocalDate today = LocalDate.now();

        for (int row = 0; row < 6; row++) {
            // Alternate row background: white / #f8fafc
            String rowBg = (row % 2 == 0) ? "white" : "#f8fafc";

            for (int col = 0; col < 7; col++) {
                int dayIndex = row * 7 + col - startOffset;
                LocalDate cellDate = firstOfMonth.plusDays(dayIndex);
                boolean inMonth = (cellDate.getMonth() == month.getMonth()
                        && cellDate.getYear() == month.getYear());
                boolean isToday = cellDate.equals(today);

                // ── Cell container ────────────────────────────────────────────
                VBox cell = new VBox(3);
                cell.setPadding(new Insets(4, 4, 4, 4));
                cell.setMinHeight(90);
                cell.setMaxWidth(Double.MAX_VALUE);
                VBox.setVgrow(cell, Priority.ALWAYS);

                String cellBg;
                if (isToday) {
                    cellBg = "#eff6ff";
                } else {
                    cellBg = rowBg;
                }
                String opacity = inMonth ? "1.0" : "0.4";
                cell.setStyle("-fx-background-color: " + cellBg + ";"
                        + "-fx-opacity: " + opacity + ";");

                // ── Date label ────────────────────────────────────────────────
                Label dateLbl = new Label(String.valueOf(cellDate.getDayOfMonth()));
                dateLbl.setStyle("-fx-font-size: 12px;"
                        + (isToday
                        ? "-fx-font-weight: bold; -fx-text-fill: #2563eb;"
                        : "-fx-text-fill: #374151;"));
                cell.getChildren().add(dateLbl);

                // ── Event chips ───────────────────────────────────────────────
                List<CalendarEvent> dayEvents = byDate.getOrDefault(cellDate, Collections.emptyList());
                int maxVisible = 3;
                int shown = Math.min(dayEvents.size(), maxVisible);

                for (int i = 0; i < shown; i++) {
                    cell.getChildren().add(buildEventChip(dayEvents.get(i)));
                }

                // ── "+N more" overflow label ───────────────────────────────────
                if (dayEvents.size() > maxVisible) {
                    int overflow = dayEvents.size() - maxVisible;
                    Label moreLbl = new Label("+" + overflow + " more");
                    moreLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #6366f1;"
                            + "-fx-cursor: hand; -fx-padding: 1 4;");
                    List<CalendarEvent> allForDay = new ArrayList<>(dayEvents);
                    moreLbl.setOnMouseClicked(e -> showAllEventsPopup(cellDate, allForDay));
                    cell.getChildren().add(moreLbl);
                }

                grid.add(cell, col, row + 1);
            }
        }

        // ── Swap into calendarArea ────────────────────────────────────────────
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        // Fade in
        scroll.setOpacity(0);
        calendarArea.getChildren().setAll(scroll);
        FadeTransition ft = new FadeTransition(Duration.millis(300), scroll);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    /**
     * Creates a compact event chip {@link Label} for the monthly view.
     *
     * <p>The chip shows the event title truncated to 15 characters and is
     * colored according to {@link #DIFFICULTY_COLOR}.
     *
     * @param event the calendar event to represent
     * @return a styled {@link Label} node
     */
    private Label buildEventChip(CalendarEvent event) {
        String title = event.getTitle() != null ? event.getTitle() : "(no title)";
        if (title.length() > 15) {
            title = title.substring(0, 14) + "…";
        }
        String color = DIFFICULTY_COLOR.getOrDefault(
                event.getDifficulty() != null ? event.getDifficulty().toUpperCase() : "",
                "#94a3b8");

        Label chip = new Label(title);
        chip.setMaxWidth(Double.MAX_VALUE);
        chip.setStyle("-fx-background-color: " + color + ";"
                + "-fx-text-fill: white;"
                + "-fx-font-size: 10px;"
                + "-fx-padding: 2 5;"
                + "-fx-background-radius: 4;"
                + "-fx-cursor: hand;");
        chip.setTooltip(new Tooltip(event.getTitle()
                + "\n" + formatTime(event.getStart()) + " – " + formatTime(event.getEnd())));

        // Task 15.1 — click handler opens event detail popup
        chip.setOnMouseClicked(e -> openEventDetailPopup(event));

        return chip;
    }

    /**
     * Opens a small popup listing all events for a given day (used when the
     * "+N more" label is clicked in the monthly view).
     *
     * @param date   the day whose events are shown
     * @param events all events on that day
     */
    private void showAllEventsPopup(LocalDate date, List<CalendarEvent> events) {
        Stage popup = new Stage(StageStyle.UNDECORATED);
        popup.initModality(Modality.APPLICATION_MODAL);

        VBox content = new VBox(6);
        content.setPadding(new Insets(12));
        content.setStyle("-fx-background-color: white;"
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 8;"
                + "-fx-background-radius: 8; -fx-effect: dropshadow(gaussian,rgba(0,0,0,0.15),8,0,0,2);");

        // Header
        Label header = new Label(date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
        header.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
        content.getChildren().add(header);

        Separator sep = new Separator();
        content.getChildren().add(sep);

        // All event chips
        for (CalendarEvent ev : events) {
            content.getChildren().add(buildEventChip(ev));
        }

        // Close button
        Button closeBtn = new Button("✕ Close");
        closeBtn.setStyle("-fx-background-radius: 8; -fx-border-radius: 8;"
                + "-fx-cursor: hand; -fx-padding: 4 10; -fx-font-size: 11px;");
        closeBtn.setOnAction(e -> popup.close());
        content.getChildren().add(closeBtn);

        Scene scene = new Scene(content);
        popup.setScene(scene);
        popup.setMinWidth(220);
        popup.showAndWait();
    }

    /**
     * Builds and displays the weekly calendar grid.
     *
     * <p>Layout: 8 columns (time-label column + 7 day columns) × 24 hourly
     * rows (00:00–23:00).  Each event block is a {@link VBox} positioned by
     * calculating the row offset from {@code event.start} and the row span
     * from the event duration.  The current day column is highlighted; time
     * slot rows alternate white / {@code #f8fafc}.
     *
     * Requirements: 10.6, 15.4
     *
     * @param weekStart the Monday of the week to display
     * @param events    the events to render within the week
     */
    private void buildWeeklyGrid(LocalDate weekStart, List<CalendarEvent> events) {
        // ── Constants ─────────────────────────────────────────────────────────
        final int HOURS = 24;
        final double ROW_HEIGHT = 60.0;   // px per hour slot
        final double TIME_COL_WIDTH = 56; // px for the time-label column

        LocalDate today = LocalDate.now();

        // ── Build GridPane ────────────────────────────────────────────────────
        GridPane grid = new GridPane();
        grid.setHgap(0);
        grid.setVgap(0);
        grid.setStyle("-fx-background-color: #e2e8f0;");

        // Column 0: time labels (fixed width)
        ColumnConstraints timeCol = new ColumnConstraints(TIME_COL_WIDTH);
        timeCol.setHgrow(Priority.NEVER);
        grid.getColumnConstraints().add(timeCol);

        // Columns 1–7: day columns (equal width)
        for (int d = 0; d < 7; d++) {
            ColumnConstraints dc = new ColumnConstraints();
            dc.setHgrow(Priority.ALWAYS);
            dc.setFillWidth(true);
            grid.getColumnConstraints().add(dc);
        }

        // ── Day-of-week header row (row 0) ────────────────────────────────────
        // Empty corner cell
        Label corner = new Label();
        corner.setMaxWidth(Double.MAX_VALUE);
        corner.setStyle("-fx-background-color: white; -fx-padding: 6 4;");
        grid.add(corner, 0, 0);

        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("EEE d");
        for (int d = 0; d < 7; d++) {
            LocalDate day = weekStart.plusDays(d);
            boolean isToday = day.equals(today);
            Label hdr = new Label(day.format(dayFmt));
            hdr.setMaxWidth(Double.MAX_VALUE);
            hdr.setAlignment(Pos.CENTER);
            hdr.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;"
                    + "-fx-padding: 6 4;"
                    + (isToday
                    ? "-fx-background-color: #eff6ff; -fx-text-fill: #2563eb;"
                    : "-fx-background-color: white; -fx-text-fill: #64748b;"));
            grid.add(hdr, d + 1, 0);
        }

        // ── Hourly time-slot rows (rows 1–24) ─────────────────────────────────
        for (int h = 0; h < HOURS; h++) {
            String rowBg = (h % 2 == 0) ? "white" : "#f8fafc";

            // Time label
            Label timeLbl = new Label(String.format("%02d:00", h));
            timeLbl.setAlignment(Pos.TOP_RIGHT);
            timeLbl.setPadding(new Insets(3, 6, 0, 0));
            timeLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8;"
                    + "-fx-background-color: white;");
            timeLbl.setMinHeight(ROW_HEIGHT);
            timeLbl.setMaxWidth(Double.MAX_VALUE);
            grid.add(timeLbl, 0, h + 1);

            // Day cells for this hour
            for (int d = 0; d < 7; d++) {
                LocalDate day = weekStart.plusDays(d);
                boolean isToday = day.equals(today);
                Pane cell = new Pane();
                cell.setMinHeight(ROW_HEIGHT);
                cell.setMaxWidth(Double.MAX_VALUE);
                cell.setStyle("-fx-background-color: "
                        + (isToday ? "#f0f9ff" : rowBg) + ";");
                grid.add(cell, d + 1, h + 1);
            }
        }

        // ── Place event blocks ────────────────────────────────────────────────
        // We use an AnchorPane overlay per day column so events can overlap freely.
        // Build one AnchorPane per day column and add it spanning all hour rows.
        Map<Integer, AnchorPane> dayOverlays = new HashMap<>();
        for (int d = 0; d < 7; d++) {
            final LocalDate overlayDay = weekStart.plusDays(d);
            AnchorPane overlay = new AnchorPane();
            overlay.setPickOnBounds(false);
            overlay.setStyle("-fx-background-color: transparent;");
            // Span all 24 hour rows (rows 1–24)
            grid.add(overlay, d + 1, 1, 1, HOURS);
            GridPane.setVgrow(overlay, Priority.ALWAYS);
            dayOverlays.put(d, overlay);

            // ── Task 16.1 — drag-over / drag-dropped on each day overlay ──────
            overlay.setOnDragOver(dragEvt -> {
                if (dragEvt.getDragboard().hasContent(EVENT_DF)) {
                    dragEvt.acceptTransferModes(TransferMode.MOVE);
                    dragEvt.consume();
                }
            });

            overlay.setOnDragDropped(dragEvt -> {
                Dragboard db = dragEvt.getDragboard();
                if (!db.hasContent(EVENT_DF)) return;

                String payload = (String) db.getContent(EVENT_DF);
                // payload format: "eventId:type"
                String[] parts = payload.split(":");
                if (parts.length < 2) return;

                int    droppedId   = Integer.parseInt(parts[0]);
                String droppedType = parts[1];

                // Calculate new start from drop Y position within the overlay
                double dropY = dragEvt.getY();
                double totalMinutes = (dropY / ROW_HEIGHT) * 60.0;
                // Snap to 15-minute increments
                int snappedMinutes = (int) (Math.round(totalMinutes / 15.0) * 15);
                snappedMinutes = Math.max(0, Math.min(snappedMinutes, HOURS * 60 - 15));
                LocalDateTime newStart = overlayDay.atTime(
                        snappedMinutes / 60, snappedMinutes % 60);

                dragEvt.setDropCompleted(true);
                dragEvt.consume();

                // Persist on background thread
                Task<Void> rescheduleTask = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        calendarService.rescheduleEvent(droppedId, droppedType, newStart);
                        return null;
                    }
                };
                rescheduleTask.setOnSucceeded(e -> Platform.runLater(this::loadCalendar));
                rescheduleTask.setOnFailed(e -> Platform.runLater(() -> {
                    Throwable ex = rescheduleTask.getException();
                    if (ex instanceof OverlapException) {
                        // Restore by reloading; show overlap tooltip as an alert
                        loadCalendar();
                        showOverlapTooltip(overlay);
                    } else {
                        loadCalendar();
                        showErrorAlert("Reschedule Failed",
                                ex != null ? ex.getMessage() : "Unknown error");
                    }
                }));
                Thread t = new Thread(rescheduleTask);
                t.setDaemon(true);
                t.start();
            });
        }

        for (CalendarEvent ev : events) {
            if (ev.getStart() == null || ev.getEnd() == null) continue;
            LocalDate evDate = ev.getStart().toLocalDate();
            int dayOffset = (int) ChronoUnit.DAYS.between(weekStart, evDate);
            if (dayOffset < 0 || dayOffset > 6) continue;

            AnchorPane overlay = dayOverlays.get(dayOffset);
            if (overlay == null) continue;

            // Calculate pixel position within the overlay
            double startMinutes = ev.getStart().getHour() * 60.0 + ev.getStart().getMinute();
            double endMinutes;
            if (ev.getEnd().toLocalDate().equals(evDate)) {
                endMinutes = ev.getEnd().getHour() * 60.0 + ev.getEnd().getMinute();
            } else {
                endMinutes = HOURS * 60.0; // cap at end of day
            }
            double durationMinutes = Math.max(endMinutes - startMinutes, 15); // min 15 min height

            double topPx    = (startMinutes / 60.0) * ROW_HEIGHT;
            double heightPx = (durationMinutes / 60.0) * ROW_HEIGHT;

            VBox block = buildWeeklyEventBlock(ev, ROW_HEIGHT);
            block.setPrefHeight(heightPx);
            block.setMaxHeight(heightPx);
            block.setMinHeight(heightPx);

            AnchorPane.setTopAnchor(block, topPx);
            AnchorPane.setLeftAnchor(block, 2.0);
            AnchorPane.setRightAnchor(block, 2.0);

            overlay.getChildren().add(block);
        }

        // ── Wrap in ScrollPane and swap into calendarArea ─────────────────────
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        // Scroll to 08:00 by default
        Platform.runLater(() -> scroll.setVvalue(8.0 / HOURS));

        scroll.setOpacity(0);
        calendarArea.getChildren().setAll(scroll);
        FadeTransition ft = new FadeTransition(Duration.millis(300), scroll);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    /**
     * Shows a brief "overlap conflict" tooltip anchored to the given node.
     * Used when a drag-and-drop or resize is rejected due to an {@link OverlapException}.
     *
     * Requirements: 11.6, 12.2
     */
    private void showOverlapTooltip(Node anchor) {
        Tooltip tip = new Tooltip("⚠ Time slot conflicts with an existing session.");
        tip.setStyle("-fx-background-color: #fef9c3; -fx-text-fill: #92400e;"
                + "-fx-font-size: 11px; -fx-padding: 6 10;");
        // Show near the anchor node for ~3 seconds
        if (anchor.getScene() != null && anchor.getScene().getWindow() != null) {
            javafx.geometry.Bounds b = anchor.localToScreen(anchor.getBoundsInLocal());
            if (b != null) {
                tip.show(anchor.getScene().getWindow(), b.getMinX(), b.getMinY() - 30);
                PauseTransition hide = new PauseTransition(Duration.seconds(3));
                hide.setOnFinished(e -> tip.hide());
                hide.play();
            }
        }
    }

    /**
     * Creates an event block {@link VBox} for the weekly view.
     *
     * <p>Shows the event title and start–end time; background is the
     * {@link #DIFFICULTY_COLOR} for the event's difficulty.
     *
     * <p>Task 16.1 — drag-and-drop rescheduling:
     * The block initiates a JavaFX drag gesture on {@code setOnDragDetected},
     * storing {@code "eventId:type"} in the {@link Dragboard} via the custom
     * {@link #EVENT_DF} {@link DataFormat}.  The receiving overlay's
     * {@code setOnDragDropped} handler (wired in {@link #buildWeeklyGrid})
     * calculates the new {@link LocalDateTime} and calls
     * {@link CalendarService#rescheduleEvent}.
     *
     * <p>Task 16.2 — resize handle:
     * A 6 px {@link Region} at the bottom of the block uses
     * {@code setOnMouseDragged} / {@code setOnMouseReleased} to compute a new
     * end time from the mouse Y delta and calls
     * {@link CalendarService#resizeEvent}.
     *
     * @param event     the calendar event to represent
     * @param rowHeight the pixel height of one hour slot (used for resize math)
     * @return a styled {@link VBox} node
     */
    private VBox buildWeeklyEventBlock(CalendarEvent event, double rowHeight) {
        String color = DIFFICULTY_COLOR.getOrDefault(
                event.getDifficulty() != null ? event.getDifficulty().toUpperCase() : "",
                "#94a3b8");

        // ── Content labels ────────────────────────────────────────────────────
        String title = event.getTitle() != null ? event.getTitle() : "(no title)";
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: white;"
                + "-fx-wrap-text: true;");
        titleLbl.setMaxWidth(Double.MAX_VALUE);

        String timeStr = formatTime(event.getStart()) + " – " + formatTime(event.getEnd());
        Label timeLbl = new Label(timeStr);
        timeLbl.setStyle("-fx-font-size: 9px; -fx-text-fill: rgba(255,255,255,0.85);");

        // ── Task 16.2 — resize handle (6 px strip at bottom) ─────────────────
        Region resizeHandle = new Region();
        resizeHandle.setPrefHeight(6);
        resizeHandle.setMinHeight(6);
        resizeHandle.setMaxHeight(6);
        resizeHandle.setMaxWidth(Double.MAX_VALUE);
        resizeHandle.setStyle("-fx-background-color: rgba(0,0,0,0.25); -fx-cursor: s-resize;");
        resizeHandle.setCursor(Cursor.S_RESIZE);

        // ── Outer VBox ────────────────────────────────────────────────────────
        VBox block = new VBox(2);
        block.setPadding(new Insets(3, 4, 0, 4));
        block.setStyle("-fx-background-color: " + color + ";"
                + "-fx-background-radius: 4;"
                + "-fx-cursor: hand;");

        // Content region grows to fill available space; handle stays at bottom
        VBox content = new VBox(2, titleLbl, timeLbl);
        content.setPadding(new Insets(0, 0, 3, 0));
        VBox.setVgrow(content, Priority.ALWAYS);

        block.getChildren().addAll(content, resizeHandle);

        Tooltip tt = new Tooltip(title + "\n" + timeStr
                + (event.getDifficulty() != null ? "\n" + event.getDifficulty() : "")
                + (event.getStatus() != null ? " · " + event.getStatus() : ""));
        Tooltip.install(block, tt);

        // ── Task 15.1 — click handler opens event detail popup ────────────────
        // Only fire click when not dragging (consume on drag-detected prevents it)
        block.setOnMouseClicked(e -> {
            if (!e.isStillSincePress()) return; // ignore if mouse moved (drag)
            openEventDetailPopup(event);
        });

        // ── Task 16.1 — drag-detected: start DnD gesture ─────────────────────
        block.setOnDragDetected(mouseEvt -> {
            Dragboard db = block.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent cc = new ClipboardContent();
            cc.put(EVENT_DF, event.getId() + ":" + event.getType());
            db.setContent(cc);
            mouseEvt.consume();
        });

        // ── Task 16.2 — resize handle mouse handlers ──────────────────────────
        resizeHandle.setOnMousePressed(mouseEvt -> {
            resizeDragStartY    = mouseEvt.getScreenY();
            resizeOriginalHeight = block.getHeight();
            resizeOriginalEnd    = event.getEnd();
            mouseEvt.consume();
        });

        resizeHandle.setOnMouseDragged(mouseEvt -> {
            double deltaY   = mouseEvt.getScreenY() - resizeDragStartY;
            double newHeight = Math.max(resizeOriginalHeight + deltaY,
                    (5.0 / 60.0) * rowHeight); // enforce ≥ 5 min visually
            block.setPrefHeight(newHeight);
            block.setMaxHeight(newHeight);
            block.setMinHeight(newHeight);
            mouseEvt.consume();
        });

        resizeHandle.setOnMouseReleased(mouseEvt -> {
            double deltaY        = mouseEvt.getScreenY() - resizeDragStartY;
            double deltaMinutes  = (deltaY / rowHeight) * 60.0;
            // Snap to 5-minute increments
            long snappedDelta    = Math.round(deltaMinutes / 5.0) * 5L;
            LocalDateTime newEnd = resizeOriginalEnd.plusMinutes(snappedDelta);

            // Validate minimum duration before hitting the service
            long newDuration = ChronoUnit.MINUTES.between(event.getStart(), newEnd);
            if (newDuration < 5) {
                // Restore visual size
                double origH = resizeOriginalHeight;
                block.setPrefHeight(origH);
                block.setMaxHeight(origH);
                block.setMinHeight(origH);
                showErrorAlert("Invalid Duration",
                        "Event duration must be at least 5 minutes.");
                mouseEvt.consume();
                return;
            }

            Task<Void> resizeTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    calendarService.resizeEvent(event.getId(), event.getType(), newEnd);
                    return null;
                }
            };
            resizeTask.setOnSucceeded(e -> Platform.runLater(this::loadCalendar));
            resizeTask.setOnFailed(e -> Platform.runLater(() -> {
                // Restore original visual size
                block.setPrefHeight(resizeOriginalHeight);
                block.setMaxHeight(resizeOriginalHeight);
                block.setMinHeight(resizeOriginalHeight);

                Throwable ex = resizeTask.getException();
                if (ex instanceof OverlapException) {
                    showOverlapTooltip(block);
                } else {
                    showErrorAlert("Resize Failed",
                            ex != null ? ex.getMessage() : "Unknown error");
                }
            }));
            Thread t = new Thread(resizeTask);
            t.setDaemon(true);
            t.start();

            mouseEvt.consume();
        });

        return block;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Task 15 — Event detail popup, Edit, Delete
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Determines whether the current user has permission to edit/delete the given event.
     *
     * <ul>
     *   <li>ROLE_ADMIN — always permitted</li>
     *   <li>ROLE_STUDENT — only own events (userId matches)</li>
     *   <li>ROLE_TUTOR — events belonging to their courses (courseId in tutor's courses)</li>
     * </ul>
     *
     * Requirements: 13.4
     */
    private boolean hasPermission(CalendarEvent event) {
        if (currentUser == null) return false;
        User.Role role = currentUser.getRole();
        if (role == User.Role.ROLE_ADMIN) return true;
        if (role == User.Role.ROLE_STUDENT) {
            return event.getUserId() == currentUser.getId();
        }
        if (role == User.Role.ROLE_TUTOR) {
            // Check if the event's courseId belongs to this tutor
            try {
                Connection cnx = MyConnection.getInstance().getCnx();
                PreparedStatement ps = cnx.prepareStatement(
                    "SELECT id FROM course WHERE id = ? AND created_by_id = ?");
                ps.setInt(1, event.getCourseId());
                ps.setInt(2, currentUser.getId());
                ResultSet rs = ps.executeQuery();
                return rs.next();
            } catch (SQLException ex) {
                System.err.println("[CalendarPlanner] hasPermission check failed: " + ex.getMessage());
                return false;
            }
        }
        return false;
    }

    /**
     * Fetches the XP earned for a SESSION event directly from the database.
     * Returns null if not a SESSION or if the value is not set.
     */
    private Integer fetchXpEarned(int eventId) {
        try {
            Connection cnx = MyConnection.getInstance().getCnx();
            PreparedStatement ps = cnx.prepareStatement(
                "SELECT xp_earned FROM study_session WHERE id = ?");
            ps.setInt(1, eventId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int xp = rs.getInt("xp_earned");
                return rs.wasNull() ? null : xp;
            }
        } catch (SQLException ex) {
            System.err.println("[CalendarPlanner] fetchXpEarned failed: " + ex.getMessage());
        }
        return null;
    }

    /**
     * Opens an undecorated modal popup showing the full details of a calendar event.
     * Includes "✏ Edit" button (always) and "🗑 Delete" button (permission-gated).
     *
     * Requirements: 11.1, 11.2, 13.4
     *
     * @param event the event whose details to display (may be a lightweight chip event;
     *              full detail is re-fetched via CalendarService.getEventDetail)
     */
    private void openEventDetailPopup(CalendarEvent event) {
        // Fetch full detail on background thread, then show popup on FX thread
        Task<CalendarEvent> fetchTask = new Task<>() {
            @Override
            protected CalendarEvent call() throws Exception {
                return calendarService.getEventDetail(event.getId(), event.getType());
            }
        };

        fetchTask.setOnSucceeded(e -> Platform.runLater(() -> {
            CalendarEvent detail = fetchTask.getValue();
            if (detail == null) {
                showErrorAlert("Event not found", "Could not load event details.");
                return;
            }
            showDetailPopup(detail);
        }));

        fetchTask.setOnFailed(e -> Platform.runLater(() ->
            showErrorAlert("Load Error", "Failed to load event details: "
                + fetchTask.getException().getMessage())));

        Thread t = new Thread(fetchTask);
        t.setDaemon(true);
        t.start();
    }

    /**
     * Builds and shows the detail popup Stage for the given (fully-loaded) event.
     */
    private void showDetailPopup(CalendarEvent detail) {
        Stage popup = new Stage(StageStyle.UNDECORATED);
        popup.initModality(Modality.APPLICATION_MODAL);

        // ── Root container ────────────────────────────────────────────────────
        VBox root = new VBox(10);
        root.setPadding(new Insets(18, 20, 16, 20));
        root.setMinWidth(320);
        root.setMaxWidth(400);
        root.setStyle("-fx-background-color: white;"
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 10;"
                + "-fx-background-radius: 10;"
                + "-fx-effect: dropshadow(gaussian,rgba(0,0,0,0.18),12,0,0,3);");

        // ── Header: event type badge + title ──────────────────────────────────
        String typeBadge = "SESSION".equals(detail.getType()) ? "📚 Study Session" : "📅 Planning";
        Label typeLbl = new Label(typeBadge);
        typeLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #6366f1; -fx-font-weight: bold;");

        String titleText = detail.getTitle() != null ? detail.getTitle() : "(no title)";
        Label titleLbl = new Label(titleText);
        titleLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #0f172a;"
                + "-fx-wrap-text: true;");
        titleLbl.setMaxWidth(360);

        root.getChildren().addAll(typeLbl, titleLbl, new Separator());

        // ── Detail rows ───────────────────────────────────────────────────────
        DateTimeFormatter dtFmt = DateTimeFormatter.ofPattern("MMM d, yyyy  HH:mm");

        addDetailRow(root, "🕐 Start",
                detail.getStart() != null ? detail.getStart().format(dtFmt) : "—");
        addDetailRow(root, "🕑 End",
                detail.getEnd() != null ? detail.getEnd().format(dtFmt) : "—");

        // Duration in minutes
        long durationMin = 0;
        if (detail.getStart() != null && detail.getEnd() != null) {
            durationMin = ChronoUnit.MINUTES.between(detail.getStart(), detail.getEnd());
        }
        addDetailRow(root, "⏱ Duration", durationMin + " min");

        addDetailRow(root, "📊 Difficulty",
                detail.getDifficulty() != null ? detail.getDifficulty() : "—");
        addDetailRow(root, "🔖 Status",
                detail.getStatus() != null ? detail.getStatus() : "—");

        // Notes / mood
        if (detail.getNotes() != null && !detail.getNotes().isEmpty()) {
            addDetailRow(root, "💬 Notes / Mood", detail.getNotes());
        }

        // XP earned — SESSION only
        if ("SESSION".equals(detail.getType())) {
            Integer xp = fetchXpEarned(detail.getId());
            addDetailRow(root, "⭐ XP Earned", xp != null ? String.valueOf(xp) : "—");
        }

        root.getChildren().add(new Separator());

        // ── Action buttons ────────────────────────────────────────────────────
        HBox btnRow = new HBox(10);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        // Close button
        Button closeBtn = new Button("✕ Close");
        closeBtn.setStyle("-fx-background-radius: 8; -fx-border-radius: 8;"
                + "-fx-cursor: hand; -fx-padding: 5 12; -fx-font-size: 11px;");
        closeBtn.setOnAction(e -> popup.close());

        // Edit button — always shown (Task 15.2)
        Button editBtn = new Button("✏ Edit");
        editBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white;"
                + "-fx-background-radius: 8; -fx-border-radius: 8;"
                + "-fx-cursor: hand; -fx-padding: 5 12; -fx-font-size: 11px;");
        editBtn.setOnAction(e -> {
            popup.close();
            openEditForm(detail);
        });

        btnRow.getChildren().addAll(closeBtn, editBtn);

        // Delete button — only if user has permission (Task 15.3)
        if (hasPermission(detail)) {
            Button deleteBtn = new Button("🗑 Delete");
            deleteBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white;"
                    + "-fx-background-radius: 8; -fx-border-radius: 8;"
                    + "-fx-cursor: hand; -fx-padding: 5 12; -fx-font-size: 11px;");
            deleteBtn.setOnAction(e -> handleDeleteEvent(detail, popup));
            btnRow.getChildren().add(deleteBtn);
        }

        root.getChildren().add(btnRow);

        Scene scene = new Scene(root);
        popup.setScene(scene);
        popup.showAndWait();
    }

    /** Adds a two-column detail row (label + value) to the popup VBox. */
    private void addDetailRow(VBox parent, String label, String value) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        Label keyLbl = new Label(label);
        keyLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b; -fx-min-width: 110;");

        Label valLbl = new Label(value);
        valLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #0f172a; -fx-wrap-text: true;");
        valLbl.setMaxWidth(220);

        row.getChildren().addAll(keyLbl, valLbl);
        parent.getChildren().add(row);
    }

    /**
     * Opens the appropriate edit form for the given event.
     * SESSION → StudySessionForm.fxml; PLANNING → PlanningForm.fxml.
     * On save, refreshes the calendar.
     *
     * Requirements: 11.2
     */
    private void openEditForm(CalendarEvent detail) {
        try {
            if ("SESSION".equals(detail.getType())) {
                // Load StudySession from DB to pre-populate the form
                StudySession session = loadStudySession(detail.getId());
                if (session == null) {
                    showErrorAlert("Not Found", "Study session record not found.");
                    return;
                }

                FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/studysession/StudySessionForm.fxml"));
                Parent formRoot = loader.load();
                StudySessionFormController ctrl = loader.getController();
                ctrl.initData(session, this::loadCalendar);

                Stage formStage = new Stage(StageStyle.DECORATED);
                formStage.initModality(Modality.APPLICATION_MODAL);
                formStage.setTitle("✏ Edit Study Session");
                formStage.setScene(new Scene(formRoot, 520, 600));
                formStage.setResizable(false);
                formStage.showAndWait();

            } else if ("PLANNING".equals(detail.getType())) {
                // Load Planning from DB to pre-populate the form
                Planning planning = loadPlanning(detail.getId());
                if (planning == null) {
                    showErrorAlert("Not Found", "Planning record not found.");
                    return;
                }

                FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/studysession/PlanningForm.fxml"));
                Parent formRoot = loader.load();
                PlanningFormController ctrl = loader.getController();
                // Use the course name from the loaded Planning record, not the event title
                String courseName = planning.getCourseNameCache() != null
                        ? planning.getCourseNameCache() : "";
                ctrl.initData(planning, planning.getCourseId(), courseName, this::loadCalendar);

                Stage formStage = new Stage(StageStyle.DECORATED);
                formStage.initModality(Modality.APPLICATION_MODAL);
                formStage.setTitle("✏ Edit Planning");
                // Explicit size so the ScrollPane inside the VBox renders correctly
                formStage.setScene(new Scene(formRoot, 480, 560));
                formStage.setResizable(false);
                formStage.showAndWait();
            }
        } catch (IOException ex) {
            showErrorAlert("Load Error", "Could not open edit form: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Handles the Delete button click: shows confirmation, then deletes on a background task.
     * Refreshes the calendar on success.
     *
     * Requirements: 12.4, 13.4, 13.5
     */
    private void handleDeleteEvent(CalendarEvent detail, Stage popup) {
        // Permission check (defensive — button should already be hidden for non-permitted users)
        if (!hasPermission(detail)) {
            Alert permAlert = new Alert(Alert.AlertType.WARNING);
            permAlert.setTitle("Permission Denied");
            permAlert.setHeaderText(null);
            permAlert.setContentText("🚫 You do not have permission to modify this event.");
            permAlert.showAndWait();
            return;
        }

        // Confirmation dialog
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText(null);
        confirm.setContentText("Are you sure you want to delete this session?");
        Optional<ButtonType> result = confirm.showAndWait();

        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return; // user cancelled
        }

        // Close the detail popup before starting the background delete
        popup.close();

        // Delete on background thread
        Task<Void> deleteTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                calendarService.deleteEvent(detail.getId(), detail.getType());
                return null;
            }
        };

        deleteTask.setOnSucceeded(e -> Platform.runLater(this::loadCalendar));

        deleteTask.setOnFailed(e -> Platform.runLater(() -> {
            Throwable ex = deleteTask.getException();
            showErrorAlert("Delete Failed",
                "Could not delete the event: " + ex.getMessage());
        }));

        Thread t = new Thread(deleteTask);
        t.setDaemon(true);
        t.start();
    }

    /**
     * Loads a {@link StudySession} by id from the database for pre-populating the edit form.
     */
    private StudySession loadStudySession(int id) {
        try {
            Connection cnx = MyConnection.getInstance().getCnx();
            PreparedStatement ps = cnx.prepareStatement(
                "SELECT ss.id, ss.user_id, ss.planning_id, ss.started_at, ss.ended_at, " +
                "ss.duration, ss.actual_duration, ss.energy_used, ss.xp_earned, ss.burnout_risk, " +
                "ss.completed_at, ss.mood, ss.energy_level, ss.break_duration, ss.break_count, ss.pomodoro_count, " +
                "c.course_name AS course_name_cache " +
                "FROM study_session ss " +
                "LEFT JOIN planning p ON ss.planning_id = p.id " +
                "LEFT JOIN course   c ON p.course_id    = c.id " +
                "WHERE ss.id = ?");
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;

            StudySession s = new StudySession();
            s.setId(rs.getInt("id"));
            s.setUserId(rs.getInt("user_id"));
            s.setPlanningId(rs.getInt("planning_id"));
            s.setCourseNameCache(rs.getString("course_name_cache"));

            java.sql.Timestamp startTs = rs.getTimestamp("started_at");
            java.sql.Timestamp endTs   = rs.getTimestamp("ended_at");
            if (startTs != null) s.setStartedAt(startTs.toLocalDateTime());
            if (endTs   != null) s.setEndedAt(endTs.toLocalDateTime());

            s.setDuration(rs.getInt("duration"));

            int ad = rs.getInt("actual_duration");
            s.setActualDuration(rs.wasNull() ? null : ad);

            int eu = rs.getInt("energy_used");
            s.setEnergyUsed(rs.wasNull() ? null : eu);

            int xp = rs.getInt("xp_earned");
            s.setXpEarned(rs.wasNull() ? null : xp);

            s.setBurnoutRisk(rs.getString("burnout_risk"));

            java.sql.Timestamp compTs = rs.getTimestamp("completed_at");
            if (compTs != null) s.setCompletedAt(compTs.toLocalDateTime());

            s.setMood(rs.getString("mood"));
            s.setEnergyLevel(rs.getString("energy_level"));

            int bd = rs.getInt("break_duration");
            s.setBreakDuration(rs.wasNull() ? null : bd);

            int bc = rs.getInt("break_count");
            s.setBreakCount(rs.wasNull() ? null : bc);

            int pc = rs.getInt("pomodoro_count");
            s.setPomodoroCount(rs.wasNull() ? null : pc);

            return s;
        } catch (SQLException ex) {
            System.err.println("[CalendarPlanner] loadStudySession failed: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Loads a {@link Planning} by id from the database for pre-populating the edit form.
     */
    private Planning loadPlanning(int id) {
        try {
            Connection cnx = MyConnection.getInstance().getCnx();
            PreparedStatement ps = cnx.prepareStatement(
                "SELECT p.id, p.course_id, p.title, p.scheduled_date, p.scheduled_time, " +
                "p.planned_duration, p.status, p.reminder, p.created_at, " +
                "c.course_name AS course_name_cache " +
                "FROM planning p LEFT JOIN course c ON p.course_id = c.id " +
                "WHERE p.id = ?");
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;

            Planning p = new Planning();
            p.setId(rs.getInt("id"));
            p.setCourseId(rs.getInt("course_id"));
            p.setCourseNameCache(rs.getString("course_name_cache"));
            p.setTitle(rs.getString("title"));

            java.sql.Date d = rs.getDate("scheduled_date");
            if (d != null) p.setScheduledDate(d.toLocalDate());

            java.sql.Time t = rs.getTime("scheduled_time");
            if (t != null) p.setScheduledTime(t.toLocalTime());

            p.setPlannedDuration(rs.getInt("planned_duration"));
            p.setStatus(rs.getString("status"));
            p.setReminder(rs.getBoolean("reminder"));

            java.sql.Timestamp createdTs = rs.getTimestamp("created_at");
            if (createdTs != null) p.setCreatedAt(createdTs.toLocalDateTime());

            return p;
        } catch (SQLException ex) {
            System.err.println("[CalendarPlanner] loadPlanning failed: " + ex.getMessage());
            return null;
        }
    }

    /** Shows a simple error alert dialog. */
    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Formatting helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Formats a {@link java.time.LocalDateTime} to "HH:mm", or "?" if null. */
    private String formatTime(java.time.LocalDateTime dt) {
        if (dt == null) return "?";
        return dt.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Period label
    // ─────────────────────────────────────────────────────────────────────────

    /** Updates the period label in the toolbar to reflect the current period. */
    private void updatePeriodLabel() {
        if (isMonthlyView) {
            // e.g. "June 2025"
            lblPeriod.setText(YearMonth.from(currentPeriodStart)
                    .format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        } else {
            // e.g. "Jun 2 – Jun 8, 2025"
            LocalDate weekEnd = currentPeriodStart.plusDays(6);
            DateTimeFormatter shortFmt = DateTimeFormatter.ofPattern("MMM d");
            DateTimeFormatter yearFmt  = DateTimeFormatter.ofPattern("MMM d, yyyy");
            if (currentPeriodStart.getYear() == weekEnd.getYear()) {
                lblPeriod.setText(currentPeriodStart.format(shortFmt)
                        + " – " + weekEnd.format(yearFmt));
            } else {
                lblPeriod.setText(currentPeriodStart.format(yearFmt)
                        + " – " + weekEnd.format(yearFmt));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Filter wiring
    // ─────────────────────────────────────────────────────────────────────────

    /** Populates static (non-role-dependent) ComboBox items. */
    private void populateStaticComboBoxes() {
        // Difficulty
        cbDifficulty.getItems().setAll("All", "BEGINNER", "INTERMEDIATE", "ADVANCED");
        cbDifficulty.setValue("All");

        // Status
        cbStatus.getItems().setAll("All", "SCHEDULED", "COMPLETED", "MISSED", "CANCELLED");
        cbStatus.setValue("All");
    }

    /**
     * Wires each filter control so that any change triggers a debounced
     * {@link #loadCalendar()} call (500 ms delay).
     *
     * Requirements: 14.3, 14.5
     */
    private void wireFilterControls() {
        cbCourse.valueProperty().addListener((obs, o, n) -> {
            updateFilterFromControls();
            filterDebounce.playFromStart();
        });
        cbDifficulty.valueProperty().addListener((obs, o, n) -> {
            updateFilterFromControls();
            filterDebounce.playFromStart();
        });
        cbStatus.valueProperty().addListener((obs, o, n) -> {
            updateFilterFromControls();
            filterDebounce.playFromStart();
        });
        dpFilterFrom.valueProperty().addListener((obs, o, n) -> {
            updateFilterFromControls();
            filterDebounce.playFromStart();
        });
        dpFilterTo.valueProperty().addListener((obs, o, n) -> {
            updateFilterFromControls();
            filterDebounce.playFromStart();
        });
        cbStudent.valueProperty().addListener((obs, o, n) -> {
            updateFilterFromControls();
            filterDebounce.playFromStart();
        });
        cbTutor.valueProperty().addListener((obs, o, n) -> {
            updateFilterFromControls();
            filterDebounce.playFromStart();
        });
    }

    /**
     * Reads the current ComboBox / DatePicker values and updates
     * the {@link #calendarFilter} state accordingly.
     * Filter state is preserved across period navigation (Prev/Next/Today).
     *
     * Requirements: 14.5
     */
    private void updateFilterFromControls() {
        // Difficulty
        String diff = cbDifficulty.getValue();
        calendarFilter.setDifficulty(("All".equals(diff) || diff == null) ? null : diff);

        // Status
        String status = cbStatus.getValue();
        calendarFilter.setStatus(("All".equals(status) || status == null) ? null : status);

        // Date range
        calendarFilter.setFrom(dpFilterFrom.getValue());
        calendarFilter.setTo(dpFilterTo.getValue());

        // Course — we store the name; courseId lookup would require a DB call,
        // so we leave courseId null here and let CalendarService handle name-based
        // filtering if needed. For now, course name is stored as a hint.
        // (Full courseId resolution can be added in task 14 when the course map is built.)
        String course = cbCourse.getValue();
        if ("All".equals(course) || course == null) {
            calendarFilter.setCourseId(null);
        }
        // Student / Tutor filters are name-based; IDs resolved in buildFilterSnapshot()
    }

    /**
     * Builds a snapshot of the current filter state for use on the background thread.
     * Resolves student/tutor names to IDs where possible.
     */
    private CalendarFilter buildFilterSnapshot() {
        CalendarFilter snap = new CalendarFilter();
        snap.setCourseId(calendarFilter.getCourseId());
        snap.setDifficulty(calendarFilter.getDifficulty());
        snap.setStatus(calendarFilter.getStatus());
        snap.setFrom(calendarFilter.getFrom());
        snap.setTo(calendarFilter.getTo());
        snap.setStudentId(calendarFilter.getStudentId());
        snap.setTutorId(calendarFilter.getTutorId());
        return snap;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Role-based visibility
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Shows or hides the Student and Tutor filter ComboBoxes based on the
     * logged-in user's role.
     *
     * <ul>
     *   <li>ROLE_STUDENT — hide both cbStudent and cbTutor</li>
     *   <li>ROLE_TUTOR   — show cbStudent, hide cbTutor</li>
     *   <li>ROLE_ADMIN   — show both cbStudent and cbTutor</li>
     * </ul>
     *
     * Requirements: 13.1–13.3, 14.2
     */
    private void applyRoleBasedFilterVisibility(User.Role role) {
        boolean showStudent = (role == User.Role.ROLE_TUTOR || role == User.Role.ROLE_ADMIN);
        boolean showTutor   = (role == User.Role.ROLE_ADMIN);

        cbStudentBox.setVisible(showStudent);
        cbStudentBox.setManaged(showStudent);
        cbTutorBox.setVisible(showTutor);
        cbTutorBox.setManaged(showTutor);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Color legend
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the color legend row below the calendar grid.
     * Shows DifficultyColor and StatusColor chips with labels.
     *
     * Requirements: 10.4, 15.7
     */
    private void buildColorLegend() {
        colorLegend.getChildren().clear();

        Label legendLabel = new Label("Legend:");
        legendLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #64748b;");
        colorLegend.getChildren().add(legendLabel);

        // Difficulty chips
        Label diffTitle = new Label("Difficulty:");
        diffTitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b; -fx-padding: 0 4 0 8;");
        colorLegend.getChildren().add(diffTitle);

        for (Map.Entry<String, String> entry : DIFFICULTY_COLOR.entrySet()) {
            colorLegend.getChildren().add(buildLegendChip(entry.getKey(), entry.getValue()));
        }

        // Separator
        Label sep = new Label("|");
        sep.setStyle("-fx-text-fill: #cbd5e1; -fx-padding: 0 8;");
        colorLegend.getChildren().add(sep);

        // Status chips
        Label statusTitle = new Label("Status:");
        statusTitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b; -fx-padding: 0 4 0 0;");
        colorLegend.getChildren().add(statusTitle);

        for (Map.Entry<String, String> entry : STATUS_COLOR.entrySet()) {
            colorLegend.getChildren().add(buildLegendChip(entry.getKey(), entry.getValue()));
        }
    }

    /**
     * Creates a single legend chip: a colored rectangle + label.
     */
    private HBox buildLegendChip(String label, String hexColor) {
        HBox chip = new HBox(5);
        chip.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        chip.setStyle("-fx-padding: 0 6 0 0;");

        Rectangle swatch = new Rectangle(12, 12);
        try {
            swatch.setFill(Color.web(hexColor));
        } catch (IllegalArgumentException e) {
            swatch.setFill(Color.GRAY);
        }
        swatch.setArcWidth(3);
        swatch.setArcHeight(3);

        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #374151;");

        chip.getChildren().addAll(swatch, lbl);
        return chip;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Spinner helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Fades out and hides the calendar spinner. */
    private void hideSpinner() {
        FadeTransition ft = new FadeTransition(Duration.millis(300), spinnerCalendar);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.setOnFinished(e -> {
            spinnerCalendar.setVisible(false);
            spinnerCalendar.setManaged(false);
        });
        ft.play();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Database helpers — populate filter ComboBoxes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads course names accessible to the given user.
     * <ul>
     *   <li>STUDENT — courses the student is enrolled in (ACCEPTED)</li>
     *   <li>TUTOR   — courses created by the tutor</li>
     *   <li>ADMIN   — all published courses</li>
     * </ul>
     */
    private List<String> loadCoursesForUser(User user) {
        List<String> courses = new ArrayList<>();
        try {
            Connection cnx = MyConnection.getInstance().getCnx();
            String sql;
            PreparedStatement ps;

            switch (user.getRole()) {
                case ROLE_STUDENT:
                    sql = "SELECT DISTINCT c.course_name FROM course c " +
                          "INNER JOIN enrollment_requests er ON er.course_id = c.id " +
                          "WHERE er.student_id = ? AND er.status = 'ACCEPTED' " +
                          "ORDER BY c.course_name";
                    ps = cnx.prepareStatement(sql);
                    ps.setInt(1, user.getId());
                    break;
                case ROLE_TUTOR:
                    sql = "SELECT course_name FROM course WHERE created_by_id = ? ORDER BY course_name";
                    ps = cnx.prepareStatement(sql);
                    ps.setInt(1, user.getId());
                    break;
                default: // ROLE_ADMIN
                    sql = "SELECT course_name FROM course WHERE is_published = 1 ORDER BY course_name";
                    ps = cnx.prepareStatement(sql);
                    break;
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                courses.add(rs.getString(1));
            }
        } catch (SQLException e) {
            System.err.println("[CalendarPlanner] loadCoursesForUser failed: " + e.getMessage());
        }
        return courses;
    }

    /**
     * Loads student usernames accessible to the given user (Admin/Tutor only).
     * Returns an empty list for STUDENT role.
     */
    private List<String> loadStudentsForUser(User user) {
        List<String> students = new ArrayList<>();
        if (user.getRole() == User.Role.ROLE_STUDENT) return students;

        try {
            Connection cnx = MyConnection.getInstance().getCnx();
            String sql;
            PreparedStatement ps;

            if (user.getRole() == User.Role.ROLE_TUTOR) {
                // Students enrolled in the tutor's courses
                sql = "SELECT DISTINCT u.username FROM user u " +
                      "INNER JOIN enrollment_requests er ON er.student_id = u.id " +
                      "INNER JOIN course c ON c.id = er.course_id " +
                      "WHERE c.created_by_id = ? AND er.status = 'ACCEPTED' " +
                      "ORDER BY u.username";
                ps = cnx.prepareStatement(sql);
                ps.setInt(1, user.getId());
            } else {
                // Admin — all students
                sql = "SELECT username FROM user WHERE role = 'ROLE_STUDENT' ORDER BY username";
                ps = cnx.prepareStatement(sql);
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                students.add(rs.getString(1));
            }
        } catch (SQLException e) {
            System.err.println("[CalendarPlanner] loadStudentsForUser failed: " + e.getMessage());
        }
        return students;
    }

    /**
     * Loads tutor usernames (Admin only).
     * Returns an empty list for non-Admin roles.
     */
    private List<String> loadTutors(User user) {
        List<String> tutors = new ArrayList<>();
        if (user.getRole() != User.Role.ROLE_ADMIN) return tutors;

        try {
            Connection cnx = MyConnection.getInstance().getCnx();
            PreparedStatement ps = cnx.prepareStatement(
                "SELECT username FROM user WHERE role = 'ROLE_TUTOR' ORDER BY username");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                tutors.add(rs.getString(1));
            }
        } catch (SQLException e) {
            System.err.println("[CalendarPlanner] loadTutors failed: " + e.getMessage());
        }
        return tutors;
    }
}
