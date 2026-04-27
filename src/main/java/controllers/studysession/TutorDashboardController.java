package controllers.studysession;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import models.users.User;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * TutorDashboardController manages the main Tutor Dashboard with tab-based navigation.
 * Provides access to: My Courses (with nested Courses / Analytics / Calendar sub-tabs),
 * Enrollment Requests, Study Sessions, and Student Progress.
 *
 * Requirements: 8.1, 8.2, 9.2, 10.1–10.7, 17.3
 * Provides access to: My Courses, Enrollment Requests, Analytics, and Study Sessions.
 *
 * Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7
 */
public class TutorDashboardController implements Initializable {

    @FXML private TabPane mainTabPane;
    @FXML private Label lblWelcome;
    @FXML private Label lblUsername;
    @FXML private Label lblAvatar;

    // Top-level tabs

    // Tabs (Subtask 12.1)
    @FXML private Tab tabMyCourses;
    @FXML private Tab tabEnrollmentRequests;
    @FXML private Tab tabAnalytics;
    // NOTE: tabAnalytics has been removed from the top-level TabPane (Requirement 8.2).
    //       Analytics is now a sub-tab inside tabMyCourses.
    @FXML private Tab tabStudySessions;
    @FXML private Tab tabStudentProgress;

    /** Logged-in tutor — stored so the Calendar sub-tab can receive it after the scene is ready. */
    private User currentUser;

    /** Stored so setCurrentUser() can propagate the user after the tab is already built. */
    private CalendarPlannerController calendarPlannerController;

    /** Stored so setCurrentUser() can propagate the tutorId after the tab is already built. */
    private TutorAnalyticsDashboardController tutorAnalyticsController;

    private String tutorUsername = "Tutor";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Load tab content dynamically
        Platform.runLater(() -> {
            // Build the nested TabPane for "My Courses" (Requirements 8.1, 8.2, 9.2)
            loadMyCoursesNestedTabs();

            // Load remaining top-level tabs
            loadTabContent(tabEnrollmentRequests, "/views/studysession/EnrollmentRequestsView.fxml");
            loadTabContent(tabAnalytics, "/views/studysession/TutorAnalyticsView.fxml");
            loadTabContent(tabStudySessions, "/views/studysession/UserStudyDashboard.fxml");
            loadTabContent(tabStudentProgress, "/views/studysession/TutorProgressMonitorView.fxml");

            // Fullscreen after scene is ready
            if (mainTabPane.getScene() != null) {
                Stage stage = (Stage) mainTabPane.getScene().getWindow();
                stage.setMaximized(true);
                mainTabPane.getScene().getRoot().getProperties()
                        .put("tutorDashboardController", this);
                playEntranceAnimation();
            }
        });
    }

    /**
     * Builds a nested TabPane inside tabMyCourses with three sub-tabs:
     *   📘 Courses   → TutorCourseView.fxml
     *   📊 Analytics → TutorAnalyticsDashboardView.fxml
     *   📅 Calendar  → CalendarPlannerView.fxml
     *
     * Requirements: 8.1, 8.2, 9.2
     */
    private void loadMyCoursesNestedTabs() {
        TabPane nestedTabPane = new TabPane();
        nestedTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        // Ensure the nested TabPane fills the parent AnchorPane completely
        AnchorPane.setTopAnchor(nestedTabPane, 0.0);
        AnchorPane.setBottomAnchor(nestedTabPane, 0.0);
        AnchorPane.setLeftAnchor(nestedTabPane, 0.0);
        AnchorPane.setRightAnchor(nestedTabPane, 0.0);

        // Sub-tab 1: Courses (existing TutorCourseView)
        Tab subCourses = new Tab("📘 Courses");
        loadTabContent(subCourses, "/views/studysession/TutorCourseView.fxml");

        // Sub-tab 2: Analytics (TutorAnalyticsDashboardView — created in task 12)
        Tab subAnalytics = new Tab("📊 Analytics");
        loadAnalyticsTab(subAnalytics);

        // Sub-tab 3: Calendar (CalendarPlannerView — created in task 13)
        Tab subCalendar = new Tab("📅 Calendar");
        loadCalendarTab(subCalendar);

        nestedTabPane.getTabs().addAll(subCourses, subAnalytics, subCalendar);

        // Replace the AnchorPane content of tabMyCourses directly
        AnchorPane wrapper = new AnchorPane(nestedTabPane);
        AnchorPane.setTopAnchor(nestedTabPane, 0.0);
        AnchorPane.setBottomAnchor(nestedTabPane, 0.0);
        AnchorPane.setLeftAnchor(nestedTabPane, 0.0);
        AnchorPane.setRightAnchor(nestedTabPane, 0.0);

        tabMyCourses.setContent(wrapper);
    }

    /**
     * Loads TutorAnalyticsDashboardView.fxml into the given tab and passes the tutor ID
     * to TutorAnalyticsDashboardController via setTutorId() (Requirements 8.3, 8.7).
     */
    private void loadAnalyticsTab(Tab tab) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/TutorAnalyticsDashboardView.fxml"));
            Parent content = loader.load();

            // Store reference so setCurrentUser() can propagate tutorId later
            Object controller = loader.getController();
            if (controller instanceof TutorAnalyticsDashboardController analyticsCtrl) {
                tutorAnalyticsController = analyticsCtrl;
                // Pass tutorId for scoped data loading if user is already set (Requirement 8.7)
                if (currentUser != null) {
                    analyticsCtrl.setTutorId(currentUser.getId());
                }
            }

            tab.setContent(content);
        } catch (IOException e) {
            System.err.println("TutorDashboard: cannot load TutorAnalyticsDashboardView.fxml — " + e.getMessage());
        }
    }

    /**
     * Loads CalendarPlannerView.fxml into the given tab and passes the current user
     * to CalendarPlannerController via setCurrentUser() (Requirement 17.2).
     */
    private void loadCalendarTab(Tab tab) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/CalendarPlannerView.fxml"));
            Parent content = loader.load();

            // Store reference so setCurrentUser() can propagate the user later
            Object controller = loader.getController();
            if (controller instanceof CalendarPlannerController calendarCtrl) {
                calendarPlannerController = calendarCtrl;
                // Pass current user for role-based data scoping if already set (Requirement 13.1–13.3)
                if (currentUser != null) {
                    calendarCtrl.setCurrentUser(currentUser);
                }
            }

            tab.setContent(content);
        } catch (IOException e) {
            System.err.println("TutorDashboard: cannot load CalendarPlannerView.fxml — " + e.getMessage());
        }
    }

    /**
     * Loads an FXML into a tab's content directly.
     * Load content into a tab
     */
    private void loadTabContent(Tab tab, String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent content = loader.load();

            // Wrap in AnchorPane for proper sizing
            AnchorPane wrapper = new AnchorPane(content);
            AnchorPane.setTopAnchor(content, 0.0);
            AnchorPane.setBottomAnchor(content, 0.0);
            AnchorPane.setLeftAnchor(content, 0.0);
            AnchorPane.setRightAnchor(content, 0.0);

            tab.setContent(wrapper);
        } catch (IOException e) {
            System.err.println("TutorDashboard: cannot load " + fxmlPath + " — " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Entrance animation for smooth dashboard appearance
     */
    private void playEntranceAnimation() {
        mainTabPane.setOpacity(0);
        mainTabPane.setTranslateY(16);
        
        PauseTransition delay = new PauseTransition(Duration.millis(200));
        delay.setOnFinished(e -> {
            FadeTransition fade = new FadeTransition(Duration.millis(400), mainTabPane);
            fade.setToValue(1);
            TranslateTransition slide = new TranslateTransition(Duration.millis(400), mainTabPane);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(fade, slide).play();
        });
        delay.play();
    }

    /**
     * Set the current user and update header display.
     * Also propagates the user to the Calendar sub-tab controller for role-based scoping,
     * and the tutorId to the Analytics sub-tab controller.
     *
     * Requirements: 8.1, 9.2, 13.1–13.3, 17.2
     */
    public void setCurrentUser(User user) {
        if (user == null) return;

        // Subtask 14.2: Role-based access guard for Tutor Dashboard
        if (user.getRole() != User.Role.ROLE_TUTOR) {
            System.err.println("[ACCESS DENIED] Role " + user.getRole() + " attempted to access Tutor Dashboard");
            redirectToCorrectDashboard(user);
            return;
        }


        this.currentUser = user;
        tutorUsername = user.getUsername();
        lblUsername.setText(tutorUsername);
        lblWelcome.setText("Welcome back, " + tutorUsername + "!");
        if (lblAvatar != null && !tutorUsername.isEmpty()) {
            lblAvatar.setText(String.valueOf(tutorUsername.charAt(0)).toUpperCase());
        }

        // Propagate to sub-tab controllers that were built before setCurrentUser() was called
        // (initialize() runs before the parent calls setCurrentUser(), so controllers may be null
        //  if the tabs haven't been built yet — they will receive the user via the stored refs)
        if (calendarPlannerController != null) {
            calendarPlannerController.setCurrentUser(user);
        }
        if (tutorAnalyticsController != null) {
            tutorAnalyticsController.setTutorId(user.getId());
        }
    }
    
    /**
     * Subtask 14.2: Redirect users to their correct dashboard based on role.
     */
    private void redirectToCorrectDashboard(User user) {
        try {
            Stage stage = (Stage) mainTabPane.getScene().getWindow();
            FXMLLoader loader;
            Parent root;
            javafx.scene.Scene scene;
            
            if (user.getRole() == User.Role.ROLE_ADMIN) {
                loader = new FXMLLoader(getClass().getResource("/views/admin/AdminDashboard.fxml"));
                root = loader.load();
                controllers.admin.AdminDashboardController adminCtrl = loader.getController();
                adminCtrl.setCurrentUser(user);
                scene = new javafx.scene.Scene(root, 1280, 800);
                scene.getStylesheets().add(getClass().getResource("/css/users.css").toExternalForm());
                stage.setTitle("NOVA - Admin Dashboard");
            } else {
                // Default to Student Dashboard
                loader = new FXMLLoader(getClass().getResource("/views/NovaDashboard.fxml"));
                root = loader.load();
                controllers.NovaDashboardController dashCtrl = loader.getController();
                dashCtrl.setCurrentUser(user);
                scene = new javafx.scene.Scene(root, 1300, 800);
                stage.setTitle("NOVA - Student Hub");
            }
            
            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (IOException e) {
            System.err.println("Redirect error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
