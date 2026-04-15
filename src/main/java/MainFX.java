import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.net.URL;

public class MainFX extends Application {

    // Make the primary stage globally accessible so we can swap scenes after login!
    public static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;

        // ðŸ›‘ THE FRONT DOOR: Load the Login Screen First
        String path = "/views/users/login.fxml";
        URL fxmlLocation = getClass().getResource(path);

        if (fxmlLocation == null) {
            System.out.println("âŒ CRITICAL ERROR âŒ: Cannot find " + path);
            System.exit(1);
        }

        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();

        // Login screens are usually smaller and focused
        Scene scene = new Scene(root, 900, 600);

        primaryStage.setTitle("NOVA - Login");
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}