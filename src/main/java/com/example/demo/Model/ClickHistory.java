package com.example.demo.Model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ClickHistory {
    private String news_id;         // 新闻ID
    private int dwell;              // 停留时间
    private String exposureTime;


    // Getters and Setters
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

    public String getExposureTime() {
        return exposureTime;
    }

    public void setExposureTime(String exposure_time) {
        this.exposureTime = exposure_time;
    }
}
