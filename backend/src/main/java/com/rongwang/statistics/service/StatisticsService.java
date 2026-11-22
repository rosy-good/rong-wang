package com.rongwang.statistics.service;

import com.rongwang.auth.session.LoginUser;
import com.rongwang.common.api.PageResponse;
import com.rongwang.common.exception.ApiException;
import com.rongwang.course.service.CourseService;
import com.rongwang.statistics.dto.StatisticsDtos.*;
import com.rongwang.task.mapper.TaskMapper;
import com.rongwang.task.model.Task;
import com.rongwang.task.model.TaskQuestion;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.*;
import java.util.*;

@Service
public class StatisticsService {
    private final JdbcTemplate db;
    private final TaskMapper tasks;
    private final CourseService courses;
    public StatisticsService(JdbcTemplate db,TaskMapper tasks,CourseService courses) {
        this.db=db; this.tasks=tasks; this.courses=courses;
    }
    public TaskStatistics statistics(LoginUser user,long taskId) {
        Task task=owned(user,taskId);
        List<Long> members=memberIds(task.getCourseId());
        Map<Long,Representative> representatives=representatives(taskId,new HashSet<>(members));
        long submitted=representatives.size();
        List<TaskQuestion> questions=tasks.questions(taskId);
        Map<Long,Integer> correctByQuestion=new HashMap<>();
        long correctTotal=0;
        BigDecimal scoreTotal=BigDecimal.ZERO;
        for(Representative representative:representatives.values()) {
            scoreTotal=scoreTotal.add(representative.score());
            List<Map<String,Object>> answers=db.queryForList(
                    "SELECT task_question_id,is_correct FROM rw_submission_answer WHERE submission_id=?",
                    representative.id());
            for(Map<String,Object> answer:answers) if(Boolean.TRUE.equals(answer.get("is_correct"))) {
                correctTotal++; long id=((Number)answer.get("task_question_id")).longValue();
                correctByQuestion.merge(id,1,Integer::sum);
            }
        }
        List<QuestionStatistic> byQuestion=questions.stream().map(q->new QuestionStatistic(
                q.getId().toString(),correctByQuestion.getOrDefault(q.getId(),0),
                percentage(correctByQuestion.getOrDefault(q.getId(),0),submitted))).toList();
        return new TaskStatistics(members.size(),submitted,percentage(submitted,members.size()),
                submitted==0?null:scoreTotal.divide(BigDecimal.valueOf(submitted),2,RoundingMode.HALF_UP).toPlainString(),
                correctTotal,questions.size(),percentage(correctTotal,submitted*questions.size()),byQuestion);
    }
    public PageResponse<StudentStatus> students(LoginUser user,long taskId,int page,int size) {
        Task task=owned(user,taskId);
        if(page<1 || size<1 || size>100)
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","page 至少为 1，size 为 1 到 100");
        long count=db.queryForObject("SELECT COUNT(*) FROM rw_course_member WHERE course_id=?",Long.class,task.getCourseId());
        List<Map<String,Object>> rows=db.queryForList("""
            SELECT u.id,u.username,u.display_name,u.student_no FROM rw_course_member m
            JOIN rw_user u ON u.id=m.student_id WHERE m.course_id=? ORDER BY m.id LIMIT ? OFFSET ?
            """,task.getCourseId(),size,((long)page-1)*size);
        Map<Long,Representative> reps=representatives(taskId,rows.stream()
                .map(r->((Number)r.get("id")).longValue()).collect(java.util.stream.Collectors.toSet()));
        List<StudentStatus> items=new ArrayList<>();
        for(Map<String,Object> row:rows) {
            long id=((Number)row.get("id")).longValue();
            Representative r=reps.get(id);
            Integer submissions=db.queryForObject(
                    "SELECT COUNT(*) FROM rw_submission WHERE task_id=? AND student_id=?",
                    Integer.class,taskId,id);
            items.add(new StudentStatus(Long.toString(id),(String)row.get("username"),
                    (String)row.get("display_name"),(String)row.get("student_no"),submissions,
                    r==null?null:r.score().toPlainString(),r==null?null:Long.toString(r.id())));
        }
        return new PageResponse<>(items,count);
    }
    private Map<Long,Representative> representatives(long taskId,Set<Long> allowed) {
        Map<Long,Representative> result=new HashMap<>();
        if(allowed.isEmpty()) return result;
        List<Map<String,Object>> rows=db.queryForList("""
            SELECT id,student_id,total_score,attempt_no FROM rw_submission WHERE task_id=?
            ORDER BY student_id,total_score DESC,attempt_no DESC
            """,taskId);
        for(Map<String,Object> row:rows) {
            long student=((Number)row.get("student_id")).longValue();
            if(allowed.contains(student)) result.putIfAbsent(student,new Representative(
                    ((Number)row.get("id")).longValue(),(BigDecimal)row.get("total_score")));
        }
        return result;
    }
    private List<Long> memberIds(long courseId) {
        return db.queryForList("SELECT student_id FROM rw_course_member WHERE course_id=?",Long.class,courseId);
    }
    private Task owned(LoginUser user,long id) {
        Task task=tasks.selectById(id);
        if(task==null) throw new ApiException(HttpStatus.NOT_FOUND,"TASK_NOT_FOUND","任务不存在或不可访问");
        courses.teacherDetail(user,task.getCourseId());
        return task;
    }
    private String percentage(long numerator,long denominator) {
        if(denominator==0)return null;
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator),2,RoundingMode.HALF_UP).toPlainString();
    }
    private record Representative(long id,BigDecimal score) {}
}

