package com.example.demo.Repository;

import com.example.demo.Model.News;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.List;

public interface NewsRepository extends JpaRepository<News, String> {
    List<News> findByTopic(String topic);

    @Query("SELECT n FROM News n WHERE " +
            "(:topic IS NULL OR n.topic = :topic) AND " +
            "(:minLen IS NULL OR n.length >= :minLen) AND " +
            "(:maxLen IS NULL OR n.length <= :maxLen) AND " +
            "(:minHeadlineLen IS NULL OR n.headlineLength >= :minHeadlineLen) AND " +
            "(:maxHeadlineLen IS NULL OR n.headlineLength <= :maxHeadlineLen)"
    )
    List<News> queryNews(
            @Param("topic") String topic,
            @Param("minLen") Integer minLen,
            @Param("maxLen") Integer maxLen,
            @Param("minHeadlineLen") Integer minHeadlineLen,
            @Param("maxHeadlineLen") Integer maxHeadlineLen,
            Pageable pageable
    );

    @Query("SELECT n.newsId FROM News n")
    List<String> findAllNewsIds();

    @Query("SELECT n FROM News n WHERE " +
            "(:topic IS NULL OR n.topic = :topic) AND " +
            "(:minLen IS NULL OR n.length >= :minLen) AND " +
            "(:maxLen IS NULL OR n.length <= :maxLen) AND " +
            "(:minHeadlineLen IS NULL OR n.headlineLength >= :minHeadlineLen) AND " +
            "(:maxHeadlineLen IS NULL OR n.headlineLength <= :maxHeadlineLen) AND " +
            "(:newsIds IS NULL OR n.newsId IN :newsIds)")
    List<News> queryNewsById(
            @Param("topic") String topic,
            @Param("minLen") Integer minLen,
            @Param("maxLen") Integer maxLen,
            @Param("minHeadlineLen") Integer minHeadlineLen,
            @Param("maxHeadlineLen") Integer maxHeadlineLen,
            @Param("newsIds") List<String> newsIds,
            Pageable pageable
    );

}
