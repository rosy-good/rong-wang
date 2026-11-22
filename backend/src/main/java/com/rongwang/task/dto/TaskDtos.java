package com.rongwang.task.dto;

import com.rongwang.question.dto.QuestionDtos;
import java.util.List;

public final class TaskDtos {
    private TaskDtos() {}
    public record WriteTask(String title, String description, String deadlineAt) {}
    public record Selection(String questionId, String taskQuestionId, QuestionDtos.WriteRequest content) {}
    public record SelectQuestions(List<Selection> questions) {}
    public record TaskQuestionView(String taskQuestionId, String questionId, int position, String type,
                                   String stem, List<QuestionDtos.Option> options, List<String> correctAnswer,
                                   String score, String explanation) {}
    public record StudentQuestionView(String taskQuestionId, int position, String type,
                                      String stem, List<QuestionDtos.Option> options, String score) {}
    public record TaskView(String id, String courseId, String title, String description, String deadlineAt,
                           String status, String publishedAt, String totalScore, List<TaskQuestionView> questions) {}
    public record StudentTaskView(String id, String courseId, String title, String description,
                                  String deadlineAt, String totalScore, List<StudentQuestionView> questions,
                                  String bestScore, String representativeSubmissionId) {}
}

