package models.library;

import java.sql.Timestamp;

public class Loan {

    public static final String STATUS_PENDING  = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_ACTIVE   = "ACTIVE";
    public static final String STATUS_RETURNED = "RETURNED";
    public static final String STATUS_OVERDUE  = "OVERDUE";

    private int id;
    private int userId;
    private int bookId;
    private int libraryId;
    private String status;
    private Timestamp requestedAt;
    private Timestamp approvedAt;
    private Timestamp startAt;
    private Timestamp endAt;
    private String rejectionReason;

    // Cache fields for display (avoids extra queries in UI)
    private String bookTitle;
    private String userName;
    private String libraryName;

    public Loan() {
        this.status = STATUS_PENDING;
        this.requestedAt = new Timestamp(System.currentTimeMillis());
    }

    public boolean isOverdue() {
        return STATUS_ACTIVE.equals(status)
                && endAt != null
                && new Timestamp(System.currentTimeMillis()).after(endAt);
    }

    // Getters & Setters
    public int getId()                          { return id; }
    public void setId(int id)                   { this.id = id; }
    public int getUserId()                      { return userId; }
    public void setUserId(int userId)           { this.userId = userId; }
    public int getBookId()                      { return bookId; }
    public void setBookId(int bookId)           { this.bookId = bookId; }
    public int getLibraryId()                   { return libraryId; }
    public void setLibraryId(int libraryId)     { this.libraryId = libraryId; }
    public String getStatus()                   { return status; }
    public void setStatus(String status)        { this.status = status; }
    public Timestamp getRequestedAt()           { return requestedAt; }
    public void setRequestedAt(Timestamp t)     { this.requestedAt = t; }
    public Timestamp getApprovedAt()            { return approvedAt; }
    public void setApprovedAt(Timestamp t)      { this.approvedAt = t; }
    public Timestamp getStartAt()               { return startAt; }
    public void setStartAt(Timestamp t)         { this.startAt = t; }
    public Timestamp getEndAt()                 { return endAt; }
    public void setEndAt(Timestamp t)           { this.endAt = t; }
    public String getRejectionReason()          { return rejectionReason; }
    public void setRejectionReason(String r)    { this.rejectionReason = r; }
    public String getBookTitle()                { return bookTitle; }
    public void setBookTitle(String t)          { this.bookTitle = t; }
    public String getUserName()                 { return userName; }
    public void setUserName(String n)           { this.userName = n; }
    public String getLibraryName()              { return libraryName; }
    public void setLibraryName(String n)        { this.libraryName = n; }

    @Override
    public String toString() {
        return "Loan{id=" + id + ", book='" + bookTitle + "', status='" + status + "'}";
    }
}
