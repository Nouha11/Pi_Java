package controllers.forum;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import models.forum.Report;
import services.forum.ReportService;
import services.forum.PostService;
import services.forum.CommentService;

import java.util.List;

public class AdminReportsController {

    @FXML private VBox reportsContainer;

    private ReportService reportService = new ReportService();
    private PostService postService = new PostService();
    private CommentService commentService = new CommentService();

    @FXML
    public void initialize() {
        loadReports();
    }

    private void loadReports() {
        reportsContainer.getChildren().clear();
        List<Report> reports = reportService.getAllReports();

        if (reports.isEmpty()) {
            Label emptyLabel = new Label("No active reports to review.");
            emptyLabel.setStyle("-fx-padding: 40; -fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 16px;");
            reportsContainer.getChildren().add(emptyLabel);
            return;
        }

        for (Report report : reports) {
            reportsContainer.getChildren().add(createReportRow(report));
        }
    }

    private HBox createReportRow(Report report) {
        HBox row = new HBox(20);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 20; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0; -fx-background-color: white;");

        VBox reporterBox = new VBox(6);
        reporterBox.setPrefWidth(150);

        Label reportedBy = new Label("Reported by:");
        reportedBy.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px; -fx-font-weight: bold;");
        Label reporterName = new Label("@" + report.getReporterName());
        reporterName.setStyle("-fx-text-fill: #1e293b; -fx-font-weight: bold;");

        Label typeBadge = new Label(report.isCommentReport() ? "💬 REPLY" : "📄 POST");
        if (report.isCommentReport()) {
            typeBadge.setStyle("-fx-background-color: #f3e8ff; -fx-text-fill: #9333ea; -fx-padding: 4 8; -fx-background-radius: 4; -fx-font-size: 10px; -fx-font-weight: 900; -fx-max-width: 70; -fx-alignment: center;");
        } else {
            typeBadge.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0284c7; -fx-padding: 4 8; -fx-background-radius: 4; -fx-font-size: 10px; -fx-font-weight: 900; -fx-max-width: 70; -fx-alignment: center;");
        }

        reporterBox.getChildren().addAll(reportedBy, reporterName, typeBadge);

        VBox contentBox = new VBox(6);
        contentBox.setPrefWidth(400);
        Label reasonLabel = new Label("Reason: " + report.getReason());
        reasonLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #e11d48; -fx-font-size: 14px;");

        Label contextLabel = new Label();
        if (report.isCommentReport()) {
            contextLabel.setText("\"" + (report.getCommentContent() != null ? report.getCommentContent() : "[Content Deleted]") + "\"");
            contextLabel.setStyle("-fx-text-fill: #475569; -fx-font-size: 13px; -fx-font-style: italic; -fx-background-color: #f8fafc; -fx-padding: 6 10; -fx-background-radius: 6; -fx-border-color: #e2e8f0; -fx-border-radius: 6;");
        } else {
            contextLabel.setText("Title: " + (report.getPostTitle() != null ? report.getPostTitle() : "[Post Deleted]"));
            contextLabel.setStyle("-fx-text-fill: #334155; -fx-font-size: 13px; -fx-font-weight: bold;");
        }
        contextLabel.setWrapText(true);
        contentBox.getChildren().addAll(reasonLabel, contextLabel);

        Label statusBadge = new Label(report.getStatus().toUpperCase());
        statusBadge.setStyle("-fx-padding: 4 10; -fx-background-radius: 12; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-color: #fef3c7; -fx-text-fill: #d97706;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Label btnDismiss = new Label("Dismiss");
        btnDismiss.setStyle("-fx-cursor: hand; -fx-text-fill: #64748b; -fx-font-weight: bold; -fx-padding: 8 15; -fx-background-color: #f1f5f9; -fx-background-radius: 6;");
        btnDismiss.setOnMouseClicked(e -> {
            reportService.resolveReport(report.getId());
            loadReports();
        });

        Label btnDelete = new Label(report.isCommentReport() ? "Remove Reply" : "Delete Post");
        btnDelete.setStyle("-fx-cursor: hand; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 15; -fx-background-color: #e11d48; -fx-background-radius: 6;");
        btnDelete.setOnMouseClicked(e -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Moderate Content");
            alert.setHeaderText("Remove this " + (report.isCommentReport() ? "reply" : "post") + "?");

            if (alert.showAndWait().get() == ButtonType.OK) {
                if (report.isCommentReport()) {
                    // 🔥 MODIFIED: Censor instead of delete!
                    commentService.censorByModerator(report.getCommentId());
                } else {
                    postService.supprimer(report.getPostId());
                }
                reportService.resolveReport(report.getId());
                loadReports();
            }
        });

        actions.getChildren().addAll(btnDismiss, btnDelete);
        row.getChildren().addAll(reporterBox, contentBox, statusBadge, spacer, actions);

        return row;
    }
}