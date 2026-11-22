import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import TeacherView from '../views/TeacherView.vue'
import StudentView from '../views/StudentView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/teacher/statistics', name: 'teacher-statistics', component: () => import('../views/TeacherGradesView.vue') },
    { path: '/teacher/courses/:id/results', name: 'teacher-course-results', component: () => import('../views/TeacherGradesView.vue') },
    { path: '/', redirect: '/login' },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/teacher/courses', name: 'teacher-courses', component: TeacherView, props: { mode: 'courses' } },
    { path: '/teacher/courses/:id', name: 'teacher-course', component: TeacherView, props: { mode: 'course' } },
    { path: '/teacher/courses/:id/questions', name: 'teacher-questions', component: TeacherView, props: { mode: 'questions' } },
    { path: '/teacher/courses/:id/questions/new', name: 'teacher-question-new', component: TeacherView, props: { mode: 'question-edit' } },
    { path: '/teacher/questions/:questionId/edit', name: 'teacher-question-edit', component: TeacherView, props: { mode: 'question-edit' } },
    { path: '/teacher/courses/:id/tasks', name: 'teacher-tasks', component: TeacherView, props: { mode: 'tasks' } },
    { path: '/teacher/courses/:id/tasks/new', name: 'teacher-task-new', component: TeacherView, props: { mode: 'task-edit' } },
    { path: '/teacher/tasks/:taskId/edit', name: 'teacher-task-edit', component: TeacherView, props: { mode: 'task-edit' } },
    { path: '/teacher/tasks/:taskId', name: 'teacher-task', component: TeacherView, props: { mode: 'task' } },
    { path: '/teacher/tasks/:taskId/results', name: 'teacher-results', component: TeacherView, props: { mode: 'results' } },
    { path: '/teacher/submissions/:submissionId', name: 'teacher-submission', component: TeacherView, props: { mode: 'submission' } },
    { path: '/student/courses', name: 'student-courses', component: StudentView, props: { mode: 'courses' } },
    { path: '/student/courses/:id', name: 'student-course', component: StudentView, props: { mode: 'course' } },
    { path: '/student/tasks/:taskId', name: 'student-task', component: StudentView, props: { mode: 'task' } },
    { path: '/student/tasks/:taskId/answer', name: 'student-answer', component: StudentView, props: { mode: 'answer' } },
    { path: '/student/submissions', name: 'student-history', component: StudentView, props: { mode: 'history' } },
    { path: '/student/submissions/:submissionId', name: 'student-submission', component: StudentView, props: { mode: 'submission' } },
    { path: '/student/mistakes', name: 'student-mistakes', component: StudentView, props: { mode: 'mistakes' } },
    { path: '/student/mistakes/:mistakeId', name: 'student-mistake', component: StudentView, props: { mode: 'mistake' } },
    { path: '/student/mistakes/:mistakeId/practice', name: 'student-practice', component: StudentView, props: { mode: 'practice' } },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundView.vue') },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuth()
  if (!auth.user) await auth.restore()
  if (to.name === 'login') return auth.user ? (auth.user.role === 'TEACHER' ? '/teacher/courses' : '/student/courses') : true
  if (!auth.user) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.path.startsWith('/teacher/') && auth.user.role !== 'TEACHER') return '/student/courses'
  if (to.path.startsWith('/student/') && auth.user.role !== 'STUDENT') return '/teacher/courses'
  return true
})

export default router
