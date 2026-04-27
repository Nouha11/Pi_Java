-- ============================================================
-- Migration: Course Learning Experience
-- Feature: course-learning-experience
-- Run against: nova_db
-- ============================================================

-- ── student_course_progress ────────────────────────────────
-- Tracks per-student per-course learning progress, Pomodoro
-- cycles, study streaks, and time studied.
CREATE TABLE IF NOT EXISTS student_course_progress (
    id                        INT AUTO_INCREMENT PRIMARY KEY,
    student_id                INT NOT NULL,
    course_id                 INT NOT NULL,
    progress_percentage       INT NOT NULL DEFAULT 0,
    total_minutes_studied     INT NOT NULL DEFAULT 0,
    pomodoro_cycles_completed INT NOT NULL DEFAULT 0,
    last_activity_at          DATETIME NULL,
    study_streak_days         INT NOT NULL DEFAULT 0,
    last_streak_date          DATE NULL,
    first_activity_at         DATETIME NULL,
    UNIQUE KEY uq_student_course (student_id, course_id),
    CONSTRAINT fk_scp_student FOREIGN KEY (student_id)
        REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_scp_course  FOREIGN KEY (course_id)
        REFERENCES course(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── course_pdf_resource ────────────────────────────────────
-- Stores metadata for PDF files uploaded by tutors and
-- associated with a specific course.
CREATE TABLE IF NOT EXISTS course_pdf_resource (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    course_id       INT NOT NULL,
    title           VARCHAR(255) NOT NULL,
    topic           VARCHAR(255) NULL,
    file_path       VARCHAR(512) NOT NULL,
    uploaded_at     DATETIME NOT NULL,
    uploaded_by_id  INT NULL,
    CONSTRAINT fk_cpr_course   FOREIGN KEY (course_id)
        REFERENCES course(id) ON DELETE CASCADE,
    CONSTRAINT fk_cpr_uploader FOREIGN KEY (uploaded_by_id)
        REFERENCES user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
