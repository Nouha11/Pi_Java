package models.library;

import java.sql.Timestamp;
import java.util.UUID;

public class Payment {

    public static final String STATUS_PENDING   = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED    = "FAILED";
    public static final String STATUS_REFUNDED  = "REFUNDED";

    public static final String METHOD_CARD   = "credit_card";
    public static final String METHOD_PAYPAL = "paypal";

    private int id;
    private int userId;
    private int bookId;
    private double amount;
    private String status;
    private String paymentMethod;
    private String transactionId;
    private String cardLastFour;
    private String cardHolderName;
    private String failureReason;
    private Timestamp createdAt;
    private Timestamp completedAt;

    // Cache fields for display
    private String bookTitle;
    private String userName;

    public Payment() {
        this.status = STATUS_PENDING;
        this.createdAt = new Timestamp(System.currentTimeMillis());
        this.transactionId = UUID.randomUUID().toString().substring(0, 16).toUpperCase();
    }

    public boolean isCompleted() { return STATUS_COMPLETED.equals(status); }
    public boolean isFailed()    { return STATUS_FAILED.equals(status); }

    // Getters & Setters
    public int getId()                          { return id; }
    public void setId(int id)                   { this.id = id; }
    public int getUserId()                      { return userId; }
    public void setUserId(int userId)           { this.userId = userId; }
    public int getBookId()                      { return bookId; }
    public void setBookId(int bookId)           { this.bookId = bookId; }
    public double getAmount()                   { return amount; }
    public void setAmount(double amount)        { this.amount = amount; }
    public String getStatus()                   { return status; }
    public void setStatus(String status)        { this.status = status; }
    public String getPaymentMethod()            { return paymentMethod; }
    public void setPaymentMethod(String m)      { this.paymentMethod = m; }
    public String getTransactionId()            { return transactionId; }
    public void setTransactionId(String t)      { this.transactionId = t; }
    public String getCardLastFour()             { return cardLastFour; }
    public void setCardLastFour(String c)       { this.cardLastFour = c; }
    public String getCardHolderName()           { return cardHolderName; }
    public void setCardHolderName(String n)     { this.cardHolderName = n; }
    public String getFailureReason()            { return failureReason; }
    public void setFailureReason(String r)      { this.failureReason = r; }
    public Timestamp getCreatedAt()             { return createdAt; }
    public void setCreatedAt(Timestamp t)       { this.createdAt = t; }
    public Timestamp getCompletedAt()           { return completedAt; }
    public void setCompletedAt(Timestamp t)     { this.completedAt = t; }
    public String getBookTitle()                { return bookTitle; }
    public void setBookTitle(String t)          { this.bookTitle = t; }
    public String getUserName()                 { return userName; }
    public void setUserName(String n)           { this.userName = n; }

    @Override
    public String toString() {
        return "Payment{id=" + id + ", amount=" + amount + ", status='" + status + "'}";
    }
}
