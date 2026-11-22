<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, errorMessage, type Page } from '../api/http'
import type { Course, TeacherTask } from '../api/types'

const route = useRoute()
const courses = ref<Course[]>([])
const course = ref<Course | null>(null)
const tasks = ref<TeacherTask[]>([])
const loading = ref(false)
const error = ref('')
const page = ref(1)
const total = ref(0)
let requestNumber = 0

async function load() {
  const request = ++requestNumber
  const id = route.params.id
  loading.value = true
  error.value = ''
  courses.value = []; tasks.value = []; course.value = null; total.value = 0
  try {
    if (id) {
      const [detail, result] = await Promise.all([
        api<Course>('GET', `/teacher/courses/${id}`),
        api<Page<TeacherTask>>('GET', `/teacher/courses/${id}/tasks`, undefined, { params: { page: page.value, size: 20 } }),
      ])
      if (request !== requestNumber) return
      course.value = detail; tasks.value = result.items; total.value = result.total
    } else {
      const result = await api<Page<Course>>('GET', '/teacher/courses', undefined, { params: { page: page.value, size: 20 } })
      if (request !== requestNumber) return
      courses.value = result.items; total.value = result.total
    }
  } catch (e) { if (request === requestNumber) error.value = errorMessage(e) }
  finally { if (request === requestNumber) loading.value = false }
}
watch(() => route.params.id, () => { page.value = 1; void load() }, { immediate: true })
</script>

<template>
  <div v-loading="loading">
    <div class="page-head">
      <div>
        <RouterLink v-if="route.params.id" class="text-link" :to="`/teacher/courses/${route.params.id}`">← 返回课程详情</RouterLink>
        <p class="eyebrow">TEACHER WORKSPACE</p>
        <h1>成绩统计{{ course ? ` · ${course.name}` : '' }}</h1>
      </div>
      <el-button :disabled="loading" @click="load">刷新</el-button>
    </div>
    <p class="muted">{{ route.params.id ? '选择已发布任务，查看提交率、班级平均分、正确率和学生答卷。草稿发布后才可查看统计。' : '选择课程，再查看该课程的任务成绩。' }}</p>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="space" />
    <template v-else>
      <section v-if="!route.params.id" class="grid-cards">
        <RouterLink v-for="item in courses" :key="item.id" class="course-card" :to="`/teacher/courses/${item.id}/results`">
          <h2>{{ item.name }}</h2><p class="muted">{{ item.description || '暂无课程介绍' }}</p><span class="text-link">查看任务成绩 →</span>
        </RouterLink>
        <el-empty v-if="!loading && !courses.length" description="暂无课程，请先在课程管理中创建课程" />
      </section>
      <section v-else class="panel">
        <div v-for="item in tasks" :key="item.id" class="list-row">
          <div><h3>{{ item.title }}</h3><p class="muted">{{ item.status === 'PUBLISHED' ? '已发布' : '草稿' }} · 总分 {{ item.totalScore }} · 截止 {{ item.deadlineAt ? new Date(item.deadlineAt).toLocaleString('zh-CN') : '未设置' }}</p></div>
          <RouterLink v-if="item.status === 'PUBLISHED'" class="text-link" :to="`/teacher/tasks/${item.id}/results`">查看成绩统计 →</RouterLink>
          <span v-else class="muted">尚未发布，暂无成绩</span>
        </div>
        <el-empty v-if="!loading && !tasks.length" description="本课程暂无任务，请先创建并发布练习任务" />
      </section>
      <el-pagination v-if="total > 20" v-model:current-page="page" layout="prev, pager, next" :total="total" :page-size="20" @current-change="load" />
    </template>
  </div>
</template>
