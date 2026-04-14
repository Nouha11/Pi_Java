package controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import java.io.IOException;

public class NovaDashboardController {

    @FXML private StackPane contentArea;

    // 🔥 THE KEY: This static variable allows other controllers to find the dashboard's center
    private static StackPane staticContentArea;

    @FXML
    public void initialize() {
        if (contentArea != null) {
            staticContentArea = contentArea;
            loadPage("/views/home.fxml"); // Load home by default
        }
    }

    // ✅ METHOD A: Use this for simple pages (Home, Forum, etc.)
    public static void loadPage(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(NovaDashboardController.class.getResource(fxmlPath));
            Parent view = loader.load();
            setView(view);
        } catch (IOException e) {
            System.err.println("❌ Could not load: " + fxmlPath);
            e.printStackTrace();
        }
    }

    // ✅ METHOD B: Use this for pages that already have data (Library, Detail views)
    public static void setView(Parent view) {
        if (staticContentArea != null) {
            staticContentArea.getChildren().clear();
            staticContentArea.getChildren().add(view);
        }
    }

    // --- NAVBAR BUTTON ACTIONS ---
    @FXML void handleShowHome(ActionEvent event) { loadPage("/views/home.fxml"); }

    @FXML void handleShowStudySessions(ActionEvent event) { loadPage("/views/studysession/MainDashboard.fxml"); }

    @FXML void handleShowLibrary(ActionEvent event) { loadPage("/views/library/BookListView.fxml"); }

    @FXML void handleShowGamification(ActionEvent event) { loadPage("/views/gamification/GameDashboard.fxml"); }

    @FXML void handleShowQuiz(ActionEvent event) { loadPage("/views/quiz/quiz_list.fxml"); }

    @FXML void handleShowForum(ActionEvent event) { loadPage("/views/forum/forum_feed.fxml"); }
}