package controllers.studysession;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import models.users.User;
import utils.UserSession;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class UserStudyDashboardController implements Initializable {

    @FXML private StackPane contentArea;
    @FXML private Label lblStats;

    @FXML private Button tabCourses;
    @FXML private Button tabPlannings;    // Calendar view
    @FXML private Button tabMyPlannings;  // Planning list (mark complete / start / cancel)
    @FXML private Button tabSessions;

    private List<Button> tabs;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        tabs = List.of(tabCourses, tabPlannings, tabMyPlannings, tabSessions);
        showCourses();
    }

    @FXML private void showCourses()     { loadTab("/views/studysession/UserCourseView.fxml",   tabCourses); }
    @FXML private void showMyPlannings() { loadTab("/views/studysession/UserPlanningView.fxml",  tabMyPlannings); }
    @FXML private void showSessions()    { loadTab("/views/studysession/UserSessionView.fxml",   tabSessions); }

    /** Loads CalendarPlannerView and passes the logged-in student for role-based scoping. */
    @FXML
    private void showPlannings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/CalendarPlannerView.fxml"));
            Parent view = loader.load();

            CalendarPlannerController ctrl = loader.getController();
            if (ctrl != null) {
                UserSession session = UserSession.getInstance();
                User user = new User();
                user.setId(session.getUserId());
                user.setUsername(session.getUsername());
                user.setEmail(session.getEmail());
                try {
                    user.setRole(User.Role.valueOf(session.getRole()));
                } catch (IllegalArgumentException ex) {
                    user.setRole(User.Role.ROLE_STUDENT);
                }
                ctrl.setCurrentUser(user);
            }

            contentArea.getChildren().setAll(view);
            setActiveTab(tabPlannings);
        } catch (IOException e) {
            System.err.println("UserStudyDashboard: cannot load CalendarPlannerView — " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadTab(String fxmlPath, Button active) {
        try {
            Parent view = FXMLLoader.load(getClass().getResource(fxmlPath));
            contentArea.getChildren().setAll(view);
            setActiveTab(active);
        } catch (IOException e) {
            System.err.println("UserStudyDashboard: cannot load " + fxmlPath + " — " + e.getMessage());
        }
    }

    private void setActiveTab(Button active) {
        for (Button btn : tabs) {
            btn.getStyleClass().removeAll("nav-btn-active");
        }
        active.getStyleClass().add("nav-btn-active");
    }
}
