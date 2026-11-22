import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, clearCsrf } from '../api/http'
import type { User } from '../api/types'

export const useAuth = defineStore('auth', () => {
  const user = ref<User | null>(null)
  async function restore() {
    try { user.value = await api<User>('GET', '/auth/me') }
    catch { user.value = null; clearCsrf() }
    return user.value
  }
  async function login(username: string, password: string) {
    clearCsrf()
    user.value = await api<User>('POST', '/auth/login', { username, password })
    return user.value
  }
  async function logout() {
    try { await api('POST', '/auth/logout') }
    finally { user.value = null; clearCsrf() }
  }
  return { user, restore, login, logout }
})
