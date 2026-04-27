package controllers.users;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import models.gamification.Game;
import models.users.User;
import org.mindrot.jbcrypt.BCrypt;
import services.gamification.FavoriteGameService;
import services.users.UserService;
import services.users.ValidationUtil;

import java.net.URL;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

public class ProfileController implements Initializable {

    // ── Profile header ────────────────────────────────────────────────────────
    @FXML private Label lblInitials, lblFullName, lblRoleBadge, lblEmail;
    @FXML private Label lblStatus, lblVerified, lblXp, lblXpLabel, lblCreatedAt;

    // ── Password change ───────────────────────────────────────────────────────
    @FXML private PasswordField pfCurrent, pfNew, pfConfirm;
    @FXML private Label lblPwdMsg;

    // ── Tabs ──────────────────────────────────────────────────────────────────
    @FXML private TabPane profileTabs;

    // ── Favorites tab ─────────────────────────────────────────────────────────
    @FXML private FlowPane favoritesPane;
    @FXML private Label lblFavCount;
    @FXML private VBox lblFavEmpty;

    private User currentUser;
    private BorderPane mainLayout;
    private final UserService userService = new UserService();
    private final FavoriteGameService favService = new FavoriteGameService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (lblPwdMsg != null) { lblPwdMsg.setVisible(false); lblPwdMsg.setManaged(false); }
    }

    public void setCurrentUser(User user, BorderPane layout) {
        this.currentUser = user;
        this.mainLayout  = layout;
        populateProfile();

        boolean isTutor = user.getRole() == User.Role.ROLE_TUTOR;

        // Hide XP row for tutors
        if (isTutor && lblXp != null) {
            lblXp.setVisible(false); lblXp.setManaged(false);
            if (lblXpLabel != null) { lblXpLabel.setVisible(false); lblXpLabel.setManaged(false); }
        }

        // Load favorites only for students; hide the tab for tutors
        if (isTutor) {
            if (profileTabs != null) {
                profileTabs.getTabs().removeIf(t -> "Favorite Games".equals(t.getText()));
            }
        } else {
            loadFavorites();
        }
    }

    // ── Profile header ────────────────────────────────────────────────────────
    private void populateProfile() {
        String username = currentUser.getUsername();
        lblInitials.setText(username.length() >= 2 ? username.substring(0, 2).toUpperCase() : username.toUpperCase());
        lblFullName.setText(username);
        lblEmail.setText(currentUser.getEmail());
        lblRoleBadge.setText(currentUser.getRole().name().replace("ROLE_", ""));
        lblXp.setText(String.valueOf(currentUser.getXp()));
        lblStatus.setText(currentUser.isBanned() ? "Banned" : currentUser.isActive() ? "Active" : "Inactive");
        lblVerified.setText(currentUser.isVerified() ? "Yes" : "No");
        if (currentUser.getCreatedAt() != null)
            lblCreatedAt.setText(currentUser.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
        else lblCreatedAt.setText("-");
    }

    // ── Favorites ─────────────────────────────────────────────────────────────
    private void loadFavorites() {
        if (favoritesPane == null) return;
        favoritesPane.getChildren().clear();
        try {
            List<Game> favs = favService.getFavorites(currentUser.getId());
            if (lblFavCount != null) lblFavCount.setText(favs.size() + " game" + (favs.size() == 1 ? "" : "s"));
            if (favs.isEmpty()) {
                if (lblFavEmpty != null) { lblFavEmpty.setVisible(true); lblFavEmpty.setManaged(true); }
                return;
            }
            if (lblFavEmpty != null) { lblFavEmpty.setVisible(false); lblFavEmpty.setManaged(false); }
            for (Game g : favs) {
                VBox card = buildFavoriteCard(g);
                // Fade-in animation
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

        // Icon circle
        Label iconLbl = new Label(typeIcon(game.getType()));
        iconLbl.setStyle("-fx-font-family:'Font Awesome 6 Free Solid';-fx-font-size:22px;-fx-text-fill:white;");
        StackPane iconCircle = new StackPane(iconLbl);
        iconCircle.setPrefSize(56, 56); iconCircle.setMaxSize(56, 56);
        iconCircle.setStyle("-fx-background-color:" + typeGradient(game.getType()) + ";-fx-background-radius:50;");

        // Title
        Label title = new Label(game.getName());
        title.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;");
        title.setWrapText(true); title.setMaxWidth(170);
        title.setAlignment(Pos.CENTER);

        // Badges
        Label typeBadge = badge(game.getType(), typeBadgeBg(game.getType()), typeBadgeFg(game.getType()));
        Label diffBadge = badge(game.getDifficulty(), diffBg(game.getDifficulty()), diffFg(game.getDifficulty()));
        HBox badges = new HBox(6, typeBadge, diffBadge); badges.setAlignment(Pos.CENTER);

        // Reward info
        Label rewardLbl;
        if (isMini) {
            int ep = game.getEnergyPoints() != null ? game.getEnergyPoints() : 0;
            rewardLbl = new Label("+" + ep + " Energy");
            rewardLbl.setStyle("-fx-text-fill:#27ae60;-fx-font-size:12px;-fx-font-weight:bold;");
        } else {
            rewardLbl = new Label("+" + game.getRewardTokens() + " tokens  +" + game.getRewardXP() + " XP");
            rewardLbl.setStyle("-fx-text-fill:#3b4fd8;-fx-font-size:12px;-fx-font-weight:bold;");
        }

        // Remove from favorites button — use Label graphic so FA font applies correctly
        Label heartIco = new Label("\uF004");
        heartIco.setStyle("-fx-font-family:'Font Awesome 6 Free Solid';-fx-font-size:12px;-fx-text-fill:#e53e3e;");
        Label unfavTxt = new Label("  Unfavorite");
        unfavTxt.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#e53e3e;");
        HBox btnContent = new HBox(2, heartIco, unfavTxt);
        btnContent.setAlignment(Pos.CENTER);

        Button btnRemove = new Button();
        btnRemove.setGraphic(btnContent);
        btnRemove.setMaxWidth(Double.MAX_VALUE);
        btnRemove.setStyle("-fx-background-color:#fff5f5;-fx-background-radius:8;-fx-padding:7 0;-fx-cursor:hand;-fx-border-color:#fed7d7;-fx-border-radius:8;");
        btnRemove.setOnMouseEntered(e -> {
            btnRemove.setStyle("-fx-background-color:#e53e3e;-fx-background-radius:8;-fx-padding:7 0;-fx-cursor:hand;");
            heartIco.setStyle("-fx-font-family:'Font Awesome 6 Free Solid';-fx-font-size:12px;-fx-text-fill:white;");
            unfavTxt.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:white;");
        });
        btnRemove.setOnMouseExited(e -> {
            btnRemove.setStyle("-fx-background-color:#fff5f5;-fx-background-radius:8;-fx-padding:7 0;-fx-cursor:hand;-fx-border-color:#fed7d7;-fx-border-radius:8;");
            heartIco.setStyle("-fx-font-family:'Font Awesome 6 Free Solid';-fx-font-size:12px;-fx-text-fill:#e53e3e;");
            unfavTxt.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#e53e3e;");
        });
        btnRemove.setOnAction(e -> {
            try {
                favService.removeFavorite(currentUser.getId(), game.getId());
                // Slide-out animation
                ScaleTransition st = new ScaleTransition(Duration.millis(200), btnRemove.getParent());
                st.setToX(0.8); st.setToY(0.8);
                FadeTransition ft = new FadeTransition(Duration.millis(200), btnRemove.getParent());
                ft.setToValue(0);
                ParallelTransition pt = new ParallelTransition(st, ft);
                pt.setOnFinished(ev -> loadFavorites());
                pt.play();
            } catch (Exception ex) { System.err.println("Remove favorite error: " + ex.getMessage()); }
        });

        VBox card = new VBox(10, iconCircle, title, badges, rewardLbl, btnRemove);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(18, 14, 18, 14));
        card.setPrefWidth(210);
        card.setStyle("-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#e4e8f0;-fx-border-radius:14;-fx-border-width:1;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.07),10,0,0,3);");
        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#c3c9f5;-fx-border-radius:14;-fx-border-width:1;-fx-effect:dropshadow(gaussian,rgba(59,79,216,0.15),14,0,0,5);"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#e4e8f0;-fx-border-radius:14;-fx-border-width:1;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.07),10,0,0,3);"));
        return card;
    }

    // ── Password change ───────────────────────────────────────────────────────
    @FXML
    private void onChangePassword() {
        hideMsg();
        String current = pfCurrent.getText(), newPwd = pfNew.getText(), confirm = pfConfirm.getText();
        if (current.isBlank()) { showMsg("Current password is required.", true); return; }
        String storedHash = currentUser.getPassword();
        boolean currentOk;
        if (storedHash != null && storedHash.startsWith("$2")) {
            String hash = storedHash.replaceFirst("^\\$2y\\$", "\\$2a\\$");
            try { currentOk = BCrypt.checkpw(current, hash); } catch (Exception e) { currentOk = false; }
        } else { currentOk = current.equals(storedHash); }
        if (!currentOk) { showMsg("Current password is incorrect.", true); return; }
        List<String> errors = ValidationUtil.validateUser(currentUser.getEmail(), currentUser.getUsername(), newPwd, currentUser.getRole().name(), true);
        if (!errors.isEmpty()) { showMsg(errors.get(0), true); return; }
        if (!newPwd.equals(confirm)) { showMsg("New passwords do not match.", true); return; }
        try {
            currentUser.setPassword(BCrypt.hashpw(newPwd, BCrypt.gensalt(13)));
            userService.updateUser(currentUser);
            pfCurrent.clear(); pfNew.clear(); pfConfirm.clear();
            showMsg("Password updated successfully.", false);
        } catch (SQLException e) { showMsg("Database error: " + e.getMessage(), true); }
    }

    @FXML
    private void onLogout() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/users/login.fxml"));
            Parent root = loader.load();
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 900, 580);
            scene.getStylesheets().add(getClass().getResource("/css/login.css").toExternalForm());
            javafx.stage.Stage stage = (javafx.stage.Stage) lblFullName.getScene().getWindow();
            stage.setTitle("NOVA - Sign In"); stage.setScene(scene);
            stage.setResizable(false); stage.centerOnScreen();
        } catch (Exception e) { showMsg("Logout error: " + e.getMessage(), true); }
    }

    @FXML
    private void onBackToList() {
        try {
            if (mainLayout != null) {
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

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void showMsg(String msg, boolean isError) {
        lblPwdMsg.setText(msg);
        lblPwdMsg.setStyle(isError
            ? "-fx-text-fill:#dc2626;-fx-background-color:#fef2f2;-fx-padding:10 14;-fx-background-radius:8;"
            : "-fx-text-fill:#059669;-fx-background-color:#f0fdf4;-fx-padding:10 14;-fx-background-radius:8;");
        lblPwdMsg.setVisible(true); lblPwdMsg.setManaged(true);
    }
    private void hideMsg() { lblPwdMsg.setVisible(false); lblPwdMsg.setManaged(false); }

    private Label badge(String text, String bg, String fg) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color:" + bg + ";-fx-text-fill:" + fg + ";-fx-background-radius:20;-fx-padding:3 10;-fx-font-size:10px;-fx-font-weight:bold;");
        return l;
    }
    private String typeIcon(String t) { return switch(t){case "PUZZLE"->"\uF12E";case "MEMORY"->"\uF5DC";case "TRIVIA"->"\uF059";case "ARCADE"->"\uF11B";default->"\uF11B";}; }
    private String typeGradient(String t) { return switch(t){case "PUZZLE"->"linear-gradient(to bottom right,#f6d365,#fda085)";case "MEMORY"->"linear-gradient(to bottom right,#a18cd1,#fbc2eb)";case "TRIVIA"->"linear-gradient(to bottom right,#4facfe,#00f2fe)";case "ARCADE"->"linear-gradient(to bottom right,#43e97b,#38f9d7)";default->"linear-gradient(to bottom right,#667eea,#764ba2)";}; }
    private String typeBadgeBg(String t) { return switch(t){case "PUZZLE"->"#fff8e1";case "MEMORY"->"#f3e5f5";case "TRIVIA"->"#e3f2fd";case "ARCADE"->"#e8f5e9";default->"#eef0fd";}; }
    private String typeBadgeFg(String t) { return switch(t){case "PUZZLE"->"#b7791f";case "MEMORY"->"#805ad5";case "TRIVIA"->"#2b6cb0";case "ARCADE"->"#276749";default->"#3b4fd8";}; }
    private String diffBg(String d) { return switch(d){case "HARD"->"#fff5f5";case "MEDIUM"->"#fffbeb";default->"#f0fff4";}; }
    private String diffFg(String d) { return switch(d){case "HARD"->"#e53e3e";case "MEDIUM"->"#d97706";default->"#27ae60";}; }
}
