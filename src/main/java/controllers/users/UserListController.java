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
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.beans.property.SimpleBooleanProperty;
import java.util.stream.Collectors;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.users.User;
import services.users.UserService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javafx.stage.FileChooser;
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
    @FXML private TableColumn<User, Boolean> colSelect;
    @FXML private javafx.scene.layout.HBox   bulkBar;
    @FXML private Label                       lblSelectedCount;
    private final java.util.Map<Integer, SimpleBooleanProperty> selectedMap = new java.util.HashMap<>();

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
        // Show flag emoji + username
        colUsername.setCellValueFactory(c -> {
            User u = c.getValue();
            String cc = u.getCountryCode();
            String display = (cc != null && !cc.isBlank())
                ? "[" + cc.toUpperCase() + "] " + u.getUsername()
                : u.getUsername();
            return new SimpleStringProperty(display);
        });
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
        selectedMap.clear();
        updateBulkBar();
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

    /** Converts ISO-2 country code to flag emoji */
    private String countryCodeToFlag(String code) {
        if (code == null || code.length() != 2) return "";
        try {
            int base = 0x1F1E6 - 'A';
            return new String(Character.toChars(base + Character.toUpperCase(code.charAt(0))))
                 + new String(Character.toChars(base + Character.toUpperCase(code.charAt(1))));
        } catch (Exception e) { return ""; }
    }

    // ── Export Users to CSV ───────────────────────────────────────────────────
    @FXML
    private void onExportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Users to CSV");
        chooser.setInitialFileName("nova_users_" +
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        Stage stage = (Stage) tableUsers.getScene().getWindow();
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            pw.print('\uFEFF'); // BOM for Excel
            final String S = ";"; // semicolon works in all Excel locales
            // Title block
            pw.println("NOVA Platform - User Export");
            pw.println("Generated" + S + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            pw.println("Total Users" + S + userList.size());
            pw.println();
            // Header
            pw.println("ID" + S + "Username" + S + "Email" + S + "Role" + S + "Status" + S + "Verified" + S + "Banned" + S + "XP" + S + "Country" + S + "Member Since");
            // Rows
            for (User u : userList) {
                String status   = u.isBanned() ? "Banned" : u.isActive() ? "Active" : "Inactive";
                String verified = u.isVerified() ? "Yes" : "No";
                String banned   = u.isBanned()   ? "Yes" : "No";
                String country  = u.getCountryCode() != null ? u.getCountryCode() : "";
                String joined   = u.getCreatedAt() != null ? u.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "";
                pw.println(u.getId() + S + csv(u.getUsername()) + S + csv(u.getEmail()) + S +
                    u.getRole().name().replace("ROLE_","") + S + status + S + verified + S +
                    banned + S + u.getXp() + S + country + S + joined);
            }
            // Summary
            pw.println();
            pw.println("--- SUMMARY ---");
            pw.println("Active"   + S + userList.stream().filter(u -> u.isActive() && !u.isBanned()).count());
            pw.println("Banned"   + S + userList.stream().filter(User::isBanned).count());
            pw.println("Verified" + S + userList.stream().filter(User::isVerified).count());
            pw.println("Students" + S + userList.stream().filter(u -> u.getRole() == User.Role.ROLE_STUDENT).count());
            pw.println("Tutors"   + S + userList.stream().filter(u -> u.getRole() == User.Role.ROLE_TUTOR).count());
            pw.println("Admins"   + S + userList.stream().filter(u -> u.getRole() == User.Role.ROLE_ADMIN).count());
            showInfo("Export Successful", "Saved to:\n" + file.getAbsolutePath());
        } catch (IOException e) { showError("Export Failed", e.getMessage()); }
    }

    private String csv(String v) {
        if (v == null) return "";
        if (v.contains(";") || v.contains("\"") || v.contains("\n"))
            return "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }
    private void showInfo(String title, String msg) {
        javafx.application.Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
            alert.setTitle(title); alert.setHeaderText(null); alert.showAndWait();
        });
    }

    // ── Bulk selection helpers ────────────────────────────────────────────────

    private void updateBulkBar() {
        long count = selectedMap.values().stream().filter(SimpleBooleanProperty::get).count();
        javafx.application.Platform.runLater(() -> {
            if (bulkBar != null) {
                bulkBar.setVisible(count > 0);
                bulkBar.setManaged(count > 0);
            }
            if (lblSelectedCount != null)
                lblSelectedCount.setText(count + " user" + (count == 1 ? "" : "s") + " selected");
        });
    }

    private java.util.List<User> getSelectedUsers() {
        return userList.stream()
            .filter(u -> selectedMap.containsKey(u.getId()) && selectedMap.get(u.getId()).get())
            .collect(Collectors.toList());
    }

    @FXML
    private void onClearSelection() {
        selectedMap.values().forEach(p -> p.set(false));
        updateBulkBar();
    }

    // ── Bulk actions ──────────────────────────────────────────────────────────

    @FXML
    private void onBulkActivate() {
        java.util.List<User> selected = getSelectedUsers();
        if (selected.isEmpty()) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Activate " + selected.size() + " user(s)?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Bulk Activate"); confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                int ok = 0;
                for (User u : selected) {
                    try { u.setActive(true); u.setBanned(false); userService.updateUser(u); ok++; }
                    catch (Exception e) { System.err.println("Activate error: " + e.getMessage()); }
                }
                showError("Activated " + ok + " user(s).", false);
                onClearSelection(); loadUsers(); refreshStats();
            }
        });
    }

    @FXML
    private void onBulkBan() {
        java.util.List<User> selected = getSelectedUsers();
        if (selected.isEmpty()) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Ban " + selected.size() + " user(s)?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Bulk Ban"); confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                int ok = 0;
                for (User u : selected) {
                    try { u.setBanned(true); u.setBanReason("Bulk ban by admin"); userService.updateUser(u); ok++; }
                    catch (Exception e) { System.err.println("Ban error: " + e.getMessage()); }
                }
                showError("Banned " + ok + " user(s).", false);
                onClearSelection(); loadUsers(); refreshStats();
            }
        });
    }

    @FXML
    private void onBulkDelete() {
        java.util.List<User> selected = getSelectedUsers();
        if (selected.isEmpty()) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Permanently delete " + selected.size() + " user(s)?\nThis cannot be undone.",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Bulk Delete"); confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                int ok = 0;
                for (User u : selected) {
                    try { userService.deleteUser(u.getId()); ok++; }
                    catch (Exception e) { System.err.println("Delete error: " + e.getMessage()); }
                }
                showError("Deleted " + ok + " user(s).", false);
                onClearSelection(); loadUsers(); refreshStats();
            }
        });
    }

    private void showError(String msg, boolean isError) {
        // Reuse existing showError or just print
        System.out.println("[BulkAction] " + msg);
    }
}