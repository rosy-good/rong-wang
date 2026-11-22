<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from './stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuth()
const isLogin = computed(() => route.name === 'login')
async function signOut() { await auth.logout(); await router.replace('/login') }
</script>

<template>
  <RouterView v-if="isLogin" />
  <div v-else class="shell">
    <aside class="sidebar">
      <RouterLink class="brand" :to="auth.user?.role === 'TEACHER' ? '/teacher/courses' : '/student/courses'"><span class="logo">融</span><span>融网学习平台<small>RONGWANG LEARNING</small></span></RouterLink>
      <div class="nav-caption">{{ auth.user?.role === 'TEACHER' ? '教师工作区' : '学生学习区' }}</div>
      <nav v-if="auth.user?.role === 'TEACHER'">
        <RouterLink to="/teacher/courses">课程管理</RouterLink>
        <RouterLink to="/teacher/statistics">成绩统计</RouterLink>
      </nav>
      <nav v-else>
        <RouterLink to="/student/courses">我的课程</RouterLink>
        <RouterLink to="/student/submissions">历史成绩</RouterLink>
        <RouterLink to="/student/mistakes">个人错题本</RouterLink>
      </nav>
      <div class="sidebar-bottom">课程 · 练习 · 复习</div>
    </aside>
    <div class="workarea">
      <header class="topbar"><span>在线教学与练习管理</span><div class="row"><span class="avatar">{{ auth.user?.displayName?.slice(0, 1) }}</span><span>{{ auth.user?.displayName }}</span><el-button text @click="signOut">退出登录</el-button></div></header>
      <main class="main"><RouterView /></main>
    </div>
  </div>
</template>
