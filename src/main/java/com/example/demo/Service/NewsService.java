package com.example.demo.Service;


import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NewsService {

    private final StringRedisTemplate redisTemplate;
    private final AIService aiService;

    // 获取某条新闻的生命周期：点击+曝光按小时
    public Map<String, Map<String, Long>> getNewsLifecycleRange(String newsId, String startDate, String endDate) {
        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter hourFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH");

        LocalDate start = LocalDate.parse(startDate, dateFormatter);
        LocalDate end = LocalDate.parse(endDate, dateFormatter);

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            for (int hour = 0; hour < 24; hour++) {
                String hourKey = date.atTime(hour, 0).format(hourFormatter); // e.g. 2025-06-05T14

                long click = getZSetScore("news:time:click:" + hourKey, newsId);
                long expose = getZSetScore("news:time:expose:" + hourKey, newsId);

                Map<String, Long> hourStats = new HashMap<>();
                hourStats.put("click", click);
                hourStats.put("expose", expose);

                result.put(hourKey, hourStats);
            }
        }

        return result;
    }

    // 类别点击/曝光趋势
    public Map<String, Map<String, Long>> getCategoryStats(String category, String startDate, String endDate) {
        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        LocalDate start = LocalDate.parse(startDate, dateFormatter);
        LocalDate end = LocalDate.parse(endDate, dateFormatter);

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            String day = date.format(dateFormatter); // yyyy-MM-dd

            String clickKey = "category:click:" + day;
            String exposeKey = "category:expose:" + day;

            long click = getZSetScore(clickKey, category);
            long expose = getZSetScore(exposeKey, category);

            Map<String, Long> dayStats = new HashMap<>();
            dayStats.put("click", click);
            dayStats.put("expose", expose);

            result.put(day, dayStats);
        }

        return result;
    }

    // 获取用户兴趣快照
    public Map<String, Double> getUserInterest(String userId, String startDate, String endDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate start = LocalDate.parse(startDate, formatter);
        LocalDate end = LocalDate.parse(endDate, formatter);

        Map<String, Double> totalInterest = new HashMap<>();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            String dayKey = "user:interest:" + userId + ":" + date.format(formatter);
            Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                    .rangeWithScores(dayKey, 0, -1);

            if (tuples != null) {
                for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                    String newsId = tuple.getValue();
                    Double score = tuple.getScore();
                    if (newsId != null && score != null) {
                        totalInterest.merge(newsId, score, Double::sum);
                    }
                }
            }
        }

        return totalInterest;
    }

    // 热点新闻排行
    public Map<String, Double> getHotNews() {
        return getZSetAsMap("news:rank:click");
    }

    // 推荐
    public Map<String, Double> recommendNews(String userId) {
        return aiService.recommendBasedOnUserInterest(userId);
    }

    private Map<String, Double> getZSetAsMap(String key) {
        Map<String, Double> map = new LinkedHashMap<>();
        redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, 9).forEach(score -> {
            if (score != null && score.getScore() != null) {
                map.put(score.getValue(), score.getScore());
            }
        });
        return map;
    }

    private long getZSetScore(String key, String member) {
        Double score = redisTemplate.opsForZSet().score(key, member);
        return (score != null) ? score.longValue() : 0L;
    }
}

