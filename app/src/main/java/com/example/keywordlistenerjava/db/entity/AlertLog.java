package com.example.keywordlistenerjava.db.entity;

public class AlertLog {
    private int logId;
    private int userId;
    private String keywordUsed;
    private String alertDate; // Stored as TEXT (YYYY-MM-DD)
    private String alertTime; // Stored as TEXT (HH:MM:SS)
    private double latitude;
    private double longitude;
    private String mapLink;
    private Boolean isFalseAlarm; // Boolean wrapper for NULL, true, false

    // Constructors
    public AlertLog() {
    }

    // Getters and Setters
    public int getLogId() {
        return logId;
    }

    public void setLogId(int logId) {
        this.logId = logId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getKeywordUsed() {
        return keywordUsed;
    }

    public void setKeywordUsed(String keywordUsed) {
        this.keywordUsed = keywordUsed;
    }

    public String getAlertDate() {
        return alertDate;
    }

    public void setAlertDate(String alertDate) {
        this.alertDate = alertDate;
    }

    public String getAlertTime() {
        return alertTime;
    }

    public void setAlertTime(String alertTime) {
        this.alertTime = alertTime;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getMapLink() {
        return mapLink;
    }

    public void setMapLink(String mapLink) {
        this.mapLink = mapLink;
    }

    public Boolean getIsFalseAlarm() {
        return isFalseAlarm;
    }

    public void setIsFalseAlarm(Boolean falseAlarm) {
        isFalseAlarm = falseAlarm;
    }
}