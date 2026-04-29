package services.quiz;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TranslationService using the MyMemory API.
 * No API key required — MyMemory is free and anonymous.
 */
class TranslationServiceTest {

    static TranslationService translator;

    @BeforeAll
    static void setup() {
        translator = new TranslationService();
    }

    @Test
    void testEnglishToFrench() {
        String result = translator.translate("What is the capital of France?", "fr");
        System.out.println("FR: " + result);
        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @Test
    void testEnglishToArabic() {
        String result = translator.translate("Which planet is closest to the Sun?", "ar");
        System.out.println("AR: " + result);
        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @Test
    void testEnglishToSpanish() {
        String result = translator.translate("What is the chemical symbol for water?", "es");
        System.out.println("ES: " + result);
        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @Test
    void testEnglishToGerman() {
        String result = translator.translate("Which data structure uses LIFO order?", "de");
        System.out.println("DE: " + result);
        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @Test
    void testEnglishToItalian() {
        String result = translator.translate("The correct answer is related to science.", "it");
        System.out.println("IT: " + result);
        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @Test
    void testEnglishReturnsOriginalUnchanged() {
        String original = "What is the largest ocean on Earth?";
        String result = translator.translate(original, "en");
        assertEquals(original, result, "English to English should return the original text");
    }

    @Test
    void testNullInputReturnsNull() {
        String result = translator.translate(null, "fr");
        assertNull(result);
    }

    @Test
    void testBlankInputReturnsBlank() {
        String result = translator.translate("   ", "fr");
        assertNotNull(result);
        assertTrue(result.isBlank(), "Blank input should return blank");
    }

    @Test
    void testShortChoiceTranslation() {
        String result = translator.translate("Paris", "ar");
        System.out.println("Choice AR: " + result);
        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @Test
    void testAllSupportedLanguagesArePresent() {
        assertTrue(TranslationService.LANGUAGES.containsKey("English"));
        assertTrue(TranslationService.LANGUAGES.containsKey("French"));
        assertTrue(TranslationService.LANGUAGES.containsKey("Arabic"));
        assertTrue(TranslationService.LANGUAGES.containsKey("Spanish"));
        assertTrue(TranslationService.LANGUAGES.containsKey("German"));
        assertEquals("en", TranslationService.LANGUAGES.get("English"));
        assertEquals("fr", TranslationService.LANGUAGES.get("French"));
        assertEquals("ar", TranslationService.LANGUAGES.get("Arabic"));
    }
}
