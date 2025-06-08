package com.example.demo.Controller;

import com.example.demo.Model.News;
import com.example.demo.Service.KafkaConsumerService;
import com.example.demo.Service.NewsService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "http://localhost:5173/")
@RequestMapping("api/news")
public class RecommendController {

    private final NewsService newsService;

    public RecommendController(NewsService newsService) {
        this.newsService = newsService;
    }

    // 2.1查询新闻的生命周期
    @GetMapping("/lifecycle")
    public Map<String, Map<String, Long>> getLifecycleRange(
            @RequestParam String newsId,
            @RequestParam String startDate,  // 格式: yyyy-MM-dd
            @RequestParam String endDate     // 格式: yyyy-MM-dd
    ) {
        return newsService.getNewsLifecycleRange(newsId, startDate, endDate);
    }


    // 2.2查询某类新闻某日的点击/曝光
    @GetMapping("/category-stat")
    public Map<String, Map<String, Long>> getCategoryStats(@RequestParam String category,
                                              @RequestParam String startDate,
                                              @RequestParam String endDate) {
        return newsService.getCategoryStats(category, startDate, endDate);
    }

    // 2.3查询用户某日兴趣快照
    @GetMapping("/user-interest")
    public Map<String, Double> getUserInterest(
            @RequestParam String userId,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        return newsService.getUserInterest(userId, startDate, endDate);
    }
    // 2.4组合查询
    @GetMapping("/query")
    public List<News> queryByMultipleConditions(
            @RequestParam(required = false) List<String> userIds,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) Integer minLength,
            @RequestParam(required = false) Integer maxLength,
            @RequestParam(required = false) Integer minHeadlineLength,
            @RequestParam(required = false) Integer maxHeadlineLength
    ) {
        return newsService.queryByConditionsOptimized(userIds, startDate, endDate,
                topic, minLength, maxLength, minHeadlineLength, maxHeadlineLength);
    }



    // 2.5热门新闻排行
    @GetMapping("/hot")
    public Map<String, Double> getHotNewsRank() {
        return newsService.getHotNews();
    }

    // 2.6推荐新闻
    @GetMapping("/recommend")
    public Map<String, Double> recommend(@RequestParam String userId) {
        return newsService.recommendNews(userId);
    }
}
