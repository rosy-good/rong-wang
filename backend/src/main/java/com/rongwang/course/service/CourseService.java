package com.rongwang.course.service;

import com.rongwang.auth.model.UserRole;
import com.rongwang.auth.session.LoginUser;
import com.rongwang.common.api.PageResponse;
import com.rongwang.common.exception.ApiException;
import com.rongwang.course.dto.CourseDtos.*;
import com.rongwang.course.mapper.CourseMapper;
import com.rongwang.course.model.Course;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CourseService {
    private final CourseMapper mapper;

    public CourseService(CourseMapper mapper) { this.mapper = mapper; }

    public PageResponse<CourseResponse> teacherCourses(LoginUser user, int page, int size) {
        requireRole(user, UserRole.TEACHER);
        long offset = offset(page, size);
        return new PageResponse<>(mapper.teacherCourses(user.id(), size, offset).stream()
                .map(c -> CourseResponse.from(c, true)).toList(), mapper.teacherCourseCount(user.id()));
    }

    @Transactional
    public CourseResponse create(LoginUser user, CreateRequest request) {
        requireRole(user, UserRole.TEACHER);
        Course course = new Course();
        course.setTeacherId(user.id());
        course.setName(validName(request.name()));
        course.setDescription(request.description());
        course.setStatus("ACTIVE");
        // 唯一索引兜底；极小概率撞码时重新生成，不向客户端暴露 SQL 错误。
        for (int i = 0; i < 3; i++) {
            course.setId(null);
            course.setInviteCode(newCode());
            try {
                mapper.insert(course);
                return CourseResponse.from(course, true);
            } catch (DuplicateKeyException exception) {
                if (i == 2) throw codeConflict();
            }
        }
        throw codeConflict();
    }

    public CourseResponse teacherDetail(LoginUser user, long id) {
        requireRole(user, UserRole.TEACHER);
        return CourseResponse.from(owned(mapper.selectById(id), user), true);
    }

    @Transactional
    public CourseResponse update(LoginUser user, long id, UpdateRequest request) {
        requireRole(user, UserRole.TEACHER);
        Course course = owned(mapper.lockById(id), user);
        if (request.name() != null) course.setName(validName(request.name()));
        if (request.description() != null) course.setDescription(request.description());
        mapper.updateDetails(course);
        return CourseResponse.from(course, true);
    }

    @Transactional
    public CourseResponse resetCode(LoginUser user, long id) {
        requireRole(user, UserRole.TEACHER);
        Course course = owned(mapper.lockById(id), user);
        for (int i = 0; i < 3; i++) {
            String code = newCode();
            if (code.equals(course.getInviteCode())) continue;
            try {
                mapper.updateInviteCode(id, code);
                course.setInviteCode(code);
                return CourseResponse.from(course, true);
            } catch (DuplicateKeyException exception) {
                if (i == 2) throw codeConflict();
            }
        }
        throw codeConflict();
    }

    public PageResponse<StudentResponse> students(LoginUser user, long id, int page, int size) {
        requireRole(user, UserRole.TEACHER);
        long offset = offset(page, size);
        owned(mapper.selectById(id), user);
        return new PageResponse<>(mapper.students(id, size, offset), mapper.memberCount(id));
    }

    public PageResponse<CourseResponse> studentCourses(LoginUser user, int page, int size) {
        requireRole(user, UserRole.STUDENT);
        long offset = offset(page, size);
        return new PageResponse<>(mapper.studentCourses(user.id(), size, offset).stream()
                .map(c -> CourseResponse.from(c, false)).toList(), mapper.studentCourseCount(user.id()));
    }

    public CourseResponse studentDetail(LoginUser user, long id) {
        requireRole(user, UserRole.STUDENT);
        if (mapper.memberId(id, user.id()) == null) throw notFound();
        Course course = mapper.selectById(id);
        if (course == null) throw notFound();
        return CourseResponse.from(course, false);
    }

    @Transactional
    public MemberResponse join(LoginUser user, JoinRequest request) {
        requireRole(user, UserRole.STUDENT);
        // 锁课程行，与重置邀请码共用同一把数据库锁；锁定读使用当前已提交版本。
        Course course = mapper.lockByInviteCode(request.inviteCode().trim());
        if (course == null || !"ACTIVE".equals(course.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INVITE_CODE", "邀请码无效或课程已归档");
        }
        Long memberId = mapper.memberId(course.getId(), user.id());
        if (memberId == null) {
            mapper.insertMember(course.getId(), user.id());
            memberId = mapper.memberId(course.getId(), user.id());
        }
        return new MemberResponse(memberId.toString(), course.getId().toString(), user.id().toString());
    }

    private Course owned(Course course, LoginUser user) {
        if (course == null || !user.id().equals(course.getTeacherId())) throw notFound();
        return course;
    }

    private void requireRole(LoginUser user, UserRole role) {
        if (user.role() != role) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ROLE_FORBIDDEN", "当前角色无权执行此操作");
        }
    }

    private long offset(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "page 至少为 1，size 为 1 到 100");
        }
        return ((long) page - 1) * size;
    }

    private String validName(String name) {
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COURSE_NAME", "课程名称不能为空且不能超过 100 字符");
        }
        return name.trim();
    }

    private String newCode() { return UUID.randomUUID().toString().replace("-", ""); }
    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "课程不存在或不可访问");
    }
    private ApiException codeConflict() {
        return new ApiException(HttpStatus.CONFLICT, "INVITE_CODE_CONFLICT", "邀请码生成冲突，请重试");
    }
}
