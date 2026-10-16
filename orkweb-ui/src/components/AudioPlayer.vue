<template>
  <div class="player">
    <div ref="waveformRef" class="waveform" />
    <div class="controls">
      <el-button :icon="isPlaying ? VideoPause : VideoPlay" type="primary" circle @click="togglePlay" />
      <span class="time">{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</span>
      <el-slider v-model="volume" :max="100" class="volume" @change="onVolume" />
      <el-icon class="volume-icon"><Mute /></el-icon>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import WaveSurfer from 'wavesurfer.js'
import { VideoPlay, VideoPause, Mute } from '@element-plus/icons-vue'
import api from '@/api/client'

const props = defineProps<{
  segmentId: number
  tapeId: number | null
}>()

const waveformRef = ref<HTMLElement>()
const isPlaying = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const volume = ref(80)
let wavesurfer: WaveSurfer | null = null

onMounted(async () => {
  if (!props.tapeId || !waveformRef.value) return

  wavesurfer = WaveSurfer.create({
    container: waveformRef.value,
    waveColor: '#a0c4ff',
    progressColor: '#409eff',
    cursorColor: '#409eff',
    height: 80,
    normalize: true,
  })

  // 带 JWT 拉音频 blob
  try {
    const { data } = await api.get(`/api/v1/tapes/${props.tapeId}/audio`, {
      responseType: 'blob',
    })
    const url = URL.createObjectURL(data)
    await wavesurfer.load(url)

    wavesurfer.on('ready', () => {
      duration.value = wavesurfer?.getDuration() || 0
    })
    wavesurfer.on('audioprocess', (t: number) => {
      currentTime.value = t
    })
    wavesurfer.on('play', () => (isPlaying.value = true))
    wavesurfer.on('pause', () => (isPlaying.value = false))
  } catch (e) {
    console.error('加载音频失败', e)
  }
})

function togglePlay() {
  wavesurfer?.playPause()
}

function onVolume(v: number) {
  wavesurfer?.setVolume(v / 100)
}

function formatTime(sec: number) {
  if (!sec) return '0:00'
  const m = Math.floor(sec / 60)
  const s = Math.floor(sec % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

onBeforeUnmount(() => {
  wavesurfer?.destroy()
})
</script>

<style scoped>
.player { width: 100%; }
.waveform { width: 100%; margin-bottom: 16px; }
.controls { display: flex; align-items: center; gap: 12px; }
.time { min-width: 90px; font-variant-numeric: tabular-nums; color: #666; }
.volume { flex: 1; max-width: 200px; }
.volume-icon { color: #999; }
</style>
