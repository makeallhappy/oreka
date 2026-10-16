<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2 class="title">Oreka 录音系统</h2>
      <el-form :model="form" @submit.prevent="onLogin">
        <el-form-item>
          <el-input v-model="form.loginString" placeholder="登录名" size="large" :prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large"
                    :prefix-icon="Lock" show-password @keyup.enter="onLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" class="btn" :loading="loading" @click="onLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const form = ref({ loginString: '', password: '' })
const loading = ref(false)

async function onLogin() {
  if (!form.value.loginString || !form.value.password) {
    ElMessage.warning('请输入登录名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form.value.loginString, form.value.password)
    ElMessage.success('登录成功')
    router.push((route.query.redirect as string) || '/')
  } catch (e: any) {
    ElMessage.error(e.response?.status === 401 ? '登录名或密码错误' : '登录失败,请检查服务')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page { height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.login-card { width: 400px; }
.title { text-align: center; margin: 0 0 24px; color: #333; }
.btn { width: 100%; }
</style>
