package services.quiz;

import models.quiz.Choice;
import models.quiz.Question;
import models.quiz.Quiz;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests: pulls real questions from the DB and generates hints
 * via OpenRouter (poolside/laguna-xs.2:free).
 * Requires a running DB and a valid openrouter key in api.properties.
 */
class GeminiQuizIntegrationTest {

    static HuggingFaceService service;
    static QuizService        quizService;
    static QuestionService    questionService;
    static ChoiceService      choiceService;

    @BeforeAll
    static void setup() {
        service         = new HuggingFaceService();
        quizService     = new QuizService();
        questionService = new QuestionService();
        choiceService   = new ChoiceService();
    }

    @Test
    void testDatabaseHasQuizzes() {
        List<Quiz> quizzes = quizService.getAllQuizzes();
        assertNotNull(quizzes);
        assertFalse(quizzes.isEmpty(), "No quizzes in DB — add at least one to run this test");
        System.out.println("Quizzes found: " + quizzes.size());
    }

    @Test
    void testHintForFirstQuestionOfFirstQuiz() {
        List<Quiz> quizzes = quizService.getAllQuizzes();
        assertFalse(quizzes.isEmpty(), "No quizzes in DB");

        Quiz targetQuiz = null;
        List<Question> questions = null;
        for (Quiz q : quizzes) {
            List<Question> qs = questionService.getQuestionsByQuizId(q.getId());
            if (!qs.isEmpty()) {
                targetQuiz = q;
                questions  = qs;
                break;
            }
        }

        assertNotNull(targetQuiz, "No quiz with questions found");
        System.out.println("Testing quiz: " + targetQuiz.getTitle());

        Question first = questions.get(0);
        List<Choice> choices = choiceService.getChoicesByQuestionId(first.getId());
        String choicesText = choices.stream()
                .map(Choice::getContent)
                .collect(Collectors.joining(", "));

        System.out.println("Question: " + first.getText());
        System.out.println("Choices:  " + choicesText);

        String hint = service.generateHint(first.getText(), choicesText);
        System.out.println("Hint:     " + hint);

        assertNotNull(hint);
        assertFalse(hint.isBlank());
        assertFalse(hint.startsWith("Hint unavailable"), "Got API error: " + hint);
    }

    @Test
    void testHintForEveryQuestionInFirstQuiz() {
        List<Quiz> quizzes = quizService.getAllQuizzes();
        assertFalse(quizzes.isEmpty(), "No quizzes in DB");

        Quiz targetQuiz = null;
        List<Question> questions = null;
        for (Quiz q : quizzes) {
            List<Question> qs = questionService.getQuestionsByQuizId(q.getId());
            if (!qs.isEmpty()) {
                targetQuiz = q;
                questions  = qs;
                break;
            }
        }

        assertNotNull(targetQuiz, "No quiz with questions found");
        System.out.println("Quiz: " + targetQuiz.getTitle() + " (" + questions.size() + " questions)");

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            List<Choice> choices = choiceService.getChoicesByQuestionId(q.getId());
            String choicesText = choices.stream()
                    .map(Choice::getContent)
                    .collect(Collectors.joining(", "));

            String hint = service.generateHint(q.getText(), choicesText);
            System.out.println("Q" + (i + 1) + ": " + q.getText());
            System.out.println("  Hint: " + hint);

            assertNotNull(hint);
            assertFalse(hint.isBlank());
            assertFalse(hint.startsWith("Hint unavailable"), "Got API error on Q" + (i + 1) + ": " + hint);
        }
    }
}
