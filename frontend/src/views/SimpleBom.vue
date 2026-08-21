<template>
  <div class="card">
    <div class="toolbar">
      <div class="left">
        <el-tag v-if="activeBatch" type="success" effect="dark">当前有效批次：{{ activeBatch.batchNo }}（{{ activeBatch.successRows }} 行）</el-tag>
        <el-tag v-else type="info">当前无有效 BOM 批次</el-tag>
        <el-input v-model="materialCode" clearable placeholder="按物料8D号过滤" style="width:200px;margin-left:12px" @keyup.enter="loadRows" />
        <el-button style="margin-left:8px" @click="loadRows">查询</el-button>
        <el-button v-if="canWrite" type="primary" style="margin-left:8px" @click="openEdit(null)">新增</el-button>
      </div>
      <div class="right" v-if="canWrite">
        <el-upload
          :show-file-list="false"
          :before-upload="onPickDelete"
          accept=".csv,.xlsx,.xls"
          style="display:inline-block"
        >
          <el-button type="warning" plain :loading="deleting">上传删除</el-button>
        </el-upload>
        <el-button type="danger" plain style="margin-left:8px" @click="confirmDeleteAll">全部删除</el-button>
        <el-upload
          :show-file-list="false"
          :before-upload="onPick"
          accept=".csv"
          style="display:inline-block;margin-left:8px"
        >
          <el-button type="success" :icon="UploadFilled" :loading="importing">{{ importing ? '导入中…' : 'CSV 高速导入' }}</el-button>
        </el-upload>
        <el-checkbox v-model="activateNow" style="margin-left:10px">导入后立即启用</el-checkbox>
        <el-link type="primary" href="/import_templates/simple_bom.csv" target="_blank" download style="margin-left:10px">下载模板</el-link>
      </div>
    </div>
    <el-alert v-if="!canWrite" type="info" :closable="false" show-icon title="当前账号仅有查看权限，导入与批次切换已隐藏。" style="margin-bottom:12px" />

    <div class="section-title">BOM 明细（当前有效批次）</div>
    <el-table :data="rows" border stripe height="420">
      <el-table-column prop="materialCode" label="物料8D号" width="200" />
      <el-table-column prop="componentCode" label="组件8D号" width="200" />
      <el-table-column prop="createdAt" label="写入时间" />
      <el-table-column v-if="canWrite" label="操作" width="140" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="removeRow(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination
        layout="total, prev, pager, next"
        :total="rowsTotal"
        :page-size="size"
        :current-page="page + 1"
        @current-change="onPageChange"
      />
    </div>

    <div class="section-title">导入批次</div>
    <el-table :data="batches" border stripe height="360">
      <el-table-column prop="batchNo" label="批次号" width="220" />
      <el-table-column prop="fileName" label="文件名" show-overflow-tooltip />
      <el-table-column label="状态" width="110">
        <template #default="{row}"><el-tag :type="tagType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="totalRows" label="总行" width="90" />
      <el-table-column prop="successRows" label="成功" width="90" />
      <el-table-column prop="failedRows" label="失败" width="90" />
      <el-table-column prop="operator" label="操作人" width="120" />
      <el-table-column prop="finishedAt" label="完成时间" width="180" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{row}">
          <el-button v-if="canWrite && row.status!=='ACTIVE' && row.status!=='RUNNING' && row.status!=='FAILED'" link type="primary" @click="activate(row.batchNo)">启用</el-button>
          <el-button v-if="row.failedRows>0" link type="warning" @click="showErrors(row.batchNo)">错误明细</el-button>
          <el-button v-if="canWrite && row.status!=='ACTIVE'" link type="danger" @click="removeBatch(row.batchNo)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editDialog" :title="form.id ? '编辑 BOM 行' : '新增 BOM 行'" width="460px">
      <el-form label-width="96px">
        <el-form-item label="物料8D号">
          <el-input v-model="form.materialCode" maxlength="8" placeholder="8位纯数字" />
        </el-form-item>
        <el-form-item label="组件8D号">
          <el-input v-model="form.componentCode" maxlength="8" placeholder="8位纯数字" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog=false">取消</el-button>
        <el-button type="primary" @click="saveRow">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="errDialog" :title="`导入错误明细 · ${currentErrBatch}`" width="900px">
      <el-table :data="errors" border height="420">
        <el-table-column prop="rowNo" label="行号" width="90" />
        <el-table-column prop="rawData" label="原始数据" />
        <el-table-column prop="errorMessage" label="错误信息" width="260" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { get, post, del, UPLOAD_TIMEOUT_MS, tagType } from '../api'
import { hasAnyRole } from '../auth'
import { ROLE_SETS } from '../permissions'

const canWrite = computed(() => hasAnyRole(ROLE_SETS.planner as any))
const importing = ref(false)
const deleting = ref(false)
const activateNow = ref(true)
const materialCode = ref('')
const activeBatch = ref<any>(null)
const rows = ref<any[]>([])
const rowsTotal = ref(0)
const page = ref(0)
const size = ref(50)
const batches = ref<any[]>([])
const errDialog = ref(false)
const errors = ref<any[]>([])
const currentErrBatch = ref('')
const editDialog = ref(false)
const form = ref<any>({ id: null, materialCode: '', componentCode: '' })

async function loadActive(){ activeBatch.value = await get('/simple-bom/active') }
async function loadBatches(){ batches.value = await get('/simple-bom/batches') }
async function loadRows(){
  const res:any = await get('/simple-bom/rows', { materialCode: materialCode.value || undefined, page: page.value, size: size.value })
  rows.value = res.rows || []
  rowsTotal.value = res.total || 0
}
async function reloadAll(){ await Promise.all([loadActive(), loadBatches()]); await loadRows() }

async function onPick(file:File){
  try {
    importing.value = true
    const fd = new FormData()
    fd.append('file', file)
    fd.append('activate', String(activateNow.value))
    const batch:any = await post('/simple-bom/import', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: UPLOAD_TIMEOUT_MS
    })
    const failed = batch?.failedRows ?? 0
    const success = batch?.successRows ?? 0
    if (failed > 0) {
      ElMessage.warning(`导入完成：成功 ${success} 行，失败 ${failed} 行，请查看错误明细`)
    } else {
      ElMessage.success(`导入完成：成功 ${success} 行${activateNow.value ? '，已启用为当前有效批次' : '，待启用'}`)
    }
    await reloadAll()
  } catch { /* 拦截器已提示 */ } finally {
    importing.value = false
  }
  return false
}

async function activate(batchNo:string){
  await ElMessageBox.confirm(`确认将批次 ${batchNo} 切换为当前有效 BOM？原有效批次会被归档，可再切回。`, '切换有效批次')
  await post(`/simple-bom/batches/${batchNo}/activate`)
  ElMessage.success('已切换')
  await reloadAll()
}
async function removeBatch(batchNo:string){
  await ElMessageBox.confirm(`确认删除批次 ${batchNo} 及其全部 BOM 明细？该操作不可恢复。`, '删除批次', { type:'warning' })
  await del(`/simple-bom/batches/${batchNo}`)
  ElMessage.success('已删除')
  await reloadAll()
}
async function showErrors(batchNo:string){
  currentErrBatch.value = batchNo
  errors.value = await get(`/simple-bom/batches/${batchNo}/errors`)
  errDialog.value = true
}
function onPageChange(p:number){ page.value = p - 1; loadRows() }

function openEdit(row:any){
  if (!activeBatch.value) { ElMessage.warning('当前无有效 BOM 批次，请先导入并启用一个批次'); return }
  form.value = row ? { id: row.id, materialCode: row.materialCode, componentCode: row.componentCode }
                   : { id: null, materialCode: '', componentCode: '' }
  editDialog.value = true
}
async function saveRow(){
  await post('/simple-bom/rows', form.value)
  editDialog.value = false
  ElMessage.success('已保存')
  await reloadAll()
}
async function removeRow(row:any){
  await ElMessageBox.confirm(`确认删除 ${row.materialCode} → ${row.componentCode} 这条 BOM？`, '删除', { type:'warning' })
  await del(`/simple-bom/rows/${row.id}`)
  ElMessage.success('已删除')
  await reloadAll()
}

async function confirmDeleteAll(){
  if (!activeBatch.value) { ElMessage.warning('当前无有效 BOM 批次，无数据可删除'); return }
  try {
    await ElMessageBox.confirm(
      `即将删除当前有效批次【${activeBatch.value.batchNo}】的全部 ${activeBatch.value.successRows} 条 BOM 数据，该操作不可恢复！`,
      '危险操作：全部删除',
      { type: 'error', confirmButtonText: '我再想想', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  let countdown = 3
  const h = ElMessageBox
  const confirmDelete = () => {
    h.close()
    executeDeleteAll()
  }
  const updateContent = () => {
    if (countdown > 0) {
      h.alert(`请再次确认：将清空当前有效批次的全部 BOM 数据。\n\n${countdown} 秒后可以确认删除...`, '二次确认', {
        showClose: false,
        showConfirmButton: false,
        showCancelButton: true,
        cancelButtonText: '取消',
        type: 'warning'
      })
      countdown--
      setTimeout(updateContent, 1000)
    } else {
      h.close()
      h.confirm('最后确认：确定要删除全部 BOM 数据？', '最终确认', {
        type: 'error',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消'
      }).then(confirmDelete).catch(() => {})
    }
  }
  updateContent()
}

async function executeDeleteAll(){
  try {
    const res:any = await del('/simple-bom/rows')
    ElMessage.success(`已删除 ${res?.deleted ?? 0} 条 BOM 数据`)
    await reloadAll()
  } catch { /* 拦截器已提示 */ }
}

async function onPickDelete(file:File){
  if (!activeBatch.value) { ElMessage.warning('当前无有效 BOM 批次，无数据可删除'); return false }
  try {
    await ElMessageBox.confirm(
      `即将上传文件进行批量删除，系统会将文件中每一行的"物料8D号+组件8D号"与当前有效批次精确匹配，找到的全部删除。该操作不可恢复，确认继续？`,
      '批量删除确认',
      { type: 'warning' }
    )
  } catch {
    return false
  }
  try {
    deleting.value = true
    const fd = new FormData()
    fd.append('file', file)
    const res:any = await post('/simple-bom/delete-by-file', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: UPLOAD_TIMEOUT_MS
    })
    const deleted = res?.deleted ?? 0
    const notFound = res?.notFound ?? 0
    const failed = res?.failed ?? 0
    if (deleted > 0) {
      ElMessage.success(`批量删除完成：成功删除 ${deleted} 条${notFound > 0 ? `，未找到 ${notFound} 条` : ''}${failed > 0 ? `，解析失败 ${failed} 条` : ''}`)
    } else {
      ElMessage.warning(`未删除任何数据：未找到 ${notFound} 条，解析失败 ${failed} 条`)
    }
    await reloadAll()
  } catch { /* 拦截器已提示 */ } finally {
    deleting.value = false
  }
  return false
}

function statusLabel(s:string){
  return ({ RUNNING:'导入中', READY:'待启用', ACTIVE:'当前有效', ARCHIVED:'已归档', FAILED:'失败' } as Record<string,string>)[s] || s
}

onMounted(reloadAll)
</script>

<style scoped>
.toolbar { display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; flex-wrap:wrap; gap:8px }
.toolbar .left { display:flex; align-items:center; flex-wrap:wrap }
.toolbar .right { display:flex; align-items:center }
.pager { margin-top:12px; display:flex; justify-content:flex-end }
.section-title { font-weight:600; margin:18px 0 10px }
</style>
