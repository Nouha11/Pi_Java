package services;

import models.quiz.Question;
import utils.MyConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class QuestionService {

    private Connection cnx;

    public QuestionService() {
        cnx = MyConnection.getInstance().getCnx();
    }

    // --- CREATE ---
    public void createQuestion(Question question) {
        String req = "INSERT INTO question (text, xpValue, difficulty, imageName, updatedAt, quizId, userId) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try {
            PreparedStatement ps = cnx.prepareStatement(req, Statement.RETURN_GENERATED_KEYS);

            ps.setString(1, question.getText());
            ps.setInt(2, question.getXpValue());
            ps.setString(3, question.getDifficulty());
            ps.setString(4, question.getImageName());
            ps.setTimestamp(5, Timestamp.valueOf(question.getUpdatedAt()));
            ps.setInt(6, question.getQuizId());
            ps.setObject(7, question.getUserId());

            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                question.setId(rs.getInt(1));
            }

            System.out.println("Question created successfully! ✅");

        } catch (SQLException e) {
            System.err.println("Error creating question: " + e.getMessage());
            throw new RuntimeException("Failed to create question", e);
        }
    }

    // --- READ (Get by ID) ---
    public Question getQuestionById(int id) {
        String req = "SELECT * FROM question WHERE id = ?";
        Question question = null;

        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setInt(1, id);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                question = new Question(
                        rs.getInt("id"),
                        rs.getString("text"),
                        rs.getInt("xpValue"),
                        rs.getString("difficulty"),
                        rs.getString("imageName"),
                        rs.getTimestamp("updatedAt").toLocalDateTime(),
                        rs.getInt("quizId"),
                        (Integer) rs.getObject("userId")
                );
            }

        } catch (SQLException e) {
            System.err.println("Error retrieving question: " + e.getMessage());
        }

        return question;
    }

    // --- READ (Get all by Quiz ID) ---
    public List<Question> getQuestionsByQuizId(int quizId) {
        String req = "SELECT * FROM question WHERE quizId = ?";
        List<Question> questions = new ArrayList<>();

        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setInt(1, quizId);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Question question = new Question(
                        rs.getInt("id"),
                        rs.getString("text"),
                        rs.getInt("xpValue"),
                        rs.getString("difficulty"),
                        rs.getString("imageName"),
                        rs.getTimestamp("updatedAt").toLocalDateTime(),
                        rs.getInt("quizId"),
                        (Integer) rs.getObject("userId")
                );
                questions.add(question);
            }

        } catch (SQLException e) {
            System.err.println("Error retrieving questions: " + e.getMessage());
        }

        return questions;
    }

    // --- UPDATE ---
    public void updateQuestion(Question question) {
        String req = "UPDATE question SET text = ?, xpValue = ?, difficulty = ?, imageName = ?, updatedAt = ?, quizId = ?, userId = ? WHERE id = ?";

        try {
            PreparedStatement ps = cnx.prepareStatement(req);

            ps.setString(1, question.getText());
            ps.setInt(2, question.getXpValue());
            ps.setString(3, question.getDifficulty());
            ps.setString(4, question.getImageName());
            ps.setTimestamp(5, Timestamp.valueOf(question.getUpdatedAt()));
            ps.setInt(6, question.getQuizId());
            ps.setObject(7, question.getUserId());
            ps.setInt(8, question.getId());

            ps.executeUpdate();
            System.out.println("Question updated successfully! ✅");

        } catch (SQLException e) {
            System.err.println("Error updating question: " + e.getMessage());
            throw new RuntimeException("Failed to update question", e);
        }
    }

    // --- DELETE ---
    public void deleteQuestion(int id) {
        String req = "DELETE FROM question WHERE id = ?";

        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setInt(1, id);

            ps.executeUpdate();
            System.out.println("Question deleted successfully! ✅");

        } catch (SQLException e) {
            System.err.println("Error deleting question: " + e.getMessage());
            throw new RuntimeException("Failed to delete question", e);
        }
    }
}
