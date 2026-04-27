package services.studysession;

import models.studysession.Course;
import models.studysession.EnrollmentRequest;
import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for TutorAnalyticsService.
 * Tests all analytics methods with real database data.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TutorAnalyticsServiceTest {

    static TutorAnalyticsService analyticsService;
    static CourseService courseService;
    static EnrollmentService enrollmentService;
    
    static int testTutorId = 999; // Mock tutor ID for testing
    static int testStudentId1 = 1001;
    static int testStudentId2 = 1002;
    static int testCourseId1;
    static int testCourseId2;

    @BeforeAll
    static void setup() throws SQLException {
        analyticsService = new TutorAnalyticsService();
        courseService = new CourseService();
        enrollmentService = new EnrollmentService();
        
        // Create test courses owned by the test tutor
        Course course1 = new Course();
        course1.setCourseName("Analytics Test Course 1");
        course1.setDescription("Test course for analytics");
        course1.setDifficulty("BEGINNER");
        course1.setEstimatedDuration(120);
        course1.setProgress(50);
        course1.setStatus("IN_PROGRESS");
        course1.setCategory("Testing");
        course1.setPublished(true);
        course1.setCreatedById(testTutorId);
        
        courseService.create(course1);
        testCourseId1 = course1.getId();
        
        Course course2 = new Course();
        course2.setCourseName("Analytics Test Course 2");
        course2.setDescription("Second test course");
        course2.setDifficulty("INTERMEDIATE");
        course2.setEstimatedDuration(180);
        course2.setProgress(100);
        course2.setStatus("COMPLETED");
        course2.setCategory("Testing");
        course2.setPublished(true);
        course2.setCreatedById(testTutorId);
        
        courseService.create(course2);
        testCourseId2 = course2.getId();
        
        // Create test enrollments
        try {
            enrollmentService.createRequest(testCourseId1, testStudentId1, "Test enrollment 1");
            enrollmentService.createRequest(testCourseId1, testStudentId2, "Test enrollment 2");
            enrollmentService.createRequest(testCourseId2, testStudentId1, "Test enrollment 3");
        } catch (IllegalStateException e) {
            // Enrollments might already exist from previous test runs
            System.out.println("Enrollment already exists: " + e.getMessage());
        }
    }

    // ─────────────────────────────
    // TEST 1: GET TOTAL COURSES OWNED
    // ─────────────────────────────
    @Test
    @Order(1)
    void testGetTotalCoursesOwned() throws SQLException {
        int totalCourses = analyticsService.getTotalCoursesOwned(testTutorId);
        
        assertTrue(totalCourses >= 2, "Should have at least 2 test courses");
        System.out.println("Total courses owned: " + totalCourses);
    }

    // ─────────────────────────────
    // TEST 2: GET TOTAL ENROLLED STUDENTS (BEFORE ACCEPTANCE)
    // ─────────────────────────────
    @Test
    @Order(2)
    void testGetTotalEnrolledStudentsBeforeAcceptance() throws SQLException {
        int enrolledStudents = analyticsService.getTotalEnrolledStudents(testTutorId);
        
        // Should be 0 since no enrollments are accepted yet
        assertEquals(0, enrolledStudents, "Should have 0 enrolled students before acceptance");
        System.out.println("Enrolled students (before acceptance): " + enrolledStudents);
    }

    // ─────────────────────────────
    // TEST 3: ACCEPT ENROLLMENTS
    // ─────────────────────────────
    @Test
    @Order(3)
    void testAcceptEnrollments() throws SQLException {
        // Find and accept pending enrollments
        List<EnrollmentRequest> pendingRequests = enrollmentService.findPendingByCreator(testTutorId);
        
        for (EnrollmentRequest request : pendingRequests) {
            enrollmentService.acceptRequest(request.getId(), testTutorId);
        }
        
        assertTrue(pendingRequests.size() >= 2, "Should have accepted at least 2 enrollments");
        System.out.println("Accepted " + pendingRequests.size() + " enrollment requests");
    }

    // ─────────────────────────────
    // TEST 4: GET TOTAL ENROLLED STUDENTS (AFTER ACCEPTANCE)
    // ─────────────────────────────
    @Test
    @Order(4)
    void testGetTotalEnrolledStudentsAfterAcceptance() throws SQLException {
        int enrolledStudents = analyticsService.getTotalEnrolledStudents(testTutorId);
        
        assertTrue(enrolledStudents >= 2, "Should have at least 2 enrolled students after acceptance");
        System.out.println("Enrolled students (after acceptance): " + enrolledStudents);
    }

    // ─────────────────────────────
    // TEST 5: GET ACTIVE STUDENTS
    // ─────────────────────────────
    @Test
    @Order(5)
    void testGetActiveStudents() throws SQLException {
        int activeStudents = analyticsService.getActiveStudents(testTutorId);
        
        // Should be 0 since no study sessions are created yet
        assertTrue(activeStudents >= 0, "Active students should be non-negative");
        System.out.println("Active students: " + activeStudents);
    }

    // ─────────────────────────────
    // TEST 6: GET COMPLETION RATE
    // ─────────────────────────────
    @Test
    @Order(6)
    void testGetCompletionRate() throws SQLException {
        double completionRate = analyticsService.getCompletionRate(testTutorId);
        
        assertTrue(completionRate >= 0 && completionRate <= 100, 
                   "Completion rate should be between 0 and 100");
        System.out.println("Completion rate: " + completionRate + "%");
    }

    // ─────────────────────────────
    // TEST 7: GET PER-COURSE BREAKDOWN
    // ─────────────────────────────
    @Test
    @Order(7)
    void testGetPerCourseBreakdown() throws SQLException {
        List<Map<String, Object>> breakdown = analyticsService.getPerCourseBreakdown(testTutorId);
        
        assertFalse(breakdown.isEmpty(), "Should have course breakdown data");
        
        for (Map<String, Object> courseData : breakdown) {
            assertNotNull(courseData.get("courseName"), "Course name should not be null");
            assertNotNull(courseData.get("enrolledCount"), "Enrolled count should not be null");
            assertNotNull(courseData.get("progress"), "Progress should not be null");
            
            System.out.println("Course: " + courseData.get("courseName") + 
                             ", Enrolled: " + courseData.get("enrolledCount") + 
                             ", Progress: " + courseData.get("progress") + "%");
        }
    }

    // ─────────────────────────────
    // TEST 8: GET MOST POPULAR COURSE
    // ─────────────────────────────
    @Test
    @Order(8)
    void testGetMostPopularCourse() throws SQLException {
        Map<String, Object> popularCourse = analyticsService.getMostPopularCourse(testTutorId);
        
        assertNotNull(popularCourse, "Should have a most popular course");
        assertNotNull(popularCourse.get("courseName"), "Course name should not be null");
        assertNotNull(popularCourse.get("enrolledCount"), "Enrolled count should not be null");
        
        System.out.println("Most popular course: " + popularCourse.get("courseName") + 
                         " with " + popularCourse.get("enrolledCount") + " enrollments");
    }

    // ─────────────────────────────
    // TEST 9: EMPTY TUTOR (NO COURSES)
    // ─────────────────────────────
    @Test
    @Order(9)
    void testEmptyTutor() throws SQLException {
        int emptyTutorId = 99999; // Non-existent tutor
        
        int totalCourses = analyticsService.getTotalCoursesOwned(emptyTutorId);
        int enrolledStudents = analyticsService.getTotalEnrolledStudents(emptyTutorId);
        int activeStudents = analyticsService.getActiveStudents(emptyTutorId);
        double completionRate = analyticsService.getCompletionRate(emptyTutorId);
        List<Map<String, Object>> breakdown = analyticsService.getPerCourseBreakdown(emptyTutorId);
        Map<String, Object> popularCourse = analyticsService.getMostPopularCourse(emptyTutorId);
        
        assertEquals(0, totalCourses, "Empty tutor should have 0 courses");
        assertEquals(0, enrolledStudents, "Empty tutor should have 0 enrolled students");
        assertEquals(0, activeStudents, "Empty tutor should have 0 active students");
        assertEquals(0.0, completionRate, "Empty tutor should have 0% completion rate");
        assertTrue(breakdown.isEmpty(), "Empty tutor should have empty breakdown");
        assertNull(popularCourse, "Empty tutor should have no popular course");
        
        System.out.println("Empty tutor test passed - all metrics returned zero/null as expected");
    }

    // ─────────────────────────────
    // CLEANUP
    // ─────────────────────────────
    @AfterAll
    static void cleanUp() throws SQLException {
        // Clean up test data
        try {
            if (testCourseId1 > 0) {
                courseService.delete(testCourseId1);
            }
            if (testCourseId2 > 0) {
                courseService.delete(testCourseId2);
            }
            System.out.println("Test cleanup completed");
        } catch (SQLException e) {
            System.err.println("Cleanup error: " + e.getMessage());
        }
    }
}
