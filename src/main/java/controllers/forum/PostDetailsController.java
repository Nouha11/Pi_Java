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
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
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
import services.forum.PollService;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
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

    // 🔥 POLL CONTAINER
    @FXML private VBox pollContainer;

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
    private PollService pollService = new PollService();

    private String selectedCommentFileName = null;
    private String selectedCommentFilePath = null;
    private int currentUserId;

    // 🔥 HELPER: Fixes Dark Mode for nodes generated AFTER the page loads!
    private void applyDarkThemeIfNeed(Node node) {
        try {
            if (utils.ThemeManager.getInstance().getMode() == utils.ThemeManager.Mode.DARK) {
                utils.DarkModeApplier.applyToNode(node, true);
            }
        } catch (Exception ignored) {}
    }

    @FXML
    public void initialize() {
        currentUserId = utils.UserSession.getInstance().getUserId();

        if (utils.ForumSession.currentPost != null) {
            setPostData(utils.ForumSession.currentPost);
        }

        commentArea.textProperty().addListener((obs, oldVal, newVal) -> {
            commentArea.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 4;");
        });

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

        boolean isAuthor = post.getAuthorId() == currentUserId;
        if (editButton != null) { editButton.setVisible(isAuthor); editButton.setManaged(isAuthor); }
        if (deleteButton != null) { deleteButton.setVisible(isAuthor); deleteButton.setManaged(isAuthor); }
        if (reportPostBtn != null) { reportPostBtn.setVisible(!isAuthor); reportPostBtn.setManaged(!isAuthor); }
        if (lockButton != null) lockButton.setVisible("ROLE_ADMIN".equals(utils.UserSession.getInstance().getRole()) || isAuthor);

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

        loadPoll();
        loadComments();
    }

    private void loadPoll() {
        if (pollContainer == null) return;
        pollContainer.getChildren().clear();

        new Thread(() -> {
            Map<String, Object> poll = pollService.getPollForPost(currentPost.getId());
            if (poll == null) return;

            int pollId = (int) poll.get("id");
            String question = (String) poll.get("question");
            List<Map<String, Object>> options = (List<Map<String, Object>>) poll.get("options");
            Integer userVote = pollService.getUserVote(currentUserId, pollId);

            Platform.runLater(() -> {
                pollContainer.setVisible(true);
                pollContainer.setManaged(true);
                pollContainer.setSpacing(12);
                pollContainer.setStyle("-fx-background-color: white; -fx-padding: 20; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-border-width: 1;");
                pollContainer.getStyleClass().add("card-bg");

                Label qLbl = new Label("📊 " + question);
                qLbl.setStyle("-fx-font-weight: 900; -fx-font-size: 16px;");
                qLbl.getStyleClass().add("text-primary");
                pollContainer.getChildren().add(qLbl);

                int totalVotes = options.stream().mapToInt(o -> (int) o.get("votes")).sum();

                for (Map<String, Object> opt : options) {
                    int optId = (int) opt.get("id");
                    String text = (String) opt.get("text");
                    int votes = (int) opt.get("votes");

                    if (userVote == null) {
                        Button b = new Button(text);
                        b.setMaxWidth(Double.MAX_VALUE);
                        b.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-radius: 6; -fx-padding: 12; -fx-cursor: hand; -fx-font-weight: bold; -fx-text-fill: #334155; -fx-alignment: center-left; -fx-font-size: 14px;");
                        b.setOnMouseEntered(e -> b.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: #94a3b8; -fx-border-radius: 6; -fx-padding: 12; -fx-cursor: hand; -fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-alignment: center-left; -fx-font-size: 14px;"));
                        b.setOnMouseExited(e -> b.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-radius: 6; -fx-padding: 12; -fx-cursor: hand; -fx-font-weight: bold; -fx-text-fill: #334155; -fx-alignment: center-left; -fx-font-size: 14px;"));
                        b.setOnAction(e -> { pollService.castVote(currentUserId, pollId, optId); loadPoll(); });
                        pollContainer.getChildren().add(b);
                    } else {
                        double pct = totalVotes == 0 ? 0 : (double) votes / totalVotes;

                        StackPane optionBar = new StackPane();
                        optionBar.setAlignment(Pos.CENTER_LEFT);
                        optionBar.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 6; -fx-border-radius: 6; -fx-border-color: " + (userVote == optId ? "#3b82f6" : "#e2e8f0") + "; -fx-border-width: " + (userVote == optId ? "2" : "1") + ";");

                        Region fill = new Region();
                        fill.setStyle("-fx-background-color: " + (userVote == optId ? "#dbeafe" : "#e2e8f0") + "; -fx-background-radius: 4;");
                        fill.setMaxWidth(pct * 550); // Scale based on container width
                        fill.setPrefHeight(40);

                        HBox textOverlay = new HBox();
                        textOverlay.setAlignment(Pos.CENTER_LEFT);
                        textOverlay.setPadding(new Insets(0, 15, 0, 15));

                        Label optText = new Label(text);
                        optText.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-font-size: 13px;");

                        Region spacer = new Region();
                        HBox.setHgrow(spacer, Priority.ALWAYS);

                        Label pctText = new Label((int)(pct*100) + "% (" + votes + ")");
                        pctText.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569; -fx-font-size: 13px;");

                        if (userVote == optId) {
                            Label check = new Label(" ✓");
                            check.setStyle("-fx-text-fill: #2563eb; -fx-font-weight: 900; -fx-font-size: 14px;");
                            textOverlay.getChildren().addAll(optText, check, spacer, pctText);
                        } else {
                            textOverlay.getChildren().addAll(optText, spacer, pctText);
                        }

                        optionBar.getChildren().addAll(fill, textOverlay);
                        pollContainer.getChildren().add(optionBar);
                    }
                }
                applyDarkThemeIfNeed(pollContainer); // 🔥 Async dark mode
            });
        }).start();
    }

    private String getEmojiApiUrl(String emoji) {
        String hex = switch (emoji) { case "👍" -> "1f44d"; case "❤️" -> "2764"; case "😂" -> "1f602"; case "💡" -> "1f4a1"; default -> "1f44d"; };
        return "https://cdnjs.cloudflare.com/ajax/libs/twemoji/14.0.2/72x72/" + hex + ".png";
    }

    private void updateVoteUI() {
        voteCountLabel.setText(String.valueOf(currentPost.getUpvotes()));
        upArrowBtn.setStyle(currentPost.getMyVote() == 1 ? "-fx-text-fill: #ef4444; -fx-cursor: hand;" : "-fx-text-fill: #94a3b8; -fx-cursor: hand;");
        downArrowBtn.setStyle(currentPost.getMyVote() == -1 ? "-fx-text-fill: #3b82f6; -fx-cursor: hand;" : "-fx-text-fill: #94a3b8; -fx-cursor: hand;");
    }

    private void handleVote(int type) {
        int change = (currentPost.getMyVote() == type) ? -type : ((currentPost.getMyVote() == -type) ? type * 2 : type);
        postService.updateUpvotes(currentPost.getId(), change);
        currentPost.setUpvotes(currentPost.getUpvotes() + change);
        currentPost.setMyVote(currentPost.getMyVote() == type ? 0 : type);
        updateVoteUI();
    }

    private void renderReactions(Map<String, Integer> reactionCounts, String myReaction) {
        if (reactionBarContainer == null) return;
        reactionBarContainer.getChildren().clear();
        String[] allowedEmojis = {"👍", "❤️", "😂", "💡"};
        for (String emoji : allowedEmojis) {
            int count = reactionCounts != null ? reactionCounts.getOrDefault(emoji, 0) : 0;
            boolean isActive = emoji.equals(myReaction);
            HBox pill = new HBox(6); pill.setAlignment(Pos.CENTER);
            ImageView emojiIcon = new ImageView(new Image(getEmojiApiUrl(emoji), true)); emojiIcon.setFitWidth(18); emojiIcon.setFitHeight(18);
            Label countLbl = new Label(count > 0 ? String.valueOf(count) : "");
            countLbl.setStyle(isActive ? "-fx-text-fill: #1d4ed8; -fx-font-weight: bold;" : "-fx-text-fill: #64748b; -fx-font-weight: bold;");
            pill.getChildren().addAll(emojiIcon, countLbl);
            pill.setStyle(isActive ? "-fx-background-color: #eff6ff; -fx-border-color: #bfdbfe; -fx-border-radius: 20; -fx-padding: 5 12; -fx-cursor: hand;" : "-fx-background-color: #f1f5f9; -fx-padding: 6 13; -fx-background-radius: 20; -fx-cursor: hand;");
            pill.setOnMouseClicked(e -> { e.consume(); new Thread(() -> { reactionService.reactToPost(currentUserId, currentPost.getId(), emoji); Platform.runLater(() -> renderReactions(reactionService.getReactionsForPost(currentPost.getId()), reactionService.getUserReactionForPost(currentUserId, currentPost.getId()))); }).start(); });
            reactionBarContainer.getChildren().add(pill);
        }
        applyDarkThemeIfNeed(reactionBarContainer); // 🔥 Async dark mode
    }

    // =========================================================================
    // 🔥 NEW: NESTED REPLIES ENGINE
    // =========================================================================

    private void loadComments() {
        commentsContainer.getChildren().clear();
        Label loadingLabel = new Label("Loading comments...");
        loadingLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic;");
        commentsContainer.getChildren().add(loadingLabel);

        new Thread(() -> {
            List<Comment> allComments = commentService.getCommentsByPost(currentPost.getId());

            Platform.runLater(() -> {
                commentsContainer.getChildren().clear();

                if (repliesCountLabel != null) repliesCountLabel.setText("Replies (" + allComments.size() + ")");
                if (statsRepliesLabel != null) statsRepliesLabel.setText("💬 Replies: " + allComments.size());
                if (topCommentBadgeLabel != null) topCommentBadgeLabel.setText("💬 " + allComments.size() + " Comments");

                if (allComments.isEmpty()) {
                    Label emptyLabel = new Label("No comments yet. Be the first to share your thoughts!");
                    emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 14px;");
                    commentsContainer.getChildren().add(emptyLabel);
                    return;
                }

                // 1. Organize Comments by Parent ID
                List<Comment> rootComments = new ArrayList<>();
                Map<Integer, List<Comment>> repliesMap = new HashMap<>();

                for (Comment c : allComments) {
                    if (c.getParentId() == null || c.getParentId() == 0) {
                        rootComments.add(c);
                    } else {
                        repliesMap.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
                    }
                }

                // 2. Render Recursively
                for (Comment root : rootComments) {
                    renderCommentTree(root, repliesMap, 0);
                }

                if (scrollToBottomFlag && mainScrollPane != null) {
                    scrollToBottomFlag = false;
                    PauseTransition waitBeforeScroll = new PauseTransition(Duration.millis(250));
                    waitBeforeScroll.setOnFinished(ev -> mainScrollPane.setVvalue(mainScrollPane.getVmax()));
                    waitBeforeScroll.play();
                }
            });
        }).start();
    }

    // Recursively builds the comment cards and indents them based on depth
    private void renderCommentTree(Comment c, Map<Integer, List<Comment>> repliesMap, int depth) {
        boolean isCensored = c.getContent().equals("🚫 *[This comment was removed by a moderator for violating community guidelines]*");

        VBox commentCard = new VBox(8);
        commentCard.getStyleClass().add("card-bg");
        commentCard.setStyle("-fx-background-radius: 8; -fx-padding: 15;");

        // If it's a nested reply, add a left border to create a "thread" line
        if (depth > 0) {
            commentCard.setStyle("-fx-background-radius: 0 8 8 0; -fx-padding: 12 15; -fx-border-color: transparent transparent transparent #cbd5e1; -fx-border-width: 0 0 0 4;");
            commentCard.getStyleClass().add("bg-secondary");
        } else {
            commentCard.setStyle("-fx-background-radius: 8; -fx-padding: 15; -fx-border-color: #e2e8f0; -fx-border-radius: 8;");
            commentCard.getStyleClass().add("card-bg");
        }

        // --- Header ---
        HBox headerBox = new HBox(10);
        Label author = new Label(c.getAuthorName() != null ? c.getAuthorName() : "Student");
        author.setStyle("-fx-font-weight: bold;");
        author.getStyleClass().add("text-primary");

        Label time = new Label(depth > 0 ? "• Reply" : "• Comment");
        time.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        headerBox.getChildren().addAll(author, time);

        // --- Content ---
        Label content = new Label(c.getContent());
        content.setWrapText(true);
        if (isCensored) {
            content.setStyle("-fx-text-fill: #64748b; -fx-font-size: 14px; -fx-font-style: italic;");
        } else {
            content.setStyle("-fx-font-size: 14px;");
            content.getStyleClass().add("text-secondary");
        }

        // --- Image ---
        VBox imageBox = new VBox();
        if (!isCensored && c.getImageName() != null && !c.getImageName().isEmpty()) {
            File imgFile = new File("C:/xampp/htdocs/projet dev/Pi_web/public/uploads/comments/" + c.getImageName());
            if (imgFile.exists()) {
                try {
                    ImageView commentImgView = new ImageView(new Image(imgFile.toURI().toString(), true));
                    commentImgView.setFitWidth(350); commentImgView.setPreserveRatio(true);
                    imageBox.setStyle("-fx-padding: 10 0 5 0;"); imageBox.getChildren().add(commentImgView);
                } catch (Exception e) {}
            }
        }

        // --- Action Buttons ---
        HBox actionsBox = new HBox(10);
        actionsBox.setAlignment(Pos.CENTER_RIGHT);

        if (!isCensored && !currentPost.isLocked()) {
            // 🔥 Reply Button (Always visible unless post is locked)
            Button replyBtn = new Button("↩ Reply");
            replyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-cursor: hand; -fx-font-size: 12px; -fx-font-weight: bold;");
            replyBtn.setOnMouseEntered(e -> replyBtn.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #3b82f6; -fx-cursor: hand; -fx-font-size: 12px; -fx-font-weight: bold; -fx-background-radius: 4;"));
            replyBtn.setOnMouseExited(e -> replyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-cursor: hand; -fx-font-size: 12px; -fx-font-weight: bold;"));
            replyBtn.setOnAction(e -> handleReplyToComment(c));
            actionsBox.getChildren().add(replyBtn);

            if (c.getAuthorId() == currentUserId) {
                Button editBtn = new Button("Edit"); editBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #3b82f6; -fx-cursor: hand; -fx-font-size: 12px; -fx-font-weight: bold;");
                editBtn.setOnAction(e -> handleEditComment(c));
                Button deleteBtn = new Button("Delete"); deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ef4444; -fx-cursor: hand; -fx-font-size: 12px; -fx-font-weight: bold;");
                deleteBtn.setOnAction(e -> handleDeleteComment(c));
                actionsBox.getChildren().addAll(editBtn, deleteBtn);
            } else {
                Button reportBtn = new Button("🚩 Report"); reportBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ef4444; -fx-cursor: hand; -fx-font-size: 12px; -fx-font-weight: bold;");
                reportBtn.setOnAction(e -> showReportDialog("COMMENT", c.getId()));
                actionsBox.getChildren().add(reportBtn);
            }
        }

        commentCard.getChildren().addAll(headerBox, content, imageBox);
        if (!actionsBox.getChildren().isEmpty()) commentCard.getChildren().add(actionsBox);

        // Apply Indentation based on Depth
        int indent = Math.min(depth * 40, 120); // Cap indentation so it doesn't squish too far
        VBox.setMargin(commentCard, new Insets(0, 0, 15, indent));

        commentsContainer.getChildren().add(commentCard);
        applyDarkThemeIfNeed(commentCard); // Apply dark mode to dynamic comment

        // Recursively render children
        List<Comment> children = repliesMap.get(c.getId());
        if (children != null) {
            for (Comment child : children) {
                renderCommentTree(child, repliesMap, depth + 1);
            }
        }
    }

    // 🔥 NEW: Pops open a dialog to write a nested reply
    private void handleReplyToComment(Comment parentComment) {
        if (currentPost.isLocked()) return;

        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.UTILITY);
        dialogStage.setTitle("Reply to " + (parentComment.getAuthorName() != null ? parentComment.getAuthorName() : "Student"));

        VBox root = new VBox(15);
        root.setStyle("-fx-background-color: white; -fx-padding: 25; -fx-border-radius: 8; -fx-background-radius: 8;");
        root.getStyleClass().add("card-bg");

        Label title = new Label("↩ Replying to " + (parentComment.getAuthorName() != null ? parentComment.getAuthorName() : "Student"));
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: 900;");
        title.getStyleClass().add("text-primary");

        TextArea textArea = new TextArea();
        textArea.setPromptText("Write your reply here...");
        textArea.setWrapText(true);
        textArea.setPrefRowCount(4);
        textArea.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-radius: 6; -fx-font-size: 14px; -fx-padding: 5;");

        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        cancelBtn.setOnAction(e -> dialogStage.close());

        Button postBtn = new Button("Post Reply");
        postBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        postBtn.setOnAction(e -> {
            String text = textArea.getText();
            if (text == null || text.trim().length() < 2) {
                textArea.setStyle("-fx-background-color: #fef2f2; -fx-border-color: #ef4444; -fx-border-radius: 6;");
                return;
            }

            // Create comment with parentId set to the target comment!
            Comment reply = new Comment(text, currentPost.getId(), currentUserId, parentComment.getId());
            commentService.ajouter(reply);

            dialogStage.close();
            scrollToBottomFlag = true; // Auto scroll down
            loadComments();
        });

        buttonBox.getChildren().addAll(cancelBtn, postBtn);
        root.getChildren().addAll(title, textArea, buttonBox);

        Scene scene = new Scene(root, 500, 250);
        dialogStage.setScene(scene);
        applyDarkThemeIfNeed(root); // Async Dark mode sync
        dialogStage.showAndWait();
    }
    // =========================================================================

    @FXML void handleBack(ActionEvent event) { controllers.NovaDashboardController.loadPage("/views/forum/forum_feed.fxml"); }
    @FXML void handleSavePost(ActionEvent event) { /* Keep logic */ }
    @FXML void handleSummarizePost(ActionEvent event) { /* Keep logic */ }
    @FXML void handleReportPost(ActionEvent event) { /* Keep logic */ }
    @FXML void handleLockPost(ActionEvent event) { /* Keep logic */ }
    @FXML void handleDeletePost(ActionEvent event) { /* Keep logic */ }
    @FXML void handleUploadCommentImage(ActionEvent event) { /* Keep logic */ }
    @FXML void handleSubmitComment(ActionEvent event) { /* Keep logic */ }
    @FXML void handleEditPost(ActionEvent event) {
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
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void updateLockUI() { /* Keep logic */ }
    private void handleEditComment(Comment c) { /* Keep logic */ }
    private void handleDeleteComment(Comment c) { /* Keep logic */ }
    private void openForumActivity(int userId, String username) { /* Keep logic */ }
    private void showReportDialog(String targetType, int targetId) { /* Keep logic */ }
    private void showAlert(Alert.AlertType type, String title, String content) { /* Keep logic */ }
}