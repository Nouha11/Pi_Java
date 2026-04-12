package controllers.studysession;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;
import models.studysession.Planning;
import services.studysession.CourseService;
import services.studysession.PlanningService;

import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ResourceBundle;

public class PlanningFormController implements Initializable {

    @FXML private Label formTitle;
    @FXML private Label lblCourseName;
    @FXML private TextField txtTitle;
    @FXML private DatePicker dpDate;
    @FXML private TextField txtTime;       // HH:mm format
    @FXML private TextField txtDuration;
    @FXML private ComboBox<String> cbStatus;
    @FXML private CheckBox chkReminder;
    @FXML private Label lblError;
    @FXML private Button btnSave;

    // Per-field error labels
    @FXML private Label errTitle;
    @FXML private Label errDate;
    @FXML private Label errTime;
    @FXML private Label errDuration;
    @FXML private Label errStatus;

    private final PlanningService planningService = new PlanningService();
    private Planning planning;
    private boolean isEdit;
    private int courseId;
    private Runnable onSaveCallback;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        cbStatus.getItems().addAll("SCHEDULED", "COMPLETED", "MISSED", "CANCELLED");

        txtTitle.focusedProperty().addListener((obs, o, n) -> { if (!n) validateTitle(); });
        dpDate.focusedProperty().addListener((obs, o, n) -> { if (!n) validateDate(); });
        txtTime.focusedProperty().addListener((obs, o, n) -> { if (!n) validateTime(); });
        txtDuration.focusedProperty().addListener((obs, o, n) -> { if (!n) validateDuration(); });

        // Default time placeholder
        txtTime.setPromptText("HH:mm  (e.g. 14:30)");
    }

    public void initData(Planning p, int courseId, String courseName, Runnable onSave) {
        this.courseId = courseId;
        this.onSaveCallback = onSave;
        lblCourseName.setText("Course: " + (courseName.isEmpty() ? "—" : courseName));

        if (p == null) {
            isEdit = false;
            planning = new Planning();
            formTitle.setText("➕ New Planning Session");
            cbStatus.setValue("SCHEDULED");
            dpDate.setValue(LocalDate.now().plusDays(1));
        } else {
            isEdit = true;
            planning = p;
            formTitle.setText("✏ Edit Planning");
            txtTitle.setText(p.getTitle());
            dpDate.setValue(p.getScheduledDate());
            if (p.getScheduledTime() != null)
                txtTime.setText(p.getScheduledTime().toString());
            txtDuration.setText(String.valueOf(p.getPlannedDuration()));
            cbStatus.setValue(p.getStatus());
            chkReminder.setSelected(p.isReminder());
        }
    }

    @FXML
    private void handleSave() {
        clearErrors();
        if (!validateAll()) return;

        populatePlanning();
        String validationError = planningService.validate(planning, isEdit);
        if (validationError != null) {
            lblError.setText("⚠ " + validationError);
            lblError.setVisible(true);
            return;
        }

        try {
            if (isEdit) planningService.update(planning);
            else planningService.create(planning);

            if (onSaveCallback != null) onSaveCallback.run();
            closeWindow();
        } catch (SQLException e) {
            lblError.setText("⚠ Database error: " + e.getMessage());
            lblError.setVisible(true);
        }
    }

    @FXML
    private void handleCancel() { closeWindow(); }

    // ─────────────────────────────────────────────
    //  VALIDATION
    // ─────────────────────────────────────────────

    private boolean validateAll() {
        boolean ok = true;
        if (!validateTitle()) ok = false;
        if (!validateDate()) ok = false;
        if (!validateTime()) ok = false;
        if (!validateDuration()) ok = false;
        if (cbStatus.getValue() == null) {
            errStatus.setText("Status is required."); errStatus.setVisible(true); ok = false;
        }
        if (courseId <= 0) {
            lblError.setText("⚠ No course selected. Open this form from a course."); lblError.setVisible(true); ok = false;
        }
        return ok;
    }

    private boolean validateTitle() {
        String v = txtTitle.getText();
        if (v == null || v.trim().isEmpty()) {
            errTitle.setText("Title is required."); errTitle.setVisible(true); return false;
        }
        if (v.trim().length() < 3) {
            errTitle.setText("Minimum 3 characters."); errTitle.setVisible(true); return false;
        }
        errTitle.setVisible(false); return true;
    }

    private boolean validateDate() {
        LocalDate d = dpDate.getValue();
        if (d == null) {
            errDate.setText("Date is required."); errDate.setVisible(true); return false;
        }
        if (!isEdit && d.isBefore(LocalDate.now())) {
            errDate.setText("Date cannot be in the past."); errDate.setVisible(true); return false;
        }
        errDate.setVisible(false); return true;
    }

    private boolean validateTime() {
        String v = txtTime.getText();
        if (v == null || v.trim().isEmpty()) {
            errTime.setText("Time is required (HH:mm)."); errTime.setVisible(true); return false;
        }
        try {
            LocalTime.parse(v.trim());
            errTime.setVisible(false); return true;
        } catch (Exception e) {
            errTime.setText("Invalid time format. Use HH:mm (e.g. 09:30).");
            errTime.setVisible(true); return false;
        }
    }

    private boolean validateDuration() {
        try {
            int val = Integer.parseInt(txtDuration.getText().trim());
            if (val <= 0) throw new NumberFormatException();
            errDuration.setVisible(false); return true;
        } catch (NumberFormatException e) {
            errDuration.setText("Must be a positive integer (minutes).");
            errDuration.setVisible(true); return false;
        }
    }

    private void populatePlanning() {
        planning.setCourseId(courseId);
        planning.setTitle(txtTitle.getText().trim());
        planning.setScheduledDate(dpDate.getValue());
        planning.setScheduledTime(LocalTime.parse(txtTime.getText().trim()));
        planning.setPlannedDuration(Integer.parseInt(txtDuration.getText().trim()));
        planning.setStatus(cbStatus.getValue());
        planning.setReminder(chkReminder.isSelected());
    }

    private void clearErrors() {
        lblError.setVisible(false);
        errTitle.setVisible(false); errDate.setVisible(false);
        errTime.setVisible(false); errDuration.setVisible(false); errStatus.setVisible(false);
    }

    private void closeWindow() { ((Stage) btnSave.getScene().getWindow()).close(); }
}