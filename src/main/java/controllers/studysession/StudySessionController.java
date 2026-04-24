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
import models.studysession.StudySession;
import services.studysession.StudySessionService;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ResourceBundle;

public class StudySessionController implements Initializable {

    @FXML private VBox cardsContainer;
    @FXML private ComboBox<String> filterBurnout, filterMood, filterEnergy;
    @FXML private DatePicker dateFrom, dateTo;
    @FXML private Label statusLabel, statsLabel;

    private final StudySessionService sessionService = new StudySessionService();

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
            LocalDateTime from = (dateFrom != null && dateFrom.getValue() != null) ? dateFrom.getValue().atStartOfDay() : null;
            LocalDateTime to   = (dateTo   != null && dateTo.getValue()   != null) ? dateTo.getValue().atTime(23, 59, 59) : null;

            List<StudySession> sessions = sessionService.findByFilters(null,
                    (burnout == null || burnout.isEmpty()) ? null : burnout,
                    (mood    == null || mood.isEmpty())    ? null : mood,
                    (energy  == null || energy.isEmpty())  ? null : energy,
                    from, to);

            renderCards(sessions);
            if (statsLabel != null) statsLabel.setText(sessions.size() + " session(s) found");
        } catch (SQLException e) { setStatus("⚠ " + e.getMessage(), true); }
    }

    private void renderCards(List<StudySession> sessions) {
        cardsContainer.getChildren().clear();
        if (sessions.isEmpty()) { cardsContainer.getChildren().add(createEmptyLabel("No study sessions found.")); return; }
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
            case "LOW"      -> "#10b981";
            case "MODERATE" -> "#f59e0b";
            case "HIGH"     -> "#ef4444";
            default         -> "#6366f1";
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

        VBox card = new VBox(0, infoRow);
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

    @FXML private void handleNew()     { openSessionForm(null); }
    @FXML private void handleRefresh() { loadData(); setStatus("Refreshed.", false); }

    @FXML private void handleStats() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/StatsView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("📊 Study Session Statistics");
            stage.setScene(new Scene(root, 900, 700));
            stage.show();
        } catch (IOException e) { showError("Cannot open stats: " + e.getMessage()); }
    }

    private void openSessionForm(StudySession s) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/StudySessionForm.fxml"));
            Parent root = loader.load();
            StudySessionFormController ctrl = loader.getController();
            ctrl.initData(s, this::loadData);
            Stage stage = new Stage();
            stage.setTitle(s == null ? "New Study Session" : "Edit Study Session");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) { showError("Cannot open form: " + e.getMessage()); }
    }

    private void setStatus(String msg, boolean isError) {
        if (statusLabel == null) return;
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill:#dc2626;" : "-fx-text-fill:#16a34a;");
    }
    private void showError(String msg) { setStatus("⚠ " + msg, true); new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait(); }
    private void showInfo(String msg)  { new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait(); }
}
