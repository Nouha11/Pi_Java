import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.net.URL;

public class MainFX extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {

        // Pointing to your new Web-Style Dashboard!
        String path = "/views/NovaDashboard.fxml";
        URL fxmlLocation = getClass().getResource(path);

        // The Safety Tripwire
        if (fxmlLocation == null) {
            System.out.println("❌ CRITICAL ERROR ❌: Cannot find " + path);
            System.exit(1);
        }

        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();

        // 1300x800 to match your wide Symfony web app layout!
        Scene scene = new Scene(root, 1300, 800);
        primaryStage.setTitle("NOVA - Desktop Application");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}