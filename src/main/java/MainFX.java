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

        // --- Quiz module ---
        FXMLLoader quizLoader = new FXMLLoader(
                getClass().getResource("/views/quiz/quiz_list.fxml")
        );
        Parent quizRoot = quizLoader.load();
        Stage quizStage = new Stage();
        quizStage.setTitle("📝 Quiz Manager");
        quizStage.setScene(new Scene(quizRoot, 900, 600));
        quizStage.show();

        // --- Library module (User flow: Browse → Detail → Borrow/Buy) ---
        FXMLLoader libraryLoader = new FXMLLoader(getClass().getResource("/views/library/BookListView.fxml"));
        Parent libraryRoot = libraryLoader.load();
        Stage libraryStage = new Stage();
        libraryStage.setTitle("📖 Library — Books");
        libraryStage.setScene(new Scene(libraryRoot, 1050, 720));
        libraryStage.show();

        // --- Library Admin (Books CRUD + Loan Management + Payments) ---
        javafx.scene.control.TabPane adminTabs = new javafx.scene.control.TabPane();
        adminTabs.setTabClosingPolicy(javafx.scene.control.TabPane.TabClosingPolicy.UNAVAILABLE);

        FXMLLoader adminBookLoader = new FXMLLoader(getClass().getResource("/views/library/BookView.fxml"));
        adminTabs.getTabs().add(new javafx.scene.control.Tab("📚 Books", adminBookLoader.load()));

        FXMLLoader adminLoanLoader = new FXMLLoader(getClass().getResource("/views/library/LoanView.fxml"));
        adminTabs.getTabs().add(new javafx.scene.control.Tab("📋 Loans", adminLoanLoader.load()));

        FXMLLoader adminPaymentLoader = new FXMLLoader(getClass().getResource("/views/library/PaymentView.fxml"));
        adminTabs.getTabs().add(new javafx.scene.control.Tab("💳 Payments", adminPaymentLoader.load()));

        Stage adminStage = new Stage();
        adminStage.setTitle("⚙️ Library Admin");
        adminStage.setScene(new Scene(adminTabs, 1050, 720));
        adminStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}