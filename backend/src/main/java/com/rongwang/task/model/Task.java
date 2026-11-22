package com.rongwang.task.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("rw_task")
public class Task {
    private Long id, courseId;
    private String title, description, status;
    private LocalDateTime deadlineAt, publishedAt;
    private BigDecimal totalScore;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long value) { this.courseId = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public LocalDateTime getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(LocalDateTime value) { this.deadlineAt = value; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime value) { this.publishedAt = value; }
    public BigDecimal getTotalScore() { return totalScore; }
    public void setTotalScore(BigDecimal value) { this.totalScore = value; }
}

