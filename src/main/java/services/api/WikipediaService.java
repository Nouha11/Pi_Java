package services.api;

import org.json.JSONObject;
import utils.ApiConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for querying the Wikipedia REST API to fetch article summaries
 * related to a course topic. Results are cached in memory for the application session.
 *
 * No API key is required; uses the public Wikipedia REST API.
 *
 * Feature: course-learning-experience
 * Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 12.4
 */
public class WikipediaService {

    // In-memory cache: courseName → WikiSummary
    private final Map<String, WikiSummary> cache = new HashMap<>();

    private final HttpClient httpClient;

    public WikipediaService() {
        this.httpClient = HttpClient.newHttpClient();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Fetches a Wikipedia summary for the given course name.
     * Returns a cached result if the same course name was already fetched this session.
     * Returns null on 404 (topic not found) or any other error.
     *
     * Feature: course-learning-experience, Property 8: same query returns cached result without HTTP call
     * Feature: course-learning-experience, Property 9: spaces replaced with underscores in topic URL
     */
    public WikiSummary fetchSummary(String courseName) {
        if (courseName == null || courseName.isBlank()) {
            return null;
        }

        // Cache hit — return immediately without an HTTP call
        if (cache.containsKey(courseName)) {
            return cache.get(courseName);
        }

        String url = buildTopicUrl(courseName);
        if (url == null) {
            return null;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "NovaLearningPlatform/1.0 (Educational Application; Java HttpClient)")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                System.err.println("[WikipediaService] Topic not found (404) for: " + courseName);
                return null;
            }

            if (response.statusCode() != 200) {
                System.err.println("[WikipediaService] API returned status "
                        + response.statusCode() + " for: " + courseName);
                return null;
            }

            WikiSummary summary = parseResponse(response.body());
            if (summary != null) {
                cache.put(courseName, summary);
            }
            return summary;

        } catch (Exception e) {
            System.err.println("[WikipediaService] Error fetching Wikipedia summary for '"
                    + courseName + "': " + e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // URL building
    // -------------------------------------------------------------------------

    /**
     * Builds the Wikipedia REST API URL for the given course name.
     * Spaces are encoded as underscores; other special characters are URL-encoded.
     *
     * Strategy: URLEncoder.encode(courseName, UTF-8) encodes spaces as '+',
     * then replace '+' with '_' to get Wikipedia-style underscored topics.
     *
     * Feature: course-learning-experience, Property 9: spaces replaced with underscores in topic URL
     */
    String buildTopicUrl(String courseName) {
        String baseUrl = ApiConfig.get("wikipedia.base.url");
        if (baseUrl == null) {
            return null;
        }

        // URLEncoder encodes spaces as '+'; replace '+' with '_' for Wikipedia topic format
        String topic = URLEncoder.encode(courseName, StandardCharsets.UTF_8)
                .replace("+", "_");

        return baseUrl + "/page/summary/" + topic;
    }

    // -------------------------------------------------------------------------
    // JSON parsing
    // -------------------------------------------------------------------------

    /**
     * Parses the Wikipedia REST API JSON response and extracts a WikiSummary.
     *
     * Extracts:
     *   title                        — article title
     *   extract                      — plain text summary
     *   thumbnail.source             — image URL (nullable)
     *   content_urls.desktop.page    — full article URL
     *
     * Returns null if parsing fails.
     */
    WikiSummary parseResponse(String json) {
        try {
            JSONObject root = new JSONObject(json);

            String title = root.optString("title", null);
            String extract = root.optString("extract", null);

            // thumbnail.source is nullable
            String thumbnailUrl = null;
            JSONObject thumbnail = root.optJSONObject("thumbnail");
            if (thumbnail != null) {
                thumbnailUrl = thumbnail.optString("source", null);
            }

            // content_urls.desktop.page
            String pageUrl = null;
            JSONObject contentUrls = root.optJSONObject("content_urls");
            if (contentUrls != null) {
                JSONObject desktop = contentUrls.optJSONObject("desktop");
                if (desktop != null) {
                    pageUrl = desktop.optString("page", null);
                }
            }

            return new WikiSummary(title, extract, thumbnailUrl, pageUrl);

        } catch (Exception e) {
            System.err.println("[WikipediaService] Failed to parse Wikipedia API response: " + e.getMessage());
            return null;
        }
    }
}
