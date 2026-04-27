# Requirements Document

## Introduction

This feature extends the NOVA JavaFX desktop learning platform (Java 17, Maven, MySQL `nova_db`) with a comprehensive **Course Learning Experience** system. The platform already has three roles — Student (`ROLE_STUDENT`), Tutor (`ROLE_TUTOR`), and Admin (`ROLE_ADMIN`) — and a working enrollment system (see `course-enrollment-roles` spec). The existing `Course`, `StudySession`, `Planning`, `EnrollmentRequest`, and `User` models are reused without modification to their schemas.

This feature adds:
- An active **Course Content Page** that students land on after clicking "Start Course"
- A built-in **Pomodoro Timer** for productivity tracking
- **YouTube Data API** integration for recommended learning videos
- **Wikipedia REST API** integration for contextual topic summaries
- **Student Progress Tracking** (time-based, persisted per student per course)
- **Tutor Progress Monitoring** dashboard showing enrolled student progress
- **PDF Resource Management** (Tutors upload; Students view/download)
- A **Course Details Page** (separate from the content page) showing planning sessions and quick analytics

All new functionality is added within the `studysession` module and must not break any existing CRUD, authentication, enrollment, UI theme, or database integrity.

---

## Glossary

- **System**: The NOVA JavaFX desktop learning platform.
- **Course_Content_Page**: The active learning view opened when a Student clicks "Start Course"; contains the Pomodoro Timer, content area, YouTube videos, Wikipedia summary, PDF resources, and progress bar.
- **Course_Details_Page**: A separate informational view opened when a Student clicks "Details"; shows planned sessions, upcoming/completed sessions, progress percentage, course summary, and quick analytics.
- **Pomodoro_Timer**: A built-in countdown timer following the Pomodoro Technique: 25-minute focus sessions alternating with 5-minute short breaks.
- **Focus_Session**: A 25-minute Pomodoro work interval.
- **Short_Break**: A 5-minute Pomodoro rest interval.
- **Pomodoro_Cycle**: One completed Focus_Session (25 minutes). Counted and persisted per student per course.
- **Student_Progress**: A persisted record tracking a Student's learning progress for a specific course, including percentage complete, total minutes studied, Pomodoro cycles completed, last activity timestamp, and study streak days.
- **Progress_Percentage**: A numeric value from 0 to 100 representing how far a Student has progressed in a course, incremented by 1 for each minute of active study, capped at 100.
- **Study_Streak**: The number of consecutive calendar days on which a Student has recorded at least one active study minute for a given course.
- **YouTube_Service**: The service class responsible for querying the YouTube Data API v3 and caching results.
- **Wikipedia_Service**: The service class responsible for querying the Wikipedia REST API and caching results.
- **PDF_Resource**: A file attachment (PDF format) uploaded by a Tutor and associated with a specific course.
- **PDF_Resource_Table**: A new database table `course_pdf_resource` storing PDF metadata (id, course_id, title, topic, file_path, uploaded_at, uploaded_by_id).
- **Student_Progress_Table**: A new database table `student_course_progress` storing per-student per-course progress data.
- **VideoResult**: A data transfer object holding YouTube video metadata: videoId, title, channelName, thumbnailUrl.
- **WikiSummary**: A data transfer object holding Wikipedia summary data: title, extract (plain text), thumbnailUrl, pageUrl.
- **Tutor_Progress_Monitor**: The view within the Tutor Dashboard showing enrolled student progress for each of the Tutor's courses.
- **CourseAccessGuard**: The existing access-control logic (already implemented in `CourseDetailController`) that prevents Students from accessing course content without an accepted enrollment.
- **UserSession**: The existing singleton `utils.UserSession` storing the logged-in user's `userId`, `username`, `email`, and `role`.
- **EnrollmentService**: The existing service in `services.studysession.EnrollmentService`.
- **StudySession**: The existing entity `models.studysession.StudySession` with fields including `pomodoroCount`, `actualDuration`, `startedAt`, `endedAt`.
- **Planning**: The existing entity `models.studysession.Planning` with fields `scheduledDate`, `scheduledTime`, `plannedDuration`, `status`.
- **API_Config**: A properties file (`src/main/resources/config/api.properties`) storing API keys and configuration values; keys are read at runtime and never hard-coded in source files.

---

## Requirements

---

### Requirement 1: Course Content Page — Entry Point

**User Story:** As a Student, I want clicking "Start Course" to open a dedicated learning page, so that I have a focused environment for studying.

#### Acceptance Criteria

1. WHEN a Student clicks the "▶ Start Course" button on a course card (for an Admin-owned course or an ACCEPTED enrollment), THE System SHALL open the Course_Content_Page in a new maximized window, passing the selected Course object to the page controller.
2. THE Course_Content_Page SHALL display the course name as the window title and as a prominent heading inside the page.
3. THE Course_Content_Page SHALL be implemented as a new FXML view `CourseContentView.fxml` with a corresponding `CourseContentController.java` in the `controllers.studysession` package.
4. THE CourseAccessGuard SHALL be applied at the start of `CourseContentController.initData(Course)`: WHEN the current user's role is `ROLE_STUDENT` and the course is not Admin-owned, THE System SHALL verify the enrollment status via `EnrollmentService.getEnrollmentStatus`; IF the status is not `ACCEPTED`, THEN THE System SHALL display an "Access Denied" alert and close the window.
5. THE Course_Content_Page SHALL be divided into a left panel (Pomodoro Timer + progress) and a right panel (content area, YouTube videos, Wikipedia summary, PDF resources) using a `SplitPane` or equivalent layout.

---

### Requirement 2: Pomodoro Timer

**User Story:** As a Student, I want a built-in Pomodoro timer on the course content page, so that I can manage my study sessions with structured focus and break intervals.

#### Acceptance Criteria

1. THE Pomodoro_Timer SHALL display a countdown starting at 25:00 (minutes:seconds) for a Focus_Session and 05:00 for a Short_Break.
2. THE Course_Content_Page SHALL provide a "▶ Start" button, a "⏸ Pause" button, and a "↺ Reset" button for the Pomodoro_Timer.
3. WHEN the Student clicks "▶ Start", THE Pomodoro_Timer SHALL begin counting down from the current interval's starting time (25:00 for Focus_Session, 05:00 for Short_Break) and update the displayed time every second using a JavaFX `Timeline`.
4. WHEN the Student clicks "⏸ Pause", THE Pomodoro_Timer SHALL stop the countdown and retain the remaining time; THE "▶ Start" button SHALL resume from the paused time.
5. WHEN the Student clicks "↺ Reset", THE Pomodoro_Timer SHALL stop the countdown and reset the display to 25:00 (Focus_Session start).
6. WHEN a Focus_Session countdown reaches 00:00, THE Pomodoro_Timer SHALL increment the completed Pomodoro_Cycle count by 1, display a visual notification ("🍅 Focus session complete! Take a break."), and automatically switch to a Short_Break countdown of 05:00.
7. WHEN a Short_Break countdown reaches 00:00, THE Pomodoro_Timer SHALL display a notification ("☕ Break over! Ready for the next focus session.") and automatically switch back to a Focus_Session countdown of 25:00.
8. THE Course_Content_Page SHALL display the current Pomodoro_Cycle count as "🍅 Cycles: N" and update it in real time.
9. WHILE a Focus_Session is actively counting down (timer is running and not paused), THE System SHALL increment the Student's active study minutes by 1 every 60 seconds and trigger a progress update (see Requirement 5).
10. WHEN the Course_Content_Page is closed, THE Pomodoro_Timer SHALL stop all running `Timeline` instances to prevent background threads.

---

### Requirement 3: Course Content Area

**User Story:** As a Student, I want to see the course content on the learning page, so that I have the material I need to study.

#### Acceptance Criteria

1. THE Course_Content_Page SHALL display a styled content card with the heading "📘 Course Content" and the course description text below it.
2. WHERE the course description is null or empty, THE System SHALL display the placeholder text: "This is the content of the course. Your instructor will add detailed materials here."
3. THE content card SHALL use a white background, rounded corners (radius 12), a subtle drop shadow, and padding of 16px, consistent with the existing card style in `UserCourseController`.
4. THE content card SHALL display the course category, difficulty badge, and estimated duration below the description, reusing the badge styles from the existing `UserCourseController.buildCard` method.

---

### Requirement 4: YouTube Data API Integration

**User Story:** As a Student, I want to see recommended YouTube videos related to my course, so that I can supplement my learning with video content.

#### Acceptance Criteria

1. THE YouTube_Service SHALL query the YouTube Data API v3 `search` endpoint using a search query composed of the course's `courseName`, `category`, and up to 3 keywords extracted from the course description (first 3 non-stop-words of 4+ characters).
2. THE YouTube_Service SHALL request a maximum of 5 video results per query, filtering by `type=video`.
3. THE YouTube_Service SHALL store the YouTube Data API key in `API_Config` (`api.properties`) under the key `youtube.api.key`; THE System SHALL read this key at runtime and SHALL NOT hard-code it in any source file.
4. THE YouTube_Service SHALL cache results in memory (a `Map<String, List<VideoResult>>` keyed by the search query string) for the duration of the application session to reduce API calls; a cached result SHALL be returned if the same query is requested again.
5. THE Course_Content_Page SHALL display each VideoResult as a card containing: the video thumbnail image (loaded asynchronously), the video title (truncated to 60 characters if longer), and the channel name.
6. WHEN a Student clicks the "▶ Watch" button on a video card, THE System SHALL open the YouTube video URL (`https://www.youtube.com/watch?v={videoId}`) in the system default browser using `java.awt.Desktop.browse`.
7. IF the YouTube API call fails (network error, quota exceeded, invalid key), THEN THE System SHALL display a fallback message "⚠ Video recommendations unavailable." in the video section without crashing the page.
8. THE YouTube_Service SHALL be implemented as a dedicated class `services.api.YouTubeService` with a method `List<VideoResult> searchVideos(String courseName, String category, String description)`.

---

### Requirement 5: Student Progress Tracking

**User Story:** As a Student, I want my learning progress to be tracked and displayed, so that I can see how far I have come in each course.

#### Acceptance Criteria

1. THE System SHALL create a new database table `student_course_progress` with columns: `id` (PK, auto-increment), `student_id` (INT, FK → `user.id` ON DELETE CASCADE), `course_id` (INT, FK → `course.id` ON DELETE CASCADE), `progress_percentage` (INT, default 0, range 0–100), `total_minutes_studied` (INT, default 0), `pomodoro_cycles_completed` (INT, default 0), `last_activity_at` (DATETIME, nullable), `study_streak_days` (INT, default 0), `last_streak_date` (DATE, nullable), with a UNIQUE constraint on `(student_id, course_id)`.
2. THE System SHALL implement a `StudentProgressService` class in `services.studysession` with methods: `getOrCreate(studentId, courseId)`, `incrementMinutes(studentId, courseId, minutes)`, `incrementPomodoroCycles(studentId, courseId, count)`, `getProgress(studentId, courseId)`, and `getProgressForCourse(courseId)` (returns all students' progress for a given course).
3. WHEN a Focus_Session minute elapses (every 60 seconds of active timer), THE System SHALL call `StudentProgressService.incrementMinutes(studentId, courseId, 1)`, which SHALL increment `total_minutes_studied` by 1 and `progress_percentage` by 1 (capped at 100), and update `last_activity_at` to the current timestamp.
4. WHEN a Pomodoro_Cycle completes (Focus_Session reaches 00:00), THE System SHALL call `StudentProgressService.incrementPomodoroCycles(studentId, courseId, 1)`, which SHALL increment `pomodoro_cycles_completed` by 1.
5. THE `incrementMinutes` method SHALL update `study_streak_days` as follows: IF `last_streak_date` equals yesterday's date, THEN increment `study_streak_days` by 1 and set `last_streak_date` to today; IF `last_streak_date` equals today's date, THEN make no change to the streak; OTHERWISE reset `study_streak_days` to 1 and set `last_streak_date` to today.
6. THE Course_Content_Page SHALL display a progress panel showing: a visual `ProgressBar` (JavaFX control) bound to `progress_percentage / 100.0`, the current percentage as text ("📈 N% complete"), total minutes studied today (derived from the in-session timer), completed Pomodoro cycles for this session, and the last activity timestamp.
7. THE progress panel SHALL refresh its displayed values after each minute increment and after each Pomodoro cycle completion without requiring a page reload.
8. IF `progress_percentage` reaches 100, THEN THE System SHALL display a congratulatory banner "🎉 Course Complete!" on the Course_Content_Page and set the course status to `COMPLETED` in the `course` table for that student (via `CourseService`).

---

### Requirement 6: Wikipedia REST API Integration

**User Story:** As a Student, I want to see a contextual Wikipedia summary for my course topic, so that I can quickly understand the subject area.

#### Acceptance Criteria

1. THE Wikipedia_Service SHALL query the Wikipedia REST API endpoint `https://en.wikipedia.org/api/rest_v1/page/summary/{topic}` where `{topic}` is derived from the course name (spaces replaced with underscores, special characters URL-encoded).
2. THE Wikipedia_Service SHALL parse the JSON response to extract: `title`, `extract` (plain text summary), `thumbnail.source` (image URL, nullable), and `content_urls.desktop.page` (full article URL).
3. THE Wikipedia_Service SHALL cache results in memory (a `Map<String, WikiSummary>` keyed by topic string) for the duration of the application session.
4. THE Course_Content_Page SHALL display the WikiSummary in a styled card with: the article title as a bold heading, the extract text (truncated to 400 characters with "..." if longer), an optional thumbnail image (loaded asynchronously, hidden if null), and a "📖 Read More" hyperlink that opens the full Wikipedia article in the system default browser.
5. IF the Wikipedia API call fails or returns a 404 (topic not found), THEN THE System SHALL display the message "ℹ No Wikipedia summary available for this topic." without crashing the page.
6. THE Wikipedia_Service SHALL be implemented as a dedicated class `services.api.WikipediaService` with a method `WikiSummary fetchSummary(String courseName)`.
7. THE Wikipedia_Service SHALL NOT require an API key; it SHALL use the public Wikipedia REST API without authentication.

---

### Requirement 7: PDF Resource Management — Tutor Upload

**User Story:** As a Tutor, I want to attach PDF resources to my courses, so that students have downloadable reference materials.

#### Acceptance Criteria

1. THE System SHALL create a new database table `course_pdf_resource` with columns: `id` (PK, auto-increment), `course_id` (INT, FK → `course.id` ON DELETE CASCADE), `title` (VARCHAR 255, NOT NULL), `topic` (VARCHAR 255, nullable), `file_path` (VARCHAR 512, NOT NULL, stores absolute path on disk), `uploaded_at` (DATETIME, NOT NULL), `uploaded_by_id` (INT, FK → `user.id` ON DELETE SET NULL).
2. THE `CourseFormController` (existing) SHALL be extended with a "📎 Add PDF Resource" section that allows a Tutor or Admin to select one or more PDF files using a `FileChooser` filtered to `*.pdf` files.
3. WHEN a Tutor selects a PDF file, THE System SHALL copy the file to a local storage directory (`{user.home}/nova_resources/pdfs/{courseId}/`) and persist a `course_pdf_resource` record with the title (defaulting to the filename without extension), topic (optional text field), file path, upload timestamp, and uploader's userId.
4. THE `CourseFormController` SHALL display a list of already-uploaded PDFs for the course being edited, with a "🗑 Remove" button for each entry that deletes the record and the file from disk.
5. THE System SHALL implement a `PdfResourceService` class in `services.studysession` with methods: `addResource(courseId, title, topic, filePath, uploadedById)`, `removeResource(resourceId)`, `findByCourse(courseId)`, and `findByTitle(courseId, searchTerm)`.
6. IF a file with the same name already exists in the target directory, THEN THE System SHALL append a numeric suffix (e.g., `filename_1.pdf`) to avoid overwriting existing files.

---

### Requirement 8: PDF Resource Management — Student View

**User Story:** As a Student, I want to view and download PDF resources attached to a course, so that I can access reference materials while studying.

#### Acceptance Criteria

1. THE Course_Content_Page SHALL include a "📚 Resources" section that loads and displays all `course_pdf_resource` records for the current course.
2. THE System SHALL display each PDF resource as a styled file card containing: a PDF icon (📄), the resource title, the topic tag (if present), and two action buttons: "👁 View" and "⬇ Download".
3. WHEN a Student clicks "👁 View", THE System SHALL open the PDF file using the system default PDF viewer via `java.awt.Desktop.open(file)`.
4. WHEN a Student clicks "⬇ Download", THE System SHALL open a `FileChooser` (save dialog) pre-populated with the resource's filename, allowing the Student to choose a save location, then copy the file to the chosen location.
5. THE "📚 Resources" section SHALL include a search field that filters displayed PDF cards in real time by matching the search text against the resource title and topic fields (case-insensitive).
6. IF no PDF resources exist for the course, THE System SHALL display the message "No resources uploaded yet." in the resources section.
7. IF a PDF file referenced in the database no longer exists on disk, THE System SHALL display the resource card with a "⚠ File not found" warning label and disable the "View" and "Download" buttons for that entry.

---

### Requirement 9: Tutor Progress Monitoring

**User Story:** As a Tutor, I want to monitor the learning progress of students enrolled in my courses, so that I can identify who needs support and track overall course effectiveness.

#### Acceptance Criteria

1. THE Tutor Dashboard SHALL include a "👥 Student Progress" tab (added to the existing `TutorDashboard.fxml` `TabPane`) that loads a `TutorProgressMonitorView.fxml` with a corresponding `TutorProgressMonitorController.java`.
2. WHEN a Tutor opens the "Student Progress" tab, THE System SHALL load all courses owned by the logged-in Tutor and, for each course, load all accepted enrollments with their associated `student_course_progress` records via `StudentProgressService.getProgressForCourse(courseId)`.
3. THE Tutor_Progress_Monitor SHALL display student progress in a card-per-course layout; each course card SHALL expand to show a table or list of enrolled students with the following columns: Student Name, Progress % (with a visual `ProgressBar`), Total Time Studied (formatted as "Xh Ym"), Pomodoro Cycles, Study Streak (days), Last Activity Date, and Completion Status (badge: "✅ Complete" / "🔄 In Progress" / "⏳ Not Started").
4. THE System SHALL calculate "Estimated Completion Date" for each student as: `today + ((100 - progress_percentage) / average_daily_progress_rate)` where `average_daily_progress_rate` is `total_minutes_studied / days_since_first_activity` (minimum 1 minute/day to avoid division by zero); WHERE `progress_percentage` equals 100, THE System SHALL display "✅ Completed".
5. THE Tutor_Progress_Monitor SHALL display aggregate statistics per course at the top of each course card: total enrolled students, average progress percentage, number of students who have completed the course, and number of students with a study streak ≥ 3 days.
6. THE Tutor_Progress_Monitor SHALL provide a search/filter field that filters the student list by student name (case-insensitive, real-time).
7. IF no students are enrolled in a course, THE System SHALL display "No enrolled students yet." for that course card.
8. THE data displayed in the Tutor_Progress_Monitor SHALL be scoped strictly to courses where `created_by_id` equals the logged-in Tutor's `userId`; no data from other Tutors' or Admins' courses SHALL appear.

---

### Requirement 10: Course Details Page (Student View)

**User Story:** As a Student, I want a dedicated details page for each course, so that I can review my planned sessions, upcoming schedule, and progress analytics before starting to study.

#### Acceptance Criteria

1. WHEN a Student clicks the "🔍 Details" button on a course card, THE System SHALL open the Course_Details_Page in a new window (distinct from the Course_Content_Page), passing the selected Course object.
2. THE Course_Details_Page SHALL display: the course name, provider label, category, difficulty, estimated duration, course description, and the Student's current `progress_percentage` for that course.
3. THE Course_Details_Page SHALL display a "📅 Planned Sessions" section listing all `Planning` records for the course (via `PlanningService.findByCourse(courseId)`), grouped into "Upcoming" (status `SCHEDULED` with `scheduledDate` ≥ today) and "Completed" (status `COMPLETED`), each shown as a card with title, date, time, and duration.
4. THE Course_Details_Page SHALL display a "📊 Quick Analytics" section showing: total planned sessions, completed sessions count, missed sessions count, total planned study time (sum of `plannedDuration` for all sessions), and the Student's progress percentage with a visual `ProgressBar`.
5. THE Course_Details_Page SHALL display a "▶ Start Studying" button that, when clicked, closes the details page and opens the Course_Content_Page for the same course.
6. THE Course_Details_Page SHALL be implemented as a new FXML view `CourseDetailsPage.fxml` with a corresponding `CourseDetailsPageController.java` in the `controllers.studysession` package.
7. THE Course_Details_Page SHALL apply the CourseAccessGuard: WHEN the current user's role is `ROLE_STUDENT` and the course is not Admin-owned, THE System SHALL verify enrollment status; IF not `ACCEPTED`, THE System SHALL display an "Access Denied" alert and close the window.

---

### Requirement 11: Student Course Card — Two-Button Layout

**User Story:** As a Student, I want two distinct action buttons on each course card, so that I can either review course details or jump directly into studying.

#### Acceptance Criteria

1. THE `UserCourseController.buildCard` method SHALL be updated so that for courses where the Student has access (Admin-owned or ACCEPTED enrollment), the card footer displays two buttons: "🔍 Details" and "▶ Start".
2. WHEN a Student clicks "🔍 Details", THE System SHALL open the Course_Details_Page for that course.
3. WHEN a Student clicks "▶ Start", THE System SHALL open the Course_Content_Page for that course.
4. FOR courses where the Student has no access (PENDING, REJECTED, or no enrollment), THE System SHALL continue to display the existing enrollment workflow buttons ("Enroll Request", "⏳ Pending", "❌ Rejected") without the "Details" or "Start" buttons.
5. THE two-button layout SHALL maintain the existing card footer style: buttons side by side, consistent padding, border radius 8, cursor hand, font size 11px.

---

### Requirement 12: API Configuration and Security

**User Story:** As a developer, I want all API keys stored in a configuration file rather than source code, so that secrets are not committed to version control.

#### Acceptance Criteria

1. THE System SHALL store all external API keys and configuration values in `src/main/resources/config/api.properties` using the following keys: `youtube.api.key` (YouTube Data API v3 key), `wikipedia.base.url` (default: `https://en.wikipedia.org/api/rest_v1`).
2. THE System SHALL implement a utility class `utils.ApiConfig` with a static method `String get(String key)` that loads `api.properties` from the classpath on first call and caches the `Properties` object for subsequent calls.
3. THE `api.properties` file SHALL be listed in `.gitignore` to prevent accidental commit of API keys; a template file `api.properties.template` SHALL be provided with placeholder values.
4. IF a required API key is missing or blank in `api.properties`, THEN THE System SHALL log a warning `"[API_CONFIG] Key '{key}' is not configured."` and the corresponding service SHALL return an empty result rather than throwing an uncaught exception.
5. THE YouTube_Service and Wikipedia_Service SHALL obtain their configuration exclusively through `ApiConfig.get(key)` and SHALL NOT accept API keys as constructor parameters or hard-coded strings.

---

### Requirement 13: Database Schema Extensions

**User Story:** As a database administrator, I want the new tables to follow the existing schema conventions, so that the database remains consistent and maintainable.

#### Acceptance Criteria

1. THE System SHALL create the `student_course_progress` table with the schema defined in Requirement 5, Criterion 1, using `CREATE TABLE IF NOT EXISTS` to be idempotent.
2. THE System SHALL create the `course_pdf_resource` table with the schema defined in Requirement 7, Criterion 1, using `CREATE TABLE IF NOT EXISTS` to be idempotent.
3. BOTH new tables SHALL declare their foreign keys with `ON DELETE CASCADE` for `course_id` and `student_id`/`uploaded_by_id` as specified in their respective requirements.
4. THE System SHALL provide a SQL migration script `src/main/resources/db/migration_course_learning.sql` containing the `CREATE TABLE IF NOT EXISTS` statements for both new tables, executable against `nova_db` without affecting existing tables.
5. THE `student_course_progress` table SHALL enforce the UNIQUE constraint on `(student_id, course_id)` so that `StudentProgressService.getOrCreate` can use an `INSERT ... ON DUPLICATE KEY UPDATE` pattern for atomic upsert operations.

---

### Requirement 14: UI/UX Consistency

**User Story:** As a UI designer, I want all new views to match the existing platform theme and interaction patterns, so that the user experience is cohesive across the application.

#### Acceptance Criteria

1. THE Course_Content_Page and Course_Details_Page SHALL apply the existing `study.css` stylesheet (located at `src/main/resources/css/study.css`) to maintain visual consistency.
2. ALL new cards (video cards, PDF resource cards, progress cards) SHALL use the same base card style as `UserCourseController.buildCard`: white background, `border-radius: 12`, `border-color: #e2e8f0`, drop shadow `rgba(0,0,0,0.07)`.
3. THE Pomodoro_Timer display SHALL use a large, bold font (minimum 36px) for the countdown digits, with a circular or rounded container styled in the platform's primary color palette.
4. THE progress bar displayed on the Course_Content_Page SHALL use JavaFX's `ProgressBar` control styled with the existing green (`#22c55e`) for progress ≥ 80%, amber (`#f59e0b`) for 40–79%, and red (`#ef4444`) for < 40%.
5. ALL loading states (while API calls are in progress) SHALL display a `ProgressIndicator` (spinner) in place of the content area being loaded, replaced by the actual content once loading completes.
6. ALL new buttons SHALL follow the existing button style conventions: background color, text color, border radius 8, cursor hand, padding 5 12, font size 11px, matching the pattern in `UserCourseController`.
7. THE Course_Content_Page SHALL open maximized (using `stage.setMaximized(true)`) to provide a full-screen learning environment.
