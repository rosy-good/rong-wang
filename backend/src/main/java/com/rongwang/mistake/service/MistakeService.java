package com.rongwang.mistake.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rongwang.auth.model.UserRole;
import com.rongwang.auth.session.LoginUser;
import com.rongwang.common.api.PageResponse;
import com.rongwang.common.exception.ApiException;
import com.rongwang.mistake.dto.MistakeDtos.*;
import com.rongwang.question.dto.QuestionDtos.Option;
import com.rongwang.submission.service.AnswerGrader;
import com.rongwang.task.model.TaskQuestion;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.*;
import java.util.*;

@Service
public class MistakeService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final AnswerGrader grader;
    public MistakeService(JdbcTemplate db,ObjectMapper json,AnswerGrader grader) {
        this.db=db; this.json=json; this.grader=grader;
    }
    public PageResponse<MistakeView> list(LoginUser user,Long courseId,String status,int page,int size) {
        student(user); long offset=offset(page,size);
        if(status!=null && !Set.of("MASTERED","UNMASTERED").contains(status)) throw invalid("掌握状态不正确");
        String sql="""
            FROM rw_mistake m JOIN rw_question q ON q.id=m.question_id
            WHERE m.student_id=? AND (? IS NULL OR q.course_id=?) AND (? IS NULL OR m.mastery_status=?)
            """;
        long count=db.queryForObject("SELECT COUNT(*) "+sql,Long.class,user.id(),courseId,courseId,status,status);
        List<MistakeView> rows=db.query("SELECT m.*,q.course_id "+sql+" ORDER BY m.last_wrong_at DESC,m.id DESC LIMIT ? OFFSET ?",
                (rs,n)->view(rs),user.id(),courseId,courseId,status,status,size,offset);
        return new PageResponse<>(rows,count);
    }
    public MistakeDetail detail(LoginUser user,long id) {
        Map<String,Object> row=owned(user,id,false);
        TaskQuestion snapshot=snapshot(((Number)row.get("last_wrong_task_question_id")).longValue());
        return new MistakeDetail(view(row),snapshot.getStem(),options(snapshot),
                answer(snapshot),snapshot.getExplanation(),snapshot.getScore().toPlainString());
    }
    @Transactional
    public MistakeView mastery(LoginUser user,long id,MasteryRequest body) {
        Map<String,Object> row=owned(user,id,true);
        if(body==null || body.masteryStatus()==null ||
                !Set.of("MASTERED","UNMASTERED").contains(body.masteryStatus()))
            throw invalid("掌握状态必须为 MASTERED 或 UNMASTERED");
        db.update("""
            UPDATE rw_mistake SET mastery_status=?,mastered_at=CASE WHEN ?='MASTERED' THEN NOW(3) ELSE NULL END
            WHERE id=?
            """,body.masteryStatus(),body.masteryStatus(),id);
        return view(owned(user,id,false));
    }
    public PracticeQuestion question(LoginUser user,long id) {
        Map<String,Object> row=owned(user,id,false);
        TaskQuestion q=snapshot(((Number)row.get("last_wrong_task_question_id")).longValue());
        return new PracticeQuestion(q.getId().toString(),q.getStem(),q.getType(),options(q),q.getScore().toPlainString());
    }
    @Transactional
    public PracticeOutcome practice(LoginUser user,long id,String key,PracticeRequest body) {
        Map<String,Object> mistake=owned(user,id,true);
        if(key==null || key.isBlank() || key.length()>100) throw invalid("Idempotency-Key 必须为 1 到 100 字符");
        if(body==null || body.taskQuestionId()==null) throw invalid("请选择合法复练题目");
        long snapshotId=positive(body.taskQuestionId());
        String hash=hash(snapshotId,body.selectedOptions());
        List<Map<String,Object>> old=db.queryForList(
                "SELECT id,request_hash FROM rw_practice_record WHERE mistake_id=? AND idempotency_key=?",id,key);
        if(!old.isEmpty()) {
            if(!hash.equals(old.get(0).get("request_hash"))) throw conflict();
            return new PracticeOutcome(practiceResult(((Number)old.get(0).get("id")).longValue()),false);
        }
        if(snapshotId!=((Number)mistake.get("last_wrong_task_question_id")).longValue())
            throw invalid("复练题目已更新，请重新获取");
        TaskQuestion q=snapshot(snapshotId);
        if(!q.getQuestionId().equals(((Number)mistake.get("question_id")).longValue()))
            throw invalid("复练题目来源不匹配");
        AnswerGrader.Grade grade=grader.grade(q,body.selectedOptions());
        KeyHolder holder=new GeneratedKeyHolder();
        db.update(connection->{
            PreparedStatement ps=connection.prepareStatement("""
                INSERT INTO rw_practice_record(mistake_id,task_question_id,answer_json,is_correct,
                earned_score,idempotency_key,request_hash) VALUES (?,?,?,?,?,?,?)
                """,Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1,id); ps.setLong(2,snapshotId); ps.setString(3,write(grade.selected()));
            ps.setBoolean(4,grade.correct()); ps.setBigDecimal(5,grade.earned());
            ps.setString(6,key); ps.setString(7,hash); return ps;
        },holder);
        if(grade.correct()) {
            db.update("UPDATE rw_mistake SET mastery_status='MASTERED',mastered_at=NOW(3) WHERE id=?",id);
        } else {
            db.update("""
                UPDATE rw_mistake SET wrong_count=wrong_count+1,mastery_status='UNMASTERED',
                mastered_at=NULL,last_wrong_at=NOW(3),last_wrong_task_question_id=? WHERE id=?
                """,snapshotId,id);
        }
        return new PracticeOutcome(practiceResult(Objects.requireNonNull(holder.getKey()).longValue()),true);
    }
    public PageResponse<PracticeResult> practices(LoginUser user,long id,int page,int size) {
        owned(user,id,false); long offset=offset(page,size);
        long count=db.queryForObject("SELECT COUNT(*) FROM rw_practice_record WHERE mistake_id=?",Long.class,id);
        List<Long> ids=db.queryForList("SELECT id FROM rw_practice_record WHERE mistake_id=? ORDER BY id DESC LIMIT ? OFFSET ?",
                Long.class,id,size,offset);
        return new PageResponse<>(ids.stream().map(this::practiceResult).toList(),count);
    }
    private PracticeResult practiceResult(long id) {
        Map<String,Object> row=db.queryForMap("""
            SELECT p.*,q.correct_answer_json,q.explanation FROM rw_practice_record p
            JOIN rw_task_question q ON q.id=p.task_question_id WHERE p.id=?
            """,id);
        return new PracticeResult(Long.toString(id),row.get("mistake_id").toString(),
                row.get("task_question_id").toString(),
                read((String)row.get("answer_json"),new TypeReference<List<String>>(){}),
                (Boolean)row.get("is_correct"),((BigDecimal)row.get("earned_score")).toPlainString(),
                read((String)row.get("correct_answer_json"),new TypeReference<List<String>>(){}),
                (String)row.get("explanation"),time(row.get("practiced_at")));
    }
    private Map<String,Object> owned(LoginUser user,long id,boolean lock) {
        student(user);
        List<Map<String,Object>> rows=db.queryForList("SELECT * FROM rw_mistake WHERE id=?"+
                (lock?" FOR UPDATE":""),id);
        if(rows.isEmpty() || ((Number)rows.get(0).get("student_id")).longValue()!=user.id()) throw notFound();
        return rows.get(0);
    }
    private TaskQuestion snapshot(long id) {
        return db.queryForObject("SELECT * FROM rw_task_question WHERE id=?",(rs,n)->{
            TaskQuestion q=new TaskQuestion();
            q.setId(rs.getLong("id")); q.setQuestionId(rs.getLong("question_id"));
            q.setType(rs.getString("type")); q.setStem(rs.getString("stem"));
            q.setOptionsJson(rs.getString("options_json")); q.setCorrectAnswerJson(rs.getString("correct_answer_json"));
            q.setScore(rs.getBigDecimal("score")); q.setExplanation(rs.getString("explanation"));
            return q;
        },id);
    }
    private MistakeView view(ResultSet rs) throws SQLException {
        return new MistakeView(Long.toString(rs.getLong("id")),Long.toString(rs.getLong("course_id")),
                Long.toString(rs.getLong("question_id")),Long.toString(rs.getLong("last_wrong_task_question_id")),
                rs.getInt("wrong_count"),time(rs.getTimestamp("last_wrong_at").toLocalDateTime()),
                rs.getString("mastery_status"),time(rs.getTimestamp("mastered_at")==null?null:
                        rs.getTimestamp("mastered_at").toLocalDateTime()));
    }
    private MistakeView view(Map<String,Object> row) {
        Long courseId=db.queryForObject("SELECT course_id FROM rw_question WHERE id=?",Long.class,row.get("question_id"));
        return new MistakeView(row.get("id").toString(),courseId.toString(),row.get("question_id").toString(),
                row.get("last_wrong_task_question_id").toString(),((Number)row.get("wrong_count")).intValue(),
                time(row.get("last_wrong_at")),(String)row.get("mastery_status"),time(row.get("mastered_at")));
    }
    private List<Option> options(TaskQuestion q) {return read(q.getOptionsJson(),new TypeReference<List<Option>>(){});}
    private List<String> answer(TaskQuestion q) {return read(q.getCorrectAnswerJson(),new TypeReference<List<String>>(){});}
    private <T>T read(String value,TypeReference<T> type) {
        try {return json.readValue(value,type);}
        catch(JsonProcessingException e) {throw new IllegalStateException("错题 JSON 数据损坏",e);}
    }
    private String write(Object value) {
        try {return json.writeValueAsString(value);}
        catch(JsonProcessingException e) {throw new IllegalStateException(e);}
    }
    private String hash(long snapshotId,List<String> selected) {
        try {
            List<String> normalized=new ArrayList<>(selected==null?List.of():selected);
            normalized.sort(Comparator.nullsFirst(String::compareTo));
            byte[] bytes=json.writeValueAsBytes(Map.of("taskQuestionId",snapshotId,"selectedOptions",normalized));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch(JsonProcessingException|NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    private long positive(String raw) {
        try {long id=Long.parseLong(raw); if(id>0)return id;} catch(Exception ignored){}
        throw invalid("快照 ID 不正确");
    }
    private String time(Object value) {
        if(value==null)return null;
        LocalDateTime local=value instanceof Timestamp t?t.toLocalDateTime():(LocalDateTime)value;
        return local.atZone(ZONE).toOffsetDateTime().toString();
    }
    private long offset(int page,int size) {
        if(page<1 || size<1 || size>100) throw invalid("page 至少为 1，size 为 1 到 100");
        return ((long)page-1)*size;
    }
    private void student(LoginUser user) {
        if(user.role()!=UserRole.STUDENT) throw new ApiException(HttpStatus.FORBIDDEN,"ROLE_FORBIDDEN","仅学生可查看本人错题");
    }
    private ApiException invalid(String message) {return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PRACTICE",message);}
    private ApiException notFound() {return new ApiException(HttpStatus.NOT_FOUND,"MISTAKE_NOT_FOUND","错题不存在或不可访问");}
    private ApiException conflict() {return new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","请求标识已用于其他复练答案");}
}

