package com.rongwang.statistics.web;

import com.rongwang.auth.session.CurrentUser;
import com.rongwang.common.api.*;
import com.rongwang.statistics.dto.StatisticsDtos.*;
import com.rongwang.statistics.service.StatisticsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/teacher/tasks/{id}")
public class StatisticsController {
    private final StatisticsService service;
    public StatisticsController(StatisticsService service) {this.service=service;}
    @GetMapping("/statistics")
    public ApiResponse<TaskStatistics> statistics(@PathVariable long id,HttpServletRequest request) {
        return ApiResponse.success(service.statistics(CurrentUser.require(request),id));
    }
    @GetMapping("/students")
    public ApiResponse<PageResponse<StudentStatus>> students(@PathVariable long id,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,
            HttpServletRequest request) {
        return ApiResponse.success(service.students(CurrentUser.require(request),id,page,size));
    }
}

