-- ============================================================
-- Migration: AI Study Assistant
-- Feature: ai-study-assistant
-- Run against: nova_db
-- ============================================================

-- ── ai_chat_messages ───────────────────────────────────────
-- Persists per-student per-course chat history for the AI
-- Study Assistant widget. Each row represents one message
-- turn (USER or ASSISTANT role).
CREATE TABLE IF NOT EXISTS ai_chat_messages (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    student_id   INT NOT NULL,
    course_id    INT NOT NULL,
    role         VARCHAR(20) NOT NULL,          -- 'USER' or 'ASSISTANT'
    message_text TEXT NOT NULL,
    is_favorite  TINYINT(1) NOT NULL DEFAULT 0,
    created_at   DATETIME NOT NULL,
    INDEX idx_student_course (student_id, course_id),
    CONSTRAINT fk_acm_student FOREIGN KEY (student_id)
        REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_acm_course  FOREIGN KEY (course_id)
        REFERENCES course(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
