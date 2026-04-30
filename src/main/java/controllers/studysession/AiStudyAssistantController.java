package controllers.studysession;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import models.ai.ChatMessage;
import models.studysession.Course;
import services.ai.ChatHistoryService;
import services.ai.HuggingFaceService;
import utils.UserSession;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the AI Study Assistant chat panel widget (AiStudyAssistantView.fxml).
 *
 * Provides:
 *  - Floating action button with role-based visibility (14.1, 14.2)
 *  - Animated chat panel open/close (1.2, 1.3, 1.4, 1.5)
 *  - Course-context-aware system prompt building (2.1–2.6)
 *  - Async AI response via HuggingFaceService (3.1–3.5, 11.9)
 *  - Chat history persistence via ChatHistoryService (9.1–9.3)
 *  - Markdown rendering in message bubbles (3.3, 5.3, 7.3)
 *  - Favorite toggle on ASSISTANT bubbles (10.1–10.3)
 *  - 90-minute break reminder timer (8.1)
 *
 * Feature: ai-study-assistant
 */
public class AiStudyAssistantController implements Initializable {

    // ── FXML fields ───────────────────────────────────────────────────────────

    @FXML private Button     btnFloatingAi;
    @FXML private VBox       chatPanel;
    @FXML private Label      lblChatCourseHeader;
    @FXML private Button     btnMinimize;
    @FXML private Button     btnClose;
    @FXML private Button     btnClearHistory;
    @FXML private ScrollPane scrollPane;
    @FXML private VBox       vboxMessages;
    @FXML private HBox       typingIndicator;
    @FXML private TextField  txtInput;
    @FXML private Button     btnSend;
    @FXML private HBox       hboxQuickPrompts;

    // ── Private state ─────────────────────────────────────────────────────────

    private Course             currentCourse;
    private ChatHistoryService chatHistoryService;
    private HuggingFaceService huggingFaceService;

    /** In-memory list of loaded messages — used to pass history to HuggingFaceService. */
    private final List<ChatMessage> messageHistory = new ArrayList<>();

    // ── Initializable ─────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        chatHistoryService = new ChatHistoryService();
        huggingFaceService = new HuggingFaceService();
        startBreakReminderTimer();
    }

    // ── initData ──────────────────────────────────────────────────────────────

    /**
     * Called by the parent controller after FXML load to inject the course context.
     * Applies role-based visibility and loads persisted chat history.
     *
     * Requirements: 2.1, 2.6, 9.2, 14.1, 14.2
     *
     * @param course the course currently open in CourseContentView
     */
    public void initData(Course course) {
        this.currentCourse = course;

        // Set header label to course name (Requirement 2.6)
        lblChatCourseHeader.setText(course.getCourseName());

        // Role-based visibility (Requirements 14.1, 14.2)
        String role = UserSession.getInstance().getRole();
        if ("ROLE_STUDENT".equals(role)) {
            btnFloatingAi.setVisible(true);
            btnFloatingAi.setManaged(true);
        } else {
            btnFloatingAi.setVisible(false);
            btnFloatingAi.setManaged(false);
        }

        // Load and render persisted history (Requirement 9.2)
        int userId   = UserSession.getInstance().getUserId();
        int courseId = course.getId();
        try {
            List<ChatMessage> history = chatHistoryService.findLast50(userId, courseId);
            messageHistory.clear();
            messageHistory.addAll(history);
            for (ChatMessage msg : history) {
                vboxMessages.getChildren().add(buildMessageBubble(msg));
            }
        } catch (Exception e) {
            System.err.println("[AiStudyAssistantController] Failed to load history: " + e.getMessage());
        }

        // Populate quick prompt suggestions based on course category
        populateQuickPrompts(course);

        scrollToBottom();
    }

    // ── handleFloatingButtonClick ─────────────────────────────────────────────

    /**
     * Expands the chat panel with a parallel fade + scale animation.
     * Requirements: 1.2, 1.5
     */
    @FXML
    private void handleFloatingButtonClick() {
        chatPanel.setVisible(true);
        chatPanel.setManaged(true);
        chatPanel.setOpacity(0);
        chatPanel.setScaleX(0.8);
        chatPanel.setScaleY(0.8);

        FadeTransition  fade  = new FadeTransition(Duration.millis(200), chatPanel);
        fade.setFromValue(0);
        fade.setToValue(1);

        ScaleTransition scale = new ScaleTransition(Duration.millis(200), chatPanel);
        scale.setFromX(0.8);
        scale.setFromY(0.8);
        scale.setToX(1.0);
        scale.setToY(1.0);

        ParallelTransition open = new ParallelTransition(fade, scale);
        open.play();

        btnFloatingAi.setVisible(false);
        btnFloatingAi.setManaged(false);
    }

    // ── handleMinimize ────────────────────────────────────────────────────────

    /**
     * Collapses the chat panel with a reverse animation and shows the floating button.
     * Requirements: 1.3, 1.5
     */
    @FXML
    private void handleMinimize() {
        FadeTransition  fade  = new FadeTransition(Duration.millis(200), chatPanel);
        fade.setFromValue(1);
        fade.setToValue(0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(200), chatPanel);
        scale.setFromX(1.0);
        scale.setFromY(1.0);
        scale.setToX(0.8);
        scale.setToY(0.8);

        ParallelTransition close = new ParallelTransition(fade, scale);
        close.setOnFinished(e -> {
            chatPanel.setVisible(false);
            chatPanel.setManaged(false);
            // Restore floating button only for ROLE_STUDENT
            if ("ROLE_STUDENT".equals(UserSession.getInstance().getRole())) {
                btnFloatingAi.setVisible(true);
                btnFloatingAi.setManaged(true);
            }
        });
        close.play();
    }

    // ── handleClose ───────────────────────────────────────────────────────────

    /**
     * Dismisses the chat panel — same behaviour as minimize.
     * Requirements: 1.4, 1.5
     */
    @FXML
    private void handleClose() {
        handleMinimize();
    }

    // ── handleSend ────────────────────────────────────────────────────────────

    /**
     * Validates input, adds the user bubble, shows the typing indicator, and
     * dispatches the AI request on a background thread.
     * Requirements: 3.1–3.5, 9.1, 11.9, 13.3, 13.4, 13.5
     */
    @FXML
    private void handleSend() {
        String userText = txtInput.getText();
        if (userText == null || userText.trim().isEmpty()) {
            return;
        }
        userText = userText.trim();

        int userId   = UserSession.getInstance().getUserId();
        int courseId = currentCourse != null ? currentCourse.getId() : 0;

        // Build and display user message bubble
        ChatMessage userMsg = new ChatMessage(userId, courseId, ChatMessage.ROLE_USER, userText);
        userMsg.setCreatedAt(LocalDateTime.now());
        messageHistory.add(userMsg);
        vboxMessages.getChildren().add(buildMessageBubble(userMsg));

        txtInput.clear();

        // Show typing indicator (Requirement 3.4)
        typingIndicator.setVisible(true);
        typingIndicator.setManaged(true);

        scrollToBottom();

        // Persist user message (Requirement 9.1)
        try {
            chatHistoryService.save(userMsg);
        } catch (Exception e) {
            System.err.println("[AiStudyAssistantController] Failed to persist user message: " + e.getMessage());
        }

        // Build system prompt
        String systemPrompt = buildSystemPrompt(currentCourse, userText);

        // Snapshot of history to pass to the background task (last 10 messages)
        List<ChatMessage> historySnapshot = new ArrayList<>(messageHistory);

        // Background task for AI call (Requirement 11.9)
        final String finalUserText = userText;
        Task<String> aiTask = new Task<>() {
            @Override
            protected String call() {
                return huggingFaceService.chat(systemPrompt, historySnapshot, finalUserText);
            }
        };

        aiTask.setOnSucceeded(e -> {
            String reply = aiTask.getValue();

            // Hide typing indicator (Requirement 3.5)
            typingIndicator.setVisible(false);
            typingIndicator.setManaged(false);

            // Build and display assistant bubble
            ChatMessage assistantMsg = new ChatMessage(userId, courseId, ChatMessage.ROLE_ASSISTANT, reply);
            assistantMsg.setCreatedAt(LocalDateTime.now());
            messageHistory.add(assistantMsg);
            vboxMessages.getChildren().add(buildMessageBubble(assistantMsg));

            // Persist assistant message (Requirement 9.1)
            try {
                chatHistoryService.save(assistantMsg);
            } catch (Exception ex) {
                System.err.println("[AiStudyAssistantController] Failed to persist assistant message: " + ex.getMessage());
            }

            scrollToBottom();
        });

        aiTask.setOnFailed(e -> {
            typingIndicator.setVisible(false);
            typingIndicator.setManaged(false);

            // Show error bubble
            ChatMessage errorMsg = new ChatMessage(userId, courseId, ChatMessage.ROLE_ASSISTANT,
                    "Sorry, I couldn't get a response. Please try again.");
            errorMsg.setCreatedAt(LocalDateTime.now());
            vboxMessages.getChildren().add(buildMessageBubble(errorMsg));
            scrollToBottom();
        });

        Thread thread = new Thread(aiTask);
        thread.setDaemon(true);
        thread.start();
    }

    // ── handleClearHistory ────────────────────────────────────────────────────

    /**
     * Clears all persisted messages for the current student+course and empties the UI list.
     * Requirements: 9.3
     */
    @FXML
    private void handleClearHistory() {
        int userId   = UserSession.getInstance().getUserId();
        int courseId = currentCourse != null ? currentCourse.getId() : 0;
        try {
            chatHistoryService.clearHistory(userId, courseId);
        } catch (Exception e) {
            System.err.println("[AiStudyAssistantController] Failed to clear history: " + e.getMessage());
        }
        messageHistory.clear();
        vboxMessages.getChildren().clear();
    }

    // ── buildSystemPrompt ─────────────────────────────────────────────────────

    /**
     * Constructs the system prompt incorporating course context and dynamic instructions
     * based on the student's message content.
     *
     * Requirements: 2.2–2.5, 4.1, 6.1, 7.1, 7.4, 8.2
     *
     * @param course      the current course
     * @param userMessage the student's latest message
     * @return the composed system prompt string
     */
    public String buildSystemPrompt(Course course, String userMessage) {
        StringBuilder sb = new StringBuilder();

        // Base context (Requirement 2.2)
        String name        = course != null ? course.getCourseName()  : "Unknown";
        String category    = course != null ? course.getCategory()    : "";
        String difficulty  = course != null ? course.getDifficulty()  : "";
        String description = course != null ? course.getDescription() : "";

        sb.append("You are an AI study assistant for the course '")
          .append(name).append("'. ")
          .append("Category: ").append(category).append(". ")
          .append("Difficulty: ").append(difficulty).append(". ")
          .append("Description: ").append(description).append(". ")
          .append("Help the student understand the course material.");

        // Category-specific instructions (Requirements 2.3–2.5)
        if (category != null) {
            String catLower = category.toLowerCase();
            if (catLower.contains("java")) {
                sb.append(" Focus on Java programming concepts, OOP patterns, and provide code examples.");
            }
            if (catLower.contains("math")) {
                sb.append(" Focus on equations, step-by-step problem solving, and theoretical explanations.");
            }
            if (catLower.contains("database") || catLower.contains("sql")) {
                sb.append(" Focus on SQL syntax, ER diagrams, normalization, and query optimization.");
            }
        }

        // Message-based dynamic instructions
        if (userMessage != null) {
            String msgLower = userMessage.toLowerCase();

            // Beginner-friendly (Requirement 4.1)
            if (msgLower.contains("explain like a beginner") || msgLower.contains("in simple words")) {
                sb.append(" Use beginner-friendly language, analogies, and avoid technical jargon.");
            }

            // Translation (Requirement 6.1)
            if (msgLower.contains("translate to") || msgLower.contains("explain in")) {
                sb.append(" Translate or explain the content in the language specified by the student.");
            }

            // Study plan (Requirements 7.1, 7.4)
            if (msgLower.contains("study plan") || msgLower.contains("exam in")) {
                sb.append(" Generate a structured, time-boxed study plan organized by day and topic."
                        + " Include Pomodoro technique recommendations if the study duration is 2 hours or more.");
            }

            // Motivational closing (Requirement 8.2)
            if (msgLower.contains("don't understand") || msgLower.contains("too hard")
                    || msgLower.contains("i don't understand")) {
                sb.append(" End your response with an encouraging motivational message to keep the student motivated.");
            }
        }

        return sb.toString();
    }

    // ── buildMessageBubble ────────────────────────────────────────────────────

    /**
     * Builds a styled HBox chat bubble for the given message.
     *
     * Requirements: 10.1–10.3, 13.1, 13.2, 3.3
     *
     * @param msg the chat message to render
     * @return the outer HBox containing the bubble
     */
    public HBox buildMessageBubble(ChatMessage msg) {
        boolean isUser = ChatMessage.ROLE_USER.equals(msg.getRole());

        // Outer HBox — alignment and margin
        HBox outer = new HBox(8);
        outer.setPadding(new Insets(2, 12, 2, 12));
        outer.setMaxWidth(Double.MAX_VALUE);
        outer.setAlignment(isUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        // Bubble VBox
        VBox bubble = new VBox(4);
        bubble.setMaxWidth(280);
        bubble.setMinWidth(60);
        if (isUser) {
            bubble.setStyle(
                "-fx-background-color: #4A90D9;" +
                "-fx-background-radius: 16 16 4 16;" +
                "-fx-padding: 10 14;");
        } else {
            bubble.setStyle(
                "-fx-background-color: #2D2D2D;" +
                "-fx-background-radius: 16 16 16 4;" +
                "-fx-padding: 10 14;" +
                "-fx-border-color: #3A3A3A;" +
                "-fx-border-radius: 16 16 16 4;" +
                "-fx-border-width: 1;");
        }

        // Render message text with lightweight Markdown (Requirement 3.3)
        renderMarkdown(msg.getMessageText(), bubble);

        // Timestamp label (Requirement 13.2)
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        String timeStr = msg.getCreatedAt() != null ? msg.getCreatedAt().format(fmt) : "";
        Label lblTime = new Label(timeStr);
        lblTime.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8;");
        bubble.getChildren().add(lblTime);

        // Favorite toggle button for ASSISTANT bubbles (Requirements 10.1–10.3)
        if (!isUser) {
            Button btnFav = new Button(msg.isFavorite() ? "★" : "☆");
            btnFav.setStyle("-fx-background-color: transparent; -fx-text-fill: #f59e0b;"
                    + " -fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 0;");
            btnFav.setOnAction(e -> {
                boolean newState = !msg.isFavorite();
                msg.setFavorite(newState);
                btnFav.setText(newState ? "★" : "☆");
                int userId = UserSession.getInstance().getUserId();
                try {
                    chatHistoryService.setFavorite(msg.getId(), newState, userId);
                } catch (Exception ex) {
                    System.err.println("[AiStudyAssistantController] setFavorite failed: " + ex.getMessage());
                }
            });
            bubble.getChildren().add(btnFav);
        }

        // Add dot + bubble in correct order based on role
        if (isUser) {
            // Spacer pushes bubble to the right
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            outer.getChildren().addAll(spacer, bubble);
        } else {
            outer.getChildren().add(bubble);
        }
        return outer;
    }

    // ── renderMarkdown ────────────────────────────────────────────────────────

    /**
     * Lightweight Markdown renderer that parses the message text line by line
     * and adds styled Labels to the bubble VBox.
     *
     * Supported patterns:
     *  - Lines starting with "- " → bullet list item
     *  - Text wrapped in **...** → bold label
     *  - Text wrapped in `...`   → monospace/code label
     *  - All other lines         → regular wrapping label
     *
     * Requirements: 3.3, 5.3, 7.3
     *
     * @param text      the raw message text
     * @param container the VBox to add rendered labels into
     */
    private void renderMarkdown(String text, VBox container) {
        if (text == null || text.isEmpty()) {
            Label lbl = new Label("");
            lbl.setStyle("-fx-text-fill: white; -fx-font-size: 13px;");
            container.getChildren().add(lbl);
            return;
        }

        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            if (line.startsWith("- ")) {
                Label lbl = new Label("  • " + line.substring(2));
                lbl.setStyle("-fx-text-fill: white; -fx-font-size: 13px;");
                lbl.setWrapText(true);
                lbl.setMaxWidth(Double.MAX_VALUE);
                container.getChildren().add(lbl);
            } else if (line.startsWith("**") && line.endsWith("**") && line.length() > 4) {
                Label lbl = new Label(line.substring(2, line.length() - 2));
                lbl.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");
                lbl.setWrapText(true);
                lbl.setMaxWidth(Double.MAX_VALUE);
                container.getChildren().add(lbl);
            } else if (line.startsWith("`") && line.endsWith("`") && line.length() > 2) {
                Label lbl = new Label(line.substring(1, line.length() - 1));
                lbl.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 12px;"
                        + " -fx-font-family: monospace;"
                        + " -fx-background-color: #1A1A1A;"
                        + " -fx-padding: 2 4;");
                lbl.setWrapText(true);
                lbl.setMaxWidth(Double.MAX_VALUE);
                container.getChildren().add(lbl);
            } else {
                container.getChildren().addAll(parseInlineLine(line));
            }
        }
    }

    /**
     * Parses a single line for inline bold (**...**) and code (``...``) markers,
     * returning a list of styled Labels.
     */
    private List<Label> parseInlineLine(String line) {
        List<Label> labels = new ArrayList<>();
        if (line == null || line.isEmpty()) {
            Label lbl = new Label(" ");
            lbl.setStyle("-fx-text-fill: white; -fx-font-size: 13px;");
            labels.add(lbl);
            return labels;
        }

        if (line.contains("**")) {
            String[] parts = line.split("\\*\\*", -1);
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].isEmpty()) continue;
                Label lbl = new Label(parts[i]);
                lbl.setStyle(i % 2 == 1
                    ? "-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;"
                    : "-fx-text-fill: white; -fx-font-size: 13px;");
                lbl.setWrapText(true);
                lbl.setMaxWidth(Double.MAX_VALUE);
                labels.add(lbl);
            }
        } else if (line.contains("`")) {
            String[] parts = line.split("`", -1);
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].isEmpty()) continue;
                Label lbl = new Label(parts[i]);
                lbl.setStyle(i % 2 == 1
                    ? "-fx-text-fill: #e2e8f0; -fx-font-size: 12px; -fx-font-family: monospace; -fx-background-color: #1A1A1A; -fx-padding: 2 4;"
                    : "-fx-text-fill: white; -fx-font-size: 13px;");
                lbl.setWrapText(true);
                lbl.setMaxWidth(Double.MAX_VALUE);
                labels.add(lbl);
            }
        } else {
            Label lbl = new Label(line);
            lbl.setStyle("-fx-text-fill: white; -fx-font-size: 13px;");
            lbl.setWrapText(true);
            lbl.setMaxWidth(Double.MAX_VALUE);
            labels.add(lbl);
        }
        return labels;
    }

    // ── startBreakReminderTimer ───────────────────────────────────────────────

    /**
     * Starts a one-shot 90-minute timeline that injects a break reminder message
     * into the chat panel.
     * Requirements: 8.1
     */
    private void startBreakReminderTimer() {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.minutes(90), e -> {
                    ChatMessage reminder = new ChatMessage();
                    reminder.setRole(ChatMessage.ROLE_ASSISTANT);
                    reminder.setMessageText(
                            "⏰ You've been studying for 90 minutes! "
                            + "Take a 10-15 minute break to refresh your mind. 🧠");
                    reminder.setCreatedAt(LocalDateTime.now());

                    Platform.runLater(() -> {
                        vboxMessages.getChildren().add(buildMessageBubble(reminder));
                        scrollToBottom();
                    });
                })
        );
        timeline.setCycleCount(1);
        timeline.play();
    }

    // ── populateQuickPrompts ──────────────────────────────────────────────────

    /**
     * Populates the quick prompt suggestion buttons based on the course category.
     * Provides context-aware prompts to help students get started with the AI assistant.
     *
     * @param course the current course
     */
    private void populateQuickPrompts(Course course) {
        if (hboxQuickPrompts == null) return;

        hboxQuickPrompts.getChildren().clear();

        // Common prompts for all courses
        String[] commonPrompts = {
            "📝 Summarize this topic",
            "🎯 Create a study plan",
            "💡 Explain like I'm a beginner"
        };

        // Category-specific prompts
        String[] categoryPrompts = {};
        if (course != null && course.getCategory() != null) {
            String category = course.getCategory().toLowerCase();
            if (category.contains("java") || category.contains("programming")) {
                categoryPrompts = new String[]{
                    "💻 Show me a code example",
                    "🔍 Explain this concept with OOP"
                };
            } else if (category.contains("math")) {
                categoryPrompts = new String[]{
                    "🧮 Solve this step-by-step",
                    "📐 Show me the formula"
                };
            } else if (category.contains("database") || category.contains("sql")) {
                categoryPrompts = new String[]{
                    "🗄️ Write a SQL query example",
                    "📊 Explain normalization"
                };
            } else {
                categoryPrompts = new String[]{
                    "❓ What are the key concepts?",
                    "📚 Give me practice questions"
                };
            }
        }

        // Add common prompts
        for (String prompt : commonPrompts) {
            hboxQuickPrompts.getChildren().add(createQuickPromptButton(prompt));
        }

        // Add category-specific prompts
        for (String prompt : categoryPrompts) {
            hboxQuickPrompts.getChildren().add(createQuickPromptButton(prompt));
        }
    }

    /**
     * Creates a styled quick prompt button that fills the input field when clicked.
     *
     * @param promptText the text to display on the button and insert into the input field
     * @return the styled Button
     */
    private Button createQuickPromptButton(String promptText) {
        Button btn = new Button(promptText);
        btn.setStyle(
            "-fx-background-color: #3A3A3A;" +
            "-fx-text-fill: #94a3b8;" +
            "-fx-font-size: 10px;" +
            "-fx-background-radius: 12;" +
            "-fx-padding: 4 10;" +
            "-fx-cursor: hand;" +
            "-fx-border-color: #4A4A4A;" +
            "-fx-border-radius: 12;" +
            "-fx-border-width: 1;"
        );

        // Hover effect
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: #4A90D9;" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 10px;" +
            "-fx-background-radius: 12;" +
            "-fx-padding: 4 10;" +
            "-fx-cursor: hand;" +
            "-fx-border-color: #4A90D9;" +
            "-fx-border-radius: 12;" +
            "-fx-border-width: 1;"
        ));

        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: #3A3A3A;" +
            "-fx-text-fill: #94a3b8;" +
            "-fx-font-size: 10px;" +
            "-fx-background-radius: 12;" +
            "-fx-padding: 4 10;" +
            "-fx-cursor: hand;" +
            "-fx-border-color: #4A4A4A;" +
            "-fx-border-radius: 12;" +
            "-fx-border-width: 1;"
        ));

        // Click action: fill input field with the prompt text (without emoji)
        btn.setOnAction(e -> {
            // Remove emoji from the prompt text
            String cleanPrompt = promptText.replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}]", "").trim();
            txtInput.setText(cleanPrompt);
            txtInput.requestFocus();
            txtInput.positionCaret(txtInput.getText().length());
        });

        return btn;
    }

    // ── scrollToBottom ────────────────────────────────────────────────────────

    /**
     * Scrolls the message list to the bottom after a new message is added.
     * Requirements: 13.5
     */
    private void scrollToBottom() {
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }
}
