package com.rongwang.mistake.dto;

import com.rongwang.question.dto.QuestionDtos.Option;
import java.util.List;

public final class MistakeDtos {
    private MistakeDtos() {}
    public record MistakeView(String id,String courseId,String questionId,String lastTaskQuestionId,
                              int wrongCount,String lastWrongAt,String masteryStatus,String masteredAt) {}
    public record MistakeDetail(MistakeView mistake,String stem,List<Option> options,
                                List<String> correctAnswer,String explanation,String score) {}
    public record MasteryRequest(String masteryStatus) {}
    public record PracticeQuestion(String taskQuestionId,String stem,String type,List<Option> options,String score) {}
    public record PracticeRequest(String taskQuestionId,List<String> selectedOptions) {}
    public record PracticeResult(String id,String mistakeId,String taskQuestionId,List<String> selectedOptions,
                                 boolean correct,String earnedScore,List<String> correctAnswer,String explanation,
                                 String practicedAt) {}
    public record PracticeOutcome(PracticeResult result,boolean created) {}
}

