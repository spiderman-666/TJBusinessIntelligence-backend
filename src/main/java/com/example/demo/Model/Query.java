package com.example.demo.Model;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Date;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name="query_log")
public class Query {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 自增主键
    private int id;
    private String session_id;
    private LocalDateTime query_time;
    private String endpoint;
    private String query_sql;
    private String query_params;
    private Long duration_ms;
    private int result_count;
    @Column(name="status_code")
    private String state_code;
    private String error_message;
    private String module;
    private Date log_date;
    public Query(LocalDateTime query_time, String endpoint, String query_sql, String query_params, Long duration_ms, int result_count, String state_code, String error_message, String module, long log_date) {
        this.query_time = query_time;
        this.endpoint = endpoint;
        this.query_sql = query_sql;
        this.query_params = query_params;
        this.duration_ms = duration_ms;
        this.result_count = result_count;

        this.state_code = state_code;
        this.error_message = error_message;
        this.module = module;
        this.log_date = new Date(log_date);
    }

    public Query() {

    }
}
