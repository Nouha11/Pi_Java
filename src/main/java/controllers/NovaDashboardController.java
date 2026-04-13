package controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

import java.io.IOException;

public class NovaDashboardController {

    // This connects to the empty center box in the FXML
    @FXML private StackPane contentArea;

    @FXML
    public void initialize() {
        // By default, let's load your teammate's Study Sessions when the app opens
        loadView("/views/studysession/MainDashboard.fxml");
    }

    @FXML
    void handleShowStudySessions(ActionEvent event) {
        // Loads your teammate's module!
        loadView("/views/studysession/MainDashboard.fxml");
    }

    @FXML
    void handleShowForum(ActionEvent event) {
        // Loads YOUR module!
        loadView("/views/forum_feed.fxml");
    }

    @FXML
    void handleShowGamification(ActionEvent event) {
        // Loads the Games module!
        loadView("/views/gamification/GameDashboard.fxml");
    }

    // --- THE ROUTING ENGINE ---
    private void loadView(String fxmlPath) {
        try {
            System.out.println("Routing to: " + fxmlPath);
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();

            // Clear out whatever is currently on the screen
            contentArea.getChildren().clear();

            // Inject the new screen into the center!
            contentArea.getChildren().add(view);

        } catch (IOException e) {
            System.err.println("❌ Failed to load view: " + fxmlPath);
            e.printStackTrace();
        }
    }
}