# Repository Guidelines

## 项目与阶段
融网学习平台是课程教学与客观题练习 Web 平台。第一阶段冻结基线日期：2026-11-11，UI V1 于 2026-11-13 完成返回导航复核，产品负责人已授权进入工程初始化阶段。prototype/ 保存演示原型，frontend/ 与 backend/ 保存正式工程，docs/ 保存需求、设计与验收说明。

## 文档组织
`docs/PRD.md` 是业务规则基线；功能、流程、页面、数据库、接口、开发计划分别位于同名 Markdown 文档；`OPEN_QUESTIONS.md` 仅保留 P1 与工程设计事项；`REVIEW_REPORT.md` 保存审查证据与结论。文档全部使用中文，保持相对链接有效。

## 工作边界与技术栈
工程骨架已经初始化。前端使用 Vue 3、TypeScript、Vite、Element Plus、Vue Router、Pinia、Axios；后端使用 Java 17、Spring Boot 3、Spring MVC、MyBatis-Plus、Maven、MySQL。采用 RESTful 前后端分离单体，不引入 Redis、Kafka 或微服务。初始化阶段只建立框架、配置和目录，不把原型中的前端判分或虚构数据迁入生产代码。

## 核心约束
- 账号预创建、固定单角色、支持启用禁用，密码安全哈希存储。
- 后端校验角色与资源归属；邀请码直接入课，无审批。
- 题库与任务快照分表；发布后完全锁定，纠错复制新任务。
- 截止前不限次提交，取最高分，并列取最后一次最高分答卷统计。
- 先验证身份权限，再查成功幂等记录，再校验服务器首次收到新请求时间。
- 正式提交、逐题记录与错题更新事务一致；正式答对不改变掌握状态。
- 分数使用 DECIMAL(8,2) 与 BigDecimal；判分读取任务快照。
- 详细规则及统计公式以 [PRD](docs/PRD.md) 为准，不得把已确认规则重新列为待确认。

## 修改与验证
规则变化须同步页面、接口字段、数据库、流程和验收案例，记录审查结果。只报告实际完成的检查；文档检查不等同于应用测试。前端使用 `npm run dev`、`npm run build`；后端使用 `mvn spring-boot:run`、`mvn test`。MySQL 配置启用前不得声称数据库集成通过。
P1 为 Q8 生命周期、Q11 部署容量及 Q12 自动保存，不能擅自定案。Q1 工程方案已于 2026-11-14 确认：采用 Session；账号由维护者通过初始化 SQL 预创建，第一版不开发管理员界面。

## 提交与评审
当前无可参考 Git 历史。后续提交建议使用简短明确的动作说明；评审说明列出修改文档、对应规则、验证结果和未决项。不得提交密码、令牌或真实账号凭据。

## 自动检查
| 规则 | 由什么拦住 |
|---|---|
| 正确密码可登录，错误密码、未知账号和禁用账号必须拒绝 | `AuthServiceTests` |
| 登录创建 Session，退出使 Session 失效 | `AuthControllerWebTests` |
| 受保护接口拒绝未登录请求 | `SessionAuthInterceptorTests` |
| 参数校验与错误 JSON 返回统一错误结构 | `AuthControllerWebTests` |
| 课程角色、归属、成员范围、邀请码重置与并发重复入课 | `CourseMysqlTests`（仅连接隔离的 `rongwang_test`，需显式设置 `COURSE_TEST_DB_URL`） |
| 课程写请求必须携带当前 Session 的 CSRF 令牌 | `CourseMysqlTests.csrfMissingOrWrongDoesNotCreateCourse` |
| 题库权限、三种题型校验、精度、部分编辑和原题/快照隔离 | `QuestionMysqlTests`（沿用隔离测试库环境变量） |
| 任务同课程选题、草稿副本、发布锁定、复制隔离、学生无答案响应 | `TaskMysqlTests`（只连接隔离的 `rongwang_test`） |
| 幂等提交、截止时间、快照判分、错题复练、最高分代表和统计口径 | `SubmissionMysqlTests`（只连接隔离的 `rongwang_test`） |
