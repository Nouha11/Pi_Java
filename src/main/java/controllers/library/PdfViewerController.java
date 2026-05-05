package controllers.library;

import controllers.NovaDashboardController;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import models.library.Book;
import netscape.javascript.JSObject;
import services.library.GroqService;

import java.io.File;
import java.net.URL;

/**
 * PDF Viewer using PDF.js rendered inside a JavaFX WebView.
 * Supports local file paths and remote URLs.
 * Includes Grok AI assistant side panel.
 */
public class PdfViewerController {

    @FXML private WebView pdfWebView;
    @FXML private Label lblBookTitle, lblBookAuthor, lblStatus;
    @FXML private ProgressBar progressBar, readingProgress;
    @FXML private Label lblPageInfo, lblReadPercent, lblZoom;
    @FXML private VBox chatPanel, chatMessages;
    @FXML private ScrollPane chatScroll;
    @FXML private Label lblSelectedText;
    @FXML private TextArea txtUserMessage;
    @FXML private Button btnToggleChat;

    private Book book;
    private String currentSelectedText = "";
    private final GroqService grokService = new GroqService();
    private boolean chatVisible = false;
    private JavaBridge javaBridge;
    private int currentZoom = 100;

    public void initData(Book book) {
        this.book = book;
        lblBookTitle.setText(book.getTitle());
        lblBookAuthor.setText(book.getAuthor() != null ? book.getAuthor() : "Unknown");
        loadPdf(book.getPdfUrl());
    }

    private void loadPdf(String pdfUrl) {
        if (pdfUrl == null || pdfUrl.isBlank()) {
            lblStatus.setText("No PDF available for this book.");
            lblStatus.setStyle("-fx-text-fill: #dc3545;");
            return;
        }

        WebEngine engine = pdfWebView.getEngine();
        engine.setJavaScriptEnabled(true);
        progressBar.setVisible(true);
        lblStatus.setText("Loading...");

        // Convert local path to file:// URL
        String fileUrl;
        if (pdfUrl.startsWith("http://") || pdfUrl.startsWith("https://")) {
            fileUrl = pdfUrl;
        } else {
            File f = new File(pdfUrl);
            if (!f.exists()) {
                lblStatus.setText("File not found: " + pdfUrl);
                lblStatus.setStyle("-fx-text-fill: #dc3545;");
                progressBar.setVisible(false);
                return;
            }
            // Convert Windows path to proper file URI
            fileUrl = f.toURI().toString();
        }

        final String finalFileUrl = fileUrl;

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                progressBar.setVisible(false);
                lblStatus.setText("Ready");

                // Keep strong reference so JVM doesn't GC the bridge
                javaBridge = new JavaBridge();
                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("JavaBridge", javaBridge);

                engine.executeScript("loadPdf('" + finalFileUrl.replace("\\", "/").replace("'", "\\'") + "')");

                engine.executeScript(
                    "document.addEventListener('mouseup', function() {" +
                    "  var sel = window.getSelection().toString().trim();" +
                    "  if (sel.length > 0 && window.JavaBridge) window.JavaBridge.onTextSelected(sel);" +
                    "});"
                );
            }
        });

        // Load the PDF.js viewer HTML
        URL viewerUrl = getClass().getResource("/pdf_viewer.html");
        if (viewerUrl == null) {
            lblStatus.setText("PDF viewer HTML not found.");
            lblStatus.setStyle("-fx-text-fill: #dc3545;");
            return;
        }
        engine.load(viewerUrl.toExternalForm());
    }

    // ── Java Bridge ───────────────────────────────────────────────────────────

    public class JavaBridge {
        public void onPdfLoaded(int totalPages) {
            Platform.runLater(() -> {
                lblPageInfo.setText("Page 1 of " + totalPages);
                lblStatus.setText("Ready");
            });
        }

        public void onPageChanged(int current, int total, int pct) {
            Platform.runLater(() -> updateProgress(current, total, pct));
        }

        public void onTextSelected(String text) {
            Platform.runLater(() -> {
                currentSelectedText = text;
                String preview = text.length() > 120 ? text.substring(0, 120) + "..." : text;
                lblSelectedText.setText(preview);
                lblSelectedText.setStyle("-fx-font-size: 12; -fx-text-fill: #e2e8f0; -fx-font-style: italic; -fx-padding: 4 0 0 0;");
                if (!chatVisible) showChatPanel(true);
            });
        }
    }

    // ── Reading Progress ──────────────────────────────────────────────────────

    private void updateProgress(int current, int total, int pct) {
        readingProgress.setProgress(pct / 100.0);
        lblReadPercent.setText(pct + "%");
        lblPageInfo.setText("Page " + current + " of " + total);
        String color = pct < 33 ? "#0d6efd" : pct < 66 ? "#f59e0b" : "#10b981";
        readingProgress.setStyle("-fx-accent: " + color + ";");
        lblReadPercent.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
    }

    // ── Chat Panel ────────────────────────────────────────────────────────────

    @FXML private void handleToggleChat() { showChatPanel(!chatVisible); }

    private void showChatPanel(boolean show) {
        chatVisible = show;
        chatPanel.setVisible(show);
        chatPanel.setManaged(show);
    }

    @FXML
    private void handleExplain() {
        if (currentSelectedText.isBlank()) { addSystemMessage("Select text from the PDF first."); return; }
        sendToGrok("Explain this passage: \"" + currentSelectedText + "\"", currentSelectedText);
    }

    @FXML
    private void handleSend() {
        String msg = txtUserMessage.getText().trim();
        if (msg.isBlank()) return;
        txtUserMessage.clear();
        sendToGrok(msg, currentSelectedText);
    }

    @FXML
    private void handleClearChat() {
        chatMessages.getChildren().clear();
        currentSelectedText = "";
        lblSelectedText.setText("Select text in the PDF...");
        lblSelectedText.setStyle("-fx-font-size: 12; -fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-padding: 4 0 0 0;");
    }

    private void sendToGrok(String userMessage, String selectedText) {
        addUserBubble(userMessage);
        Label aiLabel = new Label("");
        aiLabel.setWrapText(true);
        aiLabel.setMaxWidth(280);
        aiLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #e2e8f0;");
        HBox aiBubble = new HBox(aiLabel);
        aiBubble.setStyle("-fx-background-color: #252535; -fx-background-radius: 10; -fx-padding: 10 12;");
        HBox aiRow = new HBox(aiBubble);
        aiRow.setAlignment(Pos.CENTER_LEFT);
        chatMessages.getChildren().add(aiRow);
        scrollToBottom();
        txtUserMessage.setDisable(true);

        StringBuilder full = new StringBuilder();
        grokService.explainAsync(selectedText, userMessage,
            token -> Platform.runLater(() -> { full.append(token); aiLabel.setText(full.toString()); scrollToBottom(); }),
            () -> Platform.runLater(() -> txtUserMessage.setDisable(false)),
            error -> Platform.runLater(() -> { aiLabel.setText("Error: " + error); aiLabel.setStyle("-fx-text-fill: #f87171;"); txtUserMessage.setDisable(false); })
        );
    }

    private void addUserBubble(String text) {
        Label lbl = new Label(text);
        lbl.setWrapText(true); lbl.setMaxWidth(260);
        lbl.setStyle("-fx-font-size: 13; -fx-text-fill: white;");
        HBox bubble = new HBox(lbl);
        bubble.setStyle("-fx-background-color: #0d6efd; -fx-background-radius: 10; -fx-padding: 10 12;");
        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER_RIGHT);
        chatMessages.getChildren().add(row);
        scrollToBottom();
    }

    private void addSystemMessage(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 12; -fx-text-fill: #64748b; -fx-font-style: italic;");
        HBox row = new HBox(lbl);
        row.setAlignment(Pos.CENTER);
        chatMessages.getChildren().add(row);
        scrollToBottom();
    }

    private void scrollToBottom() { chatScroll.setVvalue(1.0); }

    @FXML private void handleBack() { NovaDashboardController.loadPage("/views/library/MyLibrary.fxml"); }
    @FXML private void handleReload() { if (book != null) loadPdf(book.getPdfUrl()); }

    @FXML private void handlePrev() {
        try { pdfWebView.getEngine().executeScript("prevPage()"); } catch (Exception ignored) {}
    }
    @FXML private void handleNext() {
        try { pdfWebView.getEngine().executeScript("nextPage()"); } catch (Exception ignored) {}
    }
    @FXML private void handleZoomIn() {
        currentZoom = Math.min(currentZoom + 20, 300);
        if (lblZoom != null) lblZoom.setText(currentZoom + "%");
        try { pdfWebView.getEngine().executeScript("setZoom(" + currentZoom + ")"); } catch (Exception ignored) {}
    }
    @FXML private void handleZoomOut() {
        currentZoom = Math.max(currentZoom - 20, 40);
        if (lblZoom != null) lblZoom.setText(currentZoom + "%");
        try { pdfWebView.getEngine().executeScript("setZoom(" + currentZoom + ")"); } catch (Exception ignored) {}
    }
}
