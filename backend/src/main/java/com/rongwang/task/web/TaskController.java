package com.rongwang.task.web;

import com.rongwang.auth.session.CurrentUser;
import com.rongwang.common.api.*;
import com.rongwang.task.dto.TaskDtos.*;
import com.rongwang.task.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class TaskController {
    private final TaskService service;
    public TaskController(TaskService service) { this.service=service; }

    @GetMapping("/teacher/courses/{id}/tasks")
    public ApiResponse<PageResponse<TaskView>> teacherList(@PathVariable long id,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,
            HttpServletRequest request) {
        return ApiResponse.success(service.teacherList(CurrentUser.require(request),id,page,size));
    }
    @PostMapping("/teacher/courses/{id}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaskView> create(@PathVariable long id,@RequestBody WriteTask body,HttpServletRequest request) {
        return ApiResponse.success(service.create(CurrentUser.require(request),id,body));
    }
    @GetMapping("/teacher/tasks/{id}")
    public ApiResponse<TaskView> teacherDetail(@PathVariable long id,HttpServletRequest request) {
        return ApiResponse.success(service.teacherDetail(CurrentUser.require(request),id));
    }
    @PatchMapping("/teacher/tasks/{id}")
    public ApiResponse<TaskView> update(@PathVariable long id,@RequestBody WriteTask body,HttpServletRequest request) {
        return ApiResponse.success(service.update(CurrentUser.require(request),id,body));
    }
    @PutMapping("/teacher/tasks/{id}/questions")
    public ApiResponse<TaskView> select(@PathVariable long id,@RequestBody SelectQuestions body,HttpServletRequest request) {
        return ApiResponse.success(service.select(CurrentUser.require(request),id,body));
    }
    @PostMapping("/teacher/tasks/{id}/publish")
    public ApiResponse<TaskView> publish(@PathVariable long id,HttpServletRequest request) {
        return ApiResponse.success(service.publish(CurrentUser.require(request),id));
    }
    @PostMapping("/teacher/tasks/{id}/copies")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaskView> copy(@PathVariable long id,HttpServletRequest request) {
        return ApiResponse.success(service.copyTask(CurrentUser.require(request),id));
    }
    @GetMapping("/student/courses/{id}/tasks")
    public ApiResponse<PageResponse<StudentTaskView>> studentList(@PathVariable long id,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,
            HttpServletRequest request) {
        return ApiResponse.success(service.studentList(CurrentUser.require(request),id,page,size));
    }
    @GetMapping("/student/tasks/{id}")
    public ApiResponse<StudentTaskView> studentDetail(@PathVariable long id,HttpServletRequest request) {
        return ApiResponse.success(service.studentDetail(CurrentUser.require(request),id));
    }
}

