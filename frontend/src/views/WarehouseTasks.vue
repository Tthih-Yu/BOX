<template>
  <div class="card">
    <div class="toolbar">
      <div>
        <div class="status-menu">
          <span class="status-menu-label">状态：</span>
          <el-button-group>
            <el-button size="small" :type="status===''?'primary':''" @click="selectStatus('')">进行中</el-button>
            <el-button size="small" :type="status==='ALL'?'primary':''" @click="selectStatus('ALL')">全部</el-button>
            <el-button v-for="s in primaryStatuses" :key="s.value" size="small" :type="status===s.value?'primary':''" @click="selectStatus(s.value)">{{ s.label }}</el-button>
            <el-dropdown v-if="moreStatuses.length" trigger="click" @command="selectStatus">
              <el-button size="small" :type="isMoreStatusActive?'primary':''">
                {{ isMoreStatusActive ? activeMoreStatusLabel : '更多状态' }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="s in moreStatuses" :key="s.value" :command="s.value" :class="{ 'is-active-status': status===s.value }">{{ s.label }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </el-button-group>
        </div>
        <div class="status-menu">
          <span class="status-menu-label">打印：</span>
          <el-button-group>
            <el-button size="small" :type="printFilter===''?'primary':''" @click="printFilter=''">全部</el-button>
            <el-button size="small" :type="printFilter==='UNPRINTED'?'warning':''" @click="printFilter='UNPRINTED'">未打印 <el-tag v-if="printFilter==='UNPRINTED'" size="small" effect="dark" type="warning" round>{{ printCounts.unprinted }}</el-tag><span v-else class="pf-count">({{ printCounts.unprinted }})</span></el-button>
            <el-button size="small" :type="printFilter==='PRINTED'?'success':''" @click="printFilter='PRINTED'">已打印 <span class="pf-count">({{ printCounts.printed }})</span></el-button>
          </el-button-group>
        </div>
        <el-input v-model="keyword" clearable placeholder="搜索：任务号/物料/工位/仓库/数量等全部字段" style="width:300px;margin-left:8px"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" placeholder="按申请日期筛选" clearable style="width:180px;margin-left:8px" @change="load" />
        <el-button @click="load" style="margin-left:8px">刷新</el-button>
        <el-select v-model="autoRefreshSec" style="width:150px;margin-left:8px" @change="onAutoRefreshChange">
          <el-option :value="0" label="自动刷新：关闭" />
          <el-option :value="10" label="每 10 秒" />
          <el-option :value="30" label="每 30 秒" />
          <el-option :value="60" label="每 1 分钟" />
          <el-option :value="300" label="每 5 分钟" />
        </el-select>
        <span v-if="lastRefreshText" class="refresh-tip">{{ lastRefreshText }}</span>
        <span class="sort-label">排序：</span>
        <el-button-group>
          <el-button size="small" :type="sortState===null?'primary':''" @click="resetSort">默认（配送区域→仓库地址）</el-button>
          <el-button size="small" :type="sortState?.prop==='deliveryArea'?'primary':''" @click="sortBy('deliveryArea')">配送区域 {{ sortArrow('deliveryArea') }}</el-button>
          <el-button size="small" :type="sortState?.prop==='warehouseAddress'?'primary':''" @click="sortBy('warehouseAddress')">仓库地址 {{ sortArrow('warehouseAddress') }}</el-button>
          <el-button size="small" :type="sortState?.prop==='createdAt'?'primary':''" @click="sortByTime">申请时间 {{ sortArrow('createdAt') }}</el-button>
        </el-button-group>
        <el-button v-if="canManagePrint" type="warning" plain style="margin-left:12px" @click="openPrintSettings">定时打印设置</el-button>
      </div>
      <div class="hint">标准流程：接单 → 拣料 → 拣完 → 配送 → 到达 → 完成。点击行前的箭头展开完整字段。同一仓库代号在去重时间窗内重复扫码会被拦截（防手抖/重发）；时间窗可在「系统配置」的 task.dedup.window-minutes 修改，超过该时间再次用空可正常生成新任务。</div>
    </div>
    <div class="warehouse-task-scope">
      <div class="task-scope-switcher">
        <div class="task-scope-track factory-track">
          <span class="task-scope-label">工厂：</span>
          <button v-for="factory in availableFactories" :key="factory.key" type="button" class="task-scope-chip" :class="{ active:selectedFactoryKey===factory.key }" @click="selectFactory(factory.key)">{{ factory.label }} {{ factory.count }}</button>
        </div>
        <div class="task-scope-track area-track">
          <span class="task-scope-label">区域：</span>
          <button v-for="area in availableAreas" :key="area.key" type="button" class="task-scope-chip area-chip" :class="{ active:selectedAreaKey===area.key, 'has-tasks':area.key!==ALL_AREAS && area.count>0 }" @click="selectedAreaKey=area.key">{{ area.label }}<template v-if="area.key!==ALL_AREAS"> {{ area.count }}</template></button>
        </div>
      </div>
      <div class="task-scope-current"><b>当前：{{ selectedFactoryLabel }} / {{ selectedAreaLabel }}</b><span>{{ currentRows.length }} 条任务</span></div>
      <el-table :data="currentRows" border stripe height="680" class="compact-task-table" @expand-change="onExpandChange" @sort-change="onSortChange">
      <el-table-column type="expand">
        <template #default="{row}">
          <div class="task-detail">
            <div class="td-header">
              <div class="td-header-left">
                <span class="td-header-label">任务号</span>
                <span class="td-header-no">{{ row.taskNo || '-' }}</span>
                <el-button v-if="row.taskNo" link type="primary" size="small" :icon="CopyDocument" @click.stop="copyTaskNo(row.taskNo)">复制</el-button>
              </div>
              <div class="td-header-right">
                <el-tag :type="tagType(row.status)" effect="dark" size="small">{{ statusCn(row.status) }}</el-tag>
                <el-tag v-if="row.printStatus" :type="printTagType(row.printStatus)" effect="plain" size="small">{{ printStatusCn(row.printStatus, row.printChannel) }}</el-tag>
                <el-tag v-else-if="row.printGenerated || row.printJobNo" type="info" effect="plain" size="small">已生成打印作业</el-tag>
                <el-tag v-if="deliveryState(row)==='urgent'" type="danger" effect="plain" size="small">紧急</el-tag>
                <el-tag v-else-if="deliveryState(row)==='overdue'" type="warning" effect="plain" size="small">超时</el-tag>
              </div>
            </div>

            <div v-if="row.exceptionReason" class="td-exception">
              <el-icon><WarningFilled /></el-icon>
              <span>{{ row.exceptionReason }}</span>
            </div>

            <div class="td-sections">
              <section class="td-card">
                <h4 class="td-card-title">基本信息</h4>
                <div class="td-grid">
                  <div v-if="row.warehouseMaterialCode" class="td-field"><span>仓库料号</span><b>{{ row.warehouseMaterialCode }}</b></div>
                  <div v-if="row.sourceLabelCode" class="td-field"><span>来源标签</span><b>{{ row.sourceLabelCode }}</b></div>
                  <div v-if="row.barcodeValue" class="td-field"><span>条码值</span><b>{{ row.barcodeValue }}</b></div>
                  <div v-if="row.labelUsageType" class="td-field"><span>标签类型</span><b>{{ labelUsageCn(row.labelUsageType) }}</b></div>
                  <div v-if="row.boxSize" class="td-field"><span>盒子大小</span><b>{{ row.boxSize }}</b></div>
                </div>
              </section>

              <section class="td-card">
                <h4 class="td-card-title">物流信息</h4>
                <div class="td-grid">
                  <div v-if="row.sendStationAddress || row.deliveryAddress" class="td-field"><span>发送工位地址</span><b>{{ row.sendStationAddress || row.deliveryAddress }}</b></div>
                  <div v-if="row.deliveryArea" class="td-field"><span>配送区域</span><b>{{ row.deliveryArea }}</b></div>
                  <div v-if="row.warehouseAddress || row.warehouseLocation" class="td-field"><span>仓库地址</span><b>{{ row.warehouseAddress || row.warehouseLocation }}</b></div>
                  <div v-if="row.stationName || row.stationCode" class="td-field"><span>工位</span><b>{{ row.stationName || row.stationCode }}</b></div>
                  <div v-if="row.lineCode" class="td-field"><span>产线</span><b>{{ row.lineCode }}</b></div>
                  <div v-if="row.deliveryMode" class="td-field"><span>配送方式</span><b>{{ row.deliveryMode }}</b></div>
                  <div v-if="row.boxCode || row.containerNo" class="td-field"><span>盒号</span><b>{{ row.boxCode || row.containerNo }}</b></div>
                  <div v-if="row.containerNo" class="td-field"><span>周转容器</span><b>{{ row.containerNo }}</b></div>
                  <div v-if="row.boxSeq && row.boxTotal" class="td-field"><span>单盒序号</span><b>{{ row.boxSeq + '/' + row.boxTotal }}</b></div>
                  <div v-if="row.printJobNo" class="td-field"><span>打印任务</span><b>{{ row.printJobNo }}</b></div>
                  <div v-if="row.agvJobNo" class="td-field"><span>AGV任务</span><b>{{ row.agvJobNo }}</b></div>
                </div>
              </section>

              <section class="td-card">
                <h4 class="td-card-title">人员信息</h4>
                <div class="td-grid">
                  <div v-if="row.delivererEmployeeNo" class="td-field"><span>送料人工号</span><b>{{ row.delivererEmployeeNo }}</b></div>
                  <div v-if="row.acceptedBy" class="td-field"><span>接单人</span><b>{{ row.acceptedBy }}</b></div>
                  <div v-if="row.picker" class="td-field"><span>拣料人</span><b>{{ row.picker }}</b></div>
                  <div v-if="row.deliverer" class="td-field"><span>配送人</span><b>{{ row.deliverer }}</b></div>
                </div>
              </section>
            </div>

            <section class="td-card td-trail-card">
              <div class="td-card-title td-trail-head">
                <span>扫码流水</span>
                <el-button link type="primary" size="small" @click.stop="loadScanTrail(row, true)">刷新</el-button>
              </div>
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
              </div>
            </section>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="工厂" width="100"><template #default="{row}">{{ factoryLabel(row) }}</template></el-table-column>
      <el-table-column prop="warehouseCode" label="仓库代号" width="120" />
      <el-table-column prop="deliveryArea" label="配送区域" width="100" sortable />
      <el-table-column prop="sendStationAddress" label="发送工位地址" width="160" />
      <el-table-column prop="warehouseAddress" label="仓库地址" width="130" sortable />
      <el-table-column prop="boxSize" label="盒子大小" width="90" />
      <el-table-column prop="materialCode" label="物料号" width="120" />
      <el-table-column label="需求数量" width="120">
        <template #default="{ row }">
          {{ row.requestQty }} {{ row.requestUnit || '个' }}
        </template>
      </el-table-column>
      <el-table-column label="配送" width="110">
        <template #default="{row}">
          <el-tag v-if="deliveryState(row)==='urgent'" type="danger">紧急</el-tag>
          <el-tag v-else-if="deliveryState(row)==='overdue'" type="warning">超时</el-tag>
          <span v-else>正常</span>
        </template>
      </el-table-column>
      <el-table-column label="打印状态" width="130">
        <template #default="{row}">
          <el-tag v-if="row.printStatus" :type="printTagType(row.printStatus)" size="small">{{ printStatusCn(row.printStatus, row.printChannel) }}</el-tag>
          <el-tag v-else-if="row.printGenerated || row.printJobNo" type="info" size="small">已生成</el-tag>
          <span v-else class="muted-text">未提交</span>
        </template>
      </el-table-column>
      <el-table-column label="申请接收时间" width="180">
        <template #default="{row}">
          <span>{{ fmtDeadline(row.createdAt || row.receivedAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column fixed="right" label="操作" width="360">
        <template #default="{row}">
          <div class="task-actions">
            <el-button v-if="canPrint(row)" size="small" type="primary" plain @click="openPrint(row)">预览/打印标签</el-button>
            <el-button v-if="canShowComplete(row)" size="small" type="success" @click="complete(row)">完成</el-button>
            <el-button v-if="canDo(row,'exception')" size="small" type="danger" @click="exception(row)">缺料/异常</el-button>
            <el-button v-if="canDo(row,'forceComplete')" size="small" type="danger" plain @click="forceComplete(row)">强制完成</el-button>
            <el-tag v-if="!hasAnyAction(row)" type="info">无可用操作</el-tag>
          </div>
        </template>
      </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="printDialog" title="仓库条形码标签预览" width="560px">
      <WarehouseLabel v-if="printRow" :row="printRow" :barcode-svg="barcodeSvg" class="preview-label" />
     
      <el-form label-width="90px" style="margin-top:14px">
        <el-form-item label="打印机"><el-input v-model="printerName" placeholder="（仅后端打印服务用）本机浏览器打印无需填写" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="printDialog=false">取消</el-button>
        <el-button type="primary" @click="browserPrint">浏览器打印(本机打印机)</el-button>
        <el-button plain @click="printTask">提交到本地代理队列</el-button>
      </template>
    </el-dialog>

    <div class="warehouse-label-print-root" aria-hidden="true">
      <div v-for="row in browserPrintRows" :key="row.taskNo" class="warehouse-label-print-page">
        <WarehouseLabel :row="row" :barcode-svg="browserPrintBarcodes[row.taskNo] || ''" />
      </div>
    </div>

    <el-dialog v-model="printSettingsDialog" title="定时自动打印设置" width="560px">
      <el-form label-width="130px">
        <el-form-item label="定时自动打印">
          <el-switch v-model="printSettings.enabled" active-text="开启" inactive-text="关闭" />
        </el-form-item>
        <el-form-item label="打印间隔(分钟)">
          <el-input-number v-model="printSettings.interval" :min="1" :step="1" style="width:180px" />
          <span class="ps-hint">如 10=每10分钟，60=每小时</span>
        </el-form-item>
        <el-form-item label="出现后延时(分钟)">
          <el-input-number v-model="printSettings.delay" :min="0" :step="1" style="width:180px" />
          <span class="ps-hint">任务出来后等这么久才自动提交；0=不延时</span>
        </el-form-item>
        <el-form-item label="打印机名称">
          <el-input v-model="printSettings.printerName" placeholder="留空用系统默认打印机，如 ZDesigner GT800 (EPL)" />
        </el-form-item>
        <el-form-item label="配送区域">
          <el-input v-model="printSettings.areas" placeholder="逗号分隔，如 1,2；留空=所有区域" />
        </el-form-item>
        <el-form-item label="上次执行">
          <span class="ps-hint">{{ printSettings.lastRun || '尚未执行' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="printSettingsDialog=false">取消</el-button>
        <el-button type="success" plain @click="runAutoPrintNow">立即执行一次</el-button>
        <el-button type="primary" @click="savePrintSettings">保存设置</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, computed, watch } from 'vue'
import { get, post, del, tagType } from '../api'
import { loadBusinessMeta } from '../meta'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CopyDocument, WarningFilled, ArrowDown, Search } from '@element-plus/icons-vue'
import { canTaskAction, TASK_TABLE_ACTIONS } from '../permissions'
import { runtimePrinterName, saveRuntimePrinterName } from '../config'
import { connectRealtime } from '../realtime'
import WarehouseLabel from '../components/WarehouseLabel.vue'
const rows = ref<any[]>([])
const mappingRows = ref<any[]>([])
const status = ref('')
const printFilter = ref('')
const keyword = ref('')
const date = ref('')
const statuses = ref<{label:string;value:string}[]>([])
// 常用状态留按钮；其余中间流转态(接单/拣料/配送/到达等)收进「更多状态」下拉，避免工具栏过长。
const PRIMARY_STATUS_VALUES = ['COMPLETED', 'EXCEPTION', 'CANCELLED']
const primaryStatuses = computed(() => statuses.value.filter(s => PRIMARY_STATUS_VALUES.includes(s.value)))
const moreStatuses = computed(() => statuses.value.filter(s => !PRIMARY_STATUS_VALUES.includes(s.value)))
const isMoreStatusActive = computed(() => moreStatuses.value.some(s => s.value === status.value))
const activeMoreStatusLabel = computed(() => moreStatuses.value.find(s => s.value === status.value)?.label || '更多状态')
const printDialog = ref(false)
const printRow = ref<any>(null)
const barcodeSvg = ref('')
const browserPrintRows = ref<any[]>([])
const browserPrintBarcodes = ref<Record<string,string>>({})
const printerName = ref(runtimePrinterName())
const scanTrailMap = ref<Record<string, any[]>>({})
const scanTrailLoading = ref<Record<string, boolean>>({})
const AUTO_REFRESH_KEY = 'warehouseTasksAutoRefreshSec'
const autoRefreshSec = ref<number>(Number(localStorage.getItem(AUTO_REFRESH_KEY) || 30))
const lastRefreshText = ref('')
let refreshTimer:number | undefined
let closeRealtime:(() => void) | undefined
const ALL_AREAS = '__all__'
const selectedFactoryKey = ref('')
const selectedAreaKey = ref(ALL_AREAS)
const sortState = ref<{prop:string;order:string} | null>(null)

const currentRole = (() => { try { return JSON.parse(localStorage.getItem('loginUser') || '{}').role || '' } catch { return '' } })()
const canManagePrint = ['ADMIN','WAREHOUSE'].includes(currentRole)
const printSettingsDialog = ref(false)
const printSettings = ref<{enabled:boolean;interval:number;delay:number;printerName:string;areas:string;lastRun:string}>({ enabled:false, interval:60, delay:0, printerName:'', areas:'', lastRun:'' })
let printConfigRows:any[] = []
function cfgVal(key:string){ const hit = printConfigRows.find(c => c.configKey === key); return hit ? (hit.configValue ?? '') : '' }
async function openPrintSettings(){
  try {
    printConfigRows = await get('/configs')
    const v = cfgVal('print.auto.enabled').trim().toLowerCase()
    printSettings.value = {
      enabled: v === 'true' || v === '1' || v === 'on' || v === 'yes',
      interval: Number(cfgVal('print.auto.interval-minutes') || 60) || 60,
      delay: Math.max(0, Number(cfgVal('print.auto.delay-minutes') || 0) || 0),
      printerName: cfgVal('print.auto.printer-name'),
      areas: cfgVal('print.auto.delivery-areas'),
      lastRun: cfgVal('print.auto.last-run')
    }
    printSettingsDialog.value = true
  } catch (e:any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '读取打印配置失败，需要管理员权限')
  }
}
const CONFIG_META:Record<string,string> = {
  'print.auto.enabled': '定时自动打印标签开关',
  'print.auto.interval-minutes': '定时自动打印间隔(分钟)',
  'print.auto.delay-minutes': '任务出现后延时自动提交打印(分钟)',
  'print.auto.printer-name': '定时自动打印使用的打印机名称',
  'print.auto.delivery-areas': '定时自动打印的配送区域(逗号分隔)'
}
async function saveOneConfig(key:string, value:string){
  const src = printConfigRows.find(c => c.configKey === key)
  if (src) {
    await post('/configs', { id: src.id, configKey: src.configKey, configName: src.configName, configValue: value, remark: src.remark, editable: src.editable !== false })
  } else {
    await post('/configs', { configKey: key, configName: CONFIG_META[key] || key, configValue: value, editable: true })
  }
}
async function savePrintSettings(){
  try {
    if (printSettings.value.interval < 1) printSettings.value.interval = 1
    if (printSettings.value.delay < 0) printSettings.value.delay = 0
    await saveOneConfig('print.auto.enabled', printSettings.value.enabled ? 'true' : 'false')
    await saveOneConfig('print.auto.interval-minutes', String(Math.floor(printSettings.value.interval)))
    await saveOneConfig('print.auto.delay-minutes', String(Math.floor(printSettings.value.delay)))
    await saveOneConfig('print.auto.printer-name', printSettings.value.printerName.trim())
    await saveOneConfig('print.auto.delivery-areas', printSettings.value.areas.trim())
    ElMessage.success('定时打印设置已保存')
    printSettingsDialog.value = false
  } catch (e:any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '保存失败')
  }
}
async function runAutoPrintNow(){
  try {
    const n:any = await post('/print-jobs/auto-print/run')
    ElMessage.success(`已执行定时打印，本轮提交 ${n ?? 0} 条`)
    load()
  } catch (e:any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '执行失败')
  }
}

function areaSortKey(v:any){
  const s = String(v ?? '').trim()
  if (!s) return { num: Number.POSITIVE_INFINITY, text: '' }
  const n = Number(s)
  return { num: isNaN(n) ? Number.POSITIVE_INFINITY : n, text: s }
}
function cmp(a:any, b:any){
  const ak = areaSortKey(a), bk = areaSortKey(b)
  if (ak.num !== bk.num) return ak.num - bk.num
  return ak.text.localeCompare(bk.text, 'zh-Hans-CN')
}
function isPrinted(row:any){
  const s = String(row?.printStatus || '').toUpperCase()
  if (s === 'PRINTED') return true
  if (s === 'CANCELLED' || s === 'FAILED') return false
  // 无 printStatus 时用旧字段兜底：只要生成过打印作业即算已打印。
  if (s) return true
  return !!(row?.printGenerated || row?.printJobNo)
}
const printCounts = computed(() => {
  let printed = 0, unprinted = 0
  for (const r of rows.value){ if (isPrinted(r)) printed++; else unprinted++ }
  return { printed, unprinted }
})
function searchableText(row:any){
  return Object.values(row || {})
    .filter(v => v !== null && v !== undefined && typeof v !== 'object')
    .join(' ')
    .toLowerCase()
}
const filteredRows = computed(() => {
  let list = rows.value
  if (printFilter.value === 'PRINTED') list = list.filter(isPrinted)
  else if (printFilter.value === 'UNPRINTED') list = list.filter(r => !isPrinted(r))
  const tokens = keyword.value.trim().toLowerCase().split(/\s+/).filter(Boolean)
  if (tokens.length) list = list.filter(row => { const text = searchableText(row); return tokens.every(t => text.includes(t)) })
  return list
})
const sortedRows = computed(() => {
  const list = [...filteredRows.value]
  const st = sortState.value
  if (st && st.order){
    const dir = st.order === 'ascending' ? 1 : -1
    if (st.prop === 'createdAt'){
      return list.sort((a,b) => (rowTime(a) - rowTime(b)) * dir)
    }
    return list.sort((a,b) => cmp(a[st.prop], b[st.prop]) * dir)
  }
  // 默认排序：第一优先级“配送区域”，第二优先级“仓库地址”。
  return list.sort((a,b) => {
    const byArea = cmp(a.deliveryArea, b.deliveryArea)
    if (byArea !== 0) return byArea
    const wa = String(a.warehouseAddress || a.warehouseLocation || '')
    const wb = String(b.warehouseAddress || b.warehouseLocation || '')
    return wa.localeCompare(wb, 'zh-Hans-CN')
  })
})
function factoryLabel(row:any){ return String(row?.factory ?? '').trim() || '未维护工厂' }
function factoryKey(row:any){ return factoryLabel(row) }
const availableFactories=computed(()=>{
  const counts=new Map<string,number>()
  for(const row of mappingRows.value) counts.set(factoryKey(row),0)
  for(const row of sortedRows.value) counts.set(factoryKey(row),(counts.get(factoryKey(row))||0)+1)
  return Array.from(counts.entries()).sort(([a],[b])=>a.localeCompare(b,'zh-Hans-CN',{numeric:true})).map(([key,count])=>({key,label:key,count}))
})
const selectedFactoryLabel=computed(()=>availableFactories.value.find(f=>f.key===selectedFactoryKey.value)?.label || '未选择工厂')
const selectedFactoryRows=computed(()=>sortedRows.value.filter(r=>factoryKey(r)===selectedFactoryKey.value))
const availableAreas=computed(()=>{
  const counts=new Map<string,number>()
  for(const row of mappingRows.value){
    if(factoryKey(row)!==selectedFactoryKey.value) continue
    const area=String(row?.deliveryArea ?? '').trim()
    if(area) counts.set(area,0)
  }
  for(const row of selectedFactoryRows.value){
    const area=String(row?.deliveryArea ?? '').trim() || '未分类'
    counts.set(area,(counts.get(area)||0)+1)
  }
  return [{key:ALL_AREAS,label:'全部',count:selectedFactoryRows.value.length},...Array.from(counts.entries()).sort(([a],[b])=>a.localeCompare(b,'zh-Hans-CN',{numeric:true})).map(([key,count])=>({key,label:key,count}))]
})
const selectedAreaLabel=computed(()=>selectedAreaKey.value===ALL_AREAS?'全部':selectedAreaKey.value)
const currentRows=computed(()=>selectedAreaKey.value===ALL_AREAS?selectedFactoryRows.value:selectedFactoryRows.value.filter(r=>(String(r?.deliveryArea ?? '').trim()||'未分类')===selectedAreaKey.value))
function selectFactory(key:string){ selectedFactoryKey.value=key; selectedAreaKey.value=ALL_AREAS }
watch(availableFactories,factories=>{ if(!factories.some(f=>f.key===selectedFactoryKey.value)) selectedFactoryKey.value=factories[0]?.key||'' },{immediate:true})
watch(availableAreas,areas=>{ if(!areas.some(a=>a.key===selectedAreaKey.value)) selectedAreaKey.value=ALL_AREAS })
function onSortChange({ prop, order }:{prop:string;order:string}){
  sortState.value = order ? { prop, order } : null
}
function sortBy(prop:string){
  if (sortState.value?.prop === prop){
    sortState.value = sortState.value.order === 'ascending' ? { prop, order: 'descending' } : null
  } else {
    sortState.value = { prop, order: 'ascending' }
  }
}
function rowTime(row:any){
  const d = new Date(row?.createdAt || row?.receivedAt || row?.acceptedAt || 0)
  return isNaN(d.getTime()) ? 0 : d.getTime()
}
function sortByTime(){
  if (sortState.value?.prop === 'createdAt'){
    sortState.value = sortState.value.order === 'descending' ? { prop:'createdAt', order:'ascending' } : { prop:'createdAt', order:'descending' }
  } else {
    sortState.value = { prop:'createdAt', order:'descending' }
  }
}
function resetSort(){ sortState.value = null }
function sortArrow(prop:string){
  if (sortState.value?.prop !== prop) return ''
  return sortState.value.order === 'ascending' ? '↑' : '↓'
}
function isUrgent(row:any){
  const mode = String(row?.deliveryMode || '').toUpperCase()
  const prio = String(row?.priority || '').toUpperCase()
  const usage = String(row?.labelUsageType || '').toUpperCase()
  return mode === 'URGENT' || prio === 'URGENT' || usage === 'SPARE'
}
async function load(){
  const params:any = {}
  if (status.value && status.value !== 'ALL') params.status = status.value
  if (date.value) params.date = date.value
  const [list,mappings]:any[] = await Promise.all([
    get('/tasks', Object.keys(params).length ? params : undefined),
    get('/mappings').catch(()=>[])
  ])
  mappingRows.value=Array.isArray(mappings)?mappings:[]
  rows.value = status.value === '' ? list.filter(t => t.status !== 'COMPLETED') : list
  lastRefreshText.value = `已更新 ${new Date().toLocaleTimeString()}`
}
function selectStatus(value:string){
  status.value = value
  load()
}
function setupAutoRefresh(){
  if (refreshTimer){ window.clearInterval(refreshTimer); refreshTimer = undefined }
  if (autoRefreshSec.value > 0){
    refreshTimer = window.setInterval(load, autoRefreshSec.value * 1000)
  }
}
function onAutoRefreshChange(){
  localStorage.setItem(AUTO_REFRESH_KEY, String(autoRefreshSec.value))
  setupAutoRefresh()
}
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
function fmtDeadline(v:any){
  if (!v) return '-'
  const d = new Date(v)
  if (isNaN(d.getTime())) return String(v)
  const p = (n:number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}年${p(d.getMonth()+1)}月${p(d.getDate())}日 ${p(d.getHours())}:${p(d.getMinutes())}`
}
function isOverdue(row:any){
  if (!row?.deadlineAt) return false
  if (['COMPLETED','CANCELLED','DELETED'].includes(row.status)) return false
  const d = new Date(row.deadlineAt)
  return !isNaN(d.getTime()) && d.getTime() < Date.now()
}
function statusCn(v:any){
  if (!v) return '-'
  const hit = statuses.value.find(s => s.value === v)
  return hit ? hit.label : v
}
function printStatusCn(v:any, channel?:any){
  const s = String(v || '').toUpperCase()
  const ch = String(channel || '').toUpperCase()
  if (s === 'PRINTED') return ch === 'BROWSER' ? '浏览器打印完成' : '自动打印完成'
  const map:Record<string,string> = {
    CREATED: '已创建',
    RENDERED: ch === 'BROWSER' ? '浏览器待打印' : '待代理领取',
    SENT: '已下发代理',
    FAILED: '打印失败',
    CANCELLED: '已取消'
  }
  return v ? (map[s] || v) : '-'
}
function printTagType(v:any){
  const s = String(v || '').toUpperCase()
  if (s === 'PRINTED') return 'success'
  if (s === 'FAILED' || s === 'CANCELLED') return 'danger'
  if (s === 'SENT') return 'primary'
  if (s === 'RENDERED' || s === 'CREATED') return 'warning'
  return 'info'
}
function labelUsageCn(v:any){
  const map:Record<string,string> = { USE:'使用', SPARE:'备用', DIRECT:'直拉' }
  return v ? (map[String(v).toUpperCase()] || v) : '-'
}
function deliveryModeCn(v:any){
  const map:Record<string,string> = { NORMAL:'普通', URGENT:'紧急', DIRECT:'直送' }
  return v ? (map[String(v).toUpperCase()] || v) : '-'
}
function deliveryState(row:any){
  const mode = String(row?.deliveryMode || '').toUpperCase()
  const prio = String(row?.priority || '').toUpperCase()
  if (mode === 'URGENT' || prio === 'URGENT') return 'urgent'
  if (isOverdue(row)) return 'overdue'
  return 'normal'
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
function canShowComplete(row:any){ return !['COMPLETED','CANCELLED'].includes(String(row?.status || '').toUpperCase()) }
function canPrint(row:any){ return ['CREATED','ACCEPTED','PICKING','PICKED'].includes(row.status) }
function canDelete(row:any){ return !['COMPLETED','CANCELLED'].includes(row.status) }
function hasAnyAction(row:any){ return canPrint(row) || canDelete(row) || canShowComplete(row) || TASK_TABLE_ACTIONS.some(a => canDo(row, a)) }
async function openPrint(row:any){
  printRow.value = row
  barcodeSvg.value = ''
  printDialog.value = true
  const code = row.warehouseCode || row.barcodeValue
  if (code) {
    try {
      const result:any = await post('/labels/code/render', { text: String(code), format: 'CODE_128', width: 520, height: 150, includeText: true })
      barcodeSvg.value = result.svg
    } catch (e:any) {
      ElMessage.error(e?.response?.data?.message || e?.message || '条形码生成失败')
    }
  } else {
    ElMessage.warning('当前任务缺少仓库代号，无法生成可扫码条形码')
  }
}
async function browserPrint(){
  const row = printRow.value
  if (!row) return
  await printLabelsInBrowser([row], { [row.taskNo]: barcodeSvg.value })
  ElMessage.success("已调起浏览器打印：80×50mm，边距0，缩放100%；任务已记录为浏览器打印已调起")
  try {
    await post("/print-jobs", { taskNo:row.taskNo, printerName:(printerName.value.trim() || "浏览器打印"), printType:"WAREHOUSE_BARCODE_LABEL", printChannel:"BROWSER" })
    load()
  } catch {}
  printDialog.value = false
}
async function printLabelsInBrowser(rows:any[], barcodes:Record<string,string>){
  browserPrintRows.value = rows
  browserPrintBarcodes.value = barcodes
  await nextTick()
  window.print()
}
async function printTask(){
  if (!printRow.value) return
  if (!printerName.value.trim()) { ElMessage.warning('请填写已连接打印机名称'); return }
  saveRuntimePrinterName(printerName.value)
  await post('/print-jobs', { taskNo: printRow.value.taskNo, printerName: printerName.value.trim(), printType: 'WAREHOUSE_BARCODE_LABEL' })
  ElMessage.success('仓库标签已提交到本地代理打印队列，打印成功回传后该任务将自动标记为已完成')
  printDialog.value=false
  load()
}
async function act(row:any, action:string){ await post(`/tasks/${row.taskNo}/${action}`, { expectedStatus: row.status }); ElMessage.success('操作成功'); load() }
async function deleteTask(row:any){
  await ElMessageBox.confirm(`确认取消任务 ${row.taskNo}？系统会释放库存锁定、取消打印/AGV，但永久保留任务用于历史统计。`, '取消任务确认', { type:'warning', confirmButtonText:'继续取消', cancelButtonText:'返回' })
  const result = await ElMessageBox.prompt(`请再次输入任务号 ${row.taskNo} 以确认取消`, '二次确认', { confirmButtonText:'确认取消', cancelButtonText:'返回', inputPattern:new RegExp(`^${row.taskNo.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`), inputErrorMessage:'输入的任务号不一致' })
  if (result.value !== row.taskNo) return
  await del(`/tasks/${encodeURIComponent(row.taskNo)}`)
  ElMessage.success('任务已取消，历史统计记录已保留')
  load()
}
async function complete(row:any){
  await ElMessageBox.confirm(`确认要完成任务 ${row.taskNo} 吗？完成后该单据会从默认“进行中”列表隐藏。`, '完成确认', { type:'warning', confirmButtonText:'继续', cancelButtonText:'取消' })
  const result = await ElMessageBox.prompt(`请再次输入任务号 ${row.taskNo} 以确认完成`, '二次确认', {
    confirmButtonText:'确认完成',
    cancelButtonText:'取消',
    inputPattern:new RegExp(`^${row.taskNo.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`),
    inputErrorMessage:'输入的任务号不一致'
  })
  if (result.value !== row.taskNo) return
  try {
    await post(`/tasks/${row.taskNo}/complete`, { expectedStatus: row.status })
    ElMessage.success('任务已完成')
    load()
  } catch (e:any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '完成失败')
  }
}
async function exception(row:any){ const reason = await ElMessageBox.prompt('请输入异常原因','异常处理'); await post(`/tasks/${row.taskNo}/exception`, { exceptionReason:reason.value, expectedStatus: row.status }); load() }
async function forceComplete(row:any){ await ElMessageBox.confirm('强制完成会跳过部分状态校验，只建议在现场已确认补料完成时使用。确认继续？','强制完成确认'); await post(`/tasks/${row.taskNo}/forceComplete`, { expectedStatus: row.status, force:true }); load() }
async function receive(row:any){ const empty = await ElMessageBox.prompt('现场收货确认，可填写空盒编号','收货确认'); await post(`/tasks/${row.taskNo}/receive`, { receiveScanCode:row.taskNo, emptyContainerNo:empty.value, expectedStatus:row.status }); load() }
async function returnEmpty(row:any){ const empty = await ElMessageBox.prompt('请输入空盒编号','空盒回收'); await post(`/tasks/${row.taskNo}/returnEmptyBox`, { emptyContainerNo:empty.value, expectedStatus:row.status }); load() }
onMounted(async()=>{
  const meta = await loadBusinessMeta(); statuses.value = meta.taskStatuses.map(x => ({ label: x.label, value: x.value })); load()
  setupAutoRefresh()
  closeRealtime = connectRealtime(['tasks','taskWarnings'], () => { load() })
})
onUnmounted(()=>{
  if (refreshTimer) window.clearInterval(refreshTimer)
  closeRealtime?.()
})
</script>

<style scoped>
.warehouse-factory-groups{display:flex;flex-direction:column;gap:14px}.warehouse-factory-group{border:1px solid rgb(203,213,225);border-radius:12px;overflow:hidden;background:white}.warehouse-factory-title{width:100%;border:0;background:rgb(239,246,255);color:rgb(30,58,138);padding:12px 16px;display:flex;align-items:center;justify-content:space-between;cursor:pointer}.warehouse-factory-title b{font-size:17px}.warehouse-factory-title small{margin-left:12px;color:rgb(100,116,139)}
.warehouse-task-scope{overflow:visible;border:1px solid #e2e8f0;border-radius:10px;background:#fff}
.task-scope-switcher{position:sticky;top:0;z-index:20;display:flex;align-items:center;gap:22px;min-width:0;padding:10px 16px;border-bottom:1px solid #e2e8f0;background:rgb(255 255 255 / 96%);box-shadow:0 4px 12px rgb(15 23 42 / 5%);backdrop-filter:blur(8px)}
.task-scope-track{display:flex;align-items:center;gap:7px;min-width:0;overflow-x:auto;white-space:nowrap;scrollbar-width:thin}
.task-scope-switcher .factory-track{flex:0 1 auto}.task-scope-switcher .area-track{flex:1 1 0}
.task-scope-label{position:sticky;left:0;z-index:1;flex:0 0 auto;padding:4px 2px;background:#fff;color:#475569;font-size:13px;font-weight:600}
.task-scope-chip{flex:0 0 auto;height:32px;padding:0 13px;border:1px solid #cbd5e1;border-radius:7px;background:#fff;color:#334155;font-size:13px;font-weight:600;line-height:30px;cursor:pointer}
.task-scope-chip:hover{border-color:#60a5fa;color:#2563eb}.task-scope-chip.active{border-color:#2563eb;background:#2563eb;color:#fff}
.task-scope-chip.area-chip{display:inline-flex;align-items:center;gap:7px}
.task-scope-chip.area-chip.has-tasks{font-weight:800;animation:warehouse-area-police-light 1s steps(1,end) infinite}
.task-scope-chip.area-chip.has-tasks:before{width:9px;height:9px;flex:0 0 auto;border-radius:50%;content:"";animation:warehouse-area-police-dot 1s steps(1,end) infinite}
.task-scope-current{display:flex;align-items:center;gap:10px;padding:11px 16px;border-bottom:1px solid #e2e8f0}.task-scope-current b{font-size:15px}.task-scope-current span{color:#64748b;font-size:12px}
@keyframes warehouse-area-police-light{0%,49%{border-color:#2563eb;background:#2563eb;color:#fff;box-shadow:0 0 0 2px rgb(37 99 235 / 22%),0 0 14px 3px rgb(37 99 235 / 50%)}50%,100%{border-color:#dc2626;background:#dc2626;color:#fff;box-shadow:0 0 0 2px rgb(220 38 38 / 22%),0 0 14px 3px rgb(220 38 38 / 52%)}}
@keyframes warehouse-area-police-dot{0%,49%{background:#fff;box-shadow:0 0 7px 2px #fff}50%,100%{background:#fde047;box-shadow:0 0 8px 3px rgb(253 224 71 / 90%)}}
@media(prefers-reduced-motion:reduce){.task-scope-chip.area-chip.has-tasks{border-color:#dc2626;background:#fff1f2;color:#b91c1c;box-shadow:0 0 0 2px rgb(220 38 38 / 18%);animation:none}.task-scope-chip.area-chip.has-tasks:before{background:#dc2626;box-shadow:none;animation:none}}
.status-menu{display:inline-flex;align-items:center;gap:8px;flex-wrap:wrap;margin-right:8px;margin-bottom:8px;vertical-align:middle}.status-menu-label{color:#64748b;font-size:13px;font-weight:600}.status-menu .el-button{margin-left:0}
.is-active-status{color:var(--el-color-primary);font-weight:600;background:var(--el-color-primary-light-9)}
.refresh-tip{ margin-left: 10px; color: #64748b; font-size: 12px; }
.muted-text{ color:#94a3b8; font-size:12px; }
.pf-count{ color:#94a3b8; font-size:12px; margin-left:2px; }
.ps-hint{ margin-left: 10px; color: #94a3b8; font-size: 12px; }
.sort-label{ margin-left: 12px; color: #64748b; font-size: 13px; }

.task-detail{
  position: sticky;
  left: 0;
  width: calc(100vw - 286px);
  max-width: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 18px;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 10px;
  border: 1px solid #e2e8f0;
}

.td-header{
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-left: 4px solid #2563eb;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, .04);
}
.td-header-left{ display: flex; align-items: center; gap: 10px; min-width: 0; }
.td-header-label{
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  background: #eff6ff;
  padding: 2px 8px;
  border-radius: 4px;
  white-space: nowrap;
}
.td-header-no{
  font-family: 'JetBrains Mono','Consolas','Menlo',monospace;
  font-weight: 700;
  font-size: 14px;
  color: #0f172a;
  word-break: break-all;
}
.td-header-right{ display: flex; align-items: center; gap: 6px; flex-shrink: 0; }

.td-exception{
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  color: #b91c1c;
  font-size: 13px;
  font-weight: 600;
}

.td-sections{
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
  align-items: start;
}

.td-card{
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 14px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, .04);
}
.td-card-title{
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 700;
  color: #1e293b;
  padding-left: 8px;
  border-left: 3px solid #2563eb;
  line-height: 1.2;
}
.td-grid{
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
}
.td-field{
  display: grid;
  grid-template-columns: 92px 1fr;
  align-items: center;
  min-height: 30px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #eef2f6;
  font-size: 12.5px;
}
.td-field span{
  background: #f8fafc;
  color: #64748b;
  font-weight: 600;
  padding: 6px 8px;
  height: 100%;
  display: flex;
  align-items: center;
  white-space: nowrap;
  border-right: 1px solid #eef2f6;
}
.td-field b{
  padding: 6px 10px;
  color: #0f172a;
  font-weight: 600;
  word-break: break-all;
}

.td-trail-card{ padding-bottom: 8px; }
.td-trail-head{
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

@media (max-width: 1180px){
  .td-sections{ grid-template-columns: 1fr; }
  .td-field{ grid-template-columns: 84px 1fr; font-size: 12px; }
}

.task-actions{
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  align-items: center;
  gap: 8px;
}
.task-actions .el-button{ margin-left: 0; }

.deadline-overdue{
  color: #b91c1c;
  font-weight: 700;
  background: #fef3c7;
  padding: 2px 6px;
  border-radius: 4px;
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

.scan-trail{
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 2px;
  max-height: 240px;
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
.preview-label{margin:0 auto}
</style>
