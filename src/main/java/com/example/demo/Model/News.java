package com.example.demo.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name="news")
public class News {
    @Id
    @Column(name = "news_id")
    private String newsId;

    @Column(name="Category")
    private String category;

    @Column(name="Topic")
    private String topic;

    @Column(name="Headline")
    private String headline;

    private Integer dwell;
    private LocalDateTime exposure_time;
    @Column(name = "Length")
    private Integer length;
}
