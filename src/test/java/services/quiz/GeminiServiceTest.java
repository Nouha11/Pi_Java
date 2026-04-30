package services.quiz;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for hint generation via Gemini 2.0 Flash.
 * Makes real HTTP calls — requires a valid gemini_api key in api.properties.
 */
class GeminiServiceTest {

    static GeminiService service;

    @BeforeAll
    static void setup() {
        service = new GeminiService();
    }

    @Test
    void testHintIsReturnedForSimpleQuestion() {
        String hint = service.generateHint(
                "What is the capital of France?",
                "Paris, London, Berlin, Madrid"
        );
        System.out.println("Hint: " + hint);
        assertNotNull(hint);
        assertFalse(hint.isBlank());
        assertFalse(hint.startsWith("Hint unavailable"), "Got error: " + hint);
    }

    @Test
    void testHintIsReturnedForScienceQuestion() {
        String hint = service.generateHint(
                "What is the chemical symbol for water?",
                "HO, H2O, CO2, NaCl"
        );
        System.out.println("Hint: " + hint);
        assertNotNull(hint);
        assertFalse(hint.isBlank());
        assertFalse(hint.startsWith("Hint unavailable"), "Got error: " + hint);
    }

    @Test
    void testHintIsReturnedForProgrammingQuestion() {
        String hint = service.generateHint(
                "Which data structure uses LIFO order?",
                "Queue, Stack, LinkedList, Tree"
        );
        System.out.println("Hint: " + hint);
        assertNotNull(hint);
        assertFalse(hint.isBlank());
        assertFalse(hint.startsWith("Hint unavailable"), "Got error: " + hint);
    }

    @Test
    void testHintWithNoChoices() {
        String hint = service.generateHint(
                "What is the largest ocean on Earth?",
                ""
        );
        System.out.println("Hint (no choices): " + hint);
        assertNotNull(hint);
        assertFalse(hint.isBlank());
    }

    @Test
    void testHintIsMoreThanOneWord() {
        String hint = service.generateHint(
                "Which planet is known as the Red Planet?",
                "Venus, Mars, Jupiter, Saturn"
        );
        System.out.println("Hint: " + hint);
        assertNotNull(hint);
        assertTrue(hint.length() > 10, "Hint should be a sentence, not just one word");
    }
}
