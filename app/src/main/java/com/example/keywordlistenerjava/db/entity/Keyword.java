package com.example.keywordlistenerjava.db.entity;

public class Keyword {
    private int keywordId;
    private String keywordText;
    private Integer userId; // Use Integer wrapper to allow null
    private String ppnFileName;
    private boolean isDefault;
    private String addedDate; // Stored as TEXT in SQLite

    // Constructors
    public Keyword() {
    }

    // Getters and Setters
    public int getKeywordId() {
        return keywordId;
    }

    public void setKeywordId(int keywordId) {
        this.keywordId = keywordId;
    }

    public String getKeywordText() {
        return keywordText;
    }

    public void setKeywordText(String keywordText) {
        this.keywordText = keywordText;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) { // Accepts null
        this.userId = userId;
    }

    public String getPpnFileName() {
        return ppnFileName;
    }

    public void setPpnFileName(String ppnFileName) {
        this.ppnFileName = ppnFileName;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public String getAddedDate() {
        return addedDate;
    }

    public void setAddedDate(String addedDate) {
        this.addedDate = addedDate;
    }
}