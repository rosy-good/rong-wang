package com.rongwang.submission.web;

import com.rongwang.auth.session.CurrentUser;
import com.rongwang.common.api.*;
import com.rongwang.submission.dto.SubmissionDtos.*;
import com.rongwang.submission.service.SubmissionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.*;

@RestController
@RequestMapping("/v1")
public class SubmissionController {
    private final SubmissionService service;
    public SubmissionController(SubmissionService service) {this.service=service;}

    @PostMapping("/student/tasks/{id}/submissions")
    public ResponseEntity<ApiResponse<SubmissionResult>> submit(@PathVariable long id,
            @RequestHeader(value="Idempotency-Key",required=false) String key,
            @RequestBody SubmitRequest body,HttpServletRequest request) {
        LocalDateTime receivedAt=(LocalDateTime)request.getAttribute(SubmissionArrivalFilter.RECEIVED_AT);
        if(receivedAt==null) throw new IllegalStateException("未记录提交请求的服务器到达时间");
        SubmitOutcome outcome=service.submit(CurrentUser.require(request),id,key,body,receivedAt);
        return ResponseEntity.status(outcome.created()?HttpStatus.CREATED:HttpStatus.OK)
                .body(ApiResponse.success(outcome.result()));
    }
    @GetMapping("/student/submissions")
    public ApiResponse<PageResponse<SubmissionSummary>> studentHistory(
            @RequestParam(required=false) Long courseId,@RequestParam(required=false) Long taskId,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,
            HttpServletRequest request) {
        return ApiResponse.success(service.studentHistory(CurrentUser.require(request),courseId,taskId,page,size));
    }
    @GetMapping("/student/submissions/{id}")
    public ApiResponse<SubmissionResult> studentDetail(@PathVariable long id,HttpServletRequest request) {
        return ApiResponse.success(service.studentDetail(CurrentUser.require(request),id));
    }
    @GetMapping("/teacher/tasks/{id}/submissions")
    public ApiResponse<PageResponse<SubmissionSummary>> teacherHistory(@PathVariable long id,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,
            HttpServletRequest request) {
        return ApiResponse.success(service.teacherHistory(CurrentUser.require(request),id,page,size));
    }
    @GetMapping("/teacher/submissions/{id}")
    public ApiResponse<SubmissionResult> teacherDetail(@PathVariable long id,HttpServletRequest request) {
        return ApiResponse.success(service.teacherDetail(CurrentUser.require(request),id));
    }
}

