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
import services.forum.PostService;
import services.forum.SpaceService;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class ForumFeedController {

    @FXML private ScrollPane mainScrollPane;
    @FXML private VBox postsContainer;
    @FXML private VBox spacesContainer;
    @FXML private HBox paginationContainer;

    @FXML private HBox btnHome;
    @FXML private HBox btnPopular;

    @FXML private Label lblBannerTitle;
    @FXML private Label lblBannerDesc;
    @FXML private Circle bannerIcon;

    private PostService postService = new PostService();
    private SpaceService spaceService = new SpaceService();

    private List<Post> allPostsCache;
    private Integer currentSpaceFilterId = null;
    private HBox activeSidebarBtn;

    // Pagination Variables
    private int currentPage = 1;
    private final int POSTS_PER_PAGE = 5; // Adjust this number to change how many posts show per page

    private final String[] spaceColors = {"#10b981", "#0ea5e9", "#f59e0b", "#8b5cf6", "#ec4899", "#f43f5e"};

    @FXML
    public void initialize() {
        activeSidebarBtn = btnHome;
        loadSpacesIntoSidebar();
        loadAllPosts();
    }

    private void setActiveSidebarButton(HBox buttonClicked) {
        btnHome.setStyle("-fx-padding: 10 15; -fx-background-radius: 8; -fx-cursor: hand; -fx-background-color: transparent;");
        btnPopular.setStyle("-fx-padding: 10 15; -fx-background-radius: 8; -fx-cursor: hand; -fx-background-color: transparent;");

        for (Node node : spacesContainer.getChildren()) {
            node.setStyle("-fx-padding: 8 15; -fx-background-radius: 8; -fx-cursor: hand; -fx-background-color: transparent;");
        }

        if (buttonClicked != null) {
            buttonClicked.setStyle("-fx-padding: 10 15; -fx-background-radius: 8; -fx-cursor: hand; -fx-background-color: #eff6ff;");
        }
        activeSidebarBtn = buttonClicked;
    }

    private void loadSpacesIntoSidebar() {
        if (spacesContainer == null) return;
        spacesContainer.getChildren().clear();
        List<Space> spaces = spaceService.afficher();

        for (Space space : spaces) {
            HBox spaceRow = new HBox(12);
            spaceRow.setAlignment(Pos.CENTER_LEFT);
            spaceRow.setStyle("-fx-padding: 8 15; -fx-background-radius: 8; -fx-cursor: hand; -fx-background-color: transparent;");

            String colorHex = spaceColors[space.getId() % spaceColors.length];
            Circle dot = new Circle(5, Color.web(colorHex));

            Label nameLabel = new Label(space.getName());
            nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569; -fx-font-size: 14px;");

            spaceRow.getChildren().addAll(dot, nameLabel);

            spaceRow.setOnMouseClicked(e -> {
                setActiveSidebarButton(spaceRow);
                currentSpaceFilterId = space.getId();
                currentPage = 1; // Reset to page 1 when switching spaces

                lblBannerTitle.setText("NOVA / " + space.getName());
                lblBannerDesc.setText(space.getDescription() != null ? space.getDescription() : "Welcome to the " + space.getName() + " community.");
                bannerIcon.setFill(Color.web(colorHex)); // Match banner icon to space color

                refreshFeed();
            });

            spaceRow.setOnMouseEntered(e -> {
                if (activeSidebarBtn != spaceRow) spaceRow.setStyle("-fx-padding: 8 15; -fx-background-radius: 8; -fx-cursor: hand; -fx-background-color: #f1f5f9;");
            });
            spaceRow.setOnMouseExited(e -> {
                if (activeSidebarBtn != spaceRow) spaceRow.setStyle("-fx-padding: 8 15; -fx-background-radius: 8; -fx-cursor: hand; -fx-background-color: transparent;");
            });

            spacesContainer.getChildren().add(spaceRow);
        }
    }

    @FXML
    void handleShowAllPosts(MouseEvent event) {
        setActiveSidebarButton(btnHome);
        currentSpaceFilterId = null;
        currentPage = 1;

        if (lblBannerTitle != null) lblBannerTitle.setText("NOVA / Home");
        if (lblBannerDesc != null) lblBannerDesc.setText("All discussions across all spaces.");
        if (bannerIcon != null) bannerIcon.setFill(Color.web("#2563eb")); // Default Blue

        refreshFeed();
    }

    private void loadAllPosts() {
        allPostsCache = postService.afficher();
        refreshFeed();
    }

    private void refreshFeed() {
        if (allPostsCache == null || postsContainer == null) return;

        List<Post> filteredPosts = allPostsCache;
        if (currentSpaceFilterId != null) {
            filteredPosts = allPostsCache.stream()
                    .filter(p -> p.getSpaceId() != null && p.getSpaceId().equals(currentSpaceFilterId))
                    .collect(Collectors.toList());
        }

        // Pagination Logic
        int totalPosts = filteredPosts.size();
        int totalPages = (int) Math.ceil((double) totalPosts / POSTS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;
        if (currentPage > totalPages) currentPage = totalPages;
        if (currentPage < 1) currentPage = 1;

        int fromIndex = (currentPage - 1) * POSTS_PER_PAGE;
        int toIndex = Math.min(fromIndex + POSTS_PER_PAGE, totalPosts);
        List<Post> pagePosts = filteredPosts.subList(fromIndex, toIndex);

        postsContainer.getChildren().clear();

        for (Post p : pagePosts) {
            if (utils.ForumSession.upvotedPosts.contains(p.getId())) p.setMyVote(1);
            else if (utils.ForumSession.downvotedPosts.contains(p.getId())) p.setMyVote(-1);

            postsContainer.getChildren().add(createPostCard(p));
        }

        renderPagination(totalPages);

        // Scroll to the top when refreshing pages
        Platform.runLater(() -> mainScrollPane.setVvalue(0.0));
    }

    private void renderPagination(int totalPages) {
        if (paginationContainer == null) return;
        paginationContainer.getChildren().clear();

        if (totalPages <= 1) return; // Hide pagination if only 1 page

        // "Previous" Button
        Label btnPrev = new Label("Prev");
        btnPrev.setStyle(currentPage > 1
                ? "-fx-padding: 8 16; -fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand; -fx-text-fill: #475569; -fx-font-weight: bold;"
                : "-fx-padding: 8 16; -fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: #94a3b8; -fx-font-weight: bold;");
        if (currentPage > 1) {
            btnPrev.setOnMouseClicked(e -> { currentPage--; refreshFeed(); });
        }

        paginationContainer.getChildren().add(btnPrev);

        // Numbered Pages
        for (int i = 1; i <= totalPages; i++) {
            final int pageNum = i;
            Label btnPage = new Label(String.valueOf(i));

            if (i == currentPage) {
                // Active Page Style
                btnPage.setStyle("-fx-padding: 8 14; -fx-background-color: #eff6ff; -fx-border-color: #2563eb; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: default; -fx-text-fill: #2563eb; -fx-font-weight: bold;");
            } else {
                // Inactive Page Style
                btnPage.setStyle("-fx-padding: 8 14; -fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand; -fx-text-fill: #475569; -fx-font-weight: bold;");
                btnPage.setOnMouseClicked(e -> { currentPage = pageNum; refreshFeed(); });
            }
            paginationContainer.getChildren().add(btnPage);
        }

        // "Next" Button
        Label btnNext = new Label("Next");
        btnNext.setStyle(currentPage < totalPages
                ? "-fx-padding: 8 16; -fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand; -fx-text-fill: #475569; -fx-font-weight: bold;"
                : "-fx-padding: 8 16; -fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: #94a3b8; -fx-font-weight: bold;");
        if (currentPage < totalPages) {
            btnNext.setOnMouseClicked(e -> { currentPage++; refreshFeed(); });
        }

        paginationContainer.getChildren().add(btnNext);
    }

    private Node createPostCard(Post post) {
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

        HBox headerRow = new HBox(6); headerRow.setAlignment(Pos.CENTER_LEFT);
        Label dot = new Label("🟢"); dot.setStyle("-fx-font-size: 8px; -fx-text-fill: #22c55e;");
        Label spaceLabel = new Label("NOVA/ " + (post.getSpaceName() != null ? post.getSpaceName() : "General"));
        spaceLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #0f172a;");
        Label authorLabel = new Label(" •  Posted by u/" + (post.getAuthorName() != null ? post.getAuthorName() : "Unknown"));
        authorLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        headerRow.getChildren().addAll(dot, spaceLabel, authorLabel);

        Label titleLabel = new Label();
        if (post.isLocked()) {
            titleLabel.setText("🔒 " + post.getTitle());
            titleLabel.setStyle("-fx-text-fill: #94a3b8;");
        } else {
            titleLabel.setText(post.getTitle());
            titleLabel.setStyle("-fx-text-fill: #0f172a;");
        }
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 17));

        Label contentLabel = new Label(post.getContent());
        contentLabel.setWrapText(true);
        contentLabel.setStyle("-fx-text-fill: #334155; -fx-font-size: 14px; -fx-line-spacing: 2px;");

        VBox attachmentBox = new VBox();
        if (post.getImageName() != null && !post.getImageName().trim().isEmpty()) {
            File imgFile = new File("C:/xampp/htdocs/projet dev/Pi_web/public/uploads/posts/" + post.getImageName());
            if (!imgFile.exists()) imgFile = new File("C:/xampp/htdocs/projet dev/Pi_web/public/uploads/forum/" + post.getImageName());

            if (imgFile.exists()) {
                try {
                    Image img = new Image(imgFile.toURI().toString(), true);
                    ImageView imageView = new ImageView(img);
                    imageView.setFitWidth(550);
                    imageView.setPreserveRatio(true);
                    attachmentBox.setStyle("-fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-padding: 2; -fx-background-color: white; -fx-background-radius: 6;");
                    attachmentBox.getChildren().add(imageView);
                    VBox.setMargin(attachmentBox, new Insets(10, 0, 5, 0));
                } catch (Exception e) {}
            }
        }

        HBox footerRow = new HBox(20); footerRow.setPadding(new Insets(10, 0, 0, 0));
        Label commentsLabel = new Label("💬 Comments");
        commentsLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4 8;");
        Label shareLabel = new Label("🔗 Share");
        shareLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4 8;");
        footerRow.getChildren().addAll(commentsLabel, shareLabel);

        contentBox.getChildren().addAll(headerRow, titleLabel, contentLabel);
        if (!attachmentBox.getChildren().isEmpty()) contentBox.getChildren().add(attachmentBox);
        contentBox.getChildren().add(footerRow);

        HBox mainCard = new HBox(0);
        mainCard.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.03), 8, 0, 0, 3); -fx-cursor: hand;");
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openPostDetails(Post post) {
        utils.ForumSession.currentPost = post;
        controllers.NovaDashboardController.loadPage("/views/forum/student/post_details.fxml");
    }
}