package services.api;

import org.json.JSONArray;
import org.json.JSONObject;
import utils.ApiConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for querying the YouTube Data API v3 to fetch video recommendations
 * related to a course. Results are cached in memory for the application session.
 *
 * Feature: course-learning-experience
 * Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.7, 4.8, 12.4, 12.5
 */
public class YouTubeService {

    private static final String YOUTUBE_SEARCH_URL =
            "https://www.googleapis.com/youtube/v3/search";

    private static final int MAX_RESULTS = 5;

    /** Common English stop words to exclude from keyword extraction. */
    private static final Set<String> STOP_WORDS = Set.of(
            "this", "that", "with", "from", "have", "will", "been", "were",
            "they", "them", "their", "what", "when", "where", "which", "while",
            "your", "into", "more", "also", "some", "such", "than", "then",
            "these", "those", "about", "after", "before", "other", "over",
            "under", "very", "just", "each", "both", "only", "even", "most",
            "many", "much", "well", "here", "there", "through", "between",
            "during", "without", "within", "along", "following", "across",
            "behind", "beyond", "plus", "except", "until", "upon", "around",
            "among", "course", "learn", "study", "using", "used", "make",
            "made", "take", "taken", "give", "given", "come", "goes", "going"
    );

    // In-memory cache: query string → list of VideoResult
    private final Map<String, List<VideoResult>> cache = new HashMap<>();

    private final HttpClient httpClient;

    public YouTubeService() {
        this.httpClient = HttpClient.newHttpClient();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Searches YouTube for videos related to the given course.
     * Returns a cached result if the same query was already executed this session.
     * Returns an empty list on any error (network, quota, invalid key, parse failure).
     *
     * Feature: course-learning-experience, Property 7: search query contains course name and category
     * Feature: course-learning-experience, Property 8: same query returns cached result without HTTP call
     */
    public List<VideoResult> searchVideos(String courseName, String category, String description) {
        String query = buildSearchQuery(courseName, category, description);

        // Cache hit — return immediately without an HTTP call
        if (cache.containsKey(query)) {
            return cache.get(query);
        }

        List<VideoResult> results = callYouTubeApi(query);
        cache.put(query, results);
        return results;
    }

    // -------------------------------------------------------------------------
    // Query building
    // -------------------------------------------------------------------------

    /**
     * Builds the YouTube search query string.
     * Format: "{courseName} {category} {keyword1} {keyword2} {keyword3}"
     * Up to 3 keywords are extracted from the description (first 3 non-stop-words of 4+ characters).
     *
     * Feature: course-learning-experience, Property 7: search query contains course name and category
     */
    String buildSearchQuery(String courseName, String category, String description) {
        StringBuilder sb = new StringBuilder();

        if (courseName != null && !courseName.isBlank()) {
            sb.append(courseName.trim());
        }
        if (category != null && !category.isBlank()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(category.trim());
        }

        List<String> keywords = extractKeywords(description, 3);
        for (String kw : keywords) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(kw);
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Keyword extraction
    // -------------------------------------------------------------------------

    /**
     * Extracts up to {@code maxCount} keywords from the description.
     * A keyword is a word of 4+ characters that is not in the stop-word list.
     * Words are taken in order of appearance (first N qualifying words).
     */
    List<String> extractKeywords(String description, int maxCount) {
        if (description == null || description.isBlank()) {
            return Collections.emptyList();
        }

        List<String> keywords = new ArrayList<>();
        // Split on any non-alphabetic character
        String[] tokens = description.split("[^a-zA-Z]+");

        for (String token : tokens) {
            if (keywords.size() >= maxCount) break;
            String word = token.toLowerCase();
            if (word.length() >= 4 && !STOP_WORDS.contains(word)) {
                keywords.add(word);
            }
        }

        return keywords;
    }

    // -------------------------------------------------------------------------
    // HTTP call
    // -------------------------------------------------------------------------

    /**
     * Calls the YouTube Data API v3 search endpoint and returns parsed results.
     * Returns an empty list on any error.
     */
    private List<VideoResult> callYouTubeApi(String query) {
        String apiKey = ApiConfig.get("youtube.api.key");
        if (apiKey == null) {
            // ApiConfig already logged the warning; return empty gracefully
            return Collections.emptyList();
        }

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = YOUTUBE_SEARCH_URL
                    + "?part=snippet"
                    + "&q=" + encodedQuery
                    + "&maxResults=" + MAX_RESULTS
                    + "&type=video"
                    + "&key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseResponse(response.body());
            } else {
                System.err.println("[YouTubeService] API returned status "
                        + response.statusCode() + " for query: " + query);
                return Collections.emptyList();
            }

        } catch (Exception e) {
            System.err.println("[YouTubeService] Error calling YouTube API: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // JSON parsing
    // -------------------------------------------------------------------------

    /**
     * Parses the YouTube API JSON response and extracts VideoResult objects.
     *
     * Extracts from each item:
     *   items[].id.videoId
     *   items[].snippet.title
     *   items[].snippet.channelTitle
     *   items[].snippet.thumbnails.default.url
     *
     * Feature: course-learning-experience, Property 7 (query contains identifiers validated upstream)
     */
    List<VideoResult> parseResponse(String json) {
        List<VideoResult> results = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            JSONArray items = root.optJSONArray("items");
            if (items == null) {
                return results;
            }

            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);

                // Extract videoId
                JSONObject idObj = item.optJSONObject("id");
                if (idObj == null) continue;
                String videoId = idObj.optString("videoId", null);
                if (videoId == null || videoId.isBlank()) continue;

                // Extract snippet fields
                JSONObject snippet = item.optJSONObject("snippet");
                if (snippet == null) continue;

                String title = snippet.optString("title", "(No title)");
                String channelName = snippet.optString("channelTitle", "(Unknown channel)");

                // Extract thumbnail URL (default quality)
                String thumbnailUrl = null;
                JSONObject thumbnails = snippet.optJSONObject("thumbnails");
                if (thumbnails != null) {
                    JSONObject defaultThumb = thumbnails.optJSONObject("default");
                    if (defaultThumb != null) {
                        thumbnailUrl = defaultThumb.optString("url", null);
                    }
                }

                results.add(new VideoResult(videoId, title, channelName, thumbnailUrl));
            }

        } catch (Exception e) {
            System.err.println("[YouTubeService] Failed to parse YouTube API response: " + e.getMessage());
        }
        return results;
    }
}
