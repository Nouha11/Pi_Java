package controllers.forum;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
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

        actionsCol.setCellFactory(param -> new TableCell<Post, Void>() {
            private final Button lockBtn = new Button();
            private final Button editBtn = new Button("Edit"); // 🔥 NEW EDIT BUTTON
            private final Button deleteBtn = new Button("Delete");
            private final HBox pane = new HBox(8, lockBtn, editBtn, deleteBtn);

            {
                pane.setAlignment(Pos.CENTER);

                // Styling
                editBtn.setStyle("-fx-background-color: #fef08a; -fx-text-fill: #a16207; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 4;");
                deleteBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 4;");

                // Toggle Lock/Unlock Logic
                lockBtn.setOnAction(e -> {
                    Post p = getTableView().getItems().get(getIndex());
                    boolean newState = !p.isLocked();
                    postService.toggleLock(p.getId(), newState);
                    p.setLocked(newState);
                    getTableView().refresh();
                });

                // 🔥 Edit Logic for Admins
                editBtn.setOnAction(e -> {
                    Post p = getTableView().getItems().get(getIndex());
                    showAdminEditDialog(p);
                });

                // Delete Logic
                deleteBtn.setOnAction(e -> {
                    Post p = getTableView().getItems().get(getIndex());
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete this post permanently?", ButtonType.YES, ButtonType.NO);
                    confirm.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.YES) {
                            postService.supprimer(p.getId());
                            getTableView().getItems().remove(p);
                        }
                    });
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Post p = getTableView().getItems().get(getIndex());
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

    // 🔥 Sleek programmatic popup window for Admin Editing
    private void showAdminEditDialog(Post post) {
        Stage editStage = new Stage();
        editStage.initModality(Modality.APPLICATION_MODAL);
        editStage.setTitle("Admin Override: Edit Post");

        VBox layout = new VBox(15);
        layout.setStyle("-fx-background-color: #f8fafc; -fx-padding: 25;");

        Label headerText = new Label("Admin Edit: ID #" + post.getId());
        headerText.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #dc2626;");

        TextField titleInput = new TextField(post.getTitle());
        titleInput.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 4; -fx-padding: 10; -fx-font-size: 14px;");

        TextArea contentInput = new TextArea(post.getContent());
        contentInput.setWrapText(true);
        contentInput.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 4; -fx-font-size: 14px;");

        Button saveChangesBtn = new Button("Force Save Changes");
        saveChangesBtn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 6; -fx-cursor: hand;");

        saveChangesBtn.setOnAction(e -> {
            if (!titleInput.getText().isEmpty() && !contentInput.getText().isEmpty()) {
                post.setTitle(titleInput.getText());
                post.setContent(contentInput.getText());

                postService.modifier(post);
                postTable.refresh(); // Magically updates the table behind the popup
                editStage.close();
            }
        });

        layout.getChildren().addAll(headerText, new Label("Title:"), titleInput, new Label("Content:"), contentInput, saveChangesBtn);

        Scene scene = new Scene(layout, 500, 450);
        editStage.setScene(scene);
        editStage.showAndWait();
    }

    private void loadData() {
        postList = FXCollections.observableArrayList(postService.afficher());
        postTable.setItems(postList);
    }
}