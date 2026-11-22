package com.rongwang.task.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;

@TableName("rw_task_question")
public class TaskQuestion {
    private Long id, taskId, questionId;
    private Integer position;
    private String type, stem, optionsJson, correctAnswerJson, explanation;
    private BigDecimal score;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { this.taskId = value; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long value) { this.questionId = value; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer value) { this.position = value; }
    public String getType() { return type; }
    public void setType(String value) { this.type = value; }
    public String getStem() { return stem; }
    public void setStem(String value) { this.stem = value; }
    public String getOptionsJson() { return optionsJson; }
    public void setOptionsJson(String value) { this.optionsJson = value; }
    public String getCorrectAnswerJson() { return correctAnswerJson; }
    public void setCorrectAnswerJson(String value) { this.correctAnswerJson = value; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String value) { this.explanation = value; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal value) { this.score = value; }
}

