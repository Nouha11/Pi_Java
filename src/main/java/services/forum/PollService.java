package services.forum;

import utils.MyConnection;
import java.sql.*;
import java.util.*;

public class PollService {
    private final Connection cnx = MyConnection.getInstance().getCnx();

    // Saves the poll question and all the options
    public void createPoll(int postId, String question, List<String> options) {
        try {
            String pollReq = "INSERT INTO post_poll (post_id, question) VALUES (?, ?)";
            PreparedStatement ps = cnx.prepareStatement(pollReq, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, postId);
            ps.setString(2, question);
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                int pollId = rs.getInt(1);
                String optReq = "INSERT INTO poll_option (poll_id, option_text) VALUES (?, ?)";
                PreparedStatement psOpt = cnx.prepareStatement(optReq);
                for (String opt : options) {
                    psOpt.setInt(1, pollId);
                    psOpt.setString(2, opt);
                    psOpt.addBatch();
                }
                psOpt.executeBatch();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Fetches the poll and calculates current votes
    public Map<String, Object> getPollForPost(int postId) {
        Map<String, Object> pollData = new HashMap<>();
        try {
            String req = "SELECT * FROM post_poll WHERE post_id = ?";
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setInt(1, postId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int pollId = rs.getInt("id");
                pollData.put("id", pollId);
                pollData.put("question", rs.getString("question"));

                List<Map<String, Object>> options = new ArrayList<>();
                String optReq = "SELECT id, option_text, (SELECT COUNT(*) FROM poll_vote WHERE option_id = poll_option.id) as votes FROM poll_option WHERE poll_id = ?";
                PreparedStatement psOpt = cnx.prepareStatement(optReq);
                psOpt.setInt(1, pollId);
                ResultSet rsOpt = psOpt.executeQuery();

                while (rsOpt.next()) {
                    Map<String, Object> opt = new HashMap<>();
                    opt.put("id", rsOpt.getInt("id"));
                    opt.put("text", rsOpt.getString("option_text"));
                    opt.put("votes", rsOpt.getInt("votes"));
                    options.add(opt);
                }
                pollData.put("options", options);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return pollData.isEmpty() ? null : pollData;
    }

    // Records a user's vote
    public void castVote(int userId, int pollId, int optionId) {
        String req = "INSERT INTO poll_vote (user_id, poll_id, option_id) VALUES (?, ?, ?)";
        try (PreparedStatement ps = cnx.prepareStatement(req)) {
            ps.setInt(1, userId);
            ps.setInt(2, pollId);
            ps.setInt(3, optionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("User already voted or DB error");
        }
    }

    // Checks if the user has already voted on this specific poll
    public Integer getUserVote(int userId, int pollId) {
        try {
            String req = "SELECT option_id FROM poll_vote WHERE user_id = ? AND poll_id = ?";
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setInt(1, userId);
            ps.setInt(2, pollId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("option_id");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}