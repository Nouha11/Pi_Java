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

/**
 * RewardFormController — CREATE + UPDATE controller for the reward form.
 *
 * Responsibilities:
 *  - Add a new reward (CREATE) when editingReward == null
 *  - Edit an existing reward (UPDATE) when setRewardToEdit() has been called
 *  - Validate all fields before saving
 *  - Pick and copy an icon image into the project resources
 *  - Link/unlink the reward to games via the game_rewards junction table
 */
public class RewardFormController {

    // ── Form fields injected from reward_form.fxml ────────────────────────────
    @FXML private TextField        nameField;
    @FXML private TextArea         descriptionArea;
    @FXML private ComboBox<String> typeCombo;
    @FXML private TextField        valueField;
    @FXML private TextField        requirementField;
    @FXML private TextField        requiredLevelField;
    @FXML private CheckBox         isActiveCheck;
    @FXML private ListView<Game>   gamesList;

    // Per-field error labels (shown directly under each field)
    @FXML private Label errName, errType, errValue, errRequiredLevel;
    @FXML private Button           saveBtn;
    @FXML private Button           pickIconBtn;
    @FXML private ImageView        iconPreview;      // live preview of the selected icon
    @FXML private Label            iconPathLabel;    // shows the filename of the selected icon

    // Services — controller never writes SQL directly
    private final RewardService rewardService = new RewardService();
    private final GameService   gameService   = new GameService();

    // null = Add mode, non-null = Edit mode
    private Reward editingReward = null;

    // Stores the filename (or absolute path as fallback) of the chosen icon
    private String selectedIconPath = null;

    // ── Called automatically by JavaFX after FXML fields are injected ─────────
    @FXML
    public void initialize() {
        // Populate the type ComboBox with all valid reward types
        typeCombo.setItems(FXCollections.observableArrayList(
                "BADGE", "ACHIEVEMENT", "BONUS_XP", "BONUS_TOKENS"));
        isActiveCheck.setSelected(true);

        // Allow selecting multiple games to link to this reward
        gamesList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Load all games into the ListView so the user can link them
        loadGamesList();
    }

    // ── Loads all games into the ListView for linking ─────────────────────────
    private void loadGamesList() {
        try {
            gamesList.setItems(FXCollections.observableArrayList(gameService.getAllGames()));
        } catch (Exception e) {
            showFieldError(errName, "Could not load games: " + e.getMessage());
        }
    }

    // ── ICON PICKER: opens a FileChooser, copies the image into resources ─────
    // The image is copied to src/main/resources/images/rewards/ with a timestamp
    // prefix to avoid name collisions. Only the filename is stored in the DB.
    @FXML
    private void handlePickIcon() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Reward Icon");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File file = fc.showOpenDialog(pickIconBtn.getScene().getWindow());
        if (file == null) return;

        // Always show the filename and preview immediately from the source file
        // This works regardless of whether the copy succeeds
        selectedIconPath = file.getAbsolutePath();
        iconPathLabel.setText(file.getName());
        try {
            iconPreview.setImage(new javafx.scene.image.Image(
                    file.toURI().toString(), 90, 90, true, true));
        } catch (Exception ignored) {}

        // Try to copy into project resources for portability
        try {
            Path iconsDir = resolveIconsDir();
            Files.createDirectories(iconsDir);
            String destName = System.currentTimeMillis() + "_" + file.getName();
            Path dest = iconsDir.resolve(destName);
            Files.copy(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
            // If copy succeeded, store just the filename (portable)
            selectedIconPath = destName;
        } catch (Exception e) {
            // Copy failed — keep the absolute path already set above
            System.err.println("Icon copy failed, using absolute path: " + e.getMessage());
        }
    }

    // ── Resolves the absolute path to src/main/resources/images/rewards ───────
    private Path resolveIconsDir() {
        // Works when running from IDE (classpath resource exists)
        URL res = getClass().getResource("/images/rewards");
        if (res != null) return Paths.get(res.getPath());

        // Fallback: navigate from the compiled classes folder back to src/main/resources
        URL classUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
        Path base = Paths.get(classUrl.getPath()).getParent();
        return base.resolve("../src/main/resources/images/rewards").normalize();
    }

    // ── Loads and displays the icon preview at 90x90 ─────────────────────────
    // Tries three locations in order: classpath resource, absolute path, icons dir
    private void loadPreview(String nameOrPath) {
        if (nameOrPath == null || nameOrPath.isBlank()) {
            iconPreview.setImage(null);
            return;
        }
        try {
            var stream = getClass().getResourceAsStream("/images/rewards/" + nameOrPath);
            if (stream != null) {
                iconPreview.setImage(new Image(stream, 90, 90, true, true));
                return;
            }
            File f = new File(nameOrPath);
            if (f.exists()) {
                iconPreview.setImage(new Image(f.toURI().toString(), 90, 90, true, true));
                return;
            }
            File fromDir = resolveIconsDir().resolve(nameOrPath).toFile();
            if (fromDir.exists()) {
                iconPreview.setImage(new Image(fromDir.toURI().toString(), 90, 90, true, true));
            }
        } catch (Exception e) {
            iconPreview.setImage(null);
        }
    }

    // ── EDIT MODE: called by GameRewardController to pre-fill the form ────────
    // Sets editingReward so handleSave() knows to call updateReward() instead of addReward()
    public void setRewardToEdit(Reward reward) {
        this.editingReward = reward;

        // Pre-fill all fields with the existing reward's data
        nameField.setText(reward.getName());
        descriptionArea.setText(reward.getDescription() != null ? reward.getDescription() : "");
        typeCombo.setValue(reward.getType());
        valueField.setText(String.valueOf(reward.getValue()));
        requirementField.setText(reward.getRequirement() != null ? reward.getRequirement() : "");
        isActiveCheck.setSelected(reward.isActive());
        if (reward.getRequiredLevel() != null)
            requiredLevelField.setText(String.valueOf(reward.getRequiredLevel()));

        // Load and preview the existing icon if one is set
        if (reward.getIcon() != null && !reward.getIcon().isBlank()) {
            selectedIconPath = reward.getIcon();
            iconPathLabel.setText(new File(reward.getIcon()).getName());
            loadPreview(selectedIconPath);
        }

        // Pre-select games already linked to this reward in the ListView
        try {
            List<Game> linked = gameService.getGamesForReward(reward.getId());
            for (Game lg : linked)
                for (Game item : gamesList.getItems())
                    if (item.getId() == lg.getId())
                        gamesList.getSelectionModel().select(item);
        } catch (Exception e) {
            showFieldError(errName, "Could not load linked games: " + e.getMessage());
        }
    }

    // ── SAVE: validates all fields, then calls addReward() or updateReward() ──
    @FXML
    private void handleSave() {
        clearErrors();
        boolean ok = true;

        // 1. Name
        String name = nameField.getText().trim();
        if (name.isEmpty())        { showFieldError(errName, "Name is required.");                    ok = false; }
        else if (name.length() < 3){ showFieldError(errName, "Name must be at least 3 characters."); ok = false; }

        // 2. Type
        if (typeCombo.getValue() == null) { showFieldError(errType, "Please select a type."); ok = false; }

        // 3. Value
        int value = 0;
        try {
            value = Integer.parseInt(valueField.getText().trim());
            if (value < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showFieldError(errValue, "Must be a whole number >= 0."); ok = false;
        }

        // 4. Required Level (optional)
        Integer requiredLevel = null;
        String rlText = requiredLevelField.getText().trim();
        if (!rlText.isEmpty()) {
            try {
                requiredLevel = Integer.parseInt(rlText);
                if (requiredLevel < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                showFieldError(errRequiredLevel, "Must be a whole number >= 0."); ok = false;
            }
        }

        if (!ok) return;

        // 5. Uniqueness check
        try {
            int excludeId = (editingReward != null) ? editingReward.getId() : 0;
            if (rewardService.rewardNameExists(name, excludeId)) {
                showFieldError(errName, "A reward with this name already exists!"); return;
            }
        } catch (Exception e) { showFieldError(errName, "DB error: " + e.getMessage()); return; }

        // 6. Build and persist
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
                // addReward now sets reward.getId() via RETURN_GENERATED_KEYS
            } else {
                rewardService.updateReward(reward);
            }
            syncGameLinks(reward.getId());
            closeWindow();
        } catch (Exception e) { showFieldError(errName, "Save error: " + e.getMessage()); }
    }

    // ── Shows an error message directly under a specific field ────────────────
    private void showFieldError(Label lbl, String msg) {
        lbl.setText(msg);
        lbl.setVisible(true);
        lbl.setManaged(true);
    }

    // ── Hides all per-field error labels at the start of each save attempt ────
    private void clearErrors() {
        for (Label l : new Label[]{errName, errType, errValue, errRequiredLevel}) {
            l.setText("");
            l.setVisible(false);
            l.setManaged(false);
        }
    }

    // ── Syncs the game_rewards junction table with the current ListView selection
    // Strategy: delete all existing links for this reward, then re-insert selected ones.
    // This is simpler than computing a diff and is correct regardless of what changed.
    private void syncGameLinks(int rewardId) throws Exception {
        // Remove all existing game links for this reward
        List<Game> current = gameService.getGamesForReward(rewardId);
        for (Game g : current) gameService.removeRewardFromGame(g.getId(), rewardId);

        // Re-add only the games currently selected in the ListView
        for (Game selected : gamesList.getSelectionModel().getSelectedItems())
            gameService.addRewardToGame(selected.getId(), rewardId);
    }

    // ── CANCEL: close the form without saving ─────────────────────────────────
    @FXML private void handleCancel() { closeWindow(); }

    // ── Closes the form window ────────────────────────────────────────────────
    private void closeWindow() { ((Stage) saveBtn.getScene().getWindow()).close(); }
}
