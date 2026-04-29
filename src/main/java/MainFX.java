import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import java.net.URL;

public class MainFX extends Application {

    public static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;

        // FontAwesome 5 is loaded automatically by Ikonli from the classpath
        // Also load our bundled copy to ensure it's available as "Font Awesome 5 Free"
        javafx.scene.text.Font.loadFont(getClass().getResourceAsStream("/fonts/fa-solid-900.ttf"), 14);

        String path = "/views/users/login.fxml";
        URL fxmlLocation = getClass().getResource(path);

        if (fxmlLocation == null) {
            System.out.println("CRITICAL ERROR: Cannot find " + path);
            System.exit(1);
        }

        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();

        Scene scene = new Scene(root, 1100, 720);
        primaryStage.setTitle("NOVA - Login");
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
