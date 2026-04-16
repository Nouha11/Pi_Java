package services.gamification;

import models.gamification.Reward;
import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RewardServiceTest {

    static RewardService service;
    static int testRewardId;

    @BeforeAll
    static void setup() {
        service = new RewardService();
    }

    // ─────────────────────────────
    // TEST 1: CREATE
    // ─────────────────────────────
    @Test
    @Order(1)
    void testAddReward() throws SQLException {
        Reward r = new Reward();
        r.setName("JUnit Badge");
        r.setDescription("A test badge");
        r.setType("BADGE");
        r.setValue(50);
        r.setRequirement("Complete 5 quizzes");
        r.setIcon("badge_test.png");
        r.setActive(true);
        r.setRequiredLevel(null);

        service.addReward(r);

        List<Reward> rewards = service.getAllRewards();
        assertFalse(rewards.isEmpty());

        // grab the inserted reward by name
        Reward inserted = rewards.stream()
                .filter(rw -> rw.getName().equals("JUnit Badge"))
                .findFirst()
                .orElse(null);

        assertNotNull(inserted);
        testRewardId = inserted.getId();
        assertTrue(testRewardId > 0);
    }

    // ─────────────────────────────
    // TEST 2: READ BY ID
    // ─────────────────────────────
    @Test
    @Order(2)
    void testGetRewardById() throws SQLException {
        Reward r = service.getRewardById(testRewardId);

        assertNotNull(r);
        assertEquals("JUnit Badge", r.getName());
        assertEquals("BADGE", r.getType());
        assertEquals(50, r.getValue());
    }

    // ─────────────────────────────
    // TEST 3: UPDATE
    // ─────────────────────────────
    @Test
    @Order(3)
    void testUpdateReward() throws SQLException {
        Reward r = service.getRewardById(testRewardId);

        r.setName("Updated Badge");
        r.setType("ACHIEVEMENT");
        r.setValue(100);
        r.setRequiredLevel(5);

        service.updateReward(r);

        Reward updated = service.getRewardById(testRewardId);

        assertEquals("Updated Badge", updated.getName());
        assertEquals("ACHIEVEMENT", updated.getType());
        assertEquals(100, updated.getValue());
        assertEquals(5, updated.getRequiredLevel());
    }

    // ─────────────────────────────
    // TEST 4: FILTER BY TYPE
    // ─────────────────────────────
    @Test
    @Order(4)
    void testGetRewardsByType() throws SQLException {
        List<Reward> achievements = service.getRewardsByType("ACHIEVEMENT");

        assertNotNull(achievements);
        assertTrue(achievements.stream().allMatch(r -> r.getType().equals("ACHIEVEMENT")));
    }

    // ─────────────────────────────
    // TEST 5: SEARCH BY KEYWORD
    // ─────────────────────────────
    @Test
    @Order(5)
    void testSearchRewards() throws SQLException {
        List<Reward> results = service.searchRewards("Updated");

        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(r -> r.getName().contains("Updated")));
    }

    // ─────────────────────────────
    // TEST 6: NAME UNIQUENESS CHECK
    // ─────────────────────────────
    @Test
    @Order(6)
    void testRewardNameExists() throws SQLException {
        // same name, different id — should exist
        boolean exists = service.rewardNameExists("Updated Badge", 0);
        assertTrue(exists);

        // same name, same id — should not conflict
        boolean selfCheck = service.rewardNameExists("Updated Badge", testRewardId);
        assertFalse(selfCheck);
    }

    // ─────────────────────────────
    // TEST 7: DELETE
    // ─────────────────────────────
    @Test
    @Order(7)
    void testDeleteReward() throws SQLException {
        service.deleteReward(testRewardId);

        Reward r = service.getRewardById(testRewardId);

        assertNull(r);
    }

    // ─────────────────────────────
    // CLEANUP
    // ─────────────────────────────
    @AfterAll
    static void cleanUp() throws Exception {
        List<Reward> rewards = service.getAllRewards();
        for (Reward r : rewards) {
            if (r.getName().equals("JUnit Badge") || r.getName().equals("Updated Badge")) {
                service.deleteReward(r.getId());
            }
        }
    }
}