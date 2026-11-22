# 题库后端模块验收

日期：2026-11-21。范围：教师题库接口；依据 [PRD](PRD.md) 的课程归属、题型与分值规则。未新增删除、归档或停用策略（Q8 待确认）。

## 实现清单

| 接口（省略 /api/v1） | 功能 |
|---|---|
| POST /teacher/courses/{id}/questions | 新增单选、多选、判断题，201 |
| GET /teacher/courses/{id}/questions | 分页、题型与题干关键词检索 |
| GET /teacher/questions/{id} | 所属教师读取原题详情 |
| PATCH /teacher/questions/{id} | 部分编辑，合并后校验整个题目 |

- 新增 `question` 模块，分为 Controller、Service、Mapper、DTO、实体；复用 Session、CSRF、课程归属检查与统一响应。
- 仅读写 `rw_question`；原题允许编辑，课程归属不可转移，不更新 `rw_task_question`。沿用现有表结构，无需执行迁移 SQL。
- Java BigDecimal 与 MySQL DECIMAL(8,2) 对齐，响应分值为十进制字符串；三位小数拒绝，不自动舍入。
- 多选至少两个正确答案，重复/未知选项拒绝；单选、判断仅一个答案。输入边界、判断题编码和请求示例见 [API 设计](API_DESIGN.md)。
- 同步了 API、数据库、页面对接、开发计划、后端 README 与 AGENTS 自动检查表。业务基线未改变。

## 自动验证

环境：Java 17、MySQL 8.0.43；独立测试实例 127.0.0.1:13361，数据库 `rongwang_test`。使用已有 001、002 脚本建立的完整表结构，未操作 3306 开发库。测试通过 MockMvc 调用实际 Controller、Service、MyBatis 和 MySQL，不替换业务服务或数据库。

配置方法见 [后端 README](../backend/README.md)。设置 `COURSE_TEST_DB_URL`、`COURSE_TEST_DB_USERNAME`、`COURSE_TEST_DB_PASSWORD` 后在后端运行 `mvn test`。题库测试沿用课程测试变量；未设置 URL 时显式跳过集成测试，不能据此认定通过。

| 检查 | 结果与证据 |
|---|---|
| 原有认证及课程回归 | PASS：25 项 |
| 题库集成测试 | PASS：35 项（7 个场景测试、28 个非法输入案例） |
| 三种题型新增、详情与数据库分值 | PASS：包含 0.01、2.50、999999.99 与答案顺序规范化 |
| 课程隔离、题型/关键词组合、分页与空结果 | PASS：只返回当前课程，关键词按字面检索 |
| 未登录、学生、其他教师读取与写入 | PASS：401/403/404，不能取得答案或修改原题 |
| CSRF 保护 | PASS：新增、编辑缺少令牌均拒绝 |
| 非法题型、空题干、非法分值、未知/重复答案、非法选项 | PASS：400，数据库不插入 |
| 部分编辑合并校验 | PASS：保留未提供字段；非法组合不保存；不能转移课程 |
| 已发布任务快照隔离 | PASS：通过 SQL 准备发布任务及快照，编辑原题全部内容后逐字段比较快照未变 |
| 故障注入验证测试有效性 | PASS：临时去掉“多选至少两个正确选项”校验，出现预期失败（应为 400，实际 201）；已恢复校验 |

## 未验证范围与下一步

- NOT TESTED：浏览器及 Vue 题库页面、真实网络 Cookie 行为，本次仅后端接口开发；没有 UI 截图。
- NOT TESTED：任务创建、发布、自动判分、错题与统计链路，尚未实现。快照隔离测试不等于任务发布功能已验收。
- NOT TESTED：大规模数据与性能目标，待 Q11 明确。

本模块后端验收 PASS。IDEA 重启 `RongwangApplication` 后加载新接口，数据库无需重建。下一阶段是任务草稿、同课程选题、独立快照、发布锁定与复制纠错。
