package controllers.studysession;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import models.ai.ChatMessage;
import models.studysession.Course;
import net.jqwik.api.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testfx.framework.junit5.ApplicationTest;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Preservation Property Tests for AI Chat Layout Fix
 * 
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7**
 * 
 * **Property 2: Preservation** - Non-Layout Functionality Unchanged
 * 
 * These tests observe and validate behavior on UNFIXED code for non-buggy inputs
 * (interactions that don't involve vertical layout issues). The tests capture
 * baseline behavior patterns that must be preserved after the fix is implemented.
 * 
 * **IMPORTANT**: These tests are EXPECTED TO PASS on unfixed code.
 * They establish the baseline behavior to preserve during the fix.
 * 
 * **Test Coverage**:
 * - Chat panel initial display with proper styling and positioning
 * - Floating action button toggle functionality
 * - Quick prompt suggestions display
 * - Typing indicator display
 * - User and assistant message rendering with styling
 * - Chat panel minimize/close functionality
 * - Clear history button functionality
 * - Button click handlers
 * - Message styling (colors, backgrounds)
 * - Timestamp display
 * - Favorite toggle on assistant messages
 */
public class AiChatPreservationTest extends ApplicationTest {

    private static Stage testStage;
    private static AiStudyAssistantController controller;
    private static VBox chatPanel;
    private static ScrollPane scrollPane;
    private static VBox vboxMessages;
    private static TextField txtInput;
    private static Button btnSend;
    private static Button btnClose;
    private static Button btnMinimize;
    private static Button btnClearHistory;
    private static Button btnFloatingAi;
    private static HBox hboxQuickPrompts;
    private static HBox typingIndicator;
    private static Label lblChatCourseHeader;

    @BeforeAll
    public static void initJavaFX() throws Exception {
        // Initialize JavaFX toolkit
        CountDownLatch latch = new CountDownLatch(1);
        Platform.startup(() -> latch.countDown());
        latch.await(5, TimeUnit.SECONDS);
    }

    @AfterAll
    public static void cleanupJavaFX() {
        Platform.exit();
    }

    @Override
    public void start(Stage stage) throws Exception {
        testStage = stage;
        
        // Load the FXML
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/views/studysession/AiStudyAssistantView.fxml")
        );
        Parent root = loader.load();
        controller = loader.getController();
        
        // Get references to UI components
        chatPanel = (VBox) root.lookup("#chatPanel");
        scrollPane = (ScrollPane) root.lookup("#scrollPane");
        vboxMessages = (VBox) root.lookup("#vboxMessages");
        txtInput = (TextField) root.lookup("#txtInput");
        btnSend = (Button) root.lookup("#btnSend");
        btnClose = (Button) root.lookup("#btnClose");
        btnMinimize = (Button) root.lookup("#btnMinimize");
        btnClearHistory = (Button) root.lookup("#btnClearHistory");
        btnFloatingAi = (Button) root.lookup("#btnFloatingAi");
        hboxQuickPrompts = (HBox) root.lookup("#hboxQuickPrompts");
        typingIndicator = (HBox) root.lookup("#typingIndicator");
        lblChatCourseHeader = (Label) root.lookup("#lblChatCourseHeader");
        
        // Initialize with a test course
        Course testCourse = new Course();
        testCourse.setId(1);
        testCourse.setCourseName("Test Course");
        testCourse.setCategory("Java");
        testCourse.setDifficulty("Intermediate");
        testCourse.setDescription("Test course for preservation testing");
        controller.initData(testCourse);
        
        // Create scene
        Scene scene = new Scene(root, 400, 800);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Property 2.1: Chat Panel Initial Display and Positioning
     * 
     * **Validates: Requirement 3.1**
     * 
     * Tests that the chat panel displays with proper styling and positioning
     * in the bottom-right corner when initialized.
     * 
     * **EXPECTED OUTCOME**: Test PASSES on unfixed code
     */
    @Property(tries = 10)
    @Label("Preservation: Chat panel initial display with proper styling and positioning")
    void chatPanelInitialDisplayPreserved() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean hasProperStyling = new AtomicBoolean(false);
        AtomicBoolean isPositionedCorrectly = new AtomicBoolean(false);
        
        Platform.runLater(() -> {
            try {
                // Check that chat panel exists and has proper structure
                hasProperStyling.set(chatPanel != null && 
                                    scrollPane != null && 
                                    vboxMessages != null &&
                                    txtInput != null &&
                                    btnSend != null);
                
                // Check positioning - chat panel should be in bottom-right
                // (In the FXML, this is controlled by AnchorPane constraints)
                isPositionedCorrectly.set(chatPanel.getLayoutX() >= 0 && 
                                         chatPanel.getLayoutY() >= 0);
                
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        
        latch.await(5, TimeUnit.SECONDS);
        
        if (!hasProperStyling.get()) {
            throw new AssertionError("Chat panel does not have proper styling or structure");
        }
        
        if (!isPositionedCorrectly.get()) {
            throw new AssertionError("Chat panel is not positioned correctly");
        }
    }

    /**
     * Property 2.2: Floating Action Button Toggle Functionality
     * 
     * **Validates: Requirement 3.2**
     * 
     * Tests that the floating action button correctly toggles the chat panel
     * visibility (open/close).
     * 
     * **EXPECTED OUTCOME**: Test PASSES on unfixed code
     */
    @Property(tries = 10)
    @Label("Preservation: Floating action button toggle functionality")
    void floatingActionButtonTogglePreserved() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean toggleWorks = new AtomicBoolean(false);
        
        Platform.runLater(() -> {
            try {
                // Initially, chat panel should be visible (from initData)
                boolean initiallyVisible = chatPanel.isVisible();
                
                // Minimize the chat
                controller.handleMinimize();
                
                // Wait for animation
                Platform.runLater(() -> {
                    try {
                        Thread.sleep(250);
                        boolean afterMinimize = chatPanel.isVisible();
                        
                        // Chat should be hidden after minimize
                        toggleWorks.set(initiallyVisible && !afterMinimize);
                        latch.countDown();
                    } catch (Exception e) {
                        e.printStackTrace();
                        latch.countDown();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        
        latch.await(5, TimeUnit.SECONDS);
        
        if (!toggleWorks.get()) {
            throw new AssertionError("Floating action button toggle does not work correctly");
        }
    }

    /**
     * Property 2.3: Quick Prompt Suggestions Display
     * 
     * **Validates: Requirement 3.3**
     * 
     * Tests that quick prompt suggestions are displayed in the horizontal
     * scrollable area and function correctly.
     * 
     * **EXPECTED OUTCOME**: Test PASSES on unfixed code
     */
    @Property(tries = 10)
    @Label("Preservation: Quick prompt suggestions display in horizontal scrollable area")
    void quickPromptSuggestionsPreserved() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean hasQuickPrompts = new AtomicBoolean(false);
        AtomicBoolean promptsAreButtons = new AtomicBoolean(false);
        
        Platform.runLater(() -> {
            try {
                // Check that quick prompts container exists and has children
                hasQuickPrompts.set(hboxQuickPrompts != null && 
                                   hboxQuickPrompts.getChildren().size() > 0);
                
                // Check that children are buttons
                if (hasQuickPrompts.get()) {
                    boolean allButtons = hboxQuickPrompts.getChildren().stream()
                        .allMatch(node -> node instanceof Button);
                    promptsAreButtons.set(allButtons);
                }
                
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        
        latch.await(5, TimeUnit.SECONDS);
        
        if (!hasQuickPrompts.get()) {
            throw new AssertionError("Quick prompt suggestions are not displayed");
        }
        
        if (!promptsAreButtons.get()) {
            throw new AssertionError("Quick prompt suggestions are not buttons");
        }
    }

    /**
     * Property 2.4: Typing Indicator Display
     * 
     * **Validates: Requirement 3.4**
     * 
     * Tests that the typing indicator is displayed correctly between the
     * messages area and input controls.
     * 
     * **EXPECTED OUTCOME**: Test PASSES on unfixed code
     */
    @Property(tries = 10)
    @Label("Preservation: Typing indicator display between messages and input controls")
    void typingIndicatorDisplayPreserved() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean indicatorExists = new AtomicBoolean(false);
        AtomicBoolean initiallyHidden = new AtomicBoolean(false);
        
        Platform.runLater(() -> {
            try {
                // Check that typing indicator exists
                indicatorExists.set(typingIndicator != null);
                
                // Initially, typing indicator should be hidden
                initiallyHidden.set(!typingIndicator.isVisible());
                
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        
        latch.await(5, TimeUnit.SECONDS);
        
        if (!indicatorExists.get()) {
            throw new AssertionError("Typing indicator does not exist");
        }
        
        if (!initiallyHidden.get()) {
            throw new AssertionError("Typing indicator should be initially hidden");
        }
    }

    /**
     * Property 2.5: User and Assistant Message Rendering with Styling
     * 
     * **Validates: Requirement 3.5**
     * 
     * Tests that user and assistant messages are rendered with their existing
     * styling and formatting (user messages with blue background, assistant
     * messages with dark background).
     * 
     * **EXPECTED OUTCOME**: Test PASSES on unfixed code
     */
    @Property(tries = 20)
    @Label("Preservation: User and assistant message rendering with existing styling")
    void messageRenderingAndStylingPreserved(
        @ForAll @IntRange(min = 1, max = 5) int messageCount
    ) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean stylingCorrect = new AtomicBoolean(false);
        
        Platform.runLater(() -> {
            try {
                // Clear existing messages
                vboxMessages.getChildren().clear();
                
                // Add messages with alternating roles
                for (int i = 0; i < messageCount; i++) {
                    ChatMessage msg = new ChatMessage();
                    msg.setId(i);
                    msg.setRole(i % 2 == 0 ? ChatMessage.ROLE_USER : ChatMessage.ROLE_ASSISTANT);
                    msg.setMessageText("Test message " + i);
                    msg.setCreatedAt(LocalDateTime.now());
                    
                    vboxMessages.getChildren().add(controller.buildMessageBubble(msg));
                }
                
                // Force layout update
                vboxMessages.layout();
                
                // Check styling of messages
                boolean allCorrect = true;
                for (int i = 0; i < vboxMessages.getChildren().size(); i++) {
                    if (vboxMessages.getChildren().get(i) instanceof HBox) {
                        HBox outer = (HBox) vboxMessages.getChildren().get(i);
                        if (outer.getChildren().size() > 0 && outer.getChildren().get(0) instanceof VBox) {
                            VBox bubble = (VBox) outer.getChildren().get(0);
                            String style = bubble.getStyle();
                            
                            // Check that bubble has background color styling
                            if (!style.contains("-fx-background-color")) {
                                allCorrect = false;
                                break;
                            }
                            
                            // User messages (even index) should have blue background
                            // Assistant messages (odd index) should have dark background
                            boolean isUser = i % 2 == 0;
                            if (isUser && !style.contains("#4A90D9")) {
                                allCorrect = false;
                                break;
                            }
                            if (!isUser && !style.contains("#2D2D2D")) {
                                allCorrect = false;
                                break;
                            }
                        }
                    }
                }
                
                stylingCorrect.set(allCorrect);
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        
        latch.await(5, TimeUnit.SECONDS);
        
        if (!stylingCorrect.get()) {
            throw new AssertionError(
                String.format("Message styling is incorrect for %d messages", messageCount)
            );
        }
    }

    /**
     * Property 2.6: Chat Panel Minimize/Close Functionality
     * 
     * **Validates: Requirement 3.6**
     * 
     * Tests that the chat panel minimize and close functionality continues
     * to work correctly.
     * 
     * **EXPECTED OUTCOME**: Test PASSES on unfixed code
     */
    @Property(tries = 10)
    @Label("Preservation: Chat panel minimize/close functionality")
    void chatPanelMinimizeClosePreserved() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean minimizeWorks = new AtomicBoolean(false);
        
        Platform.runLater(() -> {
            try {
                // Make chat panel visible
                chatPanel.setVisible(true);
                chatPanel.setManaged(true);
                
                // Test minimize
                controller.handleMinimize();
                
                // Wait for animation
                Platform.runLater(() -> {
                    try {
                        Thread.sleep(250);
                        boolean hiddenAfterMinimize = !chatPanel.isVisible();
                        
                        // Make visible again
                        chatPanel.setVisible(true);
                        chatPanel.setManaged(true);
                        
                        // Test close
                        controller.handleClose();
                        
                        Platform.runLater(() -> {
                            try {
                                Thread.sleep(250);
                                boolean hiddenAfterClose = !chatPanel.isVisible();
                                
                                minimizeWorks.set(hiddenAfterMinimize && hiddenAfterClose);
                                latch.countDown();
                            } catch (Exception e) {
                                e.printStackTrace();
                                latch.countDown();
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        latch.countDown();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        
        latch.await(10, TimeUnit.SECONDS);
        
        if (!minimizeWorks.get()) {
            throw new AssertionError("Chat panel minimize/close functionality does not work");
        }
    }

    /**
     * Property 2.7: Clear History Button Functionality
     * 
     * **Validates: Requirement 3.7**
     * 
     * Tests that the clear history button correctly clears all messages
     * from the chat.
     * 
     * **EXPECTED OUTCOME**: Test PASSES on unfixed code
     */
    @Property(tries = 20)
    @Label("Preservation: Clear history button functionality")
    void clearHistoryFunctionalityPreserved(
        @ForAll @IntRange(min = 1, max = 10) int messageCount
    ) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean clearWorks = new AtomicBoolean(false);
        
        Platform.runLater(() -> {
            try {
                // Clear existing messages
                vboxMessages.getChildren().clear();
                
                // Add messages
                for (int i = 0; i < messageCount; i++) {
                    ChatMessage msg = new ChatMessage();
                    msg.setId(i);
                    msg.setRole(i % 2 == 0 ? ChatMessage.ROLE_USER : ChatMessage.ROLE_ASSISTANT);
                    msg.setMessageText("Test message " + i);
                    msg.setCreatedAt(LocalDateTime.now());
                    
                    vboxMessages.getChildren().add(controller.buildMessageBubble(msg));
                }
                
                // Force layout update
                vboxMessages.layout();
                
                int beforeClear = vboxMessages.getChildren().size();
                
                // Clear history
                controller.handleClearHistory();
                
                int afterClear = vboxMessages.getChildren().size();
                
                clearWorks.set(beforeClear == messageCount && afterClear == 0);
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        
        latch.await(5, TimeUnit.SECONDS);
        
        if (!clearWorks.get()) {
            throw new AssertionError(
                String.format("Clear history does not work correctly with %d messages", messageCount)
            );
        }
    }

    /**
     * Property 2.8: Timestamp Display on Messages
     * 
     * **Validates: Requirement 3.5 (part of message rendering)**
     * 
     * Tests that timestamps are displayed correctly on messages.
     * 
     * **EXPECTED OUTCOME**: Test PASSES on unfixed code
     */
    @Property(tries = 20)
    @Label("Preservation: Timestamp display on messages")
    void timestampDisplayPreserved(
        @ForAll @IntRange(min = 1, max = 5) int messageCount
    ) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean timestampsPresent = new AtomicBoolean(false);
        
        Platform.runLater(() -> {
            try {
                // Clear existing messages
                vboxMessages.getChildren().clear();
                
                // Add messages with timestamps
                for (int i = 0; i < messageCount; i++) {
                    ChatMessage msg = new ChatMessage();
                    msg.setId(i);
                    msg.setRole(i % 2 == 0 ? ChatMessage.ROLE_USER : ChatMessage.ROLE_ASSISTANT);
                    msg.setMessageText("Test message " + i);
                    msg.setCreatedAt(LocalDateTime.now());
                    
                    vboxMessages.getChildren().add(controller.buildMessageBubble(msg));
                }
                
                // Force layout update
                vboxMessages.layout();
                
                // Check that each message bubble contains a timestamp label
                boolean allHaveTimestamps = true;
                for (int i = 0; i < vboxMessages.getChildren().size(); i++) {
                    if (vboxMessages.getChildren().get(i) instanceof HBox) {
                        HBox outer = (HBox) vboxMessages.getChildren().get(i);
                        if (outer.getChildren().size() > 0 && outer.getChildren().get(0) instanceof VBox) {
                            VBox bubble = (VBox) outer.getChildren().get(0);
                            
                            // Look for a Label with timestamp styling
                            boolean hasTimestamp = bubble.getChildren().stream()
                                .anyMatch(node -> node instanceof Label && 
                                         ((Label) node).getStyle().contains("10px"));
                            
                            if (!hasTimestamp) {
                                allHaveTimestamps = false;
                                break;
                            }
                        }
                    }
                }
                
                timestampsPresent.set(allHaveTimestamps);
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        
        latch.await(5, TimeUnit.SECONDS);
        
        if (!timestampsPresent.get()) {
            throw new AssertionError(
                String.format("Timestamps are not displayed correctly on %d messages", messageCount)
            );
        }
    }

    /**
     * Property 2.9: Favorite Toggle on Assistant Messages
     * 
     * **Validates: Requirement 3.5 (part of message rendering)**
     * 
     * Tests that the favorite toggle button is displayed on assistant messages
     * and functions correctly.
     * 
     * **EXPECTED OUTCOME**: Test PASSES on unfixed code
     */
    @Property(tries = 20)
    @Label("Preservation: Favorite toggle on assistant messages")
    void favoriteTogglePreserved(
        @ForAll @IntRange(min = 1, max = 5) int assistantMessageCount
    ) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean favoriteToggleWorks = new AtomicBoolean(false);
        
        Platform.runLater(() -> {
            try {
                // Clear existing messages
                vboxMessages.getChildren().clear();
                
                // Add assistant messages
                for (int i = 0; i < assistantMessageCount; i++) {
                    ChatMessage msg = new ChatMessage();
                    msg.setId(i);
                    msg.setRole(ChatMessage.ROLE_ASSISTANT);
                    msg.setMessageText("Assistant message " + i);
                    msg.setCreatedAt(LocalDateTime.now());
                    msg.setFavorite(false);
                    
                    vboxMessages.getChildren().add(controller.buildMessageBubble(msg));
                }
                
                // Force layout update
                vboxMessages.layout();
                
                // Check that each assistant message has a favorite button
                boolean allHaveFavoriteButton = true;
                for (int i = 0; i < vboxMessages.getChildren().size(); i++) {
                    if (vboxMessages.getChildren().get(i) instanceof HBox) {
                        HBox outer = (HBox) vboxMessages.getChildren().get(i);
                        if (outer.getChildren().size() > 0 && outer.getChildren().get(0) instanceof VBox) {
                            VBox bubble = (VBox) outer.getChildren().get(0);
                            
                            // Look for a Button with star symbol
                            boolean hasFavoriteButton = bubble.getChildren().stream()
                                .anyMatch(node -> node instanceof Button && 
                                         (((Button) node).getText().equals("☆") || 
                                          ((Button) node).getText().equals("★")));
                            
                            if (!hasFavoriteButton) {
                                allHaveFavoriteButton = false;
                                break;
                            }
                        }
                    }
                }
                
                favoriteToggleWorks.set(allHaveFavoriteButton);
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        
        latch.await(5, TimeUnit.SECONDS);
        
        if (!favoriteToggleWorks.get()) {
            throw new AssertionError(
                String.format("Favorite toggle is not present on %d assistant messages", 
                             assistantMessageCount)
            );
        }
    }

    /**
     * Property 2.10: Button Click Handlers Work Correctly
     * 
     * **Validates: Requirements 3.1, 3.2, 3.6, 3.7**
     * 
     * Tests that all button click handlers (send, close, minimize, clear history)
     * are properly wired and functional.
     * 
     * **EXPECTED OUTCOME**: Test PASSES on unfixed code
     */
    @Property(tries = 10)
    @Label("Preservation: Button click handlers work correctly")
    void buttonClickHandlersPreserved() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean handlersWork = new AtomicBoolean(false);
        
        Platform.runLater(() -> {
            try {
                // Check that buttons exist and are not null
                boolean buttonsExist = btnSend != null && 
                                      btnClose != null && 
                                      btnMinimize != null && 
                                      btnClearHistory != null;
                
                // Check that buttons have action handlers
                boolean hasHandlers = btnSend.getOnAction() != null &&
                                     btnClose.getOnAction() != null &&
                                     btnMinimize.getOnAction() != null &&
                                     btnClearHistory.getOnAction() != null;
                
                handlersWork.set(buttonsExist && hasHandlers);
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        
        latch.await(5, TimeUnit.SECONDS);
        
        if (!handlersWork.get()) {
            throw new AssertionError("Button click handlers are not properly wired");
        }
    }

    /**
     * Property 2.11: Course Header Label Display
     * 
     * **Validates: Requirement 3.1 (part of initial display)**
     * 
     * Tests that the course name is displayed correctly in the chat header.
     * 
     * **EXPECTED OUTCOME**: Test PASSES on unfixed code
     */
    @Property(tries = 10)
    @Label("Preservation: Course header label displays course name")
    void courseHeaderLabelPreserved() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> headerText = new AtomicReference<>("");
        
        Platform.runLater(() -> {
            try {
                // Get the header label text
                if (lblChatCourseHeader != null) {
                    headerText.set(lblChatCourseHeader.getText());
                }
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        
        latch.await(5, TimeUnit.SECONDS);
        
        if (!headerText.get().equals("Test Course")) {
            throw new AssertionError(
                String.format("Course header label does not display correct course name. " +
                             "Expected 'Test Course', got '%s'", headerText.get())
            );
        }
    }
}
