package com.rongwang.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rongwang.auth.model.UserRole;
import com.rongwang.auth.session.LoginUser;
import com.rongwang.auth.session.SessionKeys;
import com.rongwang.course.dto.CourseDtos.*;
import com.rongwang.course.service.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "COURSE_TEST_DB_URL", matches = ".+")
class CourseMysqlTests {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("COURSE_TEST_DB_URL");
        // 防止测试误用开发库：只接收明确命名的隔离库。
        if (!url.matches("jdbc:mysql://[^/]+/rongwang_test(\\?.*)?")) {
            throw new IllegalArgumentException("课程集成测试必须使用 rongwang_test");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("COURSE_TEST_DB_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("COURSE_TEST_DB_PASSWORD", ""));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired CourseService service;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactions;
    LoginUser teacher;
    LoginUser otherTeacher;
    LoginUser student;
    LoginUser otherStudent;

    @BeforeEach
    void accounts() {
        teacher = account(UserRole.TEACHER);
        otherTeacher = account(UserRole.TEACHER);
        student = account(UserRole.STUDENT);
        otherStudent = account(UserRole.STUDENT);
    }

    private LoginUser account(UserRole role) {
        String username = "test_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO rw_user(username,password_hash,display_name,role,status) VALUES (?,?,?,?,?)",
                username, encoder.encode("LocalTestOnly!123"), "课程测试", role.name(), "ACTIVE");
        Long id = jdbc.queryForObject("SELECT id FROM rw_user WHERE username=?", Long.class, username);
        return new LoginUser(id, username, "课程测试", role);
    }

    private MockHttpSession session(LoginUser user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.LOGIN_USER, user);
        return session;
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, LoginUser user)
            throws Exception {
        MockHttpSession session = session(user);
        JsonNode token = json.readTree(mvc.perform(get("/v1/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return request.session(session).header("X-CSRF-Token", token.path("data").path("token").asText());
    }

    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object value) throws Exception {
        return request.contentType("application/json").content(json.writeValueAsString(value));
    }

    private CourseResponse course() {
        return service.create(teacher, new CreateRequest("Java 基础", "课程介绍"));
    }

    @Test
    void fullDatabaseLoginCreatesCourseAndReturnsPersistedDetails() throws Exception {
        MockHttpSession loginSession = (MockHttpSession) mvc.perform(body(post("/v1/auth/login"),
                        Map.of("username", teacher.username(), "password", "LocalTestOnly!123")))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        String token = json.readTree(mvc.perform(get("/v1/auth/csrf").session(loginSession))
                .andReturn().getResponse().getContentAsString()).path("data").path("token").asText();
        JsonNode data = json.readTree(mvc.perform(body(post("/v1/teacher/courses"),
                        Map.of("name", " Java 入门 ", "teacherId", otherTeacher.id(), "status", "ARCHIVED"))
                        .session(loginSession).header("X-CSRF-Token", token))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.name").value("Java 入门"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString()).path("data");
        assertThat(data.path("id").isTextual()).isTrue();
        assertThat(jdbc.queryForObject("SELECT teacher_id FROM rw_course WHERE id=?", Long.class,
                data.path("id").asLong())).isEqualTo(teacher.id());
        mvc.perform(get("/v1/teacher/courses/" + data.path("id").asText()).session(loginSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.inviteCode").isNotEmpty());
    }

    @Test
    void unauthenticatedAndWrongRoleAreRejected() throws Exception {
        mvc.perform(get("/v1/teacher/courses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/teacher/courses").session(session(student))).andExpect(status().isForbidden());
        mvc.perform(authorized(body(post("/v1/teacher/courses"), Map.of("name","非法")), student))
                .andExpect(status().isForbidden());
        mvc.perform(authorized(body(post("/v1/student/course-memberships"), Map.of("inviteCode","unknown")), teacher))
                .andExpect(status().isForbidden());
    }

    @Test
    void anotherTeacherCannotReadModifyResetOrListMembers() throws Exception {
        String id = course().id();
        mvc.perform(get("/v1/teacher/courses/" + id).session(session(otherTeacher))).andExpect(status().isNotFound());
        mvc.perform(authorized(body(patch("/v1/teacher/courses/" + id), Map.of("name","篡改")), otherTeacher))
                .andExpect(status().isNotFound());
        mvc.perform(authorized(post("/v1/teacher/courses/" + id + "/invite-code/reset"), otherTeacher))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/teacher/courses/" + id + "/students").session(session(otherTeacher)))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherListIsScopedAndPaginated() throws Exception {
        course();
        course();
        service.create(otherTeacher, new CreateRequest("别人的课程", null));
        mvc.perform(get("/v1/teacher/courses?page=1&size=1").session(session(teacher)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(get("/v1/teacher/courses?page=2&size=1").session(session(teacher)))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void patchOnlyUpdatesSpecifiedDetails() throws Exception {
        var c = course();
        mvc.perform(authorized(body(patch("/v1/teacher/courses/" + c.id()),
                        Map.of("description", "", "teacherId", otherTeacher.id(), "inviteCode","hacked","status","ARCHIVED")), teacher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Java 基础"))
                .andExpect(jsonPath("$.data.description").value(""))
                .andExpect(jsonPath("$.data.teacherId").value(teacher.id().toString()))
                .andExpect(jsonPath("$.data.inviteCode").value(c.inviteCode()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void invalidInputAndPaginationUse400() throws Exception {
        for (String name : new String[]{"   ", "x".repeat(101)}) {
            mvc.perform(authorized(body(post("/v1/teacher/courses"), Map.of("name",name)), teacher))
                    .andExpect(status().isBadRequest());
        }
        for (String query : new String[]{"page=0", "size=101", "page=oops", "size=-1"}) {
            mvc.perform(get("/v1/teacher/courses?" + query).session(session(teacher)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/v1/teacher/courses/no-number").session(session(teacher)))
                .andExpect(status().isBadRequest());
        mvc.perform(authorized(body(patch("/v1/teacher/courses/" + course().id()), Map.of("name","  ")), teacher))
                .andExpect(status().isBadRequest());
    }

    @Test
    void csrfMissingOrWrongDoesNotCreateCourse() throws Exception {
        for (String token : new String[]{"", "wrong"}) {
            mvc.perform(body(post("/v1/teacher/courses"), Map.of("name","禁止请求"))
                            .session(session(teacher)).header("X-CSRF-Token", token))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_course WHERE teacher_id=?", Long.class, teacher.id()))
                .isZero();
    }

    @Test
    void studentJoinsDirectlyAndRetryReturnsSameMembership() throws Exception {
        var c = course();
        var request = Map.of("inviteCode", c.inviteCode());
        JsonNode first = json.readTree(mvc.perform(authorized(body(post("/v1/student/course-memberships"), request), student))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(authorized(body(post("/v1/student/course-memberships"), request), student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(first.path("data").path("id").asText()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_course_member WHERE course_id=?", Long.class,
                Long.valueOf(c.id()))).isEqualTo(1);
        mvc.perform(get("/v1/student/courses/" + c.id()).session(session(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.inviteCode").doesNotExist());
        mvc.perform(get("/v1/student/courses").session(session(student)))
                .andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/v1/student/courses").session(session(otherStudent)))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/v1/student/courses/" + c.id()).session(session(otherStudent)))
                .andExpect(status().isNotFound());
    }

    @Test
    void resetInvalidatesOldCodeButKeepsExistingMembers() throws Exception {
        var c = course();
        var membership = service.join(student, new JoinRequest(c.inviteCode()));
        JsonNode reset = json.readTree(mvc.perform(authorized(post("/v1/teacher/courses/" + c.id()
                        + "/invite-code/reset"), teacher)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
        String newCode = reset.path("inviteCode").asText();
        assertThat(newCode).isNotEqualTo(c.inviteCode());
        mvc.perform(authorized(body(post("/v1/student/course-memberships"), Map.of("inviteCode", c.inviteCode())), otherStudent))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INVITE_CODE"));
        assertThat(service.join(student, new JoinRequest(newCode)).id()).isEqualTo(membership.id());
        assertThat(service.join(otherStudent, new JoinRequest(newCode)).courseId()).isEqualTo(c.id());
    }

    @Test
    void archivedAndUnknownInvitesAreRejected() throws Exception {
        var c = course();
        jdbc.update("UPDATE rw_course SET status='ARCHIVED' WHERE id=?", Long.valueOf(c.id()));
        for (String code : new String[]{c.inviteCode(), "not-an-invite"}) {
            mvc.perform(authorized(body(post("/v1/student/course-memberships"), Map.of("inviteCode",code)), student))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_course_member WHERE course_id=?", Long.class,
                Long.valueOf(c.id()))).isZero();
    }

    @Test
    void memberListContainsOnlyPublicStudentFields() throws Exception {
        var c = course();
        service.join(student, new JoinRequest(c.inviteCode()));
        mvc.perform(get("/v1/teacher/courses/" + c.id() + "/students").session(session(teacher)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(student.id().toString()))
                .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist());
    }

    @Test
    void schemaHasExpectedTablesAndForeignKeys() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",
                Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE()",
                Integer.class)).isEqualTo(16);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousJoinsCreateOneMembership() throws Exception {
        var c = course();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<MemberResponse> join = () -> { start.await(); return service.join(student, new JoinRequest(c.inviteCode())); };
            Future<MemberResponse> a = pool.submit(join);
            Future<MemberResponse> b = pool.submit(join);
            start.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS).id()).isEqualTo(b.get(10, TimeUnit.SECONDS).id());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_course_member WHERE course_id=?", Long.class,
                    Long.valueOf(c.id()))).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void oldCodeWaitingForResetTransactionIsRejectedAfterCommit() throws Exception {
        var c = course();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch submitted = new CountDownLatch(1);
        try {
            Future<MemberResponse> future = new TransactionTemplate(transactions).execute(status -> {
                service.resetCode(teacher, Long.parseLong(c.id()));
                Future<MemberResponse> result = pool.submit(() -> {
                    submitted.countDown();
                    return service.join(student, new JoinRequest(c.inviteCode()));
                });
                try {
                    assertThat(submitted.await(3, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> result.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                } catch (InterruptedException e) { throw new RuntimeException(e); }
                return result;
            });
            assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasRootCauseMessage("邀请码无效或课程已归档");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_course_member WHERE course_id=?", Long.class,
                    Long.valueOf(c.id()))).isZero();
        } finally { pool.shutdownNow(); }
    }
}
