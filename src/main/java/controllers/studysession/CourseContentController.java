package controllers.studysession;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import models.studysession.Course;
import models.studysession.PdfResource;
import models.studysession.StudentProgress;
import services.api.VideoResult;
import services.api.WikiSummary;
import services.api.YouTubeService;
import services.api.WikipediaService;
import services.studysession.EnrollmentService;
import services.studysession.PdfResourceService;
import services.studysession.StudentProgressService;
import utils.EmojiUtil;
import utils.UserSession;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the Course Content Page (CourseContentView.fxml).
 *
 * Provides:
 *  - CourseAccessGuard (12.2)
 *  - Pomodoro timer with focus/break modes (12.3)
 *  - Per-minute study progress tracking (12.4)
 *  - Async YouTube video loading (12.5)
 *  - Async Wikipedia summary loading (12.6)
 *  - PDF resource listing with search (12.7)
 *  - Progress panel refresh (12.8)
 *  - Window close handler to stop timeline (12.9)
 *
 * Feature: course-learning-experience
 */
public class CourseContentController implements Initializable {

    // ── FXML fields (12.1) ────────────────────────────────────────────────────

    @FXML private Label lblCourseTitle;
    @FXML private Label lblTimerDisplay;
    @FXML private Label lblCycleCount;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblProgressPct;
    @FXML private Label lblLastActivity;
    @FXML private Button btnStart;
    @FXML private Button btnPause;
    @FXML private Button btnReset;
    @FXML private VBox vboxVideos;
    @FXML private VBox vboxWiki;
    @FXML private VBox vboxResources;
    @FXML private TextField txtResourceSearch;
    @FXML private Label lblStatus;

    // Additional FXML fields present in the FXML
    @FXML private Label lblCourseDescription;
    @FXML private Label lblCategory;
    @FXML private Label lblDifficulty;
    @FXML private Label lblDuration;

    // ── Energy bar FXML fields ────────────────────────────────────────────────
    @FXML private javafx.scene.layout.StackPane energyTrack;
    @FXML private javafx.scene.layout.Region    energyFill;
    @FXML private Label lblEnergyValue;
    @FXML private Label lblEnergyPct;
    @FXML private Label lblEnergyRegen;
    @FXML private Label lblEnergyTimer;
    @FXML private Label lblEnergyWarn;

    // AI Study Assistant controller (injected via fx:include)
    @FXML private AiStudyAssistantController aiAssistantController;

    // ── Services ──────────────────────────────────────────────────────────────

    private final StudentProgressService progressService = new StudentProgressService();
    private final PdfResourceService pdfService = new PdfResourceService();
    private final YouTubeService youTubeService = new YouTubeService();
    private final WikipediaService wikipediaService = new WikipediaService();
    private final EnrollmentService enrollmentService = new EnrollmentService();
    private final services.gamification.EnergyService energyService = new services.gamification.EnergyService();

    // ── State ─────────────────────────────────────────────────────────────────

    private Course course;
    private int userId;
    private int courseId;

    // Pomodoro timer state (12.3)
    private Timeline timeline;
    // Energy countdown state
    private javafx.animation.Timeline energyCountdownTimer;
    private int secondsUntilRegen = 0;
    // Local energy value — updated on drain without hitting DB every second
    private int currentEnergy = 100;
    private int remainingSeconds = 25 * 60;   // 1500 seconds = 25:00
    private boolean isFocusMode = true;
    private int cycleCount = 0;
    private int minuteCounter = 0;            // counts seconds toward next minute increment

    // PDF resources (12.7)
    private List<PdfResource> allPdfResources = new ArrayList<>();

    // ── Initializable ─────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Wire timer button handlers
        btnStart.setOnAction(e -> startTimer());
        btnPause.setOnAction(e -> pauseTimer());
        btnReset.setOnAction(e -> resetTimer());

        // Initialize timer display
        updateTimerDisplay();
    }

    // ── 12.2 — initData ───────────────────────────────────────────────────────

    /**
     * Called by the opening controller after the FXML is loaded.
     * Applies access guard, populates the page, and starts async loading.
     */
    public void initData(Course course) {
        this.course = course;
        this.userId = UserSession.getInstance().getUserId();
        this.courseId = course.getId();

        // Apply CourseAccessGuard
        String currentRole = UserSession.getInstance().getRole();
        if ("ROLE_STUDENT".equals(currentRole)) {
            boolean isAdminOwned = "ROLE_ADMIN".equals(course.getCreatorRole());
            if (!isAdminOwned) {
                try {
                    String enrollmentStatus = enrollmentService.getEnrollmentStatus(courseId, userId);
                    if (!"ACCEPTED".equals(enrollmentStatus)) {
                        showAccessDenied();
                        return;
                    }
                } catch (SQLException e) {
                    System.err.println("[CourseContentController] Enrollment check failed: " + e.getMessage());
                    showAccessDenied();
                    return;
                }
            }
        }

        // Set window title and heading
        lblCourseTitle.setText(course.getCourseName());

        // Populate content card
        String desc = course.getDescription();
        if (desc == null || desc.isBlank()) {
            desc = "This is the content of the course. Your instructor will add detailed materials here.";
        }
        if (lblCourseDescription != null) lblCourseDescription.setText(desc);

        // Use EmojiUtil for consistent emoji display
        if (lblCategory != null) {
            ImageView categoryIcon = EmojiUtil.getEmojiImage("🏷", 14);
            if (categoryIcon != null) {
                lblCategory.setGraphic(categoryIcon);
                lblCategory.setText(" " + (course.getCategory() != null ? course.getCategory() : "—"));
            } else {
                lblCategory.setText("🏷 " + (course.getCategory() != null ? course.getCategory() : "—"));
            }
        }
        if (lblDifficulty != null) lblDifficulty.setText(course.getDifficulty() != null ? course.getDifficulty() : "—");
        // Use EmojiUtil for duration emoji
        if (lblDuration != null) {
            ImageView durationIcon = EmojiUtil.getEmojiImage("⏱️", 14);
            if (durationIcon != null) {
                lblDuration.setGraphic(durationIcon);
                lblDuration.setText(" " + course.getEstimatedDuration() + " min");
            } else {
                lblDuration.setText("⏱ " + course.getEstimatedDuration() + " min");
            }
        }

        // Load progress
        try {
            progressService.getOrCreate(userId, courseId);
            refreshProgressPanel();
        } catch (SQLException e) {
            System.err.println("[CourseContentController] Failed to load progress: " + e.getMessage());
        }

        // Register close handler (12.9)
        Stage stage = getStage();
        if (stage != null) {
            stage.setOnCloseRequest(e -> {
                if (timeline != null) timeline.stop();
            });
        }

        // Start async content loading
        loadYouTubeVideos();
        loadWikipediaSummary();
        loadPdfResources();

        // Load energy bar
        loadEnergyAsync();

        // Initialize AI assistant with course context (Requirements 2.1, 15.1, 15.3)
        if (aiAssistantController != null) {
            aiAssistantController.initData(course);
        }
    }

    // ── Energy bar ────────────────────────────────────────────────────────────

    private void loadEnergyAsync() {
        int uid = utils.SessionManager.getCurrentUserId();
        if (uid <= 1) uid = userId;
        if (uid <= 0) return;
        final int finalUid = uid;
        Thread t = new Thread(() -> {
            try {
                int[] snap = energyService.getEnergySnapshot(finalUid);
                javafx.application.Platform.runLater(() -> {
                    currentEnergy = snap[0];
                    updateEnergyUI(snap[0], snap[1]);
                });
            } catch (Exception e) {
                System.err.println("[Energy] " + e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void updateEnergyUI(int energy, int secsUntilRegen) {
        energy = Math.max(0, Math.min(100, energy));
        secondsUntilRegen = secsUntilRegen;
        double pct = energy / 100.0;

        if (lblEnergyValue != null) {
            lblEnergyValue.setText(energy + " / 100");
            String col = energy > 50 ? "#27ae60" : energy > 20 ? "#d97706" : "#e53e3e";
            String bg  = energy > 50 ? "#f0fff4" : energy > 20 ? "#fffbeb" : "#fff5f5";
            lblEnergyValue.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:" + col +
                                    ";-fx-background-color:" + bg + ";-fx-background-radius:20;-fx-padding:2 10;");
        }
        if (energyFill != null) {
            // Bind to track width once it's laid out
            if (energyTrack != null && energyTrack.getWidth() > 0) {
                energyFill.setPrefWidth(energyTrack.getWidth() * pct);
            } else if (energyTrack != null) {
                final int finalEnergy = energy;
                energyTrack.widthProperty().addListener((obs, o, n) -> {
                    if (n.doubleValue() > 0) energyFill.setPrefWidth(n.doubleValue() * (finalEnergy / 100.0));
                });
            }
            String gradient = energy > 50 ? "linear-gradient(to right,#43e97b,#38f9d7)"
                            : energy > 20 ? "linear-gradient(to right,#f6d365,#fda085)"
                            :               "linear-gradient(to right,#fc5c7d,#6a3093)";
            energyFill.setStyle("-fx-background-color:" + gradient + ";-fx-background-radius:7;");
        }
        if (lblEnergyPct != null) lblEnergyPct.setText(energy + "%");
        if (lblEnergyRegen != null) {
            lblEnergyRegen.setText(energy >= 100 ? "Energy Full! Ready to study" : "\u27F3 +1 every 5 min");
            lblEnergyRegen.setStyle("-fx-font-size:11px;-fx-text-fill:" + (energy >= 100 ? "#27ae60" : "#718096") + ";");
        }
        if (lblEnergyTimer != null) {
            lblEnergyTimer.setText(energy >= 100 ? "" : "Next: " + formatEnergySeconds(secsUntilRegen));
        }
        if (lblEnergyWarn != null) {
            boolean show = energy <= 20;
            lblEnergyWarn.setVisible(show); lblEnergyWarn.setManaged(show);
            if (show) {
                lblEnergyWarn.setText(energy <= 0
                    ? "\u26A0 Energy depleted! Play mini games to restore."
                    : "\u26A0 Low energy! Play a mini game to restore it faster.");
                lblEnergyWarn.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:" +
                                       (energy <= 0 ? "#e53e3e" : "#d97706") + ";");
            }
        }

        // Start/restart countdown ticker
        if (energyCountdownTimer != null) energyCountdownTimer.stop();
        if (energy < 100 && secsUntilRegen > 0) {
            energyCountdownTimer = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> {
                    if (secondsUntilRegen <= 0) { loadEnergyAsync(); return; }
                    secondsUntilRegen--;
                    if (lblEnergyTimer != null)
                        lblEnergyTimer.setText("Next: " + formatEnergySeconds(secondsUntilRegen));
                })
            );
            energyCountdownTimer.setCycleCount(javafx.animation.Timeline.INDEFINITE);
            energyCountdownTimer.play();
        }
    }

    private String formatEnergySeconds(int secs) {
        if (secs <= 0) return "0s";
        int m = secs / 60, s = secs % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }

    // ── 12.3 — Pomodoro Timer ─────────────────────────────────────────────────

    @FXML
    private void handleStart() {
        startTimer();
    }

    @FXML
    private void handlePause() {
        pauseTimer();
    }

    @FXML
    private void handleReset() {
        resetTimer();
    }

    private void startTimer() {
        if (timeline != null && timeline.getStatus() == Timeline.Status.RUNNING) {
            return; // already running
        }

        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> onTimerTick()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void pauseTimer() {
        if (timeline != null) {
            timeline.pause();
        }
    }

    private void resetTimer() {
        if (timeline != null) {
            timeline.stop();
        }
        isFocusMode = true;
        remainingSeconds = 25 * 60;
        minuteCounter = 0;
        updateTimerDisplay();
    }

    private void onTimerTick() {
        remainingSeconds--;

        // Track minutes for study progress (only in focus mode)
        if (isFocusMode) {
            minuteCounter++;
            if (minuteCounter >= 60) {
                minuteCounter = 0;
                incrementStudyMinute();
                // ── Drain 1 energy per minute of focus study (mirrors Pi_web depleteEnergy) ──
                drainEnergyOnMinute();
            }
        }

        updateTimerDisplay();

        if (remainingSeconds <= 0) {
            if (isFocusMode) {
                onFocusSessionComplete();
            } else {
                onBreakComplete();
            }
        }
    }

    /** Drain 1 energy point per study minute. Updates UI immediately, persists async. */
    private void drainEnergyOnMinute() {
        // Optimistic local update — no DB read needed
        currentEnergy = Math.max(0, currentEnergy - 1);
        // Update bar UI immediately on FX thread (we're already on it)
        updateEnergyBarOnly(currentEnergy);
        // Persist to DB on background thread
        int uid = utils.SessionManager.getCurrentUserId();
        if (uid <= 1) uid = userId;
        final int finalUid = uid;
        Thread t = new Thread(() -> {
            try {
                energyService.drainEnergy(finalUid, 1);
            } catch (Exception e) {
                System.err.println("[Energy] Drain failed: " + e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /**
     * Update only the visual energy bar nodes without touching the countdown timer.
     * Called on every drain tick so the bar animates smoothly.
     */
    private void updateEnergyBarOnly(int energy) {
        energy = Math.max(0, Math.min(100, energy));
        double pct = energy / 100.0;

        if (lblEnergyValue != null) {
            lblEnergyValue.setText(energy + " / 100");
            String col = energy > 50 ? "#27ae60" : energy > 20 ? "#d97706" : "#e53e3e";
            String bg  = energy > 50 ? "#f0fff4" : energy > 20 ? "#fffbeb" : "#fff5f5";
            lblEnergyValue.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:" + col +
                                    ";-fx-background-color:" + bg + ";-fx-background-radius:20;-fx-padding:2 10;");
        }
        if (energyFill != null && energyTrack != null && energyTrack.getWidth() > 0) {
            energyFill.setPrefWidth(energyTrack.getWidth() * pct);
            String gradient = energy > 50 ? "linear-gradient(to right,#43e97b,#38f9d7)"
                            : energy > 20 ? "linear-gradient(to right,#f6d365,#fda085)"
                            :               "linear-gradient(to right,#fc5c7d,#6a3093)";
            energyFill.setStyle("-fx-background-color:" + gradient + ";-fx-background-radius:7;");
        }
        if (lblEnergyPct != null) lblEnergyPct.setText(energy + "%");
        if (lblEnergyRegen != null) {
            lblEnergyRegen.setText(energy >= 100 ? "Energy Full! Ready to study"
                                                 : "\u27F3 +1 every 5 min (mini games restore faster)");
            lblEnergyRegen.setStyle("-fx-font-size:11px;-fx-text-fill:" + (energy >= 100 ? "#27ae60" : "#718096") + ";");
        }
        if (lblEnergyWarn != null) {
            boolean show = energy <= 20;
            lblEnergyWarn.setVisible(show); lblEnergyWarn.setManaged(show);
            if (show) {
                lblEnergyWarn.setText(energy <= 0
                    ? "\u26A0 Energy depleted! Play mini games to restore."
                    : "\u26A0 Low energy! Play a mini game to restore it faster.");
                lblEnergyWarn.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:" +
                                       (energy <= 0 ? "#e53e3e" : "#d97706") + ";");
            }
        }
    }

    private void onFocusSessionComplete() {
        cycleCount++;
        lblCycleCount.setText("🍅 Cycles: " + cycleCount);

        // Persist cycle count
        try {
            progressService.incrementPomodoroCycles(userId, courseId, 1);
        } catch (SQLException e) {
            System.err.println("[CourseContentController] Failed to increment pomodoro cycles: " + e.getMessage());
        }

        showNotification("🍅 Focus session complete! Take a break.");

        // Switch to break mode (5:00 = 300 seconds)
        isFocusMode = false;
        remainingSeconds = 5 * 60;
        minuteCounter = 0;
        updateTimerDisplay();
    }

    private void onBreakComplete() {
        showNotification("☕ Break over! Ready for the next focus session.");

        // Switch back to focus mode (25:00 = 1500 seconds)
        isFocusMode = true;
        remainingSeconds = 25 * 60;
        updateTimerDisplay();
    }

    private void updateTimerDisplay() {
        int minutes = remainingSeconds / 60;
        int seconds = remainingSeconds % 60;
        lblTimerDisplay.setText(String.format("%02d:%02d", minutes, seconds));
    }

    // ── 12.4 — incrementStudyMinute ───────────────────────────────────────────

    private void incrementStudyMinute() {
        try {
            progressService.incrementMinutes(userId, courseId, 1);
            refreshProgressPanel();

            // Check for completion
            StudentProgress progress = progressService.getProgress(userId, courseId);
            if (progress != null && progress.getProgressPercentage() >= 100) {
                showCompletionBanner();
            }
        } catch (SQLException e) {
            System.err.println("[CourseContentController] Failed to increment study minutes: " + e.getMessage());
        }
    }

    private void showCompletionBanner() {
        lblStatus.setText("🎉 Course Complete!");
        lblStatus.setStyle("-fx-text-fill: #22c55e; -fx-font-weight: bold; -fx-font-size: 13px;");
    }

    // ── 12.5 — loadYouTubeVideos ──────────────────────────────────────────────

    private void loadYouTubeVideos() {
        vboxVideos.getChildren().clear();
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(40, 40);
        vboxVideos.getChildren().add(spinner);

        Task<List<VideoResult>> task = new Task<>() {
            @Override
            protected List<VideoResult> call() {
                return youTubeService.searchVideos(
                        course.getCourseName(),
                        course.getCategory(),
                        course.getDescription()
                );
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            vboxVideos.getChildren().clear();
            List<VideoResult> videos = task.getValue();
            if (videos == null || videos.isEmpty()) {
                vboxVideos.getChildren().add(createInfoLabel("⚠ Video recommendations unavailable."));
                return;
            }
            for (VideoResult video : videos) {
                vboxVideos.getChildren().add(buildVideoCard(video));
            }
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            vboxVideos.getChildren().clear();
            vboxVideos.getChildren().add(createInfoLabel("⚠ Video recommendations unavailable."));
        }));

        new Thread(task).start();
    }

    private VBox buildVideoCard(VideoResult video) {
        VBox card = new VBox(8);
        card.setStyle(
            "-fx-background-color: #f8fafc;" +
            "-fx-background-radius: 10;" +
            "-fx-border-color: #e2e8f0;" +
            "-fx-border-radius: 10;" +
            "-fx-border-width: 1;" +
            "-fx-padding: 12;"
        );

        // Thumbnail (loaded async)
        ImageView thumbnail = new ImageView();
        thumbnail.setFitWidth(240);
        thumbnail.setFitHeight(135);
        thumbnail.setPreserveRatio(true);
        thumbnail.setStyle("-fx-background-radius: 6;");

        if (video.getThumbnailUrl() != null && !video.getThumbnailUrl().isBlank()) {
            Task<Image> imgTask = new Task<>() {
                @Override
                protected Image call() {
                    return new Image(video.getThumbnailUrl(), true);
                }
            };
            imgTask.setOnSucceeded(e -> Platform.runLater(() -> thumbnail.setImage(imgTask.getValue())));
            new Thread(imgTask).start();
        }

        // Title (truncated to 60 chars)
        String title = video.getTitle();
        if (title != null && title.length() > 60) {
            title = title.substring(0, 60) + "…";
        }
        Label lblTitle = new Label(title);
        lblTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-wrap-text: true;");
        lblTitle.setWrapText(true);

        // Channel name
        Label lblChannel = new Label(video.getChannelName());
        lblChannel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        // Watch button
        Button btnWatch = new Button("▶ Watch");
        btnWatch.setStyle(
            "-fx-background-color: #ef4444; -fx-text-fill: white;" +
            "-fx-background-radius: 8; -fx-cursor: hand;" +
            "-fx-padding: 5 12; -fx-font-size: 11px; -fx-font-weight: bold;"
        );
        final String videoId = video.getVideoId();
        btnWatch.setOnAction(e -> {
            try {
                Desktop.getDesktop().browse(new URI("https://www.youtube.com/watch?v=" + videoId));
            } catch (Exception ex) {
                System.err.println("[CourseContentController] Cannot open browser: " + ex.getMessage());
            }
        });

        card.getChildren().addAll(thumbnail, lblTitle, lblChannel, btnWatch);
        return card;
    }

    // ── 12.6 — loadWikipediaSummary ───────────────────────────────────────────

    private void loadWikipediaSummary() {
        vboxWiki.getChildren().clear();
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(40, 40);
        vboxWiki.getChildren().add(spinner);

        Task<WikiSummary> task = new Task<>() {
            @Override
            protected WikiSummary call() {
                return wikipediaService.fetchSummary(course.getCourseName());
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            vboxWiki.getChildren().clear();
            WikiSummary summary = task.getValue();
            if (summary == null) {
                vboxWiki.getChildren().add(createInfoLabel("ℹ No Wikipedia summary available for this topic."));
                return;
            }
            vboxWiki.getChildren().add(buildWikiCard(summary));
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            vboxWiki.getChildren().clear();
            vboxWiki.getChildren().add(createInfoLabel("ℹ No Wikipedia summary available for this topic."));
        }));

        new Thread(task).start();
    }

    private VBox buildWikiCard(WikiSummary summary) {
        VBox card = new VBox(10);
        card.setStyle(
            "-fx-background-color: #f8fafc;" +
            "-fx-background-radius: 10;" +
            "-fx-border-color: #e2e8f0;" +
            "-fx-border-radius: 10;" +
            "-fx-border-width: 1;" +
            "-fx-padding: 14;"
        );

        // Bold title
        Label lblTitle = new Label(summary.getTitle() != null ? summary.getTitle() : "");
        lblTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        lblTitle.setWrapText(true);

        // Extract (truncated to 400 chars)
        String extract = summary.getExtract();
        if (extract != null && extract.length() > 400) {
            extract = extract.substring(0, 400) + "...";
        }
        Label lblExtract = new Label(extract != null ? extract : "");
        lblExtract.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151; -fx-line-spacing: 2;");
        lblExtract.setWrapText(true);

        card.getChildren().addAll(lblTitle, lblExtract);

        // Optional thumbnail (loaded async, hidden if null)
        if (summary.getThumbnailUrl() != null && !summary.getThumbnailUrl().isBlank()) {
            ImageView thumbnail = new ImageView();
            thumbnail.setFitWidth(200);
            thumbnail.setFitHeight(150);
            thumbnail.setPreserveRatio(true);
            thumbnail.setVisible(false);
            thumbnail.setManaged(false);

            Task<Image> imgTask = new Task<>() {
                @Override
                protected Image call() {
                    return new Image(summary.getThumbnailUrl(), true);
                }
            };
            imgTask.setOnSucceeded(e -> Platform.runLater(() -> {
                thumbnail.setImage(imgTask.getValue());
                thumbnail.setVisible(true);
                thumbnail.setManaged(true);
            }));
            new Thread(imgTask).start();

            card.getChildren().add(thumbnail);
        }

        // "Read More" hyperlink
        if (summary.getPageUrl() != null && !summary.getPageUrl().isBlank()) {
            Hyperlink readMore = new Hyperlink("📖 Read More");
            readMore.setStyle("-fx-font-size: 12px; -fx-text-fill: #2563eb;");
            final String pageUrl = summary.getPageUrl();
            readMore.setOnAction(e -> {
                try {
                    Desktop.getDesktop().browse(new URI(pageUrl));
                } catch (Exception ex) {
                    System.err.println("[CourseContentController] Cannot open browser: " + ex.getMessage());
                }
            });
            card.getChildren().add(readMore);
        }

        return card;
    }

    // ── 12.7 — loadPdfResources ───────────────────────────────────────────────

    private void loadPdfResources() {
        try {
            allPdfResources = pdfService.findByCourse(courseId);
        } catch (SQLException e) {
            System.err.println("[CourseContentController] Failed to load PDF resources: " + e.getMessage());
            allPdfResources = new ArrayList<>();
        }

        renderPdfCards(allPdfResources);

        // Wire search field listener
        txtResourceSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isBlank()) {
                renderPdfCards(allPdfResources);
            } else {
                String lower = newVal.toLowerCase();
                List<PdfResource> filtered = new ArrayList<>();
                for (PdfResource r : allPdfResources) {
                    boolean titleMatch = r.getTitle() != null && r.getTitle().toLowerCase().contains(lower);
                    boolean topicMatch = r.getTopic() != null && r.getTopic().toLowerCase().contains(lower);
                    if (titleMatch || topicMatch) {
                        filtered.add(r);
                    }
                }
                renderPdfCards(filtered);
            }
        });
    }

    private void renderPdfCards(List<PdfResource> resources) {
        vboxResources.getChildren().clear();

        if (resources == null || resources.isEmpty()) {
            vboxResources.getChildren().add(createInfoLabel("No resources uploaded yet."));
            return;
        }

        for (PdfResource resource : resources) {
            vboxResources.getChildren().add(buildPdfCard(resource));
        }
    }

    private HBox buildPdfCard(PdfResource resource) {
        HBox card = new HBox(10);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle(
            "-fx-background-color: #f8fafc;" +
            "-fx-background-radius: 10;" +
            "-fx-border-color: #e2e8f0;" +
            "-fx-border-radius: 10;" +
            "-fx-border-width: 1;" +
            "-fx-padding: 10 14;"
        );

        // PDF icon
        Label iconLbl = new Label("📄");
        iconLbl.setStyle("-fx-font-size: 18px;");

        // Title and topic
        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label titleLbl = new Label(resource.getTitle() != null ? resource.getTitle() : "(Untitled)");
        titleLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        info.getChildren().add(titleLbl);

        if (resource.getTopic() != null && !resource.getTopic().isBlank()) {
            Label topicLbl = new Label("🏷 " + resource.getTopic());
            topicLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
            info.getChildren().add(topicLbl);
        }

        card.getChildren().addAll(iconLbl, info);

        // Check file existence
        boolean fileExists = new File(resource.getFilePath()).exists();

        if (!fileExists) {
            Label missingLbl = new Label("⚠ File not found");
            missingLbl.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11px;");
            card.getChildren().add(missingLbl);
        }

        // View button
        Button btnView = new Button("👁 View");
        btnView.setStyle(
            "-fx-background-color: #dbeafe; -fx-text-fill: #2563eb;" +
            "-fx-background-radius: 8; -fx-cursor: hand;" +
            "-fx-padding: 5 12; -fx-font-size: 11px;"
        );
        btnView.setDisable(!fileExists);
        btnView.setOnAction(e -> {
            try {
                Desktop.getDesktop().open(new File(resource.getFilePath()));
            } catch (IOException ex) {
                showNotification("⚠ Cannot open file: " + ex.getMessage());
            }
        });

        // Download button
        Button btnDownload = new Button("⬇ Download");
        btnDownload.setStyle(
            "-fx-background-color: #dcfce7; -fx-text-fill: #16a34a;" +
            "-fx-background-radius: 8; -fx-cursor: hand;" +
            "-fx-padding: 5 12; -fx-font-size: 11px;"
        );
        btnDownload.setDisable(!fileExists);
        btnDownload.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save PDF");
            File sourceFile = new File(resource.getFilePath());
            chooser.setInitialFileName(sourceFile.getName());
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
            );
            File dest = chooser.showSaveDialog(getStage());
            if (dest != null) {
                try {
                    Files.copy(sourceFile.toPath(), dest.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    showNotification("✅ Downloaded to: " + dest.getName());
                } catch (IOException ex) {
                    showNotification("⚠ Download failed: " + ex.getMessage());
                }
            }
        });

        card.getChildren().addAll(btnView, btnDownload);
        return card;
    }

    // ── 12.8 — refreshProgressPanel ───────────────────────────────────────────

    private void refreshProgressPanel() {
        try {
            StudentProgress progress = progressService.getProgress(userId, courseId);
            if (progress == null) return;

            int pct = progress.getProgressPercentage();

            progressBar.setProgress(pct / 100.0);
            lblProgressPct.setText("📈 " + pct + "% complete");

            // Last activity
            if (progress.getLastActivityAt() != null) {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                lblLastActivity.setText("Last activity: " + progress.getLastActivityAt().format(fmt));
            } else {
                lblLastActivity.setText("Last activity: No activity yet");
            }

            // Color styling based on percentage
            String color;
            if (pct >= 80) {
                color = "#22c55e";
            } else if (pct >= 40) {
                color = "#f59e0b";
            } else {
                color = "#ef4444";
            }
            progressBar.setStyle("-fx-accent: " + color + ";");

        } catch (SQLException e) {
            System.err.println("[CourseContentController] Failed to refresh progress panel: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showAccessDenied() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Access Denied");
        alert.setHeaderText("Course Access Restricted");
        alert.setContentText("You must have an accepted enrollment to access this course.");
        alert.showAndWait();
        Stage stage = getStage();
        if (stage != null) stage.close();
    }

    private void showNotification(String message) {
        lblStatus.setText(message);
        lblStatus.setStyle("-fx-text-fill: #b0bec5; -fx-font-size: 12px; -fx-font-style: italic;");
    }

    private Label createInfoLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-padding: 8 0;");
        lbl.setWrapText(true);
        return lbl;
    }

    /**
     * Retrieves the current Stage from any FXML node.
     * Returns null if the scene is not yet attached.
     */
    private Stage getStage() {
        try {
            if (lblCourseTitle != null && lblCourseTitle.getScene() != null) {
                return (Stage) lblCourseTitle.getScene().getWindow();
            }
        } catch (Exception e) {
            // Scene not yet attached
        }
        return null;
    }
}
