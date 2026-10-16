<template>
  <div>
    <el-card>
      <template #header>
        <div class="header">
          <span>用户管理</span>
          <el-button type="primary" :icon="Plus" @click="onCreate">新建用户</el-button>
        </div>
      </template>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="firstname" label="名" width="120" />
        <el-table-column prop="lastname" label="姓" width="120" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="disabled" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.disabled ? 'danger' : 'success'" size="small">
              {{ row.disabled ? '禁用' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="onEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑/新建对话框 -->
    <el-dialog v-model="dialog" :title="isEdit ? '编辑用户' : '新建用户'" width="480px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="名" required>
          <el-input v-model="form.firstname" />
        </el-form-item>
        <el-form-item label="姓">
          <el-input v-model="form.lastname" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item :label="isEdit ? '新密码' : '密码'" :required="!isEdit">
          <el-input v-model="form.password" type="password" show-password
                    :placeholder="isEdit ? '留空则不修改' : ''" />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="登录名">
          <el-input v-model="form.loginString" placeholder="默认=名" />
        </el-form-item>
        <el-form-item v-if="isEdit" label="状态">
          <el-switch v-model="form.disabled" active-text="禁用" inactive-text="正常" />
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
const form = ref({ firstname: '', lastname: '', email: '', password: '', loginString: '', disabled: false })

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/api/v1/users')
    rows.value = data
  } finally {
    loading.value = false
  }
}

function onCreate() {
  isEdit.value = false
  form.value = { firstname: '', lastname: '', email: '', password: '', loginString: '', disabled: false }
  dialog.value = true
}

function onEdit(row: any) {
  isEdit.value = true
  editId.value = row.id
  form.value = { firstname: row.firstname, lastname: row.lastname, email: row.email, password: '', loginString: '', disabled: row.disabled }
  dialog.value = true
}

async function onSave() {
  if (!form.value.firstname) {
    ElMessage.warning('名必填')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      const body: any = { firstname: form.value.firstname, lastname: form.value.lastname, email: form.value.email, disabled: form.value.disabled }
      if (form.value.password) body.password = form.value.password
      await api.put(`/api/v1/users/${editId.value}`, body)
      ElMessage.success('已更新')
    } else {
      await api.post('/api/v1/users', form.value)
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
    await ElMessageBox.confirm(`确定删除用户 ${row.firstname} ${row.lastname}?`, '确认', { type: 'warning' })
    await api.delete(`/api/v1/users/${row.id}`)
    ElMessage.success('已删除')
    load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

onMounted(load)
</script>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: center; }
</style>
