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
 * Provides access to: My Courses, Enrollment Requests, Analytics, and Study Sessions.
 * 
 * Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7
 */
public class TutorDashboardController implements Initializable {

    @FXML private TabPane mainTabPane;
    @FXML private Label lblWelcome;
    @FXML private Label lblUsername;
    @FXML private Label lblAvatar;
    
    // Tabs (Subtask 12.1)
    @FXML private Tab tabMyCourses;
    @FXML private Tab tabEnrollmentRequests;
    @FXML private Tab tabAnalytics;
    @FXML private Tab tabStudySessions;
    @FXML private Tab tabStudentProgress;

    private String tutorUsername = "Tutor";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Load tab content dynamically
        Platform.runLater(() -> {
            loadTabContent(tabMyCourses, "/views/studysession/TutorCourseView.fxml");
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
     * Set the current user and update header display
     * Requirement: 10.3 - Display username and avatar in header
     * Subtask 14.2: Role-based access guard for Tutor Dashboard
     */
    public void setCurrentUser(User user) {
        if (user == null) return;
        
        // Subtask 14.2: Role-based access guard for Tutor Dashboard
        if (user.getRole() != User.Role.ROLE_TUTOR) {
            System.err.println("[ACCESS DENIED] Role " + user.getRole() + " attempted to access Tutor Dashboard");
            redirectToCorrectDashboard(user);
            return;
        }
        
        tutorUsername = user.getUsername();
        lblUsername.setText(tutorUsername);
        lblWelcome.setText("Welcome back, " + tutorUsername + "!");
        if (lblAvatar != null && !tutorUsername.isEmpty()) {
            lblAvatar.setText(String.valueOf(tutorUsername.charAt(0)).toUpperCase());
        }
    }
    
    /**
     * Subtask 14.2: Redirect users to their correct dashboard based on role
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
