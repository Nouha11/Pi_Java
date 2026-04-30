package services.quiz;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for hint generation via OpenRouter (poolside/laguna-xs.2:free).
 * Makes real HTTP calls — requires a valid openrouter key in api.properties.
 */
class OpenRouterServiceTest {

    static HuggingFaceService service;

    @BeforeAll
    static void setup() {
        service = new HuggingFaceService();
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

    @Test
    void testHintDoesNotRevealAnswer() {
        String hint = service.generateHint(
                "What is the powerhouse of the cell?",
                "Nucleus, Mitochondria, Ribosome, Golgi apparatus"
        );
        System.out.println("Hint: " + hint);
        assertNotNull(hint);
        assertFalse(hint.isBlank());
        assertFalse(hint.startsWith("Hint unavailable"), "Got error: " + hint);
        assertFalse(hint.toLowerCase().contains("mitochondria"),
                "Hint should not directly reveal the answer");
    }

    @Test
    void testHintForMathQuestion() {
        String hint = service.generateHint(
                "What is the result of 2 raised to the power of 10?",
                "512, 1024, 2048, 256"
        );
        System.out.println("Hint: " + hint);
        assertNotNull(hint);
        assertFalse(hint.isBlank());
        assertFalse(hint.startsWith("Hint unavailable"), "Got error: " + hint);
    }

    @Test
    void testHintForHistoryQuestion() {
        String hint = service.generateHint(
                "In which year did World War II end?",
                "1943, 1944, 1945, 1946"
        );
        System.out.println("Hint: " + hint);
        assertNotNull(hint);
        assertFalse(hint.isBlank());
        assertFalse(hint.startsWith("Hint unavailable"), "Got error: " + hint);
    }
}
