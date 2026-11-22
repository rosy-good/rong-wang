package com.rongwang.submission.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rongwang.auth.model.UserRole;
import com.rongwang.auth.session.LoginUser;
import com.rongwang.common.api.PageResponse;
import com.rongwang.common.exception.ApiException;
import com.rongwang.course.service.CourseService;
import com.rongwang.submission.dto.SubmissionDtos.*;
import com.rongwang.task.mapper.TaskMapper;
import com.rongwang.task.model.Task;
import com.rongwang.task.model.TaskQuestion;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.*;
import java.util.*;

@Service
public class SubmissionService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate db;
    private final TaskMapper tasks;
    private final CourseService courses;
    private final ObjectMapper json;
    private final AnswerGrader grader;
    public SubmissionService(JdbcTemplate db,TaskMapper tasks,CourseService courses,ObjectMapper json,AnswerGrader grader) {
        this.db=db; this.tasks=tasks; this.courses=courses; this.json=json; this.grader=grader;
    }

    @Transactional
    public SubmitOutcome submit(LoginUser user,long taskId,String key,SubmitRequest body,LocalDateTime receivedAt) {
        requireStudent(user);
        // 在首次数据库一致性读之前串行化同一学生的提交，避免 REPEATABLE READ 旧视图。
        db.queryForObject("SELECT id FROM rw_user WHERE id=? FOR UPDATE",Long.class,user.id());
        Task task=tasks.selectById(taskId);
        if(task==null || !"PUBLISHED".equals(task.getStatus())) throw notFound();
        courses.studentDetail(user,task.getCourseId());
        if(key==null || key.isBlank() || key.length()>100) throw invalid("Idempotency-Key 必须为 1 到 100 字符");
        String hash=hash(taskId,body);
        // 已验证当前任务与成员权限后，查找成功幂等请求。
        List<Map<String,Object>> old=db.queryForList(
                "SELECT id,task_id,request_hash FROM rw_submission WHERE student_id=? AND idempotency_key=?",
                user.id(),key);
        if(!old.isEmpty()) {
            Map<String,Object> row=old.get(0);
            if(((Number)row.get("task_id")).longValue()!=taskId || !hash.equals(row.get("request_hash")))
                throw conflict();
            return new SubmitOutcome(result(((Number)row.get("id")).longValue()),false);
        }
        if(task.getDeadlineAt()==null || !receivedAt.isBefore(task.getDeadlineAt()))
            throw new ApiException(HttpStatus.CONFLICT,"TASK_EXPIRED","任务已截止，不能新提交");
        List<TaskQuestion> questions=tasks.questions(taskId);
        if(questions.isEmpty()) throw invalid("任务没有题目");
        Map<Long,TaskQuestion> byId=new HashMap<>();
        for(TaskQuestion q:questions) byId.put(q.getId(),q);
        Map<Long,List<String>> answers=new HashMap<>();
        if(body!=null && body.answers()!=null) for(AnswerInput item:body.answers()) {
            if(item==null) throw invalid("答案条目不能为空");
            long id=positive(item.taskQuestionId());
            if(!byId.containsKey(id) || answers.containsKey(id)) throw invalid("题号不属于任务或重复");
            answers.put(id,item.selectedOptions()==null?List.of():item.selectedOptions());
        }
        List<Judged> judged=new ArrayList<>();
        BigDecimal total=BigDecimal.ZERO;
        int correctCount=0;
        for(TaskQuestion q:questions) {
            List<String> selected=answers.getOrDefault(q.getId(),List.of());
            AnswerGrader.Grade grade=grader.grade(q,selected);
            judged.add(new Judged(q,grade.selected(),grade.correct(),grade.earned()));
            total=total.add(grade.earned());
            if(grade.correct()) correctCount++;
        }
        Integer next=db.queryForObject(
                "SELECT COALESCE(MAX(attempt_no),0)+1 FROM rw_submission WHERE task_id=? AND student_id=?",
                Integer.class,taskId,user.id());
        KeyHolder holder=new GeneratedKeyHolder();
        BigDecimal finalTotal=total;
        int finalCorrectCount=correctCount;
        db.update(connection->{
            PreparedStatement ps=connection.prepareStatement("""
                INSERT INTO rw_submission(task_id,student_id,received_at,attempt_no,total_score,
                correct_count,question_count,idempotency_key,request_hash) VALUES (?,?,?,?,?,?,?,?,?)
                """,Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1,taskId); ps.setLong(2,user.id()); ps.setTimestamp(3,Timestamp.valueOf(receivedAt));
            ps.setInt(4,next); ps.setBigDecimal(5,finalTotal); ps.setInt(6,finalCorrectCount);
            ps.setInt(7,questions.size()); ps.setString(8,key); ps.setString(9,hash); return ps;
        },holder);
        long submissionId=Objects.requireNonNull(holder.getKey()).longValue();
        for(Judged answer:judged) {
            TaskQuestion q=answer.question();
            db.update("""
                INSERT INTO rw_submission_answer(submission_id,task_question_id,answer_json,is_correct,earned_score)
                VALUES (?,?,?,?,?)
                """,submissionId,q.getId(),write(answer.selected()),answer.correct(),answer.earned());
            if(!answer.correct()) db.update("""
                INSERT INTO rw_mistake(student_id,question_id,last_wrong_task_question_id,
                wrong_count,last_wrong_at,mastery_status) VALUES (?,?,?,1,?,'UNMASTERED')
                ON DUPLICATE KEY UPDATE wrong_count=wrong_count+1,
                last_wrong_task_question_id=IF(VALUES(last_wrong_at)>=last_wrong_at,
                    VALUES(last_wrong_task_question_id),last_wrong_task_question_id),
                last_wrong_at=GREATEST(last_wrong_at,VALUES(last_wrong_at)),
                mastery_status='UNMASTERED',mastered_at=NULL
                """,user.id(),q.getQuestionId(),q.getId(),Timestamp.valueOf(receivedAt));
        }
        return new SubmitOutcome(result(submissionId),true);
    }

    public SubmissionResult studentDetail(LoginUser user,long id) {
        requireStudent(user);
        Map<String,Object> row=header(id);
        if(((Number)row.get("student_id")).longValue()!=user.id()) throw notFound();
        return result(id);
    }
    public SubmissionResult teacherDetail(LoginUser user,long id) {
        Map<String,Object> row=header(id);
        Task task=tasks.selectById(((Number)row.get("task_id")).longValue());
        courses.teacherDetail(user,task.getCourseId());
        return result(id);
    }
    public PageResponse<SubmissionSummary> studentHistory(LoginUser user,Long courseId,Long taskId,int page,int size) {
        requireStudent(user);
        long offset=offset(page,size);
        String sql="""
            FROM rw_submission s JOIN rw_task t ON t.id=s.task_id
            WHERE s.student_id=? AND (? IS NULL OR t.course_id=?) AND (? IS NULL OR s.task_id=?)
            """;
        Object[] args={user.id(),courseId,courseId,taskId,taskId};
        long count=db.queryForObject("SELECT COUNT(*) "+sql,Long.class,args);
        List<SubmissionSummary> rows=db.query("SELECT s.* "+sql+" ORDER BY s.id DESC LIMIT ? OFFSET ?",
                (rs,n)->summary(rs),user.id(),courseId,courseId,taskId,taskId,size,offset);
        return new PageResponse<>(rows,count);
    }
    public PageResponse<SubmissionSummary> teacherHistory(LoginUser user,long taskId,int page,int size) {
        Task task=tasks.selectById(taskId);
        if(task==null) throw notFound();
        courses.teacherDetail(user,task.getCourseId());
        long offset=offset(page,size);
        long count=db.queryForObject("SELECT COUNT(*) FROM rw_submission WHERE task_id=?",Long.class,taskId);
        List<SubmissionSummary> rows=db.query("SELECT * FROM rw_submission WHERE task_id=? ORDER BY id DESC LIMIT ? OFFSET ?",
                (rs,n)->summary(rs),taskId,size,offset);
        return new PageResponse<>(rows,count);
    }
    private SubmissionSummary summary(ResultSet rs) throws SQLException {
        return new SubmissionSummary(Long.toString(rs.getLong("id")),Long.toString(rs.getLong("task_id")),
                Long.toString(rs.getLong("student_id")),rs.getInt("attempt_no"),
                rs.getTimestamp("submitted_at").toLocalDateTime().atZone(ZONE).toOffsetDateTime().toString(),
                rs.getBigDecimal("total_score").toPlainString(),rs.getInt("correct_count"),rs.getInt("question_count"));
    }
    private SubmissionResult result(long id) {
        Map<String,Object> row=header(id);
        long taskId=((Number)row.get("task_id")).longValue();
        long studentId=((Number)row.get("student_id")).longValue();
        Long representative=tasks.representative(taskId,studentId);
        BigDecimal best=representative==null?null:tasks.submissionScore(representative);
        List<AnswerResult> answers=db.query("""
            SELECT a.*,q.question_id,q.correct_answer_json,q.explanation FROM rw_submission_answer a
            JOIN rw_task_question q ON q.id=a.task_question_id
            WHERE a.submission_id=? ORDER BY q.position
            """,(rs,n)->new AnswerResult(Long.toString(rs.getLong("task_question_id")),
                Long.toString(rs.getLong("question_id")),
                read(rs.getString("answer_json"),new TypeReference<List<String>>(){}),
                rs.getBoolean("is_correct"),rs.getBigDecimal("earned_score").toPlainString(),
                read(rs.getString("correct_answer_json"),new TypeReference<List<String>>(){}),
                rs.getString("explanation")),id);
        return new SubmissionResult(Long.toString(id),Long.toString(taskId),Long.toString(studentId),
                ((Number)row.get("attempt_no")).intValue(),
                ((LocalDateTime)row.get("received_at")).atZone(ZONE).toOffsetDateTime().toString(),
                ((BigDecimal)row.get("total_score")).toPlainString(),
                ((Number)row.get("correct_count")).intValue(),((Number)row.get("question_count")).intValue(),
                best==null?null:best.toPlainString(),representative==null?null:representative.toString(),answers);
    }
    private Map<String,Object> header(long id) {
        List<Map<String,Object>> rows=db.queryForList("SELECT * FROM rw_submission WHERE id=?",id);
        if(rows.isEmpty()) throw notFound();
        return rows.get(0);
    }
    private String hash(long taskId,SubmitRequest body) {
        try {
            Map<String,Object> canonical=new TreeMap<>();
            canonical.put("taskId",taskId);
            List<Map<String,Object>> items=new ArrayList<>();
            if(body!=null && body.answers()!=null) for(AnswerInput a:body.answers()) {
                if(a==null) throw invalid("答案条目不能为空");
                Map<String,Object> item=new TreeMap<>();
                item.put("taskQuestionId",a.taskQuestionId());
                List<String> options=new ArrayList<>(a.selectedOptions()==null?List.of():a.selectedOptions());
                options.sort(Comparator.nullsFirst(String::compareTo));
                if(!options.isEmpty()) {
                    item.put("selectedOptions",options); items.add(item);
                }
            }
            items.sort(Comparator.comparing(x->String.valueOf(x.get("taskQuestionId"))));
            canonical.put("answers",items);
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(canonical));
            return HexFormat.of().formatHex(digest);
        } catch(JsonProcessingException|NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private record Judged(TaskQuestion question,List<String> selected,boolean correct,BigDecimal earned) {}
    private <T>T read(String text,TypeReference<T> type) {
        try { return json.readValue(text,type); }
        catch(JsonProcessingException e) { throw new IllegalStateException("历史 JSON 数据损坏",e); }
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch(JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    private long positive(String value) {
        try {long id=Long.parseLong(value); if(id>0)return id;} catch(Exception ignored){}
        throw invalid("题目 ID 不正确");
    }
    private long offset(int page,int size) {
        if(page<1 || size<1 || size>100) throw invalid("page 至少为 1，size 为 1 到 100");
        return ((long)page-1)*size;
    }
    private void requireStudent(LoginUser user) {
        if(user.role()!=UserRole.STUDENT) throw new ApiException(HttpStatus.FORBIDDEN,"ROLE_FORBIDDEN","仅学生可提交或查看本人答卷");
    }
    private ApiException invalid(String m) {return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SUBMISSION",m);}
    private ApiException notFound() {return new ApiException(HttpStatus.NOT_FOUND,"SUBMISSION_NOT_FOUND","答卷不存在或不可访问");}
    private ApiException conflict() {return new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","该请求标识已用于其他内容");}
}

