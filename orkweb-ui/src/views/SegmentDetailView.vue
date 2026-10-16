<template>
  <div v-loading="loading">
    <el-card v-if="segment">
      <template #header>
        <div class="header">
          <span>录音详情 #{{ segment.id }}</span>
          <el-button @click="$router.back()">返回</el-button>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="时间">{{ formatTime(segment.timestamp) }}</el-descriptions-item>
        <el-descriptions-item label="时长">{{ formatDuration(segment.duration) }}</el-descriptions-item>
        <el-descriptions-item label="主叫">{{ segment.localParty }}</el-descriptions-item>
        <el-descriptions-item label="被叫">{{ segment.remoteParty }}</el-descriptions-item>
        <el-descriptions-item label="方向">
          <el-tag :type="segment.direction === 'IN' ? 'success' : 'warning'" size="small">
            {{ segment.direction === 'IN' ? '呼入' : segment.direction === 'OUT' ? '呼出' : segment.direction }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="端口">{{ segment.portName }}</el-descriptions-item>
        <el-descriptions-item label="坐席">{{ segment.loginString }}</el-descriptions-item>
        <el-descriptions-item label="入口">{{ segment.localEntryPoint }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="segment.tapeId" class="player-section">
        <h3>播放</h3>
        <AudioPlayer :segment-id="segment.id" :tape-id="segment.tapeId" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api/client'
import AudioPlayer from '@/components/AudioPlayer.vue'

const route = useRoute()
const loading = ref(false)
const segment = ref<any>(null)

async function load() {
  loading.value = true
  try {
    const { data } = await api.get(`/api/v1/segments/${route.params.id}`)
    segment.value = data
  } finally {
    loading.value = false
  }
}

function formatTime(ts: string) {
  return ts ? new Date(ts).toLocaleString('zh-CN', { hour12: false }) : ''
}

function formatDuration(sec: number) {
  if (sec == null) return ''
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return m > 0 ? `${m}分${s}秒` : `${s}秒`
}

onMounted(load)
</script>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: center; }
.player-section { margin-top: 24px; }
</style>
