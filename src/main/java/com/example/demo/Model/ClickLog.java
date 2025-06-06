package com.example.demo.Model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClickLog {
    private String timestamp;
    private String user_id;
    private List<ClickHistory> click_history;
    private Impression impression;

    // Getters and Setters
    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getUser_id() {
        return user_id;
    }

    public void setUser_id(String user_id) {
        this.user_id = user_id;
    }

    public List<ClickHistory> getClick_history() {
        return click_history;
    }

    public void setClick_history(List<ClickHistory> click_history) {
        this.click_history = click_history;
    }

    public Impression getImpression() {
        return impression;
    }
    public void setImpression(Impression impression) {
        this.impression = impression;
    }

}
