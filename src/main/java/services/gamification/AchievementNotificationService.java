package services.gamification;

import models.gamification.Reward;
import utils.ApiConfig;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

/**
 * Sends achievement notifications via:
 *   1. Email  — Gmail SMTP (requires GMAIL_USER + GMAIL_APP_PASSWORD in config.properties)
 *   2. WhatsApp — Twilio API (requires TWILIO_SID + TWILIO_TOKEN + TWILIO_FROM + TWILIO_TO)
 *
 * ── Email setup ──────────────────────────────────────────────────────────────
 * 1. Enable 2FA on your Gmail account
 * 2. Go to https://myaccount.google.com/apppasswords
 * 3. Create an App Password for "Mail"
 * 4. Add to config.properties:
 *      GMAIL_USER=your@gmail.com
 *      GMAIL_APP_PASSWORD=xxxx xxxx xxxx xxxx
 *
 * ── WhatsApp setup (Twilio) ───────────────────────────────────────────────────
 * 1. Sign up free at https://www.twilio.com/try-twilio
 * 2. Get your Account SID and Auth Token from https://console.twilio.com
 * 3. Enable WhatsApp Sandbox at https://console.twilio.com/us1/develop/sms/try-it-out/whatsapp-learn
 * 4. Send "join <sandbox-word>" from your WhatsApp to +1 415 523 8886
 * 5. Add to config.properties:
 *      TWILIO_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
 *      TWILIO_TOKEN=your_auth_token
 *      TWILIO_FROM=whatsapp:+14155238886
 *      TWILIO_TO=whatsapp:+21600000000   (student's number)
 */
public class AchievementNotificationService {

    /**
     * Send achievement notification after a game is completed.
     * Tries email first, then WhatsApp. Both are optional — missing config = skip silently.
     */
    public void notifyAchievements(String username, String gameName,
                                    List<Reward> newRewards, int xpEarned, int tokensEarned) {
        if (newRewards == null || newRewards.isEmpty()) return;

        String subject = "NOVA - You earned new achievements!";
        String body    = buildMessageBody(username, gameName, newRewards, xpEarned, tokensEarned);

        // Run in background — don't block the UI
        Thread t = new Thread(() -> {
            sendEmail(subject, body);
            sendWhatsApp(body);
        });
        t.setDaemon(true);
        t.start();
    }

    // ── Email ─────────────────────────────────────────────────────────────────

    private void sendEmail(String subject, String body) {
        String user     = ApiConfig.get("GMAIL_USER");
        String password = ApiConfig.get("GMAIL_APP_PASSWORD");
        String to       = ApiConfig.get("GMAIL_TO"); // recipient, defaults to GMAIL_USER if blank

        if (user == null || password == null) {
            System.out.println("[Notification] Email skipped — GMAIL_USER/GMAIL_APP_PASSWORD not configured.");
            return;
        }
        if (to == null || to.isBlank()) to = user;

        Properties props = new Properties();
        props.put("mail.smtp.auth",            "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host",            "smtp.gmail.com");
        props.put("mail.smtp.port",            "587");
        props.put("mail.smtp.ssl.trust",       "smtp.gmail.com");

        final String finalTo = to;
        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, password);
            }
        });

        try {
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(user, "NOVA Platform"));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(finalTo));
            msg.setSubject(subject);

            // HTML email
            String html = buildHtmlEmail(body);
            msg.setContent(html, "text/html; charset=utf-8");
            Transport.send(msg);
            System.out.println("[Notification] Email sent to " + finalTo);
        } catch (Exception e) {
            System.err.println("[Notification] Email failed: " + e.getMessage());
        }
    }

    // ── WhatsApp via Twilio ───────────────────────────────────────────────────

    private void sendWhatsApp(String body) {
        String sid   = ApiConfig.get("TWILIO_SID");
        String token = ApiConfig.get("TWILIO_TOKEN");
        String from  = ApiConfig.get("TWILIO_FROM");
        String to    = ApiConfig.get("TWILIO_TO");

        if (sid == null || token == null || from == null || to == null) {
            System.out.println("[Notification] WhatsApp skipped — Twilio not configured.");
            return;
        }

        try {
            String apiUrl = "https://api.twilio.com/2010-04-01/Accounts/" + sid + "/Messages.json";
            String params = "From=" + URLEncoder.encode(from, StandardCharsets.UTF_8) +
                            "&To="   + URLEncoder.encode(to,   StandardCharsets.UTF_8) +
                            "&Body=" + URLEncoder.encode(body, StandardCharsets.UTF_8);

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);

            // Basic auth
            String auth = sid + ":" + token;
            String encoded = java.util.Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            conn.setRequestProperty("Authorization", "Basic " + encoded);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(params.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            if (status == 201) {
                System.out.println("[Notification] WhatsApp sent to " + to);
            } else {
                InputStream es = conn.getErrorStream();
                String err = es != null ? new String(es.readAllBytes()) : "status " + status;
                System.err.println("[Notification] WhatsApp failed: " + err);
            }
        } catch (Exception e) {
            System.err.println("[Notification] WhatsApp error: " + e.getMessage());
        }
    }

    // ── Message builders ──────────────────────────────────────────────────────

    private String buildMessageBody(String username, String gameName,
                                     List<Reward> rewards, int xp, int tokens) {
        StringBuilder sb = new StringBuilder();
        sb.append("Congratulations ").append(username).append("!\n\n");
        sb.append("You completed \"").append(gameName).append("\" and earned:\n");
        sb.append("  +").append(xp).append(" XP\n");
        sb.append("  +").append(tokens).append(" Tokens\n\n");
        sb.append("New Achievements Unlocked:\n");
        for (Reward r : rewards) {
            sb.append("  ").append(rewardEmoji(r.getType())).append(" ")
              .append(r.getName()).append(" (").append(r.getType()).append(")\n");
        }
        sb.append("\nKeep playing to unlock more rewards on NOVA!");
        return sb.toString();
    }

    private String buildHtmlEmail(String plainText) {
        String escaped = plainText
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\n", "<br>");
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;background:#f0f2f8;padding:20px;'>" +
               "<div style='max-width:500px;margin:0 auto;background:white;border-radius:12px;overflow:hidden;" +
               "box-shadow:0 4px 20px rgba(0,0,0,0.1);'>" +
               "<div style='background:linear-gradient(to right,#3b4fd8,#5b6ef5);padding:24px;text-align:center;'>" +
               "<h1 style='color:white;margin:0;font-size:24px;'>NOVA Platform</h1>" +
               "<p style='color:rgba(255,255,255,0.8);margin:8px 0 0;'>Achievement Notification</p></div>" +
               "<div style='padding:24px;line-height:1.8;color:#2d3748;font-size:14px;'>" +
               escaped + "</div>" +
               "<div style='background:#f8f9ff;padding:16px;text-align:center;border-top:1px solid #e4e8f0;'>" +
               "<p style='color:#a0aec0;font-size:12px;margin:0;'>NOVA - Intelligent Study Coaching Platform</p>" +
               "</div></div></body></html>";
    }

    private String rewardEmoji(String type) {
        return switch (type) {
            case "BADGE"        -> "\uD83C\uDFC5";
            case "ACHIEVEMENT"  -> "\uD83C\uDFC6";
            case "BONUS_XP"     -> "\u2B50";
            case "BONUS_TOKENS" -> "\uD83E\uDE99";
            default             -> "\uD83C\uDF81";
        };
    }
}
