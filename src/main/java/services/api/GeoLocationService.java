package services.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * GeoLocationService — Chained API:
 *  1. ip-api.com       → IP, city, country code, lat, lon, timezone
 *  2. restcountries.com → full country name + flag emoji
 * No API key required.
 */
public class GeoLocationService {

    private static final String IPAPI_URL         = "http://ip-api.com/json/";
    private static final String RESTCOUNTRIES_URL = "https://restcountries.com/v3.1/alpha/";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    // ── Result model ──────────────────────────────────────────────────────────

    public static class GeoInfo {
        public final String ip;
        public final String city;
        public final String region;
        public final String countryCode;
        public final String countryName;
        public final String flagEmoji;
        public final String timezone;
        public final String currency;
        public final double lat;
        public final double lon;

        public GeoInfo(String ip, String city, String region,
                       String countryCode, String countryName,
                       String flagEmoji, String timezone,
                       String currency, double lat, double lon) {
            this.ip          = ip;
            this.city        = city;
            this.region      = region;
            this.countryCode = countryCode;
            this.countryName = countryName;
            this.flagEmoji   = flagEmoji;
            this.timezone    = timezone;
            this.currency    = currency;
            this.lat         = lat;
            this.lon         = lon;
        }

        public String getSummary() {
            // JavaFX Labels cannot render flag emoji surrogate pairs
            // Use country code in brackets instead
            String prefix = countryCode != null ? "[" + countryCode + "] " : "";
            return prefix + city + ", " + countryName;
        }

        public String getTimezoneDisplay() {
            return "\uD83D\uDD50  " + timezone;
        }
    }

    // ── Main method ───────────────────────────────────────────────────────────

    public GeoInfo fetchCurrentLocation() {
        try {
            // Step 1: ip-api.com
            String ipapiJson = get(IPAPI_URL);
            if (ipapiJson == null) return null;

            String ip          = extract(ipapiJson, "query");
            String city        = extract(ipapiJson, "city");
            String region      = extract(ipapiJson, "regionName");
            String countryCode = extract(ipapiJson, "countryCode");
            String timezone    = extract(ipapiJson, "timezone");
            String latStr      = extract(ipapiJson, "lat");
            String lonStr      = extract(ipapiJson, "lon");

            if (countryCode == null) return null;

            double lat = 0, lon = 0;
            try { if (latStr != null) lat = Double.parseDouble(latStr); } catch (Exception ignored) {}
            try { if (lonStr != null) lon = Double.parseDouble(lonStr); } catch (Exception ignored) {}

            // Step 2: restcountries.com
            String countryName = countryCode;
            String flagEmoji   = countryCodeToFlag(countryCode);
            String currency    = "";

            String rcJson = get(RESTCOUNTRIES_URL + countryCode.toLowerCase());
            if (rcJson != null) {
                String common = extractNested(rcJson, "common");
                if (common != null) countryName = common;
                String flag = extract(rcJson, "flag");
                if (flag != null && !flag.isBlank()) flagEmoji = flag;
            }

            return new GeoInfo(
                ip      != null ? ip      : "Unknown",
                city    != null ? city    : "Unknown",
                region  != null ? region  : "",
                countryCode,
                countryName,
                flagEmoji,
                timezone != null ? timezone : "Unknown",
                currency,
                lat, lon
            );
        } catch (Exception e) {
            System.err.println("[GeoLocation] Error: " + e.getMessage());
            return null;
        }
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private String get(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 ? resp.body() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private String extract(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        idx += search.length();
        while (idx < json.length() && json.charAt(idx) == ' ') idx++;
        if (idx >= json.length()) return null;
        char first = json.charAt(idx);
        if (first == '"') {
            int end = json.indexOf('"', idx + 1);
            return end > idx ? json.substring(idx + 1, end) : null;
        } else if (first != '{' && first != '[') {
            int end = idx;
            while (end < json.length() && ",}\n".indexOf(json.charAt(end)) < 0) end++;
            String val = json.substring(idx, end).trim();
            return val.equals("null") ? null : val;
        }
        return null;
    }

    private String extractNested(String json, String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        idx += search.length();
        int end = json.indexOf('"', idx);
        return end > idx ? json.substring(idx, end) : null;
    }

    private String countryCodeToFlag(String code) {
        if (code == null || code.length() != 2) return "";
        try {
            int base = 0x1F1E6 - 'A';
            return new String(Character.toChars(base + Character.toUpperCase(code.charAt(0))))
                 + new String(Character.toChars(base + Character.toUpperCase(code.charAt(1))));
        } catch (Exception e) { return ""; }
    }
}
