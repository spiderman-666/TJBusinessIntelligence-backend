package com.example.demo;

import com.example.demo.Service.AIService;
import com.example.demo.Service.RecommendService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.VoidFunction;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.*;
import org.apache.spark.streaming.kafka010.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import redis.clients.jedis.Jedis;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class SparkKafkaRecommender {
    public static void main(String[] args) throws InterruptedException {
        RecommendService recommendService = new RecommendService();
        SparkConf conf = new SparkConf().setAppName("NewsRecommender").setMaster("local[*]");
        JavaStreamingContext ssc = new JavaStreamingContext(conf, Durations.seconds(5));

        Map<String, Object> kafkaParams = new HashMap<>();
        kafkaParams.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "100.80.74.200:9092");
        kafkaParams.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        kafkaParams.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        kafkaParams.put(ConsumerConfig.GROUP_ID_CONFIG, "spark-recommend-group");
        kafkaParams.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        kafkaParams.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        Collection<String> topics = Arrays.asList("news-topic");

        JavaInputDStream<ConsumerRecord<String, String>> stream = KafkaUtils.createDirectStream(
                ssc,
                LocationStrategies.PreferConsistent(),
                ConsumerStrategies.Subscribe(topics, kafkaParams)
        );

        stream.foreachRDD((VoidFunction<JavaRDD<ConsumerRecord<String, String>>>) rdd -> {
            rdd.foreachPartition(partition -> {
                ObjectMapper mapper = new ObjectMapper();
                Jedis jedis = new Jedis("47.116.116.60", 6379);
                jedis.auth("Admin-123");

                while (partition.hasNext()) {
                    ConsumerRecord<String, String> record = partition.next();
                    String message = record.value();
                    try {
                        JsonNode outer = mapper.readTree(message);
                        String inner = outer.get("message").asText();
                        JsonNode clickLog = mapper.readTree(inner);

                        String userId = clickLog.get("user_id").asText();
                        JsonNode history = clickLog.get("click_history");
                        List<String> clickedIds = new ArrayList<>();
                        for (JsonNode node : history) {
                            clickedIds.add(node.get("news_id").asText());
                        }

                        // BI：从 Redis 查询热点新闻
                        Set<String> hotNews = new LinkedHashSet<>(jedis.zrevrange("news:rank:click", 0, 9));

                        // AI：构建 Prompt
                        String prompt = "User clicked news: " + String.join(", ", clickedIds)
                                + ". Recommend 5 news topics.";
                        String aiResult = callChatGPT(prompt);

                        // 加上兴趣传播推荐
                        Map<String, Double> interestRecs = recommendService.recommendBasedOnUserInterest(userId, jedis);

                        Map<String, Object> result = new HashMap<>();
                        result.put("ai", aiResult);
                        result.put("hot", hotNews);
                        result.put("interest", interestRecs);

                        String resultJson = mapper.writeValueAsString(result);
                        jedis.set("user:recommend:" + userId, resultJson);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                jedis.close();
            });
        });

        ssc.start();
        ssc.awaitTermination();
    }

    public static String callChatGPT(String prompt) {
        try {
            HttpClient client = HttpClient.newHttpClient();

            String body = """
        {
          "messages": [
            {
              "role": "user",
              "content": "%s"
            }
          ],
          "max_tokens": 1024,
          "temperature": 0.0
        }
        """.formatted(prompt.replace("\"", "\\\""));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://ai-cxk200045417ai996429688790.openai.azure.com/openai/deployments/gpt-4o-mini/chat/completions?api-version=2025-01-01-preview"))
                    .header("Content-Type", "application/json")
                    .header("api-key", "1mctIjQOZQ6O90Z0VRhudPW4WVR9kvytJfipeVRSGAsFcx0u9NnDJQQJ99BDACHYHv6XJ3w3AAAAACOGMjjs")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();  // 可进一步提取 message.content
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\": \"调用 ChatGPT 接口失败\"}";
        }
    }

}
