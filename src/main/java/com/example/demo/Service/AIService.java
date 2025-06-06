package com.example.demo.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AIService {

    private final StringRedisTemplate redisTemplate;

    // 简单的基于用户兴趣的协同过滤推荐
    public Map<String, Double> recommendBasedOnUserInterest(String userId) {
        // 获取用户最感兴趣的新闻（Top 5）
        String key = "user:interest:" + userId;
        Map<String, Double> interest = new HashMap<>();
        redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, 4).forEach(score -> {
            if (score != null && score.getScore() != null) {
                interest.put(score.getValue(), score.getScore());
            }
        });

        // 推荐相关话题的其他新闻（此处简化为兴趣最高的新闻向周边扩展）
        Map<String, Double> recommended = new LinkedHashMap<>();
        for (String newsId : interest.keySet()) {
            Set<String> similarNews = redisTemplate.opsForZSet().range("user:interest:related:" + newsId, 0, 4);
            if (similarNews != null) {
                for (String s : similarNews) {
                    recommended.put(s, recommended.getOrDefault(s, 0.0) + 1.0);
                }
            }
        }

        return recommended;
    }
}

