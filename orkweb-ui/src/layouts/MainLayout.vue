<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">Oreka 录音系统</div>
      <el-menu :default-active="$route.path" router class="menu">
        <el-menu-item index="/">
          <el-icon><Headset /></el-icon>
          <span>录音查询</span>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/programs">
          <el-icon><AlarmClock /></el-icon>
          <span>录音计划</span>
        </el-menu-item>
        <el-menu-item index="/services">
          <el-icon><Monitor /></el-icon>
          <span>服务实例</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div />
        <div class="header-right">
          <LiveCallsIndicator />
          <el-dropdown @command="onCommand">
            <span class="user">
              {{ auth.user?.firstname || '用户' }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import { Headset, User, AlarmClock, Monitor, ArrowDown } from '@element-plus/icons-vue'
import LiveCallsIndicator from '@/components/LiveCallsIndicator.vue'

const auth = useAuthStore()
const router = useRouter()

function onCommand(cmd: string) {
  if (cmd === 'logout') {
    auth.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout { height: 100vh; }
.aside { background: #001529; }
.logo { color: #fff; font-size: 18px; font-weight: 600; padding: 20px; text-align: center; }
.menu { background: #001529; border-right: none; }
:deep(.el-menu-item) { color: rgba(255,255,255,0.75); }
:deep(.el-menu-item.is-active) { color: #fff; background: #1890ff; }
:deep(.el-menu-item:hover) { background: rgba(255,255,255,0.08); }
.header { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #eee; background: #fff; }
.header-right { display: flex; align-items: center; }
.user { cursor: pointer; display: flex; align-items: center; gap: 4px; }
.main { background: #f5f7fa; padding: 16px; }
</style>
