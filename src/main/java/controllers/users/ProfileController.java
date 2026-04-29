package controllers.users;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import models.gamification.Game;
import models.users.User;
import org.mindrot.jbcrypt.BCrypt;
import services.gamification.FavoriteGameService;
import services.users.UserService;
import services.users.ValidationUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

public class ProfileController implements Initializable {

    @FXML private Label     lblInitials, lblFullName, lblRoleBadge, lblEmail;
    @FXML private Label     lblStatus, lblVerified, lblXp, lblXpLabel, lblCreatedAt;
    @FXML private Label     lblGravatarInfo, lblGravatarStatus;
    @FXML private ImageView imgAvatar;
    @FXML private StackPane paneInitials;
    @FXML private Button    btnUploadPic, btnRemovePic;

    @FXML private PasswordField pfCurrent, pfNew, pfConfirm;
    @FXML private Label         lblPwdMsg;

    @FXML private TabPane  profileTabs;
    @FXML private FlowPane favoritesPane;
    @FXML private Label    lblFavCount;
    @FXML private VBox     lblFavEmpty;

    private User          currentUser;
    private BorderPane    mainLayout;
    private Parent        previousView;

    private final UserService         userService = new UserService();
    private final FavoriteGameService favService  = new FavoriteGameService();

    // Avatars stored in user home — works on every machine
    private static final String UPLOAD_DIR =
        System.getProperty("user.home") + File.separator + "nova_avatars" + File.separator;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        new File(UPLOAD_DIR).mkdirs();
        if (lblPwdMsg != null) { lblPwdMsg.setVisible(false); lblPwdMsg.setManaged(false); }
    }

    // ── Entry points ──────────────────────────────────────────────────────────

    public void setCurrentUser(User user, BorderPane layout) {
        setCurrentUser(user, layout, null);
    }

    public void setCurrentUser(User user, BorderPane layout, Parent previous) {
        this.currentUser  = user;
        this.mainLayout   = layout;
        this.previousView = previous;
        populateProfile();
        applyRoleRestrictions();
        loadAvatar();
    }

    // ── Role restrictions ─────────────────────────────────────────────────────

    private void applyRoleRestrictions() {
        boolean isTutor = currentUser.getRole() == User.Role.ROLE_TUTOR;
        if (isTutor) {
            if (lblXp      != null) { lblXp.setVisible(false);      lblXp.setManaged(false); }
            if (lblXpLabel != null) { lblXpLabel.setVisible(false); lblXpLabel.setManaged(false); }
            if (profileTabs != null)
                profileTabs.getTabs().removeIf(t -> "Favorite Games".equals(t.getText()));
        } else {
            loadFavorites();
        }
    }

    // ── Populate fields ───────────────────────────────────────────────────────

    private void populateProfile() {
        String username = currentUser.getUsername();
        String initials = username.length() >= 2
                ? username.substring(0, 2).toUpperCase()
                : username.toUpperCase();
        lblInitials.setText(initials);
        lblFullName.setText(username);
        lblEmail.setText(currentUser.getEmail());
        lblRoleBadge.setText(currentUser.getRole().name().replace("ROLE_", ""));
        if (lblXp != null) lblXp.setText(String.valueOf(currentUser.getXp()));
        lblStatus.setText(currentUser.isBanned() ? "Banned"
                        : currentUser.isActive()  ? "Active" : "Inactive");
        lblVerified.setText(currentUser.isVerified() ? "Yes" : "No");
        if (currentUser.getCreatedAt() != null)
            lblCreatedAt.setText(currentUser.getCreatedAt()
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
        else lblCreatedAt.setText("-");
        if (lblGravatarInfo   != null) lblGravatarInfo.setText("");
        if (lblGravatarStatus != null) lblGravatarStatus.setText("");
    }

    // ── Avatar: uploaded pic or initials (no Gravatar) ────────────────────────

    private void loadAvatar() {
        if (imgAvatar == null) return;
        String picPath = currentUser.getProfilePicture();
        if (picPath != null && !picPath.isBlank()) {
            File f = new File(picPath);
            if (f.exists()) {
                showImageInAvatar(new Image(f.toURI().toString(), 150, 150, true, true));
                if (lblGravatarInfo != null) lblGravatarInfo.setText("Custom photo");
                return;
            }
        }
        // No uploaded pic — show initials
        if (imgAvatar    != null) { imgAvatar.setVisible(false);   imgAvatar.setManaged(false); }
        if (paneInitials != null) { paneInitials.setVisible(true); paneInitials.setManaged(true); }
        if (lblGravatarInfo != null) lblGravatarInfo.setText("No photo uploaded");
    }

    private void showImageInAvatar(Image img) {
        if (imgAvatar == null) return;
        imgAvatar.setImage(img);
        imgAvatar.setVisible(true);
        imgAvatar.setManaged(true);
        if (paneInitials != null) { paneInitials.setVisible(false); paneInitials.setManaged(false); }
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    @FXML
    private void onUploadPicture() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Profile Picture");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));
        Stage stage = (Stage) btnUploadPic.getScene().getWindow();
        File selected = chooser.showOpenDialog(stage);
        if (selected == null) return;
        try {
            String destName = currentUser.getId() + "_" + selected.getName();
            Path dest = Paths.get(UPLOAD_DIR + destName);
            Files.copy(selected.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
            String picPath = dest.toString();
            userService.updateProfilePicture(currentUser.getId(), picPath);
            currentUser.setProfilePicture(picPath);
            showImageInAvatar(new Image(dest.toUri().toString(), 150, 150, true, true));
            if (lblGravatarInfo != null) lblGravatarInfo.setText("Custom photo");
            showMsg("Profile picture updated!", false);
        } catch (IOException | SQLException e) {
            showMsg("Upload failed: " + e.getMessage(), true);
        }
    }

    // ── Remove ────────────────────────────────────────────────────────────────

    @FXML
    private void onRemovePicture() {
        try {
            String pic = currentUser.getProfilePicture();
            if (pic != null) { File f = new File(pic); if (f.exists()) f.delete(); }
            userService.updateProfilePicture(currentUser.getId(), null);
            currentUser.setProfilePicture(null);
            if (imgAvatar    != null) { imgAvatar.setVisible(false);   imgAvatar.setManaged(false); }
            if (paneInitials != null) { paneInitials.setVisible(true); paneInitials.setManaged(true); }
            if (lblGravatarInfo != null) lblGravatarInfo.setText("No photo uploaded");
            showMsg("Profile picture removed.", false);
        } catch (SQLException e) {
            showMsg("Error: " + e.getMessage(), true);
        }
    }

    // ── Favorites ─────────────────────────────────────────────────────────────

    private void loadFavorites() {
        if (favoritesPane == null) return;
        favoritesPane.getChildren().clear();
        try {
            List<Game> favs = favService.getFavorites(currentUser.getId());
            if (lblFavCount != null)
                lblFavCount.setText(favs.size() + " game" + (favs.size() == 1 ? "" : "s"));
            if (favs.isEmpty()) {
                if (lblFavEmpty != null) { lblFavEmpty.setVisible(true); lblFavEmpty.setManaged(true); }
                return;
            }
            if (lblFavEmpty != null) { lblFavEmpty.setVisible(false); lblFavEmpty.setManaged(false); }
            for (Game g : favs) {
                VBox card = buildFavoriteCard(g);
                card.setOpacity(0);
                favoritesPane.getChildren().add(card);
                FadeTransition ft = new FadeTransition(Duration.millis(300), card);
                ft.setToValue(1); ft.play();
            }
        } catch (Exception e) {
            System.err.println("Could not load favorites: " + e.getMessage());
        }
    }

    private VBox buildFavoriteCard(Game game) {
        boolean isMini = "MINI_GAME".equals(game.getCategory());
        StackPane iconCircle = utils.TwemojiUtil.circle(
            typeEmoji(game.getType()), 64, typeGradient(game.getType()), 38);
        iconCircle.setMaxSize(64, 64);
        Label title = new Label(game.getName());
        title.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;");
        title.setWrapText(true); title.setMaxWidth(180); title.setAlignment(Pos.CENTER);
        HBox badges = new HBox(6,
            badge(game.getType(), typeBadgeBg(game.getType()), typeBadgeFg(game.getType())),
            badge(game.getDifficulty(), diffBg(game.getDifficulty()), diffFg(game.getDifficulty())));
        badges.setAlignment(Pos.CENTER);
        HBox rewardRow;
        if (isMini) {
            int ep = game.getEnergyPoints() != null ? game.getEnergyPoints() : 0;
            Label e = new Label("+" + ep + " Energy");
            e.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#27ae60;");
            rewardRow = new HBox(e); rewardRow.setAlignment(Pos.CENTER);
        } else {
            Label tok = new Label("+" + game.getRewardTokens() + " tokens");
            tok.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#b7791f;");
            Label xp = new Label("  +" + game.getRewardXP() + " XP");
            xp.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#2b6cb0;");
            rewardRow = new HBox(tok, xp); rewardRow.setAlignment(Pos.CENTER);
        }
        Separator sep = new Separator();
        Button btnPlay = new Button("Play Now");
        btnPlay.setMaxWidth(Double.MAX_VALUE);
        btnPlay.setStyle("-fx-background-color:" + typeGradient(game.getType()) + ";-fx-text-fill:white;" +
                         "-fx-font-weight:bold;-fx-font-size:13px;-fx-background-radius:8;-fx-padding:9 0;-fx-cursor:hand;");
        btnPlay.setOnAction(e -> launchGame(game));
        Button btnRemove = new Button("Remove from Favorites");
        btnRemove.setMaxWidth(Double.MAX_VALUE);
        btnRemove.setStyle("-fx-background-color:#fff5f5;-fx-text-fill:#e53e3e;-fx-font-size:12px;" +
                           "-fx-font-weight:bold;-fx-background-radius:8;-fx-padding:7 0;-fx-cursor:hand;" +
                           "-fx-border-color:#fed7d7;-fx-border-radius:8;-fx-border-width:1;");
        btnRemove.setOnMouseEntered(e -> btnRemove.setStyle(
            "-fx-background-color:#e53e3e;-fx-text-fill:white;-fx-font-size:12px;" +
            "-fx-font-weight:bold;-fx-background-radius:8;-fx-padding:7 0;-fx-cursor:hand;"));
        btnRemove.setOnMouseExited(e -> btnRemove.setStyle(
            "-fx-background-color:#fff5f5;-fx-text-fill:#e53e3e;-fx-font-size:12px;" +
            "-fx-font-weight:bold;-fx-background-radius:8;-fx-padding:7 0;-fx-cursor:hand;" +
            "-fx-border-color:#fed7d7;-fx-border-radius:8;-fx-border-width:1;"));
        btnRemove.setOnAction(e -> {
            try {
                favService.removeFavorite(currentUser.getId(), game.getId());
                ScaleTransition st = new ScaleTransition(Duration.millis(200), btnRemove.getParent());
                st.setToX(0.9); st.setToY(0.9);
                FadeTransition ft = new FadeTransition(Duration.millis(200), btnRemove.getParent());
                ft.setToValue(0);
                ParallelTransition pt = new ParallelTransition(st, ft);
                pt.setOnFinished(ev -> loadFavorites());
                pt.play();
            } catch (Exception ex) { System.err.println("Remove favorite: " + ex.getMessage()); }
        });
        VBox card = new VBox(10, iconCircle, title, badges, rewardRow, sep, btnPlay, btnRemove);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(18, 14, 18, 14));
        card.setPrefWidth(210);
        card.setStyle("-fx-background-color:white;-fx-background-radius:14;" +
                      "-fx-border-color:#e4e8f0;-fx-border-radius:14;-fx-border-width:1;" +
                      "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.07),10,0,0,3);");
        card.setOnMouseEntered(e -> card.setStyle(card.getStyle()
            .replace("rgba(0,0,0,0.07)", "rgba(59,79,216,0.15)").replace("#e4e8f0", "#c3c9f5")));
        card.setOnMouseExited(e -> card.setStyle(card.getStyle()
            .replace("rgba(59,79,216,0.15)", "rgba(0,0,0,0.07)").replace("#c3c9f5", "#e4e8f0")));
        return card;
    }

    private String typeEmoji(String t) {
        return switch(t) {
            case "PUZZLE" -> utils.TwemojiUtil.PUZZLE;
            case "MEMORY" -> utils.TwemojiUtil.MEMORY;
            case "TRIVIA" -> utils.TwemojiUtil.TRIVIA;
            case "ARCADE" -> utils.TwemojiUtil.ARCADE;
            default       -> utils.TwemojiUtil.GAMEPAD;
        };
    }

    private String typeGradient(String t) {
        return switch(t) {
            case "PUZZLE" -> "linear-gradient(to bottom right,#f6d365,#fda085)";
            case "MEMORY" -> "linear-gradient(to bottom right,#a18cd1,#fbc2eb)";
            case "TRIVIA" -> "linear-gradient(to bottom right,#4facfe,#00f2fe)";
            case "ARCADE" -> "linear-gradient(to bottom right,#43e97b,#38f9d7)";
            default       -> "linear-gradient(to bottom right,#667eea,#764ba2)";
        };
    }

    private void launchGame(models.gamification.Game game) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/views/gamification/game_play.fxml"));
            javafx.scene.Parent view = loader.load();
            controllers.gamification.GamePlayController ctrl = loader.getController();
            ctrl.setGame(game);
            ctrl.setContentArea(null);
            controllers.NovaDashboardController.setView(view);
        } catch (Exception e) {
            System.err.println("Cannot launch game: " + e.getMessage());
        }
    }

    // ── Change password ───────────────────────────────────────────────────────

    @FXML
    private void onChangePassword() {
        hideMsg();
        String current = pfCurrent.getText();
        String newPwd  = pfNew.getText();
        String confirm = pfConfirm.getText();
        if (current.isBlank()) { showMsg("Current password is required.", true); return; }
        String storedHash = currentUser.getPassword();
        boolean currentOk;
        if (storedHash != null && storedHash.startsWith("$2")) {
            String hash = storedHash.replaceFirst("^\\$2y\\$", "\\$2a\\$");
            try   { currentOk = BCrypt.checkpw(current, hash); }
            catch (Exception e) { currentOk = false; }
        } else { currentOk = current.equals(storedHash); }
        if (!currentOk) { showMsg("Current password is incorrect.", true); return; }
        List<String> errors = ValidationUtil.validateUser(
            currentUser.getEmail(), currentUser.getUsername(),
            newPwd, currentUser.getRole().name(), true);
        if (!errors.isEmpty()) { showMsg(errors.get(0), true); return; }
        if (!newPwd.equals(confirm)) { showMsg("New passwords do not match.", true); return; }
        try {
            currentUser.setPassword(BCrypt.hashpw(newPwd, BCrypt.gensalt(13)));
            userService.updateUser(currentUser);
            pfCurrent.clear(); pfNew.clear(); pfConfirm.clear();
            showMsg("Password updated successfully.", false);
        } catch (SQLException e) { showMsg("Database error: " + e.getMessage(), true); }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @FXML
    private void onLogout() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/users/login.fxml"));
            Parent root = loader.load();
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 1100, 720);
            scene.getStylesheets().add(getClass().getResource("/css/login.css").toExternalForm());
            Stage stage = (Stage) lblFullName.getScene().getWindow();
            stage.setTitle("NOVA - Sign In");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.centerOnScreen();
        } catch (Exception e) { showMsg("Logout error: " + e.getMessage(), true); }
    }

    // ── Back ──────────────────────────────────────────────────────────────────

    @FXML
    private void onBackToList() {
        try {
            if (previousView != null) {
                controllers.NovaDashboardController.setView(previousView);
            } else if (mainLayout != null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/users/user-list.fxml"));
                Parent root = loader.load();
                UserListController ctrl = loader.getController();
                ctrl.setCurrentUser(currentUser);
                mainLayout.setCenter(root);
            } else {
                controllers.NovaDashboardController.setView(
                    FXMLLoader.load(getClass().getResource("/views/studysession/UserStudyDashboard.fxml")));
            }
        } catch (Exception e) { showMsg("Navigation error: " + e.getMessage(), true); }
    }

    // ── Manage 2FA ────────────────────────────────────────────────────────────

    @FXML
    private void onManage2FA() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/views/users/2fa-setup.fxml"));
            javafx.scene.Parent root = loader.load();
            controllers.users.TwoFactorSetupController ctrl = loader.getController();
            ctrl.setCurrentUser(currentUser);
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 480, 560);
            scene.getStylesheets().add(getClass().getResource("/css/users.css").toExternalForm());
            javafx.stage.Stage dialog = new javafx.stage.Stage();
            dialog.setTitle("Two-Factor Authentication");
            dialog.setScene(scene);
            dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialog.setResizable(false);
            dialog.showAndWait();
        } catch (Exception e) {
            showMsg("2FA error: " + e.getMessage(), true);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showMsg(String msg, boolean isError) {
        lblPwdMsg.setText(msg);
        lblPwdMsg.setStyle(isError
            ? "-fx-text-fill:#dc2626;-fx-background-color:#fef2f2;-fx-padding:10 14;-fx-background-radius:8;"
            : "-fx-text-fill:#059669;-fx-background-color:#f0fdf4;-fx-padding:10 14;-fx-background-radius:8;");
        lblPwdMsg.setVisible(true);
        lblPwdMsg.setManaged(true);
    }

    private void hideMsg() {
        lblPwdMsg.setVisible(false);
        lblPwdMsg.setManaged(false);
    }

    private Label badge(String text, String bg, String fg) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color:" + bg + ";-fx-text-fill:" + fg
                + ";-fx-background-radius:20;-fx-padding:3 10;-fx-font-size:10px;-fx-font-weight:bold;");
        return l;
    }

    private String typeBadgeBg(String t) { return switch(t){case "PUZZLE"->"#fff8e1";case "MEMORY"->"#f3e5f5";case "TRIVIA"->"#e3f2fd";case "ARCADE"->"#e8f5e9";default->"#eef0fd";}; }
    private String typeBadgeFg(String t) { return switch(t){case "PUZZLE"->"#b7791f";case "MEMORY"->"#805ad5";case "TRIVIA"->"#2b6cb0";case "ARCADE"->"#276749";default->"#3b4fd8";}; }
    private String diffBg(String d)      { return switch(d){case "HARD"->"#fff5f5";case "MEDIUM"->"#fffbeb";default->"#f0fff4";}; }
    private String diffFg(String d)      { return switch(d){case "HARD"->"#e53e3e";case "MEDIUM"->"#d97706";default->"#27ae60";}; }

    // ── Open Edit Profile page ────────────────────────────────────────────────
    @FXML
    private void onOpenEditProfile() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/views/users/edit-profile.fxml"));
            javafx.scene.Parent root = loader.load();
            EditProfileController ctrl = loader.getController();
            ctrl.setCurrentUser(currentUser, () -> {
                // Refresh profile after save
                javafx.application.Platform.runLater(() -> {
                    try {
                        javafx.fxml.FXMLLoader pl = new javafx.fxml.FXMLLoader(
                            getClass().getResource("/views/users/profile.fxml"));
                        javafx.scene.Parent pv = pl.load();
                        ProfileController pc = pl.getController();
                        pc.setCurrentUser(currentUser, mainLayout, previousView);
                        controllers.NovaDashboardController.setView(pv);
                    } catch (Exception ex) { ex.printStackTrace(); }
                });
            });
            controllers.NovaDashboardController.setView(root);
        } catch (Exception e) {
            showMsg("Cannot open edit profile: " + e.getMessage(), true);
        }
    }
}