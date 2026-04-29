package services.gamification;

import utils.MyConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches leaderboard data from student_profile joined with user.
 * Mirrors the Symfony leaderboard controller logic.
 */
public class LeaderboardService {

    private final Connection conn = MyConnection.getInstance().getCnx();

    public static class PlayerEntry {
        public int    rank;
        public int    userId;
        public String username;
        public String initials;
        public int    level;
        public int    totalXp;
        public int    totalTokens;
        public String levelName;
        public int    progressPct; // % to next level

        public PlayerEntry(int rank, int userId, String username, int level,
                           int totalXp, int totalTokens) {
            this.rank         = rank;
            this.userId       = userId;
            this.username     = username;
            this.level        = level;
            this.totalXp      = totalXp;
            this.totalTokens  = totalTokens;
            this.initials     = username.length() >= 2
                                ? username.substring(0, 2).toUpperCase()
                                : username.toUpperCase();
            this.levelName    = levelName(level);
            this.progressPct  = progressToNext(totalXp);
        }

        private static String levelName(int level) {
            if (level >= 20) return "Legend";
            if (level >= 15) return "Master";
            if (level >= 10) return "Expert";
            if (level >= 5)  return "Adept";
            if (level >= 3)  return "Apprentice";
            return "Novice";
        }

        private static int progressToNext(int xp) {
            // Level thresholds: Lv1=0, Lv2=50, Lv3=200, Lv4=450, Lv5=800...
            // Formula: xp_for_level_n = (n-1)^2 * 50
            int currentLevel = (int)(1 + Math.sqrt(xp / 50.0));
            int xpForCurrent = (int)(Math.pow(currentLevel - 1, 2) * 50);
            int xpForNext    = (int)(Math.pow(currentLevel, 2) * 50);
            int range = xpForNext - xpForCurrent;
            if (range <= 0) return 99;
            int pct = (int)(((xp - xpForCurrent) * 100.0) / range);
            return Math.max(0, Math.min(99, pct));
        }
    }

    /**
     * Get top players sorted by XP descending.
     * @param search  filter by username (empty = all)
     * @param sortBy  "xp" | "tokens" | "level"
     * @param limit   max rows (0 = all)
     */
    public List<PlayerEntry> getLeaderboard(String search, String sortBy, int limit)
            throws SQLException {

        String orderCol = switch (sortBy) {
            case "tokens" -> "sp.total_tokens";
            case "level"  -> "sp.level";
            default       -> "sp.total_xp";
        };

        String sql = "SELECT u.id, u.username, sp.level, sp.total_xp, sp.total_tokens " +
                     "FROM user u " +
                     "JOIN student_profile sp ON u.student_profile_id = sp.id " +
                     "WHERE sp.total_xp >= 0 ";

        if (search != null && !search.isBlank()) {
            sql += "AND u.username LIKE ? ";
        }
        sql += "ORDER BY " + orderCol + " DESC";
        if (limit > 0) sql += " LIMIT " + limit;

        List<PlayerEntry> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (search != null && !search.isBlank()) {
                ps.setString(1, "%" + search + "%");
            }
            ResultSet rs = ps.executeQuery();
            int rank = 1;
            while (rs.next()) {
                list.add(new PlayerEntry(
                    rank++,
                    rs.getInt("id"),
                    rs.getString("username"),
                    rs.getInt("level"),
                    rs.getInt("total_xp"),
                    rs.getInt("total_tokens")
                ));
            }
        }
        return list;
    }

    /** Get the rank of a specific user. Returns -1 if not found. */
    public int getUserRank(int userId) throws SQLException {
        String sql = "SELECT COUNT(*) + 1 AS rank FROM student_profile sp " +
                     "JOIN user u ON u.student_profile_id = sp.id " +
                     "WHERE sp.total_xp > (" +
                     "  SELECT sp2.total_xp FROM student_profile sp2 " +
                     "  JOIN user u2 ON u2.student_profile_id = sp2.id " +
                     "  WHERE u2.id = ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("rank") : -1;
        }
    }

    /** Get a single player's stats. Falls back to user.xp if student_profile has 0 XP. */
    public PlayerEntry getPlayerStats(int userId) throws SQLException {
        // Primary: join user → student_profile
        String sql = "SELECT u.id, u.username, u.xp AS user_xp, " +
                     "sp.level, sp.total_xp, sp.total_tokens " +
                     "FROM user u " +
                     "LEFT JOIN student_profile sp ON u.student_profile_id = sp.id " +
                     "WHERE u.id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int userXp      = rs.getInt("user_xp");
                int profileXp   = rs.getObject("total_xp") != null ? rs.getInt("total_xp") : 0;
                int tokens      = rs.getObject("total_tokens") != null ? rs.getInt("total_tokens") : 0;
                // Use the higher of the two XP values — student_profile may lag behind user.xp
                int effectiveXp = Math.max(userXp, profileXp);
                // Recalculate level from XP dynamically (don't trust stored level column)
                int level = Math.max(1, (int)(1 + Math.sqrt(effectiveXp / 50.0)));
                int rank  = getUserRank(userId);
                return new PlayerEntry(rank,
                    rs.getInt("id"), rs.getString("username"),
                    level, effectiveXp, tokens);
            }
        }
        return null;
    }
}
