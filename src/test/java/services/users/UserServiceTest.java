package services.users;

import models.users.User;
import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserServiceTest {

    static UserService service;
    static int testUserId;

    @BeforeAll
    static void setup() {
        service = new UserService();
    }

    // ─────────────────────────────────────────
    // TEST 1: CREATE
    // ─────────────────────────────────────────
    @Test
    @Order(1)
    void testCreateUser() throws SQLException {
        User u = new User();
        u.setEmail("junit_test@nova.com");
        u.setUsername("junit_testuser");
        u.setPassword("$2a$13$hashedpasswordfortest");
        u.setRole(User.Role.ROLE_STUDENT);
        u.setActive(true);
        u.setVerified(false);
        u.setBanned(false);
        u.setXp(0);

        boolean result = service.addUser(u);
        testUserId = u.getId();

        assertTrue(result, "User should be created successfully");
        assertTrue(testUserId > 0, "Created user should have a valid ID");
    }

    // ─────────────────────────────────────────
    // TEST 2: READ ALL
    // ─────────────────────────────────────────
    @Test
    @Order(2)
    void testGetAllUsers() throws SQLException {
        List<User> users = service.getAllUsers();

        assertNotNull(users, "User list should not be null");
        assertFalse(users.isEmpty(), "User list should not be empty");
    }

    // ─────────────────────────────────────────
    // TEST 3: READ ONE
    // ─────────────────────────────────────────
    @Test
    @Order(3)
    void testGetUserById() throws SQLException {
        User u = service.getUserById(testUserId);

        assertNotNull(u, "User should be found by ID");
        assertEquals("junit_testuser", u.getUsername());
        assertEquals("junit_test@nova.com", u.getEmail());
        assertEquals(User.Role.ROLE_STUDENT, u.getRole());
    }

    // ─────────────────────────────────────────
    // TEST 4: UPDATE
    // ─────────────────────────────────────────
    @Test
    @Order(4)
    void testUpdateUser() throws SQLException {
        User u = service.getUserById(testUserId);
        assertNotNull(u);

        u.setEmail("junit_updated@nova.com");
        u.setRole(User.Role.ROLE_TUTOR);
        u.setXp(100);

        boolean result = service.updateUser(u);
        assertTrue(result, "Update should succeed");

        User updated = service.getUserById(testUserId);
        assertEquals("junit_updated@nova.com", updated.getEmail());
        assertEquals(User.Role.ROLE_TUTOR, updated.getRole());
        assertEquals(100, updated.getXp());
    }

    // ─────────────────────────────────────────
    // TEST 5: SEARCH
    // ─────────────────────────────────────────
    @Test
    @Order(5)
    void testSearchUsers() throws SQLException {
        List<User> results = service.searchUsers("junit");

        assertNotNull(results);
        assertFalse(results.isEmpty(), "Search should find the test user");
        assertTrue(results.stream().anyMatch(u -> u.getUsername().contains("junit")));
    }

    // ─────────────────────────────────────────
    // TEST 6: FILTER BY ROLE
    // ─────────────────────────────────────────
    @Test
    @Order(6)
    void testFilterByRole() throws SQLException {
        List<User> tutors = service.filterByRole(User.Role.ROLE_TUTOR);

        assertNotNull(tutors);
        assertTrue(tutors.stream().allMatch(u -> u.getRole() == User.Role.ROLE_TUTOR),
            "All filtered users should have ROLE_TUTOR");
    }

    // ─────────────────────────────────────────
    // TEST 7: STATS — SUMMARY
    // ─────────────────────────────────────────
    @Test
    @Order(7)
    void testGetSummaryStats() throws SQLException {
        Map<String, Integer> stats = service.getSummaryStats();

        assertNotNull(stats);
        assertTrue(stats.containsKey("total"));
        assertTrue(stats.containsKey("active"));
        assertTrue(stats.containsKey("banned"));
        assertTrue(stats.containsKey("verified"));
        assertTrue(stats.get("total") > 0, "Total users should be > 0");
    }

    // ─────────────────────────────────────────
    // TEST 8: STATS — ROLE BREAKDOWN
    // ─────────────────────────────────────────
    @Test
    @Order(8)
    void testGetRoleStats() throws SQLException {
        Map<String, Integer> stats = service.getRoleStats();

        assertNotNull(stats);
        assertFalse(stats.isEmpty(), "Role stats should not be empty");
    }

    // ─────────────────────────────────────────
    // TEST 9: DELETE
    // ─────────────────────────────────────────
    @Test
    @Order(9)
    void testDeleteUser() throws SQLException {
        boolean result = service.deleteUser(testUserId);
        assertTrue(result, "Delete should succeed");

        User deleted = service.getUserById(testUserId);
        assertNull(deleted, "User should not exist after deletion");
    }

    // ─────────────────────────────────────────
    // TEST 10: VALIDATION — VALID INPUT
    // ─────────────────────────────────────────
    @Test
    @Order(10)
    void testValidationPass() {
        List<String> errors = ValidationUtil.validateUser(
            "valid@email.com", "validUser1", "StrongPass1", "ROLE_STUDENT", true
        );
        assertTrue(errors.isEmpty(), "Valid input should produce no errors");
    }

    // ─────────────────────────────────────────
    // TEST 11: VALIDATION — BLANK EMAIL
    // ─────────────────────────────────────────
    @Test
    @Order(11)
    void testValidationBlankEmail() {
        List<String> errors = ValidationUtil.validateUser(
            "", "validUser1", "StrongPass1", "ROLE_STUDENT", true
        );
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("Email is required")));
    }

    // ─────────────────────────────────────────
    // TEST 12: VALIDATION — INVALID EMAIL FORMAT
    // ─────────────────────────────────────────
    @Test
    @Order(12)
    void testValidationInvalidEmail() {
        List<String> errors = ValidationUtil.validateUser(
            "not-an-email", "validUser1", "StrongPass1", "ROLE_STUDENT", true
        );
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("valid email")));
    }

    // ─────────────────────────────────────────
    // TEST 13: VALIDATION — USERNAME TOO SHORT
    // ─────────────────────────────────────────
    @Test
    @Order(13)
    void testValidationUsernameTooShort() {
        List<String> errors = ValidationUtil.validateUser(
            "valid@email.com", "ab", "StrongPass1", "ROLE_STUDENT", true
        );
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("3 characters")));
    }

    // ─────────────────────────────────────────
    // TEST 14: VALIDATION — USERNAME INVALID CHARS
    // ─────────────────────────────────────────
    @Test
    @Order(14)
    void testValidationUsernameInvalidChars() {
        List<String> errors = ValidationUtil.validateUser(
            "valid@email.com", "user name!", "StrongPass1", "ROLE_STUDENT", true
        );
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("letters, numbers and underscores")));
    }

    // ─────────────────────────────────────────
    // TEST 15: VALIDATION — PASSWORD TOO SHORT
    // ─────────────────────────────────────────
    @Test
    @Order(15)
    void testValidationPasswordTooShort() {
        List<String> errors = ValidationUtil.validateUser(
            "valid@email.com", "validUser1", "Sh0rt", "ROLE_STUDENT", true
        );
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("8 characters")));
    }

    // ─────────────────────────────────────────
    // TEST 16: VALIDATION — PASSWORD NO UPPERCASE
    // ─────────────────────────────────────────
    @Test
    @Order(16)
    void testValidationPasswordNoUppercase() {
        List<String> errors = ValidationUtil.validateUser(
            "valid@email.com", "validUser1", "nouppercase1", "ROLE_STUDENT", true
        );
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("uppercase")));
    }

    // ─────────────────────────────────────────
    // TEST 17: VALIDATION — PASSWORD NO DIGIT
    // ─────────────────────────────────────────
    @Test
    @Order(17)
    void testValidationPasswordNoDigit() {
        List<String> errors = ValidationUtil.validateUser(
            "valid@email.com", "validUser1", "NoDigitPass", "ROLE_STUDENT", true
        );
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("digit")));
    }

    // ─────────────────────────────────────────
    // TEST 18: VALIDATION — EDIT USER, NO PASSWORD REQUIRED
    // ─────────────────────────────────────────
    @Test
    @Order(18)
    void testValidationEditUserNoPassword() {
        // isNewUser = false, so blank password is allowed
        List<String> errors = ValidationUtil.validateUser(
            "valid@email.com", "validUser1", "", "ROLE_STUDENT", false
        );
        assertTrue(errors.isEmpty(), "Editing user with blank password should be valid");
    }

    // ─────────────────────────────────────────
    // CLEANUP
    // ─────────────────────────────────────────
    @AfterAll
    static void cleanUp() throws Exception {
        List<User> users = service.getAllUsers();
        for (User u : users) {
            if (u.getUsername().startsWith("junit_")) {
                service.deleteUser(u.getId());
            }
        }
    }
}
