# Design Document — Course Learning Experience

## Overview

This document describes the technical design for the **Course Learning Experience** feature on the NOVA JavaFX desktop platform. The feature adds an active learning environment for students, a Pomodoro productivity timer, YouTube and Wikipedia API integrations, student progress tracking, PDF resource management, and a tutor progress monitoring dashboard.

The implementation is entirely within the existing `studysession` module and follows the established patterns of the codebase: JavaFX 17 + FXML, Maven, MySQL (`nova_db`), `java.net.http.HttpClient` for HTTP, and manual JSON string parsing (no external JSON library is added — `org.json` will be added to `pom.xml` to simplify JSON parsing).

### Key Design Decisions

- **org.json added to pom.xml**: The existing `GeminiService` uses manual string parsing, which is fragile for nested JSON (Wikipedia, YouTube responses). Adding `org.json:json:20231013` is the minimal, well-known dependency that avoids introducing a full framework like Jackson.
- **Access guard reuse**: The `CourseAccessGuard` logic already in `CourseDetailController` is extracted into a shared static helper pattern and replicated in `CourseContentController` and `CourseDetailsPageController` to keep the guard close to each controller without introducing a new utility class.
- **In-memory caching**: YouTube and Wikipedia results are cached in `Map` fields on the service singletons for the application session lifetime. No TTL is needed given the desktop app's session model.
- **Progress persistence strategy**: `StudentProgressService.incrementMinutes` uses `INSERT ... ON DUPLICATE KEY UPDATE` for atomic upsert, leveraging the UNIQUE constraint on `(student_id, course_id)`.
- **File storage**: PDF files are stored under `{user.home}/nova_resources/pdfs/{courseId}/` on the local filesystem. This is consistent with a desktop app where the user's home directory is always writable.

---

## Architecture

The feature follows the existing MVC pattern of the application:

```
┌─────────────────────────────────────────────────────────────────┐
│                        JavaFX UI Layer                          │
│  CourseContentController   CourseDetailsPageController          │
│  TutorProgressMonitorController   UserCourseController (mod)    │
│  CourseFormController (mod)   TutorDashboardController (mod)    │
└──────────────────────┬──────────────────────────────────────────┘
                       │ calls
┌──────────────────────▼──────────────────────────────────────────┐
│                      Service Layer                              │
│  StudentProgressService   PdfResourceService                    │
│  YouTubeService   WikipediaService   ApiConfig                  │
│  (existing) CourseService   EnrollmentService   PlanningService │
└──────────────────────┬──────────────────────────────────────────┘
                       │ JDBC / HTTP
┌──────────────────────▼──────────────────────────────────────────┐
│              Data / External Layer                              │
│  MySQL nova_db (student_course_progress, course_pdf_resource)   │
│  YouTube Data API v3   Wikipedia REST API                       │
│  Local filesystem (PDF storage)                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Navigation Flow

```
UserCourseController (course card)
  ├── [🔍 Details] ──► CourseDetailsPageController
  │                         └── [▶ Start Studying] ──► CourseContentController
  └── [▶ Start]   ──► CourseContentController

TutorDashboardController
  └── [👥 Student Progress tab] ──► TutorProgressMonitorController
```

---

## Components and Interfaces

### New Controllers

#### `CourseContentController` (`controllers.studysession`)

```java
public class CourseContentController implements Initializable {
    // FXML fields: lblCourseTitle, lblTimerDisplay, lblCycleCount,
    //              progressBar, lblProgressPct, btnStart, btnPause, btnReset,
    //              vboxVideos, vboxWiki, vboxResources, lblStatus
    
    public void initData(Course course);
    private void applyAccessGuard(Course course);
    private void startTimer();
    private void pauseTimer();
    private void resetTimer();
    private void onFocusSessionComplete();
    private void onBreakComplete();
    private void incrementStudyMinute();
    private void loadYouTubeVideos();
    private void loadWikipediaSummary();
    private void loadPdfResources();
    private void refreshProgressPanel();
    private void showCompletionBanner();
}
```

#### `CourseDetailsPageController` (`controllers.studysession`)

```java
public class CourseDetailsPageController implements Initializable {
    // FXML fields: lblCourseName, lblCategory, lblDifficulty, lblDuration,
    //              lblProvider, txtDescription, progressBar, lblProgressPct,
    //              vboxUpcoming, vboxCompleted, lblTotalSessions,
    //              lblCompletedSessions, lblMissedSessions, lblTotalTime
    
    public void initData(Course course);
    private void applyAccessGuard(Course course);
    private void loadPlannings();
    private void loadStudentProgress();
    private void renderPlanningGroups(List<Planning> plannings);
    private void computeAnalytics(List<Planning> plannings);
    @FXML private void handleStartStudying();
}
```

#### `TutorProgressMonitorController` (`controllers.studysession`)

```java
public class TutorProgressMonitorController implements Initializable {
    // FXML fields: vboxCourseCards, txtSearch, lblStatus
    
    public void initialize(URL url, ResourceBundle rb);
    private void loadData();
    private void renderCourseCard(Course course, List<StudentProgress> progressList);
    private void renderStudentRow(StudentProgress sp);
    private String computeEstimatedCompletion(StudentProgress sp);
    @FXML private void handleSearch();
}
```

### New Services

#### `StudentProgressService` (`services.studysession`)

```java
public class StudentProgressService {
    public StudentProgress getOrCreate(int studentId, int courseId) throws SQLException;
    public void incrementMinutes(int studentId, int courseId, int minutes) throws SQLException;
    public void incrementPomodoroCycles(int studentId, int courseId, int count) throws SQLException;
    public StudentProgress getProgress(int studentId, int courseId) throws SQLException;
    public List<StudentProgress> getProgressForCourse(int courseId) throws SQLException;
}
```

`incrementMinutes` uses `INSERT INTO student_course_progress (...) VALUES (...) ON DUPLICATE KEY UPDATE ...` for atomic upsert. Streak logic is computed in Java before the SQL call.

#### `PdfResourceService` (`services.studysession`)

```java
public class PdfResourceService {
    public PdfResource addResource(int courseId, String title, String topic,
                                   String filePath, int uploadedById) throws SQLException, IOException;
    public void removeResource(int resourceId) throws SQLException;
    public List<PdfResource> findByCourse(int courseId) throws SQLException;
    public List<PdfResource> findByTitle(int courseId, String searchTerm) throws SQLException;
    
    // File management helpers
    private String resolveTargetPath(int courseId, String filename);
    private String deduplicateFilename(Path targetDir, String filename);
}
```

#### `YouTubeService` (`services.api`)

```java
public class YouTubeService {
    private final Map<String, List<VideoResult>> cache = new HashMap<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    public List<VideoResult> searchVideos(String courseName, String category,
                                          String description);
    private String buildSearchQuery(String courseName, String category, String description);
    private List<String> extractKeywords(String description, int maxCount);
    private List<VideoResult> callYouTubeApi(String query);
    private List<VideoResult> parseResponse(String json);
}
```

#### `WikipediaService` (`services.api`)

```java
public class WikipediaService {
    private final Map<String, WikiSummary> cache = new HashMap<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    public WikiSummary fetchSummary(String courseName);
    private String buildTopicUrl(String courseName);
    private WikiSummary parseResponse(String json);
}
```

#### `ApiConfig` (`utils`)

```java
public class ApiConfig {
    private static Properties props = null;
    
    public static String get(String key);
    private static void loadProperties();
}
```

Loads `config/api.properties` from the classpath on first call. Returns `null` and logs a warning if the key is missing or blank.

### New Models

#### `StudentProgress` (`models.studysession`)

```java
public class StudentProgress {
    private int id;
    private int studentId;
    private String studentName;   // populated via JOIN
    private int courseId;
    private int progressPercentage;   // 0–100
    private int totalMinutesStudied;
    private int pomodoroCyclesCompleted;
    private LocalDateTime lastActivityAt;
    private int studyStreakDays;
    private LocalDate lastStreakDate;
    private LocalDateTime firstActivityAt;  // for estimated completion calc
    // getters/setters
}
```

#### `PdfResource` (`models.studysession`)

```java
public class PdfResource {
    private int id;
    private int courseId;
    private String title;
    private String topic;
    private String filePath;
    private LocalDateTime uploadedAt;
    private int uploadedById;
    // getters/setters
}
```

#### `VideoResult` (`services.api` — DTO)

```java
public class VideoResult {
    private String videoId;
    private String title;
    private String channelName;
    private String thumbnailUrl;
    // constructor, getters
}
```

#### `WikiSummary` (`services.api` — DTO)

```java
public class WikiSummary {
    private String title;
    private String extract;
    private String thumbnailUrl;   // nullable
    private String pageUrl;
    // constructor, getters
}
```

---

## Data Models

### Database Schema

#### `student_course_progress`

```sql
CREATE TABLE IF NOT EXISTS student_course_progress (
    id                      INT AUTO_INCREMENT PRIMARY KEY,
    student_id              INT NOT NULL,
    course_id               INT NOT NULL,
    progress_percentage     INT NOT NULL DEFAULT 0,
    total_minutes_studied   INT NOT NULL DEFAULT 0,
    pomodoro_cycles_completed INT NOT NULL DEFAULT 0,
    last_activity_at        DATETIME NULL,
    study_streak_days       INT NOT NULL DEFAULT 0,
    last_streak_date        DATE NULL,
    first_activity_at       DATETIME NULL,
    UNIQUE KEY uq_student_course (student_id, course_id),
    CONSTRAINT fk_scp_student FOREIGN KEY (student_id)
        REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_scp_course  FOREIGN KEY (course_id)
        REFERENCES course(id) ON DELETE CASCADE
);
```

#### `course_pdf_resource`

```sql
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
);
```

### Upsert Pattern for Progress

`StudentProgressService.incrementMinutes` uses:

```sql
INSERT INTO student_course_progress
    (student_id, course_id, progress_percentage, total_minutes_studied,
     last_activity_at, study_streak_days, last_streak_date, first_activity_at)
VALUES (?, ?, 1, 1, NOW(), 1, CURDATE(), NOW())
ON DUPLICATE KEY UPDATE
    progress_percentage   = LEAST(progress_percentage + ?, 100),
    total_minutes_studied = total_minutes_studied + ?,
    last_activity_at      = NOW(),
    study_streak_days     = ?,   -- computed in Java
    last_streak_date      = ?,   -- computed in Java
    first_activity_at     = COALESCE(first_activity_at, NOW())
```

### Pomodoro Timer State Machine

```
IDLE ──[Start]──► FOCUS_RUNNING
FOCUS_RUNNING ──[Pause]──► FOCUS_PAUSED
FOCUS_PAUSED  ──[Start]──► FOCUS_RUNNING
FOCUS_RUNNING ──[Reset]──► IDLE (25:00)
FOCUS_RUNNING ──[00:00]──► BREAK_RUNNING (cycle++)
BREAK_RUNNING ──[00:00]──► FOCUS_RUNNING (new cycle)
BREAK_RUNNING ──[Pause]──► BREAK_PAUSED
BREAK_PAUSED  ──[Start]──► BREAK_RUNNING
```

The timer is implemented with a JavaFX `Timeline` that fires every second. On page close, `timeline.stop()` is called in a `setOnCloseRequest` handler.

### Progress Color Mapping

| Range | Color | Hex |
|-------|-------|-----|
| ≥ 80% | Green | `#22c55e` |
| 40–79% | Amber | `#f59e0b` |
| < 40% | Red | `#ef4444` |

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Access Guard Correctness

*For any* course and student, access to the Course_Content_Page or Course_Details_Page is granted if and only if the course is admin-owned (`creatorRole == "ROLE_ADMIN"`) OR the student's enrollment status is `"ACCEPTED"`. Any other enrollment status (PENDING, REJECTED, null) for a non-admin course must result in access denial.

**Validates: Requirements 1.4, 10.7**

---

### Property 2: Pomodoro Cycle Count Monotonically Increases

*For any* initial Pomodoro cycle count N, completing a Focus_Session (timer reaching 00:00 while in focus mode) results in a cycle count of exactly N+1 and the timer mode switching to SHORT_BREAK.

**Validates: Requirements 2.6**

---

### Property 3: Focus-Break-Focus Round Trip

*For any* timer state, completing a Short_Break (break timer reaching 00:00) always returns the timer to Focus_Session mode with the display reset to 25:00.

**Validates: Requirements 2.7**

---

### Property 4: Progress Increment Correctness

*For any* student/course record with current `progress_percentage` P and `total_minutes_studied` M, calling `incrementMinutes(studentId, courseId, 1)` results in `total_minutes_studied = M + 1` and `progress_percentage = min(P + 1, 100)`. Progress is never decremented and never exceeds 100.

**Validates: Requirements 5.3**

---

### Property 5: Streak Calculation Correctness

*For any* combination of `last_streak_date` and `study_streak_days`, calling `incrementMinutes` applies exactly one of three rules:
- If `last_streak_date` is yesterday → `study_streak_days` increases by 1, `last_streak_date` = today
- If `last_streak_date` is today → no change to streak fields
- Otherwise → `study_streak_days` = 1, `last_streak_date` = today

**Validates: Requirements 5.5**

---

### Property 6: Progress Upsert Idempotency

*For any* `(studentId, courseId)` pair, calling `StudentProgressService.getOrCreate` any number of times returns a record for that pair and never creates duplicate rows. The UNIQUE constraint on `(student_id, course_id)` ensures at most one record per pair.

**Validates: Requirements 5.2, 13.5**

---

### Property 7: YouTube Query Contains Course Identifiers

*For any* course name and category, the search query string built by `YouTubeService.buildSearchQuery` contains both the course name and the category as substrings.

**Validates: Requirements 4.1**

---

### Property 8: API Service Caching

*For any* search query Q, calling `YouTubeService.searchVideos` twice with the same Q returns identical results, and the second call does not invoke the HTTP client (cache hit). The same property holds for `WikipediaService.fetchSummary` with any topic string.

**Validates: Requirements 4.4, 6.3**

---

### Property 9: Wikipedia URL Construction

*For any* course name string, the Wikipedia topic URL constructed by `WikipediaService.buildTopicUrl` replaces all space characters with underscores and URL-encodes any remaining special characters.

**Validates: Requirements 6.1**

---

### Property 10: Missing API Key Returns Empty Result

*For any* service call (`YouTubeService.searchVideos`, `WikipediaService.fetchSummary`) where the corresponding API key is missing or blank in `api.properties`, the method returns an empty result (empty list or null `WikiSummary`) without throwing an uncaught exception.

**Validates: Requirements 12.4**

---

### Property 11: PDF Filename Deduplication

*For any* filename that already exists in the target PDF storage directory, `PdfResourceService.deduplicateFilename` returns a filename that does not already exist in that directory and does not equal the original filename.

**Validates: Requirements 7.6**

---

### Property 12: PDF Resource Filtering

*For any* search term T and list of `PdfResource` objects, the filtered result contains exactly those resources where `title` or `topic` contains T as a case-insensitive substring, and no others.

**Validates: Requirements 8.5**

---

### Property 13: Course Card Button Layout by Access Status

*For any* course where `isAdminOwned = true` OR `enrollmentStatus = "ACCEPTED"`, the card footer rendered by `UserCourseController.buildCard` contains both a "Details" button and a "Start" button. For any course where `enrollmentStatus` is PENDING, REJECTED, or null (and not admin-owned), the card footer does NOT contain "Details" or "Start" buttons.

**Validates: Requirements 11.1, 11.4**

---

### Property 14: Planning Session Grouping

*For any* list of `Planning` records for a course, the "Upcoming" group contains exactly those records with `status = "SCHEDULED"` and `scheduledDate >= today`, and the "Completed" group contains exactly those records with `status = "COMPLETED"`. No record appears in both groups.

**Validates: Requirements 10.3**

---

### Property 15: Quick Analytics Aggregation

*For any* list of `Planning` records, the computed analytics values satisfy:
- `totalSessions = plannings.size()`
- `completedCount = count(p where p.status == "COMPLETED")`
- `missedCount = count(p where p.status == "MISSED")`
- `totalPlannedTime = sum(p.plannedDuration for all p)`

**Validates: Requirements 10.4**

---

### Property 16: Tutor Progress Data Scoping

*For any* tutor with `userId = T`, all courses loaded by `TutorProgressMonitorController` have `created_by_id = T`. No progress data from other tutors' or admins' courses appears in the monitor.

**Validates: Requirements 9.8**

---

### Property 17: Estimated Completion Date Formula

*For any* `StudentProgress` record where `progressPercentage < 100`, `totalMinutesStudied > 0`, and `daysSinceFirstActivity >= 1`, the estimated completion date equals `today + ceil((100 - progressPercentage) / max(totalMinutesStudied / daysSinceFirstActivity, 1))` days. Division by zero is prevented by using `max(..., 1)`.

**Validates: Requirements 9.4**

---

### Property 18: Progress Color Mapping

*For any* progress percentage P in [0, 100], the color applied to the progress bar is:
- `#22c55e` (green) when P ≥ 80
- `#f59e0b` (amber) when 40 ≤ P < 80
- `#ef4444` (red) when P < 40

Every valid percentage maps to exactly one color.

**Validates: Requirements 14.4**

---

## Error Handling

### API Failures

Both `YouTubeService` and `WikipediaService` wrap all HTTP calls in try-catch blocks. On any exception (network error, timeout, non-200 status, JSON parse error):
- The method returns an empty list / null
- A warning is logged to `System.err`
- The controller displays a fallback message in the UI section
- The rest of the page continues to load normally

### Database Failures

`StudentProgressService` and `PdfResourceService` propagate `SQLException` to the controller. Controllers catch these and display an error label without crashing the page.

### File Not Found (PDF)

`PdfResourceService.findByCourse` returns all records regardless of file existence. `CourseContentController` checks `new File(resource.getFilePath()).exists()` for each resource and renders a "⚠ File not found" warning with disabled buttons when the file is missing.

### Access Denied

When `CourseAccessGuard` denies access, an `Alert.AlertType.ERROR` dialog is shown and the window is closed immediately after the user dismisses the alert. No partial page content is rendered.

### Missing API Key

`ApiConfig.get(key)` returns `null` and logs `[API_CONFIG] Key '{key}' is not configured.` The calling service checks for null/blank and returns an empty result.

---

## Testing Strategy

### Unit Tests (JUnit 5)

Unit tests cover specific examples, edge cases, and pure logic:

- `ApiConfigTest`: verify key loading, missing key returns null, caching behavior
- `YouTubeServiceTest`: query composition, keyword extraction, JSON parsing with mock responses, cache hit behavior
- `WikipediaServiceTest`: URL construction (spaces → underscores), JSON parsing, cache hit, 404 handling
- `StudentProgressServiceTest`: streak calculation logic (yesterday/today/other), progress cap at 100, upsert idempotency
- `PdfResourceServiceTest`: filename deduplication logic, findByTitle case-insensitive filtering
- `CourseDetailsPageControllerTest`: planning grouping (upcoming vs completed), analytics aggregation

### Property-Based Tests (JUnit 5 + jqwik)

Property tests use [jqwik](https://jqwik.net/) (a JUnit 5 property-based testing library for Java). Each test runs a minimum of 100 iterations.

Tag format: `// Feature: course-learning-experience, Property N: <property_text>`

**Property 1** — Access Guard Correctness
```java
// Feature: course-learning-experience, Property 1: access granted iff admin-owned or ACCEPTED
@Property void accessGuardCorrectness(@ForAll boolean isAdminOwned,
    @ForAll @From("enrollmentStatuses") String status) { ... }
```

**Property 2** — Pomodoro Cycle Count Monotonically Increases
```java
// Feature: course-learning-experience, Property 2: completing focus session increments cycle count by 1
@Property void pomodoroCompletionIncrementsCycle(@ForAll @IntRange(min=0, max=100) int initialCount) { ... }
```

**Property 4** — Progress Increment Correctness
```java
// Feature: course-learning-experience, Property 4: incrementMinutes caps at 100 and always increments minutes
@Property void progressIncrementCorrectness(
    @ForAll @IntRange(min=0, max=100) int currentProgress,
    @ForAll @IntRange(min=0) int currentMinutes) { ... }
```

**Property 5** — Streak Calculation Correctness
```java
// Feature: course-learning-experience, Property 5: streak update follows three-case rule
@Property void streakCalculationCorrectness(
    @ForAll @From("localDates") LocalDate lastStreakDate,
    @ForAll @IntRange(min=0, max=365) int currentStreak) { ... }
```

**Property 7** — YouTube Query Contains Course Identifiers
```java
// Feature: course-learning-experience, Property 7: search query contains course name and category
@Property void youtubeQueryContainsIdentifiers(
    @ForAll @StringLength(min=1, max=100) String courseName,
    @ForAll @StringLength(min=1, max=50) String category) { ... }
```

**Property 8** — API Service Caching
```java
// Feature: course-learning-experience, Property 8: same query returns cached result without HTTP call
@Property void youtubeServiceCachesResults(@ForAll @StringLength(min=1) String query) { ... }
@Property void wikipediaServiceCachesResults(@ForAll @StringLength(min=1) String topic) { ... }
```

**Property 9** — Wikipedia URL Construction
```java
// Feature: course-learning-experience, Property 9: spaces replaced with underscores in topic URL
@Property void wikipediaUrlSpacesReplaced(@ForAll String courseName) { ... }
```

**Property 11** — PDF Filename Deduplication
```java
// Feature: course-learning-experience, Property 11: deduplicated filename is unique and differs from original
@Property void pdfFilenameDeduplication(@ForAll @StringLength(min=1) String filename) { ... }
```

**Property 12** — PDF Resource Filtering
```java
// Feature: course-learning-experience, Property 12: filter returns exactly matching resources
@Property void pdfResourceFilteringCorrectness(
    @ForAll List<PdfResource> resources,
    @ForAll String searchTerm) { ... }
```

**Property 13** — Course Card Button Layout
```java
// Feature: course-learning-experience, Property 13: card shows Details+Start iff access granted
@Property void courseCardButtonLayout(
    @ForAll boolean isAdminOwned,
    @ForAll @From("enrollmentStatuses") String status) { ... }
```

**Property 14** — Planning Session Grouping
```java
// Feature: course-learning-experience, Property 14: planning sessions grouped correctly
@Property void planningSessionGrouping(@ForAll List<Planning> plannings) { ... }
```

**Property 15** — Quick Analytics Aggregation
```java
// Feature: course-learning-experience, Property 15: analytics aggregation is correct
@Property void quickAnalyticsAggregation(@ForAll List<Planning> plannings) { ... }
```

**Property 17** — Estimated Completion Date Formula
```java
// Feature: course-learning-experience, Property 17: estimated completion date formula is correct
@Property void estimatedCompletionDateFormula(
    @ForAll @IntRange(min=0, max=99) int progress,
    @ForAll @IntRange(min=1) int totalMinutes,
    @ForAll @IntRange(min=1) int daysSinceFirst) { ... }
```

**Property 18** — Progress Color Mapping
```java
// Feature: course-learning-experience, Property 18: progress percentage maps to exactly one color
@Property void progressColorMapping(@ForAll @IntRange(min=0, max=100) int progress) { ... }
```

### Integration Tests

- Verify `student_course_progress` and `course_pdf_resource` tables are created by the migration script
- Verify `StudentProgressService.getOrCreate` is idempotent against a real (test) database
- Verify `PdfResourceService.addResource` writes the file to disk and persists the DB record

### Manual / Smoke Tests

- Verify `CourseContentView.fxml`, `CourseDetailsPage.fxml`, `TutorProgressMonitorView.fxml` load without error
- Verify `api.properties` is listed in `.gitignore`
- Verify `api.properties.template` exists with placeholder values
- Verify the Pomodoro timer stops when the window is closed (no background thread leak)
- Verify the Course_Content_Page opens maximized
