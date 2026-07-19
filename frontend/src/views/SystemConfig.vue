<template>
  <div class="card">
    <div class="toolbar">
      <div>
        <el-input v-model="keyword" clearable placeholder="关键字过滤" style="width:260px" />
        <el-button @click="load" style="margin-left:8px">刷新</el-button>
      </div>
    </div>
    <el-alert v-if="!canWrite" type="info" :closable="false" show-icon title="当前账号仅有查看权限，编辑已隐藏。" style="margin-bottom:12px" />
    <el-table :data="filtered" border stripe height="620">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="configKey" label="参数键" width="220" show-overflow-tooltip />
      <el-table-column prop="configName" label="参数名称" width="220" show-overflow-tooltip />
      <el-table-column label="参数值" width="180">
        <template #default="{row}">
          <el-tag v-if="isBool(row)" :type="boolVal(row) ? 'success' : 'info'">{{ boolVal(row) ? '开启' : '关闭' }}</el-tag>
          <span v-else>{{ displayValue(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="可编辑" width="90">
        <template #default="{row}">{{ row.editable ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="260" show-overflow-tooltip />
      <el-table-column v-if="canWrite" fixed="right" label="操作" width="100">
        <template #default="{row}">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="编辑参数" width="620px">
      <el-form label-width="110px">
        <el-form-item label="参数键"><el-input v-model="form.configKey" disabled /></el-form-item>
        <el-form-item label="参数名称"><el-input v-model="form.configName" disabled /></el-form-item>
        <el-form-item label="参数值">
          <el-select v-if="isBool(form)" v-model="form.configValue" style="width:100%">
            <el-option label="开启" value="true" />
            <el-option label="关闭" value="false" />
          </el-select>
          <el-input v-else v-model="form.configValue" />
        </el-form-item>
        <el-form-item label="备注"><div class="remark-text">{{ form.remark || '-' }}</div></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog=false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { get, post } from '../api'
import { ElMessage } from 'element-plus'
import { ROLE_SETS } from '../permissions'

const rows = ref<any[]>([])
const keyword = ref('')
const dialog = ref(false)
const form = ref<any>({})

const BOOL_KEYS = ['print.auto.enabled']
function isBool(row:any){
  if (!row) return false
  if (BOOL_KEYS.includes(row.configKey)) return true
  const v = String(row.configValue ?? '').trim().toLowerCase()
  return v === 'true' || v === 'false'
}
function boolVal(row:any){ return String(row.configValue ?? '').trim().toLowerCase() === 'true' }
function displayValue(row:any){ return row.configValue == null || row.configValue === '' ? '（空）' : row.configValue }

const currentRole = computed(() => { try { return JSON.parse(localStorage.getItem('loginUser') || '{}').role || '' } catch { return '' } })
const canWrite = computed(() => currentRole.value === 'ADMIN' || ROLE_SETS.admin.includes(currentRole.value))
const filtered = computed(() => {
  if (!keyword.value) return rows.value
  const k = keyword.value.toLowerCase()
  return rows.value.filter(r => JSON.stringify(r).toLowerCase().includes(k))
})

async function load(){ rows.value = await get('/configs') }
function openEdit(row:any){
  if (!canWrite.value) return
  if (row.editable === false){ ElMessage.warning('该参数为系统内部记录，不可编辑'); return }
  form.value = JSON.parse(JSON.stringify(row || {}))
  dialog.value = true
}
async function save(){
  if (!canWrite.value) return
  await post('/configs', form.value)
  dialog.value = false
  ElMessage.success('已保存')
  load()
}
onMounted(load)
</script>
<style scoped>
.remark-text{ color:#64748b; font-size:13px; line-height:1.5; }
</style>
