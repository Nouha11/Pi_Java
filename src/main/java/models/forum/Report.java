package models.forum;

import java.sql.Timestamp;

public class Report {
    private int id;
    private String reason;
    private Timestamp createdAt;
    private Integer postId;
    private int reporterId;
    private Integer commentId;

    // 🔥 HELPER FIELDS (For UI Display)
    private String reporterName;
    private String postTitle;
    private String commentContent; // 🔥 Added for comments
    private String status = "pending";

    public Report() {}

    public static Report createPostReport(int reporterId, int postId, String reason) {
        Report r = new Report();
        r.reporterId = reporterId;
        r.postId = postId;
        r.reason = reason;
        r.createdAt = new Timestamp(System.currentTimeMillis());
        return r;
    }

    public static Report createCommentReport(int reporterId, int commentId, String reason) {
        Report r = new Report();
        r.reporterId = reporterId;
        r.commentId = commentId;
        r.reason = reason;
        r.createdAt = new Timestamp(System.currentTimeMillis());
        return r;
    }

    // Standard Getters/Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Integer getPostId() { return postId; }
    public void setPostId(Integer postId) { this.postId = postId; }
    public int getReporterId() { return reporterId; }
    public void setReporterId(int reporterId) { this.reporterId = reporterId; }
    public Integer getCommentId() { return commentId; }
    public void setCommentId(Integer commentId) { this.commentId = commentId; }

    // 🔥 HELPER GETTERS/SETTERS
    public String getReporterName() { return reporterName != null ? reporterName : "User #" + reporterId; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }

    public String getPostTitle() { return postTitle != null ? postTitle : "Unknown Post"; }
    public void setPostTitle(String postTitle) { this.postTitle = postTitle; }

    public String getCommentContent() { return commentContent != null ? commentContent : "Unknown Comment"; }
    public void setCommentContent(String commentContent) { this.commentContent = commentContent; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // 🔥 Determines if this report is for a comment or a post
    public boolean isCommentReport() {
        return commentId != null && commentId > 0;
    }
}