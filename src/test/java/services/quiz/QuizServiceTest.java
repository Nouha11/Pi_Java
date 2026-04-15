package services.quiz;

import models.quiz.Choice;
import models.quiz.Question;
import models.quiz.Quiz;
import services.quiz.ChoiceService;
import services.quiz.QuestionService;
import services.quiz.QuizService;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QuizServiceTest {

    static QuizService quizService;
    static QuestionService questionService;
    static ChoiceService choiceService;

    // shared IDs across tests
    static int quizId;
    static int questionId;
    static int choiceId;

    @BeforeAll
    static void setup() {
        quizService =   new QuizService();
        questionService = new QuestionService();
        choiceService = new ChoiceService();
    }

    // ───────── HELPERS ─────────

    private Quiz buildQuiz(String title) {
        return new Quiz(title, "A test quiz description");
    }

    private Question buildQuestion(int qzId, String text) {
        Question q = new Question(text, 10, "EASY", qzId);
        q.setUpdatedAt(LocalDateTime.now());
        return q;
    }

    private Choice buildChoice(int qnId, String content, boolean correct) {
        return new Choice(content, correct, qnId);
    }

    // ───────── QUIZ TESTS ─────────

    @Test
    @Order(1)
    void testCreateQuiz() {
        Quiz quiz = buildQuiz("Unit Test Quiz");
        assertDoesNotThrow(() -> quizService.createQuiz(quiz));

        List<Quiz> all = quizService.getAllQuizzes();
        Quiz created = all.stream()
                .filter(q -> "Unit Test Quiz".equals(q.getTitle()))
                .findFirst()
                .orElse(null);

        assertNotNull(created, "Quiz should exist in DB after creation");
        assertEquals("Unit Test Quiz", created.getTitle());
        assertEquals("A test quiz description", created.getDescription());

        quizId = created.getId(); // store for subsequent tests
    }

    @Test
    @Order(2)
    void testGetQuizById() {
        Quiz quiz = quizService.getQuizById(quizId);
        assertNotNull(quiz, "Should retrieve quiz by ID");
        assertEquals(quizId, quiz.getId());
        assertEquals("Unit Test Quiz", quiz.getTitle());
    }

    @Test
    @Order(3)
    void testGetAllQuizzes() {
        List<Quiz> quizzes = quizService.getAllQuizzes();
        assertNotNull(quizzes);
        assertFalse(quizzes.isEmpty(), "Quiz list should not be empty");
        assertTrue(quizzes.stream().anyMatch(q -> q.getId() == quizId));
    }

    @Test
    @Order(4)
    void testUpdateQuiz() {
        Quiz quiz = quizService.getQuizById(quizId);
        assertNotNull(quiz);

        quiz.setTitle("Updated Quiz Title");
        quiz.setDescription("Updated description");
        assertDoesNotThrow(() -> quizService.updateQuiz(quiz));

        Quiz updated = quizService.getQuizById(quizId);
        assertEquals("Updated Quiz Title", updated.getTitle());
        assertEquals("Updated description", updated.getDescription());
    }

    // ───────── QUESTION TESTS ─────────

    @Test
    @Order(5)
    void testCreateQuestion() {
        Question q = buildQuestion(quizId, "What is 2 + 2?");
        assertDoesNotThrow(() -> questionService.createQuestion(q));

        assertTrue(q.getId() > 0, "Generated ID should be set after creation");
        questionId = q.getId();
    }

    @Test
    @Order(6)
    void testGetQuestionById() {
        Question q = questionService.getQuestionById(questionId);
        assertNotNull(q, "Should retrieve question by ID");
        assertEquals(questionId, q.getId());
        assertEquals("What is 2 + 2?", q.getText());
        assertEquals(10, q.getXpValue());
        assertEquals("EASY", q.getDifficulty());
        assertEquals(quizId, q.getQuizId());
    }

    @Test
    @Order(7)
    void testGetQuestionsByQuizId() {
        List<Question> questions = questionService.getQuestionsByQuizId(quizId);
        assertNotNull(questions);
        assertFalse(questions.isEmpty(), "Quiz should have at least one question");
        assertTrue(questions.stream().anyMatch(q -> q.getId() == questionId));
    }

    @Test
    @Order(8)
    void testUpdateQuestion() {
        Question q = questionService.getQuestionById(questionId);
        assertNotNull(q);

        q.setText("What is 3 + 3?");
        q.setXpValue(20);
        q.setDifficulty("MEDIUM");
        q.setUpdatedAt(LocalDateTime.now());
        assertDoesNotThrow(() -> questionService.updateQuestion(q));

        Question updated = questionService.getQuestionById(questionId);
        assertEquals("What is 3 + 3?", updated.getText());
        assertEquals(20, updated.getXpValue());
        assertEquals("MEDIUM", updated.getDifficulty());
    }

    // ───────── CHOICE TESTS ─────────

    @Test
    @Order(9)
    void testCreateChoice() {
        Choice c = buildChoice(questionId, "Option A", true);
        assertDoesNotThrow(() -> choiceService.createChoice(c));

        assertTrue(c.getId() > 0, "Generated ID should be set after creation");
        choiceId = c.getId();

        // add a wrong choice too
        Choice wrong = buildChoice(questionId, "Option B", false);
        assertDoesNotThrow(() -> choiceService.createChoice(wrong));
    }

    @Test
    @Order(10)
    void testGetChoiceById() {
        Choice c = choiceService.getChoiceById(choiceId);
        assertNotNull(c, "Should retrieve choice by ID");
        assertEquals(choiceId, c.getId());
        assertEquals("Option A", c.getContent());
        assertTrue(c.isCorrect());
        assertEquals(questionId, c.getQuestionId());
    }

    @Test
    @Order(11)
    void testGetChoicesByQuestionId() {
        List<Choice> choices = choiceService.getChoicesByQuestionId(questionId);
        assertNotNull(choices);
        assertEquals(2, choices.size(), "Question should have exactly 2 choices");

        long correctCount = choices.stream().filter(Choice::isCorrect).count();
        assertEquals(1, correctCount, "Exactly one choice should be correct");
    }

    @Test
    @Order(12)
    void testUpdateChoice() {
        Choice c = choiceService.getChoiceById(choiceId);
        assertNotNull(c);

        c.setContent("Option A - Updated");
        c.setIsCorrect(false);
        assertDoesNotThrow(() -> choiceService.updateChoice(c));

        Choice updated = choiceService.getChoiceById(choiceId);
        assertEquals("Option A - Updated", updated.getContent());
        assertFalse(updated.isCorrect());
    }

    // ───────── INTEGRATION TEST ─────────

    @Test
    @Order(13)
    void testFullQuizWithQuestionsAndChoices() {
        // Create a quiz
        Quiz quiz = buildQuiz("Integration Quiz");
        quizService.createQuiz(quiz);

        List<Quiz> all = quizService.getAllQuizzes();
        Quiz saved = all.stream()
                .filter(q -> "Integration Quiz".equals(q.getTitle()))
                .findFirst()
                .orElse(null);
        assertNotNull(saved);
        int iQuizId = saved.getId();

        // Add 2 questions
        for (int i = 1; i <= 2; i++) {
            Question q = buildQuestion(iQuizId, "Integration Question " + i);
            questionService.createQuestion(q);
            assertTrue(q.getId() > 0);

            // Add 3 choices per question (first one correct)
            for (int j = 1; j <= 3; j++) {
                Choice c = buildChoice(q.getId(), "Choice " + j, j == 1);
                choiceService.createChoice(c);
                assertTrue(c.getId() > 0);
            }

            // Verify choices were saved
            List<Choice> choices = choiceService.getChoicesByQuestionId(q.getId());
            assertEquals(3, choices.size());
            assertEquals(1, choices.stream().filter(Choice::isCorrect).count());
        }

        // Verify questions were saved
        List<Question> questions = questionService.getQuestionsByQuizId(iQuizId);
        assertEquals(2, questions.size());

        // Cleanup integration quiz
        for (Question q : questions) {
            choiceService.deleteChoicesByQuestionId(q.getId());
            questionService.deleteQuestion(q.getId());
        }
        quizService.deleteQuiz(iQuizId);

        assertNull(quizService.getQuizById(iQuizId), "Quiz should be deleted");
    }

    // ───────── CLEANUP ─────────

    @Test
    @Order(14)
    void testDeleteChoicesByQuestionId() {
        assertDoesNotThrow(() -> choiceService.deleteChoicesByQuestionId(questionId));
        List<Choice> choices = choiceService.getChoicesByQuestionId(questionId);
        assertTrue(choices.isEmpty(), "All choices for question should be deleted");
    }

    @Test
    @Order(15)
    void testDeleteQuestion() {
        assertDoesNotThrow(() -> questionService.deleteQuestion(questionId));
        assertNull(questionService.getQuestionById(questionId), "Question should be deleted");
    }

    @Test
    @Order(16)
    void testDeleteQuiz() {
        assertDoesNotThrow(() -> quizService.deleteQuiz(quizId));
        assertNull(quizService.getQuizById(quizId), "Quiz should be deleted");
    }
}
