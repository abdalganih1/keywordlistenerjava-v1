package com.example.keywordlistenerjava.db.entity;

public class KeywordNumberLink {
    private int linkId;
    private int keywordId;
    private int numberId;
    private int userId; // The user who owns this specific link
    private boolean isActive; // Whether this link is currently active
    private String createdAt; // Stored as TEXT in SQLite

    // Constructors
    public KeywordNumberLink() {
    }

    // Getters and Setters
    public int getLinkId() {
        return linkId;
    }

    public void setLinkId(int linkId) {
        this.linkId = linkId;
    }

    public int getKeywordId() {
        return keywordId;
    }

    public void setKeywordId(int keywordId) {
        this.keywordId = keywordId;
    }

    public int getNumberId() {
        return numberId;
    }

    public void setNumberId(int numberId) {
        this.numberId = numberId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}