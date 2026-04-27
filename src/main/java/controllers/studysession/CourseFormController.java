package controllers.studysession;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import models.studysession.Course;
import models.studysession.PdfResource;
import services.studysession.CourseService;
import services.studysession.PdfResourceService;
import utils.UserSession;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
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

    // PDF section FXML fields
    @FXML private VBox vboxPdfSection;
    @FXML private Button btnAddPdf;
    @FXML private Label lblPdfStatus;
    @FXML private VBox vboxPdfList;

    private final CourseService courseService = new CourseService();
    private final PdfResourceService pdfResourceService = new PdfResourceService();

    private Course course;
    private boolean isEdit;
    private Runnable onSaveCallback;
    private Integer createdById; // set by TutorCourseController for new courses

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
            configurePdfSection(false);
        } else if (c.getId() == 0) {
            // New course pre-populated with ownership info (e.g. from TutorCourseController)
            isEdit = false;
            course = c;
            formTitle.setText("➕ New Course");
            cbStatus.setValue("NOT_STARTED");
            configurePdfSection(false);
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
            configurePdfSection(true);
            loadPdfList();
        }
    }

    // ─────────────────────────────────────────────
    //  PDF SECTION — 16.2 / 16.3 / 16.4
    // ─────────────────────────────────────────────

    /**
     * Configures the PDF section visibility and state.
     * When editing an existing course (courseId > 0), the section is fully active.
     * When creating a new course, the "Add PDF" button is disabled and a hint is shown.
     */
    private void configurePdfSection(boolean courseExists) {
        if (courseExists) {
            btnAddPdf.setDisable(false);
            lblPdfStatus.setVisible(false);
            lblPdfStatus.setManaged(false);
        } else {
            btnAddPdf.setDisable(true);
            lblPdfStatus.setText("Save the course first to add PDF resources.");
            lblPdfStatus.setVisible(true);
            lblPdfStatus.setManaged(true);
        }
    }

    /**
     * 16.2 — Loads and displays existing PDFs for the course being edited.
     */
    private void loadPdfList() {
        vboxPdfList.getChildren().clear();
        if (course == null || course.getId() == 0) return;

        try {
            List<PdfResource> resources = pdfResourceService.findByCourse(course.getId());
            if (resources.isEmpty()) {
                Label emptyLabel = new Label("No PDF resources uploaded yet.");
                emptyLabel.setStyle("-fx-font-size:11px; -fx-text-fill:#94a3b8;");
                vboxPdfList.getChildren().add(emptyLabel);
            } else {
                for (PdfResource resource : resources) {
                    vboxPdfList.getChildren().add(buildPdfCard(resource));
                }
            }
        } catch (SQLException e) {
            lblPdfStatus.setText("⚠ Could not load PDF resources: " + e.getMessage());
            lblPdfStatus.setVisible(true);
            lblPdfStatus.setManaged(true);
            e.printStackTrace();
        }
    }

    /**
     * Builds a styled card for a single PDF resource with a "🗑 Remove" button.
     */
    private HBox buildPdfCard(PdfResource resource) {
        HBox card = new HBox(8);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color:#f8fafc; -fx-background-radius:8; " +
                "-fx-border-color:#e2e8f0; -fx-border-radius:8; -fx-padding:6 10;");

        // PDF icon
        Label icon = new Label("📄");
        icon.setStyle("-fx-font-size:14px;");

        // Title + topic info
        VBox info = new VBox(2);
        Label titleLabel = new Label(resource.getTitle());
        titleLabel.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:#1e293b;");
        info.getChildren().add(titleLabel);

        if (resource.getTopic() != null && !resource.getTopic().isBlank()) {
            Label topicLabel = new Label("🏷 " + resource.getTopic());
            topicLabel.setStyle("-fx-font-size:10px; -fx-text-fill:#64748b;");
            info.getChildren().add(topicLabel);
        }

        // File existence warning
        File file = new File(resource.getFilePath());
        if (!file.exists()) {
            Label warnLabel = new Label("⚠ File not found");
            warnLabel.setStyle("-fx-font-size:10px; -fx-text-fill:#ef4444;");
            info.getChildren().add(warnLabel);
        }

        HBox.setHgrow(info, Priority.ALWAYS);

        // Remove button — 16.4
        Button btnRemove = new Button("🗑 Remove");
        btnRemove.setStyle("-fx-background-color:#fee2e2; -fx-text-fill:#dc2626; " +
                "-fx-background-radius:6; -fx-cursor:hand; -fx-padding:3 8; -fx-font-size:10px;");
        btnRemove.setOnAction(e -> handleRemovePdf(resource.getId()));

        card.getChildren().addAll(icon, info, btnRemove);
        return card;
    }

    /**
     * 16.3 — Opens a FileChooser filtered to *.pdf; for each selected file calls
     * PdfResourceService.addResource; refreshes the PDF list display.
     */
    @FXML
    private void handleAddPdf() {
        if (course == null || course.getId() == 0) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select PDF File(s)");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );

        Stage stage = (Stage) btnSave.getScene().getWindow();
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(stage);

        if (selectedFiles == null || selectedFiles.isEmpty()) return;

        int uploadedById = UserSession.getInstance().getUserId();
        int successCount = 0;
        StringBuilder errors = new StringBuilder();

        for (File file : selectedFiles) {
            // Default title = filename without extension
            String filename = file.getName();
            String title = filename.endsWith(".pdf")
                    ? filename.substring(0, filename.length() - 4)
                    : filename;

            try {
                pdfResourceService.addResource(
                        course.getId(),
                        title,
                        null,           // topic: optional, not collected in this dialog
                        file.getAbsolutePath(),
                        uploadedById
                );
                successCount++;
            } catch (SQLException | IOException e) {
                errors.append("⚠ ").append(filename).append(": ").append(e.getMessage()).append("\n");
                e.printStackTrace();
            }
        }

        // Refresh the list
        loadPdfList();

        // Show status feedback
        if (errors.length() > 0) {
            lblPdfStatus.setText(errors.toString().trim());
            lblPdfStatus.setStyle("-fx-font-size:11px; -fx-text-fill:#ef4444;");
            lblPdfStatus.setVisible(true);
            lblPdfStatus.setManaged(true);
        } else if (successCount > 0) {
            lblPdfStatus.setText("✅ " + successCount + " PDF(s) added successfully.");
            lblPdfStatus.setStyle("-fx-font-size:11px; -fx-text-fill:#16a34a;");
            lblPdfStatus.setVisible(true);
            lblPdfStatus.setManaged(true);
        }
    }

    /**
     * 16.4 — Removes a PDF resource by id and refreshes the list.
     */
    private void handleRemovePdf(int resourceId) {
        try {
            pdfResourceService.removeResource(resourceId);
            loadPdfList();
            lblPdfStatus.setText("🗑 Resource removed.");
            lblPdfStatus.setStyle("-fx-font-size:11px; -fx-text-fill:#64748b;");
            lblPdfStatus.setVisible(true);
            lblPdfStatus.setManaged(true);
        } catch (SQLException e) {
            lblPdfStatus.setText("⚠ Could not remove resource: " + e.getMessage());
            lblPdfStatus.setStyle("-fx-font-size:11px; -fx-text-fill:#ef4444;");
            lblPdfStatus.setVisible(true);
            lblPdfStatus.setManaged(true);
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────
    //  SAVE / CANCEL
    // ─────────────────────────────────────────────

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
            txtName.setStyle("-fx-border-color: #ef4444; -fx-border-width: 1; -fx-border-radius: 3;");
            return false;
        }
        if (v.trim().length() < 3) {
            errName.setText("Minimum 3 characters.");
            errName.setVisible(true);
            errName.setManaged(true);
            txtName.setStyle("-fx-border-color: #ef4444; -fx-border-width: 1; -fx-border-radius: 3;");
            return false;
        }
        if (v.trim().length() > 255) {
            errName.setText("Maximum 255 characters.");
            errName.setVisible(true);
            errName.setManaged(true);
            txtName.setStyle("-fx-border-color: #ef4444; -fx-border-width: 1; -fx-border-radius: 3;");
            return false;
        }
        errName.setVisible(false);
        errName.setManaged(false);
        txtName.setStyle("");
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
