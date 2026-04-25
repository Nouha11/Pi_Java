# Requirements Document

## Introduction

This feature adds an **Advanced Analytics Dashboard** and an **Interactive Calendar Planner** to the NOVA JavaFX desktop LMS platform (Java 17, Maven, MySQL `nova_db`). The platform already has three roles — Student (`ROLE_STUDENT`), Tutor (`ROLE_TUTOR`), and Admin (`ROLE_ADMIN`) — with working authentication, enrollment, course management, study sessions, and planning systems.

The existing models `StudySession`, `Planning`, `Course`, `User`, `EnrollmentRequest`, and `StudentProgress` are reused without schema modification. All new functionality is additive and must not break any existing CRUD operations, authentication flows, UI theme, or database integrity.

This feature adds:
- A **role-aware Analytics Dashboard** with real-time charts (line, pie, bar, progress) built from live database data
- **PDF export** of the Admin analytics dashboard including rendered charts and summary statistics
- An **Interactive Calendar Planner** linked directly to `Planning` and `StudySession` entities with drag-and-drop, resize, and inline editing
- **Role-based calendar access** scoping data visibility to each user's permitted records
- **Tutor Performance Statistics** surfacing per-tutor metrics across enrolled students and courses

---

## Glossary

- **System**: The NOVA JavaFX desktop learning platform.
- **Analytics_Dashboard**: The new analytics view rendered inside the existing Admin Dashboard (`AdminDashboardController`) and Tutor Dashboard (`TutorDashboardController`) content areas.
- **Calendar_Planner**: The new interactive calendar view linked to `Planning` and `StudySession` entities, accessible from all three role dashboards.
- **CalendarEvent**: A visual representation of a `StudySession` or `Planning` record displayed on the Calendar_Planner.
- **AnalyticsService**: A new service class `services.studysession.AnalyticsService` responsible for all aggregation queries used by the Analytics_Dashboard.
- **CalendarService**: A new service class `services.studysession.CalendarService` responsible for loading, saving, and validating CalendarEvents.
- **PdfExportService**: A new service class `services.studysession.PdfExportService` responsible for generating PDF exports of the Analytics_Dashboard.
- **StudySession**: The existing entity `models.studysession.StudySession` with fields `startedAt`, `endedAt`, `duration`, `actualDuration`, `pomodoroCount`, `burnoutRisk`, `mood`, `energyLevel`, `userId`, `planningId`, `courseNameCache`.
- **Planning**: The existing entity `models.studysession.Planning` with fields `scheduledDate`, `scheduledTime`, `plannedDuration`, `status`, `courseId`, `courseNameCache`, `title`.
- **Course**: The existing entity `models.studysession.Course` with fields `courseName`, `category`, `difficulty`, `estimatedDuration`, `status`, `createdById`, `isPublished`.
- **User**: The existing entity `models.users.User` with roles `ROLE_ADMIN`, `ROLE_TUTOR`, `ROLE_STUDENT`.
- **UserSession**: The existing singleton `utils.UserSession` storing the logged-in user's `userId`, `username`, `email`, and `role`.
- **EnrollmentRequest**: The existing entity `models.studysession.EnrollmentRequest` linking students to courses with an `ACCEPTED` status.
- **StudentProgress**: The existing entity `models.studysession.StudentProgress` tracking per-student per-course progress percentage, total minutes studied, and Pomodoro cycles.
- **WeeklyView**: A calendar display mode showing 7 days in a grid with time slots.
- **MonthlyView**: A calendar display mode showing a full month grid with event chips.
- **DailyView**: An optional calendar display mode showing a single day with hourly time slots.
- **DifficultyColor**: A color code assigned to a CalendarEvent based on the associated course difficulty: BEGINNER → `#22c55e` (green), INTERMEDIATE → `#f59e0b` (amber), ADVANCED → `#ef4444` (red).
- **StatusColor**: A color code assigned to a CalendarEvent based on the `Planning.status`: SCHEDULED → `#3b82f6` (blue), COMPLETED → `#22c55e` (green), MISSED → `#ef4444` (red), CANCELLED → `#94a3b8` (slate).
- **OverlapGuard**: The validation logic that prevents two CalendarEvents for the same user from occupying overlapping time ranges.
- **ChartSnapshot**: A rendered image of a JavaFX chart node captured via `WritableImage` for inclusion in a PDF export.
- **PDF_Export**: A generated PDF file containing the Analytics_Dashboard charts, summary statistics, platform branding, and a generation timestamp.
- **TutorPerformanceRow**: A data transfer object holding per-tutor metrics: tutorName, enrolledStudents, averageCompletionRate, activeCourseCount, averageSessionDuration.
- **DateRangeFilter**: A pair of `LocalDate` values (from, to) used to restrict chart data to a specific time window.

---

## Requirements

---

### Requirement 1: Admin Analytics Dashboard — Entry Point

**User Story:** As an Admin, I want a dedicated analytics dashboard accessible from the Admin sidebar, so that I can monitor platform-wide activity through charts and statistics.

#### Acceptance Criteria

1. THE existing "Statistics" nav item (`navStudyStats` HBox, `showStudyStats` handler, `iconStudyStats` label) in the STUDY SESSION dropdown of `AdminDashboard.fxml` SHALL be repurposed: its label text SHALL be changed to "📊 Analytics", its `fx:id` SHALL be renamed from `navStudyStats` to `navAnalytics`, its `onMouseClicked` handler SHALL be renamed from `#showStudyStats` to `#showAnalytics`, and its icon label `fx:id` SHALL be renamed from `iconStudyStats` to `iconAnalytics`.
2. THE `showAnalytics` handler in `AdminDashboardController` SHALL load `AdminAnalyticsDashboardView.fxml` into the existing `contentArea` `StackPane`, replacing the previous call to `/views/studysession/StatsView.fxml`; no new top-level sidebar section SHALL be added.
3. THE `AdminAnalyticsDashboardController` SHALL be implemented in the `controllers.admin` package and SHALL load all chart data on initialization via `AnalyticsService`.
4. THE Analytics_Dashboard SHALL display a header row of four summary stat cards showing: Total Study Sessions (all time), Total Students, Total Courses (published), and Total Planning Events.
5. WHEN the Analytics_Dashboard is opened, THE System SHALL load all chart data asynchronously on a background thread and display a `ProgressIndicator` in each chart panel until data is ready.
6. THE Analytics_Dashboard SHALL apply the existing `study.css` stylesheet and reuse the platform's card style (white background, `border-radius: 12`, `border-color: #e2e8f0`, drop shadow `rgba(0,0,0,0.07)`).

---

### Requirement 2: Study Sessions Line Chart

**User Story:** As an Admin, I want a line chart showing the number of study sessions over time, so that I can identify usage trends across the platform.

#### Acceptance Criteria

1. THE Analytics_Dashboard SHALL display a JavaFX `LineChart` titled "📈 Study Sessions Over Time" showing the count of `StudySession` records grouped by time period.
2. THE Line Chart SHALL support two view modes switchable via a `ToggleButton` group: **Weekly** (sessions per ISO week for the last 12 weeks) and **Monthly** (sessions per calendar month for the last 12 months).
3. WHEN the user switches between Weekly and Monthly modes, THE System SHALL reload the chart data from `AnalyticsService` and re-render the `LineChart` within 500 milliseconds without reloading the entire dashboard.
4. THE `AnalyticsService` SHALL provide a method `List<Object[]> getSessionCountByWeek(int weeks)` returning rows of `[weekLabel: String, count: Integer]` and a method `List<Object[]> getSessionCountByMonth(int months)` returning rows of `[monthLabel: String, count: Integer]`.
5. THE Line Chart x-axis SHALL display the week or month label; the y-axis SHALL display the session count with a minimum value of 0.
6. IF no session data exists for a given period, THE System SHALL display the message "No session data available for this period." in the chart panel instead of an empty chart.

---

### Requirement 3: Session Difficulty Pie Chart

**User Story:** As an Admin, I want a pie chart showing the distribution of study sessions by difficulty level, so that I can understand which course difficulties are most used.

#### Acceptance Criteria

1. THE Analytics_Dashboard SHALL display a JavaFX `PieChart` titled "🥧 Sessions by Difficulty" showing the proportion of `StudySession` records grouped by the difficulty of the associated course.
2. THE `AnalyticsService` SHALL provide a method `Map<String, Integer> getSessionCountByDifficulty()` that joins `study_session` with `planning` and `course` tables to return counts keyed by difficulty value (`BEGINNER`, `INTERMEDIATE`, `ADVANCED`).
3. THE Pie Chart slices SHALL use the DifficultyColor mapping: BEGINNER → `#22c55e`, INTERMEDIATE → `#f59e0b`, ADVANCED → `#ef4444`.
4. THE Pie Chart SHALL display a legend below the chart showing each difficulty label with its corresponding color swatch and count.
5. IF all session counts are zero, THE System SHALL display the message "No difficulty data available." in the chart panel.

---

### Requirement 4: Time Spent by Course Bar Chart

**User Story:** As an Admin, I want a bar chart showing total time spent per course or category, so that I can identify which courses receive the most study effort.

#### Acceptance Criteria

1. THE Analytics_Dashboard SHALL display a JavaFX `BarChart` titled "⏱ Time Spent by Course" showing the total `actualDuration` (in minutes) of `StudySession` records grouped by `courseNameCache`.
2. THE Bar Chart SHALL support filtering by a `DateRangeFilter` applied via two `DatePicker` controls (From / To); WHEN the user changes either date, THE System SHALL reload the chart data and re-render within 500 milliseconds.
3. THE `AnalyticsService` SHALL provide a method `List<Object[]> getTimeSpentByCourse(LocalDate from, LocalDate to)` returning rows of `[courseName: String, totalMinutes: Integer]` ordered by `totalMinutes` descending.
4. THE Bar Chart x-axis SHALL display course names (truncated to 20 characters if longer); the y-axis SHALL display total minutes with a minimum value of 0.
5. THE Bar Chart SHALL display the exact minute value as a label above each bar.
6. IF no data exists for the selected date range, THE System SHALL display the message "No data for the selected date range." in the chart panel.

---

### Requirement 5: Student Progress Line Chart

**User Story:** As an Admin, I want a line chart showing student progress over time, so that I can track overall learning advancement across the platform.

#### Acceptance Criteria

1. THE Analytics_Dashboard SHALL display a JavaFX `LineChart` titled "📊 Student Progress Over Time" showing the average `progress_percentage` from `student_course_progress` grouped by time period.
2. THE Progress Line Chart SHALL support three view modes switchable via a `ToggleButton` group: **Daily** (last 30 days), **Weekly** (last 12 weeks), and **Monthly** (last 12 months).
3. THE `AnalyticsService` SHALL provide a method `List<Object[]> getAverageProgressByPeriod(String period, int count)` where `period` is `"DAILY"`, `"WEEKLY"`, or `"MONTHLY"`, returning rows of `[periodLabel: String, avgProgress: Double]`.
4. WHEN the user switches view modes, THE System SHALL reload and re-render the chart within 500 milliseconds.
5. THE y-axis SHALL display percentage values from 0 to 100; the x-axis SHALL display the period label.
6. IF no progress data exists, THE System SHALL display the message "No progress data available." in the chart panel.

---

### Requirement 6: Tutor Performance Statistics

**User Story:** As an Admin, I want a table of tutor performance metrics, so that I can evaluate each tutor's effectiveness and identify top performers.

#### Acceptance Criteria

1. THE Analytics_Dashboard SHALL display a `TableView<TutorPerformanceRow>` titled "👑 Tutor Performance" with columns: Tutor Name, Enrolled Students, Avg. Completion Rate (%), Active Courses, Avg. Session Duration (min).
2. THE `AnalyticsService` SHALL provide a method `List<TutorPerformanceRow> getTutorPerformanceStats()` that aggregates data from `user`, `course`, `enrollment_request`, `student_course_progress`, and `study_session` tables, scoped to users with `role = 'ROLE_TUTOR'`.
3. THE table SHALL highlight the row with the highest `enrolledStudents` count with a distinct background color (`#fef9c3`, amber-50) to indicate the most successful tutor.
4. THE table SHALL include a "Course Popularity Ranking" sub-section below the table listing the top 5 courses by enrolled student count, displayed as a numbered list with course name and enrollment count.
5. THE `AnalyticsService` SHALL provide a method `List<Object[]> getCoursePopularityRanking(int limit)` returning rows of `[courseName: String, enrolledCount: Integer]` ordered by `enrolledCount` descending.
6. IF no tutors exist in the system, THE System SHALL display the message "No tutor data available." in the tutor performance panel.

---

### Requirement 7: Admin Analytics PDF Export

**User Story:** As an Admin, I want to export the analytics dashboard as a PDF, so that I can share platform reports with stakeholders.

#### Acceptance Criteria

1. THE Analytics_Dashboard SHALL display an "📄 Export PDF" button that, when clicked, triggers PDF generation via `PdfExportService`.
2. THE `PdfExportService` SHALL capture each visible chart node as a `ChartSnapshot` using JavaFX `WritableImage` (via `node.snapshot(null, null)`) and embed the images in the PDF.
3. THE generated PDF SHALL include: platform branding header ("NOVA Learning Platform — Analytics Report"), generation date and time, the four summary stat card values, all five chart snapshots with their titles, and the Tutor Performance table data as a formatted text table.
4. THE `PdfExportService` SHALL use the `iTextPDF` library (version 5.5.x) added as a Maven dependency to construct and write the PDF; THE System SHALL add the dependency to `pom.xml`.
5. WHEN the PDF is generated, THE System SHALL open a `FileChooser` (save dialog) pre-populated with the filename `nova_analytics_report_{yyyy-MM-dd}.pdf`, allowing the Admin to choose a save location.
6. WHEN the PDF is saved successfully, THE System SHALL display a confirmation alert: "✅ Report exported successfully to {filePath}".
7. IF PDF generation fails (I/O error, rendering error), THEN THE System SHALL display an error alert: "❌ Export failed: {errorMessage}" without crashing the dashboard.

---

### Requirement 8: Tutor Analytics Dashboard

**User Story:** As a Tutor, I want an analytics view accessible from within my "My Courses" tab, so that I can monitor the performance of my own courses and students without leaving the courses area.

#### Acceptance Criteria

1. THE `tabMyCourses` tab in `TutorDashboard.fxml` SHALL be converted to contain a nested `TabPane` with two sub-tabs: "📘 Courses" (loading the existing `TutorCourseView.fxml`, unchanged) and "📊 Analytics" (loading the new `TutorAnalyticsDashboardView.fxml`).
2. THE existing top-level "Analytics" tab (`tabAnalytics`) SHALL be removed from the main `TabPane` in `TutorDashboard.fxml`, since analytics is now accessible as a sub-tab within "My Courses".
3. THE `TutorAnalyticsDashboardController` SHALL be implemented in the `controllers.studysession` package.
4. THE Tutor Analytics Dashboard SHALL display the following charts scoped strictly to courses where `created_by_id` equals the logged-in Tutor's `userId`: a `LineChart` of sessions per week (last 8 weeks), a `BarChart` of time spent per course, and a `PieChart` of session distribution by difficulty.
5. THE Tutor Analytics Dashboard SHALL display a summary row of stat cards: My Total Students, My Active Courses, My Avg. Completion Rate (%), My Total Sessions.
6. THE `AnalyticsService` SHALL provide tutor-scoped methods: `getSessionCountByWeek(int weeks, int tutorId)`, `getTimeSpentByCourse(LocalDate from, LocalDate to, int tutorId)`, and `getSessionCountByDifficulty(int tutorId)`.
7. ALL data displayed in the Tutor Analytics Dashboard SHALL be scoped to the logged-in Tutor's courses only; no data from other tutors or admins SHALL appear.

---

### Requirement 9: Calendar Planner — Entry Point and Views

**User Story:** As a user of any role, I want an interactive calendar planner accessible from my dashboard, so that I can visualize and manage my study schedule.

#### Acceptance Criteria

1. THE Admin Dashboard (`AdminDashboard.fxml`) SHALL include a new `HBox` nav item with `fx:id="navCalendar"`, label text "📅 Calendar", and `onMouseClicked="#showCalendar"` appended to the `studyGroup` `VBox` after the existing Analytics (`navAnalytics`) item; THE `showCalendar()` handler in `AdminDashboardController` SHALL load `CalendarPlannerView.fxml` into the existing `contentArea` `StackPane`; no new top-level sidebar section SHALL be added.
2. THE Tutor Dashboard (`TutorDashboard.fxml`) SHALL include a new "📅 Calendar" sub-tab added as a third entry inside the nested `TabPane` within `tabMyCourses`, alongside the existing "📘 Courses" and "📊 Analytics" sub-tabs (per Requirement 8); THE sub-tab SHALL load `CalendarPlannerView.fxml` into its content area; no new top-level tab SHALL be added to the main `TabPane`.
3. THE Student Dashboard (`MainDashboard.fxml`) SHALL include a new `ToggleButton` with `fx:id="tabCalendar"`, text "📅  Calendar", appended to the existing sidebar nav `VBox` after the `tabStats` toggle button; a new `fx:include` with `fx:id="calendarView"` sourcing `CalendarPlannerView.fxml` SHALL be added to the `contentArea` `StackPane`; `MainDashboardController` SHALL declare a `@FXML Parent calendarView` field and wire `tabCalendar`'s `onAction` to call `showView(calendarView, tabCalendar)`.
4. THE `CalendarPlannerView.fxml` SHALL be implemented with a corresponding `CalendarPlannerController` in the `controllers.studysession` package.
5. THE Calendar_Planner SHALL support three view modes switchable via a `ToggleButton` group in the toolbar: **Monthly**, **Weekly**, and **Daily** (optional bonus).
6. THE Calendar_Planner toolbar SHALL include navigation buttons "◀ Prev" and "Next ▶" to move to the previous or next period, and a "Today" button to return to the current date.
7. THE Calendar_Planner SHALL display the current period label (e.g., "June 2025" for monthly, "Jun 2 – Jun 8, 2025" for weekly) in the toolbar.

---

### Requirement 10: Calendar Event Display

**User Story:** As a user, I want each study session displayed as a color-coded event on the calendar, so that I can quickly understand my schedule at a glance.

#### Acceptance Criteria

1. THE Calendar_Planner SHALL load CalendarEvents from `CalendarService.getEventsForPeriod(userId, role, from, to)` where `from` and `to` are the start and end dates of the currently displayed period.
2. EACH CalendarEvent SHALL display: the course name as the event title, the start time and end time, and a background color based on DifficultyColor of the associated course.
3. THE `CalendarService` SHALL derive CalendarEvents from `StudySession` records (using `startedAt` as start, `endedAt` as end) and from `Planning` records (using `scheduledDate` + `scheduledTime` as start, start + `plannedDuration` as end).
4. THE Calendar_Planner SHALL display a color legend below the calendar grid showing DifficultyColor and StatusColor mappings with their labels.
5. IN the Monthly view, CalendarEvents SHALL be displayed as compact chips (course name truncated to 15 characters) within the day cell; IF more than 3 events exist for a day, THE System SHALL display "+N more" and expand on click.
6. IN the Weekly view, CalendarEvents SHALL be displayed as positioned blocks spanning the correct time range within the day column.

---

### Requirement 11: Calendar Event Interaction

**User Story:** As a user, I want to click, drag, resize, and edit calendar events, so that I can manage my study schedule interactively.

#### Acceptance Criteria

1. WHEN a user clicks a CalendarEvent, THE System SHALL open a session details popup displaying: course name, start time, end time, duration (minutes), difficulty, status, notes/mood, and XP earned (for StudySession events).
2. THE session details popup SHALL include an "✏ Edit" button that opens the existing `StudySessionFormController` or `PlanningFormController` pre-populated with the event's data, and a "🗑 Delete" button visible only to users with permission to delete the event (see Requirement 13).
3. THE Calendar_Planner SHALL support drag-and-drop rescheduling of CalendarEvents: WHEN a user drags an event to a new time slot, THE System SHALL validate the new time via `OverlapGuard`, and IF valid, THE System SHALL update the `startedAt`/`endedAt` (for StudySession) or `scheduledDate`/`scheduledTime` (for Planning) in the database via `CalendarService.rescheduleEvent(eventId, type, newStart)` and refresh the calendar.
4. THE Calendar_Planner SHALL support resize of CalendarEvent duration in the Weekly view: WHEN a user drags the bottom edge of an event block, THE System SHALL update the `endedAt` or `plannedDuration` accordingly, validate the new duration is at least 5 minutes, and persist the change.
5. WHEN a CalendarEvent is updated (drag, resize, or edit), THE Calendar_Planner SHALL refresh the affected day's events within 1 second without a full page reload.
6. IF a drag-and-drop or resize operation would create an overlap with an existing event for the same user, THEN THE System SHALL reject the change, restore the event to its original position, and display a tooltip: "⚠ Time slot conflicts with an existing session."

---

### Requirement 12: Calendar Business Rules and Validation

**User Story:** As a user, I want the calendar to enforce scheduling rules, so that my study plan remains consistent and conflict-free.

#### Acceptance Criteria

1. THE `OverlapGuard` SHALL check for overlapping CalendarEvents by querying both `study_session` and `planning` tables for the same `userId` within the proposed time range, excluding the event being rescheduled.
2. THE `CalendarService.rescheduleEvent` method SHALL call `OverlapGuard` before persisting any time change; IF an overlap is detected, THE method SHALL throw an `OverlapException` with a message identifying the conflicting event title and time.
3. THE `CalendarService` SHALL enforce a minimum event duration of 5 minutes and a maximum of 480 minutes (8 hours); IF a resize or edit violates these bounds, THE System SHALL display a validation error and reject the change.
4. WHEN a CalendarEvent is deleted via the details popup, THE System SHALL display a confirmation dialog "Are you sure you want to delete this session?" before calling `CalendarService.deleteEvent(eventId, type)`.
5. WHEN a CalendarEvent is saved (create, edit, reschedule, resize), THE System SHALL persist the change to the database within the same JavaFX Application Thread callback using a background service task to avoid blocking the UI.

---

### Requirement 13: Role-Based Calendar Access

**User Story:** As a platform administrator, I want calendar data scoped to each user's role, so that users only see the sessions they are authorized to view.

#### Acceptance Criteria

1. WHEN the logged-in user has role `ROLE_STUDENT`, THE `CalendarService.getEventsForPeriod` SHALL return only `StudySession` records where `userId` equals the logged-in student's `userId`, and only `Planning` records where `courseId` is in the student's accepted enrollments.
2. WHEN the logged-in user has role `ROLE_TUTOR`, THE `CalendarService.getEventsForPeriod` SHALL return `StudySession` records for all students enrolled in the tutor's courses, and `Planning` records for all courses where `created_by_id` equals the tutor's `userId`.
3. WHEN the logged-in user has role `ROLE_ADMIN`, THE `CalendarService.getEventsForPeriod` SHALL return all `StudySession` and `Planning` records in the system without restriction.
4. THE `CalendarService` SHALL enforce edit and delete permissions: a Student MAY edit or delete only their own events; a Tutor MAY edit or delete events belonging to their own courses; an Admin MAY edit or delete any event.
5. IF a user attempts to edit or delete an event they are not authorized to modify, THEN THE System SHALL display an alert "🚫 You do not have permission to modify this event." and cancel the operation.

---

### Requirement 14: Calendar Filters and Controls

**User Story:** As a user, I want to filter calendar events by various criteria, so that I can focus on specific sessions or courses.

#### Acceptance Criteria

1. THE Calendar_Planner toolbar SHALL include a collapsible filter panel containing the following filter controls: Course (ComboBox populated from the user's accessible courses), Difficulty (ComboBox: All / BEGINNER / INTERMEDIATE / ADVANCED), Status (ComboBox: All / SCHEDULED / COMPLETED / MISSED / CANCELLED), and Date Range (two `DatePicker` controls).
2. WHEN the logged-in user has role `ROLE_ADMIN` or `ROLE_TUTOR`, THE filter panel SHALL additionally include: Student (ComboBox populated from accessible students) and Tutor (ComboBox populated from all tutors, Admin only).
3. WHEN any filter value changes, THE System SHALL re-query `CalendarService.getEventsForPeriod` with the updated filter parameters and refresh the calendar display within 500 milliseconds.
4. THE Calendar_Planner SHALL include a "🔄 Reset Filters" button that clears all filter controls to their default "All" state and reloads the calendar.
5. THE active filter state SHALL be preserved when the user navigates between calendar periods (Prev / Next / Today).

---

### Requirement 15: Analytics and Calendar UI/UX Consistency

**User Story:** As a UI designer, I want all new views to match the existing platform theme and interaction patterns, so that the user experience is cohesive.

#### Acceptance Criteria

1. THE Analytics_Dashboard and Calendar_Planner views SHALL apply the existing `study.css` stylesheet.
2. ALL new stat cards SHALL use the same base card style as the existing platform: white background, `border-radius: 12`, `border-color: #e2e8f0`, drop shadow `rgba(0,0,0,0.07)`, padding 16px.
3. ALL JavaFX charts SHALL use smooth animation on data load (`chart.setAnimated(true)`) and SHALL display tooltips on data point hover showing the exact value.
4. THE Calendar_Planner grid SHALL use alternating row shading for time slots (white / `#f8fafc`) and SHALL highlight the current day cell with a `#eff6ff` (blue-50) background.
5. ALL loading states (chart data fetch, calendar event load) SHALL display a `ProgressIndicator` spinner in the relevant panel until data is ready, then fade in the content using a `FadeTransition` of 300ms.
6. ALL new buttons SHALL follow the existing button style conventions: `border-radius: 8`, cursor hand, padding `5 12`, font size 11px.
7. THE color legend for the Calendar_Planner SHALL be displayed as a horizontal row of colored chips with labels, positioned below the calendar grid.

---

### Requirement 16: AnalyticsService and CalendarService Architecture

**User Story:** As a developer, I want analytics and calendar logic encapsulated in dedicated service classes, so that the codebase remains clean and testable.

#### Acceptance Criteria

1. THE `AnalyticsService` SHALL be implemented as `services.studysession.AnalyticsService` and SHALL use parameterized SQL queries via `java.sql.PreparedStatement` for all database interactions.
2. THE `CalendarService` SHALL be implemented as `services.studysession.CalendarService` and SHALL expose the following public methods: `getEventsForPeriod(int userId, String role, LocalDate from, LocalDate to, CalendarFilter filter)`, `rescheduleEvent(int eventId, String type, LocalDateTime newStart)`, `resizeEvent(int eventId, String type, LocalDateTime newEnd)`, `deleteEvent(int eventId, String type)`, and `getEventDetail(int eventId, String type)`.
3. THE `OverlapException` SHALL be a checked exception in the `services.studysession` package with a constructor `OverlapException(String conflictingEventTitle, LocalDateTime conflictStart, LocalDateTime conflictEnd)`.
4. ALL service methods that perform database writes SHALL execute within a transaction (using `connection.setAutoCommit(false)` / `commit()` / `rollback()`) to ensure atomicity.
5. THE `AnalyticsService` and `CalendarService` SHALL obtain database connections via the existing `utils.DatabaseConnection` utility class and SHALL NOT create new connection pools.
6. ALL service methods SHALL declare `throws SQLException` and SHALL NOT swallow exceptions silently; controllers SHALL catch and display errors via JavaFX alert dialogs.

---

### Requirement 17: No Regression and Backward Compatibility

**User Story:** As a developer, I want all new features to be purely additive, so that no existing functionality is broken.

#### Acceptance Criteria

1. THE System SHALL NOT modify any existing model class (`StudySession`, `Planning`, `Course`, `User`, `EnrollmentRequest`, `StudentProgress`) field names, types, or constructors.
2. THE System SHALL NOT modify any existing database table schema; all new queries SHALL use existing columns only.
3. THE System SHALL NOT modify any existing controller's public API (method signatures, `@FXML` field names) in a way that breaks existing FXML bindings.
4. THE "📊 Analytics" navigation item in `AdminDashboardController` SHALL be implemented by repurposing the existing `navStudyStats` HBox and `showStudyStats` handler (renaming them to `navAnalytics` / `showAnalytics`) rather than adding a new nav row; THE new "📅 Calendar" navigation item SHALL be added by appending a new `@FXML` handler method and a new `HBox` nav row to the existing FXML without removing or renaming any other existing nav items.
5. THE new "📅 Calendar" tab in `TutorDashboardController` SHALL be added by appending a new `Tab` to the existing `TabPane` in `TutorDashboard.fxml` without modifying existing tab definitions.
6. THE new "📅 Calendar" toggle in `MainDashboardController` SHALL be added by appending a new `ToggleButton` to the existing sidebar without modifying existing toggle button handlers.
