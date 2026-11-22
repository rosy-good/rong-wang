package com.rongwang.question;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rongwang.auth.model.UserRole;
import com.rongwang.auth.session.*;
import com.rongwang.course.dto.CourseDtos.CreateRequest;
import com.rongwang.course.service.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "COURSE_TEST_DB_URL", matches = ".+")
class QuestionMysqlTests {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("COURSE_TEST_DB_URL");
        if (!url.matches("jdbc:mysql://[^/]+/rongwang_test(\\?.*)?"))
            throw new IllegalArgumentException("题库集成测试必须使用 rongwang_test");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("COURSE_TEST_DB_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("COURSE_TEST_DB_PASSWORD", ""));
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired CourseService courses;
    LoginUser teacher, otherTeacher, student;
    String courseId, secondCourseId;

    @BeforeEach
    void setup() {
        teacher = account(UserRole.TEACHER);
        otherTeacher = account(UserRole.TEACHER);
        student = account(UserRole.STUDENT);
        courseId = courses.create(teacher, new CreateRequest("Java 题库测试", null)).id();
        secondCourseId = courses.create(teacher, new CreateRequest("另一课程", null)).id();
    }
    private LoginUser account(UserRole role) {
        String name = "q_" + UUID.randomUUID().toString().replace("-", "");
        // 本组验证题库权限，登录密码验证由认证测试覆盖；此占位账号不用于登录。
        jdbc.update("INSERT INTO rw_user(username,password_hash,display_name,role) VALUES (?,?,?,?)",
                name, "unused-test-hash", "题库测试", role.name());
        return new LoginUser(jdbc.queryForObject("SELECT id FROM rw_user WHERE username=?", Long.class, name),
                name, "题库测试", role);
    }
    private MockHttpSession session(LoginUser user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.LOGIN_USER, user);
        return session;
    }
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, LoginUser user) {
        MockHttpSession session = session(user);
        return request.session(session).header("X-CSRF-Token", CsrfProtection.token(session));
    }
    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object value) throws Exception {
        return request.contentType("application/json").content(json.writeValueAsString(value));
    }
    private static List<Map<String, String>> options() {
        return List.of(Map.of("key", "A", "text", "int"), Map.of("key", "B", "text", "String"),
                Map.of("key", "C", "text", "long"));
    }
    private static Map<String, Object> valid() {
        Map<String, Object> result = new HashMap<>();
        result.put("type", "SINGLE");
        result.put("stem", "Java 基本类型有哪些？");
        result.put("options", options());
        result.put("correctAnswer", List.of("A"));
        result.put("score", "2.50");
        result.put("explanation", "int 是基本类型");
        return result;
    }
    private JsonNode create(String course, Map<String, Object> value) throws Exception {
        return json.readTree(mvc.perform(as(body(post("/v1/teacher/courses/" + course + "/questions"), value), teacher))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data");
    }
    private JsonNode detail(String id) throws Exception {
        return json.readTree(mvc.perform(as(get("/v1/teacher/questions/" + id), teacher))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }

    @Test
    void threeQuestionTypesRoundTripThroughMysql() throws Exception {
        JsonNode single = create(courseId, valid());
        assertThat(single.path("id").isTextual()).isTrue();
        assertThat(detail(single.path("id").asText())).isEqualTo(single);
        assertThat(jdbc.queryForObject("SELECT score FROM rw_question WHERE id=?", BigDecimal.class,
                single.path("id").asLong())).isEqualByComparingTo("2.50");

        Map<String, Object> multiple = valid();
        multiple.put("type", "MULTIPLE");
        multiple.put("correctAnswer", List.of("C", "A"));
        multiple.put("score", "999999.99");
        JsonNode multi = create(courseId, multiple);
        assertThat(detail(multi.path("id").asText()).path("correctAnswer").toString()).isEqualTo("[\"A\",\"C\"]");
        assertThat(multi.path("score").asText()).isEqualTo("999999.99");

        Map<String, Object> judgment = valid();
        judgment.put("type", "JUDGMENT");
        judgment.put("options", List.of(Map.of("key", "A", "text", "正确"), Map.of("key", "B", "text", "错误")));
        judgment.put("correctAnswer", List.of("B"));
        judgment.put("score", "0.01");
        JsonNode judge = create(courseId, judgment);
        assertThat(detail(judge.path("id").asText()).path("correctAnswer").get(0).asText()).isEqualTo("B");
    }

    @Test
    void listScopesCourseAndCombinesSearchTypeAndPagination() throws Exception {
        create(courseId, valid());
        Map<String, Object> multi = valid();
        multi.put("type", "MULTIPLE");
        multi.put("correctAnswer", List.of("A", "C"));
        JsonNode first = create(courseId, multi);
        JsonNode second = create(courseId, multi);
        create(secondCourseId, multi);
        mvc.perform(as(get("/v1/teacher/courses/" + courseId + "/questions")
                        .param("type", "MULTIPLE").param("keyword", "基本").param("page", "2").param("size", "1"), teacher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(first.path("id").asText()));
        mvc.perform(as(get("/v1/teacher/courses/" + courseId + "/questions").param("size", "1"), teacher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].id").value(second.path("id").asText()));
        // 用户输入按字面搜索，不把 % 和 SQL 片段当通配符或 SQL 执行。
        for (String keyword : List.of("%", "' OR 1=1 --", "不存在的题干")) {
            mvc.perform(as(get("/v1/teacher/courses/" + courseId + "/questions").param("keyword", keyword), teacher))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        }
    }

    @Test
    void unauthorizedRolesAndOtherTeacherCannotReadAnswersOrWrite() throws Exception {
        String id = create(courseId, valid()).path("id").asText();
        String list = "/v1/teacher/courses/" + courseId + "/questions";
        mvc.perform(get(list)).andExpect(status().isUnauthorized());
        mvc.perform(body(post(list), valid())).andExpect(status().isUnauthorized());
        for (LoginUser user : List.of(student, otherTeacher)) {
            int expected = user.role() == UserRole.STUDENT ? 403 : 404;
            mvc.perform(as(get(list), user)).andExpect(status().is(expected)).andExpect(jsonPath("$.data").isEmpty());
            mvc.perform(as(get("/v1/teacher/questions/" + id), user))
                    .andExpect(status().is(expected)).andExpect(jsonPath("$.data").isEmpty());
            mvc.perform(as(body(post(list), valid()), user)).andExpect(status().is(expected));
            mvc.perform(as(body(patch("/v1/teacher/questions/" + id), Map.of("stem", "篡改")), user))
                    .andExpect(status().is(expected));
        }
        assertThat(detail(id).path("stem").asText()).isEqualTo("Java 基本类型有哪些？");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_question WHERE course_id=?", Long.class, courseId)).isEqualTo(1);
    }

    @Test
    void csrfRequiredForBothCreateAndEdit() throws Exception {
        String id = create(courseId, valid()).path("id").asText();
        mvc.perform(body(post("/v1/teacher/courses/" + courseId + "/questions"), valid()).session(session(teacher)))
                .andExpect(status().isForbidden());
        mvc.perform(body(patch("/v1/teacher/questions/" + id), Map.of("stem", "篡改")).session(session(teacher)))
                .andExpect(status().isForbidden());
        assertThat(detail(id).path("stem").asText()).isEqualTo("Java 基本类型有哪些？");
    }

    static Stream<Map<String, Object>> invalidQuestions() {
        List<Map<String, Object>> invalid = new ArrayList<>();
        for (String score : List.of("0", "-1", "1.001", "1000000", "1.000")) {
            Map<String, Object> value = valid(); value.put("score", score); invalid.add(value);
        }
        for (String type : List.of("ESSAY", "")) {
            Map<String, Object> value = valid(); value.put("type", type); invalid.add(value);
        }
        for (Object answer : List.of(List.of(), List.of("D"), List.of("A", "A"), List.of("A", "C"))) {
            Map<String, Object> value = valid(); value.put("correctAnswer", answer); invalid.add(value);
        }
        for (Object options : List.of(List.of(), List.of(Map.of("key", "A", "text", "only")),
                List.of(Map.of("key", "A", "text", "x"), Map.of("key", "A", "text", "y")),
                List.of(Map.of("key", "a", "text", "x"), Map.of("key", "B", "text", "y")),
                List.of(Map.of("key", "A", "text", " "), Map.of("key", "B", "text", "y")))) {
            Map<String, Object> value = valid(); value.put("options", options); invalid.add(value);
        }
        for (String stem : List.of("", "  ", "x".repeat(10001))) {
            Map<String, Object> value = valid(); value.put("stem", stem); invalid.add(value);
        }
        Map<String, Object> multiple = valid(); multiple.put("type", "MULTIPLE"); invalid.add(multiple);
        Map<String, Object> judgment = valid(); judgment.put("type", "JUDGMENT"); invalid.add(judgment);
        Map<String, Object> nullOption = valid(); nullOption.put("options", Arrays.asList(null, Map.of("key", "B", "text", "y"))); invalid.add(nullOption);
        Map<String, Object> nullAnswer = valid(); nullAnswer.put("correctAnswer", Arrays.asList((String) null)); invalid.add(nullAnswer);
        for (String missing : List.of("type", "stem", "options", "correctAnswer", "score")) {
            Map<String, Object> value = valid(); value.remove(missing); invalid.add(value);
        }
        return invalid.stream();
    }

    @ParameterizedTest
    @MethodSource("invalidQuestions")
    void invalidQuestionReturns400AndDoesNotInsert(Map<String, Object> value) throws Exception {
        mvc.perform(as(body(post("/v1/teacher/courses/" + courseId + "/questions"), value), teacher))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUESTION"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_question WHERE course_id=?", Long.class, courseId)).isZero();
    }

    @Test
    void partialUpdateValidatesMergedContentAndPreservesCourse() throws Exception {
        String id = create(courseId, valid()).path("id").asText();
        mvc.perform(as(body(patch("/v1/teacher/questions/" + id),
                        Map.of("explanation", "", "score", "3.25", "courseId", secondCourseId, "id", "0")), teacher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.courseId").value(courseId))
                .andExpect(jsonPath("$.data.explanation").value("")).andExpect(jsonPath("$.data.score").value("3.25"));
        JsonNode before = detail(id);
        mvc.perform(as(body(patch("/v1/teacher/questions/" + id), Map.of("type", "MULTIPLE", "stem", "不应保存")), teacher))
                .andExpect(status().isBadRequest());
        assertThat(detail(id)).isEqualTo(before);
        mvc.perform(as(body(patch("/v1/teacher/questions/" + id), Map.of("type", "MULTIPLE", "correctAnswer", List.of("A", "C"))), teacher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.type").value("MULTIPLE"));
        // 删除被选中的选项时必须同时修正答案。
        mvc.perform(as(body(patch("/v1/teacher/questions/" + id),
                        Map.of("options", options().subList(0, 2))), teacher)).andExpect(status().isBadRequest());
    }

    @Test
    void editingOriginalNeverRewritesPublishedSnapshot() throws Exception {
        String id = create(courseId, valid()).path("id").asText();
        jdbc.update("INSERT INTO rw_task(course_id,title,deadline_at,status,published_at,total_score) VALUES (?, ?, DATE_ADD(NOW(3), INTERVAL 1 DAY),'PUBLISHED',NOW(3),2.50)", courseId, "快照隔离测试");
        Long taskId = jdbc.queryForObject("SELECT id FROM rw_task WHERE course_id=? AND title=?", Long.class, courseId, "快照隔离测试");
        jdbc.update("""
                INSERT INTO rw_task_question(task_id,question_id,position,type,stem,options_json,correct_answer_json,score,explanation)
                SELECT ?,id,1,type,stem,options_json,correct_answer_json,score,explanation FROM rw_question WHERE id=?
                """, taskId, id);
        Map<String, Object> snapshot = jdbc.queryForMap("SELECT * FROM rw_task_question WHERE task_id=?", taskId);
        Map<String, Object> edited = valid();
        edited.put("type", "MULTIPLE"); edited.put("stem", "新的题干");
        edited.put("options", List.of(Map.of("key", "A", "text", "新选项1"), Map.of("key", "B", "text", "新选项2")));
        edited.put("correctAnswer", List.of("B", "A")); edited.put("score", "9.99"); edited.put("explanation", "新解析");
        mvc.perform(as(body(patch("/v1/teacher/questions/" + id), edited), teacher)).andExpect(status().isOk());
        assertThat(detail(id).path("stem").asText()).isEqualTo("新的题干");
        assertThat(jdbc.queryForMap("SELECT * FROM rw_task_question WHERE task_id=?", taskId)).isEqualTo(snapshot);
    }

    @Test
    void missingResourcesMalformedPayloadAndInvalidFiltersReturnClientErrors() throws Exception {
        mvc.perform(as(get("/v1/teacher/questions/9223372036854775807"), teacher)).andExpect(status().isNotFound());
        mvc.perform(as(get("/v1/teacher/courses/9223372036854775807/questions"), teacher)).andExpect(status().isNotFound());
        mvc.perform(as(get("/v1/teacher/questions/not-a-number"), teacher)).andExpect(status().isBadRequest());
        mvc.perform(as(post("/v1/teacher/courses/" + courseId + "/questions").contentType("application/json").content("{broken"), teacher))
                .andExpect(status().isBadRequest());
        for (Map.Entry<String, String> filter : Map.of("page", "0", "size", "101", "type", "OTHER", "keyword", "x".repeat(101)).entrySet()) {
            mvc.perform(as(get("/v1/teacher/courses/" + courseId + "/questions").param(filter.getKey(), filter.getValue()), teacher))
                    .andExpect(status().isBadRequest());
        }
    }
}

