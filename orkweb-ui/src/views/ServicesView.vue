<template>
  <div>
    <el-card>
      <template #header>
        <span>orkaudio 服务实例</span>
      </template>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" width="180" />
        <el-table-column prop="hostname" label="主机" width="180" />
        <el-table-column prop="tcpPort" label="TCP 端口" width="110" />
        <el-table-column prop="recordMaster" label="主录音" width="100">
          <template #default="{ row }">
            <el-tag :type="row.recordMaster ? 'success' : 'info'" size="small">
              {{ row.recordMaster ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileServePath" label="文件路径" />
      </el-table>
      <el-empty v-if="!loading && rows.length === 0" description="暂无服务实例(orkaudio 上报后自动注册)" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import api from '@/api/client'

const loading = ref(false)
const rows = ref<any[]>([])

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/api/v1/services')
    rows.value = data
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
