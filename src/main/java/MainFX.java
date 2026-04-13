import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class MainFX extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {

        // --- 1. THE NEW MASTER DASHBOARD ---
        String path = "/views/NovaDashboard.fxml";
        URL fxmlLocation = getClass().getResource(path);

        // Safety Tripwire: Stops the app if the Dashboard file is missing
        if (fxmlLocation == null) {
            System.out.println("❌ CRITICAL ERROR ❌: Cannot find " + path);
            System.exit(1);
        }

        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();

        // Made the window large enough to comfortably fit the sidebar and the content
        Scene scene = new Scene(root, 1200, 760);
        primaryStage.setTitle("NOVA - Desktop Application");
        primaryStage.setScene(scene);
        primaryStage.show();

        // --- 2. YOUR TEAMMATE'S MODULE ---
        // I safely commented this out!
        // We don't want this popping up as a second window anymore,
        // because your new NovaDashboard will load it inside the center screen!
        /*
        FXMLLoader studyLoader = new FXMLLoader(
                getClass().getResource("/views/studysession/MainDashboard.fxml")
        );
        Parent studyRoot = studyLoader.load();
        Stage studyStage = new Stage();
        studyStage.setTitle("📚 Study Session Manager");
        studyStage.setScene(new Scene(studyRoot, 1100, 740));
        studyStage.show();
        */
    }

    public static void main(String[] args) {
        launch(args);
    }
}