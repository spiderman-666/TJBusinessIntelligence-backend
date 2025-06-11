package com.example.demo.Service;

import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DataService {
    private final JdbcTemplate primaryJdbcTemplate;
    private final JdbcTemplate secondJdbcTemplate;

    public DataService(
            @Qualifier("primaryJdbcTemplate") JdbcTemplate primaryJdbcTemplate,
            @Qualifier("secondJdbcTemplate") JdbcTemplate secondJdbcTemplate
    ) {
        this.primaryJdbcTemplate = primaryJdbcTemplate;
        this.secondJdbcTemplate = secondJdbcTemplate;
    }

    public void queryFromPrimary() {
        List<Map<String, Object>> result = primaryJdbcTemplate.queryForList("SELECT * FROM table1");
        // 处理结果...
    }

    public void queryFromSecond() {
        List<Map<String, Object>> result = secondJdbcTemplate.queryForList("SELECT * FROM table2");
        // 处理结果...
    }
    public Map<String, Object> getNewsByIdInFirstDatabase(String newsId) {
        String sql = "SELECT * FROM news WHERE news_id = ?";
        List<Map<String, Object>> results = primaryJdbcTemplate.queryForList(sql, newsId);
        return results.isEmpty() ? null : results.get(0);
    }
    // 新增：根据 ID 查询第二数据库中的新闻内容
    public Map<String, Object> getNewsByIdInSecondDatabase(String newsId) {
        String sql = "SELECT * FROM news WHERE news_id = ?";
        List<Map<String, Object>> results = secondJdbcTemplate.queryForList(sql, newsId);
        return results.isEmpty() ? null : results.get(0);
    }

}
