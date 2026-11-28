package com.rongwang.statistics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rongwang.auth.model.UserRole;
import com.rongwang.auth.session.*;
import com.rongwang.course.dto.CourseDtos.*;
import com.rongwang.course.service.CourseService;
import com.rongwang.question.dto.QuestionDtos.*;
import com.rongwang.question.service.QuestionService;
import com.rongwang.task.dto.TaskDtos.*;
import com.rongwang.task.service.TaskService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "COURSE_TEST_DB_URL", matches = ".+")
class StatisticsMysqlTests {
    @DynamicPropertySource
    static void db(DynamicPropertyRegistry registry) {
        String url = System.getenv("COURSE_TEST_DB_URL");
        if (!url.matches("jdbc:mysql://[^/]+/rongwang_test(\\?.*)?")) {
            throw new IllegalArgumentException("统计测试必须使用 rongwang_test");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("COURSE_TEST_DB_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("COURSE_TEST_DB_PASSWORD", ""));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired CourseService courses;
    @Autowired QuestionService bank;
    @Autowired TaskService tasks;

    LoginUser teacher;
    String taskId, inviteCode;

    @BeforeEach
    void setup() {
        teacher = account(UserRole.TEACHER);
        CourseResponse course = courses.create(teacher, new CreateRequest("统计测试课程", null));
        inviteCode = course.inviteCode();
        String questionId = bank.create(teacher, Long.parseLong(course.id()),
                new WriteRequest("SINGLE", "统计测试题",
                        List.of(new Option("A", "正确"), new Option("B", "错误")),
                        List.of("A"), new BigDecimal("2.00"), "解析")).id();
        taskId = tasks.create(teacher, Long.parseLong(course.id()),
                new WriteTask("统计测试任务", null,
                        OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(2).toString())).id();
        tasks.select(teacher, Long.parseLong(taskId),
                new SelectQuestions(List.of(new Selection(questionId, null, null))));
        tasks.publish(teacher, Long.parseLong(taskId));
    }

    private LoginUser account(UserRole role) {
        String name = "stat_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO rw_user(username,password_hash,display_name,role) VALUES (?,?,?,?)",
                name, "test-only", "统计测试账号", role.name());
        Long id = jdbc.queryForObject("SELECT id FROM rw_user WHERE username=?", Long.class, name);
        return new LoginUser(id, name, "统计测试账号", role);
    }

    private LoginUser member() {
        LoginUser student = account(UserRole.STUDENT);
        courses.join(student, new JoinRequest(inviteCode));
        return student;
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, LoginUser user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.LOGIN_USER, user);
        return request.session(session).header("X-CSRF-Token", CsrfProtection.token(session));
    }

    private String base() { return "/v1/teacher/tasks/" + taskId; }

    private JsonNode data(MockHttpServletRequestBuilder request) throws Exception {
        return json.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
    }

    @Test
    void emptyCourseStatistics() throws Exception {
        JsonNode result = data(as(get(base() + "/statistics"), teacher));
        assertThat(result.path("memberCount").asLong()).isZero();
        assertThat(result.path("submittedStudentCount").asLong()).isZero();
        assertThat(result.path("submissionRate").isNull()).isTrue();
        assertThat(result.path("averageScore").isNull()).isTrue();
        assertThat(result.path("accuracy").isNull()).isTrue();
        assertThat(result.path("questionCount").asLong()).isEqualTo(1);
        assertThat(data(as(get(base() + "/students"), teacher)).path("total").asLong()).isZero();
    }

    @Test
    void membersWithoutSubmissions() throws Exception {
        member(); member();
        JsonNode result = data(as(get(base() + "/statistics"), teacher));
        assertThat(result.path("memberCount").asLong()).isEqualTo(2);
        assertThat(result.path("submittedStudentCount").asLong()).isZero();
        assertThat(result.path("submissionRate").asText()).isEqualTo("0.00");
        assertThat(result.path("averageScore").isNull()).isTrue();
        assertThat(result.path("accuracy").isNull()).isTrue();
        assertThat(result.path("correctQuestionCount").asLong()).isZero();
        assertThat(result.path("questionStatistics").get(0).path("accuracy").isNull()).isTrue();
        JsonNode students = data(as(get(base() + "/students"), teacher));
        assertThat(students.path("total").asLong()).isEqualTo(2);
        for (JsonNode student : students.path("items")) {
            assertThat(student.path("submissionCount").asInt()).isZero();
            assertThat(student.path("bestScore").isNull()).isTrue();
            assertThat(student.path("representativeSubmissionId").isNull()).isTrue();
        }
    }

    @Test
    void otherTeacherCannotAccess() throws Exception {
        LoginUser other = account(UserRole.TEACHER);
        for (String endpoint : List.of("/statistics", "/students")) {
            mvc.perform(as(get(base() + endpoint), other)).andExpect(status().isNotFound());
        }
    }

    @Test
    void studentCannotAccess() throws Exception {
        LoginUser student = member();
        for (String endpoint : List.of("/statistics", "/students")) {
            mvc.perform(as(get(base() + endpoint), student)).andExpect(status().isForbidden());
        }
    }

    @Test
    void studentListPagination() throws Exception {
        LoginUser first = member(), second = member(), third = member();
        JsonNode page1 = data(as(get(base() + "/students").param("page", "1").param("size", "2"), teacher));
        JsonNode page2 = data(as(get(base() + "/students").param("page", "2").param("size", "2"), teacher));
        assertThat(page1.path("total").asLong()).isEqualTo(3);
        assertThat(page2.path("total").asLong()).isEqualTo(3);
        assertThat(page1.path("items").size()).isEqualTo(2);
        assertThat(page2.path("items").size()).isEqualTo(1);
        assertThat(page1.path("items").get(0).path("studentId").asText()).isEqualTo(Long.toString(first.id()));
        assertThat(page1.path("items").get(1).path("studentId").asText()).isEqualTo(Long.toString(second.id()));
        assertThat(page2.path("items").get(0).path("studentId").asText()).isEqualTo(Long.toString(third.id()));
    }

    @Test
    void invalidPagination() throws Exception {
        for (String[] values : new String[][] {{"0", "20"}, {"1", "0"}, {"1", "101"}}) {
            mvc.perform(as(get(base() + "/students").param("page", values[0]).param("size", values[1]), teacher))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGE"));
        }
    }

    @Test
    void missingTask() throws Exception {
        for (String endpoint : List.of("/statistics", "/students")) {
            mvc.perform(as(get("/v1/teacher/tasks/0" + endpoint), teacher))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
        }
    }
}
