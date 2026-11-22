package com.rongwang.task;

import com.fasterxml.jackson.databind.*;
import com.rongwang.auth.model.UserRole;
import com.rongwang.auth.session.*;
import com.rongwang.course.dto.CourseDtos.*;
import com.rongwang.course.service.CourseService;
import com.rongwang.question.dto.QuestionDtos.*;
import com.rongwang.question.service.QuestionService;
import com.rongwang.task.dto.TaskDtos.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
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
@EnabledIfEnvironmentVariable(named="COURSE_TEST_DB_URL",matches=".+")
class TaskMysqlTests {
    @DynamicPropertySource
    static void db(DynamicPropertyRegistry registry) {
        String url=System.getenv("COURSE_TEST_DB_URL");
        if(!url.matches("jdbc:mysql://[^/]+/rongwang_test(\\?.*)?"))
            throw new IllegalArgumentException("任务测试必须使用 rongwang_test");
        registry.add("spring.datasource.url",()->url);
        registry.add("spring.datasource.username",()->System.getenv().getOrDefault("COURSE_TEST_DB_USERNAME","root"));
        registry.add("spring.datasource.password",()->System.getenv().getOrDefault("COURSE_TEST_DB_PASSWORD",""));
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired CourseService courses;
    @Autowired QuestionService bank;
    LoginUser teacher,other,student,stranger;
    String courseId,otherCourseId,questionId,otherQuestionId;
    @BeforeEach void setup() {
        teacher=account(UserRole.TEACHER); other=account(UserRole.TEACHER);
        student=account(UserRole.STUDENT); stranger=account(UserRole.STUDENT);
        CourseResponse course=courses.create(teacher,new CreateRequest("本课程",null));
        courseId=course.id();
        otherCourseId=courses.create(other,new CreateRequest("别人的课程",null)).id();
        questionId=bank.create(teacher,Long.parseLong(courseId),question("A","旧题干")).id();
        otherQuestionId=bank.create(other,Long.parseLong(otherCourseId),question("A","外课题")).id();
        courses.join(student,new JoinRequest(course.inviteCode()));
    }
    private LoginUser account(UserRole role) {
        String name="task_"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO rw_user(username,password_hash,display_name,role) VALUES (?,?,?,?)",
                name,"test-only","测试账号",role.name());
        Long id=jdbc.queryForObject("SELECT id FROM rw_user WHERE username=?",Long.class,name);
        return new LoginUser(id,name,"测试账号",role);
    }
    private WriteRequest question(String answer,String stem) {
        return new WriteRequest("SINGLE",stem,List.of(new Option("A","正确"),new Option("B","错误")),
                List.of(answer),new BigDecimal("2.50"),"旧解析");
    }
    private MockHttpSession session(LoginUser user) {
        MockHttpSession s=new MockHttpSession(); s.setAttribute(SessionKeys.LOGIN_USER,user); return s;
    }
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request,LoginUser user) {
        MockHttpSession s=session(user); return request.session(s).header("X-CSRF-Token",CsrfProtection.token(s));
    }
    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request,Object value) throws Exception {
        return request.contentType("application/json").content(json.writeValueAsBytes(value));
    }
    private JsonNode result(ResultActions action) throws Exception {
        return json.readTree(action.andReturn().getResponse().getContentAsString()).path("data");
    }
    private String deadline() { return OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(2).toString(); }
    private JsonNode draft() throws Exception {
        return result(mvc.perform(as(body(post("/v1/teacher/courses/"+courseId+"/tasks"),
                new WriteTask("练习一","说明",deadline())),teacher)).andExpect(status().isCreated()));
    }
    private JsonNode select(String taskId,List<Selection> entries) throws Exception {
        return result(mvc.perform(as(body(put("/v1/teacher/tasks/"+taskId+"/questions"),
                new SelectQuestions(entries)),teacher)).andExpect(status().isOk()));
    }
    private JsonNode publish(String id) throws Exception {
        return result(mvc.perform(as(post("/v1/teacher/tasks/"+id+"/publish"),teacher)).andExpect(status().isOk()));
    }
    @Test void publishPreservesEditedDraftSnapshotAndLocksAllMutations() throws Exception {
        String id=draft().path("id").asText();
        JsonNode selected=select(id,List.of(new Selection(questionId,null,
                question("B","草稿修改题干"))));
        assertThat(selected.path("questions").get(0).path("stem").asText()).isEqualTo("草稿修改题干");
        JsonNode released=publish(id);
        assertThat(released.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(released.path("totalScore").asText()).isEqualTo("2.50");
        assertThat(released.path("questions").get(0).path("correctAnswer").get(0).asText()).isEqualTo("B");
        bank.update(teacher,Long.parseLong(questionId),question("A","后改原题"));
        JsonNode after=result(mvc.perform(as(get("/v1/teacher/tasks/"+id),teacher)).andExpect(status().isOk()));
        assertThat(after.path("questions")).isEqualTo(released.path("questions"));
        mvc.perform(as(body(patch("/v1/teacher/tasks/"+id),Map.of("title","篡改")),teacher))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TASK_LOCKED"));
        mvc.perform(as(body(put("/v1/teacher/tasks/"+id+"/questions"),new SelectQuestions(List.of())),teacher))
                .andExpect(status().isConflict());
        mvc.perform(as(post("/v1/teacher/tasks/"+id+"/publish"),teacher)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_task_question WHERE task_id=?",Long.class,id)).isEqualTo(1);
    }
    @Test void copyCreatesIndependentDraftAndNewPublication() throws Exception {
        String id=draft().path("id").asText();
        select(id,List.of(new Selection(questionId,null,null)));
        publish(id);
        JsonNode clone=result(mvc.perform(as(post("/v1/teacher/tasks/"+id+"/copies"),teacher))
                .andExpect(status().isCreated()));
        String newId=clone.path("id").asText();
        String cloneQuestionId=clone.path("questions").get(0).path("taskQuestionId").asText();
        assertThat(newId).isNotEqualTo(id);
        select(newId,List.of(new Selection(questionId,cloneQuestionId,question("B","副本新题干"))));
        publish(newId);
        assertThat(jdbc.queryForObject("SELECT stem FROM rw_task_question WHERE task_id=?",String.class,id))
                .isEqualTo("旧题干");
        assertThat(jdbc.queryForObject("SELECT stem FROM rw_task_question WHERE task_id=?",String.class,newId))
                .isEqualTo("副本新题干");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_submission WHERE task_id=?",Long.class,newId)).isZero();
    }
    @Test void rejectsForeignQuestionAndSnapshotForgeryWithoutPartialWrite() throws Exception {
        String id=draft().path("id").asText();
        mvc.perform(as(body(put("/v1/teacher/tasks/"+id+"/questions"),
                new SelectQuestions(List.of(new Selection(questionId,null,null),new Selection(otherQuestionId,null,null)))),teacher))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_task_question WHERE task_id=?",Long.class,id)).isZero();
        JsonNode chosen=select(id,List.of(new Selection(questionId,null,null)));
        String snapshot=chosen.path("questions").get(0).path("taskQuestionId").asText();
        mvc.perform(as(body(put("/v1/teacher/tasks/"+id+"/questions"),
                new SelectQuestions(List.of(new Selection(questionId,snapshot,null),new Selection(questionId,null,null)))),teacher))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_task_question WHERE task_id=?",Long.class,id)).isEqualTo(1);
        String another=draft().path("id").asText();
        mvc.perform(as(body(put("/v1/teacher/tasks/"+another+"/questions"),
                new SelectQuestions(List.of(new Selection(questionId,snapshot,null)))),teacher))
                .andExpect(status().isBadRequest());
    }
    @Test void studentsSeeOnlyPublishedTasksAndNoAnswers() throws Exception {
        String id=draft().path("id").asText();
        select(id,List.of(new Selection(questionId,null,null)));
        mvc.perform(as(get("/v1/student/tasks/"+id),student)).andExpect(status().isNotFound());
        mvc.perform(as(get("/v1/student/courses/"+courseId+"/tasks"),student))
                .andExpect(jsonPath("$.data.total").value(0));
        publish(id);
        String raw=mvc.perform(as(get("/v1/student/tasks/"+id),student)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(raw).doesNotContain("correctAnswer","旧解析","questionId");
        assertThat(json.readTree(raw).path("data").path("questions").get(0).path("stem").asText()).isEqualTo("旧题干");
        mvc.perform(as(get("/v1/student/tasks/"+id),stranger)).andExpect(status().isNotFound());
        mvc.perform(as(get("/v1/student/courses/"+courseId+"/tasks"),student))
                .andExpect(jsonPath("$.data.total").value(1));
    }
    @Test void ownershipCsrfDeadlineAndEmptySelectionAreEnforced() throws Exception {
        String id=draft().path("id").asText();
        mvc.perform(as(get("/v1/teacher/tasks/"+id),other)).andExpect(status().isNotFound());
        mvc.perform(as(body(patch("/v1/teacher/tasks/"+id),Map.of("title","越权")),other)).andExpect(status().isNotFound());
        mvc.perform(body(patch("/v1/teacher/tasks/"+id),Map.of("title","无令牌")).session(session(teacher)))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/v1/teacher/tasks/"+id+"/publish"),teacher)).andExpect(status().isBadRequest());
        select(id,List.of(new Selection(questionId,null,null)));
        mvc.perform(as(body(patch("/v1/teacher/tasks/"+id),
                new WriteTask(null,null,OffsetDateTime.now(ZoneOffset.ofHours(8)).minusDays(1).toString())),teacher))
                .andExpect(status().isOk());
        mvc.perform(as(post("/v1/teacher/tasks/"+id+"/publish"),teacher)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT status FROM rw_task WHERE id=?",String.class,id)).isEqualTo("DRAFT");
    }
    @Test void totalScoreOverflowAndMalformedSelectionDoNotReplaceDraft() throws Exception {
        String id=draft().path("id").asText();
        String second=bank.create(teacher,Long.parseLong(courseId),question("B","第二题")).id();
        select(id,List.of(new Selection(questionId,null,null)));
        WriteRequest expensive=new WriteRequest("SINGLE","高分题",
                List.of(new Option("A","是"),new Option("B","否")),List.of("A"),
                new BigDecimal("999999.99"),"解析");
        mvc.perform(as(body(put("/v1/teacher/tasks/"+id+"/questions"),new SelectQuestions(List.of(
                new Selection(questionId,null,expensive),new Selection(second,null,expensive)))),teacher))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TASK"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_task_question WHERE task_id=?",Long.class,id))
                .isEqualTo(1);
        mvc.perform(as(body(put("/v1/teacher/tasks/"+id+"/questions"),
                new SelectQuestions(List.of(new Selection(questionId,"999999",null)))),teacher))
                .andExpect(status().isBadRequest());
    }
}

