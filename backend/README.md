# 融网学习平台后端

当前目录包含 Session 登录、课程、题库、任务、正式提交判分、错题复练与成绩统计后端接口，以及完整业务建表 SQL。前端业务页面与浏览器联调仍待完成。

## 常用命令

- `mvn test`：编译并运行认证测试；配置独立测试库后还会运行课程与题库集成测试，配置方式见下文。
- `mvn spring-boot:run`：在本地启动后端，默认地址为 `http://localhost:8080/api`。
- `mvn package`：生成可运行的 JAR。

## 本地数据库

1. 在 MySQL 创建 UTF-8 数据库 `rongwang_dev` 和仅用于本地开发的账号。
2. 依次执行 [001_auth_schema.sql](sql/001_auth_schema.sql) 创建账号表、[002_learning_schema.sql](sql/002_learning_schema.sql) 创建其余九张业务表。已建好 `rw_user` 并录入账号时只执行 `002_learning_schema.sql`；脚本使用 `CREATE TABLE IF NOT EXISTS`，不会清空已有数据。
3. 生成 BCrypt 哈希。可以在 IntelliJ 运行 `PasswordHashTool.main()`，把密码作为程序参数；也可以在 `backend/` 执行：

```powershell
mvn -q exec:java "-Dexec.mainClass=com.rongwang.tools.PasswordHashTool" "-Dexec.args=替换为待设置的密码"
```

复制命令输出中以 `$2a$12$` 开头的哈希。
4. 参考 SQL 文件末尾的模板插入教师或学生账号，不能直接保存明文密码。
5. 启动前通过环境变量提供连接信息：

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/rongwang_dev?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:DB_USERNAME = "你的本地数据库账号"
$env:DB_PASSWORD = "你的本地数据库密码"
mvn spring-boot:run
```

数据库密码只能保存在本机环境变量或未提交的本地配置中，不得写入仓库。

本机也可使用 `backend/config/application-local.properties` 设置 `spring.datasource.username` 和 `spring.datasource.password`。该文件已由 Git 忽略；从仓库根目录或 backend 目录启动均可读取。不要复制到生产环境或提交凭据。IDEA 的数据库工具连接与 Spring Boot 运行连接分别配置；修改后必须重启后端。若同时打开根目录与 backend 两个 IDEA 项目，注意它们各有独立运行配置。

### 在 Navicat 中执行第二份脚本

选中 `rongwang_dev`，使用具备建表权限的本地管理员连接，打开 `sql/002_learning_schema.sql` 并运行全部语句；普通 `rongwang` 账号只有读写数据权限，不能建表。执行后刷新“表”，应有 `rw_user` 加九张 `rw_` 业务表，共十张。执行只读的 [003_schema_verify.sql](sql/003_schema_verify.sql) 核对表数、外键及原账号。不要重复导入或覆盖现有 `rw_user` 账号。

建表脚本建立物理表、外键、索引与局部约束。当前课程接口已实现教师/学生角色与课程归属检查；同课程选题、任务发布后锁定、判分、截止与提交幂等仍需后续实现，不能因为建表完成就宣称这些流程已通过。

## 已提供接口

- `POST /api/v1/auth/login`：账号密码登录并创建 Session。
- `GET /api/v1/auth/me`：读取当前登录用户。
- `POST /api/v1/auth/logout`：注销并使当前 Session 失效。
- `GET /api/v1/auth/csrf`：登录后获取课程写请求使用的 CSRF 令牌。
- 教师课程创建、列表、详情、修改、邀请码重置、成员列表，以及学生入课、列表和详情：字段与路径见 [API 设计](../docs/API_DESIGN.md)。

课程 POST/PATCH 请求需要在同一登录 Session 下携带 `X-CSRF-Token`。重启 IDEA 中的后端后可使用 Apifox 联调；先登录，再获取令牌，最后创建课程。运行配置中的数据库账号仍使用 `rongwang`。

## 课程与题库集成测试

`CourseMysqlTests`、`QuestionMysqlTests`、`TaskMysqlTests` 与 `SubmissionMysqlTests` 使用真实 MySQL，不替换数据库。请先在独立的测试 MySQL 中创建 `rongwang_test`，依次执行两份建表脚本。IDEA 的测试运行配置添加 `COURSE_TEST_DB_URL`、`COURSE_TEST_DB_USERNAME`、`COURSE_TEST_DB_PASSWORD`；例如 URL 为 `jdbc:mysql://127.0.0.1:13362/rongwang_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai`。随后运行 `mvn test` 或对应测试类。所有集成测试沿用这组环境变量，无需新增配置项。

题库接口：`GET/POST /api/v1/teacher/courses/{id}/questions`、`GET/PATCH /api/v1/teacher/questions/{id}`。任务接口：教师创建草稿、编辑、选题、发布、复制和列表详情，以及学生已发布任务列表详情。路径、字段和请求示例见 [API 设计](../docs/API_DESIGN.md)，任务验收见 [任务模块验收](../docs/TASK_MODULE_REPORT.md)。无需重新建表；在 IDEA 重启后端即可加载新接口。

正式提交、错题复练和统计接口也已加入。前端提交时须复用同一个 `Idempotency-Key` 完成网络重试，主动重交生成新键；POST/PATCH/PUT 仍携带登录 Session 的 CSRF 令牌。请求与响应示例及验证范围见 [API 设计](../docs/API_DESIGN.md) 与 [后端闭环验收](../docs/BACKEND_FLOW_REPORT.md)。无需重新导入 SQL。

为防止污染业务库，测试只接受名称为 `rongwang_test` 的库；未设置 URL 时显式跳过这组测试。部分案例事务回滚，提交与并发案例保留隔离库中的随机测试账号、课程和答卷，测试库应为可丢弃环境。2026-11-21 已在独立 MySQL 8.0.43 中验证；详细证据见 [后端闭环验收](../docs/BACKEND_FLOW_REPORT.md)。
