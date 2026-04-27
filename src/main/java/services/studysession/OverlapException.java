package services.studysession;

import java.time.LocalDateTime;

/**
 * Checked exception thrown when a calendar event reschedule or resize operation
 * would create a time overlap with an existing event for the same user.
 *
 * Requirements: 12.2, 16.3
 */
public class OverlapException extends Exception {

    private final String conflictingEventTitle;
    private final LocalDateTime conflictStart;
    private final LocalDateTime conflictEnd;

    /**
     * Constructs an OverlapException with details about the conflicting event.
     *
     * @param conflictingEventTitle the title of the existing event that conflicts
     * @param conflictStart         the start time of the conflicting event
     * @param conflictEnd           the end time of the conflicting event
     */
    public OverlapException(String conflictingEventTitle,
                            LocalDateTime conflictStart,
                            LocalDateTime conflictEnd) {
        super(String.format(
                "Time slot conflicts with existing event \"%s\" (%s – %s)",
                conflictingEventTitle, conflictStart, conflictEnd
        ));
        this.conflictingEventTitle = conflictingEventTitle;
        this.conflictStart = conflictStart;
        this.conflictEnd = conflictEnd;
    }

    /**
     * Returns the title of the event that caused the conflict.
     *
     * @return conflicting event title
     */
    public String getConflictingEventTitle() {
        return conflictingEventTitle;
    }

    /**
     * Returns the start time of the conflicting event.
     *
     * @return conflict start time
     */
    public LocalDateTime getConflictStart() {
        return conflictStart;
    }

    /**
     * Returns the end time of the conflicting event.
     *
     * @return conflict end time
     */
    public LocalDateTime getConflictEnd() {
        return conflictEnd;
    }
}
