# Tasks

## Implementation Plan

- [x] 1. Database Schema & Migration
  - [x] 1.1 Create `src/main/resources/db/migration_course_learning.sql` with `CREATE TABLE IF NOT EXISTS student_course_progress` (all columns: id, student_id, course_id, progress_percentage, total_minutes_studied, pomodoro_cycles_completed, last_activity_at, study_streak_days, last_streak_date, first_activity_at, UNIQUE KEY on student_id+course_id, FK constraints with ON DELETE CASCADE)
  - [x] 1.2 Add `CREATE TABLE IF NOT EXISTS course_pdf_resource` to the same migration script (all columns: id, course_id, title, topic, file_path, uploaded_at, uploaded_by_id, FK constraints)
  - [x] 1.3 Run the migration script against `nova_db` to create both tables

- [x] 2. pom.xml — Add org.json Dependency
  - [x] 2.1 Add `org.json:json:20231013` dependency to `pom.xml` for JSON parsing in YouTubeService and WikipediaService

- [x] 3. API Configuration
  - [x] 3.1 Create `src/main/resources/config/api.properties` with keys: `youtube.api.key=YOUR_KEY_HERE` and `wikipedia.base.url=https://en.wikipedia.org/api/rest_v1`
  - [x] 3.2 Create `src/main/resources/config/api.properties.template` with the same keys and placeholder values
  - [x] 3.3 Add `src/main/resources/config/api.properties` to `.gitignore`
  - [x] 3.4 Implement `utils.ApiConfig` with static `get(String key)` method that loads `config/api.properties` from classpath on first call, caches the `Properties` object, logs `[API_CONFIG] Key '{key}' is not configured.` for missing/blank keys, and returns null for missing keys

- [x] 4. New Models
  - [x] 4.1 Create `models.studysession.StudentProgress` with fields: id, studentId, studentName, courseId, progressPercentage, totalMinutesStudied, pomodoroCyclesCompleted, lastActivityAt, studyStreakDays, lastStreakDate, firstActivityAt — with full getters/setters
  - [x] 4.2 Create `models.studysession.PdfResource` with fields: id, courseId, title, topic, filePath, uploadedAt, uploadedById — with full getters/setters
  - [x] 4.3 Create `services.api.VideoResult` DTO with fields: videoId, title, channelName, thumbnailUrl — with constructor and getters
  - [x] 4.4 Create `services.api.WikiSummary` DTO with fields: title, extract, thumbnailUrl (nullable), pageUrl — with constructor and getters

- [x] 5. StudentProgressService
  - [x] 5.1 Create `services.studysession.StudentProgressService` with `getOrCreate(studentId, courseId)` using `INSERT ... ON DUPLICATE KEY UPDATE` pattern
  - [x] 5.2 Implement `incrementMinutes(studentId, courseId, int minutes)`: increments total_minutes_studied by minutes, increments progress_percentage by minutes (capped at 100), updates last_activity_at, sets first_activity_at via COALESCE, and applies streak logic (yesterday→increment, today→no change, other→reset to 1)
  - [x] 5.3 Implement `incrementPomodoroCycles(studentId, courseId, int count)`: increments pomodoro_cycles_completed by count
  - [x] 5.4 Implement `getProgress(studentId, courseId)`: returns the StudentProgress record or null
  - [x] 5.5 Implement `getProgressForCourse(courseId)`: returns all StudentProgress records for a course, joining with user table to populate studentName

- [x] 6. PdfResourceService
  - [x] 6.1 Create `services.studysession.PdfResourceService` with `addResource(courseId, title, topic, filePath, uploadedById)`: copies the source file to `{user.home}/nova_resources/pdfs/{courseId}/`, deduplicates filename if needed (append `_1`, `_2`, etc.), persists the DB record, returns the created PdfResource
  - [x] 6.2 Implement `removeResource(resourceId)`: deletes the DB record and the file from disk (if it exists)
  - [x] 6.3 Implement `findByCourse(courseId)`: returns all PdfResource records for the course ordered by uploaded_at DESC
  - [x] 6.4 Implement `findByTitle(courseId, searchTerm)`: returns PdfResource records where title or topic contains searchTerm (case-insensitive, using SQL LIKE)

- [x] 7. YouTubeService
  - [x] 7.1 Create `services.api.YouTubeService` with in-memory `Map<String, List<VideoResult>>` cache
  - [x] 7.2 Implement `buildSearchQuery(courseName, category, description)`: concatenates courseName + " " + category + " " + up to 3 keywords extracted from description (first 3 non-stop-words of 4+ characters)
  - [x] 7.3 Implement `searchVideos(courseName, category, description)`: checks cache first; if miss, calls YouTube Data API v3 search endpoint with `maxResults=5&type=video`, reads API key from `ApiConfig.get("youtube.api.key")`, parses JSON response using org.json to extract videoId, title, channelName, thumbnailUrl; stores result in cache; returns empty list on any error
  - [x] 7.4 Implement JSON parsing for YouTube API response: extract `items[].id.videoId`, `items[].snippet.title`, `items[].snippet.channelTitle`, `items[].snippet.thumbnails.default.url`

- [x] 8. WikipediaService
  - [x] 8.1 Create `services.api.WikipediaService` with in-memory `Map<String, WikiSummary>` cache
  - [x] 8.2 Implement `buildTopicUrl(courseName)`: replaces spaces with underscores, URL-encodes special characters, appends to `ApiConfig.get("wikipedia.base.url") + "/page/summary/"`
  - [x] 8.3 Implement `fetchSummary(courseName)`: checks cache first; if miss, calls Wikipedia REST API, parses JSON response using org.json to extract title, extract, thumbnail.source (nullable), content_urls.desktop.page; stores result in cache; returns null on 404 or any error

- [x] 9. CourseContentView.fxml
  - [x] 9.1 Create `src/main/resources/views/studysession/CourseContentView.fxml` with `BorderPane` root applying `study.css`; top bar with course title heading; center `SplitPane` with left panel (Pomodoro timer display label ≥36px bold, cycle count label, Start/Pause/Reset buttons, ProgressBar, progress percentage label, last activity label) and right panel (`ScrollPane` containing: content card VBox, YouTube videos section VBox, Wikipedia summary card VBox, PDF resources section VBox with search field)

- [x] 10. CourseDetailsPage.fxml
  - [x] 10.1 Create `src/main/resources/views/studysession/CourseDetailsPage.fxml` with `BorderPane` root applying `study.css`; top bar with course name; center `ScrollPane` containing: course info card (name, provider, category, difficulty, duration, description, ProgressBar + percentage), planned sessions section (two sub-sections: Upcoming VBox, Completed VBox), quick analytics section (total sessions, completed, missed, total time labels), "▶ Start Studying" button at bottom

- [x] 11. TutorProgressMonitorView.fxml
  - [x] 11.1 Create `src/main/resources/views/studysession/TutorProgressMonitorView.fxml` with `BorderPane` root applying `study.css`; top bar with title and search TextField; center `ScrollPane` containing a `VBox` (`vboxCourseCards`) where each course card is dynamically added

- [x] 12. CourseContentController
  - [x] 12.1 Create `controllers.studysession.CourseContentController` implementing `Initializable`; wire all FXML fields
  - [x] 12.2 Implement `initData(Course course)`: apply CourseAccessGuard (check ROLE_STUDENT + non-admin course → verify ACCEPTED enrollment via EnrollmentService, show alert and close on denial); set window title and heading; load progress from StudentProgressService; start async loading of YouTube videos, Wikipedia summary, and PDF resources; initialize timer display to 25:00
  - [x] 12.3 Implement Pomodoro timer using JavaFX `Timeline` firing every second: decrement remaining seconds, update display label (MM:SS format), handle focus session completion (increment cycle count, call StudentProgressService.incrementPomodoroCycles, show notification, switch to break mode), handle break completion (show notification, switch to focus mode)
  - [x] 12.4 Implement `incrementStudyMinute()`: called every 60 seconds of active focus timer; calls `StudentProgressService.incrementMinutes(userId, courseId, 1)`; refreshes progress panel; checks if progress = 100 and shows completion banner
  - [x] 12.5 Implement `loadYouTubeVideos()`: show ProgressIndicator; call YouTubeService on background thread (Task); on success render video cards (thumbnail ImageView loaded async, title Label truncated to 60 chars, channel Label, "▶ Watch" Button opening browser); on failure show fallback label
  - [x] 12.6 Implement `loadWikipediaSummary()`: show ProgressIndicator; call WikipediaService on background thread; on success render wiki card (title bold, extract truncated to 400 chars, optional thumbnail, "📖 Read More" Hyperlink); on failure show fallback label
  - [x] 12.7 Implement `loadPdfResources()`: call PdfResourceService.findByCourse; render each resource as a card (📄 icon, title, topic tag, "👁 View" button, "⬇ Download" button); check file existence and disable buttons + show warning if file missing; wire search field to filter cards in real time; show "No resources uploaded yet." if empty
  - [x] 12.8 Implement `refreshProgressPanel()`: update ProgressBar value, percentage label, last activity label; apply color styling based on percentage (≥80% green, 40-79% amber, <40% red)
  - [x] 12.9 Register `stage.setOnCloseRequest` handler to call `timeline.stop()` on window close

- [x] 13. CourseDetailsPageController (implemented in existing `CourseDetailController` + `CourseDetail.fxml`)
  - [x] 13.1 `CourseDetailController` already implements `Initializable` and has all FXML fields wired (lblCourseName, lblCategory, lblDifficulty, lblStatus, lblDuration, lblProgress, lblPublished, lblPlanCount, lblProvider, txtDescription, progressBar, vboxUpcoming, vboxCompleted, lblTotalSessions, lblCompletedSessions, lblMissedSessions, lblTotalTime)
  - [x] 13.2 `initData(Course course)` already applies CourseAccessGuard, populates course info fields, loads plannings via PlanningService.findByCourse, renders planning groups and analytics — add `StudentProgressService.getProgress` call to refresh the progress bar from the live DB record instead of `course.getProgress()`
  - [x] 13.3 `renderPlanningGroups` already separates into Upcoming (SCHEDULED + scheduledDate >= today) and Completed, renders cards with title/date/time/duration, shows empty state
  - [x] 13.4 `computeAnalytics` already computes totalSessions, completedCount, missedCount, totalPlannedTime and updates labels
  - [x] 13.5 Patch `initData` to load live `StudentProgress` via `StudentProgressService.getProgress(userId, courseId)` and use `sp.getProgressPercentage()` for the progress bar and `lblProgress`, falling back to `course.getProgress()` if no record exists yet

- [x] 14. TutorProgressMonitorController
  - [x] 14.1 Create `controllers.studysession.TutorProgressMonitorController` implementing `Initializable`; wire all FXML fields
  - [x] 14.2 Implement `initialize`: call `loadData()` on Platform.runLater
  - [x] 14.3 Implement `loadData()`: get tutorId from UserSession; call CourseService.findByCreator(tutorId); for each course call StudentProgressService.getProgressForCourse(courseId); render course cards
  - [x] 14.4 Implement `renderCourseCard(Course course, List<StudentProgress> progressList)`: create a card VBox with course name header; aggregate stats row (total enrolled, avg progress, completed count, streak≥3 count); student table/list with columns: Student Name, ProgressBar + %, Total Time (Xh Ym), Pomodoro Cycles, Study Streak, Last Activity, Completion Status badge; show "No enrolled students yet." if list is empty
  - [x] 14.5 Implement `computeEstimatedCompletion(StudentProgress sp)`: if progress = 100 return "✅ Completed"; else compute today + ceil((100 - progress) / max(totalMinutes / daysSinceFirst, 1)); return formatted date string
  - [x] 14.6 Implement real-time search: wire txtSearch to filter student rows by name (case-insensitive) across all course cards

- [x] 15. Modify UserCourseController
  - [x] 15.1 Update `buildCard(Course course)` footer logic: for admin-owned courses and ACCEPTED enrollments, replace the single "▶ Start Course" button with two buttons: "🔍 Details" (calls `openCourseDetailsPage(course)`) and "▶ Start" (calls `openCourseContentPage(course)`)
  - [x] 15.2 Implement `openCourseDetailsPage(Course course)`: load `CourseDetailsPage.fxml`, call `ctrl.initData(course)`, open in new Stage
  - [x] 15.3 Implement `openCourseContentPage(Course course)`: load `CourseContentView.fxml`, call `ctrl.initData(course)`, open in new maximized Stage
  - [x] 15.4 Remove or repurpose the existing `startCourse(Course course)` method (it currently calls `openCourseDetail` which opens the old CourseDetail.fxml — update it to call `openCourseContentPage`)

- [x] 16. Modify CourseFormController — PDF Upload Section
  - [x] 16.1 Add FXML elements to `CourseForm.fxml`: a "📎 PDF Resources" section with a "➕ Add PDF" Button, a ListView or VBox for displaying existing PDFs, and a Label for status messages
  - [x] 16.2 In `CourseFormController`, add `PdfResourceService` field; in `initData` when editing an existing course, load and display existing PDFs via `PdfResourceService.findByCourse(courseId)`
  - [x] 16.3 Implement PDF add handler: open `FileChooser` filtered to `*.pdf`; for each selected file call `PdfResourceService.addResource`; refresh the PDF list display
  - [x] 16.4 Implement PDF remove handler: for each PDF card's "🗑 Remove" button, call `PdfResourceService.removeResource(resourceId)`; refresh the PDF list display

- [x] 17. Modify TutorDashboardController — Add Student Progress Tab
  - [x] 17.1 Add a new `Tab` with text "👥 Student Progress" to `TutorDashboard.fxml` TabPane
  - [x] 17.2 In `TutorDashboardController.initialize`, add `loadTabContent(tabStudentProgress, "/views/studysession/TutorProgressMonitorView.fxml")` call alongside the existing tab loads

- [ ] 18. Property-Based Tests
  - [ ] 18.1 Add `net.jqwik:jqwik:1.8.1` test dependency to `pom.xml`
  - [ ] 18.2 Write property test for Property 1 (Access Guard Correctness): generate random isAdminOwned boolean and enrollment status strings; verify access logic
  - [ ] 18.3 Write property test for Property 4 (Progress Increment Correctness): generate random currentProgress (0-100) and currentMinutes; verify incrementMinutes result
  - [ ] 18.4 Write property test for Property 5 (Streak Calculation Correctness): generate random lastStreakDate (yesterday/today/other) and currentStreak; verify streak update rules
  - [ ] 18.5 Write property test for Property 7 (YouTube Query Contains Course Identifiers): generate random courseName and category strings; verify query contains both
  - [ ] 18.6 Write property test for Property 9 (Wikipedia URL Construction): generate random course name strings with spaces; verify spaces replaced with underscores in URL
  - [ ] 18.7 Write property test for Property 11 (PDF Filename Deduplication): generate random filenames; verify deduplicated name is unique and differs from original when original exists
  - [ ] 18.8 Write property test for Property 12 (PDF Resource Filtering): generate random lists of PdfResource and search terms; verify filter returns exactly matching resources
  - [ ] 18.9 Write property test for Property 13 (Course Card Button Layout): generate random access states; verify correct buttons are shown
  - [ ] 18.10 Write property test for Property 14 (Planning Session Grouping): generate random lists of Planning records; verify grouping into Upcoming and Completed is correct
  - [ ] 18.11 Write property test for Property 15 (Quick Analytics Aggregation): generate random lists of Planning records; verify all four aggregate values are correct
  - [ ] 18.12 Write property test for Property 17 (Estimated Completion Date Formula): generate random progress, totalMinutes, daysSinceFirst; verify formula result
  - [ ] 18.13 Write property test for Property 18 (Progress Color Mapping): generate random progress percentages 0-100; verify exactly one color is returned per value
