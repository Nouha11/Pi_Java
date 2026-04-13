package models.library;

public class Book  {
    private int id;
    private String title;
    private String author;
    private String isbn;
    private double price;
    private String type; // "physical" or "digital"
    private String coverImage;
    private String pdfUrl;

    public Book() {}

    public Book(int id, String title, String author, String isbn, double price, String type) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.price = price;
        this.type = type;
    }

    public boolean isDigital() {
        return "digital".equalsIgnoreCase(this.type);
    }

    // Getters & Setters
    public int getId()                        { return id; }
    public void setId(int id)                 { this.id = id; }
    public String getTitle()                  { return title; }
    public void setTitle(String title)        { this.title = title; }
    public String getAuthor()                 { return author; }
    public void setAuthor(String author)      { this.author = author; }
    public String getIsbn()                   { return isbn; }
    public void setIsbn(String isbn)          { this.isbn = isbn; }
    public double getPrice()                  { return price; }
    public void setPrice(double price)        { this.price = price; }
    public String getType()                   { return type; }
    public void setType(String type)          { this.type = type; }
    public String getCoverImage()             { return coverImage; }
    public void setCoverImage(String c)       { this.coverImage = c; }
    public String getPdfUrl()                 { return pdfUrl; }
    public void setPdfUrl(String pdfUrl)      { this.pdfUrl = pdfUrl; }

    @Override
    public String toString() {
        return "Book{id=" + id + ", title='" + title + "', author='" + author + "', type='" + type + "'}";
    }
}

