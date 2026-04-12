package services.studysession;

import models.studysession.StudySession;
import utils.MyConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StudySessionService {

    private final Connection cnx = MyConnection.getInstance().getCnx();

    // ─────────────────────────────────────────────
    //  VALIDATION
    // ─────────────────────────────────────────────

    public String validate(StudySession s, boolean forUpdate) {
        if (s.getPlanningId() <= 0)
            return "A planning session must be selected.";

        if (s.getUserId() <= 0)
            return "User is required.";

        if (s.getDuration() <= 0)
            return "Duration must be a positive number (in minutes).";

        if (s.getActualDuration() != null && s.getActualDuration() < 0)
            return "Actual duration cannot be negative.";

        if (s.getXpEarned() != null && s.getXpEarned() < 0)
            return "XP earned cannot be negative.";

        if (s.getBreakDuration() != null && s.getBreakDuration() < 0)
            return "Break duration cannot be negative.";

        if (s.getBreakCount() != null && s.getBreakCount() < 0)
            return "Break count cannot be negative.";

        if (s.getPomodoroCount() != null && s.getPomodoroCount() < 0)
            return "Pomodoro count cannot be negative.";

        if (s.getMood() != null && !s.getMood().isEmpty() &&
                !List.of("positive","neutral","negative").contains(s.getMood()))
            return "Mood must be positive, neutral, or negative.";

        if (s.getEnergyLevel() != null && !s.getEnergyLevel().isEmpty() &&
                !List.of("low","medium","high").contains(s.getEnergyLevel()))
            return "Energy level must be low, medium, or high.";

        if (s.getBurnoutRisk() != null && !s.getBurnoutRisk().isEmpty() &&
                !List.of("LOW","MODERATE","HIGH").contains(s.getBurnoutRisk()))
            return "Burnout risk must be LOW, MODERATE, or HIGH.";

        // Uniqueness: same user + same planning (can't complete same planning twice)
        if (!forUpdate) {
            String uniqueErr = checkUniqueness(s.getUserId(), s.getPlanningId());
            if (uniqueErr != null) return uniqueErr;
        }

        return null;
    }

    private String checkUniqueness(int userId, int planningId) {
        try {
            PreparedStatement ps = cnx.prepareStatement(
                    "SELECT COUNT(*) FROM study_session WHERE user_id=? AND planning_id=?");
            ps.setInt(1, userId);
            ps.setInt(2, planningId);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt(1) > 0)
                return "A study session for this planning already exists for this user.";
        } catch (SQLException e) {
            System.err.println("Uniqueness check failed: " + e.getMessage());
        }
        return null;
    }

    // ─────────────────────────────────────────────
    //  AUTO-CALCULATE helpers
    // ─────────────────────────────────────────────

    /** Computes XP and burnout risk from duration, sets them on the session */
    public void autoCalculate(StudySession s) {
        int dur = s.getActualDuration() != null ? s.getActualDuration() : s.getDuration();
        int energyUsed = dur / 10;
        int xp = dur * 2;
        String burnout = energyUsed > 80 ? "HIGH" : energyUsed > 40 ? "MODERATE" : "LOW";
        s.setEnergyUsed(energyUsed);
        s.setXpEarned(xp);
        s.setBurnoutRisk(burnout);
    }

    // ─────────────────────────────────────────────
    //  CRUD
    // ─────────────────────────────────────────────

    public void create(StudySession s) throws SQLException {
        String sql = "INSERT INTO study_session (user_id, planning_id, started_at, ended_at, " +
                "duration, actual_duration, energy_used, xp_earned, burnout_risk, " +
                "completed_at, mood, energy_level, break_duration, break_count, pomodoro_count) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setInt(1, s.getUserId());
        ps.setInt(2, s.getPlanningId());
        ps.setTimestamp(3, s.getStartedAt() != null ? Timestamp.valueOf(s.getStartedAt()) : Timestamp.valueOf(LocalDateTime.now()));
        ps.setTimestamp(4, s.getEndedAt() != null ? Timestamp.valueOf(s.getEndedAt()) : null);
        ps.setInt(5, s.getDuration());
        setNullableInt(ps, 6, s.getActualDuration());
        setNullableInt(ps, 7, s.getEnergyUsed());
        setNullableInt(ps, 8, s.getXpEarned());
        ps.setString(9, s.getBurnoutRisk());
        ps.setTimestamp(10, s.getCompletedAt() != null ? Timestamp.valueOf(s.getCompletedAt()) : null);
        ps.setString(11, s.getMood());
        ps.setString(12, s.getEnergyLevel());
        setNullableInt(ps, 13, s.getBreakDuration());
        setNullableInt(ps, 14, s.getBreakCount());
        setNullableInt(ps, 15, s.getPomodoroCount());
        ps.executeUpdate();

        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) s.setId(keys.getInt(1));
    }

    public void update(StudySession s) throws SQLException {
        String sql = "UPDATE study_session SET user_id=?, planning_id=?, started_at=?, ended_at=?, " +
                "duration=?, actual_duration=?, energy_used=?, xp_earned=?, burnout_risk=?, " +
                "completed_at=?, mood=?, energy_level=?, break_duration=?, break_count=?, " +
                "pomodoro_count=? WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, s.getUserId());
        ps.setInt(2, s.getPlanningId());
        ps.setTimestamp(3, s.getStartedAt() != null ? Timestamp.valueOf(s.getStartedAt()) : null);
        ps.setTimestamp(4, s.getEndedAt() != null ? Timestamp.valueOf(s.getEndedAt()) : null);
        ps.setInt(5, s.getDuration());
        setNullableInt(ps, 6, s.getActualDuration());
        setNullableInt(ps, 7, s.getEnergyUsed());
        setNullableInt(ps, 8, s.getXpEarned());
        ps.setString(9, s.getBurnoutRisk());
        ps.setTimestamp(10, s.getCompletedAt() != null ? Timestamp.valueOf(s.getCompletedAt()) : null);
        ps.setString(11, s.getMood());
        ps.setString(12, s.getEnergyLevel());
        setNullableInt(ps, 13, s.getBreakDuration());
        setNullableInt(ps, 14, s.getBreakCount());
        setNullableInt(ps, 15, s.getPomodoroCount());
        ps.setInt(16, s.getId());
        ps.executeUpdate();
    }

    public void delete(int id) throws SQLException {
        PreparedStatement ps = cnx.prepareStatement("DELETE FROM study_session WHERE id=?");
        ps.setInt(1, id);
        ps.executeUpdate();
    }

    // ─────────────────────────────────────────────
    //  QUERIES
    // ─────────────────────────────────────────────

    public List<StudySession> findAll() throws SQLException {
        return mapResultSet(cnx.createStatement().executeQuery(
                "SELECT ss.*, p.title as planning_title, c.course_name " +
                        "FROM study_session ss " +
                        "LEFT JOIN planning p ON ss.planning_id=p.id " +
                        "LEFT JOIN course c ON p.course_id=c.id " +
                        "ORDER BY ss.started_at DESC"));
    }

    public List<StudySession> findByFilters(Integer userId, String burnoutRisk,
                                            String mood, String energyLevel,
                                            LocalDateTime dateFrom, LocalDateTime dateTo) throws SQLException {
        StringBuilder sb = new StringBuilder(
                "SELECT ss.*, p.title as planning_title, c.course_name " +
                        "FROM study_session ss " +
                        "LEFT JOIN planning p ON ss.planning_id=p.id " +
                        "LEFT JOIN course c ON p.course_id=c.id WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (userId != null) { sb.append(" AND ss.user_id=?"); params.add(userId); }
        if (burnoutRisk != null && !burnoutRisk.isEmpty()) { sb.append(" AND ss.burnout_risk=?"); params.add(burnoutRisk); }
        if (mood != null && !mood.isEmpty()) { sb.append(" AND ss.mood=?"); params.add(mood); }
        if (energyLevel != null && !energyLevel.isEmpty()) { sb.append(" AND ss.energy_level=?"); params.add(energyLevel); }
        if (dateFrom != null) { sb.append(" AND ss.started_at>=?"); params.add(Timestamp.valueOf(dateFrom)); }
        if (dateTo != null) { sb.append(" AND ss.started_at<=?"); params.add(Timestamp.valueOf(dateTo)); }
        sb.append(" ORDER BY ss.started_at DESC");

        PreparedStatement ps = cnx.prepareStatement(sb.toString());
        for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
        return mapResultSet(ps.executeQuery());
    }

    public StudySession findById(int id) throws SQLException {
        PreparedStatement ps = cnx.prepareStatement(
                "SELECT ss.*, p.title as planning_title, c.course_name " +
                        "FROM study_session ss " +
                        "LEFT JOIN planning p ON ss.planning_id=p.id " +
                        "LEFT JOIN course c ON p.course_id=c.id WHERE ss.id=?");
        ps.setInt(1, id);
        List<StudySession> list = mapResultSet(ps.executeQuery());
        return list.isEmpty() ? null : list.get(0);
    }

    // ─────────────────────────────────────────────
    //  ANALYTICS
    // ─────────────────────────────────────────────

    /** Returns {totalSessions, totalXP, avgDuration, totalMinutes} */
    public Map<String, Object> getGlobalStats() throws SQLException {
        Map<String, Object> stats = new LinkedHashMap<>();
        ResultSet rs = cnx.createStatement().executeQuery(
                "SELECT COUNT(*) as total, SUM(xp_earned) as total_xp, " +
                        "AVG(actual_duration) as avg_dur, SUM(actual_duration) as total_min " +
                        "FROM study_session");
        if (rs.next()) {
            stats.put("totalSessions", rs.getInt("total"));
            stats.put("totalXP", rs.getInt("total_xp"));
            stats.put("avgDuration", Math.round(rs.getFloat("avg_dur")));
            stats.put("totalMinutes", rs.getInt("total_min"));
        }
        return stats;
    }

    /** Burnout risk distribution */
    public Map<String, Integer> getBurnoutDistribution() throws SQLException {
        Map<String, Integer> dist = new LinkedHashMap<>();
        dist.put("LOW", 0); dist.put("MODERATE", 0); dist.put("HIGH", 0);
        ResultSet rs = cnx.createStatement().executeQuery(
                "SELECT burnout_risk, COUNT(*) as cnt FROM study_session GROUP BY burnout_risk");
        while (rs.next()) dist.put(rs.getString("burnout_risk"), rs.getInt("cnt"));
        return dist;
    }

    /** XP per course */
    public List<Object[]> getXpPerCourse() throws SQLException {
        List<Object[]> list = new ArrayList<>();
        ResultSet rs = cnx.createStatement().executeQuery(
                "SELECT c.course_name, SUM(ss.xp_earned) as total_xp " +
                        "FROM study_session ss " +
                        "LEFT JOIN planning p ON ss.planning_id=p.id " +
                        "LEFT JOIN course c ON p.course_id=c.id " +
                        "GROUP BY c.id ORDER BY total_xp DESC LIMIT 10");
        while (rs.next())
            list.add(new Object[]{rs.getString("course_name"), rs.getInt("total_xp")});
        return list;
    }

    // ─────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────

    private void setNullableInt(PreparedStatement ps, int idx, Integer val) throws SQLException {
        if (val != null) ps.setInt(idx, val);
        else ps.setNull(idx, Types.INTEGER);
    }

    private List<StudySession> mapResultSet(ResultSet rs) throws SQLException {
        List<StudySession> list = new ArrayList<>();
        while (rs.next()) {
            StudySession s = new StudySession();
            s.setId(rs.getInt("id"));
            s.setUserId(rs.getInt("user_id"));
            s.setPlanningId(rs.getInt("planning_id"));
            s.setPlanningTitleCache(rs.getString("planning_title"));
            s.setCourseNameCache(rs.getString("course_name"));
            Timestamp start = rs.getTimestamp("started_at");
            s.setStartedAt(start != null ? start.toLocalDateTime() : null);
            Timestamp end = rs.getTimestamp("ended_at");
            s.setEndedAt(end != null ? end.toLocalDateTime() : null);
            s.setDuration(rs.getInt("duration"));
            int ad = rs.getInt("actual_duration");
            s.setActualDuration(rs.wasNull() ? null : ad);
            int eu = rs.getInt("energy_used");
            s.setEnergyUsed(rs.wasNull() ? null : eu);
            int xp = rs.getInt("xp_earned");
            s.setXpEarned(rs.wasNull() ? null : xp);
            s.setBurnoutRisk(rs.getString("burnout_risk"));
            Timestamp comp = rs.getTimestamp("completed_at");
            s.setCompletedAt(comp != null ? comp.toLocalDateTime() : null);
            s.setMood(rs.getString("mood"));
            s.setEnergyLevel(rs.getString("energy_level"));
            int bd = rs.getInt("break_duration");
            s.setBreakDuration(rs.wasNull() ? null : bd);
            int bc = rs.getInt("break_count");
            s.setBreakCount(rs.wasNull() ? null : bc);
            int pc = rs.getInt("pomodoro_count");
            s.setPomodoroCount(rs.wasNull() ? null : pc);
            list.add(s);
        }
        return list;
    }
}
