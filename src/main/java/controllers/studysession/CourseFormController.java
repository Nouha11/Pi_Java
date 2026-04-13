package controllers.studysession;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;
import models.studysession.Course;
import services.studysession.CourseService;

import java.net.URL;
import java.sql.SQLException;
import java.util.ResourceBundle;

public class CourseFormController implements Initializable {

    @FXML private Label formTitle;
    @FXML private TextField txtName;
    @FXML private TextArea txtDescription;
    @FXML private ComboBox<String> cbDifficulty;
    @FXML private TextField txtDuration;
    @FXML private TextField txtProgress;
    @FXML private ComboBox<String> cbStatus;
    @FXML private TextField txtCategory;
    @FXML private TextField txtMaxStudents;
    @FXML private CheckBox chkPublished;
    @FXML private Label lblError;
    @FXML private Button btnSave;

    // Per-field error labels
    @FXML private Label errName;
    @FXML private Label errCategory;
    @FXML private Label errDifficulty;
    @FXML private Label errDuration;
    @FXML private Label errProgress;
    @FXML private Label errStatus;
    @FXML private Label errMaxStudents;

    private final CourseService courseService = new CourseService();
    private Course course;
    private boolean isEdit;
    private Runnable onSaveCallback;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        cbDifficulty.getItems().addAll("BEGINNER", "INTERMEDIATE", "ADVANCED");
        cbStatus.getItems().addAll("NOT_STARTED", "IN_PROGRESS", "COMPLETED");

        // Live validation on focus loss
        txtName.focusedProperty().addListener((obs, o, n) -> { if (!n) validateName(); });
        txtCategory.focusedProperty().addListener((obs, o, n) -> { if (!n) validateCategory(); });
        txtDuration.focusedProperty().addListener((obs, o, n) -> { if (!n) validateDuration(); });
        txtProgress.focusedProperty().addListener((obs, o, n) -> { if (!n) validateProgress(); });
        txtMaxStudents.focusedProperty().addListener((obs, o, n) -> { if (!n) validateMaxStudents(); });
    }

    public void initData(Course c, Runnable onSave) {
        this.onSaveCallback = onSave;
        if (c == null) {
            isEdit = false;
            course = new Course();
            formTitle.setText("➕ New Course");
            cbStatus.setValue("NOT_STARTED");
        } else {
            isEdit = true;
            course = c;
            formTitle.setText("✏ Edit Course");
            txtName.setText(c.getCourseName());
            txtDescription.setText(c.getDescription());
            cbDifficulty.setValue(c.getDifficulty());
            txtDuration.setText(String.valueOf(c.getEstimatedDuration()));
            txtProgress.setText(String.valueOf(c.getProgress()));
            cbStatus.setValue(c.getStatus());
            txtCategory.setText(c.getCategory());
            if (c.getMaxStudents() != null) txtMaxStudents.setText(String.valueOf(c.getMaxStudents()));
            chkPublished.setSelected(c.isPublished());
        }
    }

    @FXML
    private void handleSave() {
        clearErrors();
        if (!validateAll()) return;

        populateCourse();
        String validationError = courseService.validate(course, isEdit);
        if (validationError != null) {
            lblError.setText("⚠ " + validationError);
            lblError.setVisible(true);
            lblError.setManaged(true);
            return;
        }

        try {
            if (isEdit) courseService.update(course);
            else courseService.create(course);
            if (onSaveCallback != null) onSaveCallback.run();
            closeWindow();
        } catch (SQLException e) {
            lblError.setText("⚠ Database error: " + e.getMessage());
            lblError.setVisible(true);
            lblError.setManaged(true);
            e.printStackTrace();
        }
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    // ─────────────────────────────────────────────
    //  VALIDATION
    // ─────────────────────────────────────────────

    private boolean validateAll() {
        boolean ok = true;
        if (!validateName()) ok = false;
        if (!validateCategory()) ok = false;
        if (!validateDuration()) ok = false;
        if (!validateProgress()) ok = false;
        if (!validateMaxStudents()) ok = false;
        if (cbDifficulty.getValue() == null) {
            errDifficulty.setText("Difficulty is required.");
            errDifficulty.setVisible(true);
            errDifficulty.setManaged(true);
            ok = false;
        } else {
            errDifficulty.setVisible(false);
            errDifficulty.setManaged(false);
        }
        if (cbStatus.getValue() == null) {
            errStatus.setText("Status is required.");
            errStatus.setVisible(true);
            errStatus.setManaged(true);
            ok = false;
        } else {
            errStatus.setVisible(false);
            errStatus.setManaged(false);
        }
        return ok;
    }

    private boolean validateName() {
        String v = txtName.getText();
        if (v == null || v.trim().isEmpty()) {
            errName.setText("Course name is required.");
            errName.setVisible(true);
            errName.setManaged(true);
            return false;
        }
        if (v.trim().length() < 3) {
            errName.setText("Minimum 3 characters.");
            errName.setVisible(true);
            errName.setManaged(true);
            return false;
        }
        if (v.trim().length() > 255) {
            errName.setText("Maximum 255 characters.");
            errName.setVisible(true);
            errName.setManaged(true);
            return false;
        }
        errName.setVisible(false);
        errName.setManaged(false);
        return true;
    }

    private boolean validateCategory() {
        String v = txtCategory.getText();
        if (v == null || v.trim().isEmpty()) {
            errCategory.setText("Category is required.");
            errCategory.setVisible(true);
            errCategory.setManaged(true);
            return false;
        }
        if (v.trim().length() < 3) {
            errCategory.setText("Minimum 3 characters.");
            errCategory.setVisible(true);
            errCategory.setManaged(true);
            return false;
        }
        errCategory.setVisible(false);
        errCategory.setManaged(false);
        return true;
    }

    private boolean validateDuration() {
        try {
            int val = Integer.parseInt(txtDuration.getText().trim());
            if (val <= 0) throw new NumberFormatException();
            errDuration.setVisible(false);
            errDuration.setManaged(false);
            return true;
        } catch (NumberFormatException e) {
            errDuration.setText("Must be a positive integer (minutes).");
            errDuration.setVisible(true);
            errDuration.setManaged(true);
            return false;
        }
    }

    private boolean validateProgress() {
        if (txtProgress.getText() == null || txtProgress.getText().trim().isEmpty()) {
            errProgress.setVisible(false);
            errProgress.setManaged(false);
            return true;
        }
        try {
            int val = Integer.parseInt(txtProgress.getText().trim());
            if (val < 0 || val > 100) throw new NumberFormatException();
            errProgress.setVisible(false);
            errProgress.setManaged(false);
            return true;
        } catch (NumberFormatException e) {
            errProgress.setText("Must be 0–100.");
            errProgress.setVisible(true);
            errProgress.setManaged(true);
            return false;
        }
    }


    private boolean validateMaxStudents() {
        String v = txtMaxStudents.getText();
        if (v == null || v.trim().isEmpty()) {
            errMaxStudents.setVisible(false);
            errMaxStudents.setManaged(false);
            return true;
        }
        try {
            int val = Integer.parseInt(v.trim());
            if (val <= 0) throw new NumberFormatException();
            errMaxStudents.setVisible(false);
            errMaxStudents.setManaged(false);
            return true;
        } catch (NumberFormatException e) {
            errMaxStudents.setText("Must be a positive integer.");
            errMaxStudents.setVisible(true);
            errMaxStudents.setManaged(true);
            return false;
        }
    }

    private void populateCourse() {
        course.setCourseName(txtName.getText().trim());
        course.setDescription(txtDescription.getText());
        course.setDifficulty(cbDifficulty.getValue());
        course.setEstimatedDuration(Integer.parseInt(txtDuration.getText().trim()));
        String prog = txtProgress.getText();
        course.setProgress((prog == null || prog.trim().isEmpty()) ? 0 : Integer.parseInt(prog.trim()));
        course.setStatus(cbStatus.getValue());
        course.setCategory(txtCategory.getText().trim());
        String mx = txtMaxStudents.getText();
        course.setMaxStudents((mx == null || mx.trim().isEmpty()) ? null : Integer.parseInt(mx.trim()));
        course.setPublished(chkPublished.isSelected());
    }

    private void clearErrors() {
        lblError.setVisible(false);
        lblError.setManaged(false);
        errName.setVisible(false);
        errName.setManaged(false);
        errCategory.setVisible(false);
        errCategory.setManaged(false);
        errDifficulty.setVisible(false);
        errDifficulty.setManaged(false);
        errDuration.setVisible(false);
        errDuration.setManaged(false);
        errProgress.setVisible(false);
        errProgress.setManaged(false);
        errStatus.setVisible(false);
        errStatus.setManaged(false);
        errMaxStudents.setVisible(false);
        errMaxStudents.setManaged(false);
    }

    private void closeWindow() {
        ((Stage) btnSave.getScene().getWindow()).close();
    }
}
