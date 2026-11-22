package com.rongwang.question.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;

@TableName("rw_question")
public class Question {
    private Long id;
    private Long courseId;
    private String type;
    private String stem;
    private String optionsJson;
    private String correctAnswerJson;
    private BigDecimal score;
    private String explanation;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStem() { return stem; }
    public void setStem(String stem) { this.stem = stem; }
    public String getOptionsJson() { return optionsJson; }
    public void setOptionsJson(String optionsJson) { this.optionsJson = optionsJson; }
    public String getCorrectAnswerJson() { return correctAnswerJson; }
    public void setCorrectAnswerJson(String correctAnswerJson) { this.correctAnswerJson = correctAnswerJson; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}

