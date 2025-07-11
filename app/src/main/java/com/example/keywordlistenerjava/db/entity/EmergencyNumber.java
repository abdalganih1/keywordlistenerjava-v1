package com.example.keywordlistenerjava.db.entity;

public class EmergencyNumber {
    private int numberId;
    private String phoneNumber;
    private String numberDescription;
    private Integer userId; // Use Integer wrapper to allow null
    private boolean isDefault;
    private String addedDate; // Stored as TEXT in SQLite

    // Constructors
    public EmergencyNumber() {
    }

    // Getters and Setters
    public int getNumberId() {
        return numberId;
    }

    public void setNumberId(int numberId) {
        this.numberId = numberId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getNumberDescription() {
        return numberDescription;
    }

    public void setNumberDescription(String numberDescription) {
        this.numberDescription = numberDescription;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) { // Accepts null
        this.userId = userId;
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