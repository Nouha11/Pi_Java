package controllers.studysession;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.studysession.Course;
import models.studysession.Planning;
import services.studysession.PlanningService;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

public class CourseDetailController implements Initializable {

    @FXML private Label lblCourseName, lblCategory, lblDifficulty, lblStatus;
    @FXML private Label lblDuration, lblProgress, lblPublished, lblPlanCount;
    @FXML private TextArea txtDescription;
    @FXML private VBox cardsContainer;

    private final PlanningService planningService = new PlanningService();
    private Course course;

    @Override
    public void initialize(URL url, ResourceBundle rb) {}

    public void initData(Course c) {
        this.course = c;
        lblCourseName.setText(c.getCourseName());
        lblCategory.setText(c.getCategory());
        lblDifficulty.setText(c.getDifficulty());
        lblStatus.setText(c.getStatus());
        lblDuration.setText("⏱ " + c.getEstimatedDuration() + " min");
        lblProgress.setText("📈 " + c.getProgress() + "%");
        lblPublished.setText(c.isPublished() ? "✅ Published" : "❌ Draft");
        txtDescription.setText(c.getDescription() != null ? c.getDescription() : "No description.");
        loadPlannings();
    }

    private void loadPlannings() {
        try {
            List<Planning> plannings = planningService.findByCourse(course.getId());
            renderCards(plannings);
            lblPlanCount.setText(plannings.size() + " session(s)");
        } catch (SQLException e) {
            lblPlanCount.setText("Error");
        }
    }

    private void renderCards(List<Planning> plannings) {
        cardsContainer.getChildren().clear();
        if (plannings.isEmpty()) {
            cardsContainer.getChildren().add(createEmptyLabel("No planning sessions yet. Click ➕ to add one."));
            return;
        }
        for (Planning p : plannings) cardsContainer.getChildren().add(buildCard(p));
    }

    private HBox buildCard(Planning p) {
        String statusVal = p.getStatus() != null ? p.getStatus().toUpperCase() : "";
        String[] badge = switch (statusVal) {
            case "COMPLETED" -> new String[]{"Completed", "-fx-background-color: #dcfce7; -fx-text-fill: #16a34a;"};
            case "SCHEDULED" -> new String[]{"Scheduled", "-fx-background-color: #dbeafe; -fx-text-fill: #2563eb;"};
            case "MISSED"    -> new String[]{"Missed",    "-fx-background-color: #fee2e2; -fx-text-fill: #ef4444;"};
            case "CANCELLED" -> new String[]{"Cancelled", "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b;"};
            default          -> new String[]{statusVal,   "-fx-background-color: #fef3c7; -fx-text-fill: #d97706;"};
        };
        String accent = switch (statusVal) {
            case "COMPLETED" -> "#10b981"; case "SCHEDULED" -> "#3b82f6";
            case "MISSED"    -> "#ef4444"; case "CANCELLED" -> "#94a3b8"; default -> "#f59e0b";
        };

        String dateStr = p.getScheduledDate() != null ? p.getScheduledDate().toString() : "—";
        String timeStr = p.getScheduledTime() != null ? p.getScheduledTime().toString() : "—";
        String subtitle = dateStr + "  " + timeStr + "  ·  ⏱ " + p.getPlannedDuration() + " min"
                + (p.isReminder() ? "  ·  🔔" : "");

        return createCard(accent, "📅", "#eff6ff",
                p.getTitle() != null ? p.getTitle() : "—", subtitle, badge[0], badge[1]);
    }

    private HBox createCard(String accentColor, String iconText, String iconBg,
                            String title, String subtitle, String badgeText, String badgeStyle) {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        String base = "-fx-background-color: white; -fx-background-radius: 10; " +
                "-fx-border-color: " + accentColor + " transparent transparent transparent; " +
                "-fx-border-width: 0 0 0 4; -fx-border-radius: 10 0 0 10; " +
                "-fx-padding: 14 16; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 6, 0, 0, 2);";
        String hover = "-fx-background-color: #f8fafc; -fx-background-radius: 10; " +
                "-fx-border-color: " + accentColor + " transparent transparent transparent; " +
                "-fx-border-width: 0 0 0 4; -fx-border-radius: 10 0 0 10; " +
                "-fx-padding: 14 16; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 8, 0, 0, 3);";
        card.setStyle(base);
        card.setOnMouseEntered(e -> card.setStyle(hover));
        card.setOnMouseExited(e  -> card.setStyle(base));

        StackPane iconCircle = new StackPane();
        iconCircle.setMinSize(38, 38); iconCircle.setMaxSize(38, 38);
        iconCircle.setStyle("-fx-background-color: " + iconBg + "; -fx-background-radius: 50;");
        Label iconLbl = new Label(iconText); iconLbl.setStyle("-fx-font-size: 16px;");
        iconCircle.getChildren().add(iconLbl);

        VBox text = new VBox(3); HBox.setHgrow(text, Priority.ALWAYS);
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-font-size: 13px;");
        titleLbl.setWrapText(true);
        text.getChildren().add(titleLbl);
        if (subtitle != null && !subtitle.isBlank()) {
            Label subLbl = new Label(subtitle); subLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
            text.getChildren().add(subLbl);
        }
        card.getChildren().addAll(iconCircle, text);
        if (badgeText != null && !badgeText.isBlank()) {
            Label badge = new Label(badgeText);
            badge.setStyle(badgeStyle + " -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 10; -fx-background-radius: 20;");
            card.getChildren().add(badge);
        }
        return card;
    }

    private Label createEmptyLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-padding: 24; -fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 14px;");
        return lbl;
    }

    @FXML
    private void handleAddPlanning() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/PlanningForm.fxml"));
            Parent root = loader.load();
            PlanningFormController ctrl = loader.getController();
            ctrl.initData(null, course.getId(), course.getCourseName(), this::loadPlannings);
            Stage stage = new Stage();
            stage.setTitle("New Planning Session");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Cannot open form: " + e.getMessage()).showAndWait();
        }
    }
}
