package controllers.gamification;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import models.gamification.Game;
import models.gamification.Reward;
import services.gamification.GameService;
import services.gamification.RewardService;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class RewardFormController {

    @FXML private TextField        nameField;
    @FXML private TextArea         descriptionArea;
    @FXML private ComboBox<String> typeCombo;
    @FXML private TextField        valueField;
    @FXML private TextField        requirementField;
    @FXML private TextField        requiredLevelField;
    @FXML private CheckBox         isActiveCheck;
    @FXML private ListView<Game>   gamesList;
    @FXML private Label            errorLabel;
    @FXML private Button           saveBtn;
    @FXML private Button           pickIconBtn;
    @FXML private ImageView        iconPreview;
    @FXML private Label            iconPathLabel;

    private final RewardService rewardService = new RewardService();
    private final GameService   gameService   = new GameService();
    private Reward editingReward = null;
    private String selectedIconPath = null; // absolute path chosen by user

    @FXML
    public void initialize() {
        typeCombo.setItems(FXCollections.observableArrayList(
                "BADGE", "ACHIEVEMENT", "BONUS_XP", "BONUS_TOKENS"));
        isActiveCheck.setSelected(true);
        errorLabel.setText("");
        gamesList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        loadGamesList();
    }

    private void loadGamesList() {
        try {
            gamesList.setItems(FXCollections.observableArrayList(gameService.getAllGames()));
        } catch (Exception e) {
            showError("Could not load games: " + e.getMessage());
        }
    }

    @FXML
    private void handlePickIcon() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Reward Icon");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File file = fc.showOpenDialog(pickIconBtn.getScene().getWindow());
        if (file == null) return;

        try {
            // Copy into project resources so the image travels with the project
            Path iconsDir = resolveIconsDir();
            Files.createDirectories(iconsDir);

            // Avoid name collisions by prefixing with timestamp
            String destName = System.currentTimeMillis() + "_" + file.getName();
            Path dest = iconsDir.resolve(destName);
            Files.copy(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);

            selectedIconPath = destName; // store only the filename
            iconPathLabel.setText(destName);
            loadPreview(destName);
        } catch (IOException e) {
            // Fallback: store absolute path if copy fails
            selectedIconPath = file.getAbsolutePath();
            iconPathLabel.setText(file.getName());
            loadPreview(selectedIconPath);
        }
    }

    /** Returns the absolute path to src/main/resources/images/rewards */
    private Path resolveIconsDir() {
        // Try to locate via class resource first (works when running from IDE)
        URL res = getClass().getResource("/images/rewards");
        if (res != null) return Paths.get(res.getPath());

        // Fallback: walk up from the running class location to find src/main/resources
        URL classUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
        Path base = Paths.get(classUrl.getPath()).getParent();
        // target: <project>/src/main/resources/images/rewards
        return base.resolve("../src/main/resources/images/rewards").normalize();
    }

    private void loadPreview(String nameOrPath) {
        if (nameOrPath == null || nameOrPath.isBlank()) {
            iconPreview.setImage(null);
            return;
        }
        try {
            // Try as resource first (filename only)
            var stream = getClass().getResourceAsStream("/images/rewards/" + nameOrPath);
            if (stream != null) {
                iconPreview.setImage(new Image(stream, 90, 90, true, true));
                return;
            }
            // Try as absolute path
            File f = new File(nameOrPath);
            if (f.exists()) {
                iconPreview.setImage(new Image(f.toURI().toString(), 90, 90, true, true));
                return;
            }
            // Try resolving against icons dir on disk
            Path iconsDir = resolveIconsDir();
            File fromDir = iconsDir.resolve(nameOrPath).toFile();
            if (fromDir.exists()) {
                iconPreview.setImage(new Image(fromDir.toURI().toString(), 90, 90, true, true));
            }
        } catch (Exception e) {
            iconPreview.setImage(null);
        }
    }

    public void setRewardToEdit(Reward reward) {
        this.editingReward = reward;
        nameField.setText(reward.getName());
        descriptionArea.setText(reward.getDescription() != null ? reward.getDescription() : "");
        typeCombo.setValue(reward.getType());
        valueField.setText(String.valueOf(reward.getValue()));
        requirementField.setText(reward.getRequirement() != null ? reward.getRequirement() : "");
        isActiveCheck.setSelected(reward.isActive());
        if (reward.getRequiredLevel() != null)
            requiredLevelField.setText(String.valueOf(reward.getRequiredLevel()));

        if (reward.getIcon() != null && !reward.getIcon().isBlank()) {
            selectedIconPath = reward.getIcon();
            iconPathLabel.setText(new File(reward.getIcon()).getName());
            loadPreview(selectedIconPath);
        }

        try {
            List<Game> linked = gameService.getGamesForReward(reward.getId());
            for (Game lg : linked)
                for (Game item : gamesList.getItems())
                    if (item.getId() == lg.getId())
                        gamesList.getSelectionModel().select(item);
        } catch (Exception e) {
            showError("Could not load linked games: " + e.getMessage());
        }
    }

    @FXML
    private void handleSave() {
        errorLabel.setText("");

        String name = nameField.getText().trim();
        if (name.isEmpty())    { showError("Name is required.");                    return; }
        if (name.length() < 3) { showError("Name must be at least 3 characters."); return; }
        if (typeCombo.getValue() == null) { showError("Please select a type."); return; }

        int value;
        try {
            value = Integer.parseInt(valueField.getText().trim());
            if (value < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showError("Value must be a non-negative whole number."); return;
        }

        Integer requiredLevel = null;
        String rlText = requiredLevelField.getText().trim();
        if (!rlText.isEmpty()) {
            try {
                requiredLevel = Integer.parseInt(rlText);
                if (requiredLevel < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                showError("Required Level must be a non-negative whole number."); return;
            }
        }

        try {
            int excludeId = (editingReward != null) ? editingReward.getId() : 0;
            if (rewardService.rewardNameExists(name, excludeId)) {
                showError("A reward with this name already exists!"); return;
            }
        } catch (Exception e) { showError("DB error: " + e.getMessage()); return; }

        Reward reward = (editingReward != null) ? editingReward : new Reward();
        reward.setName(name);
        reward.setDescription(descriptionArea.getText().trim());
        reward.setType(typeCombo.getValue());
        reward.setValue(value);
        reward.setRequirement(requirementField.getText().trim());
        reward.setIcon(selectedIconPath != null ? selectedIconPath : "");
        reward.setActive(isActiveCheck.isSelected());
        reward.setRequiredLevel(requiredLevel);

        try {
            if (editingReward == null) {
                rewardService.addReward(reward);
                List<Reward> all = rewardService.getAllRewards();
                for (Reward r : all)
                    if (r.getName().equals(name)) { reward.setId(r.getId()); break; }
            } else {
                rewardService.updateReward(reward);
            }
            syncGameLinks(reward.getId());
            closeWindow();
        } catch (Exception e) { showError("Save error: " + e.getMessage()); }
    }

    private void syncGameLinks(int rewardId) throws Exception {
        List<Game> current = gameService.getGamesForReward(rewardId);
        for (Game g : current) gameService.removeRewardFromGame(g.getId(), rewardId);
        for (Game selected : gamesList.getSelectionModel().getSelectedItems())
            gameService.addRewardToGame(selected.getId(), rewardId);
    }

    @FXML private void handleCancel() { closeWindow(); }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setStyle("-fx-text-fill: #e94560; -fx-font-weight: bold;");
    }

    private void closeWindow() { ((Stage) saveBtn.getScene().getWindow()).close(); }
}
