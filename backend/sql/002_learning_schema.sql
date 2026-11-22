-- MySQL 8.0；先执行 001_auth_schema.sql。保留已有 rw_user 及其账号数据。
-- 表名使用 rw_ 前缀；课程归属、角色、发布时间/截止时间等跨表规则由后端校验。

CREATE TABLE IF NOT EXISTS rw_course (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    teacher_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    invite_code VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_rw_course_invite_code (invite_code),
    KEY idx_rw_course_teacher (teacher_id),
    CONSTRAINT fk_rw_course_teacher FOREIGN KEY (teacher_id) REFERENCES rw_user (id),
    CONSTRAINT ck_rw_course_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS rw_course_member (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    course_id BIGINT UNSIGNED NOT NULL,
    student_id BIGINT UNSIGNED NOT NULL,
    joined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_rw_course_member (course_id, student_id),
    KEY idx_rw_course_member_student (student_id),
    CONSTRAINT fk_rw_course_member_course FOREIGN KEY (course_id) REFERENCES rw_course (id),
    CONSTRAINT fk_rw_course_member_student FOREIGN KEY (student_id) REFERENCES rw_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS rw_question (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    course_id BIGINT UNSIGNED NOT NULL,
    type VARCHAR(20) NOT NULL COMMENT 'SINGLE/MULTIPLE/JUDGMENT',
    stem TEXT NOT NULL,
    options_json JSON NOT NULL,
    correct_answer_json JSON NOT NULL,
    score DECIMAL(8,2) NOT NULL,
    explanation TEXT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_rw_question_course (course_id),
    CONSTRAINT fk_rw_question_course FOREIGN KEY (course_id) REFERENCES rw_course (id),
    CONSTRAINT ck_rw_question_type CHECK (type IN ('SINGLE', 'MULTIPLE', 'JUDGMENT')),
    CONSTRAINT ck_rw_question_score CHECK (score > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS rw_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    course_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(100) NOT NULL,
    description TEXT NULL,
    deadline_at DATETIME(3) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at DATETIME(3) NULL,
    total_score DECIMAL(8,2) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_rw_task_course_status (course_id, status),
    CONSTRAINT fk_rw_task_course FOREIGN KEY (course_id) REFERENCES rw_course (id),
    CONSTRAINT ck_rw_task_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_rw_task_total_score CHECK (total_score >= 0),
    CONSTRAINT ck_rw_task_publication CHECK
        (status = 'DRAFT' OR (deadline_at IS NOT NULL AND published_at IS NOT NULL AND total_score > 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS rw_task_question (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_id BIGINT UNSIGNED NOT NULL,
    question_id BIGINT UNSIGNED NOT NULL COMMENT '原始题库题目，仅追溯与错题去重',
    position INT UNSIGNED NOT NULL,
    type VARCHAR(20) NOT NULL,
    stem TEXT NOT NULL,
    options_json JSON NOT NULL,
    correct_answer_json JSON NOT NULL,
    explanation TEXT NULL,
    score DECIMAL(8,2) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rw_task_question_position (task_id, position),
    KEY idx_rw_task_question_source (question_id),
    CONSTRAINT fk_rw_task_question_task FOREIGN KEY (task_id) REFERENCES rw_task (id),
    CONSTRAINT fk_rw_task_question_source FOREIGN KEY (question_id) REFERENCES rw_question (id),
    CONSTRAINT ck_rw_task_question_position CHECK (position > 0),
    CONSTRAINT ck_rw_task_question_type CHECK (type IN ('SINGLE', 'MULTIPLE', 'JUDGMENT')),
    CONSTRAINT ck_rw_task_question_score CHECK (score > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS rw_submission (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_id BIGINT UNSIGNED NOT NULL,
    student_id BIGINT UNSIGNED NOT NULL,
    received_at DATETIME(3) NOT NULL COMMENT '服务器首次接收新请求时间',
    submitted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    attempt_no INT UNSIGNED NOT NULL,
    total_score DECIMAL(8,2) NOT NULL,
    correct_count INT UNSIGNED NOT NULL,
    question_count INT UNSIGNED NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rw_submission_idempotency (student_id, idempotency_key),
    UNIQUE KEY uk_rw_submission_attempt (task_id, student_id, attempt_no),
    KEY idx_rw_submission_task_student_score (task_id, student_id, total_score, attempt_no),
    CONSTRAINT fk_rw_submission_task FOREIGN KEY (task_id) REFERENCES rw_task (id),
    CONSTRAINT fk_rw_submission_student FOREIGN KEY (student_id) REFERENCES rw_user (id),
    CONSTRAINT ck_rw_submission_attempt CHECK (attempt_no > 0),
    CONSTRAINT ck_rw_submission_score CHECK (total_score >= 0),
    CONSTRAINT ck_rw_submission_counts CHECK (question_count > 0 AND correct_count <= question_count)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS rw_submission_answer (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    submission_id BIGINT UNSIGNED NOT NULL,
    task_question_id BIGINT UNSIGNED NOT NULL,
    answer_json JSON NOT NULL COMMENT '漏答存 []',
    is_correct BOOLEAN NOT NULL,
    earned_score DECIMAL(8,2) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rw_submission_answer (submission_id, task_question_id),
    KEY idx_rw_submission_answer_snapshot (task_question_id),
    CONSTRAINT fk_rw_submission_answer_submission FOREIGN KEY (submission_id) REFERENCES rw_submission (id),
    CONSTRAINT fk_rw_submission_answer_snapshot FOREIGN KEY (task_question_id) REFERENCES rw_task_question (id),
    CONSTRAINT ck_rw_submission_answer_score CHECK (earned_score >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS rw_mistake (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    student_id BIGINT UNSIGNED NOT NULL,
    question_id BIGINT UNSIGNED NOT NULL,
    last_wrong_task_question_id BIGINT UNSIGNED NOT NULL,
    wrong_count INT UNSIGNED NOT NULL,
    last_wrong_at DATETIME(3) NOT NULL,
    mastery_status VARCHAR(20) NOT NULL DEFAULT 'UNMASTERED',
    mastered_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_rw_mistake_student_question (student_id, question_id),
    KEY idx_rw_mistake_student_status (student_id, mastery_status),
    KEY idx_rw_mistake_question (question_id),
    KEY idx_rw_mistake_last_snapshot (last_wrong_task_question_id),
    CONSTRAINT fk_rw_mistake_student FOREIGN KEY (student_id) REFERENCES rw_user (id),
    CONSTRAINT fk_rw_mistake_question FOREIGN KEY (question_id) REFERENCES rw_question (id),
    CONSTRAINT fk_rw_mistake_last_snapshot FOREIGN KEY (last_wrong_task_question_id) REFERENCES rw_task_question (id),
    CONSTRAINT ck_rw_mistake_count CHECK (wrong_count > 0),
    CONSTRAINT ck_rw_mistake_status CHECK (mastery_status IN ('UNMASTERED', 'MASTERED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS rw_practice_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    mistake_id BIGINT UNSIGNED NOT NULL,
    task_question_id BIGINT UNSIGNED NOT NULL COMMENT '本次复练实际使用的题目快照',
    answer_json JSON NOT NULL,
    is_correct BOOLEAN NOT NULL,
    earned_score DECIMAL(8,2) NOT NULL,
    practiced_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rw_practice_idempotency (mistake_id, idempotency_key),
    KEY idx_rw_practice_mistake_time (mistake_id, practiced_at),
    KEY idx_rw_practice_snapshot (task_question_id),
    CONSTRAINT fk_rw_practice_mistake FOREIGN KEY (mistake_id) REFERENCES rw_mistake (id),
    CONSTRAINT fk_rw_practice_snapshot FOREIGN KEY (task_question_id) REFERENCES rw_task_question (id),
    CONSTRAINT ck_rw_practice_score CHECK (earned_score >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
