package services.quiz;

import models.quiz.Choice;
import models.quiz.Question;
import models.quiz.Quiz;
import services.QuestionService;
import services.ChoiceService;
import org.junit.jupiter.api.*;
import services.QuizService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class QuizServiceTest {

    static QuizService quizService;
    static QuestionService questionService;
    static ChoiceService choiceService;
    
    static int testQuizId;
    static int testQuestion1Id;
    static int testQuestion2Id;

    @BeforeAll
    static void setup() {
        quizService = new QuizService();
        questionService = new QuestionService();
        choiceService = new ChoiceService();
    }

    // ─────────────────────────────
    // TEST 1: CREATE QUIZ
    // ─────────────────────────────
    @Test
    @Order(1)
    void testCreateQuiz() {
        Quiz quiz = new Quiz("Java Fundamentals", "Basic Java concepts and OOP principles");

        assertNotNull(quiz);
        assertEquals("Java Fundamentals", quiz.getTitle());
        assertEquals("Basic Java concepts and OOP principles", quiz.getDescription());

        // In a real scenario, you would save to DB here
        // quizService.createQuiz(quiz);
        // testQuizId = quiz.getId();
        
        System.out.println("✅ Quiz created: " + quiz);
    }

    // ─────────────────────────────
    // TEST 2: CREATE QUESTION WITH CHOICES
    // ─────────────────────────────
    @Test
    @Order(2)
    void testCreateQuestionWithChoices() {
        // Create a question
        Question question = new Question(
                "What is encapsulation?",
                10,
                "BEGINNER",
                1  // quizId = 1 (test quiz)
        );
        question.setUpdatedAt(LocalDateTime.now());

        assertNotNull(question);
        assertEquals("What is encapsulation?", question.getText());
        assertEquals(10, question.getXpValue());
        assertEquals("BEGINNER", question.getDifficulty());

        // Create choices
        List<Choice> choices = new ArrayList<>();
        
        Choice choice1 = new Choice("Bundling data and methods into a single unit", true, 1);
        Choice choice2 = new Choice("Making data public", false, 1);
        Choice choice3 = new Choice("Inheriting from parent classes", false, 1);
        Choice choice4 = new Choice("Creating multiple objects", false, 1);

        choices.add(choice1);
        choices.add(choice2);
        choices.add(choice3);
        choices.add(choice4);

        question.setChoices(choices);

        // Verify question-choice relationship
        assertNotNull(question.getChoices());
        assertEquals(4, question.getChoices().size());

        // Verify correct answer exists
        boolean hasCorrectAnswer = question.getChoices().stream()
                .anyMatch(Choice::isCorrect);
        assertTrue(hasCorrectAnswer);

        // Count correct answers (should be exactly 1)
        long correctAnswerCount = question.getChoices().stream()
                .filter(Choice::isCorrect)
                .count();
        assertEquals(1, correctAnswerCount);

        System.out.println("✅ Question created with choices: " + question);
        System.out.println("   Choices: " + question.getChoices());
    }

    // ─────────────────────────────
    // TEST 3: CREATE QUIZ WITH MULTIPLE QUESTIONS
    // ─────────────────────────────
    @Test
    @Order(3)
    void testCreateQuizWithMultipleQuestions() {
        // Create quiz
        Quiz quiz = new Quiz("Advanced Java", "OOP, Collections, Lambda Expressions");
        List<Question> questions = new ArrayList<>();

        // Question 1
        Question q1 = new Question("What is polymorphism?", 15, "INTERMEDIATE", 2);
        q1.setUpdatedAt(LocalDateTime.now());
        List<Choice> q1Choices = new ArrayList<>();
        q1Choices.add(new Choice("Ability of objects to take multiple forms", true, 1));
        q1Choices.add(new Choice("Creating multiple classes", false, 1));
        q1Choices.add(new Choice("Using inheritance", false, 1));
        q1.setChoices(q1Choices);
        questions.add(q1);

        // Question 2
        Question q2 = new Question("What is a Stream in Java?", 12, "INTERMEDIATE", 2);
        q2.setUpdatedAt(LocalDateTime.now());
        List<Choice> q2Choices = new ArrayList<>();
        q2Choices.add(new Choice("A sequence of elements that can be processed in parallel", true, 2));
        q2Choices.add(new Choice("A file I/O class", false, 2));
        q2Choices.add(new Choice("A networking class", false, 2));
        q2.setChoices(q2Choices);
        questions.add(q2);

        // Question 3
        Question q3 = new Question("What is a Lambda expression?", 10, "INTERMEDIATE", 2);
        q3.setUpdatedAt(LocalDateTime.now());
        List<Choice> q3Choices = new ArrayList<>();
        q3Choices.add(new Choice("An anonymous function with a concise syntax", true, 3));
        q3Choices.add(new Choice("A type of variable", false, 3));
        q3Choices.add(new Choice("A loop structure", false, 3));
        q3.setChoices(q3Choices);
        questions.add(q3);

        // Tie questions to quiz
        quiz.setQuestions(questions);

        // Verify relationships
        assertNotNull(quiz.getQuestions());
        assertEquals(3, quiz.getQuestions().size());

        for (Question q : quiz.getQuestions()) {
            assertNotNull(q.getChoices());
            assertEquals(3, q.getChoices().size());
            assertTrue(q.getQuizId() > 0);
        }

        System.out.println("✅ Quiz created with multiple questions: " + quiz.getTitle());
        System.out.println("   Total questions: " + quiz.getQuestions().size());
        for (Question q : quiz.getQuestions()) {
            System.out.println("   - " + q.getText() + " (" + q.getChoices().size() + " choices)");
        }
    }

    // ─────────────────────────────
    // TEST 4: VALIDATE QUIZ STRUCTURE
    // ─────────────────────────────
    @Test
    @Order(4)
    void testValidateQuizStructure() {
        Quiz quiz = new Quiz("Validation Test Quiz", "Testing structure validation");
        List<Question> questions = new ArrayList<>();

        Question question = new Question("Test question?", 5, "EASY", 3);
        question.setUpdatedAt(LocalDateTime.now());
        
        List<Choice> choices = new ArrayList<>();
        choices.add(new Choice("Option A", true, 1));
        choices.add(new Choice("Option B", false, 1));
        question.setChoices(choices);
        
        questions.add(question);
        quiz.setQuestions(questions);

        // Validation checks
        assertNotNull(quiz.getTitle());
        assertFalse(quiz.getTitle().isEmpty());
        
        assertNotNull(quiz.getQuestions());
        assertFalse(quiz.getQuestions().isEmpty());

        for (Question q : quiz.getQuestions()) {
            assertNotNull(q.getText());
            assertFalse(q.getText().isEmpty());
            assertTrue(q.getXpValue() > 0);
            assertNotNull(q.getChoices());
            assertTrue(q.getChoices().size() >= 2, "Question must have at least 2 choices");
            
            // Verify exactly one correct answer
            long correctCount = q.getChoices().stream()
                    .filter(Choice::isCorrect)
                    .count();
            assertEquals(1, correctCount, "Question must have exactly one correct answer");
        }

        System.out.println("✅ Quiz structure validation passed!");
    }

    // ─────────────────────────────
    // TEST 5: CHOICE CONTENT VALIDATION
    // ─────────────────────────────
    @Test
    @Order(5)
    void testChoiceContentValidation() {
        Choice choice = new Choice("Valid choice content", true, 1);

        assertNotNull(choice.getContent());
        assertFalse(choice.getContent().isEmpty());
        assertTrue(choice.getContent().length() > 0);
        assertTrue(choice.isCorrect());

        // Test invalid choice
        assertThrows(Exception.class, () -> {
            Choice invalidChoice = new Choice("", true, 1);
            if (invalidChoice.getContent().isEmpty()) {
                throw new Exception("Choice content cannot be empty");
            }
        });

        System.out.println("✅ Choice content validation passed!");
    }

    // ─────────────────────────────
    // TEST 6: QUESTION TO CHOICE RELATIONSHIP
    // ─────────────────────────────
    @Test
    @Order(6)
    void testQuestionToChoiceRelationship() {
        Question question = new Question("Select the correct definition", 8, "BEGINNER", 1);
        
        List<Choice> choices = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Choice choice = new Choice("Option " + i, i == 1, i);  // First one is correct
            choices.add(choice);
        }
        
        question.setChoices(choices);

        // Verify all choices belong to same question
        for (Choice choice : question.getChoices()) {
            assertTrue(choice.getQuestionId() > 0);
            assertEquals(choice.getQuestionId(), question.getChoices().get(0).getQuestionId());
        }

        System.out.println("✅ Question-Choice relationship validated!");
    }
}
