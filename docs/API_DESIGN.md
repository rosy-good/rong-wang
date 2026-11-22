# REST API 设计基线

基线日期：2026-11-11；认证方案更新日期：2026-11-14。路径省略 /api/v1；命名属于初步接口设计，业务约束已冻结。第一版采用服务端 Session 认证。

## 1. 通用约定
所有受保护接口后端校验身份、角色和归属。ID 以字符串传给前端；分页返回 items、total。时间带时区，分数建议以十进制字符串传输，Java BigDecimal 与 MySQL DECIMAL(8,2) 一致。
登录成功后由服务端创建 Session，通过 HttpOnly、SameSite=Lax Cookie 维持登录状态。前端 Axios 使用 `withCredentials`；不得在 localStorage 保存密码或伪造角色。退出登录使服务端 Session 失效。
课程模块工程更新（2026-11-21）：登录后调用 `GET /auth/csrf` 获取 `data.token`；对 `/teacher/**`、`/student/**` 的写请求携带 `X-CSRF-Token`，并使用同一 Session。令牌缺失或不匹配返回 403 `CSRF_INVALID`，重新登录后重新获取令牌。该措施属于 Session 请求保护，不改变业务权限规则。
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

重复加入返回已有关系；归档课程邀请码无效。邀请重置与加入并发须协调，使重置完成后旧码不能成功建立新关系。不存在注册或入课审批接口。维护者通过初始化 SQL 创建、启用和禁用账号，第一版不增加管理员接口。

### 课程接口已实现字段（2026-11-21）
- 列表参数 `page` 默认 1，`size` 默认 20、范围 1–100；课程按 ID 倒序，成员按入课记录 ID 升序，返回 `items`、`total`。
- 创建正文：`name` 必填，去除首尾空白后不可为空，最多 100 字符；`description` 可空，最多 10000 字符。创建成功返回 201。
- PATCH 仅更新提供的 `name`、`description`；null 或未提供保留原值，空字符串可清空介绍。教师 ID、邀请码、状态不允许通过该入口写入。
- 课程响应：字符串 `id`、`teacherId`，以及 `name`、`description`、`status`；仅教师视图包含 `inviteCode`。邀请码为 32 位随机十六进制字符，无自动过期。
- 加入正文：`inviteCode`；成功及重复加入都返回 200，响应包含字符串 `id`（成员关系 ID）、`courseId`、`studentId`。
- 成员响应：字符串 `id`（学生 ID）、`username`、`displayName`、可空 `studentNo`，不包含密码哈希。
- 未登录 401；角色不符 403；非课程所有者/非课程成员访问详情 404；无效或归档邀请码 400 `INVALID_INVITE_CODE`；非法分页或参数 400。没有新增删除、归档、移除成员或退课入口。

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
所有任务 PATCH、PUT、发布与复制路径均检查归属及状态；PUBLISHED 任务的修改返回 409 TASK_LOCKED，包括截止、顺序、名称说明。不提供撤回或延期接口，不能通过通用对象更新绕过锁定。复制生成新任务，不修改原任务。题库原题仍可编辑，但不会更新任务副本。

### 题库接口已实现字段（2026-11-21）
- 上表四个题库操作已实现；仅课程所属教师可调用，学生即使已入课也不能读取题库答案。创建成功 201，其余成功 200；写请求沿用 Session 和 CSRF 令牌。
- 列表参数：`page=1`、`size=20`（1–100），可选 `type` 和 `keyword`（题干包含检索、最多 100 字符）。条件同时生效，按题目 ID 倒序，返回 `items`、`total`。空结果返回空数组。
- `type`：SINGLE、MULTIPLE、JUDGMENT；`stem` 必填、去首尾空白、最多 10000 字符；`explanation` 可空、最多 10000 字符。
- `options` 为有序对象数组，每项 `key` 是不重复的大写字母 A–Z，`text` 非空、最多 2000 字符；2–26 项。判断题固定按顺序传 A「正确」、B「错误」。这些是本阶段的接口编码及输入边界约定，不改变业务判分规则。
- `correctAnswer` 为选项 key 数组：单选、判断恰好 1 项，多选至少 2 项；未知、重复答案拒绝。保存时按选项顺序规范化，答案集合不受传入顺序影响。
- `score` 建议传十进制字符串，范围 0.01–999999.99、最多两位小数；响应始终为两位小数字符串。后端 BigDecimal、数据库 DECIMAL(8,2)，禁止四舍五入接受三位小数。
- 新增必填 type、stem、options、correctAnswer、score。PATCH 合并提供的非 null 字段后重新校验整个题目；省略或 null 保留原值，解析传空字符串可清空。选项和答案有依赖时必须在同一请求中一起修改。
- 响应包含字符串 `id`、`courseId` 及六个题库字段。不允许通过正文转移 courseId 或覆盖 id；额外字段不参与更新。
- 未登录 401，学生角色 403，不属于自己的课程/题目或不存在资源 404；题目规则错误 400 `INVALID_QUESTION`，格式错误 400。不提供删除或停用接口（Q8 待确认）。

新增题目正文示例：
```json
{
  "type": "MULTIPLE",
  "stem": "以下哪些是 Java 基本数据类型？",
  "options": [
    {"key": "A", "text": "int"},
    {"key": "B", "text": "String"},
    {"key": "C", "text": "long"}
  ],
  "correctAnswer": ["A", "C"],
  "score": "2.50",
  "explanation": "int 和 long 是基本数据类型。"
}
```

只实现题库读写，不代表任务发布、自动判分等后续接口已经可用。

### 任务接口已实现字段（2026-11-21）
- 上表九个任务接口已实现。教师列表包含本人课程的草稿和已发布任务；学生列表只包含已发布任务。列表 `page=1`、`size=20`（1–100），按任务 ID 倒序，响应 `items`、`total`。
- 新建草稿正文为 `title`（必填、最多 100 字符）、可选 `description`（最多 10000 字符）、可选 `deadlineAt`（带时区 ISO 8601，例如 `2026-11-30T18:00:00+08:00`）。创建返回 201。PATCH 只更新非 null 字段；发布后所有任务修改请求返回 409 `TASK_LOCKED`。
- `PUT /teacher/tasks/{id}/questions` 接收完整有序数组 `{"questions":[{"questionId":"123","taskQuestionId":"456","content":{...}}]}`。新选题只传 `questionId`，后端从同课程题库复制当前内容；编辑既有草稿题目时传原 `questionId` 与 `taskQuestionId`，可选 `content` 为完整题目对象，字段格式同上方题库写入请求。省略 `content` 保留原草稿副本；有序数组位置决定题目顺序。最多 200 道，不允许重复原题、跨课程原题或伪造其他任务的草稿题目 ID。传空数组可清空草稿。整次替换在事务中执行，失败回滚。
- 草稿选题立即保存到 `rw_task_question` 独立副本；发布时复核草稿内容、至少一题、总分不超过 999999.99、截止时间严格晚于服务器当前时间，再在同一事务中将任务变为 `PUBLISHED`。发布后再次发布返回 409，不重新复制最新题库原题。
- `POST /teacher/tasks/{id}/copies` 返回 201 和同课程新草稿，复制当前任务信息及独立题目副本，名称追加「（副本）」。原任务及答卷不变；修改副本后才能重新发布。
- 教师任务详情包含题目快照的 `questionId`、`taskQuestionId`、题干、选项、答案、解析、分值、顺序。学生任务详情只含 `taskQuestionId`、题干、选项、分值及任务信息；不含标准答案、解析或原题 ID。`bestScore` 与 `representativeSubmissionId` 在尚无正式提交时为 null，后续提交模块会提供实际数据。
- 教师非课程所有者、学生非课程成员统一按不可访问处理；任务不存在或学生访问草稿返回 404。写操作继续要求 Session CSRF 令牌。任务删除、归档和撤回不提供接口。

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

### 提交接口工程实现（2026-11-21）
上述五个答卷接口已实现。正式提交返回本次答卷、逐题对错与分数、标准答案及解析，并返回 `bestScore` 和 `representativeSubmissionId`；首次成功 201，同键同内容重试 200。请求正文 `{"answers":[{"taskQuestionId":"123","selectedOptions":["A","C"]}]}`；遗漏某题、空数组或未提供 answers 均视为漏答。单选与判断最多选一项，多选按集合完整匹配，重复及未知选项返回 400。提交入口过滤器在请求进入时记录服务器时间；事务内先检查身份和成员、成功幂等请求，再严格比较截止时间。答卷、逐题记录和错题更新同事务。学生历史仅本人可见，教师答卷与历史仅课程创建者可见。

答卷详情中的 `id`、`taskId`、`studentId`、`taskQuestionId`、`questionId` 与代表答卷 ID 均为字符串；分数均为两位小数的十进制字符串。历史列表支持可选 `courseId`、`taskId` 及 `page`、`size`。同键不同内容/任务返回 409 `IDEMPOTENCY_CONFLICT`，新请求截止返回 409 `TASK_EXPIRED`。

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

统计与学生提交状态接口已实现。三个百分比按两位小数四舍五入返回十进制字符串，分母为零时返回 null；平均分同样返回两位小数字符串。逐题统计按任务快照顺序返回。代表答卷取最高分中最大 `attempt_no`，只统计当前课程成员；学生名单接口分页返回当前成员、提交次数、最高分与代表答卷 ID。

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

上述六个错题与复练接口已实现。复练题使用该错题最近答错的任务快照；作答前响应隐藏答案和解析，提交成功的复练结果公开。POST 正文为 `{"taskQuestionId":"123","selectedOptions":["A"]}`，携带 `Idempotency-Key`；首次成功 201，同键同内容重试 200。错题列表按 `courseId`、`masteryStatus` 筛选；掌握状态 PATCH 仅接受 `MASTERED` / `UNMASTERED`。复练历史分页保留，不计入正式成绩。筛选、分页、越权及非法答案均由后端校验。

## 7. 未决入口
Q8 的删除、退课、归档、停用等接口不作为本次冻结接口提供；仅落实已确认的归档课程邀请码无效检查。Q12 自动保存接口不提前定案。Q1 已确认由维护者使用初始化 SQL 管理账号，因此第一版不提供维护者账号管理接口。详见 [未决事项](OPEN_QUESTIONS.md)。
