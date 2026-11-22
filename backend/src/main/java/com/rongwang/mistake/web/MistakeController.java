package com.rongwang.mistake.web;

import com.rongwang.auth.session.CurrentUser;
import com.rongwang.common.api.*;
import com.rongwang.mistake.dto.MistakeDtos.*;
import com.rongwang.mistake.service.MistakeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/student/mistakes")
public class MistakeController {
    private final MistakeService service;
    public MistakeController(MistakeService service) {this.service=service;}
    @GetMapping
    public ApiResponse<PageResponse<MistakeView>> list(@RequestParam(required=false) Long courseId,
            @RequestParam(required=false) String masteryStatus,@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int size,HttpServletRequest request) {
        return ApiResponse.success(service.list(CurrentUser.require(request),courseId,masteryStatus,page,size));
    }
    @GetMapping("/{id}")
    public ApiResponse<MistakeDetail> detail(@PathVariable long id,HttpServletRequest request) {
        return ApiResponse.success(service.detail(CurrentUser.require(request),id));
    }
    @PatchMapping("/{id}")
    public ApiResponse<MistakeView> mastery(@PathVariable long id,@RequestBody MasteryRequest body,
                                           HttpServletRequest request) {
        return ApiResponse.success(service.mastery(CurrentUser.require(request),id,body));
    }
    @GetMapping("/{id}/practice-question")
    public ApiResponse<PracticeQuestion> question(@PathVariable long id,HttpServletRequest request) {
        return ApiResponse.success(service.question(CurrentUser.require(request),id));
    }
    @PostMapping("/{id}/practices")
    public ResponseEntity<ApiResponse<PracticeResult>> practice(@PathVariable long id,
            @RequestHeader(value="Idempotency-Key",required=false) String key,
            @RequestBody PracticeRequest body,HttpServletRequest request) {
        PracticeOutcome out=service.practice(CurrentUser.require(request),id,key,body);
        return ResponseEntity.status(out.created()?HttpStatus.CREATED:HttpStatus.OK)
                .body(ApiResponse.success(out.result()));
    }
    @GetMapping("/{id}/practices")
    public ApiResponse<PageResponse<PracticeResult>> practices(@PathVariable long id,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,
            HttpServletRequest request) {
        return ApiResponse.success(service.practices(CurrentUser.require(request),id,page,size));
    }
}

