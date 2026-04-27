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
 * AdminEnrollmentController provides comprehensive enrollment monitoring for admins.
 * Displays all enrollment requests across the platform with filtering, summary metrics,
 * and action buttons for pending requests.
 * 
 * Requirements: 6.2, 6.3, 6.4, 6.5, 6.6
 */
public class AdminEnrollmentController implements Initializable {

    // Summary metric labels (Subtask 13.5)
    @FXML private Label lblTotalEnrollments;
    @FXML private Label lblPendingCount;
    @FXML private Label lblAcceptedCount;
    @FXML private Label lblRejectedCount;

    // Filter controls (Subtask 13.3)
    @FXML private ComboBox<String> statusFilter;
    @FXML private TextField courseNameSearch;

    // Table view (Subtask 13.1)
    @FXML private TableView<EnrollmentRequest> enrollmentsTable;
    @FXML private TableColumn<EnrollmentRequest, String> studentNameColumn;
    @FXML private TableColumn<EnrollmentRequest, String> courseNameColumn;
    @FXML private TableColumn<EnrollmentRequest, String> creatorNameColumn;
    @FXML private TableColumn<EnrollmentRequest, String> statusColumn;
    @FXML private TableColumn<EnrollmentRequest, String> requestedAtColumn;
    @FXML private TableColumn<EnrollmentRequest, Void> actionsColumn;

    @FXML private Label statusLabel;

    private final EnrollmentService enrollmentService = new EnrollmentService();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

    // Cache for all enrollments to support filtering without re-querying
    private List<EnrollmentRequest> allEnrollments;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTableColumns();
        setupFilters();
        loadAllEnrollments();
    }

    /**
     * Subtask 13.1: Setup TableView columns
     * Requirements: 6.2, 6.3
     */
    private void setupTableColumns() {
        // Student Name column
        studentNameColumn.setCellValueFactory(new PropertyValueFactory<>("studentName"));
        studentNameColumn.setPrefWidth(150);
        
        // Course Name column
        courseNameColumn.setCellValueFactory(new PropertyValueFactory<>("courseName"));
        courseNameColumn.setPrefWidth(200);
        
        // Creator Name column
        creatorNameColumn.setCellValueFactory(new PropertyValueFactory<>("responderName"));
        creatorNameColumn.setPrefWidth(150);
        
        // Status column with styled badges
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusColumn.setPrefWidth(120);
        statusColumn.setCellFactory(column -> new TableCell<EnrollmentRequest, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label();
                    switch (status) {
                        case "PENDING":
                            badge.setText("⏳ Pending");
                            badge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #f59e0b; " +
                                         "-fx-background-radius: 12; -fx-padding: 4 10; -fx-font-size: 11px;");
                            break;
                        case "ACCEPTED":
                            badge.setText("✓ Accepted");
                            badge.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #16a34a; " +
                                         "-fx-background-radius: 12; -fx-padding: 4 10; -fx-font-size: 11px;");
                            break;
                        case "REJECTED":
                            badge.setText("✗ Rejected");
                            badge.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #ef4444; " +
                                         "-fx-background-radius: 12; -fx-padding: 4 10; -fx-font-size: 11px;");
                            break;
                        default:
                            badge.setText(status);
                            badge.setStyle("-fx-background-color: #e5e7eb; -fx-text-fill: #6b7280; " +
                                         "-fx-background-radius: 12; -fx-padding: 4 10; -fx-font-size: 11px;");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });
        
        // Requested At column
        requestedAtColumn.setCellValueFactory(new PropertyValueFactory<>("requestedAt"));
        requestedAtColumn.setPrefWidth(150);
        requestedAtColumn.setCellFactory(column -> new TableCell<EnrollmentRequest, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    EnrollmentRequest request = getTableRow().getItem();
                    if (request.getRequestedAt() != null) {
                        setText(request.getRequestedAt().format(DATE_FORMATTER));
                    } else {
                        setText("");
                    }
                }
            }
        });
        
        // Actions column with Accept and Reject buttons (Subtask 13.4)
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
                
                // Accept button action
                acceptBtn.setOnAction(e -> {
                    EnrollmentRequest request = getTableRow().getItem();
                    if (request != null) {
                        handleAccept(request);
                    }
                });
                
                // Reject button action
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
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    EnrollmentRequest request = getTableRow().getItem();
                    // Only show action buttons for PENDING requests
                    if ("PENDING".equals(request.getStatus())) {
                        setGraphic(actionBox);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });
    }

    /**
     * Subtask 13.3: Setup filter controls
     * Requirements: 6.4
     */
    private void setupFilters() {
        // Setup status filter ComboBox
        if (statusFilter != null) {
            statusFilter.getItems().addAll("All", "PENDING", "ACCEPTED", "REJECTED");
            statusFilter.setValue("All");
            
            // Apply filter on selection change
            statusFilter.setOnAction(e -> applyFilters());
        }
        
        // Setup course name search TextField
        if (courseNameSearch != null) {
            // Apply filter on text change (real-time filtering)
            courseNameSearch.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        }
    }

    /**
     * Subtask 13.2: Load all enrollment records
     * Requirements: 6.2
     */
    private void loadAllEnrollments() {
        try {
            // Call enrollmentService.findAllEnrollments()
            allEnrollments = enrollmentService.findAllEnrollments();
            
            // Apply filters (which will populate the table)
            applyFilters();
            
            // Update summary cards
            updateSummaryCards();
            
            setStatus("Loaded " + allEnrollments.size() + " enrollment record(s)", false);
        } catch (SQLException e) {
            showError("Failed to load enrollments: " + e.getMessage());
        }
    }

    /**
     * Subtask 13.3: Apply filters to the enrollment list
     * Requirements: 6.4
     */
    private void applyFilters() {
        if (allEnrollments == null) {
            return;
        }
        
        String selectedStatus = statusFilter != null ? statusFilter.getValue() : "All";
        String searchText = courseNameSearch != null ? courseNameSearch.getText() : "";
        
        List<EnrollmentRequest> filtered = allEnrollments.stream()
            .filter(er -> {
                // Status filter
                if (!"All".equals(selectedStatus) && !selectedStatus.equals(er.getStatus())) {
                    return false;
                }
                
                // Course name search filter
                if (searchText != null && !searchText.trim().isEmpty()) {
                    String courseName = er.getCourseName();
                    if (courseName == null || !courseName.toLowerCase().contains(searchText.toLowerCase().trim())) {
                        return false;
                    }
                }
                
                return true;
            })
            .toList();
        
        // Populate TableView with filtered results
        enrollmentsTable.getItems().clear();
        enrollmentsTable.getItems().addAll(filtered);
    }

    /**
     * Subtask 13.5: Update aggregate summary cards
     * Requirements: 6.6
     */
    private void updateSummaryCards() {
        if (allEnrollments == null) {
            return;
        }
        
        int total = allEnrollments.size();
        long pending = allEnrollments.stream().filter(er -> "PENDING".equals(er.getStatus())).count();
        long accepted = allEnrollments.stream().filter(er -> "ACCEPTED".equals(er.getStatus())).count();
        long rejected = allEnrollments.stream().filter(er -> "REJECTED".equals(er.getStatus())).count();
        
        if (lblTotalEnrollments != null) {
            lblTotalEnrollments.setText(String.valueOf(total));
        }
        if (lblPendingCount != null) {
            lblPendingCount.setText(String.valueOf(pending));
        }
        if (lblAcceptedCount != null) {
            lblAcceptedCount.setText(String.valueOf(accepted));
        }
        if (lblRejectedCount != null) {
            lblRejectedCount.setText(String.valueOf(rejected));
        }
    }

    /**
     * Subtask 13.4: Handle Accept button action
     * Requirements: 6.5
     */
    private void handleAccept(EnrollmentRequest request) {
        try {
            // Get current user ID (admin)
            int currentUserId = UserSession.getInstance().getUserId();
            
            // Call enrollmentService.acceptRequest
            enrollmentService.acceptRequest(request.getId(), currentUserId);
            
            // Update the request status in the cached list
            request.setStatus("ACCEPTED");
            
            // Refresh the TableView
            applyFilters();
            
            // Update summary cards
            updateSummaryCards();
            
            // Display success feedback
            setStatus("Enrollment request accepted for " + request.getStudentName(), false);
        } catch (SQLException e) {
            showError("Failed to accept request: " + e.getMessage());
        }
    }

    /**
     * Subtask 13.4: Handle Reject button action
     * Requirements: 6.5
     */
    private void handleReject(EnrollmentRequest request) {
        try {
            // Get current user ID (admin)
            int currentUserId = UserSession.getInstance().getUserId();
            
            // Call enrollmentService.rejectRequest
            enrollmentService.rejectRequest(request.getId(), currentUserId);
            
            // Update the request status in the cached list
            request.setStatus("REJECTED");
            
            // Refresh the TableView
            applyFilters();
            
            // Update summary cards
            updateSummaryCards();
            
            // Display success feedback
            setStatus("Enrollment request rejected for " + request.getStudentName(), false);
        } catch (SQLException e) {
            showError("Failed to reject request: " + e.getMessage());
        }
    }

    /**
     * Refresh button handler
     */
    @FXML
    private void handleRefresh() {
        loadAllEnrollments();
        setStatus("Refreshed.", false);
    }

    /**
     * Display success or info status message
     */
    private void setStatus(String msg, boolean isError) {
        if (statusLabel != null) {
            statusLabel.setText(msg);
            statusLabel.setStyle(isError ? "-fx-text-fill: #ef4444;" : "-fx-text-fill: #22c55e;");
            if (!isError) {
                PauseTransition pause = new PauseTransition(Duration.seconds(3));
                pause.setOnFinished(e -> statusLabel.setText(""));
                pause.play();
            }
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
