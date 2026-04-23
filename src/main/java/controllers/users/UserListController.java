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
import javafx.scene.layout.BorderPane;
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

    @FXML private TableView<User>            tableUsers;
    @FXML private TableColumn<User, String>  colUsername;
    @FXML private TableColumn<User, String>  colEmail;
    @FXML private TableColumn<User, String>  colRole;
    @FXML private TableColumn<User, String>  colStatus;
    @FXML private TableColumn<User, Integer> colXp;
    @FXML private TableColumn<User, Void>    colActions;

    @FXML private TextField        tfSearch;
    @FXML private ComboBox<String> cbRoleFilter;

    @FXML private Label lblTotal;
    @FXML private Label lblActive;
    @FXML private Label lblBanned;
    @FXML private Label lblVerified;
    @FXML private Label lblAdmins;
    @FXML private Label lblStudents;
    @FXML private Label lblTutors;

    private User currentUser;
    private final UserService userService = new UserService();
    private final ObservableList<User> userList = FXCollections.observableArrayList();

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupColumns();
        setupRoleFilter();
        loadUsers();
        refreshStats();
    }

    private void setupColumns() {
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colXp.setCellValueFactory(new PropertyValueFactory<>("xp"));

        colRole.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getRole().name().replace("ROLE_", "")));

        colStatus.setCellValueFactory(c -> {
            User u = c.getValue();
            return new SimpleStringProperty(u.isBanned() ? "Banned" : u.isActive() ? "Active" : "Inactive");
        });

        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "Active"  -> "-fx-text-fill:#059669;-fx-font-weight:bold;";
                    case "Banned"  -> "-fx-text-fill:#dc2626;-fx-font-weight:bold;";
                    default        -> "-fx-text-fill:#9ca3af;";
                });
            }
        });

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

    private void setupRoleFilter() {
        cbRoleFilter.setItems(FXCollections.observableArrayList(
            "All Roles", "ROLE_ADMIN", "ROLE_STUDENT", "ROLE_TUTOR", "ROLE_USER"));
        cbRoleFilter.setValue("All Roles");
    }

    private void loadUsers() {
        try { userList.setAll(userService.getAllUsers()); }
        catch (SQLException e) { showError("Database error", e.getMessage()); }
    }

    private void refreshStats() {
        try {
            Map<String, Integer> s = userService.getSummaryStats();
            lblTotal.setText(String.valueOf(s.getOrDefault("total",    0)));
            lblActive.setText(String.valueOf(s.getOrDefault("active",   0)));
            lblBanned.setText(String.valueOf(s.getOrDefault("banned",   0)));
            lblVerified.setText(String.valueOf(s.getOrDefault("verified", 0)));
            Map<String, Integer> r = userService.getRoleStats();
            lblAdmins.setText(String.valueOf(r.getOrDefault("ROLE_ADMIN",   0)));
            lblStudents.setText(String.valueOf(r.getOrDefault("ROLE_STUDENT", 0)));
            lblTutors.setText(String.valueOf(r.getOrDefault("ROLE_TUTOR",   0)));
        } catch (SQLException e) { showError("Stats error", e.getMessage()); }
    }

    @FXML private void onSearch() {
        String kw = tfSearch.getText().trim();
        try { userList.setAll(kw.isEmpty() ? userService.getAllUsers() : userService.searchUsers(kw)); }
        catch (SQLException e) { showError("Search error", e.getMessage()); }
    }

    @FXML private void onFilter() {
        String sel = cbRoleFilter.getValue();
        try {
            userList.setAll((sel == null || sel.equals("All Roles"))
                ? userService.getAllUsers()
                : userService.filterByRole(User.Role.valueOf(sel)));
        } catch (SQLException e) { showError("Filter error", e.getMessage()); }
    }

    @FXML private void onAddUser()  { openForm(null); }

    @FXML private void onRefresh() {
        tfSearch.clear();
        cbRoleFilter.setValue("All Roles");
        loadUsers();
        refreshStats();
    }

    private void openForm(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/views/users/user-form.fxml"));
            Parent root = loader.load();
            UserFormController ctrl = loader.getController();
            ctrl.setUser(user);
            ctrl.setOnSaved(() -> { loadUsers(); refreshStats(); });
            Stage stage = new Stage();
            stage.setTitle(user == null ? "Add User" : "Edit - " + user.getUsername());
            Scene scene = new Scene(root, 700, 480);
            scene.getStylesheets().add(getClass().getResource("/css/users.css").toExternalForm());
            stage.setScene(scene);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setResizable(false);
            stage.showAndWait();
        } catch (IOException e) { showError("Cannot open form", e.getMessage()); }
    }

    private void deleteUser(User user) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete user \"" + user.getUsername() + "\"?\nThis cannot be undone.",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText(null);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try { userService.deleteUser(user.getId()); loadUsers(); refreshStats(); }
            catch (SQLException e) { showError("Delete error", e.getMessage()); }
        }
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setTitle(title); a.setHeaderText(null); a.showAndWait();
    }
}
