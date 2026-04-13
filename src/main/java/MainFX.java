import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class MainFX extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {

        // --- Forum (original) ---
        URL fxmlLocation = getClass().getResource("/views/forum_feed.fxml");
        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();
        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("Forum Application");
        primaryStage.setScene(scene);
        primaryStage.show();

        // --- Study Session module ---
        FXMLLoader studyLoader = new FXMLLoader(
                getClass().getResource("/views/studysession/MainDashboard.fxml")
        );
        Parent studyRoot = studyLoader.load();
        Stage studyStage = new Stage();
        studyStage.setTitle("📚 Study Session Manager");
        studyStage.setScene(new Scene(studyRoot, 1100, 740));
        studyStage.show();

        // --- Gamification module ---
        FXMLLoader gamificationLoader = new FXMLLoader(
                getClass().getResource("/views/gamification/GameDashboard.fxml")
        );
        Parent gamificationRoot = gamificationLoader.load();
        Stage gamificationStage = new Stage();
        gamificationStage.setTitle("🎮 NOVA — Gamification");
        gamificationStage.setScene(new Scene(gamificationRoot, 1100, 680));
        gamificationStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}