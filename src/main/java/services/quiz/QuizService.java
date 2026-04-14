package services.quiz;

import models.quiz.Quiz;
import utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class QuizService {

    // We declare the connection object
    private Connection cnx;

    public QuizService() {
        // We get the single connection from our Singleton class
        cnx = MyConnection.getInstance().getCnx();
    }

    // --- CREATE (Add) ---
    public void createQuiz(Quiz quiz) {
        String req = "INSERT INTO quiz (title, description) VALUES (?, ?)";

        try {
            PreparedStatement ps = cnx.prepareStatement(req);

            ps.setString(1, quiz.getTitle());
            ps.setString(2, quiz.getDescription());

            ps.executeUpdate();
            System.out.println("Quiz created successfully! ✅");

        } catch (SQLException e) {
            System.err.println("Error creating quiz: " + e.getMessage());
            throw new RuntimeException("Failed to create quiz", e);
        }
    }

    // --- READ (Get by ID) ---
    public Quiz getQuizById(int id) {
        String req = "SELECT * FROM quiz WHERE id = ?";
        Quiz quiz = null;

        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setInt(1, id);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                quiz = new Quiz(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("description")
                );
            }

        } catch (SQLException e) {
            System.err.println("Error retrieving quiz: " + e.getMessage());
        }

        return quiz;
    }

    // --- READ (Get All) ---
    public List<Quiz> getAllQuizzes() {
        String req = "SELECT * FROM quiz";
        List<Quiz> quizzes = new ArrayList<>();

        try {
            Statement st = cnx.createStatement();
            ResultSet rs = st.executeQuery(req);

            while (rs.next()) {
                Quiz quiz = new Quiz(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("description")
                );
                quizzes.add(quiz);
            }

        } catch (SQLException e) {
            System.err.println("Error retrieving quizzes: " + e.getMessage());
        }

        return quizzes;
    }

    // --- UPDATE ---
    public void updateQuiz(Quiz quiz) {
        String req = "UPDATE quiz SET title = ?, description = ? WHERE id = ?";

        try {
            PreparedStatement ps = cnx.prepareStatement(req);

            ps.setString(1, quiz.getTitle());
            ps.setString(2, quiz.getDescription());
            ps.setInt(3, quiz.getId());

            ps.executeUpdate();
            System.out.println("Quiz updated successfully! ✅");

        } catch (SQLException e) {
            System.err.println("Error updating quiz: " + e.getMessage());
            throw new RuntimeException("Failed to update quiz", e);
        }
    }

    // --- DELETE ---
    public void deleteQuiz(int id) {
        String req = "DELETE FROM quiz WHERE id = ?";

        try {
            PreparedStatement ps = cnx.prepareStatement(req);
            ps.setInt(1, id);

            ps.executeUpdate();
            System.out.println("Quiz deleted successfully! ✅");

        } catch (SQLException e) {
            System.err.println("Error deleting quiz: " + e.getMessage());
            throw new RuntimeException("Failed to delete quiz", e);
        }
    }
}
