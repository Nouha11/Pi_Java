package services.studysession;

import models.studysession.Course;
import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CourseServiceTest {

    static CourseService service;
    static int testCourseId;

    @BeforeAll
    static void setup() {
        service = new CourseService();
    }

    // ─────────────────────────────
    // TEST 1: CREATE
    // ─────────────────────────────
    @Test
    @Order(1)
    void testCreateCourse() throws SQLException {
        Course c = new Course();
        c.setCourseName("JUnit Course");
        c.setDescription("Testing course");
        c.setDifficulty("BEGINNER");
        c.setEstimatedDuration(120);
        c.setProgress(0);
        c.setStatus("NOT_STARTED");
        c.setCategory("Programming");
        c.setMaxStudents(20);
        c.setPublished(false);

        // Validate before insert
        String validation = service.validate(c, false);
        assertNull(validation);

        service.create(c);
        testCourseId = c.getId();

        assertTrue(testCourseId > 0);

        List<Course> courses = service.findAll();
        assertFalse(courses.isEmpty());
    }

    // ─────────────────────────────
    // TEST 2: READ
    // ─────────────────────────────
    @Test
    @Order(2)
    void testFindById() throws SQLException {
        Course c = service.findById(testCourseId);

        assertNotNull(c);
        assertEquals("JUnit Course", c.getCourseName());
    }

    // ─────────────────────────────
    // TEST 3: UPDATE
    // ─────────────────────────────
    @Test
    @Order(3)
    void testUpdateCourse() throws SQLException {
        Course c = service.findById(testCourseId);

        c.setCourseName("Updated Course");
        c.setDifficulty("ADVANCED");

        String validation = service.validate(c, true);
        assertNull(validation);

        service.update(c);

        Course updated = service.findById(testCourseId);

        assertEquals("Updated Course", updated.getCourseName());
        assertEquals("ADVANCED", updated.getDifficulty());
    }

    // ─────────────────────────────
    // TEST 4: TOGGLE PUBLISH
    // ─────────────────────────────
    @Test
    @Order(4)
    void testTogglePublish() throws SQLException {
        Course c = service.findById(testCourseId);

        boolean initial = c.isPublished();

        service.togglePublish(c);

        Course updated = service.findById(testCourseId);

        assertNotEquals(initial, updated.isPublished());
    }

    // ─────────────────────────────
    // TEST 5: DELETE
    // ─────────────────────────────
    @Test
    @Order(5)
    void testDeleteCourse() throws SQLException {
        service.delete(testCourseId);

        Course c = service.findById(testCourseId);

        assertNull(c);
    }

    // ─────────────────────────────
    // TEST 6: VALIDATION (NEGATIVE CASE)
    // ─────────────────────────────
    @Test
    @Order(6)
    void testValidationFail() {
        Course c = new Course();

        c.setCourseName("A"); // too short
        c.setDifficulty("INVALID"); // wrong value
        c.setEstimatedDuration(-10); // invalid

        String validation = service.validate(c, false);

        assertNotNull(validation);
    }

    // ─────────────────────────────
    // CLEANUP (VERY IMPORTANT)
    // ─────────────────────────────
    @AfterAll
    static void cleanUp() throws Exception {
        List<Course> courses = service.findAll();
        for (Course c : courses) {
            if (c.getCourseName().equals("JUnit Course") ||
                    c.getCourseName().equals("Updated Course")) {
                service.delete(c.getId());
            }
        }
    }

}