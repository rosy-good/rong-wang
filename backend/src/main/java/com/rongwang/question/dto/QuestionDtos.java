package com.rongwang.question.dto;

import java.util.List;

public final class QuestionDtos {
    private QuestionDtos() {}
    public record Option(String key, String text) {}
    // 分数字符串避免前端浮点精度损失；请求用 BigDecimal 接收。
    public record WriteRequest(String type, String stem, List<Option> options,
                               List<String> correctAnswer, java.math.BigDecimal score, String explanation) {}
    public record QuestionResponse(String id, String courseId, String type, String stem,
                                   List<Option> options, List<String> correctAnswer,
                                   String score, String explanation) {}
}

