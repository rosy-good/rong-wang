package com.rongwang.statistics.dto;

import java.util.List;

public final class StatisticsDtos {
    private StatisticsDtos() {}
    public record StudentStatus(String studentId,String username,String displayName,String studentNo,
                                int submissionCount,String bestScore,String representativeSubmissionId) {}
    public record QuestionStatistic(String taskQuestionId,int correctStudentCount,String accuracy) {}
    public record TaskStatistics(long memberCount,long submittedStudentCount,String submissionRate,
                                 String averageScore,long correctQuestionCount,long questionCount,
                                 String accuracy,List<QuestionStatistic> questionStatistics) {}
}

