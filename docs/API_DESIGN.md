# REST API 设计基线

基线日期：2026-11-11。路径省略 /api/v1；命名属于初步接口设计，业务约束已冻结。认证采用 Session 或其他方案待工程设计，不预设请求令牌形式。

## 1. 通用约定
所有受保护接口后端校验身份、角色和归属。ID 以字符串传给前端；分页返回 items、total。时间带时区，分数建议以十进制字符串传输，Java BigDecimal 与 MySQL DECIMAL(8,2) 一致。
400 表示非法参数，401 未认证，403 角色无权，404 资源不存在或不可访问，409 业务状态冲突。错误包含 code、message，不泄露标准答案。
首次成功提交前任何学生响应均不得泄露题目答案解析；作答 DTO 始终不含这些字段，已成功答卷的本人结果 DTO 才公开。

## 2. 账号与课程
| 方法与路径 | 权限及功能 |
|---|---|
| POST /auth/login | 公共；校验预创建账号、密码和启用状态，禁用不能登录 |
| POST /auth/logout；GET /auth/me | 登录用户；退出、读取本人固定角色 |
| GET、POST /teacher/courses | 教师；仅本人课程列表、创建 |
| GET、PATCH /teacher/courses/{id} | 创建教师；详情或修改名称、介绍，不能借此写任意状态 |
| POST /teacher/courses/{id}/invite-code/reset | 创建教师；更换有效码，旧码立即失效，无自动过期，不改变成员关系 |
| GET /teacher/courses/{id}/students | 创建教师；当前成员分页 |
| GET /student/courses | 学生；本人已加入课程 |
| POST /student/course-memberships | 学生；传 inviteCode，验证有效码、课程状态及本人身份，直接加入无审批 |
| GET /student/courses/{id} | 课程成员；基本信息 |

重复加入返回已有关系；归档课程邀请码无效。邀请重置与加入并发须协调，使重置完成后旧码不能成功建立新关系。不存在注册或入课审批接口。维护者的账号创建、启用禁用操作方式由工程设计确定，本稿不自行增加管理员接口。

## 3. 题库与任务
| 方法与路径 | 权限及功能 |
|---|---|
| GET、POST /teacher/courses/{id}/questions | 创建教师；查询、新增本课程题目 |
| GET、PATCH /teacher/questions/{id} | 所属教师；详情、编辑原题，不能转移课程或修改已发布快照 |
| GET、POST /teacher/courses/{id}/tasks | 所属教师；列表、新建草稿 |
| GET、PATCH /teacher/tasks/{id} | 所属教师；详情、仅编辑草稿名称说明截止时间 |
| PUT /teacher/tasks/{id}/questions | 所属教师；仅草稿选题、排序和题目内容编辑，来源必须为本课程原题 |
| POST /teacher/tasks/{id}/publish | 所属教师；校验最终草稿并保存完整快照，已发布重复请求不改写内容 |
| POST /teacher/tasks/{id}/copies | 所属教师；复制到同课程新 DRAFT，返回新任务 ID，不带旧答卷或成绩 |
| GET /student/courses/{id}/tasks | 成员；仅已发布任务及本人状态 |
| GET /student/tasks/{id} | 成员；说明、时间、总分、题目、bestScore、representativeSubmissionId，不含标准答案解析 |

题库字段为 type、stem、options、correctAnswer、score、explanation。草稿选题接收有序条目，含 questionId；已有草稿副本可以编辑完整内容，保存后发布不得重新以最新原题覆盖。复制纠错只修改新草稿副本，不修改原任务或其他任务。
任务题目请求与响应区分 questionId（原题）和 taskQuestionId（快照）。快照必须含 type、stem、options、correctAnswer、explanation、score、position；学生作答 DTO 省略 correctAnswer 和 explanation。
所有 PATCH、PUT、发布与复制路径均检查归属及状态；PUBLISHED 的修改返回 409 TASK_LOCKED，包括截止、顺序、名称说明。不提供撤回或延期接口，不能通过通用对象更新绕过锁定。复制生成新任务，不修改原任务。

## 4. 正式提交
| 方法与路径 | 权限及功能 |
|---|---|
| POST /student/tasks/{id}/submissions | 成员；首次或重交，截止前不限次数 |
| GET /student/submissions | 本人历史，可按 courseId、taskId 筛选全部尝试 |
| GET /student/submissions/{id} | 本人；当次 totalScore、逐题结果、标准答案解析、当前 bestScore 与代表 ID |
| GET /teacher/tasks/{id}/submissions | 所属教师；全部提交历史 |
| GET /teacher/tasks/{id}/students | 所属教师；当前成员、提交状态、submissionCount、bestScore、representativeSubmissionId |
| GET /teacher/submissions/{id} | 所属教师；指定答卷和逐题记录 |

提交头为 Idempotency-Key，正文 answers 条目含 taskQuestionId、selectedOptions。未提供某题或空数组视为漏答；后端补齐零分逐题记录并归集错题。未知题号、重复题号、重复或未知选项拒绝整次请求。分数及对错由后端读取快照计算，客户端不决定得分。
处理顺序必须一致：
1. 服务器入口记录可信 receivedAt，作为首次收到该新请求的时间。
2. 校验当前身份、角色、任务及成员权限。
3. 查成功幂等记录，核对任务与规范化请求内容；同键同内容返回原提交和当次结果，即使已截止。相同键不同内容返回 409。
4. 未命中则要求 receivedAt 严格早于 deadlineAt；等于或晚于截止返回 409 TASK_EXPIRED，客户端点击时间无效。
5. 校验任务已发布及全部参数，按快照判分；正式答卷、逐题记录、错题更新同一事务。
6. 首次成功返回 201；成功重试返回 200，不增加尝试次数或错题次数。新一次主动重交使用新键，双击与网络重试复用原键。

同键并发由唯一约束和事务协调；尚未成功的请求不得返回虚构原结果。失败回滚后再次到达的请求仍按其新的接收时间判断截止。正式答对不改变掌握状态，正式答错含漏答必须创建或更新错题。

## 5. 教师统计
GET /teacher/tasks/{id}/statistics 仅所属教师可访问，返回：
- memberCount：统计时当前成员数，含后来加入者。
- submittedStudentCount：至少成功提交一次的学生人数，按学生去重。
- submissionRate：submittedStudentCount / memberCount × 100%。
- averageScore：每个已提交学生 bestScore 的平均值。
- correctQuestionCount：每人代表答卷正确题数之和；questionCount：任务题目数。
- accuracy：correctQuestionCount / (submittedStudentCount × questionCount) × 100%。
- questionStatistics：每题代表答卷的正确人数及正确率。

bestScore 是最高分数值；representativeSubmissionId 是最后一次获得该最高分的答卷 ID，用成功尝试序号确定。低分重交不替换代表；不能按最后一次任意提交或所有尝试计算。未提交者不参与平均分和正确率，漏答计分母，复练完全排除。
无人提交时平均分、正确率为 null；成员数大于零且无人提交时提交率 0，成员数为零时提交率 null。页面显示“暂无数据”，不以假零分代替。

## 6. 错题与复练
| 方法与路径 | 权限及功能 |
|---|---|
| GET /student/mistakes | 本人；courseId、masteryStatus 筛选 |
| GET /student/mistakes/{id} | 本人；原题来源、快照、错误次数、最近错误时间、掌握状态与解析 |
| PATCH /student/mistakes/{id} | 本人；仅 masteryStatus，允许标记 MASTERED 和取消为 UNMASTERED |
| GET /student/mistakes/{id}/practice-question | 本人；实际复练快照 ID、题干和选项，不含答案解析 |
| POST /student/mistakes/{id}/practices | 本人；幂等键、快照 ID、答案，按该快照判分 |
| GET /student/mistakes/{id}/practices | 本人；保留并分页展示全部复练历史 |

复练错误次数加 1、恢复未掌握；正确自动已掌握，次数不减。记录和错题更新同事务；同键重试不重复记录或更新。快照须属于该错题原题及合法复练来源，不能任意指定题目。最近答错快照作为复练来源是工程建议，版本选择与并发处理需在工程设计细化，不改变已确认掌握规则。

## 7. 未决入口
Q8 的删除、退课、归档、停用等接口不作为本次冻结接口提供；仅落实已确认的归档课程邀请码无效检查。Q12 自动保存接口和 Q1 维护者操作接口不提前定案。详见 [未决事项](OPEN_QUESTIONS.md)。
