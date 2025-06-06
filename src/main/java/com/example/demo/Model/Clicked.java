package com.example.demo.Model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Clicked {
    private String news_id;
    private int dwell;
    public String getNews_id() {
        return news_id;
    }
    public void setNews_id(String news_id) {
        this.news_id = news_id;
    }
    public int getDwell() {
        return dwell;
    }
    public void setDwell(int dwell) {
        this.dwell = dwell;
    }
}
