package com.rongwang.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rongwang.course.dto.CourseDtos.StudentResponse;
import com.rongwang.course.model.Course;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {
    @Select("SELECT * FROM rw_course WHERE id = #{id} FOR UPDATE")
    Course lockById(long id);

    @Select("SELECT * FROM rw_course WHERE invite_code = #{code} FOR UPDATE")
    Course lockByInviteCode(String code);

    @Select("SELECT * FROM rw_course WHERE teacher_id = #{teacherId} ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Course> teacherCourses(long teacherId, int limit, long offset);

    @Select("SELECT COUNT(*) FROM rw_course WHERE teacher_id = #{teacherId}")
    long teacherCourseCount(long teacherId);

    @Select("""
            SELECT c.* FROM rw_course c JOIN rw_course_member m ON m.course_id = c.id
            WHERE m.student_id = #{studentId} ORDER BY c.id DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<Course> studentCourses(long studentId, int limit, long offset);

    @Select("SELECT COUNT(*) FROM rw_course_member WHERE student_id = #{studentId}")
    long studentCourseCount(long studentId);

    @Update("UPDATE rw_course SET name = #{name}, description = #{description} WHERE id = #{id}")
    int updateDetails(Course course);

    @Update("UPDATE rw_course SET invite_code = #{code} WHERE id = #{id}")
    int updateInviteCode(long id, String code);

    @Select("SELECT id FROM rw_course_member WHERE course_id = #{courseId} AND student_id = #{studentId}")
    Long memberId(long courseId, long studentId);

    @Insert("INSERT INTO rw_course_member (course_id, student_id) VALUES (#{courseId}, #{studentId})")
    int insertMember(long courseId, long studentId);

    @Select("""
            SELECT CAST(u.id AS CHAR) AS id, u.username, u.display_name, u.student_no
            FROM rw_user u JOIN rw_course_member m ON m.student_id = u.id
            WHERE m.course_id = #{courseId} ORDER BY m.id LIMIT #{limit} OFFSET #{offset}
            """)
    List<StudentResponse> students(long courseId, int limit, long offset);

    @Select("SELECT COUNT(*) FROM rw_course_member WHERE course_id = #{courseId}")
    long memberCount(long courseId);
}
