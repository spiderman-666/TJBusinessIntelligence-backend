package com.example.demo.Service;


import redis.clients.jedis.Jedis;
import redis.clients.jedis.resps.Tuple;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecommendService {
    private static final Object lock = new Object();

    public static Map<String, Double> recommendBasedOnUserInterest(String userId, Jedis jedis) {
        String key = "user:interest:" + userId;

        List<Tuple> topInterests = jedis.zrevrangeWithScores(key, 0, 4);
        Map<String, Double> recommended = new LinkedHashMap<>();

        if (topInterests != null) {
            for (Tuple interestTuple : topInterests) {
                String newsId = interestTuple.getElement();
                Double weight = interestTuple.getScore();

                if (newsId == null || weight == null) continue;

                List<String> related = jedis.zrange("user:interest:related:" + newsId, 0, 4);
                if (related != null) {
                    for (String relatedNewsId : related) {
                        recommended.put(
                                relatedNewsId,
                                recommended.getOrDefault(relatedNewsId, 0.0) + weight
                        );
                    }
                }
            }
        }

        return recommended;
    }
}
