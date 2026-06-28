<template>
  <span class="import-btn-wrap">
    <el-button
      v-if="visible"
      type="success"
      :icon="UploadFilled"
      :loading="importing"
      @click="triggerPick"
    >{{ label }}</el-button>
    <el-link
      v-if="templateUrl"
      type="primary"
      :href="templateUrl"
      target="_blank"
      download
      class="tpl-link"
    >{{ templateLabel }}</el-link>
    <input
      ref="fileInputRef"
      type="file"
      accept=".xlsx,.xlsm,.xls,.csv"
      style="display:none"
      @change="onFileChange"
    />
    <el-dialog v-model="errDialog" title="导入错误明细" width="900px">
      <p class="hint">
        批次号 <b>{{ lastBatch?.batchNo }}</b>｜共 {{ lastBatch?.totalRows }} 行，
        成功 {{ lastBatch?.successRows }}，失败 {{ lastBatch?.failedRows }}。
      </p>
      <el-table :data="errors" border height="420">
        <el-table-column prop="rowNo" label="行号" width="90" />
        <el-table-column prop="rawData" label="原始数据" />
        <el-table-column prop="errorMessage" label="错误信息" />
      </el-table>
    </el-dialog>
  </span>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { get, post } from '../api'
import { ROLE_SETS } from '../permissions'

const props = withDefaults(defineProps<{
  type: string
  label?: string
  templateUrl?: string
  templateLabel?: string
  writeRoles?: string[]
}>(), {
  label: '导入',
  templateLabel: '下载导入模板',
  writeRoles: () => ROLE_SETS.planner
})

const emit = defineEmits<{
  (e: 'imported', batch: any): void
}>()

const fileInputRef = ref<HTMLInputElement | null>(null)
const importing = ref(false)
const errDialog = ref(false)
const errors = ref<any[]>([])
const lastBatch = ref<any>(null)

const currentRole = computed(() => {
  try { return JSON.parse(localStorage.getItem('loginUser') || '{}').role || '' } catch { return '' }
})
const visible = computed(() => currentRole.value === 'ADMIN' || props.writeRoles.includes(currentRole.value))

function triggerPick(){
  if (!visible.value) {
    ElMessage.warning('当前账号没有导入权限')
    return
  }
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
    fileInputRef.value.click()
  }
}

async function onFileChange(e: Event){
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    importing.value = true
    const fd = new FormData()
    fd.append('file', file)
    const batch: any = await post(`/imports/${props.type}`, fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    lastBatch.value = batch
    const total = batch?.totalRows ?? 0
    const success = batch?.successRows ?? 0
    const failed = batch?.failedRows ?? 0
    if (failed > 0) {
      errors.value = await get(`/imports/${batch.batchNo}/errors`)
      ElMessage.warning(`导入完成：成功 ${success} 行，失败 ${failed} 行，请查看错误明细`)
      errDialog.value = true
    } else {
      ElMessage.success(`导入完成：共 ${total} 行，全部成功`)
    }
    emit('imported', batch)
  } catch (err: any) {
    ElMessageBox.alert(err?.message || '导入失败，请检查文件格式与模板字段', '导入失败', { type: 'error' })
  } finally {
    importing.value = false
    if (input) input.value = ''
  }
}
</script>

<style scoped>
.import-btn-wrap { display: inline-flex; align-items: center; gap: 8px; margin-left: 8px; }
.tpl-link { margin-left: 4px; }
.hint { color: #64748b; font-size: 13px; margin-top: 0; }
</style>
