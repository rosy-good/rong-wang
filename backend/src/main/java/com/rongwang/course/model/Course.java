package com.rongwang.course.model;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("rw_course")
public class Course {
    private Long id;
    private Long teacherId;
    private String name;
    private String description;
    private String inviteCode;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
