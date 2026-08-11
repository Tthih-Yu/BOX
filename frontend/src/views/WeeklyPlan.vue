<template>
  <div class="card">
    <div class="toolbar">
      <div class="left">
        <span>上传年份</span>
        <el-input-number v-model="planYear" :min="2000" :max="2100" :controls="false" style="width:110px;margin:0 12px" />
        <span>计算日期</span>
        <el-date-picker v-model="calcDate" type="date" value-format="YYYY-MM-DD" placeholder="选择某一天"
                        style="width:150px;margin:0 12px" />
        <el-button :loading="calcing" @click="preview">计算预览</el-button>
        <el-button type="warning" :loading="calcing" @click="apply">刷新当天用量</el-button>
      </div>
      <div class="right">
        <el-button :icon="Download" @click="goTemplate">下载模板</el-button>
        <el-upload v-if="canWrite" :show-file-list="false" :before-upload="onPick" accept=".xlsx,.xlsm,.xls" style="margin-left:8px">
          <el-button type="success" :icon="UploadFilled" :loading="importing">上传周计划</el-button>
        </el-upload>
      </div>
    </div>
    <el-alert v-if="!canWrite" type="info" :closable="false" show-icon title="当前账号仅有查看权限，上传与计算已隐藏。" style="margin-bottom:12px" />

    <div class="section-title">上传批次</div>
    <el-table :data="batches" border stripe height="440">
      <el-table-column prop="batchNo" label="批次号" width="200" />
      <el-table-column prop="planYear" label="年份" width="80" />
      <el-table-column prop="weekNo" label="周次" width="70" />
      <el-table-column prop="excelVersion" label="Excel版本" width="90" />
      <el-table-column prop="factory" label="生产工厂" width="100" />
      <el-table-column prop="fileName" label="文件名" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{row}"><el-tag :type="tagType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="successRows" label="行数" width="80" />
      <el-table-column prop="operator" label="上传人" width="110" />
      <el-table-column prop="finishedAt" label="完成时间" width="170" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="viewRows(row.batchNo)">明细</el-button>
          <el-button v-if="canWrite" link type="danger" @click="removeBatch(row.batchNo)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="conflictDialog" title="计划冲突确认" width="760px">
      <p>检测到以下计划与已有记录冲突（同年份+周次+8D号）。确认覆盖将删除旧计划行并写入本次上传。</p>
      <el-table :data="conflicts" border height="360">
        <el-table-column prop="productCode" label="8D号" width="120" />
        <el-table-column prop="existingBatchNo" label="已有批次" />
        <el-table-column prop="existingVersion" label="库内版本" width="90" />
        <el-table-column prop="existingWeekQty" label="原周数量" width="110" />
        <el-table-column prop="factory" label="工厂" width="90" />
        <el-table-column prop="project" label="项目" width="90" />
      </el-table>
      <template #footer>
        <el-button @click="conflictDialog=false">取消</el-button>
        <el-button type="warning" @click="confirmOverwrite">确认覆盖</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rowsDialog" title="周计划明细" width="900px">
      <el-table :data="rows" border height="440">
        <el-table-column prop="productCode" label="8D号" width="110" />
        <el-table-column prop="customer" label="客户" width="90" />
        <el-table-column prop="factory" label="工厂" width="90" />
        <el-table-column prop="project" label="项目" width="90" />
        <el-table-column prop="customerNo" label="客户号" width="130" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="packageQty" label="标包" width="80" />
        <el-table-column prop="weekQty" label="周数量" width="100" />
      </el-table>
      <template #footer><span>共 {{ rowsTotal }} 行</span></template>
    </el-dialog>

    <el-dialog v-model="calcDialog" :title="calcResult && calcResult.applied ? '已刷新当天用量' : '当天用量预览'" width="860px">
      <template v-if="calcResult">
        <p>
          {{ calcResult.planDate }} 当天：物料 {{ calcResult.materialCount }} 个｜组件 {{ calcResult.componentCount }} 个｜
          影响料号映射 {{ calcResult.affectedMappings }} 条｜
          <b :style="{color: calcResult.applied ? '#16a34a' : '#d97706'}">{{ calcResult.applied ? '已写入数量' : '仅预览，未写入' }}</b>
        </p>
        <el-alert v-if="calcResult.skippedNoUsage > 0" type="info" :closable="false" show-icon
                  :title="`有 ${calcResult.skippedNoUsage} 条映射未维护单根用量，已跳过不参与计算，其数量保持原值不动。`"
                  style="margin-bottom:10px" />
        <div v-if="(calcResult.warnings||[]).length">
          <div class="section-title">提示（不影响其余条目落库）</div>
          <div class="err-list"><div v-for="(e,i) in calcResult.warnings" :key="i">· {{ e }}</div></div>
        </div>
        <div v-if="(calcResult.preview||[]).length">
          <div class="section-title">{{ calcResult.applied ? '已写入的数量' : '将写入的数量' }}</div>
          <el-table :data="calcResult.preview" border height="360">
            <el-table-column prop="lineMaterialCode" label="组件(料号)" width="130" />
            <el-table-column prop="warehouseCode" label="仓库代号" width="120" />
            <el-table-column prop="mappingOrder" label="映射序号" width="90" />
            <el-table-column prop="singleUnitUsage" label="单根用量" width="100" />
            <el-table-column prop="oldQuantity" label="原数量" width="110" />
            <el-table-column prop="newQuantity" label="新数量" />
          </el-table>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, Download } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { get, post, del, UPLOAD_TIMEOUT_MS, tagType } from '../api'
import { hasAnyRole } from '../auth'
import { ROLE_SETS } from '../permissions'

const router = useRouter()
const canWrite = computed(() => hasAnyRole(ROLE_SETS.planner as any))
const planYear = ref(new Date().getFullYear())
const calcDate = ref(new Date().toISOString().slice(0, 10))
const importing = ref(false)
const calcing = ref(false)
const batches = ref<any[]>([])
const pendingFile = ref<File | null>(null)
const conflictDialog = ref(false)
const conflicts = ref<any[]>([])
const rowsDialog = ref(false)
const rows = ref<any[]>([])
const rowsTotal = ref(0)
const calcDialog = ref(false)
const calcResult = ref<any>(null)

async function loadBatches(){ batches.value = await get('/weekly-plan/batches') }

function goTemplate(){ router.push('/weekly-plan-template') }

function onPick(file:File){
  pendingFile.value = file
  doUpload(false)
  return false
}

async function doUpload(confirm:boolean){
  const file = pendingFile.value
  if (!file) return
  try {
    importing.value = true
    const fd = new FormData()
    fd.append('file', file)
    fd.append('planYear', String(planYear.value))
    fd.append('confirm', String(confirm))
    const res:any = await post('/weekly-plan/import', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: UPLOAD_TIMEOUT_MS
    })
    if (res?.needConfirm) {
      conflicts.value = res.conflicts || []
      conflictDialog.value = true
      return
    }
    ElMessage.success(`上传成功：第 ${res.weekNo} 周，共 ${res.parsedRows} 行${res.excelVersion ? '（Excel ' + res.excelVersion + '）' : ''}`)
    pendingFile.value = null
    await loadBatches()
  } catch { /* 拦截器已提示 */ } finally {
    importing.value = false
  }
}

async function confirmOverwrite(){
  conflictDialog.value = false
  await doUpload(true)
}

async function removeBatch(batchNo:string){
  await ElMessageBox.confirm(`确认删除批次 ${batchNo} 及其计划行？`, '删除批次', { type:'warning' })
  await del(`/weekly-plan/batches/${batchNo}`)
  ElMessage.success('已删除')
  await loadBatches()
}

async function viewRows(batchNo:string){
  const res:any = await get('/weekly-plan/rows', { batchNo, page: 0, size: 200 })
  rows.value = res.rows || []
  rowsTotal.value = res.total || 0
  rowsDialog.value = true
}

async function preview(){ await runCalc(false) }
async function apply(){
  if (!calcDate.value) { ElMessage.warning('请先选择计算日期'); return }
  await ElMessageBox.confirm(`确认按 ${calcDate.value} 当天的计划用量刷新料号映射数量？未维护单根用量的条目会保持原值不动。`, '刷新当天用量', { type:'warning' })
  await runCalc(true)
}
async function runCalc(applyFlag:boolean){
  if (!calcDate.value) { ElMessage.warning('请先选择计算日期'); return }
  try {
    calcing.value = true
    const res:any = await post('/weekly-plan/calculate', null, { params: { planDate: calcDate.value, apply: applyFlag } })
    calcResult.value = res
    calcDialog.value = true
    if (res?.applied) ElMessage.success(`已刷新 ${res.affectedMappings} 条料号映射数量`)
    else if ((res?.warnings||[]).length > 0) ElMessage.warning(`有 ${res.warnings.length} 项提示，请查看明细`)
  } catch { /* 拦截器已提示 */ } finally {
    calcing.value = false
  }
}
function statusLabel(s:string){
  return ({ RUNNING:'处理中', SUCCESS:'成功', PARTIAL_SUCCESS:'部分成功', FAILED:'失败' } as Record<string,string>)[s] || s
}

onMounted(loadBatches)
</script>

<style scoped>
.toolbar { display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; flex-wrap:wrap; gap:8px }
.toolbar .left { display:flex; align-items:center; flex-wrap:wrap }
.section-title { font-weight:600; margin:18px 0 10px }
.err-list { max-height:300px; overflow:auto; color:#b91c1c }
</style>
