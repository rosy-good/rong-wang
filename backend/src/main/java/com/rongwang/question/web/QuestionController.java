package com.rongwang.question.web;

import com.rongwang.auth.session.CurrentUser;
import com.rongwang.common.api.ApiResponse;
import com.rongwang.common.api.PageResponse;
import com.rongwang.question.dto.QuestionDtos.*;
import com.rongwang.question.service.QuestionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/teacher")
public class QuestionController {
    private final QuestionService service;
    public QuestionController(QuestionService service) { this.service = service; }

    @GetMapping("/courses/{id}/questions")
    public ApiResponse<PageResponse<QuestionResponse>> list(@PathVariable long id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type, @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        return ApiResponse.success(service.list(CurrentUser.require(request), id, page, size, type, keyword));
    }

    @PostMapping("/courses/{id}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuestionResponse> create(@PathVariable long id, @RequestBody WriteRequest body,
                                                HttpServletRequest request) {
        return ApiResponse.success(service.create(CurrentUser.require(request), id, body));
    }

    @GetMapping("/questions/{id}")
    public ApiResponse<QuestionResponse> detail(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.success(service.detail(CurrentUser.require(request), id));
    }

    @PatchMapping("/questions/{id}")
    public ApiResponse<QuestionResponse> update(@PathVariable long id, @RequestBody WriteRequest body,
                                                HttpServletRequest request) {
        return ApiResponse.success(service.update(CurrentUser.require(request), id, body));
    }
}

