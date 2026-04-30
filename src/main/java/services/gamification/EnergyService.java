package services.gamification;

import utils.MyConnection;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Manages student energy — mirrors Pi_web EnergyMonitorService.php exactly.
 *
 * Rules (same as Pi_web):
 *   - Max energy: 100
 *   - Auto-regen: +1 energy every 5 minutes while below 100
 *   - Mini games restore energy instantly on completion
 *   - Energy is stored in student_profile.energy + last_energy_update
 */
public class EnergyService {

    private static final int MAX_ENERGY          = 100;
    private static final int REGEN_MINUTES       = 5;   // 1 point per 5 minutes
    private static final int REGEN_POINTS        = 1;

    private final Connection conn = MyConnection.getInstance().getCnx();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Get current energy for a user, applying auto-regen first.
     * Returns 100 if no student_profile exists.
     */
    public int getCurrentEnergy(int userId) throws SQLException {
        int profileId = getProfileId(userId);
        if (profileId <= 0) return MAX_ENERGY;
        applyAutoRegen(profileId);
        return readEnergy(profileId);
    }

    /**
     * Seconds until the next +1 energy point is added.
     * Returns 0 if already at max or no profile.
     */
    public int getSecondsUntilNextRegen(int userId) throws SQLException {
        int profileId = getProfileId(userId);
        if (profileId <= 0) return 0;
        int energy = readEnergy(profileId);
        if (energy >= MAX_ENERGY) return 0;

        LocalDateTime lastUpdate = readLastUpdate(profileId);
        if (lastUpdate == null) return 0;

        long secondsSince = ChronoUnit.SECONDS.between(lastUpdate, LocalDateTime.now());
        long cycleSeconds = REGEN_MINUTES * 60L;
        long secondsUntilNext = cycleSeconds - (secondsSince % cycleSeconds);
        return (int) Math.max(0, secondsUntilNext);
    }

    /**
     * Drain energy during study (called every minute of Pomodoro focus time).
     * Mirrors Pi_web depleteEnergy() — drains 1 point per minute, floors at 0.
     */
    public int drainEnergy(int userId, int amount) throws SQLException {
        int profileId = getProfileId(userId);
        if (profileId <= 0) return 0;

        // Drain and return the new value in one query
        String sql = "UPDATE student_profile SET " +
                     "energy = GREATEST(0, energy - ?), " +
                     "last_energy_update = NOW() " +
                     "WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, amount);
            ps.setInt(2, profileId);
            ps.executeUpdate();
        }
        return readEnergy(profileId);
    }
    public void restoreEnergy(int userId, int amount) throws SQLException {
        int profileId = getProfileId(userId);
        if (profileId <= 0) return;
        String sql = "UPDATE student_profile SET " +
                     "energy = LEAST(?, energy + ?), " +
                     "last_energy_update = NOW() " +
                     "WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, MAX_ENERGY);
            ps.setInt(2, amount);
            ps.setInt(3, profileId);
            ps.executeUpdate();
        }
    }

    /**
     * Returns a snapshot: [energy, secondsUntilNextRegen]
     */
    public int[] getEnergySnapshot(int userId) throws SQLException {
        return new int[]{getCurrentEnergy(userId), getSecondsUntilNextRegen(userId)};
    }

    // ── Auto-regen (mirrors applyAutoRefill in PHP) ───────────────────────────

    private void applyAutoRegen(int profileId) throws SQLException {
        int currentEnergy = readEnergy(profileId);
        if (currentEnergy >= MAX_ENERGY) return;

        LocalDateTime lastUpdate = readLastUpdate(profileId);
        if (lastUpdate == null) {
            // First time — just set the timestamp
            touchLastUpdate(profileId);
            return;
        }

        long minutesPassed = ChronoUnit.MINUTES.between(lastUpdate, LocalDateTime.now());
        int energyToAdd = (int)(minutesPassed / REGEN_MINUTES) * REGEN_POINTS;

        if (energyToAdd > 0) {
            int newEnergy = Math.min(MAX_ENERGY, currentEnergy + energyToAdd);
            String sql = "UPDATE student_profile SET energy = ?, last_energy_update = NOW() WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, newEnergy);
                ps.setInt(2, profileId);
                ps.executeUpdate();
            }
        }
    }

    // ── DB helpers ────────────────────────────────────────────────────────────

    private int getProfileId(int userId) throws SQLException {
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

    private int readEnergy(int profileId) throws SQLException {
        String sql = "SELECT energy FROM student_profile WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, profileId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int e = rs.getInt("energy");
                return rs.wasNull() ? MAX_ENERGY : e;
            }
        }
        return MAX_ENERGY;
    }

    private LocalDateTime readLastUpdate(int profileId) throws SQLException {
        String sql = "SELECT last_energy_update FROM student_profile WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, profileId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Timestamp ts = rs.getTimestamp("last_energy_update");
                return ts != null ? ts.toLocalDateTime() : null;
            }
        }
        return null;
    }

    private void touchLastUpdate(int profileId) throws SQLException {
        String sql = "UPDATE student_profile SET last_energy_update = NOW() WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, profileId);
            ps.executeUpdate();
        }
    }
}
