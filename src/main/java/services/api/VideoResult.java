package services.api;

public class VideoResult {
    private final String videoId;
    private final String title;
    private final String channelName;
    private final String thumbnailUrl;

    public VideoResult(String videoId, String title, String channelName, String thumbnailUrl) {
        this.videoId = videoId;
        this.title = title;
        this.channelName = channelName;
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getVideoId() { return videoId; }
    public String getTitle() { return title; }
    public String getChannelName() { return channelName; }
    public String getThumbnailUrl() { return thumbnailUrl; }
}
