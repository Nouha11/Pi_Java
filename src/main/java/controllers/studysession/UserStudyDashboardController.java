package controllers.studysession;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class UserStudyDashboardController implements Initializable {

    @FXML private StackPane contentArea;
    @FXML private Label lblStats;

    @FXML private Button tabCourses;
    @FXML private Button tabPlannings;
    @FXML private Button tabSessions;
    @FXML private Button tabStats;

    private List<Button> tabs;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        tabs = List.of(tabCourses, tabPlannings, tabSessions, tabStats);
        showCourses();
    }

    @FXML private void showCourses()   { loadTab("/views/studysession/UserCourseView.fxml",   tabCourses); }
    @FXML private void showPlannings() { loadTab("/views/studysession/UserPlanningView.fxml",  tabPlannings); }
    @FXML private void showSessions()  { loadTab("/views/studysession/UserSessionView.fxml",   tabSessions); }
    @FXML private void showStats()     { loadTab("/views/studysession/StatsView.fxml",         tabStats); }

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
