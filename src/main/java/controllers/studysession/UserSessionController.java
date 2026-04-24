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
import models.studysession.Planning;
import models.studysession.StudySession;
import services.studysession.PlanningService;
import services.studysession.StudySessionService;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ResourceBundle;

/** User-facing study session history — with inline action buttons. */
public class UserSessionController implements Initializable {

    @FXML private VBox cardsContainer;
    @FXML private ComboBox<String> filterBurnout, filterMood, filterEnergy;
    @FXML private DatePicker dateFrom, dateTo;
    @FXML private Label statsLabel;

    private final StudySessionService sessionService = new StudySessionService();
    private final PlanningService planningService = new PlanningService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupFilters();
        loadData();
    }

    private void setupFilters() {
        filterBurnout.getItems().addAll("", "LOW", "MODERATE", "HIGH"); filterBurnout.setValue("");
        filterMood.getItems().addAll("", "positive", "neutral", "negative");   filterMood.setValue("");
        filterEnergy.getItems().addAll("", "low", "medium", "high");           filterEnergy.setValue("");
        filterBurnout.valueProperty().addListener((obs, o, n) -> loadData());
        filterMood.valueProperty().addListener((obs, o, n) -> loadData());
        filterEnergy.valueProperty().addListener((obs, o, n) -> loadData());
        if (dateFrom != null) dateFrom.valueProperty().addListener((obs, o, n) -> loadData());
        if (dateTo   != null) dateTo.valueProperty().addListener((obs, o, n) -> loadData());
    }

    private void loadData() {
        try {
            String burnout = filterBurnout.getValue();
            String mood    = filterMood.getValue();
            String energy  = filterEnergy.getValue();
            LocalDateTime from = dateFrom != null && dateFrom.getValue() != null ? dateFrom.getValue().atStartOfDay() : null;
            LocalDateTime to   = dateTo   != null && dateTo.getValue()   != null ? dateTo.getValue().atTime(23, 59, 59) : null;

            List<StudySession> sessions = sessionService.findByFilters(null,
                    (burnout == null || burnout.isEmpty()) ? null : burnout,
                    (mood    == null || mood.isEmpty())    ? null : mood,
                    (energy  == null || energy.isEmpty())  ? null : energy,
                    from, to);

            renderCards(sessions);
            if (statsLabel != null) statsLabel.setText(sessions.size() + " session(s) found");
        } catch (SQLException e) {
            if (statsLabel != null) statsLabel.setText("⚠ Error loading sessions.");
        }
    }

    private void renderCards(List<StudySession> sessions) {
        cardsContainer.getChildren().clear();
        if (sessions.isEmpty()) {
            cardsContainer.getChildren().add(createEmptyLabel("No study sessions yet. Start one from My Plannings."));
            return;
        }
        for (StudySession s : sessions) cardsContainer.getChildren().add(buildCard(s));
    }

    private VBox buildCard(StudySession s) {
        String burnoutVal = s.getBurnoutRisk() != null ? s.getBurnoutRisk().toUpperCase() : "";
        String[] badge = switch (burnoutVal) {
            case "LOW"      -> new String[]{"Low Burnout",      "-fx-background-color: #dcfce7; -fx-text-fill: #16a34a;"};
            case "MODERATE" -> new String[]{"Moderate Burnout", "-fx-background-color: #fef3c7; -fx-text-fill: #d97706;"};
            case "HIGH"     -> new String[]{"High Burnout",     "-fx-background-color: #fee2e2; -fx-text-fill: #ef4444;"};
            default         -> new String[]{"—",                "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b;"};
        };
        String accent = switch (burnoutVal) {
            case "LOW"      -> "#10b981"; case "MODERATE" -> "#f59e0b";
            case "HIGH"     -> "#ef4444"; default -> "#6366f1";
        };

        String startedStr = s.getStartedAt() != null ? s.getStartedAt().toString().replace("T", " ") : "—";
        String subtitle = (s.getCourseNameCache() != null ? s.getCourseNameCache() : "—")
                + "  ·  🕐 " + startedStr
                + "  ·  ⏱ " + s.getDuration() + " min"
                + "  ·  ⭐ " + (s.getXpEarned() != null ? s.getXpEarned() : 0) + " XP"
                + (s.getMood() != null ? "  ·  😊 " + s.getMood() : "")
                + (s.getEnergyLevel() != null ? "  ·  ⚡ " + s.getEnergyLevel() : "");

        String title = s.getPlanningTitleCache() != null ? s.getPlanningTitleCache() : "Study Session";

        HBox infoRow = createInfoRow(accent, "📚", "#eff6ff", title, subtitle, badge[0], badge[1]);

        Region divider = new Region();
        divider.setPrefHeight(1);
        divider.setStyle("-fx-background-color: #f1f5f9;");

        Button startBtn    = cardBtn("▶ Start",       "#dbeafe", "#2563eb", null);
        Button completeBtn = cardBtn("✅ Complete",    "#dcfce7", "#16a34a", null);
        Button cancelBtn   = cardBtn("🚫 Cancel",      "#fef9c3", "#854d0e", null);
        Button editBtn     = cardBtn("✏ Edit",         "#f8fafc", "#334155", "#e2e8f0");
        Button deleteBtn   = cardBtn("🗑 Delete",       "#fee2e2", "#dc2626", null);

        startBtn.setOnAction(e -> {
            if (s.getPlanningId() <= 0) { showInfo("No linked planning session to start."); return; }
            try {
                Planning planning = planningService.findById(s.getPlanningId());
                if (planning == null) { showInfo("Planning session not found."); return; }
                if (!Planning.STATUS_SCHEDULED.equals(planning.getStatus())) {
                    showInfo("Only SCHEDULED sessions can be started."); return;
                }
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/StudySessionForm.fxml"));
                Parent root = loader.load();
                StudySessionFormController ctrl = loader.getController();
                ctrl.initForPlanning(planning, this::loadData);
                Stage stage = new Stage();
                stage.setTitle("Complete Session — " + planning.getTitle());
                stage.initModality(Modality.APPLICATION_MODAL);
                stage.setScene(new Scene(root));
                stage.showAndWait();
            } catch (SQLException | IOException ex) { showInfo("Cannot open session: " + ex.getMessage()); }
        });

        completeBtn.setOnAction(e -> {
            if (s.getPlanningId() <= 0) { showInfo("No linked planning to mark complete."); return; }
            try {
                planningService.updateStatus(s.getPlanningId(), Planning.STATUS_COMPLETED);
                loadData();
            } catch (SQLException ex) { showInfo("Error: " + ex.getMessage()); }
        });

        cancelBtn.setOnAction(e -> {
            if (s.getPlanningId() <= 0) { showInfo("No linked planning to cancel."); return; }
            try {
                planningService.updateStatus(s.getPlanningId(), Planning.STATUS_CANCELLED);
                loadData();
            } catch (SQLException ex) { showInfo("Error: " + ex.getMessage()); }
        });

        editBtn.setOnAction(e -> openSessionForm(s));

        deleteBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete this study session?", ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().filter(r -> r == ButtonType.YES).ifPresent(r -> {
                try { sessionService.delete(s.getId()); loadData(); }
                catch (SQLException ex) { showInfo("Error: " + ex.getMessage()); }
            });
        });

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actionsRow = new HBox(8, spacer, startBtn, completeBtn, cancelBtn, editBtn, deleteBtn);
        actionsRow.setAlignment(Pos.CENTER_RIGHT);
        actionsRow.setStyle("-fx-padding: 8 16 10 16;");

        VBox card = new VBox(0, infoRow, divider, actionsRow);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                "-fx-border-color: " + accent + " transparent transparent transparent; " +
                "-fx-border-width: 0 0 0 4; -fx-border-radius: 10 0 0 10; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 6, 0, 0, 2);");
        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 10; " +
                "-fx-border-color: " + accent + " transparent transparent transparent; " +
                "-fx-border-width: 0 0 0 4; -fx-border-radius: 10 0 0 10; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 8, 0, 0, 3);"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                "-fx-border-color: " + accent + " transparent transparent transparent; " +
                "-fx-border-width: 0 0 0 4; -fx-border-radius: 10 0 0 10; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 6, 0, 0, 2);"));
        return card;
    }

    private HBox createInfoRow(String accentColor, String iconText, String iconBg,
                               String title, String subtitle, String badgeText, String badgeStyle) {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 14 16 10 16;");

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
        row.getChildren().addAll(iconCircle, text);
        if (badgeText != null && !badgeText.isBlank()) {
            Label badge = new Label(badgeText);
            badge.setStyle(badgeStyle + " -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 10; -fx-background-radius: 20;");
            row.getChildren().add(badge);
        }
        return row;
    }

    private Button cardBtn(String text, String bg, String fg, String border) {
        Button btn = new Button(text);
        String style = "-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; " +
                "-fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 5 12; " +
                "-fx-background-radius: 6; -fx-cursor: hand;";
        if (border != null) style += " -fx-border-color: " + border + "; -fx-border-radius: 6;";
        btn.setStyle(style);
        return btn;
    }

    private Label createEmptyLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-padding: 24; -fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 14px;");
        return lbl;
    }

    private void openSessionForm(StudySession s) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/StudySessionForm.fxml"));
            Parent root = loader.load();
            StudySessionFormController ctrl = loader.getController();
            ctrl.initData(s, this::loadData);
            Stage stage = new Stage();
            stage.setTitle("Edit Study Session");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) { showInfo("Cannot open form: " + e.getMessage()); }
    }

    @FXML private void handleRefresh() { loadData(); }

    private void showInfo(String msg) { new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait(); }
}
