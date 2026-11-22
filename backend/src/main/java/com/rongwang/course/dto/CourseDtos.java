package com.rongwang.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rongwang.course.model.Course;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CourseDtos {
    private CourseDtos() { }

    public record CreateRequest(
            @NotBlank(message = "请输入课程名称") @Size(max = 100) String name,
            @Size(max = 10000) String description) { }

    // PATCH 中 null 表示保留原值，空字符串可以清空介绍；不接收归属或状态字段。
    public record UpdateRequest(@Size(max = 100) String name, @Size(max = 10000) String description) { }

    public record JoinRequest(@NotBlank @Size(max = 32) String inviteCode) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CourseResponse(String id, String teacherId, String name, String description,
                                 String status, String inviteCode) {
        public static CourseResponse from(Course course, boolean teacherView) {
            return new CourseResponse(course.getId().toString(), course.getTeacherId().toString(),
                    course.getName(), course.getDescription(), course.getStatus(),
                    teacherView ? course.getInviteCode() : null);
        }
    }

    public record MemberResponse(String id, String courseId, String studentId) { }
    public record StudentResponse(String id, String username, String displayName, String studentNo) { }
}
