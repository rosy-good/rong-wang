package com.rongwang.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rongwang.question.model.Question;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {
    @Select("SELECT * FROM rw_question WHERE id=#{id} FOR UPDATE")
    Question lockById(long id);

    @Select("""
            <script>
            SELECT * FROM rw_question WHERE course_id=#{courseId}
            <if test="type != null">AND type=#{type}</if>
            <if test="keyword != null">AND LOCATE(#{keyword}, stem) > 0</if>
            ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Question> list(long courseId, String type, String keyword, int limit, long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM rw_question WHERE course_id=#{courseId}
            <if test="type != null">AND type=#{type}</if>
            <if test="keyword != null">AND LOCATE(#{keyword}, stem) > 0</if>
            </script>
            """)
    long count(long courseId, String type, String keyword);

    // 显式列出允许编辑字段；不更新 course_id，也不触碰 rw_task_question。
    @Update("""
            UPDATE rw_question SET type=#{type}, stem=#{stem}, options_json=#{optionsJson},
            correct_answer_json=#{correctAnswerJson}, score=#{score}, explanation=#{explanation}
            WHERE id=#{id}
            """)
    int updateContent(Question question);
}

