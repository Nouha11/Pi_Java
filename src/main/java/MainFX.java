import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.net.URL;

public class MainFX extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {

        String path = "views/forum/student/add_post.fxml";
        URL fxmlLocation = getClass().getResource(path);

        // --- OUR TRIPWIRE ---
        if (fxmlLocation == null) {
            System.out.println("❌ CRITICAL ERROR ❌");
            System.out.println("Java cannot find the file at path: " + path);
            System.out.println("Check the 'target/classes' folder to see if it actually compiled!");
            System.exit(1);
        }
        // --------------------

        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("Forum Application - Add Post Test");
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
    }

    public static void main(String[] args) {
        // TEMPORARY TEST
        services.forum.PostService service = new services.forum.PostService();
        java.util.List<models.forum.Post> myPosts = service.afficher();

        System.out.println("--- FORUM FEED TEST ---");
        for (models.forum.Post p : myPosts) {
            System.out.println("Title: " + p.getTitle());
            System.out.println("Author: " + p.getAuthorName() + " | Space: " + p.getSpaceName());
            System.out.println("Tags: " + p.getTags());
            System.out.println("-----------------------");
        }

        launch(args);
    }
}