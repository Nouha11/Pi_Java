package controllers.studysession;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;
import models.studysession.Planning;
import models.studysession.StudySession;
import services.studysession.PlanningService;
import services.studysession.StudySessionService;

import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ResourceBundle;

public class StudySessionFormController implements Initializable {

    @FXML private Label formTitle;
    @FXML private ComboBox<Planning> cbPlanning;
    @FXML private TextField txtDuration;
    @FXML private TextField txtActualDuration;
    @FXML private TextField txtXpEarned;
    @FXML private TextField txtBurnoutRisk;
    @FXML private ComboBox<String> cbMood;
    @FXML private ComboBox<String> cbEnergyLevel;
    @FXML private TextField txtBreakDuration;
    @FXML private TextField txtBreakCount;
    @FXML private TextField txtPomodoroCount;
    @FXML private Label lblAutoCalc;
    @FXML private Label lblError;
    @FXML private Button btnSave;

    @FXML private Label errPlanning;
    @FXML private Label errDuration;
    @FXML private Label errActualDuration;

    private final StudySessionService sessionService = new StudySessionService();
    private final PlanningService planningService = new PlanningService();
    private StudySession session;
    private boolean isEdit;
    private int preselectedUserId = 4;
    private Runnable onSaveCallback;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        cbMood.getItems().addAll("", "positive", "neutral", "negative");
        cbMood.setValue("");
        cbEnergyLevel.getItems().addAll("", "low", "medium", "high");
        cbEnergyLevel.setValue("");

        loadPlannings();

        txtActualDuration.textProperty().addListener((obs, o, n) -> autoCalculatePreview());
        txtDuration.textProperty().addListener((obs, o, n) -> autoCalculatePreview());

        txtDuration.focusedProperty().addListener((obs, o, n) -> { if (!n) validateDuration(); });
        txtActualDuration.focusedProperty().addListener((obs, o, n) -> { if (!n) validateActualDuration(); });
    }

    public void initForPlanning(Planning planning, Runnable onSave) {
        this.onSaveCallback = onSave;
        isEdit = false;
        session = new StudySession();
        formTitle.setText("✅ Complete Session — " + planning.getTitle());
        cbPlanning.setValue(planning);
        cbPlanning.setDisable(true);
        txtDuration.setText(String.valueOf(planning.getPlannedDuration()));
        txtActualDuration.setText(String.valueOf(planning.getPlannedDuration()));
        autoCalculatePreview();
    }

    public void initData(StudySession s, Runnable onSave) {
        this.onSaveCallback = onSave;
        if (s == null) {
            isEdit = false;
            session = new StudySession();
            formTitle.setText("➕ New Study Session");
        } else {
            isEdit = true;
            session = s;
            formTitle.setText("✏ Edit Study Session");
            for (Planning p : cbPlanning.getItems()) {
                if (p.getId() == s.getPlanningId()) { cbPlanning.setValue(p); break; }
            }
            txtDuration.setText(String.valueOf(s.getDuration()));
            if (s.getActualDuration() != null) txtActualDuration.setText(String.valueOf(s.getActualDuration()));
            if (s.getXpEarned() != null) txtXpEarned.setText(String.valueOf(s.getXpEarned()));
            if (s.getBurnoutRisk() != null) txtBurnoutRisk.setText(s.getBurnoutRisk());
            cbMood.setValue(s.getMood() != null ? s.getMood() : "");
            cbEnergyLevel.setValue(s.getEnergyLevel() != null ? s.getEnergyLevel() : "");
            if (s.getBreakDuration() != null) txtBreakDuration.setText(String.valueOf(s.getBreakDuration()));
            if (s.getBreakCount() != null) txtBreakCount.setText(String.valueOf(s.getBreakCount()));
            if (s.getPomodoroCount() != null) txtPomodoroCount.setText(String.valueOf(s.getPomodoroCount()));
            autoCalculatePreview();
        }
    }

    private void loadPlannings() {
        try {
            List<Planning> plannings = planningService.findAll();
            cbPlanning.getItems().addAll(plannings);
            cbPlanning.setCellFactory(lv -> new ListCell<>() {
                @Override protected void updateItem(Planning p, boolean empty) {
                    super.updateItem(p, empty);
                    setText(empty || p == null ? "" : p.getTitle() + " — " + p.getCourseNameCache());
                }
            });
            cbPlanning.setButtonCell(new ListCell<>() {
                @Override protected void updateItem(Planning p, boolean empty) {
                    super.updateItem(p, empty);
                    setText(empty || p == null ? "— Select planning —" : p.getTitle() + " — " + p.getCourseNameCache());
                }
            });
        } catch (SQLException e) {
            System.err.println("Failed to load plannings: " + e.getMessage());
        }
    }

    private void autoCalculatePreview() {
        try {
            String durText = txtActualDuration.getText();
            if (durText == null || durText.trim().isEmpty())
                durText = txtDuration.getText();
            int dur = Integer.parseInt(durText.trim());
            int energyUsed = dur / 10;
            int xp = dur * 2;
            String burnout = energyUsed > 80 ? "HIGH" : energyUsed > 40 ? "MODERATE" : "LOW";
            lblAutoCalc.setText("⚡ Auto: XP = " + xp + " | Burnout = " + burnout + " | Energy used = " + energyUsed);
            txtXpEarned.setText(String.valueOf(xp));
            txtBurnoutRisk.setText(burnout);
        } catch (NumberFormatException e) {
            lblAutoCalc.setText("⚡ Enter duration to auto-calculate XP & burnout");
        }
    }

    @FXML
    private void handleSave() {
        clearErrors();
        if (!validateAll()) return;

        populateSession();
        sessionService.autoCalculate(session);

        String validationError = sessionService.validate(session, isEdit);
        if (validationError != null) {
            lblError.setText("⚠ " + validationError);
            lblError.setVisible(true);
            lblError.setManaged(true);
            return;
        }

        try {
            if (isEdit) sessionService.update(session);
            else sessionService.create(session);

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
    private void handleCancel() { closeWindow(); }

    // ─────────────────────────────────────────────
    //  VALIDATION
    // ─────────────────────────────────────────────

    private boolean validateAll() {
        boolean ok = true;
        if (cbPlanning.getValue() == null) {
            errPlanning.setText("Planning is required.");
            errPlanning.setVisible(true);
            errPlanning.setManaged(true);
            ok = false;
        } else {
            errPlanning.setVisible(false);
            errPlanning.setManaged(false);
        }
        if (!validateDuration()) ok = false;
        if (!validateActualDuration()) ok = false;
        return ok;
    }

    private boolean validateDuration() {
        try {
            int val = Integer.parseInt(txtDuration.getText().trim());
            if (val <= 0) throw new NumberFormatException();
            errDuration.setVisible(false);
            errDuration.setManaged(false);
            return true;
        } catch (NumberFormatException e) {
            errDuration.setText("Planned duration must be a positive integer (minutes).");
            errDuration.setVisible(true);
            errDuration.setManaged(true);
            return false;
        }
    }

    private boolean validateActualDuration() {
        String v = txtActualDuration.getText();
        if (v == null || v.trim().isEmpty()) {
            errActualDuration.setVisible(false);
            errActualDuration.setManaged(false);
            return true;
        }
        try {
            int val = Integer.parseInt(v.trim());
            if (val < 0) throw new NumberFormatException();
            errActualDuration.setVisible(false);
            errActualDuration.setManaged(false);
            return true;
        } catch (NumberFormatException e) {
            errActualDuration.setText("Must be a non-negative integer.");
            errActualDuration.setVisible(true);
            errActualDuration.setManaged(true);
            return false;
        }
    }

    private void populateSession() {
        session.setUserId(preselectedUserId);
        Planning p = cbPlanning.getValue();
        if (p != null) session.setPlanningId(p.getId());
        session.setDuration(Integer.parseInt(txtDuration.getText().trim()));
        String ad = txtActualDuration.getText();
        session.setActualDuration((ad == null || ad.trim().isEmpty()) ? null : Integer.parseInt(ad.trim()));
        session.setMood(cbMood.getValue().isEmpty() ? null : cbMood.getValue());
        session.setEnergyLevel(cbEnergyLevel.getValue().isEmpty() ? null : cbEnergyLevel.getValue());
        String bd = txtBreakDuration.getText();
        session.setBreakDuration((bd == null || bd.trim().isEmpty()) ? null : Integer.parseInt(bd.trim()));
        String bc = txtBreakCount.getText();
        session.setBreakCount((bc == null || bc.trim().isEmpty()) ? null : Integer.parseInt(bc.trim()));
        String pc = txtPomodoroCount.getText();
        session.setPomodoroCount((pc == null || pc.trim().isEmpty()) ? null : Integer.parseInt(pc.trim()));
        if (!isEdit) {
            session.setStartedAt(LocalDateTime.now());
            session.setCompletedAt(LocalDateTime.now());
        }
    }

    private void clearErrors() {
        lblError.setVisible(false);
        lblError.setManaged(false);
        errPlanning.setVisible(false);
        errPlanning.setManaged(false);
        errDuration.setVisible(false);
        errDuration.setManaged(false);
        errActualDuration.setVisible(false);
        errActualDuration.setManaged(false);
    }

    private void closeWindow() { ((Stage) btnSave.getScene().getWindow()).close(); }
}