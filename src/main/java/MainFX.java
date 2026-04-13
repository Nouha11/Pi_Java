import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class MainFX extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        URL fxmlLocation = getClass().getResource("/views/forum_feed.fxml");
        FXMLLoader loader = new FXMLLoader(fxmlLocation);

        Parent root = loader.load();

        // 2. Set the window size and title
        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("Forum Application");
        primaryStage.setScene(scene);

        // 3. Show the window!
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