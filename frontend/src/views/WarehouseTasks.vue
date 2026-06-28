<template>
  <div class="card">
    <div class="toolbar">
      <div>
        <el-select v-model="status" clearable placeholder="状态筛选" style="width:160px" @change="load">
          <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
        </el-select>
        <el-button @click="load" style="margin-left:8px">刷新</el-button>
      </div>
      <div class="hint">标准流程：接单 → 拣料 → 拣完 → 配送 → 到达 → 完成。点击行前的箭头展开完整字段。</div>
    </div>
    <el-table :data="rows" border stripe height="680" class="compact-task-table" @expand-change="onExpandChange">
      <el-table-column type="expand">
        <template #default="{row}">
          <div class="task-detail">
            <div class="detail-item detail-full">
              <span>任务号</span>
              <b class="task-detail-no">
                {{ row.taskNo || '-' }}
                <el-button v-if="row.taskNo" link type="primary" size="small" :icon="CopyDocument" @click.stop="copyTaskNo(row.taskNo)">复制</el-button>
              </b>
            </div>
            <div class="detail-item"><span>来源标签</span><b>{{ row.sourceLabelCode || '-' }}</b></div>
            <div class="detail-item"><span>仓库代号</span><b>{{ row.warehouseCode || '-' }}</b></div>
            <div class="detail-item"><span>条码值</span><b>{{ row.barcodeValue || '-' }}</b></div>
            <div class="detail-item"><span>盒号</span><b>{{ row.boxCode || row.containerNo || '-' }}</b></div>
            <div class="detail-item"><span>周转容器</span><b>{{ row.containerNo || '-' }}</b></div>
            <div class="detail-item"><span>标签类型</span><b>{{ row.labelUsageType || '-' }}</b></div>
            <div class="detail-item"><span>配送方式</span><b>{{ row.deliveryMode || '-' }}</b></div>
            <div class="detail-item"><span>单盒序号</span><b>{{ row.boxSeq && row.boxTotal ? row.boxSeq + '/' + row.boxTotal : '-' }}</b></div>
            <div class="detail-item"><span>打印任务</span><b>{{ row.printJobNo || '-' }}</b></div>
            <div class="detail-item"><span>AGV任务</span><b>{{ row.agvJobNo || '-' }}</b></div>
            <div class="detail-item"><span>产线</span><b>{{ row.lineCode || '-' }}</b></div>
            <div class="detail-item"><span>工位</span><b>{{ row.stationName || row.stationCode || '-' }}</b></div>
            <div class="detail-item"><span>仓库料号</span><b>{{ row.warehouseMaterialCode || '-' }}</b></div>
            <div class="detail-item"><span>发送工位地址</span><b>{{ row.sendStationAddress || row.deliveryAddress || '-' }}</b></div>
            <div class="detail-item"><span>仓库地址</span><b>{{ row.warehouseAddress || row.warehouseLocation || '-' }}</b></div>
            <div class="detail-item"><span>盒子大小</span><b>{{ row.boxSize || '-' }}</b></div>
            <div class="detail-item"><span>送料人工号</span><b>{{ row.delivererEmployeeNo || '-' }}</b></div>
            <div class="detail-item"><span>接单人</span><b>{{ row.acceptedBy || '-' }}</b></div>
            <div class="detail-item"><span>拣料人</span><b>{{ row.picker || '-' }}</b></div>
            <div class="detail-item"><span>配送人</span><b>{{ row.deliverer || '-' }}</b></div>
            <div v-if="row.exceptionReason" class="detail-item detail-full"><span>异常原因</span><b>{{ row.exceptionReason }}</b></div>
            <div class="detail-item detail-full scan-trail-block">
              <span>扫码流水</span>
              <div class="scan-trail">
                <div v-if="scanTrailLoading[row.taskNo]" class="trail-empty">加载中…</div>
                <template v-else-if="(scanTrailMap[row.taskNo] || []).length">
                  <div v-for="t in scanTrailMap[row.taskNo]" :key="t.id" class="trail-row" :class="'trail-' + scanTrailType(t)">
                    <el-tag :type="scanTrailTag(t)" size="small" effect="dark">{{ scanTrailLabel(t.action) }}</el-tag>
                    <span class="trail-time">{{ fmtTime(t.scanAt) }}</span>
                    <span class="trail-text">{{ t.message || '-' }}</span>
                    <span class="trail-meta">{{ t.operator || '-' }} ｜ {{ t.deviceNo || '-' }}</span>
                  </div>
                </template>
                <div v-else class="trail-empty">该标签暂无扫码流水</div>
                <div class="trail-actions">
                  <el-button link type="primary" size="small" @click.stop="loadScanTrail(row, true)">刷新流水</el-button>
                </div>
              </div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="任务号" width="150">
        <template #default="{row}">
          <el-tooltip :content="row.taskNo" placement="top">
            <span class="task-no" @click.stop="copyTaskNo(row.taskNo)">
              <b>{{ shortTaskNo(row.taskNo) }}</b>
              <el-icon class="copy-ico"><CopyDocument /></el-icon>
            </span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="warehouseCode" label="仓库代号" width="120" />
      <el-table-column prop="sendStationAddress" label="发送工位地址" width="160" />
      <el-table-column prop="warehouseAddress" label="仓库地址" width="130" />
      <el-table-column prop="boxSize" label="盒子大小" width="90" />
      <el-table-column prop="stationCode" label="工位" width="100" />
      <el-table-column prop="materialCode" label="物料号" width="120" />
      <el-table-column prop="materialName" label="物料名称" width="130" />
      <el-table-column prop="requestQty" label="需求数量" width="100" />
      <el-table-column prop="priority" label="优先级" width="95"><template #default="{row}"><el-tag :type="tagType(row.priority)">{{row.priority}}</el-tag></template></el-table-column>
      <el-table-column prop="labelUsageType" label="标签" width="95" />
      <el-table-column prop="deliveryMode" label="配送" width="95" />
      <el-table-column prop="containerNo" label="周转容器" width="145" />
      <el-table-column prop="boxSeq" label="盒序" width="75"><template #default="{row}">{{row.boxSeq && row.boxTotal ? row.boxSeq + '/' + row.boxTotal : ''}}</template></el-table-column>
      <el-table-column prop="status" label="状态" width="120"><template #default="{row}"><el-tag :type="tagType(row.status)">{{row.status}}</el-tag></template></el-table-column>
      <el-table-column prop="deadlineAt" label="截止时间" width="180" />
      <el-table-column fixed="right" label="操作" width="450">
        <template #default="{row}">
          <el-button v-if="canPrint(row)" size="small" type="primary" plain @click="openPrint(row)">预览/打印标签</el-button>
          <el-button v-if="canDo(row,'accept')" size="small" @click="act(row,'accept')">接单</el-button>
          <el-button v-if="canDo(row,'startPick')" size="small" @click="act(row,'startPick')">开始拣料</el-button>
          <el-button v-if="canDo(row,'picked')" size="small" @click="act(row,'picked')">拣料完成</el-button>
          <el-button v-if="canDo(row,'deliver')" size="small" @click="act(row,'deliver')">配送</el-button>
          <el-button v-if="canDo(row,'arrive')" size="small" @click="act(row,'arrive')">到达</el-button>
          <el-button v-if="canDo(row,'complete')" size="small" type="success" @click="act(row,'complete')">完成</el-button>
          <el-button v-if="canDo(row,'receive')" size="small" type="success" plain @click="receive(row)">现场收货</el-button>
          <el-button v-if="canDo(row,'returnEmptyBox')" size="small" type="warning" plain @click="returnEmpty(row)">空盒回收</el-button>
          <el-button v-if="canDo(row,'exception')" size="small" type="danger" @click="exception(row)">异常</el-button>
          <el-button v-if="canDo(row,'retry')" size="small" type="warning" @click="act(row,'retry')">异常重试</el-button>
          <el-button v-if="canDo(row,'forceComplete')" size="small" type="danger" plain @click="forceComplete(row)">强制完成</el-button>
          <el-button v-if="canDelete(row)" size="small" type="danger" plain @click="deleteTask(row)">删除</el-button>
          <el-tag v-if="!hasAnyAction(row)" type="info">无可用操作</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="printDialog" title="仓库条形码标签预览" width="560px">
      <div v-if="printRow" class="warehouse-label-preview">
        <div class="label-barcode" v-html="barcodeSvg || ''"></div>
        <div class="label-line"><span>物料名称</span><b>{{ printRow.materialCode || printRow.materialName || '-' }}</b></div>
        <div class="label-line"><span>仓库地址</span><b>{{ printRow.warehouseAddress || printRow.warehouseLocation || ' ' }}</b></div>
        <div class="label-line"><span>发送工位地址</span><b>{{ printRow.sendStationAddress || printRow.deliveryAddress || printRow.stationName || printRow.stationCode || ' ' }}</b></div>
        <div class="label-two">
          <div><span>盒子大小</span><b>{{ printRow.boxSize || '-' }}</b></div>
          <div><span>数量</span><b>{{ printRow.requestQty || '-' }}</b></div>
        </div>
        <div class="label-line"><span>送料人工号</span><b>{{ printRow.delivererEmployeeNo || '' }}</b></div>
        <div class="label-foot">任务号：{{ printRow.taskNo }}</div>
      </div>
      <el-alert type="info" :closable="false" style="margin-top:12px" title="该标签由现场扫码申请自动生成，条形码内容为仓库代号；确认后会提交到已配置/输入的打印机。" />
      <el-form label-width="90px" style="margin-top:14px">
        <el-form-item label="打印机"><el-input v-model="printerName" placeholder="请输入已连接打印机名称" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="printDialog=false">取消</el-button>
        <el-button type="primary" @click="printTask">确认打印</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { get, post, del, tagType } from '../api'
import { loadBusinessMeta } from '../meta'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CopyDocument } from '@element-plus/icons-vue'
import { canTaskAction, TASK_TABLE_ACTIONS } from '../permissions'
import { runtimePrinterName, saveRuntimePrinterName } from '../config'
const rows = ref<any[]>([])
const status = ref('')
const statuses = ref<string[]>([])
const printDialog = ref(false)
const printRow = ref<any>(null)
const barcodeSvg = ref('')
const printerName = ref(runtimePrinterName())
const scanTrailMap = ref<Record<string, any[]>>({})
const scanTrailLoading = ref<Record<string, boolean>>({})
async function load(){ rows.value = await get('/tasks', status.value ? {status:status.value}:undefined) }
function shortTaskNo(no:string){
  if (!no) return '-'
  if (no.length <= 12) return no
  const prefix = no.split('-')[0] || ''
  const tail = no.slice(-6)
  return prefix ? `${prefix}…${tail}` : `…${tail}`
}
async function copyTaskNo(no:string){
  if (!no) return
  try {
    if (navigator?.clipboard?.writeText) await navigator.clipboard.writeText(no)
    else {
      const ta = document.createElement('textarea')
      ta.value = no
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    ElMessage.success(`已复制任务号 ${no}`)
  } catch {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}
function fmtTime(v:any){
  if (!v) return '-'
  try { return new Date(v).toLocaleString() } catch { return String(v) }
}
function scanTrailType(t:any){
  const a = String(t?.action || '').toUpperCase()
  if (a.includes('DUPLICATE')) return 'dup'
  if (a.includes('EXCEPTION')) return 'err'
  if (a.includes('RECEIVE')) return 'ok'
  if (a.includes('URGENT')) return 'urgent'
  if (t?.success === false) return 'err'
  return 'normal'
}
function scanTrailTag(t:any){
  switch (scanTrailType(t)) {
    case 'dup': return 'warning'
    case 'err': return 'danger'
    case 'ok': return 'success'
    case 'urgent': return 'danger'
    default: return 'primary'
  }
}
function scanTrailLabel(action:string){
  const map:Record<string,string> = {
    EMPTY: '用完拉动',
    EMPTY_DUPLICATE: '重复扫码（已拦截）',
    DIRECT_PULL: '直拉',
    DIRECT_PULL_DUPLICATE: '直拉重复（已拦截）',
    MATERIAL_PULL: '物料号拉动',
    MATERIAL_PULL_DUPLICATE: '物料号重复（已拦截）',
    MATERIAL_PULL_URGENT: '物料号紧急',
    SPARE_URGENT: '备用标签紧急',
    RECEIVE: '现场收货',
    SITE_EXCEPTION: '异常上报'
  }
  return map[action] || action || '-'
}
async function loadScanTrail(row:any, force=false){
  if (!row?.taskNo) return
  if (!force && scanTrailMap.value[row.taskNo]) return
  scanTrailLoading.value = { ...scanTrailLoading.value, [row.taskNo]: true }
  try {
    const list:any = await get('/logs/scans', { taskNo: row.taskNo })
    scanTrailMap.value = { ...scanTrailMap.value, [row.taskNo]: Array.isArray(list) ? list : [] }
  } catch {
    scanTrailMap.value = { ...scanTrailMap.value, [row.taskNo]: [] }
  } finally {
    scanTrailLoading.value = { ...scanTrailLoading.value, [row.taskNo]: false }
  }
}
function onExpandChange(row:any, expandedRows:any[]){
  const opened = (expandedRows || []).some((x:any) => x?.taskNo === row?.taskNo)
  if (opened) loadScanTrail(row, false)
}
function canDo(row:any, action:string){ return canTaskAction(action, row.status) }
function canPrint(row:any){ return ['CREATED','ACCEPTED','PICKING','PICKED'].includes(row.status) }
function canDelete(row:any){ return !['DELIVERING','ARRIVED'].includes(row.status) }
function hasAnyAction(row:any){ return canPrint(row) || canDelete(row) || TASK_TABLE_ACTIONS.some(a => canDo(row, a)) }
async function openPrint(row:any){
  printRow.value = row
  barcodeSvg.value = ''
  printDialog.value = true
  const code = row.warehouseCode || row.barcodeValue
  if (code) {
    try {
      const result:any = await post('/labels/code/render', { text: String(code), format: 'CODE_128', width: 620, height: 150, includeText: true })
      barcodeSvg.value = result.svg
    } catch (e:any) {
      ElMessage.error(e?.response?.data?.message || e?.message || '条形码生成失败')
    }
  } else {
    ElMessage.warning('当前任务缺少仓库代号，无法生成可扫码条形码')
  }
}
async function printTask(){
  if (!printRow.value) return
  if (!printerName.value.trim()) { ElMessage.warning('请填写已连接打印机名称'); return }
  saveRuntimePrinterName(printerName.value)
  await post('/print-jobs', { taskNo: printRow.value.taskNo, printerName: printerName.value.trim(), printType: 'WAREHOUSE_BARCODE_LABEL' })
  ElMessage.success('仓库标签已提交打印，条形码为仓库代号')
  printDialog.value=false
  load()
}
async function act(row:any, action:string){ await post(`/tasks/${row.taskNo}/${action}`, { expectedStatus: row.status }); ElMessage.success('操作成功'); load() }
async function deleteTask(row:any){
  await ElMessageBox.confirm(`确认删除任务 ${row.taskNo}？删除会释放库存锁定、取消打印/AGV并移除任务记录。`, '删除任务确认', { type:'warning', confirmButtonText:'继续删除', cancelButtonText:'取消' })
  const result = await ElMessageBox.prompt(`请再次输入任务号 ${row.taskNo} 以确认删除`, '二次确认', { confirmButtonText:'确认删除', cancelButtonText:'取消', inputPattern:new RegExp(`^${row.taskNo.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`), inputErrorMessage:'输入的任务号不一致' })
  if (result.value !== row.taskNo) return
  await del(`/tasks/${encodeURIComponent(row.taskNo)}`)
  ElMessage.success('任务已删除')
  load()
}
async function exception(row:any){ const reason = await ElMessageBox.prompt('请输入异常原因','异常处理'); await post(`/tasks/${row.taskNo}/exception`, { exceptionReason:reason.value, expectedStatus: row.status }); load() }
async function forceComplete(row:any){ await ElMessageBox.confirm('强制完成会跳过部分状态校验，只建议在现场已确认补料完成时使用。确认继续？','强制完成确认'); await post(`/tasks/${row.taskNo}/forceComplete`, { expectedStatus: row.status, force:true }); load() }
async function receive(row:any){ const empty = await ElMessageBox.prompt('现场收货确认，可填写空盒编号','收货确认'); await post(`/tasks/${row.taskNo}/receive`, { receiveScanCode:row.taskNo, emptyContainerNo:empty.value, expectedStatus:row.status }); load() }
async function returnEmpty(row:any){ const empty = await ElMessageBox.prompt('请输入空盒编号','空盒回收'); await post(`/tasks/${row.taskNo}/returnEmptyBox`, { emptyContainerNo:empty.value, expectedStatus:row.status }); load() }
onMounted(async()=>{ const meta = await loadBusinessMeta(); statuses.value = meta.taskStatuses.map(x => x.value); load() })
</script>

<style scoped>
.warehouse-label-preview{width:310px;height:380px;border:2px solid #111;background:#fff;color:#111;margin:0 auto;font-family:Arial,'Microsoft YaHei',sans-serif;box-sizing:border-box;display:grid;grid-template-rows:86px 54px 54px 54px 54px 54px 20px}.label-barcode{border-bottom:2px solid #111;display:flex;align-items:center;justify-content:center;padding:6px 8px;overflow:hidden}.label-barcode :deep(svg){width:100%;height:74px;display:block}.label-line{display:grid;grid-template-columns:118px 1fr;border-bottom:2px solid #111;min-height:0}.label-line span{display:flex;align-items:center;justify-content:center;border-right:2px solid #111;font-size:20px;font-weight:600}.label-line b{display:flex;align-items:center;justify-content:center;text-align:center;font-size:18px;font-weight:700;word-break:break-all;padding:0 8px}.label-two{display:grid;grid-template-columns:1fr 1fr;border-bottom:2px solid #111}.label-two>div{display:grid;grid-template-columns:1fr 1fr;min-height:0}.label-two>div:first-child{border-right:2px solid #111}.label-two span{display:flex;align-items:center;justify-content:center;border-right:2px solid #111;font-size:18px;font-weight:600}.label-two b{display:flex;align-items:center;justify-content:center;text-align:center;font-size:18px;font-weight:800;padding:0 6px;word-break:break-all}.label-foot{font-size:10px;color:#555;display:flex;align-items:center;justify-content:center;overflow:hidden;white-space:nowrap}

.task-detail{
  position: sticky;
  left: 0;
  width: calc(100vw - 286px);
  max-width: 100%;
  box-sizing: border-box;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 8px 12px;
  padding: 12px 14px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}
.task-detail .detail-item{
  display: grid;
  grid-template-columns: 96px 1fr;
  align-items: center;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  overflow: hidden;
  min-height: 32px;
  font-size: 12.5px;
}
.task-detail .detail-item span{
  background: #f1f5f9;
  color: #475569;
  padding: 6px 8px;
  font-weight: 600;
  border-right: 1px solid #e2e8f0;
  height: 100%;
  display: flex;
  align-items: center;
  white-space: nowrap;
}
.task-detail .detail-item b{
  padding: 6px 10px;
  color: #0f172a;
  font-weight: 600;
  word-break: break-all;
}
.task-detail .detail-full{ grid-column: 1 / -1; }
.task-detail .detail-full span{ background: #fef3c7; color: #92400e; }

@media (max-width: 1180px){
  .task-detail{ grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); }
  .task-detail .detail-item{ grid-template-columns: 88px 1fr; font-size: 12px; }
}

.task-no{
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #1d4ed8;
  font-weight: 600;
  font-family: 'JetBrains Mono','Consolas','Menlo',monospace;
  letter-spacing: .3px;
}
.task-no:hover{ text-decoration: underline; }
.task-no .copy-ico{ font-size: 13px; opacity: .55; }
.task-no:hover .copy-ico{ opacity: 1; }
.task-detail-no{
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: 'JetBrains Mono','Consolas','Menlo',monospace;
  font-weight: 700;
  color: #0f172a;
  word-break: break-all;
}

.scan-trail-block{
  grid-template-columns: 96px 1fr !important;
  align-items: stretch;
}
.scan-trail-block > span{ background:#eef2ff !important; color:#3730a3 !important; }
.scan-trail{
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 10px;
  max-height: 220px;
  overflow-y: auto;
}
.scan-trail .trail-row{
  display: grid;
  grid-template-columns: 110px 150px 1fr auto;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  border-radius: 6px;
  background: #fff;
  border: 1px solid #e2e8f0;
  font-size: 12.5px;
}
.scan-trail .trail-time{ color:#475569; font-variant-numeric: tabular-nums; }
.scan-trail .trail-text{ color:#0f172a; word-break: break-all; }
.scan-trail .trail-meta{ color:#64748b; font-size: 12px; white-space: nowrap; }
.scan-trail .trail-dup{ background:#fffbeb; border-color:#fde68a; }
.scan-trail .trail-err{ background:#fef2f2; border-color:#fecaca; }
.scan-trail .trail-ok{ background:#ecfdf5; border-color:#bbf7d0; }
.scan-trail .trail-urgent{ background:#fff7ed; border-color:#fed7aa; }
.scan-trail .trail-empty{ color:#94a3b8; padding: 6px 8px; font-size: 12.5px; }
.scan-trail .trail-actions{ text-align:right; padding-top: 2px; }

@media (max-width: 1180px){
  .scan-trail .trail-row{ grid-template-columns: 96px 130px 1fr; }
  .scan-trail .trail-row .trail-meta{ display:none; }
}
</style>
