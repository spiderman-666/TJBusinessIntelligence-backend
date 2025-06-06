//package com.example.demo.Config;
//
//import org.apache.spark.SparkConf;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import java.util.Map;
//
//@Configuration
//public class SparkStreamingConfig {
//
//    @Bean(destroyMethod = "stop")
//    public JavaStreamingContext createContext() {
//        SparkConf conf = new SparkConf()
//                .setAppName("NewsAnalysisStreaming")
//                .setMaster("local[*]");
//
//        JavaStreamingContext ssc = new JavaStreamingContext(conf, Durations.seconds(10));
//
//        Map<String, Object> kafkaParams = new HashMap<>();
//        kafkaParams.put("bootstrap.servers", "localhost:9092");
//        kafkaParams.put("group.id", "news-stream-group");
//        kafkaParams.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
//        kafkaParams.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
//        kafkaParams.put("auto.offset.reset", "latest");
//        kafkaParams.put("enable.auto.commit", false);
//
//        Collection<String> topics = Arrays.asList("news-topic");
//
//        JavaInputDStream<ConsumerRecord<String, String>> stream = KafkaUtils.createDirectStream(
//                ssc,
//                LocationStrategies.PreferConsistent(),
//                ConsumerStrategies.Subscribe(topics, kafkaParams)
//        );
//
//        stream.foreachRDD(rdd -> {
//            rdd.foreachPartition(partition -> {
//                ObjectMapper objectMapper = new ObjectMapper();
//                StringRedisTemplate redis = SpringContextUtil.getBean(StringRedisTemplate.class);
//                NewsRepository newsRepo = SpringContextUtil.getBean(NewsRepository.class);
//
//                partition.forEachRemaining(record -> {
//                    try {
//                        System.out.println("收到Kafka消息: " + record.value());
//
//                        // 解析外层 JSON
//                        KafkaLogMessage kafkaLog = objectMapper.readValue(record.value(), KafkaLogMessage.class);
//                        String rawMessage = kafkaLog.getMessage();
//
//                        // 解析内层 message 字段
//                        ClickLog clickLog = objectMapper.readValue(rawMessage, ClickLog.class);
//                        String timestamp = clickLog.getTimestamp();
//                        String userId = clickLog.getUser_id();
//                        List<ClickHistory> clickHistoryList = clickLog.getClick_history();
//                        List<Impression> impressionList = clickLog.getImpression();
//
//                        // 点击量（代表用户真实兴趣行为）
//                        for (ClickHistory clickHistory : clickHistoryList) {
//                            String newsId = clickHistory.getNews_id();
//                            int dwell = clickHistory.getDwell();
//                            String exposure_time = clickHistory.getExposure_time();
//
//                            // 点击总数递增
//                            redis.opsForValue().increment("news:click:" + newsId);
//
//                            // 爆款新闻点击量统计（2.5判断爆款新闻（热点排名））
//                            redis.opsForZSet().incrementScore("news:rank:click", newsId, 1);
//
//                            // 统计每小时点击量(2.1单个新闻的生命周期查询)
//                            redis.opsForZSet().incrementScore("news:time:click:" + timestamp.substring(0, 13), newsId, 1);
//
//                            // 停留时间总量
//                            redis.opsForValue().increment("news:dwell:" + newsId, dwell);
//
//                            // 某类新闻(2.2某类新闻变化趋势）
//                            String day = timestamp.substring(0, 10);
//                            News news = newsRepo.findById(newsId).orElse(null);
//                            if (news != null) {
//                                redis.opsForZSet().incrementScore("category:click:" + day, news.getCategory(), 1);
//                            }
//
//                            // 总兴趣(2.3)
//                            redis.opsForZSet().incrementScore("user:interest:" + userId, newsId, 1);
//
//                            // 每日兴趣快照(2.3)
//                            redis.opsForZSet().incrementScore("user:interest:" + userId + ":" + day, newsId, 1);
//                        }
//
//                        // 曝光量（代表该类别下新闻的整体被推荐/展示频率）
//                        for (Impression impression : impressionList) {
//                            for (Clicked clicked : impression.getClicked()) {
//                                String newsId = clicked.getNews_id();
//
//                                // 曝光总数统计
//                                redis.opsForValue().increment("news:expose:" + newsId);
//
//                                // 每小时曝光量(2.1单个新闻的生命周期查询)
//                                redis.opsForZSet().incrementScore("news:time:expose:" + timestamp.substring(0, 13), newsId, 1);
//
//                                // 每日曝光类别统计(2.2类别新闻变化统计)
//                                String day = timestamp.substring(0, 10);
//                                News news = newsRepo.findById(newsId).orElse(null);
//                                if (news != null) {
//                                    redis.opsForZSet().incrementScore("category:expose:" + day, news.getCategory(), 1);
//                                }
//                            }
//                        }
//
//                        // 用户行为快照
//                        String redisKey = "user:click:" + userId;
//                        String value = objectMapper.writeValueAsString(clickLog);
//                        redis.opsForValue().set(redisKey, value);
//
//                        // 查询日志记录
//                        Map<String, Object> queryLog = new HashMap<>();
//                        queryLog.put("query", "Kafka消息处理");
//                        queryLog.put("user", userId);
//                        queryLog.put("time", System.currentTimeMillis());
//                        redis.opsForList().leftPush("query:log", objectMapper.writeValueAsString(queryLog));
//
//                        System.out.println("已保存到 Redis, key=" + redisKey);
//
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                        System.out.println("解析消息失败: " + record.value());
//                    }
//                });
//            });
//        });
//
//        return ssc;
//    }
//}
//
