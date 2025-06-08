package com.example.demo.Model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Impression {
    private String start;
    private String end;
    private List<Clicked> clicked;
    private List<String> not_clicked;
    public String getStart() {
        return start;
    }
    public void setStart(String start) {
        this.start = start;
    }
    public String getEnd() {
        return end;
    }
    public void setEnd(String end) {
        this.end = end;
    }
    public List<Clicked> getClicked() {
        return clicked;
    }
    public void setClicked(List<Clicked> clicked) {
        this.clicked = clicked;
    }
    public List<String> getNot_clicked() {
        return not_clicked;
    }
    public void setNot_clicked(List<String> not_clicked) {
        this.not_clicked = not_clicked;
    }

}
