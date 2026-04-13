package controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import java.io.IOException;

public class NovaDashboardController {

    // The physical box on the main dashboard
    @FXML private StackPane contentArea;

    // A static memory bank so the Home Page cards can safely change the screen!
    private static StackPane staticContentArea;

    @FXML
    public void initialize() {
        // SAFETY CHECK: Only set up the router if this is the Main Dashboard!
        if (contentArea != null) {
            staticContentArea = contentArea;
            loadPage("/views/home.fxml"); // Load the home page safely
        }
    }

    // --- BUTTON CLICKS (Works for the Navbar AND the Home Cards!) ---
    @FXML void handleShowHome(ActionEvent event) { loadPage("/views/home.fxml"); }
    @FXML void handleShowStudySessions(ActionEvent event) { loadPage("/views/studysession/MainDashboard.fxml"); }
    @FXML void handleShowForum(ActionEvent event) { loadPage("/views/forum/forum_feed.fxml"); }
    @FXML void handleShowGamification(ActionEvent event) { loadPage("/views/gamification/GameDashboard.fxml"); }
    @FXML void handleShowLibrary(ActionEvent event) { loadPage("/views/library/LibrariesView.fxml"); }

    // --- THE STATIC ENGINE ---
    public static void loadPage(String fxmlPath) {
        try {
            System.out.println("Routing to: " + fxmlPath);
            // Notice we use NovaDashboardController.class here now
            FXMLLoader loader = new FXMLLoader(NovaDashboardController.class.getResource(fxmlPath));
            Parent view = loader.load();

            staticContentArea.getChildren().clear();
            staticContentArea.getChildren().add(view);

        } catch (Exception e) {
            System.err.println("❌ Failed to load view: " + fxmlPath);
            e.printStackTrace();
        }
    }
}