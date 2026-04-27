package controllers.users;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;
import utils.ThemeManager;

import java.net.URL;
import java.time.LocalTime;
import java.util.ResourceBundle;

public class ThemeSettingsController implements Initializable {

    @FXML private ToggleButton  btnLight;
    @FXML private ToggleButton  btnDark;
    @FXML private ToggleButton  btnSchedule;

    @FXML private javafx.scene.layout.VBox schedulePane;

    @FXML private Spinner<Integer> spDarkHour;
    @FXML private Spinner<Integer> spDarkMin;
    @FXML private Spinner<Integer> spLightHour;
    @FXML private Spinner<Integer> spLightMin;

    @FXML private ComboBox<String> cbPresets;
    @FXML private Label            lblPreview;
    @FXML private Button           btnApply;
    @FXML private Button           btnCancel;

    private final ToggleGroup modeGroup = new ToggleGroup();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Wire toggle group in code (avoids FXML forward-reference issues)
        btnLight.setToggleGroup(modeGroup);
        btnDark.setToggleGroup(modeGroup);
        btnSchedule.setToggleGroup(modeGroup);

        // Spinners
        spDarkHour .setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 20));
        spDarkMin  .setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59,  0, 5));
        spLightHour.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23,  7));
        spLightMin .setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59,  0, 5));

        // Presets
        cbPresets.getItems().add("-- Custom --");
        cbPresets.getItems().addAll(ThemeManager.PRESETS.keySet());
        cbPresets.setValue("-- Custom --");
        cbPresets.setOnAction(e -> applyPreset());

        // Schedule pane visibility
        schedulePane.setVisible(false);
        schedulePane.setManaged(false);
        btnSchedule.selectedProperty().addListener((obs, old, val) -> {
            schedulePane.setVisible(val);
            schedulePane.setManaged(val);
        });

        // Load current state
        ThemeManager tm = ThemeManager.getInstance();
        switch (tm.getMode()) {
            case LIGHT     -> btnLight.setSelected(true);
            case DARK      -> btnDark.setSelected(true);
            case SCHEDULED -> {
                btnSchedule.setSelected(true);
                spDarkHour .getValueFactory().setValue(tm.getDarkStart().getHour());
                spDarkMin  .getValueFactory().setValue(tm.getDarkStart().getMinute());
                spLightHour.getValueFactory().setValue(tm.getLightStart().getHour());
                spLightMin .getValueFactory().setValue(tm.getLightStart().getMinute());
            }
        }

        updatePreview();
        spDarkHour .valueProperty().addListener((o, a, b) -> updatePreview());
        spDarkMin  .valueProperty().addListener((o, a, b) -> updatePreview());
        spLightHour.valueProperty().addListener((o, a, b) -> updatePreview());
        spLightMin .valueProperty().addListener((o, a, b) -> updatePreview());
    }

    private void applyPreset() {
        String sel = cbPresets.getValue();
        if (sel == null || sel.equals("-- Custom --")) return;
        LocalTime[] t = ThemeManager.PRESETS.get(sel);
        if (t == null) return;
        spDarkHour .getValueFactory().setValue(t[0].getHour());
        spDarkMin  .getValueFactory().setValue(t[0].getMinute());
        spLightHour.getValueFactory().setValue(t[1].getHour());
        spLightMin .getValueFactory().setValue(t[1].getMinute());
        updatePreview();
    }

    private void updatePreview() {
        lblPreview.setText(String.format("Dark at %02d:%02d  •  Light at %02d:%02d",
            spDarkHour.getValue(), spDarkMin.getValue(),
            spLightHour.getValue(), spLightMin.getValue()));
    }

    @FXML
    private void onApply() {
        ThemeManager tm = ThemeManager.getInstance();
        Toggle sel = modeGroup.getSelectedToggle();
        if      (sel == btnLight)    tm.setLight();
        else if (sel == btnDark)     tm.setDark();
        else if (sel == btnSchedule) {
            tm.setScheduled(
                LocalTime.of(spDarkHour.getValue(),  spDarkMin.getValue()),
                LocalTime.of(spLightHour.getValue(), spLightMin.getValue()));
        }
        close();
    }

    @FXML
    private void onCancel() { close(); }

    private void close() {
        ((Stage) btnApply.getScene().getWindow()).close();
    }
}
