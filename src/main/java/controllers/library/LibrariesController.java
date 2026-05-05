package controllers.library;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import models.library.Book;
import models.library.Library;
import netscape.javascript.JSObject;
import services.library.LibraryService;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

/**
 * Libraries view controller.
 * - Full-height OpenStreetMap with custom markers
 * - Compact sidebar with library cards sorted by distance
 * - Geolocation via browser API → sorts libraries by proximity
 * - Clicking a card focuses the map; clicking map popup selects library
 */
public class LibrariesController {

    @FXML private Label lblBookName;
    @FXML private Label lblSortInfo;
    @FXML private Label lblLocationStatus;
    @FXML private VBox librariesContainer;
    @FXML private WebView mapView;

    private Book book;
    private List<Library> libraries;
    private WebEngine webEngine;
    private MapBridge mapBridge;
    private ServerSocket localServer;
    private static final int SERVER_PORT = 18765;

    // User's location (null until obtained)
    private double userLat = Double.NaN;
    private double userLng = Double.NaN;

    @FXML
    public void initialize() {
        webEngine = mapView.getEngine();
        webEngine.setJavaScriptEnabled(true);
        startLocalServer();
        // Load map over http://localhost so browser geolocation works
        webEngine.load("http://localhost:" + SERVER_PORT + "/map.html");
    }

    /**
     * Starts a minimal HTTP server on localhost that serves map.html.
     * This is necessary because navigator.geolocation only works over http/https,
     * not file:// URLs.
     */
    private void startLocalServer() {
        new Thread(() -> {
            try {
                localServer = new ServerSocket(SERVER_PORT);
                while (!localServer.isClosed()) {
                    try {
                        Socket client = localServer.accept();
                        new Thread(() -> handleRequest(client)).start();
                    } catch (IOException ignored) {}
                }
            } catch (IOException e) {
                System.err.println("Map server error: " + e.getMessage());
            }
        }, "map-server").start();
    }

    private void handleRequest(Socket client) {
        try (client) {
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            String requestLine = in.readLine();
            if (requestLine == null) return;

            // Read map.html from resources
            InputStream resource = getClass().getResourceAsStream("/map.html");
            if (resource == null) return;
            byte[] content = resource.readAllBytes();

            OutputStream out = client.getOutputStream();
            String header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=UTF-8\r\n" +
                    "Content-Length: " + content.length + "\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(content);
            out.flush();
        } catch (IOException ignored) {}
    }

    public void initData(Book book) {
        this.book = book;
        lblBookName.setText(book.getTitle());
        libraries = new LibraryService().findByBook(book.getId());

        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                mapBridge = new MapBridge();
                JSObject window = (JSObject) webEngine.executeScript("window");
                window.setMember("JavaBridge", mapBridge);
                addAllMarkers();
            }
        });
    }

    // ── MAP OPERATIONS ────────────────────────────────────────────────────────

    private void addAllMarkers() {
        webEngine.executeScript("clearMarkers()");
        for (Library lib : libraries) {
            if (lib.getLatitude() != 0 && lib.getLongitude() != 0) {
                String name    = escape(lib.getName());
                String address = lib.getAddress() != null ? escape(lib.getAddress()) : "";
                String dist    = Double.isNaN(userLat) ? "" : formatDistance(distanceTo(lib));
                // Use Locale.US to force dot as decimal separator
                webEngine.executeScript(String.format(java.util.Locale.US,
                        "addMarker(%f, %f, '%s', '%s', '%s', %d)",
                        lib.getLatitude(), lib.getLongitude(), name, address, dist, lib.getId()
                ));
            }
        }
        webEngine.executeScript("fitAll()");
        buildSidebarCards();
    }

    @FXML
    private void handleFitAll() {
        if (webEngine != null) webEngine.executeScript("fitAll()");
    }

    @FXML
    private void handleGetLocation() {
        // JavaFX WebView doesn't support navigator.geolocation
        // Best approach: let user click on the map to place their pin
        lblLocationStatus.setText("Click anywhere on the map to set your location");
        // Zoom to Tunisia as a starting point
        webEngine.executeScript("map.setView([36.8065, 10.1815], 12)");
    }

    private void geocodeCity(String cityName) {
        try {
            String encoded = java.net.URLEncoder.encode(cityName, "UTF-8");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL("https://nominatim.openstreetmap.org/search?q=" +
                            encoded + "&format=json&limit=1")
                    .openConnection();
            conn.setRequestProperty("User-Agent", "NovaLibraryApp/1.0");
            conn.setConnectTimeout(6000); conn.setReadTimeout(6000);
            String body = new String(conn.getInputStream().readAllBytes());
            org.json.JSONArray arr = new org.json.JSONArray(body);
            if (!arr.isEmpty()) {
                double lat = arr.getJSONObject(0).getDouble("lat");
                double lng = arr.getJSONObject(0).getDouble("lon");
                Platform.runLater(() -> {
                    userLat = lat; userLng = lng;
                    lblLocationStatus.setText("📍 " + cityName);
                    webEngine.executeScript(String.format(java.util.Locale.US, "setUserLocation(%f, %f)", lat, lng));
                    addAllMarkers();
                });
            } else {
                Platform.runLater(() -> lblLocationStatus.setText("City not found."));
            }
        } catch (Exception e) {
            Platform.runLater(() -> lblLocationStatus.setText("Error: " + e.getMessage()));
        }
    }

    // ── SIDEBAR CARDS ─────────────────────────────────────────────────────────

    private void buildSidebarCards() {
        // Sort by distance if we have user location, otherwise by name
        List<Library> sorted;
        if (!Double.isNaN(userLat)) {
            sorted = libraries.stream()
                    .filter(l -> l.getLatitude() != 0 && l.getLongitude() != 0)
                    .sorted(Comparator.comparingDouble(this::distanceTo))
                    .toList();
            lblSortInfo.setText("Sorted by distance from you");
        } else {
            sorted = libraries.stream()
                    .filter(l -> l.getLatitude() != 0 && l.getLongitude() != 0)
                    .sorted(Comparator.comparing(Library::getName))
                    .toList();
            lblSortInfo.setText("Sorted by name — click 'My Location' to sort by distance");
        }

        librariesContainer.getChildren().clear();

        if (sorted.isEmpty()) {
            Label empty = new Label("No libraries available for this book.");
            empty.setStyle("-fx-font-size: 13; -fx-text-fill: rgba(255,255,255,0.4); -fx-padding: 20;");
            empty.setWrapText(true);
            librariesContainer.getChildren().add(empty);
            return;
        }

        for (int i = 0; i < sorted.size(); i++) {
            librariesContainer.getChildren().add(buildLibraryCard(sorted.get(i), i + 1));
        }
    }

    private VBox buildLibraryCard(Library lib, int rank) {
        VBox card = new VBox(8);
        card.setStyle("-fx-background-color: #1e293b; -fx-background-radius: 12; " +
                "-fx-border-color: #334155; -fx-border-radius: 12; -fx-border-width: 1; " +
                "-fx-padding: 14 16; -fx-cursor: hand;");

        // Header row: rank + name
        HBox header = new HBox(10);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label rankLbl = new Label(String.valueOf(rank));
        rankLbl.setStyle("-fx-background-color: #6366f1; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-font-size: 12; -fx-padding: 3 8; -fx-background-radius: 20;");

        Label name = new Label(lib.getName());
        name.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: white;");
        name.setWrapText(true);
        HBox.setHgrow(name, Priority.ALWAYS);
        header.getChildren().addAll(rankLbl, name);

        // Address
        Label address = new Label(lib.getAddress() != null ? "📍 " + lib.getAddress() : "");
        address.setStyle("-fx-font-size: 11; -fx-text-fill: rgba(255,255,255,0.5);");
        address.setWrapText(true);

        // Distance badge
        HBox footer = new HBox(8);
        footer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        if (!Double.isNaN(userLat) && lib.getLatitude() != 0) {
            double dist = distanceTo(lib);
            Label distLbl = new Label("📏 " + formatDistance(dist));
            distLbl.setStyle("-fx-background-color: rgba(99,102,241,0.2); -fx-text-fill: #a5b4fc; " +
                    "-fx-padding: 3 10; -fx-background-radius: 20; -fx-font-size: 11; -fx-font-weight: bold;");
            footer.getChildren().add(distLbl);
        }

        // Loan button
        Button btnLoan = new Button("Request Loan");
        btnLoan.setMaxWidth(Double.MAX_VALUE);
        btnLoan.setStyle("-fx-background-color: linear-gradient(to right, #6366f1, #8b5cf6); " +
                "-fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; " +
                "-fx-padding: 8; -fx-cursor: hand; -fx-font-size: 12;");
        btnLoan.setOnAction(e -> openLoanForm(lib));

        card.getChildren().addAll(header, address);
        if (!footer.getChildren().isEmpty()) card.getChildren().add(footer);
        card.getChildren().add(btnLoan);

        // Click card → focus map
        card.setOnMouseClicked(e -> {
            if (lib.getLatitude() != 0 && lib.getLongitude() != 0) {
                webEngine.executeScript(String.format(java.util.Locale.US, "focusLibrary(%f, %f)", lib.getLatitude(), lib.getLongitude()));
            }
        });

        // Hover
        card.setOnMouseEntered(e -> card.setStyle(card.getStyle()
                .replace("-fx-border-color: #334155", "-fx-border-color: #6366f1")
                .replace("-fx-background-color: #1e293b", "-fx-background-color: #1e2a4a")));
        card.setOnMouseExited(e -> card.setStyle(card.getStyle()
                .replace("-fx-border-color: #6366f1", "-fx-border-color: #334155")
                .replace("-fx-background-color: #1e2a4a", "-fx-background-color: #1e293b")));

        return card;
    }

    // ── DISTANCE UTILS ────────────────────────────────────────────────────────

    /** Haversine formula — returns distance in km */
    private double distanceTo(Library lib) {
        if (Double.isNaN(userLat) || lib.getLatitude() == 0) return Double.MAX_VALUE;
        double R = 6371;
        double dLat = Math.toRadians(lib.getLatitude() - userLat);
        double dLng = Math.toRadians(lib.getLongitude() - userLng);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2)
                + Math.cos(Math.toRadians(userLat)) * Math.cos(Math.toRadians(lib.getLatitude()))
                * Math.sin(dLng/2) * Math.sin(dLng/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }

    private String formatDistance(double km) {
        if (km < 1) return String.format("%.0f m", km * 1000);
        return String.format("%.1f km", km);
    }

    private String escape(String s) {
        return s.replace("'", "\\'").replace("\n", " ");
    }

    // ── JAVA BRIDGE ───────────────────────────────────────────────────────────

    public class MapBridge {
        /** Called by JS after user clicks map or setUserLocation() is called */
        public void onLocationObtained(double lat, double lng) {
            Platform.runLater(() -> {
                userLat = lat; userLng = lng;
                lblLocationStatus.setText(String.format("📍 %.5f, %.5f", lat, lng));
                buildSidebarCards();
            });
        }

        /** Called when user clicks "Request Loan" in a map popup */
        public void onLibrarySelected(int libId) {
            Platform.runLater(() ->
                libraries.stream().filter(l -> l.getId() == libId)
                        .findFirst().ifPresent(lib -> openLoanForm(lib)));
        }

        public void onLocationError(String msg) {
            Platform.runLater(() -> lblLocationStatus.setText("Error: " + msg));
        }
    }

    // ── NAVIGATION ────────────────────────────────────────────────────────────

    private void openLoanForm(Library library) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/library/LoanFormView.fxml"));
            Parent root = loader.load();
            ((LoanFormController) loader.getController()).initData(book, library);
            controllers.NovaDashboardController.setView(root);
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Error: " + e.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/library/BookDetailView.fxml"));
            Parent root = loader.load();
            ((BookDetailController) loader.getController()).initData(book);
            controllers.NovaDashboardController.setView(root);
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Error: " + e.getMessage(), ButtonType.OK).showAndWait();
        }
    }
}
