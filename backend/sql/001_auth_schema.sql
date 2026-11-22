CREATE TABLE IF NOT EXISTS rw_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    username VARCHAR(50) NOT NULL COMMENT '登录账号',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
    display_name VARCHAR(50) NOT NULL COMMENT '显示名称',
    student_no VARCHAR(50) NULL COMMENT '学生学号，教师为空',
    role VARCHAR(20) NOT NULL COMMENT '固定角色：TEACHER/STUDENT',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_rw_user_username (username),
    UNIQUE KEY uk_rw_user_student_no (student_no),
    CONSTRAINT ck_rw_user_role CHECK (role IN ('TEACHER', 'STUDENT')),
    CONSTRAINT ck_rw_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '平台预创建账号';

-- 维护者先生成 BCrypt 哈希，再执行类似下面的语句。禁止写入明文密码。
-- INSERT INTO rw_user
--     (username, password_hash, display_name, student_no, role, status)
-- VALUES
--     ('teacher001', '$2a$12$替换为实际BCrypt哈希', '林老师', NULL, 'TEACHER', 'ACTIVE'),
--     ('student001', '$2a$12$替换为实际BCrypt哈希', '陈同学', '20260001', 'STUDENT', 'ACTIVE');
