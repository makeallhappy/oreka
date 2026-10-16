<template>
  <el-popover placement="bottom-end" :width="420" trigger="click">
    <template #reference>
      <el-badge :value="liveCalls.length" :hidden="liveCalls.length === 0" :max="99" class="live-badge">
        <el-button :type="connected ? (liveCalls.length > 0 ? 'danger' : 'success') : 'info'" round size="small">
          <el-icon class="pulse" :class="{ active: connected }"><Phone /></el-icon>
          <span style="margin-left: 6px">实时通话 {{ liveCalls.length > 0 ? `(${liveCalls.length})` : '' }}</span>
        </el-button>
      </el-badge>
    </template>

    <div class="live-panel">
      <div class="panel-header">
        <span class="title">正在录音的通话</span>
        <el-tag :type="connected ? 'success' : 'danger'" size="small">
          {{ connected ? '已连接' : '连接断开' }}
        </el-tag>
      </div>

      <el-empty v-if="liveCalls.length === 0" description="当前无进行中通话" :image-size="60" />

      <div v-for="call in liveCalls" :key="call.callId" class="call-item">
        <div class="call-parties">
          <span class="party">{{ call.localParty || '?' }}</span>
          <el-icon class="arrow"><Right /></el-icon>
          <span class="party">{{ call.remoteParty || '?' }}</span>
          <el-tag :type="call.direction === 'IN' ? 'success' : 'warning'" size="small">
            {{ call.direction === 'IN' ? '呼入' : call.direction === 'OUT' ? '呼出' : call.direction }}
          </el-tag>
        </div>
        <div class="call-meta">
          <span>端口 {{ call.capturePort || '-' }}</span>
          <span>{{ call.service }}</span>
          <span>开始 {{ formatTime(call.startedAt) }}</span>
          <span class="duration">{{ elapsed(call.startedAt) }}</span>
        </div>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { Phone, Right } from '@element-plus/icons-vue'
import { useLiveCalls } from '@/composables/useLiveCalls'

const { liveCalls, connected } = useLiveCalls()

// 每秒刷新一次通话时长显示
const tick = ref(0)
let timer: number
onMounted(() => { timer = window.setInterval(() => tick.value++, 1000) })
onBeforeUnmount(() => clearInterval(timer))

function formatTime(iso: string) {
  return iso ? new Date(iso).toLocaleTimeString('zh-CN', { hour12: false }) : ''
}

function elapsed(iso: string) {
  if (!iso) return ''
  void tick.value // 依赖 tick 触发重算
  const sec = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000))
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `已通话 ${m}:${s.toString().padStart(2, '0')}`
}
</script>

<style scoped>
.live-badge { margin-right: 16px; }
.pulse.active { animation: pulse 2s infinite; }
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
.panel-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.title { font-weight: 600; }
.call-item { padding: 10px; border: 1px solid #ebeef5; border-radius: 6px; margin-bottom: 8px; }
.call-parties { display: flex; align-items: center; gap: 8px; font-weight: 600; margin-bottom: 6px; }
.arrow { color: #909399; }
.call-meta { display: flex; gap: 12px; font-size: 12px; color: #909399; flex-wrap: wrap; }
.duration { color: #f56c6c; font-variant-numeric: tabular-nums; }
</style>
