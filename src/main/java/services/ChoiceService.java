package services;

import models.quiz.Choice;
import utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ChoiceService {

    private Connection cnx;

    public ChoiceService() {
        cnx = MyConnection.getInstance().getCnx();
    }

    // --- CREATE ---
    public void createChoice(Choice choice) {
        String req = "INSERT INTO choice (content, is_correct, question_id) VALUES (?, ?, ?)";

        try {
            PreparedStatement ps = cnx.prepareStatement(req, Statement.RETURN_GENERATED_KEYS);

            ps.setString(1, choice.getContent());
            ps.setBoolean(2, choice.isCorrect());
            ps.setInt(3, choice.getQuestionId());

            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                choice.setId(rs.getInt(1));
            }

            System.out.println("Choice created successfully! ✅");

        } catch (SQLException e) {
            System.err.println("Error creating choice: " + e.getMessage());
            throw new RuntimeException("Failed to create choice", e);
        }
    }

    // --- READ (Get by ID) ---
    public Choice getChoiceById(int id) {
        String req = "SELECT * FROM choice WHERE id = ?";
        Choice choice = null;

        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setInt(1, id);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                choice = new Choice(
                        rs.getInt("id"),
                        rs.getString("content"),
                        rs.getBoolean("is_correct"),
                        rs.getInt("question_id")
                );
            }

        } catch (SQLException e) {
            System.err.println("Error retrieving choice: " + e.getMessage());
        }

        return choice;
    }

    // --- READ (Get all by Question ID) ---
    public List<Choice> getChoicesByQuestionId(int questionId) {
        String req = "SELECT * FROM choice WHERE question_id = ?";
        List<Choice> choices = new ArrayList<>();

        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setInt(1, questionId);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Choice choice = new Choice(
                        rs.getInt("id"),
                        rs.getString("content"),
                        rs.getBoolean("is_correct"),
                        rs.getInt("question_id")
                );
                choices.add(choice);
            }

        } catch (SQLException e) {
            System.err.println("Error retrieving choices: " + e.getMessage());
        }

        return choices;
    }

    // --- UPDATE ---
    public void updateChoice(Choice choice) {
        String req = "UPDATE choice SET content = ?, is_correct = ?, question_id = ? WHERE id = ?";

        try {
            PreparedStatement ps = cnx.prepareStatement(req);

            ps.setString(1, choice.getContent());
            ps.setBoolean(2, choice.isCorrect());
            ps.setInt(3, choice.getQuestionId());
            ps.setInt(4, choice.getId());

            ps.executeUpdate();
            System.out.println("Choice updated successfully! ✅");

        } catch (SQLException e) {
            System.err.println("Error updating choice: " + e.getMessage());
            throw new RuntimeException("Failed to update choice", e);
        }
    }

    // --- DELETE ---
    public void deleteChoice(int id) {
        String req = "DELETE FROM choice WHERE id = ?";

        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setInt(1, id);

            ps.executeUpdate();
            System.out.println("Choice deleted successfully! ✅");

        } catch (SQLException e) {
            System.err.println("Error deleting choice: " + e.getMessage());
            throw new RuntimeException("Failed to delete choice", e);
        }
    }

    // --- DELETE all choices for a question ---
    public void deleteChoicesByQuestionId(int questionId) {
        String req = "DELETE FROM choice WHERE question_id = ?";

        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setInt(1, questionId);

            ps.executeUpdate();
            System.out.println("All choices for question deleted successfully! ✅");

        } catch (SQLException e) {
            System.err.println("Error deleting choices: " + e.getMessage());
            throw new RuntimeException("Failed to delete choices", e);
        }
    }
}
