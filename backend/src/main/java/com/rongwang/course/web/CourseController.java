package com.rongwang.course.web;

import com.rongwang.auth.session.CurrentUser;
import com.rongwang.common.api.ApiResponse;
import com.rongwang.common.api.PageResponse;
import com.rongwang.course.dto.CourseDtos.*;
import com.rongwang.course.service.CourseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class CourseController {
    private final CourseService service;
    public CourseController(CourseService service) { this.service = service; }

    @GetMapping("/teacher/courses")
    public ApiResponse<PageResponse<CourseResponse>> teacherCourses(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return ApiResponse.success(service.teacherCourses(CurrentUser.require(request), page, size));
    }

    @PostMapping("/teacher/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CourseResponse> create(@Valid @RequestBody CreateRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.create(CurrentUser.require(request), body));
    }

    @GetMapping("/teacher/courses/{id}")
    public ApiResponse<CourseResponse> teacherDetail(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.success(service.teacherDetail(CurrentUser.require(request), id));
    }

    @PatchMapping("/teacher/courses/{id}")
    public ApiResponse<CourseResponse> update(@PathVariable long id, @Valid @RequestBody UpdateRequest body,
                                              HttpServletRequest request) {
        return ApiResponse.success(service.update(CurrentUser.require(request), id, body));
    }

    @PostMapping("/teacher/courses/{id}/invite-code/reset")
    public ApiResponse<CourseResponse> reset(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.success(service.resetCode(CurrentUser.require(request), id));
    }

    @GetMapping("/teacher/courses/{id}/students")
    public ApiResponse<PageResponse<StudentResponse>> students(
            @PathVariable long id, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, HttpServletRequest request) {
        return ApiResponse.success(service.students(CurrentUser.require(request), id, page, size));
    }

    @GetMapping("/student/courses")
    public ApiResponse<PageResponse<CourseResponse>> studentCourses(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return ApiResponse.success(service.studentCourses(CurrentUser.require(request), page, size));
    }

    @GetMapping("/student/courses/{id}")
    public ApiResponse<CourseResponse> studentDetail(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.success(service.studentDetail(CurrentUser.require(request), id));
    }

    @PostMapping("/student/course-memberships")
    public ApiResponse<MemberResponse> join(@Valid @RequestBody JoinRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.join(CurrentUser.require(request), body));
    }
}
