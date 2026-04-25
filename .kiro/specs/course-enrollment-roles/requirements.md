# Requirements Document

## Introduction

This feature extends the existing NOVA JavaFX learning platform (Maven, Java 17, MySQL `nova_db`) with a Course Enrollment system, Role-Based Permission enforcement, and Tutor Course Management capabilities. The platform already has three roles — Student (`ROLE_STUDENT`), Tutor (`ROLE_TUTOR`), and Admin (`ROLE_ADMIN`) — stored in the `User` model, and a `studysession` module containing `Course`, `Planning`, and `StudySession` models with their controllers and services.

The feature must extend the system cleanly: no existing CRUD logic, database tables, UI theme, authentication flow, or architecture may be broken. All new functionality is added within the `studysession` module (models, services, controllers, FXML views) and the shared `utils` package.

---

## Glossary

- **System**: The NOVA JavaFX desktop learning platform.
- **Enrollment_System**: The subsystem responsible for managing enrollment requests and access control.
- **Course**: An existing entity in `models.studysession.Course`, stored in the `course` table.
- **EnrollmentRequest**: A new entity representing a student's request to join a course, with statuses `PENDING`, `ACCEPTED`, or `REJECTED`.
- **Student**: A user with role `ROLE_STUDENT`. Browses courses and requests enrollment.
- **Tutor**: A user with role `ROLE_TUTOR`. Creates and manages own courses; accepts or rejects enrollment requests for own courses.
- **Admin**: A user with role `ROLE_ADMIN`. Has full access to all courses and enrollments; courses created by Admin are auto-open.
- **CourseOwner**: The user (Tutor or Admin) who created a given course, identified by `created_by_id` on the `course` table.
- **Provider_Label**: A display string shown on every course card and detail page: "Provided by [Instructor Name]" for Tutor-created courses, or "Provided by Nova" for Admin-created courses.
- **Start_Course_Button**: A button shown to a Student after enrollment is accepted, replacing the "Enroll Request" button, which navigates to the course content page.
- **Tutor_Dashboard**: The analytics view shown to a Tutor, displaying statistics for their own courses only.
- **UserSession**: The existing singleton `utils.UserSession` that stores the logged-in user's `userId`, `username`, `email`, and `role` string (e.g., `"ROLE_TUTOR"`).
- **EnrollmentService**: A new service class in `services.studysession` responsible for all enrollment request persistence and queries.
- **CourseAccessGuard**: The access-control logic that prevents Students from viewing course content without an accepted enrollment.
- **TutorCourseController**: A new controller (mirroring `CourseController`) that provides Tutors with full CRUD over their own courses only.

---

## Requirements

---

### Requirement 1: Course Ownership Tracking

**User Story:** As a platform architect, I want every course to record who created it, so that the system can enforce per-role access rules and display the correct provider label.

#### Acceptance Criteria

1. THE System SHALL use the existing `created_by_id` column (INT, nullable, FK → `user.id` ON DELETE SET NULL) already present on the `course` table to identify the CourseOwner; no new ownership column SHALL be added to the `course` table.
2. WHEN a Tutor creates a course, THE System SHALL persist the Tutor's `userId` as `created_by_id`.
3. WHEN an Admin creates a course, THE System SHALL persist the Admin's `userId` as `created_by_id`.
4. THE Course model SHALL be extended with `createdById` (Integer), `creatorName` (String), and `creatorRole` (String) fields; THE System SHALL populate `creatorRole` by joining `created_by_id` → `user.id` → `user.role` at query time, not from a separate DB column.
5. THE Course model SHALL expose `getCreatedById()`, `getCreatorName()`, and `getCreatorRole()` accessors so that controllers can read ownership without additional queries.
6. IF `created_by_id` IS NULL for a legacy course row, THEN THE System SHALL treat that course as Admin-owned and display "Provided by Nova".

---

### Requirement 2: Provider Label Display

**User Story:** As a Student, I want to see who provides each course, so that I know whether it is an official platform course or an instructor-led course.

#### Acceptance Criteria

1. WHEN a course card is rendered for any role, THE System SHALL display a Provider_Label below the course title.
2. WHERE `creatorRole` (derived from `user.role` via JOIN on `created_by_id`) equals `"ROLE_TUTOR"`, THE System SHALL set the Provider_Label text to "Provided by [creator username]".
3. WHERE `creatorRole` equals `"ROLE_ADMIN"` or `created_by_id` is NULL, THE System SHALL set the Provider_Label text to "Provided by Nova".
4. THE Provider_Label SHALL be visible on both the course card (in `UserCourseController` and `TutorCourseController`) and the course detail page (`CourseDetailController`).
5. THE System SHALL resolve the creator username from the `user` table using `created_by_id` at course load time and cache it in the `Course` object's `creatorName` field to avoid repeated queries.

---

### Requirement 3: Enrollment Request Entity and Persistence

**User Story:** As a system designer, I want a dedicated enrollment request table, so that enrollment state is reliably stored and queryable.

#### Acceptance Criteria

1. THE System SHALL use the existing `enrollment_request` table in `nova_db` with columns: `id` (PK, auto-increment), `status` (VARCHAR, values: `PENDING`, `ACCEPTED`, `REJECTED`), `requested_at` (DATETIME), `responded_at` (DATETIME, nullable), `message` (TEXT/VARCHAR, nullable), `student_id` (FK → `user.id` ON DELETE CASCADE), `course_id` (FK → `course.id` ON DELETE CASCADE), and `responded_by_id` (INT, nullable, FK → `user.id`); no new `enrollment_request` table SHALL be created.
2. THE System SHALL verify that a UNIQUE constraint exists on `(course_id, student_id)` so that a Student cannot submit duplicate enrollment requests for the same course.
3. THE EnrollmentService SHALL expose `createRequest(courseId, studentId)`, `acceptRequest(requestId, respondedById)`, `rejectRequest(requestId, respondedById)`, `findByCourse(courseId)`, `findByStudent(studentId)`, and `getEnrollmentStatus(courseId, studentId)` methods.
4. WHEN `acceptRequest` is called, THE EnrollmentService SHALL set `status = 'ACCEPTED'`, `responded_at = NOW()`, and `responded_by_id` to the acting user's id in a single atomic UPDATE.
5. WHEN `rejectRequest` is called, THE EnrollmentService SHALL set `status = 'REJECTED'`, `responded_at = NOW()`, and `responded_by_id` to the acting user's id in a single atomic UPDATE.
6. IF a Student calls `createRequest` for a course where an enrollment record already exists, THEN THE EnrollmentService SHALL throw an `IllegalStateException` with a descriptive message rather than inserting a duplicate row.
7. THE `EnrollmentRequest` model SHALL include a `message` field (String, nullable) that stores an optional note from the student when submitting the request, and an optional response note from the tutor/admin when accepting or rejecting.

---

### Requirement 4: Student Enrollment Workflow

**User Story:** As a Student, I want to request enrollment in a course and track my request status, so that I can gain access to courses I am interested in.

#### Acceptance Criteria

1. WHEN a Student views the course list, THE Enrollment_System SHALL display an "Enroll Request" button on each course card for courses where the Student has no enrollment record.
2. WHEN a Student clicks "Enroll Request", THE Enrollment_System SHALL create an EnrollmentRequest with status `PENDING` and replace the button with a "⏳ Pending" status badge on that card.
3. WHEN a Student views a course card for which the enrollment status is `ACCEPTED`, THE Enrollment_System SHALL display a "▶ Start Course" button instead of the enrollment button.
4. WHEN a Student views a course card for which the enrollment status is `REJECTED`, THE Enrollment_System SHALL display a "❌ Rejected" badge and allow the Student to submit a new enrollment request.
5. WHEN a Student clicks "▶ Start Course", THE Enrollment_System SHALL navigate the Student to the course content page for that course.
6. WHILE a Student's enrollment status for a course is `PENDING` or does not exist, THE CourseAccessGuard SHALL prevent the Student from accessing that course's content page.
7. WHERE the course was created by an Admin (`creatorRole` derived as `"ROLE_ADMIN"` via JOIN on `created_by_id`), THE Enrollment_System SHALL display the "▶ Start Course" button directly without requiring an enrollment request.
8. THE Enrollment_System SHALL load enrollment statuses for all visible courses in a single batch query (not one query per card) to avoid N+1 performance issues.

---

### Requirement 5: Tutor Enrollment Request Management

**User Story:** As a Tutor, I want to review enrollment requests for my own courses, so that I can control which students access my content.

#### Acceptance Criteria

1. WHEN a Tutor opens the Tutor course management view, THE System SHALL display an "Enrollment Requests" tab in the top navigation bar, visible only to users with role `ROLE_TUTOR`.
2. WHEN a Tutor clicks the "Enrollment Requests" tab, THE System SHALL load a dedicated page listing all `PENDING` enrollment requests for courses owned by that Tutor.
3. THE Enrollment Requests page SHALL display for each request: the student's username, the course name, and the date the request was submitted (`requested_at`).
4. THE Enrollment Requests page SHALL provide an "Accept" button and a "Reject" button for each pending request.
5. WHEN a Tutor clicks "Accept" for a request, THE Enrollment_System SHALL call `EnrollmentService.acceptRequest` and refresh the pending requests list, removing the accepted entry.
6. WHEN a Tutor clicks "Reject" for a request, THE Enrollment_System SHALL call `EnrollmentService.rejectRequest` and refresh the pending requests list, removing the rejected entry.
7. IF no pending enrollment requests exist for the Tutor's courses, THEN THE System SHALL display an empty-state message: "No pending enrollment requests."
8. THE Enrollment Requests page SHALL only show requests for courses where `created_by_id` equals the currently logged-in Tutor's `userId`, enforced at the service query level.

---

### Requirement 6: Admin Enrollment Monitoring

**User Story:** As an Admin, I want to monitor all enrollments across the platform, so that I can oversee student access and course popularity.

#### Acceptance Criteria

1. WHEN an Admin navigates to the Courses section of the Admin Dashboard, THE System SHALL display an "Enrollments" sub-navigation item alongside the existing Courses, Plannings, Sessions, and Stats items.
2. WHEN an Admin clicks "Enrollments", THE System SHALL load an enrollment overview page showing all enrollment records across all courses.
3. THE enrollment overview page SHALL display for each record: student username, course name, creator name, enrollment status, and `requested_at` date.
4. THE enrollment overview page SHALL support filtering by status (`PENDING`, `ACCEPTED`, `REJECTED`) and by course name search.
5. THE System SHALL allow an Admin to accept or reject any `PENDING` enrollment request from the enrollment overview page.
6. THE System SHALL display aggregate counts (total enrollments, pending, accepted, rejected) as summary cards at the top of the enrollment overview page.

---

### Requirement 7: Tutor Course Management (Full CRUD)

**User Story:** As a Tutor, I want the same course creation and management experience as an Admin, so that I can independently build and maintain my own course catalog.

#### Acceptance Criteria

1. WHEN a user with role `ROLE_TUTOR` logs in, THE System SHALL route the Tutor to a Tutor Dashboard that includes a "My Courses" section with full CRUD capabilities.
2. THE TutorCourseController SHALL reuse the existing `CourseFormController` and `CourseForm.fxml` for creating and editing courses, passing the Tutor's `userId` as `created_by_id`.
3. WHEN a Tutor creates a course, THE System SHALL automatically set `created_by_id` to the Tutor's `userId`; THE System SHALL derive `creatorRole` as `"ROLE_TUTOR"` from the `user` table via JOIN on `created_by_id` and SHALL NOT store a separate `creator_role` column.
4. THE Tutor course list SHALL only display courses where `created_by_id` equals the logged-in Tutor's `userId`; courses owned by other Tutors or Admins SHALL NOT appear.
5. THE System SHALL apply the same form validation rules as the Admin course form: course name (3–255 chars, required), category (3+ chars, required), difficulty (BEGINNER/INTERMEDIATE/ADVANCED, required), estimated duration (positive integer, required), max students (positive integer, optional), and published flag.
6. WHEN a Tutor attempts to delete a course that has accepted enrollments, THE System SHALL display a confirmation dialog warning that enrolled students will lose access, and SHALL only proceed on explicit confirmation.
7. THE Tutor course management UI SHALL match the visual style of the existing Admin `CourseView.fxml` (card layout, difficulty color strips, badge rows, footer action buttons).
8. THE System SHALL allow a Tutor to toggle the published state of their own courses using the same publish/unpublish mechanism as the Admin view.

---

### Requirement 8: Role-Based Access Control Enforcement

**User Story:** As a security-conscious developer, I want the system to enforce role permissions at the controller level, so that no user can access functionality outside their role.

#### Acceptance Criteria

1. THE CourseAccessGuard SHALL read the current user's role from `UserSession.getInstance().getRole()` on every protected navigation action.
2. WHEN a Student attempts to navigate to the Admin Dashboard or Tutor Dashboard, THE System SHALL redirect the Student to the Student Dashboard (`UserStudyDashboard.fxml`) without loading the restricted view.
3. WHEN a Tutor attempts to access course management for a course where `created_by_id` does not equal the Tutor's `userId`, THE System SHALL display an "Access Denied" alert and abort the operation.
4. WHEN an Admin accesses any course or enrollment record, THE System SHALL apply no ownership restriction.
5. THE System SHALL not expose edit, delete, or publish buttons on course cards rendered in the Student view (`UserCourseController`).
6. THE System SHALL not expose the "Enrollment Requests" tab to Admin or Student users; it SHALL only be visible to Tutors.
7. IF a controller action is invoked with an insufficient role, THEN THE System SHALL log a warning to `System.err` with the format: `"[ACCESS DENIED] Role {role} attempted {action}"`.

---

### Requirement 9: Tutor Analytics Dashboard

**User Story:** As a Tutor, I want to see analytics for my own courses on my dashboard, so that I can monitor student engagement and course performance.

#### Acceptance Criteria

1. THE Tutor_Dashboard SHALL display a summary section with the following metrics for the logged-in Tutor's courses: total enrolled students (accepted enrollments), active students (students with at least one `IN_PROGRESS` study session linked to a Tutor course), completion rate (percentage of accepted enrollments where course status is `COMPLETED`), and total courses owned.
2. WHEN a Tutor views the analytics section, THE System SHALL display a per-course breakdown showing: course name, enrolled student count, and a visual progress indicator.
3. THE analytics data SHALL be scoped strictly to courses where `created_by_id` equals the logged-in Tutor's `userId`; no data from other Tutors' or Admins' courses SHALL appear.
4. THE Tutor_Dashboard SHALL include a "Most Popular Course" highlight showing the course with the highest accepted enrollment count.
5. WHEN no courses exist for the Tutor, THE System SHALL display an empty-state message: "No courses yet. Create your first course to see analytics."
6. THE analytics queries SHALL be encapsulated in a dedicated `TutorAnalyticsService` class in `services.studysession`, keeping analytics logic separate from `CourseService` and `EnrollmentService`.

---

### Requirement 10: Tutor Dashboard Routing

**User Story:** As a Tutor, I want a dedicated dashboard when I log in, so that I have a tailored experience with my courses and enrollment requests front and center.

#### Acceptance Criteria

1. WHEN a user with role `ROLE_TUTOR` successfully authenticates, THE System SHALL route the Tutor to a `TutorDashboard.fxml` view instead of the Student `NovaDashboard.fxml`.
2. THE Tutor Dashboard SHALL contain a top navigation bar with tabs: "My Courses", "Enrollment Requests", "Analytics", and "Study Sessions".
3. THE Tutor Dashboard SHALL display the Tutor's username and a first-letter avatar in the header, consistent with the existing Admin Dashboard header style.
4. THE Tutor Dashboard SHALL reuse the existing `UserStudyDashboard` planning and session views for the "Study Sessions" tab, so Tutors retain access to their own planning and session tracking.
5. WHEN a Tutor clicks "My Courses", THE System SHALL load the `TutorCourseView.fxml` showing only that Tutor's courses.
6. WHEN a Tutor clicks "Enrollment Requests", THE System SHALL load the `EnrollmentRequestsView.fxml` showing pending requests for that Tutor's courses.
7. WHEN a Tutor clicks "Analytics", THE System SHALL load the `TutorAnalyticsView.fxml` showing the Tutor's course analytics.
8. THE `LoginController.routeUserBasedOnRole` method SHALL be updated to handle `ROLE_TUTOR` routing without modifying the existing `ROLE_ADMIN` or `ROLE_STUDENT` routing paths.

---

### Requirement 11: Data Integrity and Cascade Rules

**User Story:** As a database administrator, I want referential integrity enforced on all new tables, so that orphaned records cannot accumulate.

#### Acceptance Criteria

1. THE `enrollment_request` table SHALL declare `ON DELETE CASCADE` on the `course_id` foreign key so that deleting a course removes all its enrollment requests.
2. THE `enrollment_request` table SHALL declare `ON DELETE CASCADE` on the `student_id` foreign key so that deleting a user removes all their enrollment requests.
3. WHEN `CourseService.delete` is called for a course that has `ACCEPTED` enrollment records, THE System SHALL throw a `SQLException` with the message: "Cannot delete course: it has accepted enrollments. Notify enrolled students first."
4. THE `course` table's existing `created_by_id` column SHALL use `ON DELETE SET NULL` so that deleting a user account does not cascade-delete their courses.
5. THE System SHALL wrap `acceptRequest` and `rejectRequest` operations in a database transaction so that partial updates (to `status`, `responded_at`, and `responded_by_id`) cannot leave enrollment records in an inconsistent state.

---

### Requirement 12: UI Consistency and Validation

**User Story:** As a UI designer, I want all new views to match the existing platform theme, so that the user experience is cohesive.

#### Acceptance Criteria

1. THE System SHALL apply the existing `study.css` stylesheet to all new FXML views in the `studysession` module.
2. ALL new course cards in Tutor and Student views SHALL use the same card structure as the existing `UserCourseController.buildCard` method: difficulty color strip, badge row, meta row, progress bar, and footer action buttons.
3. THE System SHALL display inline per-field validation error labels (matching the pattern in `CourseFormController`) on all new forms introduced by this feature.
4. WHEN a form field fails validation, THE System SHALL highlight the field border in red (`#ef4444`) and display the error message below the field without closing the form.
5. THE System SHALL display success feedback (green status label, `#22c55e`) after any successful create, update, accept, or reject operation.
6. ALL buttons introduced by this feature SHALL follow the existing button style conventions: background color, text color, border radius 8, cursor hand, padding 5 12, font size 11px.

