package com.example.demo.Service;


import com.example.demo.Model.News;
import com.example.demo.Model.Query;
import com.example.demo.Repository.NewsQueryRepository;
import com.example.demo.Repository.NewsRepository;
import com.example.demo.Repository.QueryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import jakarta.persistence.criteria.Predicate;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NewsService {

    private final StringRedisTemplate redisTemplate;
    private final NewsRepository newsRepository;
    private final DataService dataService;
    private final QueryRepository queryRepository;
    private final NewsQueryRepository newsQueryRepository;

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

        // 按分数降序排序并返回有序 LinkedHashMap
        return totalInterest.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(
                        LinkedHashMap::new,
                        (m, e) -> m.put(e.getKey(), e.getValue()),
                        LinkedHashMap::putAll
                );
    }

    // 组合查询
    public List<Map<String, Object>> queryByConditionsOptimized(
            List<String> userIds, String startDate, String endDate,
            String topic, Integer minLength, Integer maxLength,
            Integer minHeadlineLength, Integer maxHeadlineLength
    ) {
        // 处理空字符串转 null
        if (topic != null && topic.trim().isEmpty()) {
            topic = null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate start = (startDate != null && !startDate.trim().isEmpty())
                ? LocalDate.parse(startDate, formatter)
                : null;

        LocalDate end = (endDate != null && !endDate.trim().isEmpty())
                ? LocalDate.parse(endDate, formatter)
                : null;

        Set<String> newsIds = new HashSet<>();
        List<News> result = new ArrayList<>();
        List<Map<String, Object>> answer = new ArrayList<>();

        Pageable limit100 = PageRequest.of(0, 100);   // 只要第一页，大小 100

        // 用户ID存在，且start/end不为null时才进行基于Redis兴趣的查询
        if (userIds != null && !userIds.isEmpty() && start != null && end != null) {
            for (String userId : userIds) {
                for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                    String key = "user:interest:" + userId + ":" + date.format(formatter);
                    Set<String> ids = redisTemplate.opsForZSet().range(key, 0, -1);
                    if (ids != null) newsIds.addAll(ids);
                }
            }

            long begin = System.currentTimeMillis();
            LocalDateTime localDateTime = LocalDateTime.now();

            result = newsRepository.queryNewsById(
                    topic, minLength, maxLength, minHeadlineLength, maxHeadlineLength, new ArrayList<>(newsIds), limit100
            );

            long time = System.currentTimeMillis() - begin;
            String safeTopic = (topic != null) ? URLEncoder.encode(topic, StandardCharsets.UTF_8) : "null";
            String safeNewsIds = (newsIds != null && !newsIds.isEmpty())
                    ? URLEncoder.encode(String.join(",", newsIds), StandardCharsets.UTF_8)
                    : "null";

            String queryParam = String.format(
                    "topic=%s&minLength=%s&maxLength=%s&minHeadlineLength=%s&maxHeadlineLength=%s&newsIds=%s",
                    safeTopic,
                    String.valueOf(minLength),
                    String.valueOf(maxLength),
                    String.valueOf(minHeadlineLength),
                    String.valueOf(maxHeadlineLength),
                    safeNewsIds
            );


            Query query = new Query(localDateTime, "/api/news/query", "queryNewsById",
                    queryParam, time, result.size(), "200", null, "news", begin);
            queryRepository.save(query);
        } else {
            // 非用户兴趣查询模式：全库筛选 newsId + Redis 判断时间范围
            long begin = System.currentTimeMillis();
            LocalDateTime localDateTime = LocalDateTime.now();

            // 第一步：只查询符合 topic、length 等筛选条件的 newsId（不查整个 News 实体）
            // 你需要实现一个类似 findNewsIdsByConditions() 的方法，只查字段而不是对象（优化性能）
            List<News> allNews = newsRepository.queryNews(
                    topic, minLength, maxLength, minHeadlineLength, maxHeadlineLength, limit100
            );

            long time = System.currentTimeMillis() - begin;
            String safeTopic = (topic != null) ? URLEncoder.encode(topic, StandardCharsets.UTF_8) : "null";
            String safeNewsIds = (newsIds != null && !newsIds.isEmpty())
                    ? URLEncoder.encode(String.join(",", newsIds), StandardCharsets.UTF_8)
                    : "null";

            String queryParam = String.format(
                    "topic=%s&minLength=%s&maxLength=%s&minHeadlineLength=%s&maxHeadlineLength=%s&newsIds=%s",
                    safeTopic,
                    String.valueOf(minLength),
                    String.valueOf(maxLength),
                    String.valueOf(minHeadlineLength),
                    String.valueOf(maxHeadlineLength),
                    safeNewsIds
            );

            // 第二步：记录日志
            Query query = new Query(localDateTime, "/api/news/query", queryParam,
                    "newsId", time, allNews.size(), "200", null, "news", begin);
            queryRepository.save(query);

            // 第三步：遍历每个 newsId，检查 Redis 中的发布时间是否符合筛选条件
            for (News news : allNews) {
                //if (result.size() >= 100) break;  // 加入数量上限控制
                String newsId = news.getNewsId();

                // 获取 Redis 中记录的发布时间（如：2025-06-10T12:34:56）
                String record = redisTemplate.opsForValue().get("news:time:record:" + newsId);

                // 如果 Redis 中没有时间，或格式太短，直接通过
                if (record == null || record.length() < 10) {
                    result.add(news);
                    continue;
                }

                LocalDate recordDate;
                try {
                    recordDate = LocalDate.parse(record.substring(0, 10), formatter);
                } catch (Exception e) {
                    // 日期格式解析错误，跳过
                    continue;
                }

                // 判断是否在时间范围内
                if (start != null && recordDate.isBefore(start)) continue;
                if (end != null && recordDate.isAfter(end)) continue;

                result.add(news);
            }

        }
        for (News news : result) {
            Map<String, Object> map = new HashMap<>();
            map.put("news_id", news.getNewsId());
            map.put("Topic", news.getTopic());
            map.put("Category", news.getCategory());
            map.put("Length", news.getLength());
            map.put("Headline", news.getHeadline());
            map.put("dwell", news.getDwell());
            map.put("exposure_time", news.getExposure_time());
            map.put("headline_length", news.getHeadlineLength());
            map.put("newsbody", "");
            map.put("title_entity","");
            answer.add(map);
        }
        return answer;
    }

    public List<News> queryNewsByConditions(
            String topic,
            Integer minLen,
            Integer maxLen,
            Integer minHeadlineLen,
            Integer maxHeadlineLen,
            List<String> newsIds
    ) {
        Specification<News> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (topic != null) predicates.add(cb.equal(root.get("topic"), topic));
            if (minLen != null) predicates.add(cb.ge(root.get("length"), minLen));
            if (maxLen != null) predicates.add(cb.le(root.get("length"), maxLen));
            if (minHeadlineLen != null) predicates.add(cb.ge(root.get("headlineLength"), minHeadlineLen));
            if (maxHeadlineLen != null) predicates.add(cb.le(root.get("headlineLength"), maxHeadlineLen));
            if (newsIds != null && !newsIds.isEmpty()) predicates.add(root.get("newsId").in(newsIds));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable limit = PageRequest.of(0, 1000);
        return newsQueryRepository.findAll(spec, limit).getContent();
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
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> baseResult = objectMapper.readValue(json, new TypeReference<>() {});

            // 提取 aiNews 中的 ID
            JsonNode root = objectMapper.readTree(json);
            JsonNode aiNewsArray = root.get("aiNews");

            List<String> ids = new ArrayList<>();
            if (aiNewsArray != null && aiNewsArray.isArray()) {
                for (JsonNode item : aiNewsArray) {
                    ids.add(item.get("id").asText());
                }
            }
            List<Map<String, Object>> fullNews = new ArrayList<>();
            // 查 MySQL 获取完整新闻内容
            for (String id : ids) {
                fullNews.add(getNews(id));
            }

            baseResult.remove("aiNews");
            // 合并结果
            baseResult.put("aiNewsFull", fullNews);
            return baseResult;

        } catch (Exception e) {
            return Map.of("error", "Failed to parse recommendation", "exception", e.getMessage());
        }
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
        long begin = System.currentTimeMillis();
        LocalDateTime localDateTime = LocalDateTime.now();
        Map<String, Object> first = dataService.getNewsByIdInFirstDatabase(id);
        Map<String, Object> second = dataService.getNewsByIdInSecondDatabase(id);
        long time = System.currentTimeMillis() - begin;
        Query query = new Query(localDateTime, "/api/news/{id}","getNewsByIdInFirstAndSecondDatabase","id" + id, time, first.size() + second.size(), "200", null, "news", begin);
        queryRepository.save(query);
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

