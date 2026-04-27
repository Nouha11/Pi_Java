package controllers.forum;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import models.forum.Comment;
import models.forum.Post;
import models.forum.Report;
import services.forum.CommentService;
import services.forum.PostService;
import services.forum.ReportService;
import services.api.GeminiService;
import services.api.ReactionService;

import javafx.scene.layout.Region;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class PostDetailsController {

    @FXML private ScrollPane mainScrollPane;
    public static boolean scrollToBottomFlag = false;

    @FXML private Button backButton, editButton, deleteButton;
    @FXML private Button lockButton, submitCommentBtn, uploadCommentImgBtn;
    @FXML private Button summarizeBtn, reportPostBtn;
    @FXML private Label breadcrumbSpaceLabel, badgeSpaceLabel, topTitleLabel;
    @FXML private Label authorLabel, dateLabel;
    @FXML private Label topCommentBadgeLabel, commentImgNameLabel;
    @FXML private Label contentLabel, statusLabel;
    @FXML private ImageView postImageView;
    @FXML private Label repliesCountLabel, statsRepliesLabel, statsUpvotesLabel;
    @FXML private TextArea commentArea;
    @FXML private VBox commentsContainer;
    @FXML private Button saveButton;

    // VOTE & REACTION UI ELEMENTS
    @FXML private HBox voteBox;
    @FXML private Label upArrowBtn;
    @FXML private Label voteCountLabel;
    @FXML private Label downArrowBtn;
    @FXML private HBox reactionBarContainer;

    private Post currentPost;
    private CommentService commentService = new CommentService();
    private PostService postService = new PostService();
    private GeminiService geminiService = new GeminiService();
    private ReportService reportService = new ReportService();
    private ReactionService reactionService = new ReactionService();

    private String selectedCommentFileName = null;
    private String selectedCommentFilePath = null;
    private int currentUserId;

    @FXML
    public void initialize() {
        currentUserId = utils.UserSession.getInstance().getUserId();

        if (utils.ForumSession.currentPost != null) {
            setPostData(utils.ForumSession.currentPost);
        }

        commentArea.textProperty().addListener((obs, oldVal, newVal) -> {
            commentArea.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 4;");
        });

        // Report button hover effect
        if(reportPostBtn != null) {
            reportPostBtn.setOnMouseEntered(e -> reportPostBtn.setStyle("-fx-background-color: #ffe4e6; -fx-border-color: transparent; -fx-text-fill: #e11d48; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 7 14; -fx-background-radius: 6;"));
            reportPostBtn.setOnMouseExited(e -> reportPostBtn.setStyle("-fx-background-color: transparent; -fx-border-color: #fecdd3; -fx-border-radius: 6; -fx-text-fill: #e11d48; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 7 14; -fx-background-radius: 6;"));
        }
    }

    public void setPostData(Post post) {
        this.currentPost = post;
        String spaceName = post.getSpaceName() != null ? post.getSpaceName() : "General";

        breadcrumbSpaceLabel.setText(spaceName + "  •");
        badgeSpaceLabel.setText(spaceName);
        topTitleLabel.setText(post.getTitle());

        final String authorNameStr = post.getAuthorName() != null ? post.getAuthorName() : "Unknown Student";
        authorLabel.setText(authorNameStr);

        authorLabel.setStyle("-fx-text-fill: #3b82f6; -fx-font-weight: bold; -fx-cursor: hand;");
        authorLabel.setOnMouseEntered(e -> authorLabel.setUnderline(true));
        authorLabel.setOnMouseExited(e -> authorLabel.setUnderline(false));
        authorLabel.setOnMousePressed(e -> {
            e.consume();
            openForumActivity(post.getAuthorId(), authorNameStr);
        });

        if (post.getCreatedAt() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy");
            dateLabel.setText("Posted on " + sdf.format(post.getCreatedAt()));
        } else {
            dateLabel.setText("Posted recently");
        }

        contentLabel.setText(post.getContent());
        if (statsUpvotesLabel != null) statsUpvotesLabel.setText("👍 Upvotes: " + post.getUpvotes());

        if (utils.ForumSession.upvotedPosts.contains(post.getId())) currentPost.setMyVote(1);
        else if (utils.ForumSession.downvotedPosts.contains(post.getId())) currentPost.setMyVote(-1);
        else currentPost.setMyVote(0);

        updateVoteUI();

        upArrowBtn.setOnMouseClicked(e -> handleVote(1));
        downArrowBtn.setOnMouseClicked(e -> handleVote(-1));

        if (post.getContent() != null && post.getContent().length() > 100) {
            if (summarizeBtn != null) summarizeBtn.setVisible(true);
        } else {
            if (summarizeBtn != null) summarizeBtn.setVisible(false);
        }

        if (saveButton != null) {
            Set<Integer> mySavedPosts = utils.ForumSession.savedPostsPerUser.computeIfAbsent(currentUserId, k -> new java.util.HashSet<>());
            if (mySavedPosts.contains(post.getId())) {
                saveButton.setText("🔖 Saved");
                saveButton.setStyle("-fx-background-color: #eff6ff; -fx-border-color: #bfdbfe; -fx-border-radius: 6; -fx-text-fill: #2563eb; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 6 12; -fx-font-size: 12px; -fx-background-radius: 6;");
            } else {
                saveButton.setText("🔖 Save");
                saveButton.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: transparent; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 6 12; -fx-font-size: 12px; -fx-background-radius: 6;");
            }
        }

        if (reportPostBtn != null) {
            if (post.getAuthorId() == currentUserId) {
                reportPostBtn.setVisible(false);
                reportPostBtn.setManaged(false);
            } else {
                reportPostBtn.setVisible(true);
                reportPostBtn.setManaged(true);
            }
        }

        if (editButton != null) editButton.setVisible(post.getAuthorId() == currentUserId);
        if (deleteButton != null) deleteButton.setVisible(post.getAuthorId() == currentUserId);

        if (lockButton != null) {
            boolean isAdmin = "ROLE_ADMIN".equals(utils.UserSession.getInstance().getRole());
            lockButton.setVisible(isAdmin || post.getAuthorId() == currentUserId);
        }

        updateLockUI();

        if (post.getImageName() != null && !post.getImageName().trim().isEmpty()) {
            File imgFile = new File("C:/xampp/htdocs/projet dev/Pi_web/public/uploads/posts/" + post.getImageName());
            if (!imgFile.exists()) imgFile = new File("C:/xampp/htdocs/projet dev/Pi_web/public/uploads/forum/" + post.getImageName());

            if (imgFile.exists()) {
                postImageView.setImage(new Image(imgFile.toURI().toString(), true));
            }
        }

        new Thread(() -> {
            Map<String, Integer> counts = reactionService.getReactionsForPost(post.getId());
            String myReact = reactionService.getUserReactionForPost(currentUserId, post.getId());
            Platform.runLater(() -> renderReactions(counts, myReact));
        }).start();

        loadComments();
    }

    // ==========================================
    // 🔥 REACTION & VOTING ENGINE 🔥
    // ==========================================

    // API Call to fetch Emojis from CDN
    private String getEmojiApiUrl(String emoji) {
        String hex = switch (emoji) {
            case "👍" -> "1f44d";
            case "❤️" -> "2764";
            case "😂" -> "1f602";
            case "💡" -> "1f4a1";
            default -> "1f44d";
        };
        return "https://cdnjs.cloudflare.com/ajax/libs/twemoji/14.0.2/72x72/" + hex + ".png";
    }

    private void updateVoteUI() {
        voteCountLabel.setText(String.valueOf(currentPost.getUpvotes()));
        upArrowBtn.setStyle(currentPost.getMyVote() == 1 ? "-fx-text-fill: #ef4444; -fx-font-size: 14px; -fx-cursor: hand;" : "-fx-text-fill: #94a3b8; -fx-font-size: 14px; -fx-cursor: hand;");
        downArrowBtn.setStyle(currentPost.getMyVote() == -1 ? "-fx-text-fill: #3b82f6; -fx-font-size: 14px; -fx-cursor: hand;" : "-fx-text-fill: #94a3b8; -fx-font-size: 14px; -fx-cursor: hand;");
    }

    private void handleVote(int type) {
        if (currentPost.getMyVote() == type) {
            postService.updateUpvotes(currentPost.getId(), -type);
            currentPost.setUpvotes(currentPost.getUpvotes() - type);
            currentPost.setMyVote(0);
            utils.ForumSession.upvotedPosts.remove(currentPost.getId());
            utils.ForumSession.downvotedPosts.remove(currentPost.getId());
        } else {
            int change = (currentPost.getMyVote() == -type) ? type * 2 : type;
            postService.updateUpvotes(currentPost.getId(), change);
            currentPost.setUpvotes(currentPost.getUpvotes() + change);
            currentPost.setMyVote(type);
            if (type == 1) { utils.ForumSession.upvotedPosts.add(currentPost.getId()); utils.ForumSession.downvotedPosts.remove(currentPost.getId()); }
            else { utils.ForumSession.downvotedPosts.add(currentPost.getId()); utils.ForumSession.upvotedPosts.remove(currentPost.getId()); }
        }
        updateVoteUI();
        if (statsUpvotesLabel != null) statsUpvotesLabel.setText("👍 Upvotes: " + currentPost.getUpvotes());
    }

    private void renderReactions(Map<String, Integer> reactionCounts, String myReaction) {
        if (reactionBarContainer == null) return;
        reactionBarContainer.getChildren().clear();
        String[] allowedEmojis = {"👍", "❤️", "😂", "💡"};

        for (String emoji : allowedEmojis) {
            int count = reactionCounts != null ? reactionCounts.getOrDefault(emoji, 0) : 0;
            boolean isActive = emoji.equals(myReaction);

            HBox pill = new HBox(6);
            pill.setAlignment(Pos.CENTER);

            // 🔥 IMAGE API USAGE HERE 🔥
            ImageView emojiIcon = new ImageView(new Image(getEmojiApiUrl(emoji), true));
            emojiIcon.setFitWidth(18);
            emojiIcon.setFitHeight(18);
            emojiIcon.setPreserveRatio(true);

            Label countLbl = new Label(count > 0 ? String.valueOf(count) : "");
            countLbl.setStyle(isActive ? "-fx-text-fill: #1d4ed8; -fx-font-weight: bold; -fx-font-size: 13px;" : "-fx-text-fill: #64748b; -fx-font-weight: bold; -fx-font-size: 13px;");

            pill.getChildren().add(emojiIcon);
            if (count > 0) pill.getChildren().add(countLbl);

            String baseStyle = isActive ?
                    "-fx-background-color: #eff6ff; -fx-border-color: #bfdbfe; -fx-border-radius: 20; -fx-background-radius: 20; -fx-padding: 5 12; -fx-cursor: hand;" :
                    "-fx-background-color: #f1f5f9; -fx-border-color: transparent; -fx-border-radius: 20; -fx-background-radius: 20; -fx-padding: 6 13; -fx-cursor: hand;";
            String hoverStyle = isActive ?
                    "-fx-background-color: #dbeafe; -fx-border-color: #93c5fd; -fx-border-radius: 20; -fx-background-radius: 20; -fx-padding: 5 12; -fx-cursor: hand;" :
                    "-fx-background-color: #e2e8f0; -fx-border-color: transparent; -fx-border-radius: 20; -fx-background-radius: 20; -fx-padding: 6 13; -fx-cursor: hand;";

            pill.setStyle(baseStyle);
            pill.setOnMouseEntered(e -> pill.setStyle(hoverStyle));
            pill.setOnMouseExited(e -> pill.setStyle(baseStyle));

            pill.setOnMouseClicked(e -> {
                e.consume();
                new Thread(() -> {
                    reactionService.reactToPost(currentUserId, currentPost.getId(), emoji);
                    Map<String, Integer> updatedCounts = reactionService.getReactionsForPost(currentPost.getId());
                    String updatedMyReact = reactionService.getUserReactionForPost(currentUserId, currentPost.getId());
                    Platform.runLater(() -> renderReactions(updatedCounts, updatedMyReact));
                }).start();
            });

            reactionBarContainer.getChildren().add(pill);
        }
    }
    // ==========================================

    private void loadComments() {
        commentsContainer.getChildren().clear();

        Label loadingLabel = new Label("Loading comments...");
        loadingLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic;");
        commentsContainer.getChildren().add(loadingLabel);

        new Thread(() -> {
            List<Comment> comments = commentService.getCommentsByPost(currentPost.getId());

            Platform.runLater(() -> {
                commentsContainer.getChildren().clear();

                String replyCount = String.valueOf(comments.size());
                if (repliesCountLabel != null) repliesCountLabel.setText("Replies (" + replyCount + ")");
                if (statsRepliesLabel != null) statsRepliesLabel.setText("💬 Replies: " + replyCount);
                if (topCommentBadgeLabel != null) topCommentBadgeLabel.setText("💬 " + replyCount + " Comments");

                if (comments.isEmpty()) {
                    Label emptyLabel = new Label("No comments yet. Be the first to share your thoughts!");
                    emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 14px;");
                    commentsContainer.getChildren().add(emptyLabel);
                    return;
                }

                for (Comment c : comments) {
                    boolean isCensored = c.getContent().equals("🚫 *[This comment was removed by a moderator for violating community guidelines]*");

                    VBox commentCard = new VBox(8);
                    commentCard.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 15;");

                    HBox headerBox = new HBox(10);
                    Label author = new Label(c.getAuthorName() != null ? c.getAuthorName() : "Student");
                    author.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a;");
                    Label time = new Label("• Reply");
                    time.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
                    headerBox.getChildren().addAll(author, time);

                    Label content = new Label(c.getContent());
                    content.setWrapText(true);

                    if (isCensored) {
                        content.setStyle("-fx-text-fill: #64748b; -fx-font-size: 14px; -fx-font-style: italic;");
                    } else {
                        content.setStyle("-fx-text-fill: #334155; -fx-font-size: 14px;");
                    }

                    VBox imageBox = new VBox();
                    if (!isCensored && c.getImageName() != null && !c.getImageName().isEmpty()) {
                        File imgFile = new File("C:/xampp/htdocs/projet dev/Pi_web/public/uploads/comments/" + c.getImageName());
                        if (imgFile.exists()) {
                            try {
                                ImageView commentImgView = new ImageView(new Image(imgFile.toURI().toString(), true));
                                commentImgView.setFitWidth(350);
                                commentImgView.setPreserveRatio(true);
                                imageBox.setStyle("-fx-padding: 10 0 5 0;");
                                imageBox.getChildren().add(commentImgView);
                            } catch (Exception e) {}
                        }
                    }

                    HBox actionsBox = new HBox(10);
                    actionsBox.setAlignment(Pos.CENTER_RIGHT);

                    if (!isCensored) {
                        if (c.getAuthorId() == currentUserId) {
                            Button editBtn = new Button("Edit");
                            editBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #3b82f6; -fx-cursor: hand; -fx-font-size: 12px; -fx-font-weight: bold;");
                            editBtn.setOnAction(e -> handleEditComment(c));

                            Button deleteBtn = new Button("Delete");
                            deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ef4444; -fx-cursor: hand; -fx-font-size: 12px; -fx-font-weight: bold;");
                            deleteBtn.setOnAction(e -> handleDeleteComment(c));

                            actionsBox.getChildren().addAll(editBtn, deleteBtn);
                        } else {
                            Button reportBtn = new Button("🚩 Report");
                            reportBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ef4444; -fx-cursor: hand; -fx-font-size: 12px; -fx-font-weight: bold;");
                            reportBtn.setOnAction(e -> showReportDialog("COMMENT", c.getId()));
                            actionsBox.getChildren().add(reportBtn);
                        }
                    }

                    commentCard.getChildren().addAll(headerBox, content, imageBox);
                    if (!actionsBox.getChildren().isEmpty()) commentCard.getChildren().add(actionsBox);

                    commentsContainer.getChildren().add(commentCard);
                }

                if (scrollToBottomFlag && mainScrollPane != null) {
                    scrollToBottomFlag = false;

                    PauseTransition waitBeforeScroll = new PauseTransition(Duration.millis(250));
                    waitBeforeScroll.setOnFinished(ev -> {
                        mainScrollPane.setVvalue(mainScrollPane.getVmax());

                        if (!commentsContainer.getChildren().isEmpty()) {
                            Node lastNode = commentsContainer.getChildren().get(commentsContainer.getChildren().size() - 1);

                            String baseStyle = "-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 15;";
                            String flashStyle = "-fx-background-color: #eff6ff; -fx-border-color: #3b82f6; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 15; -fx-effect: dropshadow(gaussian, rgba(59,130,246,0.3), 15, 0, 0, 0);";

                            lastNode.setStyle(flashStyle);

                            PauseTransition revert = new PauseTransition(Duration.seconds(2.5));
                            revert.setOnFinished(e -> lastNode.setStyle(baseStyle));
                            revert.play();
                        }
                    });
                    waitBeforeScroll.play();
                }
            });
        }).start();
    }

    @FXML
    void handleReportPost(ActionEvent event) {
        showReportDialog("POST", currentPost.getId());
    }

    private void showReportDialog(String targetType, int targetId) {
        if (targetType.equals("POST") && reportService.hasAlreadyReportedPost(currentUserId, targetId)) {
            showAlert(Alert.AlertType.INFORMATION, "Already Reported", "You have already reported this post. Our admins are reviewing it.");
            return;
        } else if (targetType.equals("COMMENT") && reportService.hasAlreadyReportedComment(currentUserId, targetId)) {
            showAlert(Alert.AlertType.INFORMATION, "Already Reported", "You have already reported this reply. Our admins are reviewing it.");
            return;
        }

        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.UTILITY);
        dialogStage.setTitle("Report " + (targetType.equals("POST") ? "Discussion" : "Reply"));

        VBox root = new VBox(15);
        root.setStyle("-fx-background-color: white; -fx-padding: 25; -fx-border-radius: 8; -fx-background-radius: 8;");

        Label title = new Label("🚩 Report to Admins");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 900; -fx-text-fill: #0f172a;");

        Label message = new Label("Please select a reason for reporting. This will be sent to moderation for review.");
        message.setWrapText(true);
        message.setStyle("-fx-text-fill: #475569; -fx-font-size: 13px; -fx-line-spacing: 4px;");

        ComboBox<String> reasonBox = new ComboBox<>();
        reasonBox.getItems().addAll(
                "Spam or self-promotion",
                "Harassment or bullying",
                "Hate speech or inappropriate content",
                "Off-topic or irrelevant",
                "Other"
        );
        reasonBox.setPromptText("Select a reason...");
        reasonBox.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-radius: 6; -fx-pref-width: 300; -fx-font-size: 13px;");

        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        VBox.setMargin(buttonBox, new Insets(10, 0, 0, 0));

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        cancelBtn.setOnAction(e -> dialogStage.close());

        Button submitBtn = new Button("Submit Report");
        submitBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        submitBtn.setOnAction(e -> {
            if (reasonBox.getValue() == null) {
                reasonBox.setStyle("-fx-background-color: #fef2f2; -fx-border-color: #ef4444; -fx-border-radius: 6; -fx-pref-width: 300;");
                return;
            }

            Report report;
            if (targetType.equals("POST")) {
                report = Report.createPostReport(currentUserId, targetId, reasonBox.getValue());
            } else {
                report = Report.createCommentReport(currentUserId, targetId, reasonBox.getValue());
            }

            reportService.submitReport(report);
            dialogStage.close();

            showAlert(Alert.AlertType.INFORMATION, "Report Submitted", "Thank you for helping keep NOVA safe. Our team will review this shortly.");
        });

        buttonBox.getChildren().addAll(cancelBtn, submitBtn);
        root.getChildren().addAll(title, message, reasonBox, buttonBox);

        Scene scene = new Scene(root, 400, 250);
        dialogStage.setScene(scene);
        dialogStage.showAndWait();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    void handleSavePost(ActionEvent event) {
        Set<Integer> mySavedPosts = utils.ForumSession.savedPostsPerUser.computeIfAbsent(currentUserId, k -> new java.util.HashSet<>());
        if (mySavedPosts.contains(currentPost.getId())) {
            mySavedPosts.remove(currentPost.getId());
            postService.unsavePost(currentUserId, currentPost.getId());
            if (saveButton != null) {
                saveButton.setText("🔖 Save");
                saveButton.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: transparent; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 6 12; -fx-font-size: 12px; -fx-background-radius: 6;");
            }
        } else {
            mySavedPosts.add(currentPost.getId());
            postService.savePost(currentUserId, currentPost.getId());
            if (saveButton != null) {
                saveButton.setText("🔖 Saved");
                saveButton.setStyle("-fx-background-color: #eff6ff; -fx-border-color: #bfdbfe; -fx-border-radius: 6; -fx-text-fill: #2563eb; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 6 12; -fx-font-size: 12px; -fx-background-radius: 6;");
            }
        }
    }

    @FXML
    void handleSummarizePost(ActionEvent event) {
        String fullContent = currentPost.getContent();
        summarizeBtn.setDisable(true);
        String originalText = summarizeBtn.getText();
        summarizeBtn.setText("✨ AI is reading...");

        new Thread(() -> {
            String aiSummary = geminiService.summarizePost(fullContent);
            Platform.runLater(() -> {
                summarizeBtn.setDisable(false);
                summarizeBtn.setText(originalText);
                if (aiSummary != null && !aiSummary.contains("Error")) {
                    Stage summaryStage = new Stage();
                    summaryStage.initModality(Modality.APPLICATION_MODAL);
                    summaryStage.initStyle(StageStyle.TRANSPARENT);

                    VBox root = new VBox(20);
                    root.setAlignment(Pos.CENTER);
                    root.setStyle("-fx-background-color: white; -fx-padding: 35; -fx-background-radius: 16; -fx-border-radius: 16; -fx-border-color: #e2e8f0; -fx-border-width: 1; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.15), 20, 0, 0, 8);");

                    Label title = new Label("✨ AI Summary");
                    title.setStyle("-fx-font-size: 22px; -fx-font-weight: 900; -fx-text-fill: #9333ea;");

                    Label subtitle = new Label("TL;DR (Too Long; Didn't Read)");
                    subtitle.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px; -fx-font-weight: bold;");

                    VBox headerBox = new VBox(2);
                    headerBox.setAlignment(Pos.CENTER);
                    headerBox.getChildren().addAll(title, subtitle);

                    Label content = new Label(aiSummary);
                    content.setWrapText(true);
                    content.setMaxWidth(450);
                    content.setStyle("-fx-font-size: 15px; -fx-text-fill: #334155; -fx-line-spacing: 6px; -fx-alignment: center;");

                    Button closeBtn = new Button("Awesome, thanks!");
                    closeBtn.setStyle("-fx-background-color: #f3e8ff; -fx-text-fill: #9333ea; -fx-font-weight: bold; -fx-padding: 10 24; -fx-background-radius: 8; -fx-cursor: hand; -fx-font-size: 14px;");
                    closeBtn.setOnAction(e -> summaryStage.close());

                    root.getChildren().addAll(headerBox, content, closeBtn);

                    Scene scene = new Scene(root);
                    scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
                    summaryStage.setScene(scene);
                    summaryStage.centerOnScreen();
                    summaryStage.showAndWait();
                } else {
                    showAlert(Alert.AlertType.ERROR, "AI Error", "The AI service is currently unavailable. Please try again later.");
                }
            });
        }).start();
    }

    private void updateLockUI() {
        if (currentPost.isLocked()) {
            if (topTitleLabel != null) {
                topTitleLabel.setText("🔒 " + currentPost.getTitle());
                topTitleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #94a3b8;");
            }
            if (statusLabel != null) {
                statusLabel.setText("🔒 Status: Locked");
                statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
            }
            if (lockButton != null) lockButton.setText("🔓 Unlock");
            if (commentArea != null) {
                commentArea.setDisable(true);
                commentArea.setPromptText("🔒 This discussion has been locked.");
            }
            if (submitCommentBtn != null) submitCommentBtn.setDisable(true);
            if(uploadCommentImgBtn != null) uploadCommentImgBtn.setDisable(true);
        } else {
            if (topTitleLabel != null) {
                topTitleLabel.setText(currentPost.getTitle());
                topTitleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
            }
            if (statusLabel != null) {
                statusLabel.setText("🛡 Status: Open");
                statusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
            }
            if (lockButton != null) lockButton.setText("🔒 Lock");
            if (commentArea != null) {
                commentArea.setDisable(false);
                commentArea.setPromptText("Write a reply...");
            }
            if (submitCommentBtn != null) submitCommentBtn.setDisable(false);
            if(uploadCommentImgBtn != null) uploadCommentImgBtn.setDisable(false);
        }
    }

    @FXML
    void handleUploadCommentImage(ActionEvent event) {
        if (currentPost.isLocked()) return;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Image for Reply");
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            selectedCommentFileName = selectedFile.getName();
            selectedCommentFilePath = selectedFile.getAbsolutePath();
            if (commentImgNameLabel != null) commentImgNameLabel.setText("Selected: " + selectedCommentFileName);
        }
    }

    @FXML
    void handleSubmitComment(ActionEvent event) {
        if (currentPost.isLocked()) return;
        String text = commentArea.getText();
        if (text == null || text.trim().length() < 2) {
            applyErrorStyle("Reply must be at least 2 characters!");
            return;
        }
        if (!commentService.isCommentUnique(text, currentPost.getId())) {
            applyErrorStyle("You already posted this exact reply!");
            return;
        }

        Comment newComment = new Comment(text, currentPost.getId(), currentUserId, null);
        if (selectedCommentFileName != null && selectedCommentFilePath != null) {
            try {
                java.nio.file.Path sourcePath = java.nio.file.Paths.get(selectedCommentFilePath);
                String xamppPath = "C:/xampp/htdocs/projet dev/Pi_web/public/uploads/comments/" + selectedCommentFileName;
                java.nio.file.Path destPath = java.nio.file.Paths.get(xamppPath);
                java.nio.file.Files.createDirectories(destPath.getParent());
                java.nio.file.Files.copy(sourcePath, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                newComment.setImageName(selectedCommentFileName);
            } catch (java.io.IOException e) {}
        }
        commentService.ajouter(newComment);
        commentArea.clear();
        commentArea.setPromptText("Write a reply...");
        selectedCommentFileName = null;
        selectedCommentFilePath = null;
        if(commentImgNameLabel != null) commentImgNameLabel.setText("");

        scrollToBottomFlag = true;
        loadComments();
    }

    private void handleEditComment(Comment c) {
        if (currentPost.isLocked()) {
            applyErrorStyle("Cannot edit replies in a locked discussion.");
            return;
        }
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.UTILITY);
        dialogStage.setTitle("Edit Reply");

        VBox root = new VBox(15);
        root.setStyle("-fx-background-color: white; -fx-padding: 25; -fx-border-radius: 8; -fx-background-radius: 8;");
        Label title = new Label("✏ Edit Your Reply");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 900; -fx-text-fill: #0f172a;");

        TextArea textArea = new TextArea(c.getContent());
        textArea.setWrapText(true);
        textArea.setPrefRowCount(4);
        textArea.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-radius: 6; -fx-font-size: 14px; -fx-padding: 5;");

        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        cancelBtn.setOnAction(e -> dialogStage.close());

        Button saveBtn = new Button("Save Changes");
        saveBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        saveBtn.setOnAction(e -> {
            String newContent = textArea.getText().trim();
            if (!newContent.isEmpty() && !newContent.equals(c.getContent())) {
                c.setContent(newContent);
                commentService.modifier(c);
                loadComments();
            }
            dialogStage.close();
        });
        buttonBox.getChildren().addAll(cancelBtn, saveBtn);
        root.getChildren().addAll(title, textArea, buttonBox);
        Scene scene = new Scene(root, 500, 250);
        dialogStage.setScene(scene);
        dialogStage.showAndWait();
    }

    private void handleDeleteComment(Comment c) {
        if (currentPost.isLocked()) {
            applyErrorStyle("Cannot delete replies in a locked discussion.");
            return;
        }
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.UTILITY);
        dialogStage.setTitle("Delete Reply");

        VBox root = new VBox(15);
        root.setStyle("-fx-background-color: white; -fx-padding: 25; -fx-border-radius: 8; -fx-background-radius: 8;");
        Label title = new Label("🗑 Delete Reply?");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 900; -fx-text-fill: #e11d48;");

        Label message = new Label("Are you sure you want to delete this reply? This action cannot be undone.");
        message.setWrapText(true);
        message.setStyle("-fx-text-fill: #475569; -fx-font-size: 14px; -fx-line-spacing: 4px;");

        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = new Button("Keep Reply");
        cancelBtn.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        cancelBtn.setOnAction(e -> dialogStage.close());

        Button deleteBtn = new Button("Yes, Delete");
        deleteBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16; -background-radius: 6; -fx-cursor: hand;");
        deleteBtn.setOnAction(e -> {
            commentService.supprimer(c.getId());
            loadComments();
            dialogStage.close();
        });
        buttonBox.getChildren().addAll(cancelBtn, deleteBtn);
        root.getChildren().addAll(title, message, buttonBox);
        Scene scene = new Scene(root, 450, 200);
        dialogStage.setScene(scene);
        dialogStage.showAndWait();
    }

    @FXML
    void handleLockPost(ActionEvent event) {
        boolean newState = !currentPost.isLocked();
        currentPost.setLocked(newState);
        postService.toggleLock(currentPost.getId(), newState);
        updateLockUI();
    }

    private void applyErrorStyle(String errorMessage) {
        commentArea.setStyle("-fx-background-color: #fef2f2; -fx-border-color: #ef4444; -fx-border-radius: 4;");
        commentArea.clear();
        commentArea.setPromptText("⚠️ " + errorMessage);
        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(e -> {
            commentArea.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 8;");
            commentArea.setPromptText("Write a thoughtful reply...");
        });
        pause.play();
    }

    @FXML
    void handleDeletePost(ActionEvent event) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Post");
        confirm.setHeaderText("Are you sure you want to delete this post?");
        confirm.setContentText("This action cannot be undone.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            postService.supprimer(currentPost.getId());
            handleBack(null);
        }
    }

    @FXML
    void handleEditPost(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/forum/student/add_post.fxml"));
            Parent root = loader.load();
            AddPostController controller = loader.getController();
            controller.setPostToEdit(this.currentPost);
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Edit Post");
            stage.setScene(new Scene(root));
            stage.showAndWait();
            setPostData(this.currentPost);
        } catch (Exception ex) {}
    }

    @FXML
    void handleBack(ActionEvent event) {
        controllers.NovaDashboardController.loadPage("/views/forum/forum_feed.fxml");
    }

    private void openForumActivity(int userId, String username) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/forum/student/forum_activity.fxml"));
            Parent root = loader.load();
            ForumActivityController controller = loader.getController();
            controller.loadUserData(userId, username);
            Stage popupStage = new Stage();
            popupStage.setTitle(username + " - Forum Activity");
            popupStage.setScene(new Scene(root, 450, 550));
            popupStage.initModality(Modality.APPLICATION_MODAL);
            popupStage.show();
        } catch (Exception e) {}
    }
}