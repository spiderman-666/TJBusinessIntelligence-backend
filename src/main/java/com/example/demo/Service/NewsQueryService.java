package com.example.demo.Service;

import com.example.demo.Model.News;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NewsQueryService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<News> queryNewsByCategoryAndTopic(String category, String topic, String excludeNewsId, int limit) {

        String tableName = "news_" + category.toLowerCase();
        String sql = "SELECT * FROM " + tableName + " WHERE topic = ? AND news_id <> ? LIMIT ?";

        return jdbcTemplate.query(
                sql,
                new Object[]{topic, excludeNewsId, limit},
                new BeanPropertyRowMapper<>(News.class)
        );
    }

}

