<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errorMessage, type Page } from '../api/http'
import type { Course, Option, Question, Submission, SubmissionSummary, StudentStatus, TaskStatistics, TeacherTask } from '../api/types'

const props = defineProps<{ mode: string }>()
const route = useRoute(), router = useRouter()
const busy = ref(false), saving = ref(false), error = ref('')
const courses = ref<Course[]>([]), course = ref<Course | null>(null), students = ref<{ id: string; displayName: string; username: string; studentNo: string | null }[]>([])
const questions = ref<Question[]>([]), bank = ref<Question[]>([]), question = ref<Question | null>(null)
const tasks = ref<TeacherTask[]>([]), task = ref<TeacherTask | null>(null)
const stats = ref<TaskStatistics | null>(null), statuses = ref<StudentStatus[]>([]), submissions = ref<SubmissionSummary[]>([]), submission = ref<Submission | null>(null)
const total = ref(0), page = ref(1), keyword = ref(''), typeFilter = ref('')
const courseDialog = ref(false), courseForm = reactive({ name: '', description: '' })
const questionForm = reactive({ type: 'SINGLE', stem: '', options: [{ key: 'A', text: '' }, { key: 'B', text: '' }] as Option[], correctAnswer: [] as string[], score: '1.00', explanation: '' })
const taskForm = reactive({ title: '', description: '', deadlineAt: '' })
const selected = ref<{ questionId: string; taskQuestionId?: string; content?: unknown; stem: string }[]>([])
const snapshotDialog = ref(false), snapshotIndex = ref(-1)
const courseId = computed(() => String(route.params.id || course.value?.id || task.value?.courseId || question.value?.courseId || ''))
const backPath = computed(() => {
  if (props.mode === 'course') return '/teacher/courses'
  if (props.mode === 'questions' || props.mode === 'tasks') return `/teacher/courses/${courseId.value}`
  if (props.mode === 'question-edit') return `/teacher/courses/${courseId.value}/questions`
  if (props.mode === 'task-edit') return `/teacher/courses/${courseId.value}/tasks`
  if (props.mode === 'task') return `/teacher/courses/${courseId.value}/tasks`
  if (props.mode === 'results') return `/teacher/tasks/${route.params.taskId}`
  return task.value ? `/teacher/tasks/${task.value.id}/results` : '/teacher/courses'
})
const title = computed(() => ({ courses: '课程管理', course: course.value?.name || '课程详情', questions: '课程题库', 'question-edit': route.params.questionId ? '编辑题目' : '新建题目', tasks: '练习任务', 'task-edit': route.params.taskId ? '编辑任务草稿' : '创建练习任务', task: task.value?.title || '任务详情', results: '成绩统计', submission: '答卷详情' })[props.mode] || '')
const questionType = (value: string) => ({ SINGLE: '单选题', MULTIPLE: '多选题', JUDGMENT: '判断题' })[value as 'SINGLE' | 'MULTIPLE' | 'JUDGMENT'] || value
const formatTime = (value?: string | null) => value ? new Date(value).toLocaleString('zh-CN') : '未设置'
const datetimeLocal = (value?: string | null) => value ? new Date(new Date(value).getTime() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16) : ''

async function load() {
  error.value = ''; busy.value = true
  try {
    const id = String(route.params.id || ''), taskId = String(route.params.taskId || '')
    if (props.mode === 'courses') { const p = await api<Page<Course>>('GET', '/teacher/courses', undefined, { params: { page: page.value, size: 20 } }); courses.value = p.items; total.value = p.total }
    if (props.mode === 'course') { course.value = await api<Course>('GET', `/teacher/courses/${id}`); students.value = (await api<Page<typeof students.value[number]>>('GET', `/teacher/courses/${id}/students`, undefined, { params: { size: 100 } })).items }
    if (props.mode === 'questions') { const p = await api<Page<Question>>('GET', `/teacher/courses/${id}/questions`, undefined, { params: { page: page.value, size: 20, keyword: keyword.value || undefined, type: typeFilter.value || undefined } }); questions.value = p.items; total.value = p.total }
    if (props.mode === 'question-edit') {
      if (route.params.questionId) { question.value = await api<Question>('GET', `/teacher/questions/${route.params.questionId}`); Object.assign(questionForm, { type: question.value.type, stem: question.value.stem, options: question.value.options.map(o => ({ ...o })), correctAnswer: [...question.value.correctAnswer], score: question.value.score, explanation: question.value.explanation || '' }) }
      else { question.value = null; Object.assign(questionForm, { type: 'SINGLE', stem: '', options: [{ key: 'A', text: '' }, { key: 'B', text: '' }], correctAnswer: [], score: '1.00', explanation: '' }) }
    }
    if (props.mode === 'tasks') { const p = await api<Page<TeacherTask>>('GET', `/teacher/courses/${id}/tasks`, undefined, { params: { page: page.value, size: 20 } }); tasks.value = p.items; total.value = p.total }
    if (props.mode === 'task-edit') {
      if (taskId) { task.value = await api<TeacherTask>('GET', `/teacher/tasks/${taskId}`); Object.assign(taskForm, { title: task.value.title, description: task.value.description || '', deadlineAt: datetimeLocal(task.value.deadlineAt) }); selected.value = task.value.questions.map(q => ({ questionId: q.questionId, taskQuestionId: q.taskQuestionId, stem: q.stem })) }
      else { task.value = null; selected.value = []; Object.assign(taskForm, { title: '', description: '', deadlineAt: '' }) }
      const cid = task.value?.courseId || id
      bank.value = (await api<Page<Question>>('GET', `/teacher/courses/${cid}/questions`, undefined, { params: { size: 100 } })).items
    }
    if (props.mode === 'task' || props.mode === 'results') task.value = await api<TeacherTask>('GET', `/teacher/tasks/${taskId}`)
    if (props.mode === 'results') {
      stats.value = await api<TaskStatistics>('GET', `/teacher/tasks/${taskId}/statistics`)
      statuses.value = (await api<Page<StudentStatus>>('GET', `/teacher/tasks/${taskId}/students`, undefined, { params: { size: 100 } })).items
      submissions.value = (await api<Page<SubmissionSummary>>('GET', `/teacher/tasks/${taskId}/submissions`, undefined, { params: { size: 100 } })).items
    }
    if (props.mode === 'submission') {
      submission.value = await api<Submission>('GET', `/teacher/submissions/${route.params.submissionId}`)
      task.value = await api<TeacherTask>('GET', `/teacher/tasks/${submission.value.taskId}`)
    }
  } catch (e) { error.value = errorMessage(e) }
  finally { busy.value = false }
}
watch(() => route.fullPath, () => { void load() }, { immediate: true })
function back() { void router.push(backPath.value) }
async function createCourse() {
  if (!courseForm.name.trim()) return ElMessage.warning('请输入课程名称')
  saving.value = true
  try { const created = await api<Course>('POST', '/teacher/courses', courseForm); courseDialog.value = false; await router.push(`/teacher/courses/${created.id}`); ElMessage.success('课程已创建') }
  catch (e) { ElMessage.error(errorMessage(e)) } finally { saving.value = false }
}
async function updateCourse() {
  if (!course.value || !courseForm.name.trim()) return ElMessage.warning('请输入课程名称')
  saving.value = true
  try { course.value = await api<Course>('PATCH', `/teacher/courses/${course.value.id}`, courseForm); courseDialog.value = false; ElMessage.success('课程已保存') }
  catch (e) { ElMessage.error(errorMessage(e)) } finally { saving.value = false }
}
function openCourseEdit() { if (!course.value) return; Object.assign(courseForm, { name: course.value.name, description: course.value.description || '' }); courseDialog.value = true }
async function resetCode() {
  if (!course.value) return
  try { await ElMessageBox.confirm('重置后旧邀请码立即失效，已加入学生不受影响。确定重置吗？', '重置邀请码', { type: 'warning' }); course.value = await api<Course>('POST', `/teacher/courses/${course.value.id}/invite-code/reset`); ElMessage.success('邀请码已重置') }
  catch (e) { if (e !== 'cancel') ElMessage.error(errorMessage(e)) }
}
function switchType(value: string) { questionForm.type = value; questionForm.correctAnswer = []; if (value === 'JUDGMENT') questionForm.options = [{ key: 'A', text: '正确' }, { key: 'B', text: '错误' }] }
function addOption() { if (questionForm.options.length < 26) questionForm.options.push({ key: String.fromCharCode(65 + questionForm.options.length), text: '' }) }
function removeOption() { if (questionForm.options.length > 2) { const removed = questionForm.options.pop(); questionForm.correctAnswer = questionForm.correctAnswer.filter(k => k !== removed?.key) } }
function toggleAnswer(key: string) {
  questionForm.correctAnswer = questionForm.type === 'MULTIPLE' ? (questionForm.correctAnswer.includes(key) ? questionForm.correctAnswer.filter(k => k !== key) : [...questionForm.correctAnswer, key]) : [key]
}
function validQuestion() {
  if (!questionForm.stem.trim() || questionForm.options.some(o => !o.text.trim())) return '请填写题干及全部选项'
  if (questionForm.correctAnswer.length < (questionForm.type === 'MULTIPLE' ? 2 : 1)) return '请设置符合题型要求的正确答案'
  if (!/^\d+(\.\d{1,2})?$/.test(questionForm.score) || Number(questionForm.score) <= 0 || Number(questionForm.score) > 999999.99) return '分值须大于零且最多两位小数'
  return ''
}
function questionBody() { return { type: questionForm.type, stem: questionForm.stem.trim(), options: questionForm.options, correctAnswer: questionForm.correctAnswer, score: questionForm.score, explanation: questionForm.explanation } }
async function saveQuestion() {
  const validation = validQuestion(); if (validation) return ElMessage.warning(validation)
  saving.value = true
  try {
    const q = route.params.questionId ? await api<Question>('PATCH', `/teacher/questions/${route.params.questionId}`, questionBody()) : await api<Question>('POST', `/teacher/courses/${courseId.value}/questions`, questionBody())
    ElMessage.success('题目已保存'); await router.push(`/teacher/courses/${q.courseId}/questions`)
  } catch (e) { ElMessage.error(errorMessage(e)) } finally { saving.value = false }
}
async function saveTask() {
  if (!taskForm.title.trim()) return ElMessage.warning('请输入任务名称')
  if (!taskForm.deadlineAt) return ElMessage.warning('请选择截止时间')
  const body = { title: taskForm.title.trim(), description: taskForm.description, deadlineAt: new Date(taskForm.deadlineAt).toISOString() }
  saving.value = true
  try {
    if (task.value) {
      await api<TeacherTask>('PATCH', `/teacher/tasks/${task.value.id}`, body)
      task.value = await api<TeacherTask>('PUT', `/teacher/tasks/${task.value.id}/questions`, { questions: selected.value.map(s => ({ questionId: s.questionId, taskQuestionId: s.taskQuestionId, content: s.content })) })
      ElMessage.success('草稿已保存')
    } else {
      task.value = await api<TeacherTask>('POST', `/teacher/courses/${courseId.value}/tasks`, body)
      if (selected.value.length) task.value = await api<TeacherTask>('PUT', `/teacher/tasks/${task.value.id}/questions`, { questions: selected.value.map(s => ({ questionId: s.questionId, content: s.content })) })
      ElMessage.success('草稿已创建'); await router.replace(`/teacher/tasks/${task.value.id}/edit`)
    }
  } catch (e) { ElMessage.error(errorMessage(e)) } finally { saving.value = false }
}
async function publish() {
  if (!task.value) return ElMessage.warning('请先保存草稿')
  try { await ElMessageBox.confirm('发布后题目、答案、分值、解析及截止时间完全锁定，也不能撤回。请确认已保存并核对。', '发布任务', { type: 'warning' }); await api('POST', `/teacher/tasks/${task.value.id}/publish`); ElMessage.success('任务已发布'); await router.push(`/teacher/tasks/${task.value.id}`) }
  catch (e) { if (e !== 'cancel') ElMessage.error(errorMessage(e)) }
}
async function copyTask() {
  if (!task.value) return
  try { const copy = await api<TeacherTask>('POST', `/teacher/tasks/${task.value.id}/copies`); ElMessage.success('已生成新草稿'); await router.push(`/teacher/tasks/${copy.id}/edit`) }
  catch (e) { ElMessage.error(errorMessage(e)) }
}
function addSelection(id: string) { if (selected.value.some(s => s.questionId === id)) return; const q = bank.value.find(q => q.id === id); if (q) selected.value.push({ questionId: q.id, stem: q.stem }) }
function moveSelection(index: number, direction: number) { const next = index + direction; if (next < 0 || next >= selected.value.length) return; const [item] = selected.value.splice(index, 1); if (item) selected.value.splice(next, 0, item) }
function editSnapshot(index: number) {
  const s = selected.value[index], q = task.value?.questions.find(q => q.taskQuestionId === s?.taskQuestionId) || bank.value.find(q => q.id === s?.questionId)
  if (!s || !q) return
  Object.assign(questionForm, { type: q.type, stem: q.stem, options: q.options.map(o => ({ ...o })), correctAnswer: [...q.correctAnswer], score: q.score, explanation: q.explanation || '' })
  snapshotIndex.value = index; snapshotDialog.value = true
}
function saveSnapshot() { const validation = validQuestion(); if (validation) return ElMessage.warning(validation); const s = selected.value[snapshotIndex.value]; if (s) { s.content = questionBody(); s.stem = questionForm.stem }; snapshotDialog.value = false }
</script>

<template>
  <div v-loading="busy">
    <div class="page-head"><div><el-button v-if="mode !== 'courses'" text class="back" @click="back">← 返回上一级</el-button><p class="eyebrow">TEACHER WORKSPACE</p><h1>{{ title }}</h1></div>
      <el-button v-if="mode === 'courses'" type="primary" @click="Object.assign(courseForm, { name: '', description: '' }); courseDialog = true">创建课程</el-button>
      <el-button v-if="mode === 'questions'" type="primary" @click="router.push(`/teacher/courses/${courseId}/questions/new`)">新建题目</el-button>
      <el-button v-if="mode === 'tasks'" type="primary" @click="router.push(`/teacher/courses/${courseId}/tasks/new`)">创建任务</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon class="space" @close="error = ''" />

    <section v-if="mode === 'courses'" class="grid-cards">
      <button v-for="c in courses" :key="c.id" class="course-card" @click="router.push(`/teacher/courses/${c.id}`)"><span class="eyebrow">我的课程</span><h2>{{ c.name }}</h2><p class="muted">{{ c.description || '暂无课程介绍' }}</p><span class="text-link">查看课程 →</span></button>
      <el-empty v-if="!courses.length && !busy" description="还没有课程，先创建一门课程" />
    </section>
    <section v-if="mode === 'course' && course" class="stack">
      <div class="panel"><div class="row between"><h2>课程信息</h2><el-button @click="openCourseEdit">编辑课程</el-button></div><p>{{ course.description || '暂无课程介绍' }}</p><div class="invite"><span>加入邀请码：<strong>{{ course.inviteCode }}</strong></span><el-button @click="resetCode">重置邀请码</el-button></div><p class="muted">学生输入有效邀请码后直接加入课程，无需审批。</p></div>
      <div class="grid-cards"><RouterLink class="course-card" :to="`/teacher/courses/${course.id}/questions`"><h2>题库管理</h2><p>维护本课程独立题库</p><span class="text-link">进入题库 →</span></RouterLink><RouterLink class="course-card" :to="`/teacher/courses/${course.id}/tasks`"><h2>练习任务</h2><p>创建草稿、发布任务与查看成绩</p><span class="text-link">查看任务 →</span></RouterLink></div>
      <RouterLink class="course-card" :to="`/teacher/courses/${course.id}/results`"><h2>成绩统计</h2><p class="muted">查看每项任务的提交率、班级平均分、正确率，以及学生答卷。</p><span class="text-link">查看任务成绩 →</span></RouterLink>
      <div class="panel"><h2>课程学生</h2><el-table :data="students" class="table"><el-table-column prop="displayName" label="姓名" /><el-table-column prop="username" label="账号" /><el-table-column prop="studentNo" label="学号" /></el-table><el-empty v-if="!students.length" description="暂无学生加入" :image-size="70" /></div>
    </section>
    <section v-if="mode === 'questions'" class="panel"><div class="row filters"><el-input v-model="keyword" placeholder="搜索题干" clearable @keyup.enter="page = 1; load()" /><el-select v-model="typeFilter" placeholder="全部题型" clearable @change="page = 1; load()"><el-option label="单选题" value="SINGLE" /><el-option label="多选题" value="MULTIPLE" /><el-option label="判断题" value="JUDGMENT" /></el-select><el-button @click="page = 1; load()">查询</el-button></div><el-table :data="questions" class="table"><el-table-column prop="stem" label="题干" min-width="260" show-overflow-tooltip /><el-table-column label="题型" width="100"><template #default="scope">{{ questionType(scope.row.type) }}</template></el-table-column><el-table-column prop="score" label="分值" width="90" /><el-table-column label="操作" width="100"><template #default="scope"><el-button link type="primary" @click="router.push(`/teacher/questions/${scope.row.id}/edit`)">编辑</el-button></template></el-table-column></el-table><el-empty v-if="!questions.length && !busy" description="没有符合条件的题目" :image-size="70" /><el-pagination v-if="total > 20" v-model:current-page="page" layout="prev, pager, next" :total="total" :page-size="20" @current-change="load" /></section>
    <section v-if="mode === 'question-edit'" class="panel form-panel"><p v-if="route.params.questionId" class="muted">编辑题库原题不会改变已经发布的任务快照。</p><el-form label-position="top" @submit.prevent="saveQuestion"><el-form-item label="题型"><el-select :model-value="questionForm.type" @change="switchType"><el-option label="单选题" value="SINGLE" /><el-option label="多选题" value="MULTIPLE" /><el-option label="判断题" value="JUDGMENT" /></el-select></el-form-item><el-form-item label="题干"><el-input v-model="questionForm.stem" type="textarea" :rows="3" maxlength="10000" show-word-limit /></el-form-item><div class="row between"><h3>选项与标准答案</h3><div v-if="questionForm.type !== 'JUDGMENT'"><el-button @click="removeOption">减少选项</el-button><el-button @click="addOption">增加选项</el-button></div></div><div v-for="o in questionForm.options" :key="o.key" class="option-edit"><el-checkbox :model-value="questionForm.correctAnswer.includes(o.key)" @change="toggleAnswer(o.key)">{{ o.key }} · 正确</el-checkbox><el-input v-model="o.text" :disabled="questionForm.type === 'JUDGMENT'" :placeholder="`选项 ${o.key}`" /></div><p class="muted">多选题至少设置两个正确选项，只有完整匹配才得分。</p><el-form-item label="分值"><el-input v-model="questionForm.score" placeholder="例如 2.50" /></el-form-item><el-form-item label="解析"><el-input v-model="questionForm.explanation" type="textarea" :rows="3" maxlength="10000" show-word-limit /></el-form-item><el-button type="primary" native-type="submit" :loading="saving">保存题目</el-button></el-form></section>
    <section v-if="mode === 'tasks'" class="panel"><el-table :data="tasks" class="table"><el-table-column prop="title" label="任务名称" min-width="210" /><el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.status === 'PUBLISHED' ? 'success' : 'info'">{{ scope.row.status === 'PUBLISHED' ? '已发布' : '草稿' }}</el-tag></template></el-table-column><el-table-column label="截止时间" min-width="160"><template #default="scope">{{ formatTime(scope.row.deadlineAt) }}</template></el-table-column><el-table-column label="操作" min-width="170"><template #default="scope"><el-button link type="primary" @click="router.push(scope.row.status === 'DRAFT' ? `/teacher/tasks/${scope.row.id}/edit` : `/teacher/tasks/${scope.row.id}`)">{{ scope.row.status === 'DRAFT' ? '编辑草稿' : '查看任务' }}</el-button><el-button v-if="scope.row.status === 'PUBLISHED'" link @click="router.push(`/teacher/tasks/${scope.row.id}/results`)">成绩</el-button></template></el-table-column></el-table><el-empty v-if="!tasks.length && !busy" description="还没有练习任务" :image-size="70" /></section>
    <section v-if="mode === 'task-edit'" class="stack"><div class="panel form-panel"><h2>任务信息</h2><el-form label-position="top" @submit.prevent="saveTask"><el-form-item label="任务名称"><el-input v-model="taskForm.title" maxlength="100" /></el-form-item><el-form-item label="任务说明"><el-input v-model="taskForm.description" type="textarea" :rows="3" /></el-form-item><el-form-item label="截止时间"><el-input v-model="taskForm.deadlineAt" type="datetime-local" /></el-form-item><el-button type="primary" native-type="submit" :loading="saving">保存草稿</el-button><el-button v-if="task" @click="publish">发布任务</el-button></el-form></div><div class="panel"><h2>已选题目（{{ selected.length }}）</h2><p class="muted">选题和顺序保存到独立任务副本；发布后完全锁定。修改列表后请点击“保存草稿”。</p><div v-for="(s, i) in selected" :key="s.questionId" class="list-row"><span>{{ i + 1 }}. {{ s.stem }}</span><div class="row"><el-button size="small" @click="moveSelection(i, -1)">上移</el-button><el-button size="small" @click="moveSelection(i, 1)">下移</el-button><el-button size="small" @click="editSnapshot(i)">编辑副本</el-button><el-button size="small" type="danger" text @click="selected.splice(i, 1)">移除</el-button></div></div></div><div class="panel"><h2>从本课程题库选题</h2><div v-for="q in bank" :key="q.id" class="list-row"><span>{{ questionType(q.type) }} · {{ q.stem }} · {{ q.score }} 分</span><el-button :disabled="selected.some(s => s.questionId === q.id)" @click="addSelection(q.id)">加入</el-button></div><el-empty v-if="!bank.length" description="本课程题库暂无题目" :image-size="70" /></div></section>
    <section v-if="mode === 'task' && task" class="stack"><div class="panel"><div class="row between"><div><el-tag type="success">已发布 · 只读</el-tag><h2>{{ task.title }}</h2></div><div><el-button @click="copyTask">复制为新草稿</el-button><el-button type="primary" @click="router.push(`/teacher/tasks/${task.id}/results`)">查看成绩</el-button></div></div><p>{{ task.description }}</p><p class="muted">截止：{{ formatTime(task.deadlineAt) }}　总分：{{ task.totalScore }}</p></div><div v-for="q in task.questions" :key="q.taskQuestionId" class="panel"><h3>{{ q.position }}. {{ q.stem }} <small>（{{ questionType(q.type) }} · {{ q.score }} 分）</small></h3><p v-for="o in q.options" :key="o.key" class="option-line">{{ o.key }}. {{ o.text }}</p><p>标准答案：{{ q.correctAnswer.join('、') }}</p><p class="muted">解析：{{ q.explanation || '暂无解析' }}</p></div></section>
    <section v-if="mode === 'results' && task" class="stack"><div class="panel"><h2>{{ task.title }}</h2><p class="muted">每位学生按历史最高分计入统计；并列最高分取最后一次获得最高分的答卷。</p></div><div v-if="stats" class="metric-grid"><div class="metric">提交率<strong>{{ stats.submissionRate == null ? '暂无数据' : `${stats.submissionRate}%` }}</strong><small>{{ stats.submittedStudentCount }} / {{ stats.memberCount }} 位成员</small></div><div class="metric">班级平均分<strong>{{ stats.averageScore ?? '暂无数据' }}</strong><small>未提交学生不参与</small></div><div class="metric">班级正确率<strong>{{ stats.accuracy == null ? '暂无数据' : `${stats.accuracy}%` }}</strong><small>{{ stats.correctQuestionCount }} 道答对</small></div></div><div class="panel"><h2>学生提交情况</h2><el-table :data="statuses"><el-table-column prop="displayName" label="学生" /><el-table-column prop="submissionCount" label="提交次数" /><el-table-column label="最高分"><template #default="scope">{{ scope.row.bestScore ?? '未提交' }}</template></el-table-column><el-table-column label="代表答卷"><template #default="scope"><el-button v-if="scope.row.representativeSubmissionId" link type="primary" @click="router.push(`/teacher/submissions/${scope.row.representativeSubmissionId}`)">查看</el-button><span v-else>—</span></template></el-table-column></el-table></div><div class="panel"><h2>全部提交历史</h2><el-table :data="submissions"><el-table-column prop="studentId" label="学生 ID" /><el-table-column prop="attemptNo" label="次数" /><el-table-column prop="totalScore" label="得分" /><el-table-column label="提交时间"><template #default="scope">{{ formatTime(scope.row.submittedAt) }}</template></el-table-column><el-table-column label="答卷"><template #default="scope"><el-button link type="primary" @click="router.push(`/teacher/submissions/${scope.row.id}`)">查看</el-button></template></el-table-column></el-table></div><div v-if="stats" class="panel"><h2>逐题正确率</h2><div v-for="q in stats.questionStatistics" :key="q.taskQuestionId" class="list-row"><span>{{ task.questions.find(item => item.taskQuestionId === q.taskQuestionId)?.stem }}</span><strong>{{ q.accuracy == null ? '暂无数据' : `${q.accuracy}%` }}</strong></div></div></section>
    <section v-if="mode === 'submission' && submission" class="stack"><div class="panel"><h2>第 {{ submission.attemptNo }} 次提交 · {{ submission.totalScore }} 分</h2><p>最高分 {{ submission.bestScore }} <el-tag v-if="submission.representativeSubmissionId === submission.id" type="success">统计代表答卷</el-tag></p><p class="muted">收到时间：{{ formatTime(submission.receivedAt) }}</p></div><div v-for="a in submission.answers" :key="a.taskQuestionId" class="panel"><h3>{{ task?.questions.find(q => q.taskQuestionId === a.taskQuestionId)?.stem }}</h3><p>学生作答：{{ a.selectedOptions.join('、') || '未作答' }}</p><p>标准答案：{{ a.correctAnswer.join('、') }} · {{ a.correct ? '正确' : '错误' }} · 得分 {{ a.earnedScore }}</p><p class="muted">解析：{{ a.explanation || '暂无解析' }}</p></div></section>

    <el-dialog v-model="courseDialog" :title="course ? '编辑课程' : '创建课程'" width="min(520px, 95vw)"><el-form label-position="top"><el-form-item label="课程名称"><el-input v-model="courseForm.name" maxlength="100" /></el-form-item><el-form-item label="课程介绍"><el-input v-model="courseForm.description" type="textarea" :rows="4" /></el-form-item></el-form><template #footer><el-button @click="courseDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="course ? updateCourse() : createCourse()">保存</el-button></template></el-dialog>
    <el-dialog v-model="snapshotDialog" title="编辑本任务题目副本" width="min(680px, 96vw)"><p class="muted">这里的修改仅作用于当前草稿副本，不改变题库原题。</p><el-form label-position="top"><el-form-item label="题干"><el-input v-model="questionForm.stem" type="textarea" /></el-form-item><div v-for="o in questionForm.options" :key="o.key" class="option-edit"><el-checkbox :model-value="questionForm.correctAnswer.includes(o.key)" @change="toggleAnswer(o.key)">{{ o.key }} · 正确</el-checkbox><el-input v-model="o.text" /></div><el-form-item label="分值"><el-input v-model="questionForm.score" /></el-form-item><el-form-item label="解析"><el-input v-model="questionForm.explanation" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="snapshotDialog = false">取消</el-button><el-button type="primary" @click="saveSnapshot">保存到待提交草稿</el-button></template></el-dialog>
  </div>
</template>
