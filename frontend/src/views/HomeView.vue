<script setup lang="ts">
import { ElTag } from 'element-plus'
import 'element-plus/es/components/tag/style/css'
</script>

<template>
  <main class="welcome">
    <section class="welcome__card">
      <div class="welcome__logo">融</div>
      <p class="welcome__eyebrow">RONGWANG LEARNING PLATFORM</p>
      <h1>融网学习平台</h1>
      <p>前端工程已完成初始化。当前只包含基础框架，业务页面将在后续阶段按冻结需求逐步实现。</p>
      <el-tag type="success" effect="light">Vue 3 + TypeScript + Element Plus</el-tag>
    </section>
  </main>
</template>

<style scoped>
.welcome { min-height: 100vh; display: grid; place-items: center; padding: 24px; background: linear-gradient(135deg, #eef7f4 0%, #f9fbfa 100%); }
.welcome__card { width: min(560px, 100%); padding: 48px; border: 1px solid #d9e9e5; border-radius: 18px; background: #fff; box-shadow: 0 20px 50px rgb(24 76 70 / 10%); }
.welcome__logo { display: grid; width: 56px; height: 56px; place-items: center; border-radius: 14px; color: #fff; background: #0f766e; font-size: 28px; font-weight: 700; }
.welcome__eyebrow { margin: 24px 0 8px; color: #0f766e; font-size: 12px; font-weight: 700; letter-spacing: 0.12em; }
h1 { margin: 0 0 16px; font-size: clamp(30px, 5vw, 42px); }
p { line-height: 1.8; }
</style>
