package services.gamification;

import utils.MyConnection;
import java.sql.*;

/**
 * Handles awarding XP, tokens and energy to a student after completing a game.
 *
 * DB layout:
 *   user(id, xp, student_profile_id)
 *   student_profile(id, total_xp, total_tokens, level, energy)
 */
public class GameRewardService {

    private final Connection conn = MyConnection.getInstance().getCnx();

    /**
     * Award XP + tokens to the student linked to the given userId.
     * Updates both user.xp and student_profile.total_xp / total_tokens / level.
     *
     * @return a short result message, e.g. "+40 XP  +20 Tokens"
     */
    public String awardGameRewards(int userId, int xpGain, int tokenGain) throws SQLException {
        // 1. Get student_profile_id for this user
        int profileId = getStudentProfileId(userId);
        if (profileId == -1) {
            // No student profile — just update user.xp
            updateUserXp(userId, xpGain);
            return "+" + xpGain + " XP awarded";
        }

        // 2. Update user.xp
        updateUserXp(userId, xpGain);

        // 3. Update student_profile totals and recalculate level
        String sql = "UPDATE student_profile SET " +
                     "total_xp = total_xp + ?, " +
                     "total_tokens = total_tokens + ?, " +
                     "level = GREATEST(1, FLOOR(1 + SQRT((total_xp + ?) / 50.0))) " +
                     "WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, xpGain);
            ps.setInt(2, tokenGain);
            ps.setInt(3, xpGain);   // used in level formula
            ps.setInt(4, profileId);
            ps.executeUpdate();
        }

        return "+" + xpGain + " XP  +" + tokenGain + " Tokens";
    }

    /**
     * Restore energy for a mini-game completion.
     */
    public String awardMiniGameEnergy(int userId, int energyGain) throws SQLException {
        int profileId = getStudentProfileId(userId);
        if (profileId == -1) return "+" + energyGain + " Energy";

        String sql = "UPDATE student_profile SET " +
                     "energy = LEAST(100, energy + ?), " +
                     "last_energy_update = NOW() " +
                     "WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, energyGain);
            ps.setInt(2, profileId);
            ps.executeUpdate();
        }
        return "+" + energyGain + " Energy restored";
    }

    /** Returns current XP + tokens for display, or null if no profile. */
    public int[] getCurrentStats(int userId) throws SQLException {
        int profileId = getStudentProfileId(userId);
        if (profileId == -1) return null;
        String sql = "SELECT total_xp, total_tokens, level FROM student_profile WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, profileId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return new int[]{rs.getInt("total_xp"), rs.getInt("total_tokens"), rs.getInt("level")};
        }
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int getStudentProfileId(int userId) throws SQLException {
        String sql = "SELECT student_profile_id FROM user WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int pid = rs.getInt("student_profile_id");
                return rs.wasNull() ? -1 : pid;
            }
        }
        return -1;
    }

    private void updateUserXp(int userId, int xpGain) throws SQLException {
        String sql = "UPDATE user SET xp = xp + ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, xpGain);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }
}
