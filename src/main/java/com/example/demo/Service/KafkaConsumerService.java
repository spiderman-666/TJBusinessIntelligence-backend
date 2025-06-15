package com.example.demo.Service;

import com.example.demo.Model.*;
import com.example.demo.Repository.NewsRepository;
import com.example.demo.Repository.QueryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
// 实时处理和推荐使用Spark Streaming, 落盘现在就行，它们是同步的
// AI要推荐的能力，但要结合BI本地的数据
// 推荐要考虑时效
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private NewsRepository newsRepository;

    @Autowired
    private NewsQueryService newsQueryService;

    @Autowired
    private QueryRepository queryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    //@KafkaListener(id = "newsListener", topics = "news-topic", groupId = "news-consumer-group", containerFactory = "kafkaListenerContainerFactory")
    public void consume(String kafkaRecord) {
        try {
            System.out.println("收到Kafka消息: " + kafkaRecord);

            // 解析外层 JSON
            KafkaLogMessage kafkaLog = objectMapper.readValue(kafkaRecord, KafkaLogMessage.class);
            String rawMessage = kafkaLog.getMessage();

            // 解析内层 message 字段
            ClickLog clickLog = objectMapper.readValue(rawMessage, ClickLog.class);
            String userId = clickLog.getUser_id();
            List<ClickHistory> clickHistoryList = clickLog.getClick_history();
            Impression impression = clickLog.getImpression();

            // 异步处理点击和曝光
            handleClickAndExpose(clickHistoryList, impression, userId);

            // 用户行为快照
            String redisKey = "user:click:" + userId;
            String value = objectMapper.writeValueAsString(clickLog);
            stringRedisTemplate.opsForValue().set(redisKey, value);

            System.out.println("已保存到 Redis, key=" + redisKey);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("解析消息失败: " + kafkaRecord);
        }
    }

    @Async
    public void handleClickAndExpose(List<ClickHistory> clickHistoryList, Impression impression, String userId) {
        try {
            Set<String> newsIdSet = new HashSet<>();
            for (ClickHistory ch : clickHistoryList) newsIdSet.add(ch.getNews_id());
            for (Clicked c : impression.getClicked()) newsIdSet.add(c.getNews_id());

            // 批量查找新闻数据，避免重复查询，并添加到查询日志中
            long begin = System.currentTimeMillis();
            LocalDateTime localDateTime = LocalDateTime.now();
            Map<String, News> newsMap = newsRepository.findAllById(newsIdSet).stream()
                    .collect(Collectors.toMap(News::getNewsId, n -> n));
            String queryParam = "newsIds=" + String.join(",", newsIdSet);
            long time = System.currentTimeMillis() - begin;
            Query query = new Query(localDateTime, "storage","findAllById",queryParam, time, newsMap.size(), "200", null, "news", begin);
            queryRepository.save(query);
            // 格式化时间
            DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.US);
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH");
            // 点击量（代表用户真实兴趣行为）
            for (ClickHistory clickHistory : clickHistoryList) {
                // 提取字段
                String newsId = clickHistory.getNews_id();
                int dwell = clickHistory.getDwell();
                String exposeTimeStr = clickHistory.getExposure_time();
                if (exposeTimeStr == null || exposeTimeStr.isEmpty()) continue;
                LocalDateTime parsedTime = LocalDateTime.parse(exposeTimeStr, inputFormatter);
                String exposeTime = parsedTime.format(outputFormatter);
                String day = exposeTime.substring(0, 10);

                News news = newsMap.get(newsId);
                if (news == null) continue;

                // topic 推荐构建（基于点击新闻的 topic）
                String category = news.getCategory(); // 比如 "sports"
                String topic = news.getTopic();
                if (topic != null && !topic.isEmpty()) {
                    // 构建查询日志
                    begin = System.currentTimeMillis();
                    localDateTime = LocalDateTime.now();
                    List<News> sameTopicNews = newsQueryService.queryNewsByCategoryAndTopic(category, topic, newsId, 10);
                    time = System.currentTimeMillis() - begin;
                    String queryParams = String.format(
                            "category=%s&topic=%s&excludeNewsId=%s&limit=10",
                            URLEncoder.encode(category, StandardCharsets.UTF_8),
                            URLEncoder.encode(topic, StandardCharsets.UTF_8),
                            URLEncoder.encode(newsId, StandardCharsets.UTF_8)
                    );
                    new Query(localDateTime, "storage","queryNewsByCategoryAndTopic",queryParams, time, sameTopicNews.size(), "200", null, "news", begin);
                    // 查询相同topic新闻并保存
                    for (News related : sameTopicNews) {
                        stringRedisTemplate.opsForZSet()
                                .incrementScore("user:interest:related:" + newsId, related.getNewsId(), 1.0);
                    }
                }

                // 记录新闻第一次点击时间
                String recordKey = "news:time:record:" + newsId;
                if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(recordKey))) {
                    stringRedisTemplate.opsForValue().set(recordKey, exposeTime);
                }
                // 提前维护每条新闻的曝光时间戳
                stringRedisTemplate.opsForValue().setIfAbsent("news:time:record:" + newsId, exposeTime.substring(0, 13));
                // 记录新闻最后一次点击时间
                String latestKey = "news:time:latest:" + newsId;
                stringRedisTemplate.opsForValue().set(latestKey, exposeTime);
                // 点击总数递增
                stringRedisTemplate.opsForValue().increment("news:click:" + newsId);
                // 爆款新闻点击量统计（2.5判断爆款新闻（热点排名））
                stringRedisTemplate.opsForZSet().incrementScore("news:rank:click", newsId, 1);
                // 统计每小时点击量(2.1单条新闻的生命周期查询)
                stringRedisTemplate.opsForZSet().incrementScore("news:time:click:" + exposeTime.substring(0, 13), newsId, 1);
                // 停留时间总量
                stringRedisTemplate.opsForValue().increment("news:dwell:" + newsId, dwell);
                // 某类新闻(2.2某类新闻变化趋势）
                stringRedisTemplate.opsForZSet().incrementScore("category:click:" + day, news.getCategory(), 1);
                // 总兴趣(2.3)
                // stringRedisTemplate.opsForZSet().incrementScore("user:interest:" + userId, newsId, 1);
                // 每日兴趣快照(2.3)
                stringRedisTemplate.opsForZSet().incrementScore("user:interest:" + userId + ":" + day, newsId, Math.log(dwell + 1));
                // 基于停留时间进行推荐(2.6)
                stringRedisTemplate.opsForZSet().incrementScore("user:interest:" + userId, newsId, Math.log(dwell + 1));


            }

            // 曝光量（代表该类别下新闻的整体被推荐/展示频率）
            for (Clicked clicked : impression.getClicked()) {
                String newsId = clicked.getNews_id();
                String startStr = impression.getStart();

                if (startStr == null || startStr.isEmpty()) continue;
                LocalDateTime parsedStart = LocalDateTime.parse(startStr, inputFormatter);
                String start = parsedStart.format(outputFormatter);
                String day = start.substring(0, 10);

                News news = newsMap.get(newsId);
                if (news == null) continue;
                // 曝光总数统计
                stringRedisTemplate.opsForValue().increment("news:expose:" + newsId);
                // 每小时曝光量(2.1单条新闻的生命周期查询)
                stringRedisTemplate.opsForZSet().incrementScore("news:time:expose:" + start.substring(0, 13), newsId, 1);
                // 每日曝光类别统计(2.2类别新闻变化统计)
                stringRedisTemplate.opsForZSet().incrementScore("category:expose:" + day, news.getCategory(), 1);


            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
