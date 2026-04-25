package controllers.forum;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.forum.Post;
import models.forum.Space;
import services.forum.CommentService;
import services.forum.PostService;
import services.forum.SpaceService;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class ForumFeedController {

    @FXML private ScrollPane mainScrollPane;
    @FXML private VBox postsContainer;
    @FXML private VBox spacesContainer;
    @FXML private VBox trendingTagsContainer;
    @FXML private HBox paginationContainer;

    @FXML private HBox btnHome;
    @FXML private HBox btnPopular;
    @FXML private HBox btnSaved;
    @FXML private HBox btnSandbox;

    @FXML private Label btnSortHot;
    @FXML private Label btnSortNew;
    @FXML private Label btnSortTop;

    @FXML private TextField searchField;

    @FXML private Label lblBannerTitle;
    @FXML private Label lblBannerDesc;
    @FXML private Circle bannerIcon;

    private PostService postService = new PostService();
    private SpaceService spaceService = new SpaceService();
    private CommentService commentService = new CommentService();

    private List<Post> allPostsCache;
    private int currentUserId;

    private Integer currentSpaceFilterId = null;
    private String currentTagFilter = null;
    private boolean showSavedOnly = false;

    private HBox activeSidebarBtn;
    private String currentSortMode = "HOT";

    private int currentPage = 1;
    private final int POSTS_PER_PAGE = 5;

    // Compact Sidebar Styles
    private final String SIDEBAR_IDLE = "-fx-padding: 6 12; -fx-background-radius: 6; -fx-cursor: hand; -fx-background-color: transparent;";
    private final String SIDEBAR_HOVER = "-fx-padding: 6 12; -fx-background-radius: 6; -fx-cursor: hand; -fx-background-color: #f8fafc;";
    private final String SIDEBAR_ACTIVE = "-fx-padding: 6 12; -fx-background-radius: 6; -fx-cursor: hand; -fx-background-color: #eff6ff;";

    private final String[] spaceColors = {"#10b981", "#0ea5e9", "#f59e0b", "#8b5cf6", "#ec4899", "#f43f5e"};

    @FXML
    public void initialize() {
        currentUserId = utils.UserSession.getInstance().getUserId();
        activeSidebarBtn = btnHome;

        Set<Integer> dbSavedPosts = postService.getSavedPostsForUser(currentUserId);
        utils.ForumSession.savedPostsPerUser.put(currentUserId, dbSavedPosts);

        loadSpacesIntoSidebar();
        loadAllPosts();

        if (searchField != null) {
            searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                String query = newValue.trim();

                if (!query.isEmpty()) {
                    lblBannerTitle.setText("NOVA / Search");
                    lblBannerDesc.setText("Searching for: \"" + query + "\"");
                    bannerIcon.setFill(Color.web("#8b5cf6"));
                    setActiveSidebarButton(null);
                } else {
                    lblBannerTitle.setText("NOVA / Home");
                    lblBannerDesc.setText("All discussions across all spaces.");
                    bannerIcon.setFill(Color.web("#2563eb"));
                    setActiveSidebarButton(btnHome);
                }

                new Thread(() -> {
                    if (query.isEmpty()) {
                        allPostsCache = postService.afficher();
                    } else {
                        allPostsCache = postService.searchPosts(query);
                    }

                    Platform.runLater(() -> {
                        currentPage = 1;
                        refreshFeed();
                    });
                }).start();
            });
        }
    }

    private void setSidebarStyle(HBox box, String bgColor) {
        if (box == null) return;
        box.setPadding(new Insets(6, 12, 6, 12));
        box.setStyle("-fx-background-radius: 6; -fx-cursor: hand; -fx-background-color: " + bgColor + ";");
    }

    private void setActiveSidebarButton(HBox buttonClicked) {
        setSidebarStyle(btnHome, "transparent");
        setSidebarStyle(btnPopular, "transparent");
        setSidebarStyle(btnSaved, "transparent");
        setSidebarStyle(btnSandbox, "transparent");

        for (Node node : spacesContainer.getChildren()) {
            if(node instanceof HBox) setSidebarStyle((HBox) node, "transparent");
        }

        if (trendingTagsContainer != null) {
            for (Node node : trendingTagsContainer.getChildren()) {
                if(node instanceof HBox) setSidebarStyle((HBox) node, "transparent");
            }
        }

        if (buttonClicked != null) {
            setSidebarStyle(buttonClicked, "#eff6ff");
        }
        activeSidebarBtn = buttonClicked;
    }

    @FXML
    void handleShowAllPosts(MouseEvent event) {
        setActiveSidebarButton(btnHome);
        currentSpaceFilterId = null;
        currentTagFilter = null;
        showSavedOnly = false;
        currentPage = 1;
        if (searchField != null) searchField.clear();
        refreshFeed();
    }

    @FXML
    void handleShowPopular(MouseEvent event) {
        setActiveSidebarButton(btnPopular);
        currentSpaceFilterId = null;
        currentTagFilter = null;
        showSavedOnly = false;
        setSortMode("TOP", btnSortTop);
        if (searchField != null) searchField.clear();
        refreshFeed();
    }

    @FXML
    void handleShowSavedPosts(MouseEvent event) {
        setActiveSidebarButton(btnSaved);
        currentSpaceFilterId = null;
        currentTagFilter = null;
        showSavedOnly = true;
        currentPage = 1;
        if (searchField != null) searchField.clear();
        refreshFeed();
    }

    // 🔥 RESTORED LOGIC FOR TAG FILTERING 🔥
    private void filterByTag(String tagName) {
        setActiveSidebarButton(null);
        currentSpaceFilterId = null;
        showSavedOnly = false;
        currentTagFilter = tagName.toLowerCase();
        currentPage = 1;

        if (searchField != null) searchField.clear();

        if (lblBannerTitle != null) lblBannerTitle.setText("NOVA / #" + tagName);
        if (lblBannerDesc != null) lblBannerDesc.setText("Exploring discussions tagged with #" + tagName);
        if (bannerIcon != null) bannerIcon.setFill(Color.web("#8b5cf6"));

        if (trendingTagsContainer != null) {
            for (Node node : trendingTagsContainer.getChildren()) {
                if (node instanceof HBox) {
                    HBox box = (HBox) node;
                    Label lbl = (Label) box.getChildren().get(1);
                    if (lbl.getText().equalsIgnoreCase(tagName)) {
                        setSidebarStyle(box, "#eff6ff");
                        activeSidebarBtn = box;
                    }
                }
            }
        }

        refreshFeed();
    }

    @FXML void handleSortHot(MouseEvent event) { setSortMode("HOT", btnSortHot); }
    @FXML void handleSortNew(MouseEvent event) { setSortMode("NEW", btnSortNew); }
    @FXML void handleSortTop(MouseEvent event) { setSortMode("TOP", btnSortTop); }

    private void setSortMode(String mode, Label activeBtn) {
        currentSortMode = mode;
        String inactiveStyle = "-fx-padding: 6 12; -fx-text-fill: #64748b; -fx-font-weight: bold; -fx-font-size: 13px; -fx-cursor: hand; -fx-background-color: transparent;";
        String activeStyle = "-fx-padding: 6 12; -fx-text-fill: #2563eb; -fx-font-weight: bold; -fx-font-size: 13px; -fx-cursor: hand; -fx-background-color: #eff6ff; -fx-background-radius: 4;";
        btnSortHot.setStyle(inactiveStyle);
        btnSortNew.setStyle(inactiveStyle);
        btnSortTop.setStyle(inactiveStyle);
        activeBtn.setStyle(activeStyle);
        currentPage = 1;
        refreshFeed();
    }

    private void loadSpacesIntoSidebar() {
        if (spacesContainer == null) return;
        spacesContainer.getChildren().clear();
        List<Space> spaces = spaceService.afficher();
        for (Space space : spaces) {
            final Space currentSpace = space;
            final HBox spaceRow = new HBox(8);
            spaceRow.setAlignment(Pos.CENTER_LEFT);
            setSidebarStyle(spaceRow, "transparent");
            String colorHex = spaceColors[currentSpace.getId() % spaceColors.length];
            Circle dot = new Circle(4, Color.web(colorHex));
            Label nameLabel = new Label(currentSpace.getName());
            nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569; -fx-font-size: 12px;");
            spaceRow.getChildren().addAll(dot, nameLabel);
            spaceRow.setOnMouseClicked(e -> {
                setActiveSidebarButton(spaceRow);
                currentSpaceFilterId = currentSpace.getId();
                currentTagFilter = null;
                showSavedOnly = false;
                currentPage = 1;
                if (searchField != null) searchField.clear();
                lblBannerTitle.setText("NOVA / " + currentSpace.getName());
                lblBannerDesc.setText(currentSpace.getDescription() != null ? currentSpace.getDescription() : "Welcome to the " + currentSpace.getName() + " community.");
                bannerIcon.setFill(Color.web(colorHex));
                refreshFeed();
            });
            spacesContainer.getChildren().add(spaceRow);
        }
    }

    private void loadAllPosts() {
        new Thread(() -> {
            allPostsCache = postService.afficher();
            Platform.runLater(() -> {
                calculateTrendingTags();
                refreshFeed();
            });
        }).start();
    }

    private void calculateTrendingTags() {
        if (trendingTagsContainer == null || allPostsCache == null) return;
        trendingTagsContainer.getChildren().clear();
        Map<String, Long> tagCounts = allPostsCache.stream()
                .filter(p -> p.getTags() != null && !p.getTags().trim().isEmpty())
                .flatMap(p -> Arrays.stream(p.getTags().split(",")))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.groupingBy(String::toLowerCase, Collectors.counting()));
        List<Map.Entry<String, Long>> topTags = tagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());
        for (Map.Entry<String, Long> entry : topTags) {
            final String tagName = entry.getKey();
            final HBox tagBox = new HBox(8);
            tagBox.setAlignment(Pos.CENTER_LEFT);
            setSidebarStyle(tagBox, "transparent");
            Label hashLbl = new Label("#");
            hashLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #94a3b8; -fx-font-size: 13px;");
            Label lbl = new Label(tagName);
            lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569; -fx-font-size: 12px;");
            tagBox.getChildren().addAll(hashLbl, lbl);
            tagBox.setOnMouseClicked(e -> filterByTag(tagName));
            trendingTagsContainer.getChildren().add(tagBox);
        }
    }

    private void refreshFeed() {
        if (allPostsCache == null || postsContainer == null) return;
        final Set<Integer> mySavedPosts = utils.ForumSession.savedPostsPerUser.computeIfAbsent(currentUserId, k -> new java.util.HashSet<>());
        List<Post> filteredPosts = allPostsCache.stream()
                .filter(p -> currentSpaceFilterId == null || (p.getSpaceId() != null && p.getSpaceId().equals(currentSpaceFilterId)))
                .filter(p -> currentTagFilter == null || (p.getTags() != null && p.getTags().toLowerCase().contains(currentTagFilter)))
                .filter(p -> !showSavedOnly || mySavedPosts.contains(p.getId()))
                .collect(Collectors.toList());

        if ("NEW".equals(currentSortMode)) filteredPosts.sort((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()));
        else if ("TOP".equals(currentSortMode)) filteredPosts.sort((p1, p2) -> Integer.compare(p2.getUpvotes(), p1.getUpvotes()));
        else filteredPosts.sort((p1, p2) -> Double.compare(p2.getHotScore(), p1.getHotScore()));

        int totalPosts = filteredPosts.size();
        int finalTotalPages = Math.max(1, (int) Math.ceil((double) totalPosts / POSTS_PER_PAGE));
        if (currentPage > finalTotalPages) currentPage = finalTotalPages;

        int fromIndex = (currentPage - 1) * POSTS_PER_PAGE;
        int toIndex = Math.min(fromIndex + POSTS_PER_PAGE, totalPosts);
        final List<Post> pagePosts = filteredPosts.subList(fromIndex, toIndex);

        postsContainer.getChildren().clear();
        if (pagePosts.isEmpty()) {
            Label emptyLbl = new Label("No posts found. Try a different search or be the first to start a discussion!");
            emptyLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px; -fx-padding: 20;");
            postsContainer.getChildren().add(emptyLbl);
            renderPagination(finalTotalPages);
            return;
        }

        new Thread(() -> {
            Map<Integer, Integer> commentCounts = new HashMap<>();
            for (Post p : pagePosts) commentCounts.put(p.getId(), commentService.getCommentsByPost(p.getId()).size());
            Platform.runLater(() -> {
                for (Post p : pagePosts) {
                    if (utils.ForumSession.upvotedPosts.contains(p.getId())) p.setMyVote(1);
                    else if (utils.ForumSession.downvotedPosts.contains(p.getId())) p.setMyVote(-1);
                    postsContainer.getChildren().add(createPostCard(p, mySavedPosts, commentCounts.get(p.getId())));
                }
                renderPagination(finalTotalPages);
                mainScrollPane.setVvalue(0.0);
            });
        }).start();
    }

    private void renderPagination(int totalPages) {
        if (paginationContainer == null || totalPages <= 1) {
            if(paginationContainer != null) paginationContainer.getChildren().clear();
            return;
        }
        paginationContainer.getChildren().clear();
        for (int i = 1; i <= totalPages; i++) {
            final int pageNum = i;
            Label btnPage = new Label(String.valueOf(i));
            btnPage.setStyle(i == currentPage ?
                    "-fx-padding: 6 10; -fx-background-color: #eff6ff; -fx-border-color: #2563eb; -fx-border-radius: 4; -fx-text-fill: #2563eb; -fx-font-weight: bold; -fx-font-size: 12px;" :
                    "-fx-padding: 6 10; -fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 4; -fx-text-fill: #475569; -fx-cursor: hand; -fx-font-size: 12px;");
            btnPage.setOnMouseClicked(e -> { currentPage = pageNum; refreshFeed(); });
            paginationContainer.getChildren().add(btnPage);
        }
    }

    private Node createPostCard(final Post post, final Set<Integer> mySavedPosts, final int preloadedCommentCount) {
        VBox voteBox = new VBox(4);
        voteBox.setAlignment(Pos.TOP_CENTER);
        voteBox.setPadding(new Insets(10, 8, 0, 8));
        voteBox.setStyle("-fx-background-color: #f8fafc; -fx-border-color: transparent #e2e8f0 transparent transparent; -fx-border-width: 0 1 0 0; -fx-background-radius: 8 0 0 8;");

        Label upArrow = new Label("↑");
        Label voteCount = new Label(String.valueOf(post.getUpvotes()));
        voteCount.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");
        Label downArrow = new Label("↓");

        upArrow.setStyle(post.getMyVote() == 1 ? "-fx-text-fill: #ff4500; -fx-font-size: 15px; -fx-cursor: hand;" : "-fx-text-fill: #94a3b8; -fx-font-size: 15px; -fx-cursor: hand;");
        downArrow.setStyle(post.getMyVote() == -1 ? "-fx-text-fill: #3b82f6; -fx-font-size: 15px; -fx-cursor: hand;" : "-fx-text-fill: #94a3b8; -fx-font-size: 15px; -fx-cursor: hand;");

        upArrow.setOnMouseClicked(e -> handleVote(post, 1));
        downArrow.setOnMouseClicked(e -> handleVote(post, -1));
        voteBox.getChildren().addAll(upArrow, voteCount, downArrow);

        VBox contentBox = new VBox(6);
        contentBox.setPadding(new Insets(8, 12, 8, 12));
        HBox.setHgrow(contentBox, Priority.ALWAYS);

        HBox headerRow = new HBox(6);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        Label spaceLabel = new Label("NOVA/ " + (post.getSpaceName() != null ? post.getSpaceName() : "General"));
        spaceLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #0f172a;");

        final int finalAuthorId = post.getAuthorId();
        final String finalAuthorName = post.getAuthorName() != null ? post.getAuthorName() : "Unknown";
        Label authorLabel = new Label("u/" + finalAuthorName);
        authorLabel.setStyle("-fx-text-fill: #3b82f6; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;");
        authorLabel.setOnMousePressed(e -> { e.consume(); openUserProfile(finalAuthorId, finalAuthorName); });

        headerRow.getChildren().addAll(spaceLabel, new Label(" • "), authorLabel);

        Label titleLabel = new Label(post.getTitle());
        titleLabel.setStyle("-fx-text-fill: #0f172a; -fx-font-size: 15px; -fx-font-weight: bold;");

        Label contentLabel = new Label(post.getContent());
        contentLabel.setWrapText(true);
        contentLabel.setStyle("-fx-text-fill: #334155; -fx-font-size: 13px; -fx-line-spacing: 1px;");

        VBox attachmentBox = new VBox();
        if (post.getImageName() != null && !post.getImageName().trim().isEmpty()) {
            File imgFile = new File("C:/xampp/htdocs/projet dev/Pi_web/public/uploads/posts/" + post.getImageName());
            if (!imgFile.exists())
                imgFile = new File("C:/xampp/htdocs/projet dev/Pi_web/public/uploads/forum/" + post.getImageName());

            if (imgFile.exists()) {
                try {
                    Image img = new Image(imgFile.toURI().toString(), true);
                    ImageView iv = new ImageView(img);
                    iv.setFitHeight(180);
                    iv.setFitWidth(500);
                    iv.setPreserveRatio(true);
                    attachmentBox.setStyle("-fx-border-color: #e2e8f0; -fx-border-radius: 4; -fx-padding: 2;");
                    attachmentBox.getChildren().add(iv);
                } catch (Exception e) {}
            }
        }

        HBox footerRow = new HBox(12);
        footerRow.setPadding(new Insets(4, 0, 0, 0));
        Label commentsLabel = new Label("💬 " + preloadedCommentCount + " Replies");
        commentsLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px; -fx-font-weight: bold;");

        boolean isSaved = mySavedPosts.contains(post.getId());
        Label saveLabel = new Label(isSaved ? "🔖 Saved" : "🔖 Save");
        saveLabel.setStyle("-fx-text-fill: " + (isSaved ? "#2563eb" : "#64748b") + "; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
        saveLabel.setOnMouseClicked(e -> {
            e.consume();
            if (isSaved) { postService.unsavePost(currentUserId, post.getId()); mySavedPosts.remove(post.getId()); }
            else { postService.savePost(currentUserId, post.getId()); mySavedPosts.add(post.getId()); }
            refreshFeed();
        });

        footerRow.getChildren().addAll(commentsLabel, saveLabel);
        contentBox.getChildren().addAll(headerRow, titleLabel, contentLabel);
        if(!attachmentBox.getChildren().isEmpty()) contentBox.getChildren().add(attachmentBox);
        contentBox.getChildren().add(footerRow);

        HBox mainCard = new HBox(voteBox, contentBox);
        mainCard.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;");
        mainCard.setOnMouseClicked(e -> openPostDetails(post));
        return mainCard;
    }

    private void handleVote(Post post, int type) {
        if (post.getMyVote() == type) {
            postService.updateUpvotes(post.getId(), -type);
            post.setMyVote(0);
            utils.ForumSession.upvotedPosts.remove(post.getId());
            utils.ForumSession.downvotedPosts.remove(post.getId());
        } else {
            int change = (post.getMyVote() == -type) ? type * 2 : type;
            postService.updateUpvotes(post.getId(), change);
            post.setMyVote(type);
            if(type == 1) { utils.ForumSession.upvotedPosts.add(post.getId()); utils.ForumSession.downvotedPosts.remove(post.getId()); }
            else { utils.ForumSession.downvotedPosts.add(post.getId()); utils.ForumSession.upvotedPosts.remove(post.getId()); }
        }
        refreshFeed();
    }

    // 🔥 RESTORED LOGIC FOR BUTTONS 🔥
    @FXML
    void handleGoToCreatePost(MouseEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/forum/student/add_post.fxml"));
            Parent root = loader.load();
            Stage popupStage = new Stage();
            popupStage.setTitle("Create a New Post");
            popupStage.setScene(new Scene(root, 700, 600));
            popupStage.initModality(Modality.APPLICATION_MODAL);
            popupStage.showAndWait();
            loadAllPosts();
        } catch (Exception e) {
            System.err.println("🚨 Error loading Add Post: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    void handleOpenSandbox(MouseEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/forum/student/code_sandbox.fxml"));
            Parent root = loader.load();
            Stage popupStage = new Stage();
            popupStage.setTitle("NOVA Code Sandbox");
            popupStage.setScene(new Scene(root, 900, 700));
            popupStage.initModality(Modality.APPLICATION_MODAL);
            popupStage.showAndWait();
        } catch (Exception ex) {
            System.err.println("🚨 Error loading Code Sandbox: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void openUserProfile(int userId, String username) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/forum/student/forum_activity.fxml"));
            Parent root = loader.load();
            ForumActivityController controller = loader.getController();
            controller.loadUserData(userId, username);
            Stage stage = new Stage();
            stage.setTitle(username + " - Activity");
            stage.setScene(new Scene(root, 450, 550));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void openPostDetails(Post post) {
        utils.ForumSession.currentPost = post;
        controllers.NovaDashboardController.loadPage("/views/forum/student/post_details.fxml");
    }
}