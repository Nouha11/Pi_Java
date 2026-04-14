package controllers.forum;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import models.forum.Post;
import services.forum.PostService;

public class AdminForumController {

    @FXML private TableView<Post> postTable;
    @FXML private TableColumn<Post, Integer> idCol;
    @FXML private TableColumn<Post, String> titleCol;
    @FXML private TableColumn<Post, String> authorCol;
    @FXML private TableColumn<Post, String> spaceCol;
    @FXML private TableColumn<Post, Integer> upvotesCol;
    @FXML private TableColumn<Post, String> statusCol;
    @FXML private TableColumn<Post, Void> actionsCol;

    private PostService postService = new PostService();
    private ObservableList<Post> postList;

    @FXML
    public void initialize() {
        setupTableColumns();
        loadData();
    }

    private void setupTableColumns() {
        // Map the columns to the Post object's data
        idCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getId()).asObject());
        titleCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getTitle()));

        authorCol.setCellValueFactory(cellData -> {
            String author = cellData.getValue().getAuthorName();
            return new SimpleStringProperty(author != null ? author : "Unknown");
        });

        spaceCol.setCellValueFactory(cellData -> {
            String space = cellData.getValue().getSpaceName();
            return new SimpleStringProperty(space != null ? space : "General");
        });

        upvotesCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getUpvotes()).asObject());

        statusCol.setCellValueFactory(cellData -> {
            boolean isLocked = cellData.getValue().isLocked();
            return new SimpleStringProperty(isLocked ? "🔒 Locked" : "🟢 Open");
        });

        // 🔥 GENERATE CUSTOM ACTION BUTTONS FOR EVERY ROW 🔥
        actionsCol.setCellFactory(param -> new TableCell<Post, Void>() {
            private final Button lockBtn = new Button();
            private final Button deleteBtn = new Button("Delete");
            private final HBox pane = new HBox(10, lockBtn, deleteBtn);

            {
                pane.setAlignment(Pos.CENTER);

                // Style Delete Button
                deleteBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 4;");

                // Toggle Lock/Unlock Logic
                lockBtn.setOnAction(e -> {
                    Post p = getTableView().getItems().get(getIndex());
                    boolean newState = !p.isLocked();

                    // Update Database
                    postService.toggleLock(p.getId(), newState);

                    // Update Local UI State
                    p.setLocked(newState);
                    getTableView().refresh(); // Refresh table to show new status
                });

                // Delete Logic
                deleteBtn.setOnAction(e -> {
                    Post p = getTableView().getItems().get(getIndex());
                    postService.supprimer(p.getId());
                    getTableView().getItems().remove(p); // Remove from UI instantly
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Post p = getTableView().getItems().get(getIndex());

                    // Change button appearance based on current lock state
                    if (p.isLocked()) {
                        lockBtn.setText("Unlock");
                        lockBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 4;");
                    } else {
                        lockBtn.setText("Lock");
                        lockBtn.setStyle("-fx-background-color: #f59e0b; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 4;");
                    }

                    setGraphic(pane);
                }
            }
        });
    }

    private void loadData() {
        postList = FXCollections.observableArrayList(postService.afficher());
        postTable.setItems(postList);
    }
}