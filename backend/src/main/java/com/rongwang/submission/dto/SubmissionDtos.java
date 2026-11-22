package com.rongwang.submission.dto;

import java.util.List;

public final class SubmissionDtos {
    private SubmissionDtos() {}
    public record AnswerInput(String taskQuestionId, List<String> selectedOptions) {}
    public record SubmitRequest(List<AnswerInput> answers) {}
    public record AnswerResult(String taskQuestionId, String questionId, List<String> selectedOptions,
                               boolean correct, String earnedScore, List<String> correctAnswer,
                               String explanation) {}
    public record SubmissionResult(String id, String taskId, String studentId, int attemptNo,
                                   String receivedAt, String totalScore, int correctCount, int questionCount,
                                   String bestScore, String representativeSubmissionId,
                                   List<AnswerResult> answers) {}
    public record SubmissionSummary(String id, String taskId, String studentId,
                                    int attemptNo, String submittedAt, String totalScore,
                                    int correctCount, int questionCount) {}
    public record SubmitOutcome(SubmissionResult result, boolean created) {}
}

