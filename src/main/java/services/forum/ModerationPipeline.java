package services.forum;

import models.forum.Report;
import java.util.Arrays;
import java.util.List;

public class ModerationPipeline {

    // ==========================================
    // 🔥 1. THIS IS YOUR CENSOR DICTIONARY 🔥
    // You can add any words you want to block right here.
    // ==========================================
    private static final List<String> BLACKLIST = Arrays.asList(
            "badword", "idiot", "stupid", "moron", "spam", "hate", "crap"
    );

    // Allows 1 typo (e.g., "stpid" still gets caught as "stupid")
    private static final int MAX_DISTANCE = 1;

    private ReportService reportService;

    public ModerationPipeline() {
        this.reportService = new ReportService();
    }

    /**
     * Scans the text. If it finds a bad word, it replaces it with asterisks.
     */
    public String sanitize(String rawContent) {
        if (rawContent == null || rawContent.trim().isEmpty()) return rawContent;

        String[] words = rawContent.split("\\s+");
        StringBuilder sanitizedContent = new StringBuilder();

        for (String word : words) {
            String cleanWord = word.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            boolean isBad = false;

            for (String blacklisted : BLACKLIST) {
                if (cleanWord.equals(blacklisted) ||
                        (cleanWord.length() > 3 && calculateLevenshtein(cleanWord, blacklisted) <= MAX_DISTANCE)) {
                    isBad = true;
                    break;
                }
            }

            if (isBad) {
                // Replaces the word but keeps the first letter (e.g., i****)
                String censored = word.charAt(0) + word.substring(1).replaceAll("[a-zA-Z0-9]", "*");
                sanitizedContent.append(censored).append(" ");
            } else {
                sanitizedContent.append(word).append(" ");
            }
        }
        return sanitizedContent.toString().trim();
    }

    /**
     * Creates the report AFTER the post/comment is saved to the database.
     */
    public void triggerAutoReport(int userId, String originalContent, String targetType, int targetId) {
        String reason = "SYSTEM AUTO-FLAG: Profanity detected. Original text: \"" + originalContent + "\"";
        Report autoReport;

        if (targetType.equalsIgnoreCase("POST")) {
            autoReport = Report.createPostReport(userId, targetId, reason);
        } else {
            autoReport = Report.createCommentReport(userId, targetId, reason);
        }

        reportService.submitReport(autoReport);
        System.out.println("🚨 SYSTEM ALERT: Auto-Report generated for " + targetType + " #" + targetId);
    }

    /**
     * Mathematical typo-detection algorithm
     */
    private int calculateLevenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) dp[i][j] = j;
                else if (j == 0) dp[i][j] = i;
                else {
                    int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
                }
            }
        }
        return dp[a.length()][b.length()];
    }
}