package services.library;

import models.library.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BookServiceTest {

    private BookService bookService;

    @BeforeEach
    void setUp() {
        bookService = new BookService();
    }

    // ─────────────────────────────────────────────
    //  VALIDATION
    // ─────────────────────────────────────────────

    @Test
    void testValidBook() {
        Book b = new Book();
        b.setTitle("Clean Code");
        b.setAuthor("Robert Martin");
        b.setPrice(29.99);
        b.setType("physical");

        assertNull(bookService.validate(b, false));
    }

    @Test
    void testInvalidBook_NoTitle() {
        Book b = new Book();
        b.setAuthor("Author");
        b.setPrice(10.0);
        b.setType("physical");

        assertNotNull(bookService.validate(b, false));
    }

    @Test
    void testInvalidBook_TitleTooShort() {
        Book b = new Book();
        b.setTitle("A");
        b.setAuthor("Author");
        b.setPrice(10.0);
        b.setType("physical");

        assertNotNull(bookService.validate(b, false));
    }

    @Test
    void testInvalidBook_NoAuthor() {
        Book b = new Book();
        b.setTitle("Some Book");
        b.setPrice(10.0);
        b.setType("physical");

        assertNotNull(bookService.validate(b, false));
    }

    @Test
    void testInvalidBook_NegativePrice() {
        Book b = new Book();
        b.setTitle("Some Book");
        b.setAuthor("Author");
        b.setPrice(-5.0);
        b.setType("physical");

        assertNotNull(bookService.validate(b, false));
    }

    @Test
    void testInvalidBook_BadType() {
        Book b = new Book();
        b.setTitle("Some Book");
        b.setAuthor("Author");
        b.setPrice(10.0);
        b.setType("ebook"); // invalid

        assertNotNull(bookService.validate(b, false));
    }

    @Test
    void testValidBook_Digital() {
        Book b = new Book();
        b.setTitle("Java Programming");
        b.setAuthor("James Gosling");
        b.setPrice(0.0);
        b.setType("digital");

        assertNull(bookService.validate(b, false));
    }

    @Test
    void testBook_isDigital() {
        Book b = new Book();
        b.setType("digital");
        assertTrue(b.isDigital());
    }

    @Test
    void testBook_isNotDigital() {
        Book b = new Book();
        b.setType("physical");
        assertFalse(b.isDigital());
    }
}
