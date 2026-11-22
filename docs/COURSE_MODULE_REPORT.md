# 课程后端模块验收

日期：2026-11-21。范围：课程与邀请码后端，业务基线为 [PRD](PRD.md) 的 Q1、Q5、Q13；未确定的 Q8 生命周期接口未实现。

## 实现

- `course/web/CourseController` 提供九个课程接口，`course/service/CourseService` 集中处理角色、归属和入课规则。
- `course/mapper/CourseMapper` 使用 MyBatis SQL 与 MyBatis-Plus 插入；实体对应现有 `rw_course`，成员对应 `rw_course_member`。
- 创建课程的教师身份取自 Session；修改只能更新名称和介绍。学生视图不返回邀请码，成员视图不返回密码哈希。
- 加入和重置邀请码在事务中锁同一课程行；重置完成后旧码不能入课。重复加入返回原成员关系。
- 课程写请求要求 Session CSRF 令牌；提供 `GET /api/v1/auth/csrf` 获取。前端与 Apifox 需携带同一 Cookie 和 `X-CSRF-Token`。

## 验证方式与范围

使用本机已安装的 MySQL 8.0.43 启动独立实例，监听 127.0.0.1:13361，库名 `rongwang_test`；执行项目 001、002 建表脚本。测试使用真实 Spring MVC、业务服务、事务与 MyBatis/MySQL；MockMvc 模拟 Servlet 请求，没有启动浏览器或验证网络 Cookie 行为。未修改用户 3306 开发库的数据。

| 验证 | 结果 |
|---|---|
| 原有认证测试 11 项 | PASS |
| 新增课程 MySQL 集成测试 14 项 | PASS |
| 真实数据库中教师登录、创建、读取课程 | PASS |
| 教师越权访问/修改/重置/查看成员被拒绝 | PASS |
| 学生只能访问已加入课程、角色限制、分页校验 | PASS |
| 空白名称、超长名称、非法 ID/分页参数 | PASS |
| 修改不能转移归属、变更状态或任意指定邀请码 | PASS |
| CSRF 缺失/错误时拒绝写入 | PASS |
| 无审批入课、重复返回原关系、归档和未知码拒绝 | PASS |
| 并发同学生入课仅形成一条关系 | PASS |
| 入课等待重置事务结束后，旧码被拒绝 | PASS |
| 测试库 10 张表与 16 个外键 | PASS |
| 移除课程归属判断后，越权测试应失败 | PASS：实际由预期 404 变成 200，测试失败；随后恢复检查 |
| 浏览器课程页面、开发库完整课程联调 | NOT TESTED |

## 复现与下一步

设置独立测试库变量后执行 `mvn test`；不设置 `COURSE_TEST_DB_URL` 时课程集成测试会明确跳过，不能据此声称课程数据库测试通过。配置方式见 [后端 README](../backend/README.md)。

本模块后端自动化验收通过，可进入题库模块。课程删除、归档入口、移除成员及退课规则仍等待 Q8，不能由通用更新接口绕过。
