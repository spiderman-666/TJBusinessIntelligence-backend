package com.example.demo.Repository;

import com.example.demo.Model.News;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NewsQueryRepository extends JpaRepository<News, String>, JpaSpecificationExecutor<News> {
    @Query("SELECT n.newsId FROM News n")
    List<String> findAllNewsIds();
}
