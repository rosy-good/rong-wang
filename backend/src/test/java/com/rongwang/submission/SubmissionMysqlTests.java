package com.rongwang.submission;

import com.fasterxml.jackson.databind.*;
import com.rongwang.auth.model.UserRole;
import com.rongwang.auth.session.*;
import com.rongwang.course.dto.CourseDtos.*;
import com.rongwang.course.service.CourseService;
import com.rongwang.question.dto.QuestionDtos.*;
import com.rongwang.question.service.QuestionService;
import com.rongwang.task.dto.TaskDtos.*;
import com.rongwang.task.service.TaskService;
import com.rongwang.submission.service.SubmissionService;
import com.rongwang.submission.dto.SubmissionDtos.*;
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
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="COURSE_TEST_DB_URL",matches=".+")
class SubmissionMysqlTests {
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        String url=System.getenv("COURSE_TEST_DB_URL");
        if(!url.matches("jdbc:mysql://[^/]+/rongwang_test(\\?.*)?"))
            throw new IllegalArgumentException("提交测试只能使用 rongwang_test");
        r.add("spring.datasource.url",()->url);
        r.add("spring.datasource.username",()->System.getenv().getOrDefault("COURSE_TEST_DB_USERNAME","root"));
        r.add("spring.datasource.password",()->System.getenv().getOrDefault("COURSE_TEST_DB_PASSWORD",""));
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired CourseService courses;
    @Autowired QuestionService bank;
    @Autowired TaskService tasks;
    @Autowired SubmissionService submissions;
    LoginUser teacher,student,other;
    String courseId,taskId,singleId,multiId;
    @BeforeEach void setup() {
        teacher=account(UserRole.TEACHER); student=account(UserRole.STUDENT); other=account(UserRole.STUDENT);
        CourseResponse course=courses.create(teacher,new CreateRequest("提交测试",null));
        courseId=course.id(); courses.join(student,new JoinRequest(course.inviteCode()));
        String q1=bank.create(teacher,Long.parseLong(courseId),new WriteRequest("SINGLE","单选",
                options(),List.of("A"),new BigDecimal("2.00"),"单选解析")).id();
        String q2=bank.create(teacher,Long.parseLong(courseId),new WriteRequest("MULTIPLE","多选",
                options(),List.of("A","C"),new BigDecimal("4.00"),"多选解析")).id();
        String until=OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(2).toString();
        TaskView task=tasks.create(teacher,Long.parseLong(courseId),new WriteTask("正式测试",null,until));
        taskId=task.id();
        TaskView selected=tasks.select(teacher,Long.parseLong(taskId),new SelectQuestions(
                List.of(new Selection(q1,null,null),new Selection(q2,null,null))));
        singleId=selected.questions().get(0).taskQuestionId();
        multiId=selected.questions().get(1).taskQuestionId();
        tasks.publish(teacher,Long.parseLong(taskId));
    }
    private List<Option> options() {return List.of(new Option("A","甲"),new Option("B","乙"),new Option("C","丙"));}
    private LoginUser account(UserRole role) {
        String username="sub_"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO rw_user(username,password_hash,display_name,role) VALUES (?,?,?,?)",
                username,"test-only","测试",role.name());
        Long id=jdbc.queryForObject("SELECT id FROM rw_user WHERE username=?",Long.class,username);
        return new LoginUser(id,username,"测试",role);
    }
    private MockHttpSession session(LoginUser user) {
        MockHttpSession s=new MockHttpSession(); s.setAttribute(SessionKeys.LOGIN_USER,user); return s;
    }
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder r,LoginUser user) {
        MockHttpSession s=session(user); return r.session(s).header("X-CSRF-Token",CsrfProtection.token(s));
    }
    private MockHttpServletRequestBuilder request(String key,List<Map<String,Object>> answers,LoginUser user) throws Exception {
        return as(post("/v1/student/tasks/"+taskId+"/submissions"),user).contentType("application/json")
                .header("Idempotency-Key",key).content(json.writeValueAsBytes(Map.of("answers",answers)));
    }
    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request,Object value) throws Exception {
        return request.contentType("application/json").content(json.writeValueAsBytes(value));
    }
    private Map<String,Object> answer(String id,String... keys) {
        return Map.of("taskQuestionId",id,"selectedOptions",List.of(keys));
    }
    @Test void repeatedAttemptsUseHighestAndLatestTieWithoutDoubleCounting() throws Exception {
        JsonNode first=json.readTree(mvc.perform(request("one",List.of(answer(singleId,"A"),answer(multiId,"A")),student))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(first.path("totalScore").asText()).isEqualTo("2.00");
        assertThat(jdbc.queryForObject("SELECT wrong_count FROM rw_mistake WHERE student_id=?",Integer.class,student.id())).isEqualTo(1);
        mvc.perform(request("one",List.of(answer(multiId,"A"),answer(singleId,"A")),student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(first.path("id").asText()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_submission WHERE student_id=?",Long.class,student.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT wrong_count FROM rw_mistake WHERE student_id=?",Integer.class,student.id())).isEqualTo(1);
        mvc.perform(request("one",List.of(answer(singleId,"B")),student))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        JsonNode second=json.readTree(mvc.perform(request("two",List.of(answer(singleId,"A"),answer(multiId,"C","A")),student))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(second.path("totalScore").asText()).isEqualTo("6.00");
        JsonNode third=json.readTree(mvc.perform(request("three",List.of(answer(singleId,"A"),answer(multiId,"A","C")),student))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(third.path("bestScore").asText()).isEqualTo("6.00");
        assertThat(third.path("representativeSubmissionId").asText()).isEqualTo(third.path("id").asText());
        mvc.perform(request("four",List.of(answer(singleId,"B")),student)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.bestScore").value("6.00"))
                .andExpect(jsonPath("$.data.representativeSubmissionId").value(third.path("id").asText()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_submission_answer WHERE submission_id=?",Long.class,
                first.path("id").asLong())).isEqualTo(2);
    }
    @Test void omissionsAreZeroAndCreateMistakesButFormalCorrectDoesNotMaster() throws Exception {
        mvc.perform(request("empty",List.of(),student)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalScore").value("0.00"))
                .andExpect(jsonPath("$.data.answers.length()").value(2));
        mvc.perform(request("empty",List.of(answer(singleId),answer(multiId)),student))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_mistake WHERE student_id=?",Long.class,student.id())).isEqualTo(2);
        jdbc.update("UPDATE rw_mistake SET mastery_status='MASTERED' WHERE student_id=?",student.id());
        mvc.perform(request("correct",List.of(answer(singleId,"A"),answer(multiId,"A","C")),student))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_mistake WHERE student_id=? AND mastery_status='MASTERED'",
                Long.class,student.id())).isEqualTo(2);
        mvc.perform(request("againwrong",List.of(),student)).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT SUM(wrong_count) FROM rw_mistake WHERE student_id=?",Long.class,student.id())).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_mistake WHERE student_id=? AND mastery_status='UNMASTERED'",
                Long.class,student.id())).isEqualTo(2);
    }
    @Test void invalidOptionsAndUnauthorizedRequestsLeaveNoWrites() throws Exception {
        for(List<Map<String,Object>> answers:List.of(
                List.of(answer(multiId,"A","A")),List.of(answer(multiId,"D")),
                List.of(answer(singleId,"A","B")),
                List.of(answer(singleId,"A"),answer(singleId,"B")),
                List.of(answer("999999","A")))) {
            mvc.perform(request(UUID.randomUUID().toString(),answers,student)).andExpect(status().isBadRequest());
        }
        mvc.perform(request("other",List.of(),other)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_submission WHERE task_id=?",Long.class,taskId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_mistake WHERE student_id=?",Long.class,student.id())).isZero();
    }
    @Test void deadlineAndHistoryDisclosureFollowRules() throws Exception {
        JsonNode created=json.readTree(mvc.perform(request("good",List.of(answer(singleId,"A")),student))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data");
        jdbc.update("UPDATE rw_task SET deadline_at=DATE_SUB(NOW(3),INTERVAL 1 SECOND) WHERE id=?",taskId);
        mvc.perform(request("new",List.of(),student)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_EXPIRED"));
        mvc.perform(request("good",List.of(answer(singleId,"A")),student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(created.path("id").asText()));
        JsonNode omitted=json.readTree(mvc.perform(request("omitted",List.of(),student))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString());
        assertThat(omitted.path("code").asText()).isEqualTo("TASK_EXPIRED");
        mvc.perform(as(get("/v1/student/submissions/"+created.path("id").asText()),other)).andExpect(status().isNotFound());
        mvc.perform(as(get("/v1/student/submissions/"+created.path("id").asText()),student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answers[0].correctAnswer[0]").value("A"))
                .andExpect(jsonPath("$.data.answers[0].explanation").value("单选解析"));
        mvc.perform(as(get("/v1/student/submissions").param("courseId",courseId),student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(as(get("/v1/teacher/tasks/"+taskId+"/submissions"),teacher)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }
    @Test void mistakePracticeUpdatesMasteryAndKeepsHistoryWithoutChangingFormalScore() throws Exception {
        mvc.perform(request("wrong",List.of(),student)).andExpect(status().isCreated());
        Long mistakeId=jdbc.queryForObject("SELECT id FROM rw_mistake WHERE student_id=? ORDER BY id LIMIT 1",
                Long.class,student.id());
        String base="/v1/student/mistakes/"+mistakeId;
        JsonNode practiceQuestion=json.readTree(mvc.perform(as(get(base+"/practice-question"),student))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String snapshotId=practiceQuestion.path("data").path("taskQuestionId").asText();
        assertThat(practiceQuestion.toString()).doesNotContain("correctAnswer","explanation");
        mvc.perform(as(get(base),other)).andExpect(status().isNotFound());
        mvc.perform(as(get("/v1/student/mistakes").param("courseId",courseId),student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
        Map<String,Object> wrong=Map.of("taskQuestionId",snapshotId,"selectedOptions",List.of("B"));
        MockHttpServletRequestBuilder wrongRequest=as(post(base+"/practices"),student)
                .header("Idempotency-Key","practice-wrong").contentType("application/json")
                .content(json.writeValueAsBytes(wrong));
        mvc.perform(wrongRequest).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.correct").value(false));
        mvc.perform(as(post(base+"/practices"),student).header("Idempotency-Key","practice-wrong")
                .contentType("application/json").content(json.writeValueAsBytes(wrong))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT wrong_count FROM rw_mistake WHERE id=?",Integer.class,mistakeId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT last_wrong_task_question_id FROM rw_mistake WHERE id=?",
                Long.class,mistakeId)).isEqualTo(Long.parseLong(singleId));
        mvc.perform(as(post(base+"/practices"),student).header("Idempotency-Key","practice-correct")
                .contentType("application/json").content(json.writeValueAsBytes(
                        Map.of("taskQuestionId",snapshotId,"selectedOptions",List.of("A")))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.correct").value(true));
        mvc.perform(as(get(base+"/practices"),student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
        assertThat(jdbc.queryForObject("SELECT mastery_status FROM rw_mistake WHERE id=?",String.class,mistakeId))
                .isEqualTo("MASTERED");
        mvc.perform(as(body(patch(base),Map.of("masteryStatus","UNMASTERED")),student))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_submission WHERE student_id=?",Long.class,student.id()))
                .isEqualTo(1);
    }
    @Test void teacherStatisticsUseHighestRepresentativeAndCurrentMembers() throws Exception {
        String invite=jdbc.queryForObject("SELECT invite_code FROM rw_course WHERE id=?",String.class,courseId);
        courses.join(other,new JoinRequest(invite));
        LoginUser newcomer=account(UserRole.STUDENT);
        courses.join(newcomer,new JoinRequest(invite));
        mvc.perform(request("score2",List.of(answer(singleId,"A")),student)).andExpect(status().isCreated());
        mvc.perform(request("score6-first",List.of(answer(singleId,"A"),answer(multiId,"A","C")),student))
                .andExpect(status().isCreated());
        JsonNode representative=json.readTree(mvc.perform(request("score6-last",
                List.of(answer(singleId,"A"),answer(multiId,"C","A")),student))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data");
        mvc.perform(request("score4",List.of(answer(singleId,"B"),answer(multiId,"A","C")),other))
                .andExpect(status().isCreated());
        mvc.perform(request("score0",List.of(),student)).andExpect(status().isCreated());
        mvc.perform(as(get("/v1/teacher/tasks/"+taskId+"/statistics"),teacher)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberCount").value(3))
                .andExpect(jsonPath("$.data.submittedStudentCount").value(2))
                .andExpect(jsonPath("$.data.submissionRate").value("66.67"))
                .andExpect(jsonPath("$.data.averageScore").value("5.00"))
                .andExpect(jsonPath("$.data.accuracy").value("75.00"))
                .andExpect(jsonPath("$.data.correctQuestionCount").value(3))
                .andExpect(jsonPath("$.data.questionStatistics[0].accuracy").value("50.00"))
                .andExpect(jsonPath("$.data.questionStatistics[1].accuracy").value("100.00"));
        mvc.perform(as(get("/v1/teacher/tasks/"+taskId+"/students"),teacher)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].bestScore").value("6.00"))
                .andExpect(jsonPath("$.data.items[0].representativeSubmissionId")
                        .value(representative.path("id").asText()))
                .andExpect(jsonPath("$.data.items[2].submissionCount").value(0));
        mvc.perform(as(get("/v1/teacher/tasks/"+taskId+"/statistics"),student)).andExpect(status().isForbidden());
    }
    @Test void concurrentSameKeyCreatesOneSubmissionAndOneMistakeIncrement() throws Exception {
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM rw_submission WHERE student_id=?",Integer.class,student.id());
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2), start=new CountDownLatch(1);
        try {
            Callable<SubmitOutcome> action=()->{
                ready.countDown(); start.await();
                return submissions.submit(student,Long.parseLong(taskId),"same-key",
                        new SubmitRequest(List.of()),LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
            };
            Future<SubmitOutcome> first=pool.submit(action), second=pool.submit(action);
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();
            start.countDown();
            SubmitOutcome a=first.get(10,TimeUnit.SECONDS), b=second.get(10,TimeUnit.SECONDS);
            assertThat(a.result().id()).isEqualTo(b.result().id());
            assertThat(a.created() ^ b.created()).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rw_submission WHERE student_id=?",Integer.class,student.id()))
                    .isEqualTo(before+1);
            assertThat(jdbc.queryForObject("SELECT SUM(wrong_count) FROM rw_mistake WHERE student_id=?",Long.class,student.id()))
                    .isEqualTo(2);
        } finally {pool.shutdownNow();}
    }
    @Test void exactDeadlineRejectsNewRequestButEarlierArrivalCanFinishLater() {
        LocalDateTime deadline=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusSeconds(1);
        jdbc.update("UPDATE rw_task SET deadline_at=? WHERE id=?",java.sql.Timestamp.valueOf(deadline),taskId);
        deadline=jdbc.queryForObject("SELECT deadline_at FROM rw_task WHERE id=?",
                LocalDateTime.class,taskId);
        LocalDateTime storedDeadline=deadline;
        assertThatThrownBy(()->submissions.submit(student,Long.parseLong(taskId),"equal",
                new SubmitRequest(List.of()),storedDeadline)).isInstanceOf(com.rongwang.common.exception.ApiException.class)
                .hasMessageContaining("截止");
        SubmitOutcome accepted=submissions.submit(student,Long.parseLong(taskId),"earlier",
                new SubmitRequest(List.of()),deadline.minusNanos(1));
        assertThat(accepted.created()).isTrue();
        SubmitOutcome retried=submissions.submit(student,Long.parseLong(taskId),"earlier",
                new SubmitRequest(List.of()),deadline.plusMinutes(1));
        assertThat(retried.result().id()).isEqualTo(accepted.result().id());
    }
}

