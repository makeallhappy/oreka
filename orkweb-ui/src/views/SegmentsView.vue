<template>
  <div>
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :model="query" inline>
        <el-form-item label="主叫">
          <el-input v-model="query.localParty" placeholder="主叫号码" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="被叫">
          <el-input v-model="query.remoteParty" placeholder="被叫号码" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="方向">
          <el-select v-model="query.direction" placeholder="全部" clearable style="width: 100px">
            <el-option label="呼入" value="IN" />
            <el-option label="呼出" value="OUT" />
          </el-select>
        </el-form-item>
        <el-form-item label="端口">
          <el-input v-model="query.portName" placeholder="SIP/1001" clearable style="width: 130px" />
        </el-form-item>
        <el-form-item label="时长">
          <el-input-number v-model="query.minDuration" :min="0" placeholder="最短(秒)" style="width: 120px" controls-position="right" />
          <span style="margin: 0 6px">-</span>
          <el-input-number v-model="query.maxDuration" :min="0" placeholder="最长(秒)" style="width: 120px" controls-position="right" />
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker v-model="timeRange" type="datetimerange" range-separator="至"
                          start-placeholder="开始" end-placeholder="结束" style="width: 340px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 结果表格 -->
    <el-card>
      <el-table :data="rows" v-loading="loading" stripe @row-click="onRowClick" style="cursor: pointer">
        <el-table-column prop="timestamp" label="时间" width="170">
          <template #default="{ row }">{{ formatTime(row.timestamp) }}</template>
        </el-table-column>
        <el-table-column prop="localParty" label="主叫" width="110" />
        <el-table-column prop="remoteParty" label="被叫" width="110" />
        <el-table-column prop="direction" label="方向" width="80">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'IN' ? 'success' : 'warning'" size="small">
              {{ row.direction === 'IN' ? '呼入' : row.direction === 'OUT' ? '呼出' : row.direction }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="时长" width="100">
          <template #default="{ row }">{{ formatDuration(row.duration) }}</template>
        </el-table-column>
        <el-table-column prop="portName" label="端口" width="120" />
        <el-table-column prop="loginString" label="坐席" width="100" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click.stop="onPlay(row)" :icon="VideoPlay">播放</el-button>
            <el-button size="small" type="primary" link @click.stop="onDownload(row)" :icon="Download">下载</el-button>
            <el-button size="small" type="primary" link @click.stop="onDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @current-change="load"
        @size-change="onSearch"
      />
    </el-card>

    <!-- 播放对话框 -->
    <el-dialog v-model="playDialog" :title="`播放录音 #${currentSegment?.id}`" width="640px" destroy-on-close>
      <AudioPlayer v-if="currentSegment" :segment-id="currentSegment.id" :tape-id="currentSegment.tapeId" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Refresh, VideoPlay, Download } from '@element-plus/icons-vue'
import api from '@/api/client'
import AudioPlayer from '@/components/AudioPlayer.vue'

interface Segment {
  id: number
  timestamp: string
  duration: number
  localParty: string
  remoteParty: string
  direction: string
  portName: string
  loginString: string
  tapeId: number | null
}

const router = useRouter()
const loading = ref(false)
const rows = ref<Segment[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const timeRange = ref<[Date, Date] | null>(null)
const query = ref({
  localParty: '',
  remoteParty: '',
  direction: '',
  portName: '',
  minDuration: undefined as number | undefined,
  maxDuration: undefined as number | undefined,
})
const playDialog = ref(false)
const currentSegment = ref<Segment | null>(null)

async function load() {
  loading.value = true
  try {
    const params: Record<string, any> = {
      page: page.value - 1,
      size: pageSize.value,
      sort: 'timestamp,desc',
    }
    if (query.value.localParty) params.localParty = query.value.localParty
    if (query.value.remoteParty) params.remoteParty = query.value.remoteParty
    if (query.value.direction) params.direction = query.value.direction
    if (query.value.portName) params.portName = query.value.portName
    if (query.value.minDuration != null) params.minDuration = query.value.minDuration
    if (query.value.maxDuration != null) params.maxDuration = query.value.maxDuration
    if (timeRange.value?.[0]) params.from = timeRange.value[0].toISOString()
    if (timeRange.value?.[1]) params.to = timeRange.value[1].toISOString()

    const { data } = await api.get('/api/v1/segments', { params })
    rows.value = data.content
    total.value = data.totalElements
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  load()
}

function onReset() {
  query.value = { localParty: '', remoteParty: '', direction: '', portName: '', minDuration: undefined, maxDuration: undefined }
  timeRange.value = null
  onSearch()
}

function onRowClick(row: Segment) {
  router.push(`/segments/${row.id}`)
}

function onPlay(row: Segment) {
  currentSegment.value = row
  playDialog.value = true
}

function onDownload(row: Segment) {
  if (row.tapeId) {
    window.open(`${api.defaults.baseURL}/api/v1/tapes/${row.tapeId}/download`, '_blank')
  }
}

function onDetail(row: Segment) {
  router.push(`/segments/${row.id}`)
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
.search-card { margin-bottom: 16px; }
.pagination { margin-top: 16px; justify-content: flex-end; }
</style>
