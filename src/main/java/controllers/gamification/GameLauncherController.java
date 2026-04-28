package controllers.gamification;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import models.gamification.Game;
import services.gamification.FavoriteGameService;
import services.gamification.GameRatingService;
import services.gamification.GameService;
import utils.TwemojiUtil;
import utils.UserSession;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class GameLauncherController {

    @FXML private VBox     mainContainer;
    @FXML private FlowPane fullGamesPane;
    @FXML private FlowPane miniGamesPane;
    @FXML private Label    lblFullCount;
    @FXML private Label    lblMiniCount;
    @FXML private TextField        searchField;
    @FXML private ComboBox<String> filterType;
    @FXML private ComboBox<String> filterDifficulty;
    @FXML private Button   btnTabFull;
    @FXML private Button   btnTabMini;
    @FXML private VBox     sectionFull;
    @FXML private VBox     sectionMini;

    private final GameService        gameService   = new GameService();
    private final FavoriteGameService favService   = new FavoriteGameService();
    private final GameRatingService  ratingService = new GameRatingService();
    private List<Game> allGames;
    private StackPane contentArea;
    private boolean showingFull = true;

    public void setContentArea(StackPane ca) { this.contentArea = ca; }

    public void setInitialTab(int tabIndex) {
        if (tabIndex == 1) switchToMini();
        else switchToFull();
    }

    @FXML
    public void initialize() {
        filterType.setItems(FXCollections.observableArrayList("All Types","PUZZLE","MEMORY","TRIVIA","ARCADE"));
        filterType.setValue("All Types");
        filterDifficulty.setItems(FXCollections.observableArrayList("All Levels","EASY","MEDIUM","HARD"));
        filterDifficulty.setValue("All Levels");
        filterType.valueProperty().addListener((o,a,b) -> applyFilters());
        filterDifficulty.valueProperty().addListener((o,a,b) -> applyFilters());
        try {
            allGames = gameService.getAllGames().stream().filter(Game::isActive).collect(Collectors.toList());
        } catch (Exception e) { allGames = List.of(); }
        renderAll(allGames);
        switchToFull();
    }

    @FXML private void handleSearch() { applyFilters(); }
    @FXML private void handleClearFilters() {
        searchField.clear(); filterType.setValue("All Types"); filterDifficulty.setValue("All Levels");
        renderAll(allGames);
    }
    @FXML private void handleShowFull() { switchToFull(); }
    @FXML private void handleShowMini() { switchToMini(); }

    private void switchToFull() {
        showingFull = true;
        sectionFull.setVisible(true); sectionFull.setManaged(true);
        sectionMini.setVisible(false); sectionMini.setManaged(false);
        btnTabFull.setStyle(tabActiveStyle());
        btnTabMini.setStyle(tabInactiveStyle());
    }
    private void switchToMini() {
        showingFull = false;
        sectionFull.setVisible(false); sectionFull.setManaged(false);
        sectionMini.setVisible(true); sectionMini.setManaged(true);
        btnTabFull.setStyle(tabInactiveStyle());
        btnTabMini.setStyle(tabActiveStyle());
    }

    private String tabActiveStyle() {
        return "-fx-background-color:#3b4fd8;-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:13px;" +
               "-fx-background-radius:8 8 0 0;-fx-padding:10 24;-fx-cursor:hand;-fx-border-color:transparent;";
    }
    private String tabInactiveStyle() {
        return "-fx-background-color:#f0f2f8;-fx-text-fill:#718096;-fx-font-size:13px;" +
               "-fx-background-radius:8 8 0 0;-fx-padding:10 24;-fx-cursor:hand;-fx-border-color:transparent;";
    }

    private void applyFilters() {
        String kw = searchField.getText().trim().toLowerCase();
        String type = filterType.getValue();
        String diff = filterDifficulty.getValue();
        List<Game> filtered = allGames.stream().filter(g -> {
            boolean matchKw   = kw.isEmpty() || g.getName().toLowerCase().contains(kw) || g.getType().toLowerCase().contains(kw);
            boolean matchType = "All Types".equals(type) || g.getType().equals(type);
            boolean matchDiff = "All Levels".equals(diff) || g.getDifficulty().equals(diff);
            return matchKw && matchType && matchDiff;
        }).collect(Collectors.toList());
        renderAll(filtered);
    }

    private void renderAll(List<Game> games) {
        List<Game> full = games.stream().filter(g -> "FULL_GAME".equals(g.getCategory())).collect(Collectors.toList());
        List<Game> mini = games.stream().filter(g -> "MINI_GAME".equals(g.getCategory())).collect(Collectors.toList());
        fullGamesPane.getChildren().clear();
        miniGamesPane.getChildren().clear();
        full.forEach(g -> fullGamesPane.getChildren().add(buildCard(g, false)));
        mini.forEach(g -> miniGamesPane.getChildren().add(buildCard(g, true)));
        lblFullCount.setText(full.size() + " game" + (full.size()==1?"":"s"));
        lblMiniCount.setText(mini.size() + " mini-game" + (mini.size()==1?"":"s"));
    }
    private VBox buildCard(Game game, boolean isMini) {
        // Twemoji icon circle
        StackPane iconCircle = TwemojiUtil.circle(typeEmoji(game.getType()), 64, typeGradient(game.getType()), 38);
        iconCircle.setMaxSize(64, 64);

        Label title = new Label(game.getName());
        title.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;");
        title.setWrapText(true); title.setTextAlignment(TextAlignment.CENTER);
        title.setAlignment(Pos.CENTER); title.setMaxWidth(190);

        Label typeBadge = badge(game.getType(), typeBadgeBg(game.getType()), typeBadgeFg(game.getType()));
        Label diffBadge = badge(game.getDifficulty(), diffBg(game.getDifficulty()), diffFg(game.getDifficulty()));
        HBox badges = new HBox(6, typeBadge, diffBadge); badges.setAlignment(Pos.CENTER);

        Separator sep = new Separator();

        // Stats — tokens/XP for full, energy for mini
        HBox stats;
        if (isMini) {
            int ep = game.getEnergyPoints()!=null?game.getEnergyPoints():0;
            stats = statsRow("Energy", "+"+ep, "#27ae60", "Cost", String.valueOf(game.getTokenCost()), "#718096");
        } else {
            stats = statsRow("Tokens", "+"+game.getRewardTokens(), "#b7791f", "XP", "+"+game.getRewardXP(), "#2b6cb0");
        }
        stats.setStyle("-fx-background-color:#f8f9ff;-fx-background-radius:8;-fx-padding:8 6;");

        // Rating row
        HBox ratingRow = buildRatingDisplay(game.getId());
        int userId = UserSession.getInstance().getUserId();
        if (userId > 0) {
            Button btnRate = new Button("Rate");
            btnRate.setStyle("-fx-background-color:transparent;-fx-text-fill:#3b4fd8;-fx-font-size:11px;-fx-cursor:hand;-fx-underline:true;-fx-padding:0 4;");
            btnRate.setOnAction(e -> showRatingDialog(game, ratingRow));
            ratingRow.getChildren().add(btnRate);
        }

        // Play button — plain text, no FA icon
        String btnBg    = isMini ? "#27ae60" : "#3b4fd8";
        String btnHover = isMini ? "#1e8449" : "#2d3fc7";
        Button btnPlay = new Button(isMini ? "Play Mini Game" : "Play Now");
        btnPlay.setMaxWidth(Double.MAX_VALUE);
        btnPlay.setStyle("-fx-background-color:"+btnBg+";-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:13px;-fx-background-radius:8;-fx-padding:10 0;-fx-cursor:hand;");
        btnPlay.setOnMouseEntered(e -> btnPlay.setStyle(btnPlay.getStyle().replace(btnBg, btnHover)));
        btnPlay.setOnMouseExited(e -> btnPlay.setStyle(btnPlay.getStyle().replace(btnHover, btnBg)));
        btnPlay.setOnAction(e -> launchGame(game));

        // Favorite button
        final boolean[] isFav = {false};
        try { isFav[0] = userId>0 && favService.isFavorite(userId, game.getId()); } catch (Exception ignored) {}
        Label heartLbl = new Label(isFav[0] ? "\u2764" : "\u2661");
        heartLbl.setStyle("-fx-font-size:16px;-fx-text-fill:"+(isFav[0]?"#e53e3e":"#a0aec0")+";");
        Button btnFav = new Button(); btnFav.setGraphic(heartLbl);
        btnFav.setStyle("-fx-background-color:transparent;-fx-cursor:hand;-fx-padding:4 8;");
        btnFav.setOnAction(e -> {
            if (userId<=0) return;
            try {
                boolean nowFav = favService.toggle(userId, game.getId());
                isFav[0] = nowFav;
                heartLbl.setText(nowFav?"\u2764":"\u2661");
                heartLbl.setStyle("-fx-font-size:16px;-fx-text-fill:"+(nowFav?"#e53e3e":"#a0aec0")+";");
            } catch (Exception ex) {}
        });

        HBox actionRow = new HBox(6, btnPlay, btnFav);
        actionRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(btnPlay, Priority.ALWAYS);

        VBox card = new VBox(10, iconCircle, title, badges, sep, stats, ratingRow, actionRow);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(18,14,18,14));
        card.setPrefWidth(210);
        card.setStyle("-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#e4e8f0;-fx-border-radius:14;-fx-border-width:1;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.07),10,0,0,3);");
        card.setOnMouseEntered(e -> card.setStyle(card.getStyle().replace("rgba(0,0,0,0.07)","rgba(59,79,216,0.18)").replace("#e4e8f0","#c3c9f5")));
        card.setOnMouseExited(e  -> card.setStyle(card.getStyle().replace("rgba(59,79,216,0.18)","rgba(0,0,0,0.07)").replace("#c3c9f5","#e4e8f0")));
        return card;
    }

    private HBox buildRatingDisplay(int gameId) {
        HBox row = new HBox(4); row.setAlignment(Pos.CENTER);
        try {
            double avg = ratingService.getAverageRating(gameId);
            int count  = ratingService.getRatingCount(gameId);
            if (count == 0) {
                Label none = new Label("No ratings yet"); none.setStyle("-fx-text-fill:#cbd5e0;-fx-font-size:11px;");
                row.getChildren().add(none);
            } else {
                for (int i=1;i<=5;i++) {
                    Label star = new Label(i<=Math.round(avg)?"\u2605":"\u2606");
                    star.setStyle("-fx-font-size:14px;-fx-text-fill:"+(i<=Math.round(avg)?"#f6d365":"#cbd5e0")+";");
                    row.getChildren().add(star);
                }
                Label avgLbl = new Label(String.format(" %.1f (%d)",avg,count));
                avgLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#718096;");
                row.getChildren().add(avgLbl);
            }
        } catch (Exception e) { row.getChildren().add(new Label("")); }
        return row;
    }

    private void showRatingDialog(Game game, HBox ratingRowToRefresh) {
        int userId = UserSession.getInstance().getUserId();
        if (userId<=0) return;
        Dialog<Void> dlg = new Dialog<>(); dlg.setTitle("Rate: "+game.getName());
        VBox content = new VBox(14); content.setAlignment(Pos.CENTER); content.setPadding(new Insets(24)); content.setMinWidth(300);
        Label title = new Label("How would you rate this game?"); title.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;");
        final int[] selected = {0};
        try { selected[0] = ratingService.getUserRating(userId, game.getId()); } catch (Exception ignored) {}
        Label[] starLabels = new Label[5];
        HBox stars = new HBox(10); stars.setAlignment(Pos.CENTER);
        for (int i=1;i<=5;i++) {
            final int n=i;
            Label star = new Label(i<=selected[0]?"\u2605":"\u2606");
            star.setStyle("-fx-font-size:36px;-fx-cursor:hand;-fx-text-fill:"+(i<=selected[0]?"#f6d365":"#cbd5e0")+";");
            starLabels[i-1]=star;
            star.setOnMouseEntered(e->{ for(int j=0;j<5;j++){boolean f=j<n;starLabels[j].setText(f?"\u2605":"\u2606");starLabels[j].setStyle("-fx-font-size:36px;-fx-cursor:hand;-fx-text-fill:"+(f?"#f6d365":"#cbd5e0")+";");}});
            star.setOnMouseExited(e->{ for(int j=0;j<5;j++){boolean f=j<selected[0];starLabels[j].setText(f?"\u2605":"\u2606");starLabels[j].setStyle("-fx-font-size:36px;-fx-cursor:hand;-fx-text-fill:"+(f?"#f6d365":"#cbd5e0")+";");}});
            star.setOnMouseClicked(e->{ selected[0]=n; for(int j=0;j<5;j++){boolean f=j<selected[0];starLabels[j].setText(f?"\u2605":"\u2606");starLabels[j].setStyle("-fx-font-size:36px;-fx-cursor:hand;-fx-text-fill:"+(f?"#f6d365":"#cbd5e0")+";");}});
            stars.getChildren().add(star);
        }
        content.getChildren().addAll(title, stars, new Label("Click a star to rate"));
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.getDialogPane().lookupButton(ButtonType.OK).setStyle("-fx-background-color:#3b4fd8;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:6;-fx-padding:7 18;");
        dlg.setResultConverter(bt -> {
            if (bt==ButtonType.OK && selected[0]>0) {
                try {
                    ratingService.rate(userId, game.getId(), selected[0]);
                    HBox fresh = buildRatingDisplay(game.getId());
                    ratingRowToRefresh.getChildren().setAll(fresh.getChildren());
                    Button btnRate = new Button("Rate");
                    btnRate.setStyle("-fx-background-color:transparent;-fx-text-fill:#3b4fd8;-fx-font-size:11px;-fx-cursor:hand;-fx-underline:true;-fx-padding:0 4;");
                    btnRate.setOnAction(ev -> showRatingDialog(game, ratingRowToRefresh));
                    ratingRowToRefresh.getChildren().add(btnRate);
                } catch (Exception ex) {}
            }
            return null;
        });
        dlg.showAndWait();
    }

    private HBox statsRow(String lbl1, String val1, String col1, String lbl2, String val2, String col2) {
        HBox row = new HBox(0, statCell(lbl1, val1, col1), statCell(lbl2, val2, col2));
        row.setAlignment(Pos.CENTER);
        HBox.setHgrow(row.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(row.getChildren().get(1), Priority.ALWAYS);
        return row;
    }
    private VBox statCell(String label, String value, String valueColor) {
        Label val = new Label(value);
        val.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:" + valueColor + ";");
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size:10px;-fx-text-fill:#a0aec0;");
        VBox b = new VBox(2, val, lbl); b.setAlignment(Pos.CENTER); b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private void launchGame(Game game) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/gamification/game_play.fxml"));
            Parent view = loader.load();
            GamePlayController ctrl = loader.getController();
            ctrl.setGame(game); ctrl.setContentArea(contentArea);
            if (contentArea!=null) contentArea.getChildren().setAll(view);
        } catch (Exception e) { new Alert(Alert.AlertType.ERROR,"Cannot load game: "+e.getMessage()).showAndWait(); }
    }

    private Label badge(String text, String bg, String fg) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color:"+bg+";-fx-text-fill:"+fg+";-fx-background-radius:20;-fx-padding:3 10;-fx-font-size:10px;-fx-font-weight:bold;");
        return l;
    }

    private String typeEmoji(String t) { return switch(t){case "PUZZLE"->TwemojiUtil.PUZZLE;case "MEMORY"->TwemojiUtil.MEMORY;case "TRIVIA"->TwemojiUtil.TRIVIA;case "ARCADE"->TwemojiUtil.ARCADE;default->TwemojiUtil.GAMEPAD;}; }
    private String typeGradient(String t) { return switch(t){case "PUZZLE"->"linear-gradient(to bottom right,#f6d365,#fda085)";case "MEMORY"->"linear-gradient(to bottom right,#a18cd1,#fbc2eb)";case "TRIVIA"->"linear-gradient(to bottom right,#4facfe,#00f2fe)";case "ARCADE"->"linear-gradient(to bottom right,#43e97b,#38f9d7)";default->"linear-gradient(to bottom right,#667eea,#764ba2)";}; }
    private String typeBadgeBg(String t) { return switch(t){case "PUZZLE"->"#fff8e1";case "MEMORY"->"#f3e5f5";case "TRIVIA"->"#e3f2fd";case "ARCADE"->"#e8f5e9";default->"#eef0fd";}; }
    private String typeBadgeFg(String t) { return switch(t){case "PUZZLE"->"#b7791f";case "MEMORY"->"#805ad5";case "TRIVIA"->"#2b6cb0";case "ARCADE"->"#276749";default->"#3b4fd8";}; }
    private String diffBg(String d) { return switch(d){case "HARD"->"#fff5f5";case "MEDIUM"->"#fffbeb";default->"#f0fff4";}; }
    private String diffFg(String d) { return switch(d){case "HARD"->"#e53e3e";case "MEDIUM"->"#d97706";default->"#27ae60";}; }
}
