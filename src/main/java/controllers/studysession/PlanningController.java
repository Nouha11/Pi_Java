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
import services.studysession.CourseService;
import services.studysession.PlanningService;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

public class PlanningController implements Initializable {

    @FXML private VBox cardsContainer;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterStatus;
    @FXML private ComboBox<Course> filterCourse;
    @FXML private DatePicker dateFrom, dateTo;
    @FXML private Label statusLabel, statsLabel, pageTitleLabel;

    private final PlanningService planningService = new PlanningService();
    private final CourseService courseService = new CourseService();
    private Course preselectedCourse = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupFilters();
        autoMarkMissed();
        loadData();
    }

    public void initWithCourse(Course course) {
        this.preselectedCourse = course;
        if (pageTitleLabel != null) pageTitleLabel.setText("Plannings — " + course.getCourseName());
        if (filterCourse != null) filterCourse.setValue(course);
        loadData();
    }

    private void setupFilters() {
        filterStatus.getItems().addAll("", "SCHEDULED", "COMPLETED", "MISSED", "CANCELLED");
        filterStatus.setValue("");
        try {
            if (filterCourse != null) {
                List<Course> courses = courseService.findAll();
                filterCourse.getItems().add(null);
                filterCourse.getItems().addAll(courses);
                filterCourse.setCellFactory(lv -> new ListCell<>() {
                    @Override protected void updateItem(Course c, boolean empty) {
                        super.updateItem(c, empty);
                        setText(empty || c == null ? "— All Courses —" : c.getCourseName());
                    }
                });
                filterCourse.setButtonCell(new ListCell<>() {
                    @Override protected void updateItem(Course c, boolean empty) {
                        super.updateItem(c, empty);
                        setText(empty || c == null ? "— All Courses —" : c.getCourseName());
                    }
                });
                filterCourse.valueProperty().addListener((obs, o, n) -> loadData());
            }
        } catch (SQLException e) { System.err.println("Failed to load courses: " + e.getMessage()); }
        if (searchField != null) searchField.textProperty().addListener((obs, o, n) -> loadData());
        filterStatus.valueProperty().addListener((obs, o, n) -> loadData());
        if (dateFrom != null) dateFrom.valueProperty().addListener((obs, o, n) -> loadData());
        if (dateTo   != null) dateTo.valueProperty().addListener((obs, o, n) -> loadData());
    }

    private void autoMarkMissed() {
        try { planningService.autoMarkMissed(); }
        catch (SQLException e) { System.err.println("Auto-mark missed: " + e.getMessage()); }
    }

    private void loadData() {
        try {
            String status = filterStatus.getValue();
            String search = searchField != null ? searchField.getText() : null;
            LocalDate from = dateFrom != null ? dateFrom.getValue() : null;
            LocalDate to   = dateTo   != null ? dateTo.getValue()   : null;
            Course course  = filterCourse != null ? filterCourse.getValue() : preselectedCourse;

            List<Planning> plannings = planningService.findByFilters(
                    (status == null || status.isEmpty()) ? null : status,
                    from, to,
                    course != null ? course.getId() : null,
                    (search == null || search.isEmpty()) ? null : search);

            renderCards(plannings);
            if (statsLabel != null) statsLabel.setText(plannings.size() + " planning session(s) found");
        } catch (SQLException e) { setStatus("⚠ " + e.getMessage(), true); }
    }

    private void renderCards(List<Planning> plannings) {
        cardsContainer.getChildren().clear();
        if (plannings.isEmpty()) {
            cardsContainer.getChildren().add(createEmptyLabel("No planning sessions found."));
            return;
        }
        for (Planning p : plannings) cardsContainer.getChildren().add(buildCard(p));
    }

    private VBox buildCard(Planning p) {
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
        String subtitle = (p.getCourseNameCache() != null ? p.getCourseNameCache() : "")
                + "  ·  " + dateStr + "  " + timeStr
                + "  ·  ⏱ " + p.getPlannedDuration() + " min"
                + (p.isReminder() ? "  ·  🔔" : "");

        HBox infoRow = createInfoRow(accent, "📅", "#eff6ff",
                p.getTitle() != null ? p.getTitle() : "—", subtitle, badge[0], badge[1]);

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

    // ── Exact createCard from AdminHomeController ──────────────────────
    private HBox createInfoRow(String accentColor, String iconText, String iconBg,
                            String title, String subtitle, String badgeText, String badgeStyle) {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 14 16 10 16;");

        StackPane iconCircle = new StackPane();
        iconCircle.setMinSize(38, 38); iconCircle.setMaxSize(38, 38);
        iconCircle.setStyle("-fx-background-color: " + iconBg + "; -fx-background-radius: 50;");
        Label iconLbl = new Label(iconText);
        iconLbl.setStyle("-fx-font-size: 16px;");
        iconCircle.getChildren().add(iconLbl);

        VBox text = new VBox(3);
        HBox.setHgrow(text, Priority.ALWAYS);
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-font-size: 13px;");
        titleLbl.setWrapText(true);
        text.getChildren().add(titleLbl);
        if (subtitle != null && !subtitle.isBlank()) {
            Label subLbl = new Label(subtitle);
            subLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
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

    @FXML private void handleNew() {
        Course course = (filterCourse != null && filterCourse.getValue() != null)
                ? filterCourse.getValue() : preselectedCourse;
        openPlanningForm(null, course != null ? course.getId() : 0,
                course != null ? course.getCourseName() : "");
    }

    @FXML private void handleRefresh() { autoMarkMissed(); loadData(); setStatus("Refreshed.", false); }

    private void openPlanningForm(Planning p, int courseId, String courseName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/studysession/PlanningForm.fxml"));
            Parent root = loader.load();
            PlanningFormController ctrl = loader.getController();
            ctrl.initData(p, courseId, courseName, this::loadData);
            Stage stage = new Stage();
            stage.setTitle(p == null ? "New Planning" : "Edit Planning");
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
