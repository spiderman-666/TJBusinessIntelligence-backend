package com.example.demo.Service;


import com.example.demo.Model.News;
import com.example.demo.Repository.NewsRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NewsService {

    private final StringRedisTemplate redisTemplate;
    private final AIService aiService;
    private final NewsRepository newsRepository;
    private final DataService dataService;

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

    // 组合查询
    public List<News> queryByConditionsOptimized(
            List<String> userIds, String startDate, String endDate,
            String topic, Integer minLength, Integer maxLength,
            Integer minHeadlineLength, Integer maxHeadlineLength
    ) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate start = (startDate != null && !startDate.trim().isEmpty())
                ? LocalDate.parse(startDate, formatter)
                : null;

        LocalDate end = (endDate != null && !endDate.trim().isEmpty())
                ? LocalDate.parse(endDate, formatter)
                : null;
        Set<String> newsIds = new HashSet<>();

        if (userIds != null && !userIds.isEmpty()) {
            for (String userId : userIds) {
                for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                    String key = "user:interest:" + userId + ":" + date.format(formatter);
                    Set<String> ids = redisTemplate.opsForZSet().range(key, 0, -1);
                    if (ids != null) newsIds.addAll(ids);
                }
            }
            return newsRepository.queryNewsById(topic, minLength, maxLength, minHeadlineLength, maxHeadlineLength, new ArrayList<>(newsIds));
        } else {
            // 查询所有新闻 ID 从 Redis 判断时间
            List<News> result = new ArrayList<>();
            List<News> all = newsRepository.queryNews(topic, minLength, maxLength, minHeadlineLength, maxHeadlineLength);
            for (News news : all) {
                Map<String, Object> newsContent = dataService.getNewsByIdInSecondDatabase(news.getNewsId());
                if (newsContent != null && newsContent.get("newsbody") != null) {
                    news.setNewsbody(newsContent.get("newsbody").toString());
                }
                String record = redisTemplate.opsForValue().get("news:time:record:" + news.getNewsId());
                if (record == null || record.length() < 10) {
                    newsIds.add(news.getNewsId());
                    result.add(news);
                    continue;
                }
                LocalDate recordDate = LocalDate.parse(record.substring(0, 10), formatter);
                if (start != null && recordDate.isBefore(start)) continue;
                if (end != null && recordDate.isAfter(end)) continue;
                newsIds.add(news.getNewsId());
                result.add(news);
            }
            return result;
        }
    }


    // 热点新闻排行
    public Map<String, Double> getHotNews() {
        return getZSetAsMap("news:rank:click");
    }

    // 推荐
    public Map<String, Object> recommendNews(String userId) {
        String key = "user:recommend:" + userId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isEmpty()) {
            return Map.of("error", "No recommendation found for user: " + userId);
        }

        try {
            // 示例 JSON: { "ai": "...", "hot": ["n001", "n002", ...] }
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("error", "Failed to parse recommendation", "exception", e.getMessage());
        }
        // return aiService.recommendBasedOnUserInterest(userId);
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

    // 通过新闻ID查找新闻
    public Map<String, Object> getNews(String id) {
        Map<String, Object> first = dataService.getNewsByIdInFirstDatabase(id);
        Map<String, Object> second = dataService.getNewsByIdInSecondDatabase(id);
        // 如果两个结果都为空，返回 null
        if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
            return null;
        }

        // 创建一个新 Map 用于合并结果
        Map<String, Object> merged = new HashMap<>();

        // 先加入主库内容
        if (first != null) {
            merged.putAll(first);
        }

        // 用副库内容补充缺失字段，但不覆盖已有字段
        if (second != null) {
            for (Map.Entry<String, Object> entry : second.entrySet()) {
                merged.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        return merged;
    }
}

