package controllers.gamification;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.util.Duration;
import models.gamification.Game;
import java.util.*;

public class GamePlayController {
    @FXML private Label lblGameTitle, lblGameType, lblCategory, lblRewardInfo, lblTimer, lblScore, lblInstructions;
    @FXML private StackPane gameOverlay;
    @FXML private VBox overlayContent;
    @FXML private Button btnStart, btnBack;
    @FXML private ProgressBar progressBar;
    @FXML private ScrollPane gameScrollPane;
    @FXML private VBox gameContentArea;

    private Game game;
    private StackPane contentArea;
    private Timeline gameTimer;
    private int secondsLeft = 60;
    private int score = 0;
    private boolean running = false;

    // Game-specific state
    // Memory Match
    private List<String> memoryIcons; private List<Button> memoryCards; private List<Integer> flippedIndices; private int matchedPairs; private boolean canFlip;
    // Word Scramble
    private List<String> wordList; private int wordIndex; private int wordScore; private Label scrambledLabel; private TextField wordInput;
    // Trivia
    private List<String[]> triviaQuestions; private int triviaIndex; private int triviaScore;
    // Arcade
    private int targetsHit, targetsMissed, totalTargets; private Timeline targetSpawner; private Button activeTarget;

    public void setGame(Game game) { this.game = game; populateHeader(); showStartOverlay(); }
    public void setContentArea(StackPane ca) { this.contentArea = ca; }

    private void populateHeader() {
        lblGameTitle.setText(game.getName());
        lblGameType.setText(game.getType());
        lblCategory.setText(game.getCategory().replace("_", " "));
        boolean isMini = "MINI_GAME".equals(game.getCategory());
        if (isMini) { int ep = game.getEnergyPoints()!=null?game.getEnergyPoints():0; lblRewardInfo.setText("+" + ep + " Energy on completion"); lblRewardInfo.setStyle("-fx-text-fill:#27ae60;-fx-font-weight:bold;"); }
        else { lblRewardInfo.setText("+" + game.getRewardTokens() + " tokens  +" + game.getRewardXP() + " XP"); lblRewardInfo.setStyle("-fx-text-fill:#3b4fd8;-fx-font-weight:bold;"); }
        secondsLeft = timeLimitFor(game.getDifficulty());
        lblTimer.setText(formatTime(secondsLeft));
        lblScore.setText("Score: 0");
        progressBar.setProgress(1.0);
        lblInstructions.setText(instructionsFor(game.getType(), game.getCategory()));
    }

    private void showStartOverlay() {
        gameOverlay.setVisible(true); overlayContent.getChildren().clear();
        StackPane typeIcon = faCircle(typeIcon(game.getType()), 28, typeGradient(game.getType()), "white");
        typeIcon.setPrefSize(72, 72); typeIcon.setMaxSize(72, 72);
        Label title = new Label(game.getName()); title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;");
        Label desc = new Label(game.getDescription()!=null?game.getDescription():""); desc.setWrapText(true); desc.setMaxWidth(380); desc.setStyle("-fx-text-fill:#718096;-fx-font-size:13px;");
        Label info = new Label("Type: "+game.getType()+"   Difficulty: "+game.getDifficulty()+"   Time: "+timeLimitFor(game.getDifficulty())+"s"); info.setStyle("-fx-text-fill:#4a5568;-fx-font-size:12px;");
        btnStart.setText("\uF144  Start Game"); btnStart.setOnAction(e -> startGame());
        overlayContent.getChildren().addAll(typeIcon, title, desc, info);
    }

    private void showResultOverlay(boolean passed) {
        gameOverlay.setVisible(true); overlayContent.getChildren().clear();
        StackPane resultIcon;
        if (passed) {
            resultIcon = faCircle("\uF091", 32, "linear-gradient(to bottom right, #f6d365, #fda085)", "white");
        } else {
            resultIcon = faCircle("\uF00D", 32, "linear-gradient(to bottom right, #fc5c7d, #6a3093)", "white");
        }
        resultIcon.setPrefSize(80, 80); resultIcon.setMaxSize(80, 80);
        Label result = new Label(passed?"Game Complete!":"Time's Up!"); result.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:"+(passed?"#27ae60":"#e53e3e")+";");
        Label scoreLabel = new Label("Final Score: "+score); scoreLabel.setStyle("-fx-font-size:16px;-fx-text-fill:#2d3748;");
        overlayContent.getChildren().addAll(resultIcon, result, scoreLabel);
        if (passed) { boolean isMini="MINI_GAME".equals(game.getCategory()); String rw=isMini?"+"+(game.getEnergyPoints()!=null?game.getEnergyPoints():0)+" Energy restored!":"+"+game.getRewardTokens()+" tokens  +"+game.getRewardXP()+" XP earned!"; Label rl=new Label(rw); rl.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#3b4fd8;"); overlayContent.getChildren().add(rl); }
        btnStart.setText("\uF2F9  Play Again"); btnStart.setOnAction(e -> { score=0; secondsLeft=timeLimitFor(game.getDifficulty()); lblScore.setText("Score: 0"); progressBar.setProgress(1.0); startGame(); });
    }

    @FXML private void startGame() {
        gameOverlay.setVisible(false); running=true; score=0; lblScore.setText("Score: 0");
        secondsLeft=timeLimitFor(game.getDifficulty());
        if (gameTimer!=null) gameTimer.stop();
        boolean isMini = "MINI_GAME".equals(game.getCategory());
        if (!isMini) { gameTimer=new Timeline(new KeyFrame(Duration.seconds(1),e->tick())); gameTimer.setCycleCount(Timeline.INDEFINITE); gameTimer.play(); }
        else { lblTimer.setText("—"); progressBar.setProgress(1.0); }
        gameContentArea.getChildren().clear();
        if (isMini) {
            buildMiniGame();
        } else {
            switch (game.getType()) {
                case "MEMORY" -> buildMemoryGame();
                case "PUZZLE" -> buildWordScramble();
                case "TRIVIA" -> buildTrivia();
                case "ARCADE" -> buildArcade();
                default       -> buildGeneric();
            }
        }
    }

    private void tick() {
        if (!running) return; secondsLeft--;
        lblTimer.setText(formatTime(secondsLeft));
        progressBar.setProgress((double)secondsLeft/timeLimitFor(game.getDifficulty()));
        if (secondsLeft<=0) endGame(false);
    }
    private void endGame(boolean passed) {
        running=false; if(gameTimer!=null) gameTimer.stop(); if(targetSpawner!=null) targetSpawner.stop();
        showResultOverlay(passed);
    }

    // ── MEMORY MATCH ─────────────────────────────────────────────────────────
    private void buildMemoryGame() {
        String[] icons={"A","B","C","D","E","F","G","H","I","J","K","L"};
        int pairs = game.getDifficulty().equals("HARD")?8:game.getDifficulty().equals("MEDIUM")?6:4;
        memoryIcons=new ArrayList<>(); for(int i=0;i<pairs;i++){memoryIcons.add(icons[i]);memoryIcons.add(icons[i]);}
        Collections.shuffle(memoryIcons);
        memoryCards=new ArrayList<>(); flippedIndices=new ArrayList<>(); matchedPairs=0; canFlip=true;
        StackPane brainIcon = faCircle("\uF5DC", 18, "linear-gradient(to bottom right, #a18cd1, #fbc2eb)", "white");
        brainIcon.setPrefSize(36, 36); brainIcon.setMaxSize(36, 36);
        Label hdrTxt = new Label("Memory Match \u2014 find all "+pairs+" pairs");
        hdrTxt.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;");
        HBox hdr = new HBox(10, brainIcon, hdrTxt); hdr.setAlignment(Pos.CENTER_LEFT);
        int cols=pairs<=4?4:pairs<=6?4:6;
        GridPane grid=new GridPane(); grid.setHgap(10); grid.setVgap(10); grid.setAlignment(Pos.CENTER);
        for(int i=0;i<memoryIcons.size();i++){
            final int idx=i;
            Button card=new Button("?"); card.setPrefSize(72,60);
            card.setStyle("-fx-background-color:#3b4fd8;-fx-text-fill:white;-fx-font-size:20px;-fx-background-radius:10;-fx-cursor:hand;");
            card.setOnAction(e->flipMemoryCard(idx));
            memoryCards.add(card); grid.add(card,i%cols,i/cols);
        }
        VBox box=new VBox(14,hdr,grid); box.setAlignment(Pos.CENTER); box.setPadding(new Insets(20));
        gameContentArea.getChildren().add(box);
    }
    private void flipMemoryCard(int idx) {
        if(!running||!canFlip) return;
        Button card=memoryCards.get(idx);
        if(card.getText().equals(memoryIcons.get(idx))||flippedIndices.contains(idx)) return;
        card.setText(memoryIcons.get(idx)); card.setStyle("-fx-background-color:#f0f2f8;-fx-text-fill:#1e2a5e;-fx-font-size:24px;-fx-background-radius:10;");
        flippedIndices.add(idx);
        if(flippedIndices.size()==2) {
            canFlip=false; int a=flippedIndices.get(0),b=flippedIndices.get(1);
            if(memoryIcons.get(a).equals(memoryIcons.get(b))) {
                matchedPairs++; score+=100; lblScore.setText("Score: "+score);
                memoryCards.get(a).setStyle("-fx-background-color:#27ae60;-fx-text-fill:white;-fx-font-size:24px;-fx-background-radius:10;");
                memoryCards.get(b).setStyle("-fx-background-color:#27ae60;-fx-text-fill:white;-fx-font-size:24px;-fx-background-radius:10;");
                flippedIndices.clear(); canFlip=true;
                int pairs=game.getDifficulty().equals("HARD")?8:game.getDifficulty().equals("MEDIUM")?6:4;
                if(matchedPairs==pairs) endGame(true);
            } else {
                PauseTransition pt=new PauseTransition(Duration.millis(900));
                pt.setOnFinished(e->{ memoryCards.get(a).setText("?"); memoryCards.get(a).setStyle("-fx-background-color:#3b4fd8;-fx-text-fill:white;-fx-font-size:20px;-fx-background-radius:10;-fx-cursor:hand;"); memoryCards.get(b).setText("?"); memoryCards.get(b).setStyle("-fx-background-color:#3b4fd8;-fx-text-fill:white;-fx-font-size:20px;-fx-background-radius:10;-fx-cursor:hand;"); flippedIndices.clear(); canFlip=true; });
                pt.play();
            }
        }
    }

    // ── WORD SCRAMBLE ─────────────────────────────────────────────────────────
    private void buildWordScramble() {
        wordList=new ArrayList<>(Arrays.asList("STUDY","LEARN","BRAIN","FOCUS","THINK","SMART","GRADE","TEACH","WRITE","SOLVE","MEMORY","SKILL","SCIENCE","HISTORY","PHYSICS"));
        Collections.shuffle(wordList);
        int count=game.getDifficulty().equals("HARD")?8:game.getDifficulty().equals("MEDIUM")?5:3;
        wordList=wordList.subList(0,Math.min(count,wordList.size())); wordIndex=0; wordScore=0;
        showNextWord();
    }
    private void showNextWord() {
        gameContentArea.getChildren().clear();
        if(wordIndex>=wordList.size()){endGame(wordScore>=(wordList.size()*0.6));return;}
        String word=wordList.get(wordIndex); String scrambled=scramble(word);
        StackPane puzzleIcon = faCircle("\uF12E", 18, "linear-gradient(to bottom right, #f6d365, #fda085)", "white");
        puzzleIcon.setPrefSize(36, 36); puzzleIcon.setMaxSize(36, 36);
        Label hdrTxt = new Label("Word Scramble \u2014 "+(wordIndex+1)+" / "+wordList.size());
        hdrTxt.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;");
        HBox hdr = new HBox(10, puzzleIcon, hdrTxt); hdr.setAlignment(Pos.CENTER_LEFT);
        Label hint=new Label("Unscramble this word:"); hint.setStyle("-fx-text-fill:#718096;-fx-font-size:13px;");
        scrambledLabel=new Label(scrambled); scrambledLabel.setStyle("-fx-font-size:36px;-fx-font-weight:bold;-fx-text-fill:#3b4fd8;-fx-letter-spacing:6;");
        wordInput=new TextField(); wordInput.setPromptText("Type your answer..."); wordInput.setMaxWidth(300);
        wordInput.setStyle("-fx-font-size:18px;-fx-padding:8 14;-fx-background-radius:8;-fx-border-color:#3b4fd8;-fx-border-radius:8;");
        Label feedback=new Label(""); feedback.setStyle("-fx-font-size:14px;");
        Button submit=new Button("\uF00C  Submit"); submit.setStyle("-fx-background-color:#3b4fd8;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-padding:8 20;-fx-cursor:hand;");
        Button skip=new Button("\uF051  Skip"); skip.setStyle("-fx-background-color:#e4e8f0;-fx-text-fill:#4a5568;-fx-background-radius:8;-fx-padding:8 20;-fx-cursor:hand;");
        submit.setOnAction(e->{ String ans=wordInput.getText().trim().toUpperCase(); if(ans.equals(word)){wordScore++;score+=100;lblScore.setText("Score: "+score);feedback.setText("\uF00C Correct!");feedback.setStyle("-fx-text-fill:#27ae60;-fx-font-size:14px;");}else{feedback.setText("\uF00D Try again!");feedback.setStyle("-fx-text-fill:#e53e3e;-fx-font-size:14px;");wordInput.clear();return;} PauseTransition pt=new PauseTransition(Duration.millis(800)); pt.setOnFinished(ev->{wordIndex++;showNextWord();}); pt.play(); });
        skip.setOnAction(e->{ feedback.setText("Skipped! Answer: "+word); feedback.setStyle("-fx-text-fill:#d97706;-fx-font-size:14px;"); PauseTransition pt=new PauseTransition(Duration.millis(1200)); pt.setOnFinished(ev->{wordIndex++;showNextWord();}); pt.play(); });
        wordInput.setOnAction(e->submit.fire());
        HBox btns=new HBox(12,submit,skip); btns.setAlignment(Pos.CENTER);
        VBox box=new VBox(16,hdr,hint,scrambledLabel,wordInput,btns,feedback); box.setAlignment(Pos.CENTER); box.setPadding(new Insets(30));
        gameContentArea.getChildren().add(box);
        Platform.runLater(()->wordInput.requestFocus());
    }
    private String scramble(String w){char[]a=w.toCharArray();Random r=new Random();for(int i=a.length-1;i>0;i--){int j=r.nextInt(i+1);char t=a[i];a[i]=a[j];a[j]=t;}return new String(a);}

    // ── TRIVIA / QUICK QUIZ ───────────────────────────────────────────────────
    private void buildTrivia() {
        triviaQuestions=getTriviaQuestions(game.getDifficulty()); triviaIndex=0; triviaScore=0;
        showNextQuestion();
    }
    private void showNextQuestion() {
        gameContentArea.getChildren().clear();
        if(triviaIndex>=triviaQuestions.size()){endGame(triviaScore>=(triviaQuestions.size()*0.6));return;}
        String[]q=triviaQuestions.get(triviaIndex); // q[0]=question, q[1..4]=options, q[5]=correct index
        StackPane qIcon = faCircle("\uF059", 14, "linear-gradient(to bottom right, #4facfe, #00f2fe)", "white");
        qIcon.setPrefSize(28, 28); qIcon.setMaxSize(28, 28);
        Label hdrTxt = new Label("Question "+(triviaIndex+1)+" / "+triviaQuestions.size());
        hdrTxt.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#718096;");
        HBox hdr = new HBox(8, qIcon, hdrTxt); hdr.setAlignment(Pos.CENTER_LEFT);
        Label qLabel=new Label(q[0]); qLabel.setWrapText(true); qLabel.setMaxWidth(600); qLabel.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;-fx-padding:16 20;-fx-background-color:#eef0fd;-fx-background-radius:10;");
        VBox opts=new VBox(10); opts.setMaxWidth(600);
        int correct=Integer.parseInt(q[5]);
        for(int i=1;i<=4;i++){
            final int oi=i-1;
            Button ob=new Button(String.valueOf((char)(64+i))+". "+q[i]); ob.setMaxWidth(Double.MAX_VALUE); ob.setAlignment(Pos.CENTER_LEFT);
            ob.setStyle("-fx-background-color:white;-fx-text-fill:#2d3748;-fx-font-size:14px;-fx-padding:12 18;-fx-background-radius:10;-fx-border-color:#e4e8f0;-fx-border-radius:10;-fx-cursor:hand;");
            ob.setOnAction(e->{
                opts.getChildren().forEach(n->((Button)n).setDisable(true));
                if(oi==correct){triviaScore++;score+=100;lblScore.setText("Score: "+score);ob.setStyle("-fx-background-color:#27ae60;-fx-text-fill:white;-fx-font-size:14px;-fx-padding:12 18;-fx-background-radius:10;");}
                else{ob.setStyle("-fx-background-color:#e53e3e;-fx-text-fill:white;-fx-font-size:14px;-fx-padding:12 18;-fx-background-radius:10;"); ((Button)opts.getChildren().get(correct)).setStyle("-fx-background-color:#27ae60;-fx-text-fill:white;-fx-font-size:14px;-fx-padding:12 18;-fx-background-radius:10;");}
                PauseTransition pt=new PauseTransition(Duration.millis(1200)); pt.setOnFinished(ev->{triviaIndex++;showNextQuestion();}); pt.play();
            }); opts.getChildren().add(ob);
        }
        VBox box=new VBox(14,hdr,qLabel,opts); box.setAlignment(Pos.TOP_CENTER); box.setPadding(new Insets(24)); box.setMaxWidth(640);
        gameContentArea.getChildren().add(box);
    }
    private List<String[]> getTriviaQuestions(String diff) {
        List<String[]> easy=Arrays.asList(
            new String[]{"What is 2 + 2?","3","4","5","6","1"},
            new String[]{"How many days in a week?","5","6","7","8","2"},
            new String[]{"What color is the sky?","Green","Blue","Red","Yellow","1"},
            new String[]{"Capital of France?","London","Berlin","Paris","Madrid","2"},
            new String[]{"How many legs does a spider have?","6","8","10","12","1"}
        );
        List<String[]> medium=Arrays.asList(
            new String[]{"Largest planet in solar system?","Earth","Mars","Jupiter","Saturn","2"},
            new String[]{"Who wrote Romeo and Juliet?","Dickens","Shakespeare","Austen","Twain","1"},
            new String[]{"Chemical symbol for gold?","Go","Gd","Au","Ag","2"},
            new String[]{"WWII ended in?","1943","1944","1945","1946","2"},
            new String[]{"Speed of light?","300,000 km/s","150,000 km/s","450,000 km/s","600,000 km/s","0"},
            new String[]{"How many continents?","5","6","7","8","2"},
            new String[]{"Smallest country?","Monaco","Vatican City","San Marino","Liechtenstein","1"}
        );
        List<String[]> hard=Arrays.asList(
            new String[]{"Smallest prime number?","0","1","2","3","2"},
            new String[]{"Theory of relativity?","Newton","Einstein","Hawking","Bohr","1"},
            new String[]{"Capital of Australia?","Sydney","Melbourne","Canberra","Brisbane","2"},
            new String[]{"Elements in periodic table?","108","118","128","138","1"},
            new String[]{"Longest river?","Amazon","Nile","Yangtze","Mississippi","1"},
            new String[]{"Square root of 144?","10","11","12","13","2"},
            new String[]{"Who painted Mona Lisa?","Michelangelo","Da Vinci","Raphael","Donatello","1"},
            new String[]{"Boiling point of water?","90C","95C","100C","105C","2"}
        );
        List<String[]> src="HARD".equals(diff)?hard:"EASY".equals(diff)?easy:medium;
        List<String[]> shuffled=new ArrayList<>(src); Collections.shuffle(shuffled);
        int count="HARD".equals(diff)?8:"EASY".equals(diff)?5:7;
        return shuffled.subList(0,Math.min(count,shuffled.size()));
    }

    // ── ARCADE / REACTION CLICKER ─────────────────────────────────────────────
    private void buildArcade() {
        totalTargets=game.getDifficulty().equals("HARD")?16:game.getDifficulty().equals("MEDIUM")?10:6;
        targetsHit=0; targetsMissed=0; activeTarget=null;
        StackPane arcadeIcon = faCircle("\uF11B", 18, "linear-gradient(to bottom right, #43e97b, #38f9d7)", "white");
        arcadeIcon.setPrefSize(36, 36); arcadeIcon.setMaxSize(36, 36);
        Label hdrTxt = new Label("Reaction Clicker \u2014 hit "+totalTargets+" targets!");
        hdrTxt.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;");
        HBox hdr = new HBox(10, arcadeIcon, hdrTxt); hdr.setAlignment(Pos.CENTER_LEFT);
        Label statsLbl=new Label("Hit: 0 / "+totalTargets+"  |  Missed: 0"); statsLbl.setStyle("-fx-text-fill:#718096;-fx-font-size:13px;");
        Pane arena=new Pane(); arena.setPrefSize(680,360); arena.setStyle("-fx-background-color:#f8f9ff;-fx-background-radius:12;-fx-border-color:#e4e8f0;-fx-border-radius:12;");
        VBox box=new VBox(12,hdr,statsLbl,arena); box.setAlignment(Pos.TOP_CENTER); box.setPadding(new Insets(20));
        gameContentArea.getChildren().add(box);
        String[]colors={"#e53e3e","#3b4fd8","#27ae60","#d97706","#9b59b6"};
        int speed=game.getDifficulty().equals("HARD")?1200:game.getDifficulty().equals("MEDIUM")?1800:2500;
        targetSpawner=new Timeline(new KeyFrame(Duration.millis(speed),e->{
            if(!running) return;
            if(activeTarget!=null){targetsMissed++;statsLbl.setText("Hit: "+targetsHit+" / "+totalTargets+"  |  Missed: "+targetsMissed); arena.getChildren().remove(activeTarget); activeTarget=null;}
            if(targetsHit+targetsMissed>=totalTargets){endGame((double)targetsHit/totalTargets>=0.6);return;}
            Button t=new Button("\uF111"); t.setPrefSize(56,56);
            String col=colors[new Random().nextInt(colors.length)];
            t.setStyle("-fx-background-color:"+col+";-fx-text-fill:white;-fx-font-family:'Font Awesome 6 Free Solid';-fx-font-size:22px;-fx-background-radius:50;-fx-cursor:hand;");
            double x=20+Math.random()*(680-76), y=20+Math.random()*(360-76);
            t.setLayoutX(x); t.setLayoutY(y);
            t.setOnAction(ev->{
                if(!running) return;
                targetsHit++; score+=100; lblScore.setText("Score: "+score);
                statsLbl.setText("Hit: "+targetsHit+" / "+totalTargets+"  |  Missed: "+targetsMissed);
                arena.getChildren().remove(t); activeTarget=null;
                if(targetsHit+targetsMissed>=totalTargets) endGame((double)targetsHit/totalTargets>=0.6);
            });
            arena.getChildren().add(t); activeTarget=t;
        })); targetSpawner.setCycleCount(Timeline.INDEFINITE); targetSpawner.play();
    }

    // ── MINI GAME ROUTER ─────────────────────────────────────────────────────
    private void buildMiniGame() {
        String name = game.getName().toLowerCase();
        if (name.contains("stretch") || name.contains("exercise"))      buildStretchGame();
        else if (name.contains("eye") || name.contains("vision"))       buildEyeRestGame();
        else if (name.contains("hydrat") || name.contains("water"))     buildHydrationGame();
        else                                                              buildBreathingGame();
    }

    // ── BREATHING ────────────────────────────────────────────────────────────
    private void buildBreathingGame() {
        int cycles = 3;
        int ep = game.getEnergyPoints() != null ? game.getEnergyPoints() : 20;

        StackPane windIcon = faCircle("\uF72E", 24, "linear-gradient(to bottom right, #4facfe, #00f2fe)", "white");
        windIcon.setPrefSize(48, 48); windIcon.setMaxSize(48, 48);
        Label hdrTxt = new Label("Breathing Exercise");
        hdrTxt.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;");
        HBox hdr = new HBox(12, windIcon, hdrTxt); hdr.setAlignment(Pos.CENTER_LEFT);
        Label sub  = new Label("Complete " + cycles + " breathing cycles to restore " + ep + " energy");
        sub.setStyle("-fx-text-fill:#718096;-fx-font-size:13px;");

        Circle circle = new Circle(70);
        circle.setFill(Color.web("#3b4fd8", 0.15));
        circle.setStroke(Color.web("#3b4fd8")); circle.setStrokeWidth(3);

        Label instrLbl = new Label("Get Ready...");
        instrLbl.setStyle("-fx-font-size:24px;-fx-font-weight:bold;-fx-text-fill:#3b4fd8;");
        Label cycleLbl = new Label("Cycle 0 / " + cycles);
        cycleLbl.setStyle("-fx-text-fill:#718096;-fx-font-size:13px;");
        ProgressBar bp = new ProgressBar(0); bp.setPrefWidth(320);
        bp.setStyle("-fx-accent:#3b4fd8;");

        Label tip = new Label("Breathe in for 4s · Hold for 4s · Breathe out for 4s");
        tip.setStyle("-fx-text-fill:#a0aec0;-fx-font-size:11px;");

        VBox box = new VBox(16, hdr, sub, circle, instrLbl, cycleLbl, bp, tip);
        box.setAlignment(Pos.CENTER); box.setPadding(new Insets(30));
        gameContentArea.getChildren().add(box);
        running = true;

        final int[] cyc = {0};
        final String[] phases = {"Breathe In...", "Hold...", "Breathe Out..."};
        final int[] pi = {0};

        Timeline breathe = new Timeline(new KeyFrame(Duration.seconds(4), e -> {
            if (!running) return;
            pi[0] = (pi[0] + 1) % 3;
            if (pi[0] == 0) cyc[0]++;
            instrLbl.setText(phases[pi[0]]);
            cycleLbl.setText("Cycle " + cyc[0] + " / " + cycles);
            bp.setProgress((double) cyc[0] / cycles);
            ScaleTransition st = new ScaleTransition(Duration.seconds(3.5), circle);
            st.setToX(pi[0] == 0 ? 1.6 : pi[0] == 2 ? 0.8 : 1.6);
            st.setToY(pi[0] == 0 ? 1.6 : pi[0] == 2 ? 0.8 : 1.6);
            st.play();
            if (cyc[0] >= cycles && pi[0] == 2) { running = false; endGame(true); }
        }));
        breathe.setCycleCount(cycles * 3 + 1); breathe.play();
    }

    // ── STRETCH ──────────────────────────────────────────────────────────────
    private void buildStretchGame() {
        int ep = game.getEnergyPoints() != null ? game.getEnergyPoints() : 20;
        String[][] exercises = {
            {"Neck Rolls",       "Slowly roll your neck in circles"},
            {"Shoulder Shrugs",  "Lift shoulders up and down"},
            {"Arm Circles",      "Make big circles with your arms"},
            {"Wrist Rotations",  "Rotate your wrists gently"},
            {"Back Stretch",     "Reach up and stretch your back"}
        };

        StackPane stretchIcon = faCircle("\uF44B", 24, "linear-gradient(to bottom right, #43e97b, #38f9d7)", "white");
        stretchIcon.setPrefSize(48, 48); stretchIcon.setMaxSize(48, 48);
        Label hdrTxt = new Label("Quick Stretch");
        hdrTxt.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;");
        HBox hdr = new HBox(12, stretchIcon, hdrTxt); hdr.setAlignment(Pos.CENTER_LEFT);
        Label sub = new Label("Complete all exercises to restore " + ep + " energy");
        sub.setStyle("-fx-text-fill:#718096;-fx-font-size:13px;");

        Label nameLbl = new Label(exercises[0][0]);
        nameLbl.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;");
        Label instrLbl = new Label(exercises[0][1]);
        instrLbl.setStyle("-fx-text-fill:#718096;-fx-font-size:14px;");
        Label timerLbl = new Label("10");
        timerLbl.setStyle("-fx-font-size:48px;-fx-font-weight:bold;-fx-text-fill:#3b4fd8;");
        Label progressTxt = new Label("Exercise 1 / " + exercises.length);
        progressTxt.setStyle("-fx-text-fill:#a0aec0;-fx-font-size:12px;");
        ProgressBar bp = new ProgressBar(0); bp.setPrefWidth(320);
        bp.setStyle("-fx-accent:#27ae60;");

        VBox box = new VBox(14, hdr, sub, nameLbl, instrLbl, timerLbl, progressTxt, bp);
        box.setAlignment(Pos.CENTER); box.setPadding(new Insets(24));
        gameContentArea.getChildren().add(box);
        running = true;

        final int[] exIdx = {0};
        final int[] secs  = {10};

        Timeline t = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (!running) return;
            secs[0]--;
            timerLbl.setText(String.valueOf(secs[0]));
            if (secs[0] <= 0) {
                exIdx[0]++;
                if (exIdx[0] >= exercises.length) { running = false; endGame(true); return; }
                secs[0] = 10;
                nameLbl.setText(exercises[exIdx[0]][0]);
                instrLbl.setText(exercises[exIdx[0]][1]);
                timerLbl.setText("10");
                progressTxt.setText("Exercise " + (exIdx[0] + 1) + " / " + exercises.length);
                bp.setProgress((double)(exIdx[0]) / exercises.length);
            }
        }));
        t.setCycleCount(exercises.length * 10 + 5); t.play();
    }

    // ── EYE REST ─────────────────────────────────────────────────────────────
    private void buildEyeRestGame() {
        int ep = game.getEnergyPoints() != null ? game.getEnergyPoints() : 20;
        int duration = 20;

        StackPane eyeHdrIcon = faCircle("\uF06E", 24, "linear-gradient(to bottom right, #4facfe, #00f2fe)", "white");
        eyeHdrIcon.setPrefSize(48, 48); eyeHdrIcon.setMaxSize(48, 48);
        Label hdrTxt = new Label("Eye Rest \u2014 20-20-20 Rule");
        hdrTxt.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;");
        HBox hdr = new HBox(12, eyeHdrIcon, hdrTxt); hdr.setAlignment(Pos.CENTER_LEFT);
        Label sub = new Label("Look at something 20 feet away for 20 seconds to restore " + ep + " energy");
        sub.setWrapText(true); sub.setMaxWidth(420);
        sub.setStyle("-fx-text-fill:#718096;-fx-font-size:13px;-fx-text-alignment:center;");

        StackPane eyeEmoji = faCircle("\uF06E", 32, "linear-gradient(to bottom right, #4facfe, #00f2fe)", "white");
        eyeEmoji.setPrefSize(80, 80); eyeEmoji.setMaxSize(80, 80);

        Label instrLbl = new Label("Get Ready...");
        instrLbl.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#3b4fd8;");
        Label tipLbl = new Label("This helps reduce eye strain from screen time");
        tipLbl.setStyle("-fx-text-fill:#718096;-fx-font-size:13px;");

        Label timerLbl = new Label(String.valueOf(duration));
        timerLbl.setStyle("-fx-font-size:56px;-fx-font-weight:bold;-fx-text-fill:#3b4fd8;");

        ProgressBar bp = new ProgressBar(0); bp.setPrefWidth(320);
        bp.setStyle("-fx-accent:#3b4fd8;");

        Label tip2 = new Label("Every 20 min, look 20 feet away for 20 seconds");
        tip2.setStyle("-fx-text-fill:#a0aec0;-fx-font-size:11px;");

        VBox box = new VBox(14, hdr, sub, eyeEmoji, instrLbl, tipLbl, timerLbl, bp, tip2);
        box.setAlignment(Pos.CENTER); box.setPadding(new Insets(24));
        gameContentArea.getChildren().add(box);
        running = true;

        final int[] secs = {duration};
        // Short delay before starting
        PauseTransition delay = new PauseTransition(Duration.seconds(2));
        delay.setOnFinished(ev -> {
            instrLbl.setText("Look Away From Screen");
            tipLbl.setText("Focus on something far away...");
            Timeline t = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
                if (!running) return;
                secs[0]--;
                timerLbl.setText(String.valueOf(secs[0]));
                bp.setProgress((double)(duration - secs[0]) / duration);
                if (secs[0] <= 0) { running = false; endGame(true); }
            }));
            t.setCycleCount(duration + 1); t.play();
        });
        delay.play();
    }

    // ── HYDRATION ────────────────────────────────────────────────────────────
    private void buildHydrationGame() {
        int ep = game.getEnergyPoints() != null ? game.getEnergyPoints() : 20;
        int duration = 30;

        StackPane waterHdrIcon = faCircle("\uF043", 24, "linear-gradient(to bottom right, #4facfe, #00f2fe)", "white");
        waterHdrIcon.setPrefSize(48, 48); waterHdrIcon.setMaxSize(48, 48);
        Label hdrTxt = new Label("Hydration Break");
        hdrTxt.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#1e2a5e;");
        HBox hdr = new HBox(12, waterHdrIcon, hdrTxt); hdr.setAlignment(Pos.CENTER_LEFT);
        Label sub = new Label("Drink water to restore " + ep + " energy");
        sub.setStyle("-fx-text-fill:#718096;-fx-font-size:13px;");

        // Water glass visual
        StackPane glassPane = new StackPane();
        glassPane.setPrefSize(80, 120);
        glassPane.setStyle("-fx-border-color:#0dcaf0;-fx-border-width:3;-fx-border-radius:0 0 8 8;-fx-background-color:rgba(13,202,240,0.08);");
        javafx.scene.shape.Rectangle waterFill = new javafx.scene.shape.Rectangle(74, 0);
        waterFill.setFill(Color.web("#0dcaf0", 0.6));
        waterFill.setTranslateY(60); // starts at bottom
        glassPane.getChildren().add(waterFill);
        StackPane.setAlignment(waterFill, Pos.BOTTOM_CENTER);

        Label instrLbl = new Label("Get Your Water");
        instrLbl.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#3b4fd8;");
        Label tipLbl = new Label("Staying hydrated improves focus and energy");
        tipLbl.setStyle("-fx-text-fill:#718096;-fx-font-size:13px;");

        Label timerLbl = new Label(duration + "s");
        timerLbl.setStyle("-fx-font-size:36px;-fx-font-weight:bold;-fx-text-fill:#0dcaf0;");

        ProgressBar bp = new ProgressBar(0); bp.setPrefWidth(320);
        bp.setStyle("-fx-accent:#0dcaf0;");

        final double[] glassLevel = {0};
        Button drinkBtn = new Button("\uF043  I'm Drinking!");
        drinkBtn.setStyle("-fx-background-color:#0dcaf0;-fx-text-fill:white;-fx-font-weight:bold;" +
                "-fx-font-family:'Font Awesome 6 Free Solid';-fx-font-size:14px;-fx-background-radius:8;-fx-padding:10 24;-fx-cursor:hand;");
        drinkBtn.setOnAction(e -> {
            glassLevel[0] = Math.min(glassLevel[0] + 20, 100);
            double fillH = (glassLevel[0] / 100.0) * 114;
            waterFill.setHeight(fillH);
            if (glassLevel[0] >= 100) instrLbl.setText("Great! Keep Drinking!");
            else instrLbl.setText("Keep Going! " + (int)glassLevel[0] + "% full");
        });

        Label tip2 = new Label("Aim for 8 glasses of water per day");
        tip2.setStyle("-fx-text-fill:#a0aec0;-fx-font-size:11px;");

        VBox box = new VBox(14, hdr, sub, glassPane, instrLbl, tipLbl, timerLbl, drinkBtn, bp, tip2);
        box.setAlignment(Pos.CENTER); box.setPadding(new Insets(24));
        gameContentArea.getChildren().add(box);
        running = true;

        final int[] secs = {duration};
        PauseTransition delay = new PauseTransition(Duration.seconds(2));
        delay.setOnFinished(ev -> {
            instrLbl.setText("Drink Water Now");
            tipLbl.setText("Click the button as you drink");
            Timeline t = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
                if (!running) return;
                secs[0]--;
                timerLbl.setText(secs[0] + "s");
                bp.setProgress((double)(duration - secs[0]) / duration);
                if (secs[0] <= 0) { running = false; endGame(true); }
            }));
            t.setCycleCount(duration + 1); t.play();
        });
        delay.play();
    }

    private void buildGeneric() {
        Label l=new Label(game.getName()+" \u2014 coming soon"); l.setStyle("-fx-font-size:18px;-fx-text-fill:#718096;");
        gameContentArea.getChildren().add(l);
    }

    @FXML private void goBack() {
        running=false; if(gameTimer!=null) gameTimer.stop(); if(targetSpawner!=null) targetSpawner.stop();
        try { FXMLLoader loader=new FXMLLoader(getClass().getResource("/views/gamification/game_launcher.fxml")); Parent view=loader.load(); GameLauncherController ctrl=loader.getController(); ctrl.setContentArea(contentArea); if(contentArea!=null) contentArea.getChildren().setAll(view); } catch(Exception e){e.printStackTrace();}
    }

    private String instructionsFor(String type, String cat) {
        if ("MINI_GAME".equals(cat)) {
            String name = game.getName().toLowerCase();
            if (name.contains("stretch"))                         return "Follow each stretching exercise for 10 seconds. Complete all 5 to finish.";
            if (name.contains("eye") || name.contains("vision")) return "Look away from your screen at something far away for 20 seconds.";
            if (name.contains("hydrat") || name.contains("water")) return "Drink water and click the button as you sip. Complete in 30 seconds.";
            return "Follow the breathing circle: inhale 4s · hold 4s · exhale 4s. Complete 3 cycles.";
        }
        return switch (type) {
            case "MEMORY" -> "Flip cards to find matching pairs. Match all pairs before time runs out!";
            case "PUZZLE" -> "Unscramble the letters to form the correct word. Submit or skip each word.";
            case "TRIVIA" -> "Answer multiple-choice questions correctly. 60% correct to win!";
            case "ARCADE" -> "Click the colored targets as fast as you can before they disappear!";
            default       -> "Follow the on-screen instructions to complete the game.";
        };
    }
    private int timeLimitFor(String d){return switch(d){case "HARD"->45;case "MEDIUM"->75;default->120;};}
    private String formatTime(int s){return String.format("%d:%02d",s/60,s%60);}

    // ── FA Icon Helpers ───────────────────────────────────────────────────────
    private StackPane faCircle(String unicode, double iconSize, String bgGradient, String iconColor) {
        Label ico = new Label(unicode);
        ico.setStyle("-fx-font-family: 'Font Awesome 6 Free Solid'; -fx-font-size: " + iconSize + "px; -fx-text-fill: " + iconColor + ";");
        StackPane sp = new StackPane(ico);
        sp.setPrefSize(56, 56); sp.setMaxSize(56, 56);
        sp.setStyle("-fx-background-color: " + bgGradient + "; -fx-background-radius: 50;");
        return sp;
    }

    private String typeIcon(String type) {
        return switch (type) {
            case "PUZZLE"  -> "\uF12E";
            case "MEMORY"  -> "\uF5DC";
            case "TRIVIA"  -> "\uF059";
            case "ARCADE"  -> "\uF11B";
            default        -> "\uF11B";
        };
    }

    private String typeGradient(String type) {
        return switch (type) {
            case "PUZZLE"  -> "linear-gradient(to bottom right, #f6d365, #fda085)";
            case "MEMORY"  -> "linear-gradient(to bottom right, #a18cd1, #fbc2eb)";
            case "TRIVIA"  -> "linear-gradient(to bottom right, #4facfe, #00f2fe)";
            case "ARCADE"  -> "linear-gradient(to bottom right, #43e97b, #38f9d7)";
            default        -> "linear-gradient(to bottom right, #667eea, #764ba2)";
        };
    }
}
