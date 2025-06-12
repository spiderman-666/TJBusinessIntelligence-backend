package com.example.demo.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AIService {

    private final StringRedisTemplate redisTemplate;


    // 基于用户兴趣 + 权重传播的协同过滤推荐
    public Map<String, Double> recommendBasedOnUserInterest(String userId) {
        String key = "user:interest:" + userId;

        // 获取用户最感兴趣的前5条新闻及其兴趣得分（如点击量、停留时间等累计）
        Set<ZSetOperations.TypedTuple<String>> topInterests =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, 9);

        Map<String, Double> recommended = new LinkedHashMap<>();

        if (topInterests != null) {
            for (ZSetOperations.TypedTuple<String> interestTuple : topInterests) {
                String newsId = interestTuple.getValue();
                Double weight = interestTuple.getScore(); // 兴趣强度作为传播权重

                if (newsId == null || weight == null) continue;

                // 从 Redis 中获取该新闻的相关联推荐新闻集合
                Set<String> similarNews = redisTemplate.opsForZSet()
                        .range("user:interest:related:" + newsId, 0, 4);

                if (similarNews != null) {
                    for (String relatedNewsId : similarNews) {
                        // 将兴趣得分作为推荐传播权重累加
                        recommended.put(relatedNewsId,
                                recommended.getOrDefault(relatedNewsId, 0.0) + weight);
                    }
                }
            }
        }

        return recommended;
    }
}
