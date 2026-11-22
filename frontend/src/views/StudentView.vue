<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { AxiosError } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errorMessage, newKey, type Page } from '../api/http'
import type { Course, Mistake, MistakeDetail, PracticeQuestion, PracticeResult, StudentTask, Submission, SubmissionSummary } from '../api/types'

const props = defineProps<{ mode: string }>()
const route = useRoute(), router = useRouter()
const busy = ref(false), saving = ref(false), error = ref('')
const courses = ref<Course[]>([]), course = ref<Course | null>(null), tasks = ref<StudentTask[]>([]), task = ref<StudentTask | null>(null)
const history = ref<SubmissionSummary[]>([]), submission = ref<Submission | null>(null), mistakes = ref<Mistake[]>([]), mistake = ref<MistakeDetail | null>(null)
const practiceQuestion = ref<PracticeQuestion | null>(null), practiceHistory = ref<PracticeResult[]>([]), practiceResult = ref<PracticeResult | null>(null)
const joinDialog = ref(false), inviteCode = ref(''), page = ref(1), total = ref(0), filterCourse = ref(''), filterMastery = ref('')
const answers = reactive<Record<string, string[]>>({}), practiceAnswer = ref<string[]>([])
const pendingSubmission = ref<{ key: string; body: { answers: { taskQuestionId: string; selectedOptions: string[] }[] } } | null>(null)
const pendingPractice = ref<{ key: string; body: { taskQuestionId: string; selectedOptions: string[] } } | null>(null)
const submitted = ref(false)
const title = computed(() => ({ courses: '我的课程', course: course.value?.name || '课程详情', task: task.value?.title || '任务详情', answer: task.value?.title || '在线答题', history: '历史成绩', submission: '提交结果', mistakes: '个人错题本', mistake: '错题详情', practice: '错题复练' })[props.mode] || '')
const backPath = computed(() => {
  if (props.mode === 'course') return '/student/courses'
  if (props.mode === 'task') return `/student/courses/${task.value?.courseId || ''}`
  if (props.mode === 'answer') return `/student/tasks/${route.params.taskId}`
  if (props.mode === 'submission') return submission.value ? `/student/tasks/${submission.value.taskId}` : '/student/submissions'
  if (props.mode === 'mistake') return '/student/mistakes'
  if (props.mode === 'practice') return `/student/mistakes/${route.params.mistakeId}`
  return '/student/courses'
})
const formatTime = (value?: string | null) => value ? new Date(value).toLocaleString('zh-CN') : '未设置'
const questionType = (value: string) => ({ SINGLE: '单选', MULTIPLE: '多选', JUDGMENT: '判断' })[value as 'SINGLE' | 'MULTIPLE' | 'JUDGMENT'] || value
const expired = computed(() => !!task.value?.deadlineAt && Date.now() >= new Date(task.value.deadlineAt).getTime())
const unanswered = computed(() => task.value?.questions.filter(q => !(answers[q.taskQuestionId]?.length)).length || 0)
function clearAnswers() { for (const key of Object.keys(answers)) delete answers[key] }

async function load() {
  busy.value = true; error.value = ''
  try {
    const id = String(route.params.id || ''), taskId = String(route.params.taskId || ''), mistakeId = String(route.params.mistakeId || '')
    if (props.mode === 'courses') { const p = await api<Page<Course>>('GET', '/student/courses', undefined, { params: { page: page.value, size: 20 } }); courses.value = p.items; total.value = p.total }
    if (props.mode === 'course') {
      course.value = await api<Course>('GET', `/student/courses/${id}`)
      const p = await api<Page<StudentTask>>('GET', `/student/courses/${id}/tasks`, undefined, { params: { page: page.value, size: 20 } }); tasks.value = p.items; total.value = p.total
    }
    if (props.mode === 'task' || props.mode === 'answer') {
      task.value = await api<StudentTask>('GET', `/student/tasks/${taskId}`)
      if (props.mode === 'answer') { clearAnswers(); submitted.value = false; pendingSubmission.value = null }
    }
    if (props.mode === 'history') {
      const p = await api<Page<SubmissionSummary>>('GET', '/student/submissions', undefined, { params: { page: page.value, size: 20, courseId: filterCourse.value || undefined } }); history.value = p.items; total.value = p.total
      courses.value = (await api<Page<Course>>('GET', '/student/courses', undefined, { params: { size: 100 } })).items
    }
    if (props.mode === 'submission') {
      submission.value = await api<Submission>('GET', `/student/submissions/${route.params.submissionId}`)
      task.value = await api<StudentTask>('GET', `/student/tasks/${submission.value.taskId}`)
    }
    if (props.mode === 'mistakes') {
      const p = await api<Page<Mistake>>('GET', '/student/mistakes', undefined, { params: { page: page.value, size: 20, courseId: filterCourse.value || undefined, masteryStatus: filterMastery.value || undefined } }); mistakes.value = p.items; total.value = p.total
      courses.value = (await api<Page<Course>>('GET', '/student/courses', undefined, { params: { size: 100 } })).items
    }
    if (props.mode === 'mistake') {
      mistake.value = await api<MistakeDetail>('GET', `/student/mistakes/${mistakeId}`)
      practiceHistory.value = (await api<Page<PracticeResult>>('GET', `/student/mistakes/${mistakeId}/practices`, undefined, { params: { size: 100 } })).items
    }
    if (props.mode === 'practice') {
      practiceQuestion.value = await api<PracticeQuestion>('GET', `/student/mistakes/${mistakeId}/practice-question`)
      practiceAnswer.value = []; practiceResult.value = null; pendingPractice.value = null
    }
  } catch (e) { error.value = errorMessage(e) }
  finally { busy.value = false }
}
watch(() => route.fullPath, () => { void load() }, { immediate: true })
function isDirty() { return props.mode === 'answer' && !submitted.value && Object.values(answers).some(v => v.length > 0) }
function leavePrompt() { return !isDirty() || window.confirm('存在未提交的答案，离开后当前作答内容不会保存。确定离开吗？') }
onBeforeRouteLeave(() => leavePrompt())
onBeforeRouteUpdate(() => leavePrompt())
function beforeUnload(event: BeforeUnloadEvent) { if (isDirty()) { event.preventDefault(); event.returnValue = '' } }
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
function back() { void router.push(backPath.value) }
async function joinCourse() {
  if (!inviteCode.value.trim()) return ElMessage.warning('请输入邀请码')
  saving.value = true
  try { const member = await api<{ courseId: string }>('POST', '/student/course-memberships', { inviteCode: inviteCode.value.trim() }); joinDialog.value = false; inviteCode.value = ''; ElMessage.success('已加入课程'); await router.push(`/student/courses/${member.courseId}`) }
  catch (e) { ElMessage.error(errorMessage(e)) } finally { saving.value = false }
}
function setSingle(id: string, value: string | number | boolean) { answers[id] = [String(value)] }
function setMultiple(id: string, value: string[]) { answers[id] = value }
async function submit() {
  if (!task.value) return
  if (!pendingSubmission.value) {
    if (expired.value) return ElMessage.warning('任务已截止，不能发起新提交')
    try { await ElMessageBox.confirm(`还有 ${unanswered.value} 道题未作答。漏答计零分并进入错题本；提交后立即公开答案。确定提交吗？`, '确认提交', { type: 'warning' }) }
    catch { return }
    pendingSubmission.value = { key: newKey(), body: { answers: task.value.questions.map(q => ({ taskQuestionId: q.taskQuestionId, selectedOptions: [...(answers[q.taskQuestionId] || [])] })) } }
  }
  saving.value = true
  try {
    const result = await api<Submission>('POST', `/student/tasks/${task.value.id}/submissions`, pendingSubmission.value.body, { headers: { 'Idempotency-Key': pendingSubmission.value.key } })
    submitted.value = true; pendingSubmission.value = null; await router.replace(`/student/submissions/${result.id}`)
  } catch (e) {
    ElMessage.error(errorMessage(e))
    if (e instanceof AxiosError && e.response && e.response.status < 500) pendingSubmission.value = null
  } finally { saving.value = false }
}
async function markMastery(status: 'MASTERED' | 'UNMASTERED') {
  if (!mistake.value) return
  try { mistake.value.mistake = await api<Mistake>('PATCH', `/student/mistakes/${mistake.value.mistake.id}`, { masteryStatus: status }); ElMessage.success('掌握状态已更新') }
  catch (e) { ElMessage.error(errorMessage(e)) }
}
async function practice() {
  if (!practiceQuestion.value) return
  if (!pendingPractice.value) pendingPractice.value = { key: newKey(), body: { taskQuestionId: practiceQuestion.value.taskQuestionId, selectedOptions: [...practiceAnswer.value] } }
  saving.value = true
  try { practiceResult.value = await api<PracticeResult>('POST', `/student/mistakes/${route.params.mistakeId}/practices`, pendingPractice.value.body, { headers: { 'Idempotency-Key': pendingPractice.value.key } }); pendingPractice.value = null; ElMessage.success(practiceResult.value.correct ? '复练答对，已自动标记掌握' : '复练答错，错误次数增加') }
  catch (e) { ElMessage.error(errorMessage(e)); if (e instanceof AxiosError && e.response && e.response.status < 500) pendingPractice.value = null }
  finally { saving.value = false }
}
</script>

<template>
  <div v-loading="busy"><div class="page-head"><div><el-button v-if="!['courses', 'history', 'mistakes'].includes(mode)" text class="back" @click="back">← 返回上一级</el-button><p class="eyebrow">STUDENT WORKSPACE</p><h1>{{ title }}</h1></div><el-button v-if="mode === 'courses'" type="primary" @click="joinDialog = true">通过邀请码加入课程</el-button></div>
    <el-alert v-if="error" :title="error" type="error" show-icon class="space" @close="error = ''" />
    <section v-if="mode === 'courses'" class="grid-cards"><button v-for="c in courses" :key="c.id" class="course-card" @click="router.push(`/student/courses/${c.id}`)"><span class="eyebrow">已加入课程</span><h2>{{ c.name }}</h2><p class="muted">{{ c.description || '暂无课程介绍' }}</p><span class="text-link">进入学习 →</span></button><el-empty v-if="!courses.length && !busy" description="暂无课程，可输入邀请码加入" /></section>
    <section v-if="mode === 'course' && course" class="stack"><div class="panel"><h2>{{ course.name }}</h2><p>{{ course.description || '暂无课程介绍' }}</p></div><div class="panel"><h2>练习任务</h2><div v-for="t in tasks" :key="t.id" class="list-row"><div><strong>{{ t.title }}</strong><p class="muted">截止：{{ formatTime(t.deadlineAt) }} · 最高分：{{ t.bestScore ?? '未提交' }}</p></div><el-button @click="router.push(`/student/tasks/${t.id}`)">查看任务</el-button></div><el-empty v-if="!tasks.length && !busy" description="教师尚未发布任务" :image-size="70" /></div></section>
    <section v-if="mode === 'task' && task" class="stack"><div class="panel"><h2>{{ task.title }}</h2><p>{{ task.description || '暂无任务说明' }}</p><div class="info-grid"><span>截止时间<strong>{{ formatTime(task.deadlineAt) }}</strong></span><span>题目数量<strong>{{ task.questions.length }}</strong></span><span>总分<strong>{{ task.totalScore }}</strong></span><span>当前最高分<strong>{{ task.bestScore ?? '未提交' }}</strong></span></div><p class="muted">截止前可不限次数提交，最终成绩取最高分；并列最高分取最后一次最高分答卷。首次成功提交前不显示标准答案。</p><div class="row"><el-button type="primary" :disabled="expired" @click="router.push(`/student/tasks/${task.id}/answer`)">{{ task.bestScore == null ? '开始答题' : '再次作答' }}</el-button><el-tag v-if="expired" type="warning">已截止，不能新提交</el-tag><el-button @click="router.push({ path: '/student/submissions', query: { taskId: task.id } })">查看历史成绩</el-button></div></div><div class="panel"><h2>题目概览</h2><div v-for="q in task.questions" :key="q.taskQuestionId" class="list-row">{{ q.position }}. {{ q.stem }} <span class="muted">{{ questionType(q.type) }} · {{ q.score }} 分</span></div></div></section>
    <section v-if="mode === 'answer' && task" class="stack"><div class="panel"><div class="row between"><div><h2>{{ task.title }}</h2><p class="muted">截止：{{ formatTime(task.deadlineAt) }} · 漏答允许提交，未答计零分</p></div><el-tag v-if="expired" type="warning">已截止</el-tag></div></div><div v-for="q in task.questions" :key="q.taskQuestionId" class="panel question-panel"><h3>{{ q.position }}. {{ q.stem }} <small>（{{ questionType(q.type) }} · {{ q.score }} 分）</small></h3><el-checkbox-group v-if="q.type === 'MULTIPLE'" :model-value="answers[q.taskQuestionId] || []" :disabled="!!pendingSubmission" class="answer-options" @update:model-value="(value: string[]) => setMultiple(q.taskQuestionId, value)"><el-checkbox v-for="o in q.options" :key="o.key" :value="o.key">{{ o.key }}. {{ o.text }}</el-checkbox></el-checkbox-group><el-radio-group v-else :model-value="answers[q.taskQuestionId]?.[0] || ''" :disabled="!!pendingSubmission" class="answer-options" @update:model-value="(value: string) => setSingle(q.taskQuestionId, value)"><el-radio v-for="o in q.options" :key="o.key" :value="o.key">{{ o.key }}. {{ o.text }}</el-radio></el-radio-group></div><div class="panel row between"><span>未作答 {{ unanswered }} / {{ task.questions.length }} 题</span><el-button type="primary" :loading="saving" @click="submit">{{ pendingSubmission ? '重试同一次提交' : '提交答卷' }}</el-button></div><el-alert v-if="pendingSubmission" title="上次请求结果尚不明确，请使用同一请求重试；作答内容已暂时锁定。" type="warning" show-icon /></section>
    <section v-if="mode === 'history'" class="panel"><div class="row filters"><el-select v-model="filterCourse" placeholder="全部课程" clearable @change="page = 1; load()"><el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" /></el-select></div><el-table :data="history"><el-table-column prop="taskId" label="任务 ID" /><el-table-column prop="attemptNo" label="提交次数" /><el-table-column prop="totalScore" label="本次得分" /><el-table-column label="提交时间"><template #default="scope">{{ formatTime(scope.row.submittedAt) }}</template></el-table-column><el-table-column label="操作"><template #default="scope"><el-button link type="primary" @click="router.push(`/student/submissions/${scope.row.id}`)">查看答卷</el-button></template></el-table-column></el-table><el-empty v-if="!history.length && !busy" description="暂无提交记录" :image-size="70" /></section>
    <section v-if="mode === 'submission' && submission" class="stack"><div class="panel"><h2>第 {{ submission.attemptNo }} 次提交 · {{ submission.totalScore }} 分</h2><div class="info-grid"><span>当前最高分<strong>{{ submission.bestScore }}</strong></span><span>正确题目<strong>{{ submission.correctCount }} / {{ submission.questionCount }}</strong></span><span>统计代表<strong>{{ submission.representativeSubmissionId === submission.id ? '本答卷' : '其他答卷' }}</strong></span></div><p class="muted">成功提交后立即公开本次答卷的答案与解析；历史答卷会保留。</p><el-button v-if="task && (!task.deadlineAt || Date.now() < new Date(task.deadlineAt).getTime())" @click="router.push(`/student/tasks/${submission.taskId}/answer`)">再次作答</el-button></div><div v-for="(a, index) in submission.answers" :key="a.taskQuestionId" class="panel"><h3>{{ index + 1 }}. {{ task?.questions.find(q => q.taskQuestionId === a.taskQuestionId)?.stem }}</h3><p>我的答案：{{ a.selectedOptions.join('、') || '未作答' }}</p><p>标准答案：{{ a.correctAnswer.join('、') }}</p><p :class="a.correct ? 'success-text' : 'danger-text'">{{ a.correct ? '回答正确' : '回答错误' }} · 得分 {{ a.earnedScore }}</p><p class="muted">解析：{{ a.explanation || '暂无解析' }}</p></div></section>
    <section v-if="mode === 'mistakes'" class="panel"><div class="row filters"><el-select v-model="filterCourse" placeholder="全部课程" clearable @change="page = 1; load()"><el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" /></el-select><el-select v-model="filterMastery" placeholder="全部掌握状态" clearable @change="page = 1; load()"><el-option label="未掌握" value="UNMASTERED" /><el-option label="已掌握" value="MASTERED" /></el-select></div><el-table :data="mistakes"><el-table-column prop="questionId" label="原题 ID" /><el-table-column label="课程"><template #default="scope">{{ courses.find(c => c.id === scope.row.courseId)?.name || scope.row.courseId }}</template></el-table-column><el-table-column prop="wrongCount" label="错误次数" /><el-table-column label="最近答错"><template #default="scope">{{ formatTime(scope.row.lastWrongAt) }}</template></el-table-column><el-table-column label="状态"><template #default="scope"><el-tag :type="scope.row.masteryStatus === 'MASTERED' ? 'success' : 'warning'">{{ scope.row.masteryStatus === 'MASTERED' ? '已掌握' : '未掌握' }}</el-tag></template></el-table-column><el-table-column label="操作"><template #default="scope"><el-button link type="primary" @click="router.push(`/student/mistakes/${scope.row.id}`)">查看与复练</el-button></template></el-table-column></el-table><el-empty v-if="!mistakes.length && !busy" description="暂无错题" :image-size="70" /></section>
    <section v-if="mode === 'mistake' && mistake" class="stack"><div class="panel"><div class="row between"><el-tag :type="mistake.mistake.masteryStatus === 'MASTERED' ? 'success' : 'warning'">{{ mistake.mistake.masteryStatus === 'MASTERED' ? '已掌握' : '未掌握' }}</el-tag><div><el-button @click="markMastery(mistake.mistake.masteryStatus === 'MASTERED' ? 'UNMASTERED' : 'MASTERED')">{{ mistake.mistake.masteryStatus === 'MASTERED' ? '取消掌握' : '标记已掌握' }}</el-button><el-button type="primary" @click="router.push(`/student/mistakes/${mistake.mistake.id}/practice`)">重新练习</el-button></div></div><h2>{{ mistake.stem }}</h2><p v-for="o in mistake.options" :key="o.key" class="option-line">{{ o.key }}. {{ o.text }}</p><p>标准答案：{{ mistake.correctAnswer.join('、') }}</p><p class="muted">解析：{{ mistake.explanation || '暂无解析' }}</p><p class="muted">错误 {{ mistake.mistake.wrongCount }} 次 · 最近答错 {{ formatTime(mistake.mistake.lastWrongAt) }}</p></div><div class="panel"><h2>复练历史</h2><div v-for="p in practiceHistory" :key="p.id" class="list-row"><span>{{ formatTime(p.practicedAt) }} · {{ p.selectedOptions.join('、') || '未作答' }}</span><el-tag :type="p.correct ? 'success' : 'danger'">{{ p.correct ? '正确' : '错误' }}</el-tag></div><el-empty v-if="!practiceHistory.length" description="还没有复练记录" :image-size="70" /></div></section>
    <section v-if="mode === 'practice' && practiceQuestion" class="stack"><div class="panel"><h2>{{ practiceQuestion.stem }}</h2><p class="muted">{{ questionType(practiceQuestion.type) }} · {{ practiceQuestion.score }} 分；提交后显示答案和解析。</p><el-checkbox-group v-if="practiceQuestion.type === 'MULTIPLE'" v-model="practiceAnswer" :disabled="!!practiceResult || !!pendingPractice" class="answer-options"><el-checkbox v-for="o in practiceQuestion.options" :key="o.key" :value="o.key">{{ o.key }}. {{ o.text }}</el-checkbox></el-checkbox-group><el-radio-group v-else :model-value="practiceAnswer[0] || ''" :disabled="!!practiceResult || !!pendingPractice" class="answer-options" @update:model-value="(value: string) => practiceAnswer = [value]"><el-radio v-for="o in practiceQuestion.options" :key="o.key" :value="o.key">{{ o.key }}. {{ o.text }}</el-radio></el-radio-group><el-button v-if="!practiceResult" type="primary" :loading="saving" @click="practice">{{ pendingPractice ? '重试同一次复练' : '提交复练' }}</el-button></div><div v-if="practiceResult" class="panel"><h2>{{ practiceResult.correct ? '回答正确，已自动标记掌握' : '回答错误，恢复未掌握' }}</h2><p>本次得分：{{ practiceResult.earnedScore }}</p><p>标准答案：{{ practiceResult.correctAnswer.join('、') }}</p><p>解析：{{ practiceResult.explanation || '暂无解析' }}</p><el-button @click="back">返回错题详情</el-button></div></section>
    <el-pagination v-if="total > 20 && ['courses', 'course', 'history', 'mistakes'].includes(mode)" v-model:current-page="page" layout="prev, pager, next" :total="total" :page-size="20" @current-change="load" />
    <el-dialog v-model="joinDialog" title="通过邀请码加入课程" width="min(500px, 95vw)"><p class="muted">有效邀请码可直接加入课程，无需教师审批。</p><el-input v-model="inviteCode" maxlength="32" placeholder="输入教师提供的邀请码" @keyup.enter="joinCourse" /><template #footer><el-button @click="joinDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="joinCourse">加入课程</el-button></template></el-dialog>
  </div>
</template>
