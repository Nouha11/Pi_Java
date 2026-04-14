package controllers.studysession;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.util.ResourceBundle;

public class MainDashboardController implements Initializable {

    @FXML private ToggleButton tabCourses;
    @FXML private ToggleButton tabPlannings;
    @FXML private ToggleButton tabSessions;
    @FXML private ToggleButton tabStats;

    @FXML private StackPane contentArea;

    // injected sub-controllers via fx:id on fx:include — use Parent to support any root type
    @FXML private Parent courseView;
    @FXML private Parent planningView;
    @FXML private Parent sessionView;
    @FXML private Parent statsView;

    private static final String ACTIVE_STYLE =
            "-fx-background-color: rgba(255,255,255,0.18); -fx-text-fill: white; " +
            "-fx-alignment: CENTER_LEFT; -fx-padding: 10 16; " +
            "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 13; -fx-font-weight: bold;";

    private static final String INACTIVE_STYLE =
            "-fx-background-color: transparent; -fx-text-fill: rgba(255,255,255,0.8); " +
            "-fx-alignment: CENTER_LEFT; -fx-padding: 10 16; " +
            "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 13;";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        tabCourses.setOnAction(e -> showView(courseView, tabCourses));
        tabPlannings.setOnAction(e -> showView(planningView, tabPlannings));
        tabSessions.setOnAction(e -> showView(sessionView, tabSessions));
        tabStats.setOnAction(e -> showView(statsView, tabStats));

        // default: show courses
        showView(courseView, tabCourses);
        tabCourses.setSelected(true);
    }

    private void showView(Parent target, ToggleButton active) {
        for (Node child : contentArea.getChildren()) {
            child.setVisible(false);
            child.setManaged(false);
        }
        target.setVisible(true);
        target.setManaged(true);

        for (ToggleButton btn : new ToggleButton[]{tabCourses, tabPlannings, tabSessions, tabStats}) {
            btn.setStyle(btn == active ? ACTIVE_STYLE : INACTIVE_STYLE);
        }
    }
}
