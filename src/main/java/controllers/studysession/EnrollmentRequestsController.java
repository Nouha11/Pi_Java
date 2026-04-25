package controllers.studysession;

import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import models.studysession.EnrollmentRequest;
import services.studysession.EnrollmentService;
import utils.UserSession;

import java.net.URL;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

/**
 * EnrollmentRequestsController manages enrollment requests for tutors.
 * Tutors can view pending enrollment requests for their courses and accept or reject them.
 * 
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8
 */
public class EnrollmentRequestsController implements Initializable {

    @FXML private TableView<EnrollmentRequest> requestsTable;
    @FXML private TableColumn<EnrollmentRequest, String> studentNameColumn;
    @FXML private TableColumn<EnrollmentRequest, String> courseNameColumn;
    @FXML private TableColumn<EnrollmentRequest, java.time.LocalDateTime> requestedAtColumn;
    @FXML private TableColumn<EnrollmentRequest, Void> actionsColumn;
    @FXML private VBox emptyState;
    @FXML private Label statusLabel;

    private final EnrollmentService enrollmentService = new EnrollmentService();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTableColumns();
        loadPendingRequests();
    }

    /**
     * Subtask 10.1: Setup TableView columns
     * Requirements: 5.1, 5.2
     */
    private void setupTableColumns() {
        // Student Name column
        studentNameColumn.setCellValueFactory(new PropertyValueFactory<>("studentName"));
        studentNameColumn.setPrefWidth(200);
        
        // Course Name column
        courseNameColumn.setCellValueFactory(new PropertyValueFactory<>("courseName"));
        courseNameColumn.setPrefWidth(250);
        
        // Requested At column
        requestedAtColumn.setCellValueFactory(new PropertyValueFactory<>("requestedAt"));
        requestedAtColumn.setPrefWidth(180);
        requestedAtColumn.setCellFactory(column -> new TableCell<EnrollmentRequest, java.time.LocalDateTime>() {
            @Override
            protected void updateItem(java.time.LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.format(DATE_FORMATTER));
                }
            }
        });
        
        // Actions column with Accept and Reject buttons
        actionsColumn.setPrefWidth(200);
        actionsColumn.setCellFactory(column -> new TableCell<EnrollmentRequest, Void>() {
            private final Button acceptBtn = new Button("✓ Accept");
            private final Button rejectBtn = new Button("✗ Reject");
            private final HBox actionBox = new HBox(8);
            
            {
                // Style Accept button
                acceptBtn.setStyle(
                    "-fx-background-color: #dcfce7; -fx-text-fill: #16a34a;" +
                    "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 5 12; -fx-font-size: 11px;"
                );
                
                // Style Reject button
                rejectBtn.setStyle(
                    "-fx-background-color: #fee2e2; -fx-text-fill: #ef4444;" +
                    "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 5 12; -fx-font-size: 11px;"
                );
                
                actionBox.setAlignment(Pos.CENTER_LEFT);
                actionBox.getChildren().addAll(acceptBtn, rejectBtn);
                
                // Subtask 10.3: Accept button action
                acceptBtn.setOnAction(e -> {
                    EnrollmentRequest request = getTableRow().getItem();
                    if (request != null) {
                        handleAccept(request);
                    }
                });
                
                // Subtask 10.4: Reject button action
                rejectBtn.setOnAction(e -> {
                    EnrollmentRequest request = getTableRow().getItem();
                    if (request != null) {
                        handleReject(request);
                    }
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(actionBox);
                }
            }
        });
    }

    /**
     * Subtask 10.2: Load pending enrollment requests for the current tutor
     * Requirements: 5.2, 5.3, 5.8
     */
    private void loadPendingRequests() {
        try {
            // Get current user ID from UserSession
            int currentUserId = UserSession.getInstance().getUserId();
            
            // Call enrollmentService.findPendingByCreator
            List<EnrollmentRequest> pendingRequests = enrollmentService.findPendingByCreator(currentUserId);
            
            // Populate TableView with results
            requestsTable.getItems().clear();
            requestsTable.getItems().addAll(pendingRequests);
            
            // Subtask 10.5: Show empty state if no pending requests
            boolean isEmpty = pendingRequests.isEmpty();
            emptyState.setVisible(isEmpty);
            emptyState.setManaged(isEmpty);
            requestsTable.setVisible(!isEmpty);
            requestsTable.setManaged(!isEmpty);
            
            if (!isEmpty) {
                setStatus(pendingRequests.size() + " pending request(s)", false);
            }
        } catch (SQLException e) {
            showError("Failed to load pending requests: " + e.getMessage());
        }
    }

    /**
     * Subtask 10.3: Handle Accept button action
     * Requirements: 5.4, 5.5
     */
    private void handleAccept(EnrollmentRequest request) {
        try {
            // Get current user ID
            int currentUserId = UserSession.getInstance().getUserId();
            
            // Call enrollmentService.acceptRequest
            enrollmentService.acceptRequest(request.getId(), currentUserId);
            
            // Remove the row from TableView
            requestsTable.getItems().remove(request);
            
            // Display success feedback
            setStatus("Enrollment request accepted for " + request.getStudentName(), false);
            
            // Check if table is now empty and show empty state
            if (requestsTable.getItems().isEmpty()) {
                emptyState.setVisible(true);
                emptyState.setManaged(true);
                requestsTable.setVisible(false);
                requestsTable.setManaged(false);
            }
        } catch (SQLException e) {
            showError("Failed to accept request: " + e.getMessage());
        }
    }

    /**
     * Subtask 10.4: Handle Reject button action
     * Requirements: 5.4, 5.6
     */
    private void handleReject(EnrollmentRequest request) {
        try {
            // Get current user ID
            int currentUserId = UserSession.getInstance().getUserId();
            
            // Call enrollmentService.rejectRequest
            enrollmentService.rejectRequest(request.getId(), currentUserId);
            
            // Remove the row from TableView
            requestsTable.getItems().remove(request);
            
            // Display success feedback
            setStatus("Enrollment request rejected for " + request.getStudentName(), false);
            
            // Check if table is now empty and show empty state
            if (requestsTable.getItems().isEmpty()) {
                emptyState.setVisible(true);
                emptyState.setManaged(true);
                requestsTable.setVisible(false);
                requestsTable.setManaged(false);
            }
        } catch (SQLException e) {
            showError("Failed to reject request: " + e.getMessage());
        }
    }

    /**
     * Refresh button handler
     */
    @FXML
    private void handleRefresh() {
        loadPendingRequests();
        setStatus("Refreshed.", false);
    }

    /**
     * Display success or info status message
     */
    private void setStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill: #ef4444;" : "-fx-text-fill: #22c55e;");
        if (!isError) {
            PauseTransition pause = new PauseTransition(Duration.seconds(3));
            pause.setOnFinished(e -> statusLabel.setText(""));
            pause.play();
        }
    }

    /**
     * Display error message
     */
    private void showError(String msg) {
        setStatus("⚠ " + msg, true);
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setTitle("Error");
        alert.showAndWait();
    }
}
