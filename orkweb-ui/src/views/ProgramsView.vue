<template>
  <div>
    <el-card>
      <template #header>
        <div class="header">
          <span>录音计划</span>
          <el-button type="primary" :icon="Plus" @click="onCreate">新建计划</el-button>
        </div>
      </template>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" width="150" />
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="direction" label="方向" width="90">
          <template #default="{ row }">
            <el-tag size="small">{{ directionLabel(row.direction) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="randomPercent" label="抽样%" width="90" />
        <el-table-column prop="active" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.active ? 'success' : 'info'" size="small">{{ row.active ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="onEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="onDelete(row)">废弃</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" :title="isEdit ? '编辑计划' : '新建计划'" width="560px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.active" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="方向">
          <el-select v-model="form.direction" style="width: 100%">
            <el-option label="全部" value="ALL" />
            <el-option label="呼入" value="IN" />
            <el-option label="呼出" value="OUT" />
          </el-select>
        </el-form-item>
        <el-form-item label="时长范围">
          <el-input-number v-model="form.minDuration" :min="0" placeholder="最短(秒)" style="width: 150px" controls-position="right" />
          <span style="margin: 0 8px">-</span>
          <el-input-number v-model="form.maxDuration" :min="0" placeholder="最长(秒)" style="width: 150px" controls-position="right" />
        </el-form-item>
        <el-form-item label="抽样比例%">
          <el-input-number v-model="form.randomPercent" :min="0" :max="100" style="width: 150px" controls-position="right" />
        </el-form-item>
        <el-form-item label="主叫">
          <el-input v-model="form.localParty" placeholder="留空=不限" />
        </el-form-item>
        <el-form-item label="被叫">
          <el-input v-model="form.remoteParty" placeholder="留空=不限" />
        </el-form-item>
        <el-form-item label="保留时长">
          <el-input-number v-model="form.keepForHours" :min="0" style="width: 150px" controls-position="right" />
          <span style="margin-left: 8px">小时</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import api from '@/api/client'

const loading = ref(false)
const saving = ref(false)
const rows = ref<any[]>([])
const dialog = ref(false)
const isEdit = ref(false)
const editId = ref(0)
const form = ref<any>({})

function directionLabel(d: string) {
  return d === 'IN' ? '呼入' : d === 'OUT' ? '呼出' : '全部'
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/api/v1/programs')
    rows.value = data
  } finally {
    loading.value = false
  }
}

function onCreate() {
  isEdit.value = false
  form.value = { name: '', description: '', active: true, direction: 'ALL', minDuration: 0, maxDuration: 0, randomPercent: 100, localParty: '', remoteParty: '', keepForHours: 0 }
  dialog.value = true
}

function onEdit(row: any) {
  isEdit.value = true
  editId.value = row.id
  form.value = { ...row }
  dialog.value = true
}

async function onSave() {
  if (!form.value.name) {
    ElMessage.warning('名称必填')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await api.put(`/api/v1/programs/${editId.value}`, form.value)
      ElMessage.success('已更新')
    } else {
      await api.post('/api/v1/programs', form.value)
      ElMessage.success('已创建')
    }
    dialog.value = false
    load()
  } catch (e: any) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function onDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定废弃计划「${row.name}」?`, '确认', { type: 'warning' })
    await api.delete(`/api/v1/programs/${row.id}`)
    ElMessage.success('已废弃')
    load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

onMounted(load)
</script>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: center; }
</style>
