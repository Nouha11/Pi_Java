package services.gamification;

import models.gamification.Reward;
import utils.MyConnection;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.InputStream;
import java.sql.*;
import java.util.List;
import java.util.Properties;

/**
 * Sends a congratulatory email to a user when they earn new achievements.
 * Uses Gmail SMTP with the App Password configured in config.properties.
 *
 * Config keys (in src/main/resources/config.properties):
 *   MAIL_FROM     — sender address  (e.g. oussamastudy2@gmail.com)
 *   MAIL_PASSWORD — Gmail App Password (16-char, no spaces)
 */
public class AchievementMailService {

    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final int    SMTP_PORT = 587;

    // ── Send achievement email ────────────────────────────────────────────────

    /**
     * Send a "You earned new achievements!" email to the user.
     * Runs on a daemon thread so it never blocks the UI.
     *
     * @param userId      the user who won
     * @param gameName    name of the game they just completed
     * @param newRewards  list of rewards they just earned (not previously earned)
     */
    public void sendAsync(int userId, String gameName, List<Reward> newRewards) {
        if (newRewards == null || newRewards.isEmpty()) return;

        Thread t = new Thread(() -> {
            try {
                String[] userInfo = getUserEmailAndName(userId);
                if (userInfo == null || userInfo[0] == null || userInfo[0].isBlank()) {
                    System.out.println("[AchievementMail] No email for userId=" + userId + " — skipping.");
                    return;
                }
                String toEmail  = userInfo[0];
                String username = userInfo[1] != null ? userInfo[1] : "Player";

                String[] creds = loadCredentials();
                if (creds == null) {
                    System.err.println("[AchievementMail] Mail credentials not configured in config.properties.");
                    return;
                }
                String fromEmail = creds[0];
                String password  = creds[1];

                send(fromEmail, password, toEmail, username, gameName, newRewards);
                System.out.println("[AchievementMail] Sent to " + toEmail + " (" + newRewards.size() + " achievements)");
            } catch (Exception e) {
                System.err.println("[AchievementMail] Failed: " + e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // ── Core send logic ───────────────────────────────────────────────────────

    private void send(String fromEmail, String password,
                      String toEmail, String username,
                      String gameName, List<Reward> rewards) throws MessagingException {

        Properties props = new Properties();
        props.put("mail.smtp.auth",            "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host",            SMTP_HOST);
        props.put("mail.smtp.port",            String.valueOf(SMTP_PORT));
        props.put("mail.smtp.ssl.trust",       SMTP_HOST);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(fromEmail, password);
            }
        });

        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(fromEmail));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        msg.setSubject("You earned new achievements in NOVA!", "UTF-8");
        msg.setContent(buildHtml(username, gameName, rewards), "text/html; charset=utf-8");

        Transport.send(msg);
    }

    // ── HTML email body ───────────────────────────────────────────────────────

    private String buildHtml(String username, String gameName, List<Reward> rewards) {
        StringBuilder rows = new StringBuilder();
        for (Reward r : rewards) {
            String icon = switch (r.getType()) {
                case "BADGE"        -> "🥇";
                case "ACHIEVEMENT"  -> "🏆";
                case "BONUS_XP"     -> "⭐";
                case "BONUS_TOKENS" -> "🪙";
                default             -> "🎁";
            };
            rows.append("""
                <tr>
                  <td style="padding:10px 16px;font-size:22px;">%s</td>
                  <td style="padding:10px 0;">
                    <strong style="color:#1e2a5e;font-size:14px;">%s</strong><br>
                    <span style="color:#718096;font-size:12px;">%s &nbsp;·&nbsp; +%d pts</span>
                  </td>
                </tr>
                """.formatted(icon, r.getName(), r.getType(), r.getValue()));
        }

        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background:#f0f2f8;font-family:Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f0f2f8;padding:32px 0;">
                <tr><td align="center">
                  <table width="560" cellpadding="0" cellspacing="0"
                         style="background:white;border-radius:16px;overflow:hidden;
                                box-shadow:0 4px 24px rgba(0,0,0,0.08);">

                    <!-- Header -->
                    <tr>
                      <td style="background:linear-gradient(135deg,#667eea,#764ba2);
                                 padding:32px 40px;text-align:center;">
                        <div style="font-size:48px;margin-bottom:8px;">🏆</div>
                        <h1 style="color:white;margin:0;font-size:24px;font-weight:bold;">
                          New Achievement Unlocked!
                        </h1>
                        <p style="color:rgba(255,255,255,0.8);margin:8px 0 0;font-size:14px;">
                          Congratulations, %s!
                        </p>
                      </td>
                    </tr>

                    <!-- Game info -->
                    <tr>
                      <td style="padding:24px 40px 8px;text-align:center;">
                        <p style="color:#718096;font-size:14px;margin:0;">
                          You just completed <strong style="color:#3b4fd8;">%s</strong>
                          and earned the following rewards:
                        </p>
                      </td>
                    </tr>

                    <!-- Rewards table -->
                    <tr>
                      <td style="padding:8px 40px 24px;">
                        <table width="100%%" cellpadding="0" cellspacing="0"
                               style="background:#f8f9ff;border-radius:12px;
                                      border:1px solid #e4e8f0;overflow:hidden;">
                          %s
                        </table>
                      </td>
                    </tr>

                    <!-- CTA -->
                    <tr>
                      <td style="padding:0 40px 32px;text-align:center;">
                        <p style="color:#a0aec0;font-size:12px;margin:0;">
                          Keep playing to unlock more achievements and climb the leaderboard!
                        </p>
                      </td>
                    </tr>

                    <!-- Footer -->
                    <tr>
                      <td style="background:#f8f9ff;padding:16px 40px;text-align:center;
                                 border-top:1px solid #e4e8f0;">
                        <p style="color:#a0aec0;font-size:11px;margin:0;">
                          NOVA Platform &nbsp;·&nbsp; This is an automated notification.
                        </p>
                      </td>
                    </tr>

                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(username, gameName, rows.toString());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns [email, username] for the given userId, or null if not found. */
    private String[] getUserEmailAndName(int userId) {
        try {
            Connection conn = MyConnection.getInstance().getCnx();
            String sql = "SELECT email, username FROM user WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return new String[]{rs.getString("email"), rs.getString("username")};
            }
        } catch (Exception e) {
            System.err.println("[AchievementMail] DB error: " + e.getMessage());
        }
        return null;
    }

    /** Load MAIL_FROM and MAIL_PASSWORD from config.properties. */
    private String[] loadCredentials() {
        String[] paths = {"config.properties", "config/api.properties"};
        for (String path : paths) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
                if (is == null) continue;
                Properties p = new Properties();
                p.load(is);
                String from = p.getProperty("MAIL_FROM");
                String pass = p.getProperty("MAIL_PASSWORD");
                if (from != null && !from.isBlank() && pass != null && !pass.isBlank()) {
                    return new String[]{from.trim(), pass.trim()};
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
