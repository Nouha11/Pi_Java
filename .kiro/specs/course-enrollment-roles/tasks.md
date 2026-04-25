# Implementation Plan: Course Enrollment and Role-Based Access

## Overview

This implementation plan extends the NOVA JavaFX learning platform with a Course Enrollment system, Role-Based Permission enforcement, and Tutor Course Management capabilities. The implementation follows a bottom-up approach: models → services → controllers → views → integration, ensuring each layer is functional before building on top of it.

## Tasks

- [x] 1. Extend Course model with ownership fields
  - Add `createdById` (Integer), `creatorName` (String), and `creatorRole` (String) fields to `Course.java`
  - Add getters and setters for the new fields
  - Update the `Course` constructor to accept the new fields
  - _Requirements: 1.4, 1.5_

- [x] 2. Create EnrollmentRequest model
  - Create `models/studysession/EnrollmentRequest.java` with fields: `id`, `status`, `requestedAt`, `respondedAt`, `message`, `studentId`, `courseId`, `respondedById`
  - Add getters, setters, and constructors
  - Add a `getStatusBadgeText()` helper method that returns display text for each status
  - _Requirements: 3.1, 3.7_

- [x] 3. Update CourseService to populate ownership fields
  - [x] 3.1 Modify `mapResultSet()` to JOIN with `user` table on `created_by_id`
    - Update SQL queries to include `LEFT JOIN user ON course.created_by_id = user.id`
    - Extract `user.username` as `creatorName` and `user.role` as `creatorRole`
    - Handle NULL `created_by_id` by setting `creatorRole` to "ROLE_ADMIN" and `creatorName` to "Nova"
    - _Requirements: 1.1, 1.4, 1.6_

  - [x] 3.2 Update `create()` method to accept and persist `createdById`
    - Add `createdById` parameter to the INSERT statement
    - Set `created_by_id` column from the parameter
    - _Requirements: 1.2, 1.3_

  - [x] 3.3 Add `findByCreator(int userId)` method
    - Query courses WHERE `created_by_id = ?`
    - Return list of courses owned by the specified user
    - _Requirements: 7.4_

- [x] 4. Create EnrollmentService with full CRUD
  - [x] 4.1 Create `services/studysession/EnrollmentService.java` class
    - Add database connection field using `MyConnection.getInstance().getCnx()`
    - _Requirements: 3.3_

  - [x] 4.2 Implement `createRequest(int courseId, int studentId, String message)`
    - Check for existing enrollment using `getEnrollmentStatus()`
    - Throw `IllegalStateException` if duplicate enrollment exists
    - INSERT new record with status `PENDING`, `requested_at = NOW()`
    - _Requirements: 3.3, 3.6_

  - [x] 4.3 Implement `acceptRequest(int requestId, int respondedById)`
    - UPDATE enrollment_request SET status='ACCEPTED', responded_at=NOW(), responded_by_id=? WHERE id=?
    - Wrap in transaction for atomicity
    - _Requirements: 3.4, 3.5_

  - [x] 4.4 Implement `rejectRequest(int requestId, int respondedById)`
    - UPDATE enrollment_request SET status='REJECTED', responded_at=NOW(), responded_by_id=? WHERE id=?
    - Wrap in transaction for atomicity
    - _Requirements: 3.4, 3.5_

  - [x] 4.5 Implement `findByCourse(int courseId)` query method
    - SELECT all enrollment requests for a given course
    - JOIN with user table to get student username
    - Return list of EnrollmentRequest objects
    - _Requirements: 3.3_

  - [x] 4.6 Implement `findByStudent(int studentId)` query method
    - SELECT all enrollment requests for a given student
    - JOIN with course table to get course name
    - Return list of EnrollmentRequest objects
    - _Requirements: 3.3_

  - [x] 4.7 Implement `getEnrollmentStatus(int courseId, int studentId)` method
    - Query enrollment_request WHERE course_id=? AND student_id=?
    - Return status string (PENDING/ACCEPTED/REJECTED) or null if no record exists
    - _Requirements: 3.3, 4.1_

  - [x] 4.8 Implement `findPendingByCreator(int creatorId)` method
    - Query enrollment requests with status='PENDING' for courses owned by creatorId
    - JOIN enrollment_request → course → user to filter by created_by_id
    - Return list with student username and course name populated
    - _Requirements: 5.2, 5.8_

  - [x] 4.9 Implement `findAllEnrollments()` admin query method
    - SELECT all enrollment records with JOINs to get student name, course name, creator name
    - Support filtering by status and course name search
    - Return list of enriched EnrollmentRequest objects
    - _Requirements: 6.2, 6.4_

- [x] 5. Create TutorAnalyticsService
  - [x] 5.1 Create `services/studysession/TutorAnalyticsService.java` class
    - Add database connection field
    - _Requirements: 9.6_

  - [x] 5.2 Implement `getTotalEnrolledStudents(int tutorId)` method
    - COUNT DISTINCT student_id from enrollment_request WHERE status='ACCEPTED' AND course_id IN (SELECT id FROM course WHERE created_by_id=?)
    - _Requirements: 9.1_

  - [x] 5.3 Implement `getActiveStudents(int tutorId)` method
    - COUNT DISTINCT students with IN_PROGRESS study sessions linked to tutor's courses
    - JOIN study_session → planning → course WHERE created_by_id=? AND session status='IN_PROGRESS'
    - _Requirements: 9.1_

  - [x] 5.4 Implement `getCompletionRate(int tutorId)` method
    - Calculate percentage: (completed enrollments / total accepted enrollments) * 100
    - Query enrollment_request JOIN course WHERE status='ACCEPTED' AND created_by_id=?
    - _Requirements: 9.1_

  - [x] 5.5 Implement `getTotalCoursesOwned(int tutorId)` method
    - COUNT courses WHERE created_by_id=?
    - _Requirements: 9.1_

  - [x] 5.6 Implement `getPerCourseBreakdown(int tutorId)` method
    - Query each course with enrolled student count
    - Return list of objects: {courseName, enrolledCount, progress}
    - _Requirements: 9.2_

  - [x] 5.7 Implement `getMostPopularCourse(int tutorId)` method
    - Query course with MAX(enrollment count) WHERE created_by_id=?
    - Return course name and enrollment count
    - _Requirements: 9.4_

- [ ] 6. Checkpoint - Verify database schema and service layer
  - Run the application and verify CourseService can read creator fields
  - Test EnrollmentService CRUD operations using a simple test controller or main method
  - Verify TutorAnalyticsService queries return expected data
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Update UserCourseController with enrollment buttons
  - [x] 7.1 Add EnrollmentService field to UserCourseController
    - Initialize in the controller
    - _Requirements: 4.1_

  - [x] 7.2 Modify `buildCard()` to display provider label
    - Add a Label below the course title showing "Provided by [creatorName]" or "Provided by Nova"
    - Use `course.getCreatorRole()` to determine the text
    - Style the label with font-size 10px, color #94a3b8
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

  - [x] 7.3 Modify `buildCard()` footer to add enrollment button logic
    - Get current user ID from `UserSession.getInstance().getUserId()`
    - Call `enrollmentService.getEnrollmentStatus(courseId, studentId)` for each card
    - If course is Admin-owned (creatorRole == "ROLE_ADMIN"), show "▶ Start Course" button directly
    - If no enrollment exists, show "Enroll Request" button
    - If status is PENDING, show "⏳ Pending" badge (not a button)
    - If status is ACCEPTED, show "▶ Start Course" button
    - If status is REJECTED, show "❌ Rejected" badge and "Enroll Request" button
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.7_

  - [x] 7.4 Implement batch enrollment status loading
    - Before rendering cards, collect all course IDs
    - Query enrollment statuses in a single batch query (not one per card)
    - Store results in a Map<Integer, String> (courseId → status)
    - Use the map in `buildCard()` to avoid N+1 queries
    - _Requirements: 4.8_

  - [x] 7.5 Implement "Enroll Request" button action
    - Open a simple dialog to collect optional message from student
    - Call `enrollmentService.createRequest(courseId, studentId, message)`
    - Refresh the card to show "⏳ Pending" badge
    - Display success feedback
    - _Requirements: 4.2_

  - [x] 7.6 Implement "▶ Start Course" button action
    - Navigate to CourseDetailController for the selected course
    - Pass the course object to the detail view
    - _Requirements: 4.5_

- [x] 8. Implement CourseAccessGuard in CourseDetailController
  - [x] 8.1 Add access check in `initData()` method
    - Get current user role from `UserSession.getInstance().getRole()`
    - If role is ROLE_STUDENT, check enrollment status using `enrollmentService.getEnrollmentStatus()`
    - If status is not ACCEPTED and course is not Admin-owned, display "Access Denied" alert and close the window
    - Log access denial to System.err
    - _Requirements: 4.6, 8.1, 8.7_

  - [x] 8.2 Update CourseDetailController to display provider label
    - Add a Label in the detail view showing "Provided by [creatorName]" or "Provided by Nova"
    - Use the same logic as UserCourseController
    - _Requirements: 2.4_

- [x] 9. Create TutorCourseController for tutor course management
  - [x] 9.1 Create `controllers/studysession/TutorCourseController.java`
    - Copy structure from `CourseController.java` (card layout, filters, search)
    - Add CourseService and EnrollmentService fields
    - _Requirements: 7.1, 7.2_

  - [x] 9.2 Implement `loadData()` to load only tutor's courses
    - Get current user ID from `UserSession.getInstance().getUserId()`
    - Call `courseService.findByCreator(userId)` instead of `findAll()`
    - Apply filters on the result set
    - _Requirements: 7.4_

  - [x] 9.3 Implement `buildCard()` with provider label and full CRUD buttons
    - Reuse card structure from CourseController
    - Add provider label below title
    - Include Edit, Delete, Publish/Unpublish, and View Plannings buttons
    - _Requirements: 7.7, 2.4_

  - [x] 9.4 Implement "New Course" button action
    - Open CourseFormController with null course
    - Pass current user ID as `createdById` to the form
    - On save, call `courseService.create()` with `createdById` set
    - _Requirements: 7.2, 7.3_

  - [x] 9.5 Implement "Edit Course" button action
    - Open CourseFormController with selected course
    - Verify course.createdById matches current user ID before allowing edit
    - Display "Access Denied" alert if ownership check fails
    - _Requirements: 8.3_

  - [x] 9.6 Implement "Delete Course" button action
    - Check if course has accepted enrollments using `enrollmentService.findByCourse()`
    - If accepted enrollments exist, show confirmation dialog with warning
    - Only proceed with delete on explicit confirmation
    - Call `courseService.delete(courseId)`
    - _Requirements: 7.6_

  - [x] 9.7 Implement "Publish/Unpublish" toggle action
    - Call `courseService.togglePublish(course)`
    - Refresh the card to reflect new published state
    - _Requirements: 7.8_

  - [x] 9.8 Apply form validation rules
    - Reuse existing CourseFormController validation
    - Ensure course name (3–255 chars), category (3+ chars), difficulty, duration, max students validation
    - _Requirements: 7.5_

- [x] 10. Create EnrollmentRequestsController for tutor enrollment management
  - [x] 10.1 Create `controllers/studysession/EnrollmentRequestsController.java`
    - Add EnrollmentService field
    - Add TableView with columns: Student Name, Course Name, Requested At, Actions
    - _Requirements: 5.1, 5.2_

  - [x] 10.2 Implement `loadPendingRequests()` method
    - Get current user ID from `UserSession.getInstance().getUserId()`
    - Call `enrollmentService.findPendingByCreator(userId)`
    - Populate TableView with results
    - _Requirements: 5.2, 5.3, 5.8_

  - [x] 10.3 Add "Accept" button action for each row
    - Call `enrollmentService.acceptRequest(requestId, currentUserId)`
    - Remove the row from the TableView
    - Display success feedback
    - _Requirements: 5.4, 5.5_

  - [x] 10.4 Add "Reject" button action for each row
    - Call `enrollmentService.rejectRequest(requestId, currentUserId)`
    - Remove the row from the TableView
    - Display success feedback
    - _Requirements: 5.4, 5.6_

  - [x] 10.5 Implement empty state display
    - If no pending requests exist, show "No pending enrollment requests." message
    - Hide TableView and show empty state VBox
    - _Requirements: 5.7_

- [x] 11. Create TutorAnalyticsController for tutor analytics view
  - [x] 11.1 Create `controllers/studysession/TutorAnalyticsController.java`
    - Add TutorAnalyticsService field
    - Add Labels for summary metrics: total enrolled, active students, completion rate, total courses
    - _Requirements: 9.1_

  - [x] 11.2 Implement `loadAnalytics()` method
    - Get current user ID from `UserSession.getInstance().getUserId()`
    - Call all TutorAnalyticsService methods to get metrics
    - Populate summary Labels with the data
    - _Requirements: 9.1, 9.3_

  - [x] 11.3 Add per-course breakdown table
    - Add TableView with columns: Course Name, Enrolled Students, Progress Indicator
    - Call `tutorAnalyticsService.getPerCourseBreakdown(userId)`
    - Populate TableView with results
    - _Requirements: 9.2_

  - [x] 11.4 Add "Most Popular Course" highlight card
    - Call `tutorAnalyticsService.getMostPopularCourse(userId)`
    - Display course name and enrollment count in a styled card
    - _Requirements: 9.4_

  - [x] 11.5 Implement empty state for no courses
    - If tutor has no courses, show "No courses yet. Create your first course to see analytics."
    - Hide analytics content and show empty state VBox
    - _Requirements: 9.5_

- [x] 12. Create TutorDashboardController with tab navigation
  - [x] 12.1 Create `controllers/studysession/TutorDashboardController.java`
    - Add TabPane with tabs: "My Courses", "Enrollment Requests", "Analytics", "Study Sessions"
    - Add header with username and avatar (reuse Admin Dashboard header style)
    - _Requirements: 10.1, 10.2, 10.3_

  - [x] 12.2 Load TutorCourseView in "My Courses" tab
    - Embed TutorCourseView.fxml in the tab content
    - _Requirements: 10.5_

  - [x] 12.3 Load EnrollmentRequestsView in "Enrollment Requests" tab
    - Embed EnrollmentRequestsView.fxml in the tab content
    - _Requirements: 10.6_

  - [x] 12.4 Load TutorAnalyticsView in "Analytics" tab
    - Embed TutorAnalyticsView.fxml in the tab content
    - _Requirements: 10.7_

  - [x] 12.5 Load UserStudyDashboard in "Study Sessions" tab
    - Reuse existing UserStudyDashboard.fxml for tutor's own planning and session tracking
    - _Requirements: 10.4_

- [x] 13. Create AdminEnrollmentController for admin enrollment monitoring
  - [x] 13.1 Create `controllers/studysession/AdminEnrollmentController.java`
    - Add EnrollmentService field
    - Add TableView with columns: Student Name, Course Name, Creator Name, Status, Requested At, Actions
    - _Requirements: 6.2, 6.3_

  - [x] 13.2 Implement `loadAllEnrollments()` method
    - Call `enrollmentService.findAllEnrollments()`
    - Populate TableView with all enrollment records
    - _Requirements: 6.2_

  - [x] 13.3 Add filter controls for status and course name search
    - Add ComboBox for status filter (All, PENDING, ACCEPTED, REJECTED)
    - Add TextField for course name search
    - Apply filters on the enrollment list
    - _Requirements: 6.4_

  - [x] 13.4 Add "Accept" and "Reject" buttons for PENDING requests
    - Call `enrollmentService.acceptRequest()` or `rejectRequest()`
    - Refresh the TableView after action
    - _Requirements: 6.5_

  - [x] 13.5 Add aggregate summary cards
    - Display total enrollments, pending count, accepted count, rejected count
    - Calculate from the enrollment list
    - Style as cards at the top of the view
    - _Requirements: 6.6_

- [x] 14. Update LoginController to route ROLE_TUTOR to TutorDashboard
  - [x] 14.1 Modify `routeUserBasedOnRole()` method
    - Add case for `User.Role.ROLE_TUTOR`
    - Load `TutorDashboard.fxml` and set scene
    - Pass current user to TutorDashboardController
    - _Requirements: 10.8, 8.1_

  - [x] 14.2 Implement role-based redirection guards
    - If Student attempts to access Admin or Tutor dashboard, redirect to Student dashboard
    - If Tutor attempts to access Admin dashboard, redirect to Tutor dashboard
    - Log access denial to System.err
    - _Requirements: 8.2, 8.7_

- [x] 15. Create FXML views for all new controllers
  - [x] 15.1 Create `views/studysession/TutorCourseView.fxml`
    - Mirror structure of CourseView.fxml (FlowPane for cards, filters, search, empty state)
    - Apply study.css stylesheet
    - _Requirements: 12.1, 12.2_

  - [x] 15.2 Create `views/studysession/EnrollmentRequestsView.fxml`
    - TableView with columns for student, course, date, actions
    - Empty state VBox
    - Apply study.css stylesheet
    - _Requirements: 12.1, 12.2_

  - [x] 15.3 Create `views/studysession/TutorAnalyticsView.fxml`
    - Summary cards for metrics
    - TableView for per-course breakdown
    - Highlight card for most popular course
    - Empty state VBox
    - Apply study.css stylesheet
    - _Requirements: 12.1, 12.2_

  - [x] 15.4 Create `views/studysession/TutorDashboard.fxml`
    - TabPane with four tabs
    - Header with username and avatar
    - Apply study.css stylesheet
    - _Requirements: 12.1, 12.2_

  - [x] 15.5 Create `views/studysession/AdminEnrollmentView.fxml`
    - Summary cards at top
    - Filter controls (status ComboBox, search TextField)
    - TableView for enrollment records
    - Apply study.css stylesheet
    - _Requirements: 12.1, 12.2_

  - [x] 15.6 Update `views/studysession/CourseDetail.fxml` to add provider label
    - Add Label for "Provided by [name]" below course title
    - Style consistently with other views
    - _Requirements: 2.4, 12.1_

- [x] 16. Apply UI consistency and validation rules
  - [x] 16.1 Verify all new cards use the same structure
    - Difficulty color strip, badge row, meta row, progress bar, footer buttons
    - Match UserCourseController.buildCard() pattern
    - _Requirements: 12.2_

  - [x] 16.2 Add inline validation error labels to all new forms
    - Red border (#ef4444) on invalid fields
    - Error message below field
    - Do not close form on validation error
    - _Requirements: 12.3, 12.4_

  - [x] 16.3 Add success feedback to all create/update/accept/reject actions
    - Green status label (#22c55e) on success
    - Display success message for 3 seconds
    - _Requirements: 12.5_

  - [x] 16.4 Verify all buttons follow style conventions
    - Background color, text color, border radius 8, cursor hand, padding 5 12, font size 11px
    - _Requirements: 12.6_

- [x] 17. Implement database integrity checks and cascade rules
  - [x] 17.1 Verify UNIQUE constraint on enrollment_request(course_id, student_id)
    - Check database schema
    - Add constraint if missing using ALTER TABLE
    - _Requirements: 3.2_

  - [x] 17.2 Verify ON DELETE CASCADE on enrollment_request foreign keys
    - Check course_id and student_id foreign keys
    - Ensure CASCADE is set for both
    - _Requirements: 11.1, 11.2_

  - [x] 17.3 Verify ON DELETE SET NULL on course.created_by_id
    - Check foreign key constraint
    - Ensure SET NULL is configured
    - _Requirements: 11.4_

  - [x] 17.4 Update CourseService.delete() to check for accepted enrollments
    - Query enrollment_request for ACCEPTED status before delete
    - Throw SQLException with descriptive message if accepted enrollments exist
    - _Requirements: 11.3_

  - [x] 17.5 Wrap acceptRequest and rejectRequest in transactions
    - Use Connection.setAutoCommit(false) and commit/rollback
    - Ensure atomicity of status, responded_at, and responded_by_id updates
    - _Requirements: 11.5_

- [ ] 18. Implement role-based access control enforcement
  - [ ] 18.1 Add role check to all protected controller actions
    - Read role from `UserSession.getInstance().getRole()`
    - Verify role matches expected role for the action
    - _Requirements: 8.1_

  - [ ] 18.2 Hide edit/delete/publish buttons in UserCourseController
    - Ensure Student view only shows "View Details" and "Plan Session" buttons
    - _Requirements: 8.5_

  - [ ] 18.3 Hide "Enrollment Requests" tab from Admin and Student users
    - Only show tab when role is ROLE_TUTOR
    - _Requirements: 8.6_

  - [ ] 18.4 Add ownership check to TutorCourseController edit/delete actions
    - Verify course.createdById == currentUserId before allowing action
    - Display "Access Denied" alert if check fails
    - Log access denial to System.err
    - _Requirements: 8.3, 8.7_

  - [ ] 18.5 Allow Admin unrestricted access to all courses and enrollments
    - Skip ownership checks for ROLE_ADMIN
    - _Requirements: 8.4_

- [ ] 19. Final checkpoint - Integration testing and verification
  - Test Student enrollment workflow: request → pending → accepted → start course
  - Test Tutor course creation, enrollment management, and analytics
  - Test Admin enrollment monitoring and override capabilities
  - Verify role-based routing: Student → Student Dashboard, Tutor → Tutor Dashboard, Admin → Admin Dashboard
  - Verify access control: Students cannot access Tutor/Admin views, Tutors cannot edit other tutors' courses
  - Verify provider labels display correctly on all course cards and detail pages
  - Verify database integrity: cascade deletes, unique constraints, transactions
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- All tasks reference specific requirements for traceability
- Checkpoints ensure incremental validation at key milestones
- The implementation follows a layered approach: models → services → controllers → views → integration
- No property-based tests are included because this feature involves UI rendering, database CRUD, and role-based access control, which are better tested with integration tests
- All new views reuse the existing `study.css` stylesheet for UI consistency
- The implementation preserves all existing functionality and does not modify core authentication, database schema (except reading existing columns), or theme
