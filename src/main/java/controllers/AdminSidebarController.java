package controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;

public class AdminSidebarController {
    @FXML
    private Button dashboardBtn;
    @FXML
    private Button quizBtn;
    @FXML
    private Button usersBtn;
    @FXML
    private Button reportsBtn;
    @FXML
    private Button settingsBtn;
    @FXML
    private Button logoutBtn;

    @FXML
    private void handleDashboard() {
        System.out.println("Navigating to Dashboard");
        // TODO: Implement navigation to dashboard
    }

    @FXML
    private void handleQuizManagement() {
        System.out.println("Navigating to Quiz Management");
        // TODO: Implement navigation to quiz management
    }

    @FXML
    private void handleUsersManagement() {
        System.out.println("Navigating to Users Management");
        // TODO: Implement navigation to users management
    }

    @FXML
    private void handleReports() {
        System.out.println("Navigating to Reports");
        // TODO: Implement navigation to reports
    }

    @FXML
    private void handleSettings() {
        System.out.println("Navigating to Settings");
        // TODO: Implement navigation to settings
    }

    @FXML
    private void handleLogout() {
        System.out.println("Logging out");
        // TODO: Implement logout functionality
    }
}
