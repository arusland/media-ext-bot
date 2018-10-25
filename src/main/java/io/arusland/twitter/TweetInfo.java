package io.arusland.twitter;

public class TweetInfo {
    public final String bearerToken;
    public final String tweetId;

    public TweetInfo(String bearerToken, String tweetId) {
        this.bearerToken = bearerToken;
        this.tweetId = tweetId;
    }

    @Override
    public String toString() {
        return "TweetInfo{" +
                "bearerToken='" + bearerToken + '\'' +
                ", tweetId='" + tweetId + '\'' +
                '}';
    }
}