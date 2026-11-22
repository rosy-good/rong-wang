<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { errorMessage } from '../api/http'
import { useAuth } from '../stores/auth'

const username = ref('')
const password = ref('')
const pending = ref(false)
const loginError = ref('')
const auth = useAuth()
const route = useRoute()
const router = useRouter()
async function submit() {
  if (pending.value) return
  loginError.value = ''
  if (!username.value.trim() || !password.value) { loginError.value = '请输入账号和密码'; return }
  pending.value = true
  try {
    const user = await auth.login(username.value.trim(), password.value)
    const home = user.role === 'TEACHER' ? '/teacher/courses' : '/student/courses'
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith(user.role === 'TEACHER' ? '/teacher/' : '/student/') ? route.query.redirect : home
    await router.replace(redirect)
  } catch (error) { loginError.value = errorMessage(error); ElMessage.error(loginError.value) }
  finally { pending.value = false }
}
</script>

<template>
  <div class="login-page"><div class="login-card">
    <div class="login-mark">融</div><p class="eyebrow">RONGWANG LEARNING PLATFORM</p>
    <h1>欢迎使用融网学习平台</h1><p class="muted">使用平台维护者分配的教师或学生账号登录</p>
    <el-alert v-if="loginError" :title="loginError" type="error" show-icon :closable="false" />
    <el-form class="login-form" @submit.prevent="submit">
      <el-form-item label="账号"><el-input v-model="username" autocomplete="username" placeholder="请输入账号" /></el-form-item>
      <el-form-item label="密码"><el-input v-model="password" type="password" autocomplete="current-password" show-password placeholder="请输入密码" /></el-form-item>
      <el-button type="primary" size="large" native-type="submit" :loading="pending" class="full">登录</el-button>
    </el-form><p class="muted footnote">账号由平台维护者创建，第一版不提供自助注册。</p>
  </div></div>
</template>
