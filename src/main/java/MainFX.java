import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.net.URL;

public class MainFX extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {

        // 🎯 THE ONLY ENTRY POINT: Load the Dashboard
        String path = "/views/NovaDashboard.fxml";
        URL fxmlLocation = getClass().getResource(path);

        if (fxmlLocation == null) {
            System.out.println("❌ CRITICAL ERROR ❌: Cannot find " + path);
            System.exit(1);
        }

        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();

        // 1300x800 to match your wide Symfony web app layout!
        Scene scene = new Scene(root, 1300, 800);

        // Add a nice CSS clear-up if you have one, otherwise just set the scene
        primaryStage.setTitle("NOVA - Unified Learning Platform");
        primaryStage.setScene(scene);

        // ✅ This is the ONLY window that should EVER be shown at startup
        primaryStage.show();

        /* 🛑 REMOVED: Extra stages for Study Session, Gamification, Quiz, and Library.
           Everything is now handled by your NovaDashboardController's routing!
        */
    }

    public static void main(String[] args) {
        launch(args);
    }
}