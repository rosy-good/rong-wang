package com.rongwang.question.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rongwang.auth.model.UserRole;
import com.rongwang.auth.session.LoginUser;
import com.rongwang.common.api.PageResponse;
import com.rongwang.common.exception.ApiException;
import com.rongwang.course.service.CourseService;
import com.rongwang.question.dto.QuestionDtos.*;
import com.rongwang.question.mapper.QuestionMapper;
import com.rongwang.question.model.Question;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class QuestionService {
    private static final Set<String> TYPES = Set.of("SINGLE", "MULTIPLE", "JUDGMENT");
    private final QuestionMapper mapper;
    private final CourseService courses;
    private final ObjectMapper json;

    public QuestionService(QuestionMapper mapper, CourseService courses, ObjectMapper json) {
        this.mapper = mapper;
        this.courses = courses;
        this.json = json;
    }

    public PageResponse<QuestionResponse> list(LoginUser user, long courseId, int page, int size,
                                                String type, String keyword) {
        courses.teacherDetail(user, courseId);
        if (page < 1 || size < 1 || size > 100) throw invalid("page 至少为 1，size 为 1 到 100");
        if (type != null && !TYPES.contains(type)) throw invalid("题型必须为 SINGLE、MULTIPLE 或 JUDGMENT");
        if (keyword != null) {
            keyword = keyword.trim();
            if (keyword.length() > 100) throw invalid("搜索词最多 100 字符");
            if (keyword.isEmpty()) keyword = null;
        }
        return new PageResponse<>(mapper.list(courseId, type, keyword, size, ((long) page - 1) * size)
                .stream().map(this::response).toList(), mapper.count(courseId, type, keyword));
    }

    public QuestionResponse detail(LoginUser user, long id) {
        return response(owned(user, id, false));
    }

    // 任务草稿复用与题库相同的题目校验，但生成独立内容，不修改原题。
    public Question normalizedDraft(WriteRequest body) {
        Question question = new Question();
        apply(question, body);
        return question;
    }

    @Transactional
    public QuestionResponse create(LoginUser user, long courseId, WriteRequest body) {
        courses.teacherDetail(user, courseId);
        Question question = new Question();
        question.setCourseId(courseId);
        apply(question, body);
        mapper.insert(question);
        return response(question);
    }

    @Transactional
    public QuestionResponse update(LoginUser user, long id, WriteRequest body) {
        Question question = owned(user, id, true);
        // 锁定后合并部分更新，再校验整个题目，避免选项与答案不一致。
        apply(question, body);
        mapper.updateContent(question);
        return response(question);
    }

    private Question owned(LoginUser user, long id, boolean lock) {
        if (user.role() != UserRole.TEACHER)
            throw new ApiException(HttpStatus.FORBIDDEN, "ROLE_FORBIDDEN", "仅教师可访问题库");
        Question question = lock ? mapper.lockById(id) : mapper.selectById(id);
        if (question == null) throw new ApiException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "题目不存在或不可访问");
        courses.teacherDetail(user, question.getCourseId());
        return question;
    }

    private void apply(Question question, WriteRequest body) {
        if (body.type() != null) question.setType(body.type());
        if (body.stem() != null) question.setStem(body.stem().trim());
        if (body.score() != null) question.setScore(body.score());
        if (body.explanation() != null) question.setExplanation(body.explanation());
        List<Option> options = body.options() != null ? body.options() : readOptions(question.getOptionsJson());
        List<String> answer = body.correctAnswer() != null ? body.correctAnswer() : readAnswer(question.getCorrectAnswerJson());

        if (question.getType() == null || !TYPES.contains(question.getType())) throw invalid("请选择有效题型");
        if (question.getStem() == null || question.getStem().isBlank() || question.getStem().length() > 10000)
            throw invalid("题干不能为空且最多 10000 字符");
        if (question.getExplanation() != null && question.getExplanation().length() > 10000)
            throw invalid("解析最多 10000 字符");
        BigDecimal score = question.getScore();
        if (score == null || score.signum() <= 0 || score.scale() > 2
                || score.compareTo(new BigDecimal("999999.99")) > 0)
            throw invalid("分值必须为 0.01 到 999999.99，最多两位小数");
        if (options == null || options.size() < 2 || options.size() > 26) throw invalid("选项数量必须为 2 到 26");
        Set<String> keys = new HashSet<>();
        for (Option option : options) {
            if (option == null || option.key() == null || !option.key().matches("[A-Z]")
                    || !keys.add(option.key())) throw invalid("选项标识必须为不重复的大写字母 A 到 Z");
            if (option.text() == null || option.text().isBlank() || option.text().length() > 2000)
                throw invalid("选项内容不能为空且最多 2000 字符");
        }
        if ("JUDGMENT".equals(question.getType())
                && !options.equals(List.of(new Option("A", "正确"), new Option("B", "错误"))))
            throw invalid("判断题选项固定为 A 正确、B 错误，按此顺序传入");
        if (answer == null || answer.isEmpty()) throw invalid("标准答案不能为空");
        Set<String> answers = new HashSet<>();
        for (String key : answer) {
            if (key == null || !keys.contains(key) || !answers.add(key)) throw invalid("标准答案包含未知或重复选项");
        }
        if ("MULTIPLE".equals(question.getType())) {
            if (answers.size() < 2) throw invalid("多选题至少有两个正确选项");
        } else if (answers.size() != 1) {
            throw invalid("单选题和判断题只能有一个正确选项");
        }
        question.setScore(score.setScale(2));
        question.setOptionsJson(write(options));
        // 标准答案按选项顺序存储，输入顺序不影响答案集合。
        question.setCorrectAnswerJson(write(options.stream().map(Option::key).filter(answers::contains).toList()));
    }

    private QuestionResponse response(Question question) {
        return new QuestionResponse(question.getId().toString(), question.getCourseId().toString(),
                question.getType(), question.getStem(), readOptions(question.getOptionsJson()),
                readAnswer(question.getCorrectAnswerJson()), question.getScore().toPlainString(), question.getExplanation());
    }
    private List<Option> readOptions(String value) { return read(value, new TypeReference<List<Option>>() {}); }
    private List<String> readAnswer(String value) { return read(value, new TypeReference<List<String>>() {}); }
    private <T> T read(String value, TypeReference<T> type) {
        if (value == null) return null;
        try { return json.readValue(value, type); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("题库 JSON 数据损坏", exception); }
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("题库 JSON 序列化失败", exception); }
    }
    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUESTION", message);
    }
}

