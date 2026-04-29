# Bugfix Requirements Document

## Introduction

The AI assistant chat interface in the course content view has a critical layout defect where the chat container expands indefinitely as messages accumulate. This causes the input controls (send button, cancel button, and hide button) to be pushed below the visible viewport, making them inaccessible to users. This prevents users from sending new messages or closing the chat after multiple messages have been exchanged.

The fix must ensure that the chat container maintains a fixed maximum height, the messages area scrolls independently, and all input controls remain visible and accessible at all times.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN multiple messages are sent in the AI assistant chat THEN the chat container (VBox with fx:id="chatPanel") expands vertically without any height limit

1.2 WHEN the chat container expands beyond the viewport height THEN the input controls (send button, text field) at the bottom are pushed out of the visible area

1.3 WHEN the input controls are pushed out of view THEN users cannot access the send button to send new messages

1.4 WHEN the input controls are pushed out of view THEN users cannot access the close/minimize buttons in the header to dismiss the chat

1.5 WHEN messages accumulate in the vboxMessages container THEN no scrolling mechanism is available for the messages area, causing the entire panel to grow

### Expected Behavior (Correct)

2.1 WHEN multiple messages are sent in the AI assistant chat THEN the chat container SHALL maintain a fixed maximum height that fits within the viewport

2.2 WHEN messages accumulate beyond the visible area THEN the messages ScrollPane SHALL scroll independently while keeping the input controls visible

2.3 WHEN the chat contains many messages THEN the send button and text input field SHALL remain visible and accessible at the bottom of the chat panel

2.4 WHEN the chat contains many messages THEN the close, minimize, and clear history buttons SHALL remain visible and accessible in the header

2.5 WHEN new messages are added THEN the ScrollPane SHALL automatically scroll to show the latest message while maintaining the fixed panel height

### Unchanged Behavior (Regression Prevention)

3.1 WHEN the chat panel is initially opened THEN the system SHALL CONTINUE TO display the chat with proper styling and positioning in the bottom-right corner

3.2 WHEN the floating action button is clicked THEN the system SHALL CONTINUE TO toggle the chat panel visibility correctly

3.3 WHEN quick prompt suggestions are displayed THEN the system SHALL CONTINUE TO render them in the horizontal scrollable area

3.4 WHEN the typing indicator is shown THEN the system SHALL CONTINUE TO display it correctly between the messages area and input controls

3.5 WHEN messages are displayed THEN the system SHALL CONTINUE TO render user and assistant messages with their existing styling and formatting

3.6 WHEN the chat panel is minimized or closed THEN the system SHALL CONTINUE TO hide the panel and show the floating action button

3.7 WHEN the clear history button is clicked THEN the system SHALL CONTINUE TO clear all messages from the chat
