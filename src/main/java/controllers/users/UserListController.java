package controllers.users;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.users.User;
import services.users.UserService;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

public class UserListController implements Initializable {

    // ── Sidebar ────────────────────────────────────────────────────────────────
    @FXML private Label lblCurrentUser;
    @FXML private Label lblCurrentRole;

    // ── Table ──────────────────────────────────────────────────────────────────
    @FXML private TableView<User> tableUsers;
    @FXML private TableColumn<User, Integer> colId;
    @FXML private TableColumn<User, String>  colUsername;
    @FXML private TableColumn<User, String>  colEmail;
    @FXML private TableColumn<User, String>  colRole;
    @FXML private TableColumn<User, String>  colStatus;
    @FXML private TableColumn<User, Integer> colXp;
    @FXML private TableColumn<User, Void>    colActions;

    // ── Top bar ────────────────────────────────────────────────────────────────
    @FXML private TextField        tfSearch;
    @FXML private ComboBox<String> cbRoleFilter;

    // ── Stat cards ─────────────────────────────────────────────────────────────
    @FXML private Label lblTotal;
    @FXML private Label lblActive;
    @FXML private Label lblBanned;
    @FXML private Label lblVerified;
    @FXML private Label lblAdmins;
    @FXML private Label lblStudents;
    @FXML private Label lblTutors;

    private final UserService userService = new UserService();
    private final ObservableList<User> userList = FXCollections.observableArrayList();

    // ── Called by LoginController after successful login ───────────────────────
    public void setCurrentUser(User user) {
        if (lblCurrentUser != null)
            lblCurrentUser.setText(user.getUsername());
        if (lblCurrentRole != null)
            lblCurrentRole.setText(user.getRole().name());
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupColumns();
        setupRoleFilter();
        loadUsers();
        refreshStats();
    }

    // ── Column setup ──────────────────────────────────────────────────────────

    private void setupColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colXp.setCellValueFactory(new PropertyValueFactory<>("xp"));

        colRole.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getRole().name().replace("ROLE_", "")));

        // Status cell with color badge
        colStatus.setCellValueFactory(c -> {
            User u = c.getValue();
            String s = u.isBanned() ? "Banned" : (u.isActive() ? "Active" : "Inactive");
            return new SimpleStringProperty(s);
        });

        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "Active"   -> "-fx-text-fill:#059669; -fx-font-weight:bold;";
                    case "Banned"   -> "-fx-text-fill:#dc2626; -fx-font-weight:bold;";
                    default         -> "-fx-text-fill:#9ca3af;";
                });
            }
        });

        // Actions: Edit + Delete buttons using CSS classes
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit   = new Button("Edit");
            private final Button btnDelete = new Button("Delete");
            private final HBox   box       = new HBox(6, btnEdit, btnDelete);

            {
                btnEdit.getStyleClass().add("btn-edit");
                btnDelete.getStyleClass().add("btn-danger");
                btnEdit.setOnAction(e   -> openForm(getTableView().getItems().get(getIndex())));
                btnDelete.setOnAction(e -> deleteUser(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        tableUsers.setItems(userList);
    }

    // ── Role filter ───────────────────────────────────────────────────────────

    private void setupRoleFilter() {
        cbRoleFilter.setItems(FXCollections.observableArrayList(
            "All Roles", "ROLE_ADMIN", "ROLE_STUDENT", "ROLE_TUTOR", "ROLE_USER"
        ));
        cbRoleFilter.setValue("All Roles");
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadUsers() {
        try {
            userList.setAll(userService.getAllUsers());
        } catch (SQLException e) {
            showError("Database error", e.getMessage());
        }
    }

    private void refreshStats() {
        try {
            Map<String, Integer> summary = userService.getSummaryStats();
            lblTotal.setText(String.valueOf(summary.getOrDefault("total",    0)));
            lblActive.setText(String.valueOf(summary.getOrDefault("active",   0)));
            lblBanned.setText(String.valueOf(summary.getOrDefault("banned",   0)));
            lblVerified.setText(String.valueOf(summary.getOrDefault("verified", 0)));

            Map<String, Integer> roles = userService.getRoleStats();
            lblAdmins.setText(String.valueOf(roles.getOrDefault("ROLE_ADMIN",   0)));
            lblStudents.setText(String.valueOf(roles.getOrDefault("ROLE_STUDENT", 0)));
            lblTutors.setText(String.valueOf(roles.getOrDefault("ROLE_TUTOR",   0)));
        } catch (SQLException e) {
            showError("Stats error", e.getMessage());
        }
    }

    // ── Toolbar actions ───────────────────────────────────────────────────────

    @FXML
    private void onSearch() {
        String keyword = tfSearch.getText().trim();
        try {
            userList.setAll(keyword.isEmpty()
                ? userService.getAllUsers()
                : userService.searchUsers(keyword));
        } catch (SQLException e) {
            showError("Search error", e.getMessage());
        }
    }

    @FXML
    private void onFilter() {
        String selected = cbRoleFilter.getValue();
        try {
            if (selected == null || selected.equals("All Roles")) {
                userList.setAll(userService.getAllUsers());
            } else {
                userList.setAll(userService.filterByRole(User.Role.valueOf(selected)));
            }
        } catch (SQLException e) {
            showError("Filter error", e.getMessage());
        }
    }

    @FXML
    private void onAddUser() { openForm(null); }

    @FXML
    private void onRefresh() {
        tfSearch.clear();
        cbRoleFilter.setValue("All Roles");
        loadUsers();
        refreshStats();
    }

    // ── Open Add/Edit form ────────────────────────────────────────────────────

    private void openForm(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/views/users/user-form.fxml"));
            Parent root = loader.load();

            UserFormController ctrl = loader.getController();
            ctrl.setUser(user);
            ctrl.setOnSaved(() -> { loadUsers(); refreshStats(); });

            Stage stage = new Stage();
            stage.setTitle(user == null ? "Add User" : "Edit — " + user.getUsername());
            Scene scene = new Scene(root, 700, 480);
            scene.getStylesheets().add(
                getClass().getResource("/css/users.css").toExternalForm());
            stage.setScene(scene);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setResizable(false);
            stage.showAndWait();
        } catch (IOException e) {
            showError("Cannot open form", e.getMessage());
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    private void deleteUser(User user) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete user \"" + user.getUsername() + "\"?\nThis action cannot be undone.",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText(null);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                userService.deleteUser(user.getId());
                loadUsers();
                refreshStats();
            } catch (SQLException e) {
                showError("Delete error", e.getMessage());
            }
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private void showError(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
