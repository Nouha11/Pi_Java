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

    private final String SIDEBAR_IDLE = "-fx-padding: 10 15; -fx-background-radius: 8; -fx-cursor: hand; -fx-background-color: transparent;";
    private final String SIDEBAR_HOVER = "-fx-padding: 10 15; -fx-background-radius: 8; -fx-cursor: hand; -fx-background-color: #f8fafc;";
    private final String SIDEBAR_ACTIVE = "-fx-padding: 10 15; -fx-background-radius: 8; -fx-cursor: hand; -fx-background-color: #eff6ff;";

    private final String[] spaceColors = {"#10b981", "#0ea5e9", "#f59e0b", "#8b5cf6", "#ec4899", "#f43f5e"};

    @FXML
    public void initialize() {
        currentUserId = utils.UserSession.getInstance().getUserId();
        activeSidebarBtn = btnHome;

        Set<Integer> dbSavedPosts = postService.getSavedPostsForUser(currentUserId);
        utils.ForumSession.savedPostsPerUser.put(currentUserId, dbSavedPosts);

        loadSpacesIntoSidebar();
        loadAllPosts();
    }

    private void setSidebarStyle(HBox box, String bgColor) {
        if (box == null) return;
        box.setPadding(new Insets(10, 15, 10, 15));
        box.setStyle("-fx-background-radius: 8; -fx-cursor: hand; -fx-background-color: " + bgColor + ";");
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

        if (lblBannerTitle != null) lblBannerTitle.setText("NOVA / Home");
        if (lblBannerDesc != null) lblBannerDesc.setText("All discussions across all spaces.");
        if (bannerIcon != null) bannerIcon.setFill(Color.web("#2563eb"));

        refreshFeed();
    }

    @FXML
    void handleShowPopular(MouseEvent event) {
        setActiveSidebarButton(btnPopular);
        currentSpaceFilterId = null;
        currentTagFilter = null;
        showSavedOnly = false;
        setSortMode("TOP", btnSortTop);

        if (lblBannerTitle != null) lblBannerTitle.setText("NOVA / Popular");
        if (lblBannerDesc != null) lblBannerDesc.setText("The most upvoted discussions right now.");
        if (bannerIcon != null) bannerIcon.setFill(Color.web("#f59e0b"));

        refreshFeed();
    }

    @FXML
    void handleShowSavedPosts(MouseEvent event) {
        setActiveSidebarButton(btnSaved);
        currentSpaceFilterId = null;
        currentTagFilter = null;
        showSavedOnly = true;
        currentPage = 1;

        if (lblBannerTitle != null) lblBannerTitle.setText("NOVA / Saved Posts");
        if (lblBannerDesc != null) lblBannerDesc.setText("Your personal bookmarks and saved resources.");
        if (bannerIcon != null) bannerIcon.setFill(Color.web("#8b5cf6"));

        refreshFeed();
    }

    private void filterByTag(String tagName) {
        setActiveSidebarButton(null);
        currentSpaceFilterId = null;
        showSavedOnly = false;
        currentTagFilter = tagName.toLowerCase();
        currentPage = 1;

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
        String inactiveStyle = "-fx-padding: 8 15; -fx-text-fill: #64748b; -fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand; -fx-background-color: transparent;";
        String activeStyle = "-fx-padding: 8 15; -fx-text-fill: #2563eb; -fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand; -fx-background-color: #eff6ff; -fx-background-radius: 6;";

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
            final HBox spaceRow = new HBox(12);

            spaceRow.setAlignment(Pos.CENTER_LEFT);
            setSidebarStyle(spaceRow, "transparent");

            String colorHex = spaceColors[currentSpace.getId() % spaceColors.length];
            Circle dot = new Circle(5, Color.web(colorHex));
            Label nameLabel = new Label(currentSpace.getName());
            nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569; -fx-font-size: 14px;");

            spaceRow.getChildren().addAll(dot, nameLabel);

            spaceRow.setOnMouseClicked(e -> {
                setActiveSidebarButton(spaceRow);
                currentSpaceFilterId = currentSpace.getId();
                currentTagFilter = null;
                showSavedOnly = false;
                currentPage = 1;

                lblBannerTitle.setText("NOVA / " + currentSpace.getName());
                lblBannerDesc.setText(currentSpace.getDescription() != null ? currentSpace.getDescription() : "Welcome to the " + currentSpace.getName() + " community.");
                bannerIcon.setFill(Color.web(colorHex));

                refreshFeed();
            });

            spaceRow.setOnMouseEntered(e -> {
                if (activeSidebarBtn != spaceRow) setSidebarStyle(spaceRow, "#f8fafc");
            });
            spaceRow.setOnMouseExited(e -> {
                if (activeSidebarBtn != spaceRow) setSidebarStyle(spaceRow, "transparent");
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

        if (topTags.isEmpty()) {
            Label emptyLbl = new Label("No trending topics yet.");
            emptyLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px; -fx-font-style: italic;");
            trendingTagsContainer.getChildren().add(emptyLbl);
            return;
        }

        for (Map.Entry<String, Long> entry : topTags) {
            final String tagName = entry.getKey();
            final HBox tagBox = new HBox(12);
            tagBox.setAlignment(Pos.CENTER_LEFT);

            if (currentTagFilter != null && currentTagFilter.equals(tagName)) {
                setSidebarStyle(tagBox, "#eff6ff");
                activeSidebarBtn = tagBox;
            } else {
                setSidebarStyle(tagBox, "transparent");
            }

            Label hashLbl = new Label("#");
            hashLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #94a3b8; -fx-font-size: 16px;");

            Label lbl = new Label(tagName);
            lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569; -fx-font-size: 14px;");

            tagBox.getChildren().addAll(hashLbl, lbl);

            tagBox.setOnMouseClicked(e -> filterByTag(tagName));

            tagBox.setOnMouseEntered(e -> {
                if (activeSidebarBtn != tagBox) setSidebarStyle(tagBox, "#f8fafc");
            });
            tagBox.setOnMouseExited(e -> {
                if (activeSidebarBtn != tagBox) setSidebarStyle(tagBox, "transparent");
            });

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

        if ("NEW".equals(currentSortMode)) {
            filteredPosts.sort((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()));
        } else if ("TOP".equals(currentSortMode)) {
            filteredPosts.sort((p1, p2) -> Integer.compare(p2.getUpvotes(), p1.getUpvotes()));
        } else {
            filteredPosts.sort((p1, p2) -> Double.compare(p2.getHotScore(), p1.getHotScore()));
        }

        int totalPosts = filteredPosts.size();

        int calculatedPages = (int) Math.ceil((double) totalPosts / POSTS_PER_PAGE);
        if (calculatedPages == 0) calculatedPages = 1;
        final int finalTotalPages = calculatedPages;

        if (currentPage > finalTotalPages) currentPage = finalTotalPages;
        if (currentPage < 1) currentPage = 1;

        int fromIndex = (currentPage - 1) * POSTS_PER_PAGE;
        int toIndex = Math.min(fromIndex + POSTS_PER_PAGE, totalPosts);

        final List<Post> pagePosts = filteredPosts.subList(fromIndex, toIndex);

        postsContainer.getChildren().clear();

        if (pagePosts.isEmpty()) {
            Label emptyLbl = new Label("No posts found. Be the first to start a discussion!");
            emptyLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 16px; -fx-padding: 30;");
            postsContainer.getChildren().add(emptyLbl);
            renderPagination(finalTotalPages);
            return;
        }

        new Thread(() -> {
            Map<Integer, Integer> commentCounts = new HashMap<>();
            for (Post p : pagePosts) {
                commentCounts.put(p.getId(), commentService.getCommentsByPost(p.getId()).size());
            }

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
        if (paginationContainer == null) return;
        paginationContainer.getChildren().clear();
        if (totalPages <= 1) return;

        Label btnPrev = new Label("Prev");
        btnPrev.setStyle(currentPage > 1
                ? "-fx-padding: 8 16; -fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand; -fx-text-fill: #475569; -fx-font-weight: bold;"
                : "-fx-padding: 8 16; -fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: #94a3b8; -fx-font-weight: bold;");
        if (currentPage > 1) {
            btnPrev.setOnMouseClicked(e -> {
                currentPage--;
                refreshFeed();
            });
        }
        paginationContainer.getChildren().add(btnPrev);

        for (int i = 1; i <= totalPages; i++) {
            final int pageNum = i;
            Label btnPage = new Label(String.valueOf(i));

            if (i == currentPage) {
                btnPage.setStyle("-fx-padding: 8 14; -fx-background-color: #eff6ff; -fx-border-color: #2563eb; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: default; -fx-text-fill: #2563eb; -fx-font-weight: bold;");
            } else {
                btnPage.setStyle("-fx-padding: 8 14; -fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand; -fx-text-fill: #475569; -fx-font-weight: bold;");
                btnPage.setOnMouseClicked(e -> {
                    currentPage = pageNum;
                    refreshFeed();
                });
            }
            paginationContainer.getChildren().add(btnPage);
        }

        Label btnNext = new Label("Next");
        btnNext.setStyle(currentPage < totalPages
                ? "-fx-padding: 8 16; -fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand; -fx-text-fill: #475569; -fx-font-weight: bold;"
                : "-fx-padding: 8 16; -fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: #94a3b8; -fx-font-weight: bold;");
        if (currentPage < totalPages) {
            btnNext.setOnMouseClicked(e -> {
                currentPage++;
                refreshFeed();
            });
        }
        paginationContainer.getChildren().add(btnNext);
    }

    private Node createPostCard(final Post post, final Set<Integer> mySavedPosts, final int preloadedCommentCount) {
        VBox voteBox = new VBox(5);
        voteBox.setAlignment(Pos.TOP_CENTER);
        voteBox.setPadding(new Insets(12, 12, 0, 12));
        voteBox.setStyle("-fx-background-color: #f8fafc; -fx-border-color: transparent #e2e8f0 transparent transparent; -fx-border-width: 0 1 0 0; -fx-background-radius: 8 0 0 8;");

        Label upArrow = new Label("↑");
        Label voteCount = new Label(String.valueOf(post.getUpvotes()));
        voteCount.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1e293b;");
        Label downArrow = new Label("↓");

        String defaultStyle = "-fx-font-size: 16px; -fx-text-fill: #94a3b8; -fx-font-weight: bold; -fx-cursor: hand;";
        String activeUpStyle = "-fx-font-size: 16px; -fx-text-fill: #ff4500; -fx-font-weight: bold; -fx-cursor: hand;";
        String activeDownStyle = "-fx-font-size: 16px; -fx-text-fill: #3b82f6; -fx-font-weight: bold; -fx-cursor: hand;";

        upArrow.setStyle(post.getMyVote() == 1 ? activeUpStyle : defaultStyle);
        downArrow.setStyle(post.getMyVote() == -1 ? activeDownStyle : defaultStyle);

        upArrow.setOnMouseClicked(e -> {
            e.consume();
            if (post.getMyVote() != 1) {
                int change = (post.getMyVote() == -1) ? 2 : 1;
                postService.updateUpvotes(post.getId(), change);
                post.setUpvotes(post.getUpvotes() + change);
                post.setMyVote(1);
                utils.ForumSession.upvotedPosts.add(post.getId());
                utils.ForumSession.downvotedPosts.remove(post.getId());
            } else {
                postService.updateUpvotes(post.getId(), -1);
                post.setUpvotes(post.getUpvotes() - 1);
                post.setMyVote(0);
                utils.ForumSession.upvotedPosts.remove(post.getId());
            }
            double scrollPos = mainScrollPane.getVvalue();
            refreshFeed();
            Platform.runLater(() -> mainScrollPane.setVvalue(scrollPos));
        });

        downArrow.setOnMouseClicked(e -> {
            e.consume();
            if (post.getMyVote() != -1) {
                int change = (post.getMyVote() == 1) ? -2 : -1;
                postService.updateUpvotes(post.getId(), change);
                post.setUpvotes(post.getUpvotes() + change);
                post.setMyVote(-1);
                utils.ForumSession.downvotedPosts.add(post.getId());
                utils.ForumSession.upvotedPosts.remove(post.getId());
            } else {
                postService.updateUpvotes(post.getId(), 1);
                post.setUpvotes(post.getUpvotes() + 1);
                post.setMyVote(0);
                utils.ForumSession.downvotedPosts.remove(post.getId());
            }
            double scrollPos = mainScrollPane.getVvalue();
            refreshFeed();
            Platform.runLater(() -> mainScrollPane.setVvalue(scrollPos));
        });

        voteBox.getChildren().addAll(upArrow, voteCount, downArrow);

        VBox contentBox = new VBox(10);
        contentBox.setPadding(new Insets(12, 20, 12, 20));
        HBox.setHgrow(contentBox, Priority.ALWAYS);

        HBox headerRow = new HBox(6);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        Label dot = new Label("🟢");
        dot.setStyle("-fx-font-size: 8px; -fx-text-fill: #22c55e;");
        Label spaceLabel = new Label("NOVA/ " + (post.getSpaceName() != null ? post.getSpaceName() : "General"));
        spaceLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #0f172a;");

        // ==========================================
        // 🔥 THE EVENT BUBBLING FIX 🔥
        // ==========================================
        final int finalAuthorId = post.getAuthorId();
        final String finalAuthorName = post.getAuthorName() != null ? post.getAuthorName() : "Unknown";

        Label prefixLabel = new Label(" •  Posted by ");
        prefixLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        Label authorLabel = new Label("u/" + finalAuthorName);
        authorLabel.setStyle("-fx-text-fill: #3b82f6; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
        authorLabel.setOnMouseEntered(e -> authorLabel.setUnderline(true));
        authorLabel.setOnMouseExited(e -> authorLabel.setUnderline(false));

        // Use setOnMousePressed to stop the click from reaching the post card
        authorLabel.setOnMousePressed(e -> {
            e.consume();
            openUserProfile(finalAuthorId, finalAuthorName);
        });

        HBox authorBox = new HBox(prefixLabel, authorLabel);
        authorBox.setAlignment(Pos.CENTER_LEFT);
        headerRow.getChildren().addAll(dot, spaceLabel, authorBox);
        // ==========================================

        Label titleLabel = new Label();
        if (post.isLocked()) {
            titleLabel.setText("🔒 " + post.getTitle());
            titleLabel.setStyle("-fx-text-fill: #94a3b8;");
        } else {
            titleLabel.setText(post.getTitle());
            titleLabel.setStyle("-fx-text-fill: #0f172a;");
        }
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 17));

        HBox postTagsBox = new HBox(6);
        if (post.getTags() != null && !post.getTags().isEmpty()) {
            String[] tags = post.getTags().split(",");
            for (String tag : tags) {
                final String cleanTag = tag.trim();
                if (!cleanTag.isEmpty()) {
                    Label tagLbl = new Label("#" + cleanTag);
                    tagLbl.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-padding: 2 6; -fx-background-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;");
                    tagLbl.setOnMouseClicked(e -> {
                        e.consume();
                        filterByTag(cleanTag);
                    });
                    postTagsBox.getChildren().add(tagLbl);
                }
            }
        }

        Label contentLabel = new Label(post.getContent());
        contentLabel.setWrapText(true);
        contentLabel.setStyle("-fx-text-fill: #334155; -fx-font-size: 14px; -fx-line-spacing: 2px;");

        VBox attachmentBox = new VBox();
        if (post.getImageName() != null && !post.getImageName().trim().isEmpty()) {
            File imgFile = new File("C:/xampp/htdocs/projet dev/Pi_web/public/uploads/posts/" + post.getImageName());
            if (!imgFile.exists())
                imgFile = new File("C:/xampp/htdocs/projet dev/Pi_web/public/uploads/forum/" + post.getImageName());

            if (imgFile.exists()) {
                try {
                    Image img = new Image(imgFile.toURI().toString(), true);
                    ImageView imageView = new ImageView(img);
                    imageView.setFitWidth(550);
                    imageView.setPreserveRatio(true);
                    attachmentBox.setStyle("-fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-padding: 2; -fx-background-color: white; -fx-background-radius: 6;");
                    attachmentBox.getChildren().add(imageView);
                    VBox.setMargin(attachmentBox, new Insets(10, 0, 5, 0));
                } catch (Exception e) { }
            }
        }

        HBox footerRow = new HBox(20);
        footerRow.setPadding(new Insets(10, 0, 0, 0));

        Label commentsLabel = new Label("💬 " + preloadedCommentCount + " Comments");
        commentsLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4 8;");

        boolean isSaved = mySavedPosts.contains(post.getId());

        final Label saveLabel = new Label(isSaved ? "🔖 Saved" : "🔖 Save");
        saveLabel.setStyle("-fx-text-fill: " + (isSaved ? "#2563eb" : "#64748b") + "; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4 8; -fx-cursor: hand;");

        saveLabel.setOnMouseClicked(e -> {
            e.consume();
            if (mySavedPosts.contains(post.getId())) {
                mySavedPosts.remove(post.getId());
                postService.unsavePost(currentUserId, post.getId());

                saveLabel.setText("🔖 Save");
                saveLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4 8; -fx-cursor: hand;");
            } else {
                mySavedPosts.add(post.getId());
                postService.savePost(currentUserId, post.getId());

                saveLabel.setText("🔖 Saved");
                saveLabel.setStyle("-fx-text-fill: #2563eb; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4 8; -fx-cursor: hand;");
            }
            if (showSavedOnly) refreshFeed();
        });

        Label shareLabel = new Label("🔗 Share");
        shareLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4 8;");

        footerRow.getChildren().addAll(commentsLabel, saveLabel, shareLabel);

        contentBox.getChildren().addAll(headerRow, titleLabel);
        if (!postTagsBox.getChildren().isEmpty()) contentBox.getChildren().add(postTagsBox);
        contentBox.getChildren().add(contentLabel);
        if (!attachmentBox.getChildren().isEmpty()) contentBox.getChildren().add(attachmentBox);
        contentBox.getChildren().add(footerRow);

        HBox mainCard = new HBox(0);
        String idleStyle = "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;";
        String hoverStyle = "-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;";

        mainCard.setStyle(idleStyle);
        mainCard.setOnMouseEntered(e -> mainCard.setStyle(hoverStyle));
        mainCard.setOnMouseExited(e -> mainCard.setStyle(idleStyle));

        mainCard.getChildren().addAll(voteBox, contentBox);
        mainCard.setOnMouseClicked(e -> openPostDetails(post));

        return mainCard;
    }

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

            Stage popupStage = new Stage();
            popupStage.setTitle(username + " - Forum Activity");
            popupStage.setScene(new Scene(root, 450, 550));
            popupStage.initModality(Modality.APPLICATION_MODAL);
            popupStage.show();
        } catch (Exception e) {
            System.err.println("🚨 Error loading Forum Activity: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void openPostDetails(Post post) {
        try {
            utils.ForumSession.currentPost = post;
            controllers.NovaDashboardController.loadPage("/views/forum/student/post_details.fxml");
        } catch (Exception e) {
            System.err.println("🚨 Error loading Post Details: " + e.getMessage());
            e.printStackTrace();
        }
    }
}