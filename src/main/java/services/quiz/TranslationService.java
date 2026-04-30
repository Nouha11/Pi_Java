package services.quiz;

import org.json.JSONObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates text using the MyMemory API — completely free, no key required.
 * Limit: ~5000 words/day on the anonymous tier.
 *
 * Supported language codes (subset):
 *   en  English   fr  French    ar  Arabic    de  German
 *   es  Spanish   it  Italian   pt  Portuguese  zh  Chinese
 *   ja  Japanese  ru  Russian   tr  Turkish   nl  Dutch
 */
public class TranslationService {

    private static final String API_URL = "https://api.mymemory.translated.net/get";

    /** Human-readable language names → ISO codes shown in the UI ComboBox. */
    public static final Map<String, String> LANGUAGES = new LinkedHashMap<>();

    static {
        LANGUAGES.put("English",    "en");
        LANGUAGES.put("French",     "fr");
        LANGUAGES.put("Arabic",     "ar");
        LANGUAGES.put("German",     "de");
        LANGUAGES.put("Spanish",    "es");
        LANGUAGES.put("Italian",    "it");
        LANGUAGES.put("Portuguese", "pt");
        LANGUAGES.put("Chinese",    "zh");
        LANGUAGES.put("Japanese",   "ja");
        LANGUAGES.put("Russian",    "ru");
        LANGUAGES.put("Turkish",    "tr");
        LANGUAGES.put("Dutch",      "nl");
    }

    private final HttpClient http;

    public TranslationService() {
        http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    /**
     * Translates {@code text} from English to the given target language code.
     * Returns the original text unchanged if translation fails.
     * Blocking — always call from a background thread.
     *
     * @param text       text to translate
     * @param targetLang ISO 639-1 code, e.g. "fr", "ar", "de"
     * @return translated text, or original on failure
     */
    public String translate(String text, String targetLang) {
        if (text == null || text.isBlank()) return text;
        if ("en".equalsIgnoreCase(targetLang))  return text; // no-op for English

        try {
            String encoded  = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String langPair = URLEncoder.encode("en|" + targetLang, StandardCharsets.UTF_8);
            String url      = API_URL + "?q=" + encoded + "&langpair=" + langPair;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                String translated = json
                        .getJSONObject("responseData")
                        .getString("translatedText");

                // MyMemory returns the original text when it can't translate
                if (translated != null && !translated.isBlank()) {
                    return translated;
                }
            }
        } catch (Exception e) {
            System.err.println("[TranslationService] Failed: " + e.getMessage());
        }

        return text; // fall back to original silently
    }
}
