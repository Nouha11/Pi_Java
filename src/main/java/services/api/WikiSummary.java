package services.api;

public class WikiSummary {
    private final String title;
    private final String extract;
    private final String thumbnailUrl;   // nullable
    private final String pageUrl;

    public WikiSummary(String title, String extract, String thumbnailUrl, String pageUrl) {
        this.title = title;
        this.extract = extract;
        this.thumbnailUrl = thumbnailUrl;
        this.pageUrl = pageUrl;
    }

    public String getTitle() { return title; }
    public String getExtract() { return extract; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getPageUrl() { return pageUrl; }
}
