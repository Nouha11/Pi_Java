import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class MainFX extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // 1. Tell it exactly where to find your visual file
        URL fxmlLocation = getClass().getResource("/views/forum_feed.fxml");
        FXMLLoader loader = new FXMLLoader(fxmlLocation);

        Parent root = loader.load();

        // 2. Set the window size and title
        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("Forum Application");
        primaryStage.setScene(scene);

        // 3. Show the window!
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}