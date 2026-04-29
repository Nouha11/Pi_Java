package controllers.studysession;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
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

/**
 * Bug Condition Exploration Test for AI Chat Layout Issue
 * 
 * **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5**
 * 
 * **Property 1: Bug Condition** - Chat Panel Height Exceeds Viewport
 * 
 * This test explores the bug condition where the chat panel expands indefinitely
 * as messages accumulate, pushing input controls below the viewport.
 * 
 * **CRITICAL**: This test is EXPECTED TO FAIL on unfixed code - failure confirms the bug exists.
 * When the test FAILS, it proves the bug condition is present.
 * When the test PASSES, it means the expected behavior is satisfied (bug is fixed).
 * 
 * **Bug Condition**: isBugCondition(input) where:
 *   - input.messageCount > threshold (15+ messages)
 *   - chatPanel.height > viewport.height (800px)
 *   - inputControls.visibleInViewport == false
 * 
 * **Expected Behavior** (what this test validates):
 *   - chatPanel.height <= maxHeight (600px) for all message counts
 *   - ScrollPane scrolls independently to show all messages
 *   - Input controls (send button, text field) remain visible in viewport
 *   - Header controls (close, minimize, clear history) remain visible in viewport
 */
public class AiChatLayoutBugConditionTest extends ApplicationTest {

    private static final double VIEWPORT_HEIGHT = 800.0;
    private static final double EXPECTED_MAX_HEIGHT = 600.0;
    private static final int MESSAGE_THRESHOLD = 15;
    
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
        
        // Initialize with a test course
        Course testCourse = new Course();
        testCourse.setId(1);
        testCourse.setCourseName("Test Course");
        testCourse.setCategory("Java");
        testCourse.setDifficulty("Intermediate");
        testCourse.setDescription("Test course for layout testing");
        controller.initData(testCourse);
        
        // Make chat panel visible for testing
        chatPanel.setVisible(true);
        chatPanel.setManaged(true);
        
        // Create scene with fixed viewport height
        Scene scene = new Scene(root, 400, VIEWPORT_HEIGHT);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Property 1: Bug Condition - Chat Panel Height Exceeds Viewport
     * 
     * **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5**
     * 
     * Tests that when messageCount > threshold (15+), the chat panel maintains
     * a maximum height constraint, messages scroll independently, and all input
     * controls remain visible and accessible.
     * 
     * **EXPECTED OUTCOME ON UNFIXED CODE**: Test FAILS
     *   - chatPanel.height > EXPECTED_MAX_HEIGHT (600px)
     *   - Input controls pushed below viewport
     *   - Counterexample: "After adding 15 messages, chatPanel height is 1500px,
     *     send button Y-coordinate is 1450px, viewport height is 800px - button is inaccessible"
     * 
     * **EXPECTED OUTCOME ON FIXED CODE**: Test PASSES
     *   - chatPanel.height <= EXPECTED_MAX_HEIGHT (600px)
     *   - Input controls remain visible in viewport
     *   - ScrollPane scrolls independently
     */
    @Property(tries = 50)
    @Label("Bug Condition: Chat panel height exceeds viewport with many messages")
    void chatPanelHeightExceedsViewportWithManyMessages(
        @ForAll @IntRange(min = MESSAGE_THRESHOLD, max = 30) int messageCount
    ) throws Exception {
        
        CountDownLatch latch = new CountDownLatch(1);
        
        Platform.runLater(() -> {
            try {
                // Clear existing messages
                vboxMessages.getChildren().clear();
                
                // Add messages to trigger bug condition
                for (int i = 0; i < messageCount; i++) {
                    ChatMessage msg = new ChatMessage();
                    msg.setId(i);
                    msg.setRole(i % 2 == 0 ? ChatMessage.ROLE_USER : ChatMessage.ROLE_ASSISTANT);
                    msg.setMessageText("Test message " + i + ". This is a test message to simulate "
                        + "a conversation with multiple exchanges. The message needs to be long enough "
                        + "to take up vertical space in the chat panel.");
                    msg.setCreatedAt(LocalDateTime.now());
                    
                    vboxMessages.getChildren().add(controller.buildMessageBubble(msg));
                }
                
                // Force layout update
                chatPanel.layout();
                vboxMessages.layout();
                scrollPane.layout();
                
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        
        latch.await(5, TimeUnit.SECONDS);
        
        // Give JavaFX time to complete layout
        Thread.sleep(100);
        
        // Measure actual dimensions
        double actualChatPanelHeight = chatPanel.getHeight();
        double actualChatPanelLayoutY = chatPanel.getLayoutY();
        
        // Calculate positions of input controls
        double sendButtonY = actualChatPanelLayoutY + actualChatPanelHeight - btnSend.getHeight();
        double textFieldY = actualChatPanelLayoutY + actualChatPanelHeight - txtInput.getHeight();
        
        // Calculate positions of header controls
        double closeButtonY = actualChatPanelLayoutY + btnClose.getLayoutY();
        double minimizeButtonY = actualChatPanelLayoutY + btnMinimize.getLayoutY();
        double clearHistoryButtonY = actualChatPanelLayoutY + btnClearHistory.getLayoutY();
        
        // Check if ScrollPane can scroll (indicates independent scrolling)
        double scrollPaneContentHeight = vboxMessages.getHeight();
        double scrollPaneViewportHeight = scrollPane.getViewportBounds().getHeight();
        boolean canScroll = scrollPaneContentHeight > scrollPaneViewportHeight;
        
        // Build detailed counterexample message
        String counterexample = String.format(
            "After adding %d messages: chatPanel height is %.1fpx, " +
            "send button Y-coordinate is %.1fpx, text field Y-coordinate is %.1fpx, " +
            "viewport height is %.1fpx, ScrollPane content height is %.1fpx, " +
            "ScrollPane viewport height is %.1fpx, can scroll: %s",
            messageCount, actualChatPanelHeight, sendButtonY, textFieldY, 
            VIEWPORT_HEIGHT, scrollPaneContentHeight, scrollPaneViewportHeight, canScroll
        );
        
        // ASSERTION 1: Chat panel height must not exceed maximum height
        // This prevents indefinite vertical expansion (Requirement 1.1)
        if (actualChatPanelHeight > EXPECTED_MAX_HEIGHT) {
            throw new AssertionError(
                "Bug Condition Detected: Chat panel height exceeds maximum. " + counterexample
            );
        }
        
        // ASSERTION 2: Input controls must remain visible in viewport
        // This ensures users can always send messages (Requirements 1.2, 1.3)
        if (sendButtonY > VIEWPORT_HEIGHT || textFieldY > VIEWPORT_HEIGHT) {
            throw new AssertionError(
                "Bug Condition Detected: Input controls pushed below viewport. " + counterexample
            );
        }
        
        // ASSERTION 3: Header controls must remain visible in viewport
        // This ensures users can close/minimize the chat (Requirement 1.4)
        if (closeButtonY < 0 || minimizeButtonY < 0 || clearHistoryButtonY < 0) {
            throw new AssertionError(
                "Bug Condition Detected: Header controls pushed above viewport. " + counterexample
            );
        }
        
        // ASSERTION 4: ScrollPane must be able to scroll when content exceeds visible area
        // This ensures messages scroll independently (Requirement 1.5)
        if (messageCount > MESSAGE_THRESHOLD && !canScroll) {
            throw new AssertionError(
                "Bug Condition Detected: ScrollPane cannot scroll despite many messages. " + counterexample
            );
        }
        
        // If all assertions pass, the expected behavior is satisfied
        System.out.println("Expected behavior validated: " + counterexample);
    }

    /**
     * Edge Case: Chat panel with few messages should not trigger bug condition
     * 
     * Tests that with fewer messages (below threshold), the chat panel displays
     * correctly without any layout issues.
     */
    @Property(tries = 20)
    @Label("Edge Case: Chat panel with few messages displays correctly")
    void chatPanelWithFewMessagesDisplaysCorrectly(
        @ForAll @IntRange(min = 0, max = 5) int messageCount
    ) throws Exception {
        
        CountDownLatch latch = new CountDownLatch(1);
        
        Platform.runLater(() -> {
            try {
                // Clear existing messages
                vboxMessages.getChildren().clear();
                
                // Add few messages
                for (int i = 0; i < messageCount; i++) {
                    ChatMessage msg = new ChatMessage();
                    msg.setId(i);
                    msg.setRole(i % 2 == 0 ? ChatMessage.ROLE_USER : ChatMessage.ROLE_ASSISTANT);
                    msg.setMessageText("Short message " + i);
                    msg.setCreatedAt(LocalDateTime.now());
                    
                    vboxMessages.getChildren().add(controller.buildMessageBubble(msg));
                }
                
                // Force layout update
                chatPanel.layout();
                vboxMessages.layout();
                scrollPane.layout();
                
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        
        latch.await(5, TimeUnit.SECONDS);
        Thread.sleep(100);
        
        // Measure dimensions
        double actualChatPanelHeight = chatPanel.getHeight();
        double actualChatPanelLayoutY = chatPanel.getLayoutY();
        
        // Calculate positions
        double sendButtonY = actualChatPanelLayoutY + actualChatPanelHeight - btnSend.getHeight();
        
        // With few messages, all controls should be visible
        if (actualChatPanelHeight > VIEWPORT_HEIGHT) {
            throw new AssertionError(
                String.format("Even with %d messages, chat panel height (%.1fpx) exceeds viewport (%.1fpx)",
                    messageCount, actualChatPanelHeight, VIEWPORT_HEIGHT)
            );
        }
        
        if (sendButtonY > VIEWPORT_HEIGHT) {
            throw new AssertionError(
                String.format("Even with %d messages, send button (Y=%.1fpx) is below viewport (%.1fpx)",
                    messageCount, sendButtonY, VIEWPORT_HEIGHT)
            );
        }
        
        System.out.println(String.format(
            "Edge case validated: %d messages, chatPanel height %.1fpx, send button Y %.1fpx",
            messageCount, actualChatPanelHeight, sendButtonY
        ));
    }
}
