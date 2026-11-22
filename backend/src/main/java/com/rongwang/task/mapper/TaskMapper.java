package com.rongwang.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rongwang.task.model.Task;
import com.rongwang.task.model.TaskQuestion;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {
    @Select("SELECT * FROM rw_task WHERE id=#{id} FOR UPDATE")
    Task lockTask(long id);
    @Select("SELECT * FROM rw_task WHERE course_id=#{courseId} ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Task> teacherList(long courseId, int limit, long offset);
    @Select("SELECT COUNT(*) FROM rw_task WHERE course_id=#{courseId}")
    long teacherCount(long courseId);
    @Select("SELECT * FROM rw_task WHERE course_id=#{courseId} AND status='PUBLISHED' ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Task> studentList(long courseId, int limit, long offset);
    @Select("SELECT COUNT(*) FROM rw_task WHERE course_id=#{courseId} AND status='PUBLISHED'")
    long studentCount(long courseId);
    @Update("UPDATE rw_task SET title=#{title},description=#{description},deadline_at=#{deadlineAt} WHERE id=#{id} AND status='DRAFT'")
    int updateDraft(Task task);
    @Update("UPDATE rw_task SET status='PUBLISHED',published_at=NOW(3),total_score=#{score} WHERE id=#{id} AND status='DRAFT'")
    int publish(long id, java.math.BigDecimal score);
    @Select("SELECT * FROM rw_task_question WHERE task_id=#{taskId} ORDER BY position")
    List<TaskQuestion> questions(long taskId);
    @Delete("DELETE FROM rw_task_question WHERE task_id=#{taskId}")
    int clearQuestions(long taskId);
    @Insert("""
            INSERT INTO rw_task_question(task_id,question_id,position,type,stem,options_json,correct_answer_json,score,explanation)
            VALUES(#{taskId},#{questionId},#{position},#{type},#{stem},#{optionsJson},#{correctAnswerJson},#{score},#{explanation})
            """)
    @Options(useGeneratedKeys=true, keyProperty="id")
    int insertQuestion(TaskQuestion question);
    @Select("""
            SELECT s.id FROM rw_submission s WHERE s.task_id=#{taskId} AND s.student_id=#{studentId}
            ORDER BY s.total_score DESC,s.attempt_no DESC LIMIT 1
            """)
    Long representative(long taskId, long studentId);
    @Select("SELECT total_score FROM rw_submission WHERE id=#{id}")
    java.math.BigDecimal submissionScore(long id);
}

