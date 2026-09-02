<template>
  <div class="factory-print-page">
    <section class="toolbar print-toolbar">
      <div>
        <el-input v-model="printerName" placeholder="打印机名称" style="width:220px" />
        <el-input v-model="searchKeyword" clearable placeholder="搜索：任务号/工位/物料/仓库/数量等全部关键字" style="width:340px" />
        <el-button type="success" plain @click="batchSubmitPrintJobs" :disabled="!filteredRows.length || !printerName.trim()" :loading="batchSubmitting">一键提交到本地代理队列</el-button>
        <el-select v-model="groupingMode" style="width:150px">
          <el-option label="按配送区域" value="area" />
          <el-option label="按总装地址" value="address" />
        </el-select>
        <el-select v-model="defaultGroupSort" style="width:170px">
          <el-option label="分类默认：拼音" value="pinyin" />
          <el-option label="分类默认：字母数字" value="alnum" />
          <el-option label="分类默认：时间早→晚" value="timeAsc" />
          <el-option label="分类默认：时间晚→早" value="timeDesc" />
        </el-select>
        <el-button @click="load">刷新</el-button>
        <el-select v-model="autoRefreshSec" style="width:150px" @change="onAutoRefreshChange">
          <el-option :value="0" label="自动刷新：关闭" />
          <el-option :value="10" label="每 10 秒" />
          <el-option :value="30" label="每 30 秒" />
          <el-option :value="60" label="每 1 分钟" />
          <el-option :value="300" label="每 5 分钟" />
        </el-select>
        <span v-if="lastRefreshText" class="refresh-tip">{{ lastRefreshText }}</span>
        <el-button type="warning" plain @click="runAutoPrint">立即执行定时打印</el-button>
        <el-button type="success" plain @click="openAreaDialog">定时打印设置(按区域)</el-button>
      </div>
      <div class="hint">定时自动打印支持全局与按区域两种模式，按区域设置优先于全局。</div>
    </section>
    <section class="pending-panel">
      <div class="pending-head">
        <div>
          <h3>待打印补货任务</h3><p>{{ showScopeSwitcher ? '按工厂与配送分类处理尚未生成打印作业的任务' : '仅显示当前账号已授权区域内尚未生成打印作业的任务' }}</p>
        </div>
        <div class="pending-actions">
          <el-tag type="warning" effect="plain">共 {{ filteredRows.length }} 条待生成</el-tag>
          <el-tag type="info" effect="plain">{{ factoryGroups.reduce((n,g)=>n+g.groups.length,0) }} 个分类</el-tag>
          <el-button size="small" type="success" plain :disabled="!filteredRows.length || !printerName.trim()" :loading="batchSubmitting" @click="batchSubmitPrintJobs">提交到代理队列</el-button>
          <el-button size="small" @click="loadPending">刷新待打印</el-button>
        </div>
      </div>
      <div v-if="showScopeSwitcher" class="scope-switcher">
        <div class="scope-track factory-track">
          <span class="scope-label">工厂：</span>
          <button v-for="factory in availableFactories" :key="factory.key" type="button" class="scope-chip" :class="{ active: selectedFactoryKey === factory.key }" @click="selectFactory(factory.key)">{{ factory.label }} {{ factory.count }}</button>
        </div>
        <div class="scope-track area-track">
          <span class="scope-label">区域：</span>
          <button v-for="area in availableAreas" :key="area.key" type="button" class="scope-chip area-chip" :class="{ active: selectedAreaKey === area.key, 'has-tasks': area.key !== ALL_AREAS && area.count > 0 }" @click="selectedAreaKey = area.key">{{ area.label }}<template v-if="area.key !== ALL_AREAS"> {{ area.count }}</template></button>
        </div>
      </div>
      <el-alert v-if="!currentRows.length" type="info" :closable="false" :title="rawRows.length ? '当前工厂和区域没有匹配的待打印标签' : '暂无待打印标签'" style="margin-bottom:10px" />
      <section v-else class="current-scope">
          <div class="station-title">
            <div><b v-if="showScopeSwitcher">当前：{{ selectedFactoryLabel }} / {{ selectedAreaLabel }}</b><span>{{ currentRows.length }} 张标签</span></div>
            <div class="group-actions">
              <el-button size="small" type="primary" plain :loading="batchPrinting" @click="printRows(currentRows, `已调起本区域打印`)">打印本区域</el-button>
              <el-button size="small" type="success" plain :disabled="!printerName.trim()" :loading="batchSubmitting" @click="submitRows(currentRows, `已提交本区域`)">提交本区域</el-button>
              <div class="group-sort">
                <small>组内排序</small>
                <el-select v-model="groupSortMap[currentGroupKey]" size="small" style="width:132px">
                  <el-option label="拼音" value="pinyin" />
                  <el-option label="字母数字" value="alnum" />
                  <el-option label="时间早→晚" value="timeAsc" />
                  <el-option label="时间晚→早" value="timeDesc" />
          <el-option label="仓储地址升序" value="warehouseAsc" />
          <el-option label="仓储地址降序" value="warehouseDesc" />
                </el-select>
              </div>
            </div>
          </div>
          <div class="thumb-grid">
        <article v-for="row in currentRows" :key="row.taskNo" class="thumb-card" :class="{ urgent: isUrgentTask(row), selected: isSelected(row) }">
          <div class="thumb-tags"><el-checkbox :model-value="isSelected(row)" @change="checked => toggleSelected(row, Boolean(checked))" />
            <el-tag :type="isUrgentTask(row) ? 'danger' : 'success'" effect="dark" size="small">{{ isUrgentTask(row) ? '紧急' : '正常' }}</el-tag>
            <el-tag :type="tagType(row.status)" size="small">{{ row.status }}</el-tag>
          </div>
          <div class="label-thumb" @click="previewPending(row)">
            <div class="thumb-top">
              <div class="thumb-usage" :class="{ urgent: isUrgentTask(row) }">{{ isUrgentTask(row) ? '紧急配送(备用)' : '正常配送(使用)' }}</div>
              <div class="thumb-barcode"><div class="fake-bars"></div><b>{{ previewField(row, 'warehouseCode', 'barcodeValue') }}</b></div>
            </div>
            <div class="thumb-mid">
              <div class="thumb-cell"><span>物料</span><b>{{ previewField(row, 'materialCode', 'materialName') }}</b></div>
              <div class="thumb-cell"><span>仓储</span><b>{{ previewField(row, 'warehouseAddress', 'warehouseLocation') }}</b></div>
              <div class="thumb-cell"><span>工位</span><b>{{ stationName(row) }}</b></div>
            </div>
            <div class="thumb-bottom">
              <div class="thumb-cell"><span>盒子</span><b>{{ previewField(row, 'boxSize') }}</b></div>
              <div class="thumb-cell"><span>数量</span><b>{{ previewField(row, 'requestQty') }}</b></div>
              <div class="thumb-cell"><span>配送区域</span><b>{{ previewField(row, 'deliveryArea') }}</b></div>
              <div class="thumb-cell"><span>任务</span><b class="mini">{{ row.taskNo }}</b></div>
            </div>
          </div>
          <div class="thumb-meta"><div><span>申请时间</span><b>{{ fmtTime(row.createdAt || row.receivedAt) }}</b></div><div><span>仓库代号</span><b>{{ row.warehouseCode || '-' }}</b></div></div>
          <div class="thumb-actions">
            <el-button size="small" plain @click="previewPending(row)">预览</el-button>
            <el-button size="small" type="primary" @click="previewPending(row)">提交打印</el-button>
          </div>
        </article>
          </div>
      </section>
    </section>

    <section class="records-panel">
      <div class="records-head">
        <div>
          <h3>打印记录</h3><p>查看浏览器与本地代理的历史打印状态</p>
        </div>
        <div class="records-actions">
          <el-select v-model="recordChannel" size="small" style="width:150px" @change="onRecordFilterChange">
            <el-option label="全部方式" value="" />
            <el-option label="浏览器打印" value="BROWSER" />
            <el-option label="代理自动打印" value="AGENT" />
          </el-select>
          <el-select v-model="recordStatus" size="small" style="width:150px" @change="onRecordFilterChange">
            <el-option label="全部状态" value="" />
            <el-option label="待代理领取" value="RENDERED" />
            <el-option label="已下发代理" value="SENT" />
            <el-option label="已打印完成" value="PRINTED" />
            <el-option label="打印失败" value="FAILED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
          <el-button size="small" @click="loadRecords">刷新记录</el-button>
        </div>
      </div>
      <el-table :data="records" border stripe size="small" height="360">
        <el-table-column label="工厂" width="110"><template #default="{row}">{{ factoryName(row) }}</template></el-table-column>
        <el-table-column prop="materialCode" label="料号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="warehouseAddress" label="仓储地址" min-width="150" show-overflow-tooltip />
        <el-table-column prop="deliveryAddress" label="总装地址" min-width="150" show-overflow-tooltip />
        <el-table-column prop="deliveryArea" label="配送区域" width="110" show-overflow-tooltip />
        <el-table-column label="打印方式" width="120">
          <template #default="{row}">
            <el-tag :type="row.printChannel==='BROWSER' ? 'warning' : 'primary'" size="small">{{ channelCn(row.printChannel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{row}">
            <el-tag :type="recordTagType(row.status)" size="small">{{ recordStatusCn(row.status, row.printChannel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="printerName" label="打印机" min-width="150" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="120" show-overflow-tooltip />
        <el-table-column label="提交时间" width="170">
          <template #default="{row}">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="完成时间" width="170">
          <template #default="{row}">{{ fmtTime(row.printedAt) }}</template>
        </el-table-column>
        <el-table-column prop="lastError" label="错误信息" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{row}">
            <el-button size="small" type="primary" plain :loading="reprintingJobNo === row.printJobNo" @click="reprintRecord(row)">补打标签</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="recordTotal > PAGE_SIZE"
        v-model:current-page="recordPage"
        :page-size="PAGE_SIZE"
        :total="recordTotal"
        layout="total, prev, pager, next"
        style="margin-top:16px;justify-content:flex-end"
        @current-change="loadRecords"
      />
    </section>

    <el-dialog v-model="previewDialog" title="待打印标签预览" width="560px">
      <WarehouseLabel v-if="previewRow" :row="previewRow" :barcode-svg="previewBarcodeSvg" class="preview-label" />
      <el-form label-width="90px" style="margin-top:14px">
        <el-form-item label="打印机"><el-input v-model="printerName" placeholder="后端打印服务用；浏览器打印无需填写" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="previewDialog=false">取消</el-button>
        <el-button type="primary" @click="browserPrint">浏览器打印(本机打印机)</el-button>
        <el-button plain @click="printPending(previewRow)">提交到本地代理队列</el-button>
      </template>
    </el-dialog>

    <div class="warehouse-label-print-root" aria-hidden="true">
      <div v-for="row in browserPrintRows" :key="row.taskNo" class="warehouse-label-print-page">
        <WarehouseLabel :row="row" :barcode-svg="browserPrintBarcodes[row.taskNo] || ''" />
      </div>
    </div>

    <el-dialog v-model="areaDialog" title="定时打印设置（按配送区域）" width="720px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px"
        title="每个配送区域可单独设定是否自动打印、间隔分钟、任务出现后延时分钟、打印机与标签类型。“出现后延时”表示任务生成后需等待这么久才会被自动提交到代理打印队列（0=不延时）。保存后下一分钟内生效，无需重启。若这里一条都不配置，则回退为“系统参数”里的全局间隔+延时+区域白名单模式。" />
      <div style="margin-bottom:10px">
        <el-button size="small" type="primary" plain @click="addAreaRow">新增区域</el-button>
        <el-select v-model="pickArea" size="small" clearable placeholder="从已有区域选择" style="width:200px;margin-left:8px" @change="onPickArea">
          <el-option v-for="a in knownAreas" :key="a" :label="a" :value="a" />
        </el-select>
        <span class="hint" style="margin-left:8px">间隔单位：分钟，最小 1。打印机/类型留空则用全局默认。</span>
      </div>
      <el-table :data="areaRows" border size="small">
        <el-table-column label="配送区域" width="120">
          <template #default="{row}"><el-input v-model="row.area" placeholder="如 1" /></template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{row}"><el-switch v-model="row.enabled" /></template>
        </el-table-column>
        <el-table-column label="间隔(分钟)" width="120">
          <template #default="{row}"><el-input-number v-model="row.intervalMinutes" :min="1" :step="1" controls-position="right" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="出现后延时(分钟)" width="140">
          <template #default="{row}"><el-input-number v-model="row.delayMinutes" :min="0" :step="1" controls-position="right" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="打印机(可留空)" min-width="160">
          <template #default="{row}"><el-input v-model="row.printer" placeholder="留空用全局" /></template>
        </el-table-column>
        <el-table-column label="标签类型(可留空)" min-width="180">
          <template #default="{row}"><el-input v-model="row.printType" placeholder="WAREHOUSE_BARCODE_LABEL" /></template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{$index}"><el-button link type="danger" @click="areaRows.splice($index,1)">删除</el-button></template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="areaDialog=false">取消</el-button>
        <el-button type="primary" @click="saveAreaSchedules">保存设置</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { get, post, tagType } from '../api'
import { getLoginUser } from '../auth'
import { ElMessage } from 'element-plus'
import { runtimePrinterName, saveRuntimePrinterName } from '../config'
import WarehouseLabel from '../components/WarehouseLabel.vue'
const rawRows = ref<any[]>([])
const PAGE_SIZE = 20
const printerName = ref(runtimePrinterName())
const previewDialog = ref(false)
const previewRow = ref<any>(null)
const previewBarcodeSvg = ref('')
const browserPrintRows = ref<any[]>([])
const browserPrintBarcodes = ref<Record<string,string>>({})
const searchKeyword = ref('')
const selectedTaskNos = ref<Set<string>>(new Set())
type SortMode = 'pinyin'|'alnum'|'timeAsc'|'timeDesc'|'warehouseAsc'|'warehouseDesc'
const ALL_AREAS = '__all__'
const selectedFactoryKey = ref<string>('')
const selectedAreaKey = ref<string>(ALL_AREAS)
// 仓库员恢复工厂/配送区域筛选；后端仍按账号范围过滤，按钮不会扩大权限。
// ADMIN/SYSTEM 即使被维护了范围，仍保留完整入口。
const showScopeSwitcher = computed(() => {
  const user = getLoginUser()
  const role = String(user.role || '').toUpperCase()
  if (role === 'ADMIN' || role === 'SYSTEM' || role === 'WAREHOUSE') return true
  return !String(user.factory || '').trim()
    || !Array.isArray(user.deliveryAreas)
    || !user.deliveryAreas.some(area => String(area || '').trim())
})
const defaultGroupSort = ref<SortMode>('pinyin')
const groupingMode = ref<'area'|'address'>('area')
const groupSortMap = ref<Record<string, SortMode>>({})
const batchPrinting = ref(false)
const batchSubmitting = ref(false)
const records = ref<any[]>([])
const recordPage = ref(1)
const recordTotal = ref(0)
const recordChannel = ref('')
const recordStatus = ref('')
const reprintingJobNo = ref('')
const AUTO_REFRESH_KEY = 'factoryPrintAutoRefreshSec'
const autoRefreshSec = ref<number>(Number(localStorage.getItem(AUTO_REFRESH_KEY) || 30))
const lastRefreshText = ref('')
let refreshTimer:number | undefined
let loading = false
async function load(){
  if (loading) return
  loading = true
  try {
    await Promise.all([loadPending(), loadRecords()])
    lastRefreshText.value = `已更新 ${new Date().toLocaleTimeString()}`
  } finally {
    loading = false
  }
}
function setupAutoRefresh(){
  if (refreshTimer){ window.clearInterval(refreshTimer); refreshTimer = undefined }
  if (autoRefreshSec.value > 0 && !previewDialog.value) refreshTimer = window.setInterval(load, autoRefreshSec.value * 1000)
}
function onAutoRefreshChange(){
  localStorage.setItem(AUTO_REFRESH_KEY, String(autoRefreshSec.value))
  setupAutoRefresh()
}
async function loadRecords(){
  try {
    const result:any = await get('/print-jobs', {
      page: recordPage.value - 1,
      size: PAGE_SIZE,
      ...(recordStatus.value ? { status: recordStatus.value } : {}),
      ...(recordChannel.value ? { channel: recordChannel.value } : {})
    })
    records.value = Array.isArray(result?.items) ? result.items.map(normalizePrintRecord) : []
    recordTotal.value = Number(result?.total || 0)
  } catch (e:any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '读取打印记录失败')
  }
}
function onRecordFilterChange(){
  recordPage.value = 1
  loadRecords()
}
function channelCn(v:any){
  const s = String(v || '').toUpperCase()
  if (s === 'BROWSER') return '浏览器打印'
  if (s === 'AGENT') return '代理自动'
  return '代理自动'
}
function recordStatusCn(v:any, channel?:any){
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
function recordTagType(v:any){
  const s = String(v || '').toUpperCase()
  if (s === 'PRINTED') return 'success'
  if (s === 'FAILED' || s === 'CANCELLED') return 'danger'
  if (s === 'SENT') return 'primary'
  if (s === 'RENDERED' || s === 'CREATED') return 'warning'
  return 'info'
}
function recordLabelRow(record:any){
  let snapshot:any = {}
  try { snapshot = JSON.parse(record?.payload || '{}') || {} } catch { snapshot = {} }
  return {
    ...record,
    ...snapshot,
    taskNo: snapshot.taskNo || record?.taskNo,
    factory: snapshot.factory || record?.factory,
    warehouseCode: snapshot.warehouseCode || snapshot.barcode,
    barcodeValue: snapshot.barcode || snapshot.warehouseCode,
    requestQty: snapshot.requestQty ?? snapshot.qty,
    warehouseAddress: snapshot.warehouseAddress || snapshot.from,
    warehouseLocation: snapshot.from || snapshot.warehouseAddress,
    sendStationAddress: snapshot.sendStationAddress || snapshot.to,
    deliveryAddress: snapshot.to || snapshot.sendStationAddress,
    labelUsageType: snapshot.labelUsageType || snapshot.usageType
  }
}
function normalizePrintRecord(record:any){
  const label = recordLabelRow(record)
  return {
    ...record,
    materialCode: previewField(label, 'materialCode', 'warehouseMaterialCode'),
    warehouseAddress: previewField(label, 'warehouseAddress', 'warehouseLocation', 'from'),
    deliveryAddress: previewField(label, 'sendStationAddress', 'deliveryAddress', 'to', 'stationName', 'stationCode'),
    deliveryArea: previewField(label, 'deliveryArea')
  }
}
async function reprintRecord(record:any){
  if (!record?.taskNo || reprintingJobNo.value) return
  reprintingJobNo.value = record.printJobNo || record.taskNo
  try {
    const row = recordLabelRow(record)
    const svgMap = await renderBarcodes([row])
    await printLabelsInBrowser([row], svgMap)
    try {
      await post("/print-jobs", { taskNo:row.taskNo, printerName:(printerName.value.trim() || "浏览器补打"), printType:record.printType || "WAREHOUSE_BARCODE_LABEL", printChannel:"BROWSER" })
      await loadRecords()
    } catch (e:any) {
      ElMessage.warning(e?.response?.data?.message || e?.message || "标签已调起打印，但补打记录保存失败")
      return
    }
    ElMessage.success(`已调起补打：`)
  } catch (e:any) { ElMessage.error(e?.message || "补打标签失败") }
  finally { reprintingJobNo.value = "" }
}
async function loadPending(){
  const first:any = await get('/tasks/printable', { page: 0, size: PAGE_SIZE })
  const rows:any[] = Array.isArray(first?.items) ? [...first.items] : []
  const total = Number(first?.total || rows.length)
  // 兼容尚未重启的旧后端：页面不做分页，但自动取完后端的所有旧分页。
  for (let page = 1; rows.length < total; page++) {
    const next:any = await get('/tasks/printable', { page, size: PAGE_SIZE })
    const items:any[] = Array.isArray(next?.items) ? next.items : []
    if (!items.length) break
    rows.push(...items)
  }
  rawRows.value = rows
}
const filteredRows = computed(() => {
  const tokens = searchKeyword.value.trim().toLowerCase().split(/\s+/).filter(Boolean)
  const rows = tokens.length
    ? rawRows.value.filter(row => tokens.every(t => searchableText(row).includes(t)))
    : rawRows.value
  return sortRows(rows, defaultGroupSort.value)
})
async function runAutoPrint(){
  try {
    const n:any = await post('/print-jobs/auto-print/run')
    ElMessage.success(`定时打印已执行，本轮提交 ${n ?? 0} 条`)
    load()
  } catch (e:any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '定时打印执行失败')
  }
}
async function printPending(row:any){
  if (!row?.taskNo) return
  if (!printerName.value.trim()) { ElMessage.warning('请先填写打印机名称'); return }
  saveRuntimePrinterName(printerName.value)
  await post('/print-jobs', { taskNo: row.taskNo, printerName: printerName.value.trim(), printType: 'WAREHOUSE_BARCODE_LABEL' })
  ElMessage.success(`已提交到本地代理打印队列：${row.taskNo}`)
  previewDialog.value = false
  load()
}
async function batchSubmitPrintJobs(){
  await submitRows(oneFactoryRows(batchRows()), '已提交全部筛选结果')
}
async function submitGroup(group:any){
  if (group.submitting) return
  group.submitting = true
  try {
    await submitRows(group.rows, `已提交${group.category}类别`)
  } finally {
    group.submitting = false
  }
}
async function submitRows(inputRows:any[], successPrefix:string){
  if (!inputRows.length) return
  const printer = printerName.value.trim()
  if (!printer) { ElMessage.warning('请先填写打印机名称，用于本地 exe 代理匹配和打印'); return }
  saveRuntimePrinterName(printer)
  batchSubmitting.value = true
  let ok = 0
  let fail = 0
  try {
    for (const row of [...inputRows]) {
      try {
        await post('/print-jobs', { taskNo: row.taskNo, printerName: printer, printType: 'WAREHOUSE_BARCODE_LABEL' })
        ok++
      } catch {
        fail++
      }
    }
    if (ok) ElMessage.success(`${successPrefix}：${ok} 条${fail ? `，失败 ${fail} 条` : ''}`)
    else if (fail) ElMessage.error(`提交失败 ${fail} 条，请检查后端日志或打印机名称`)
    await load()
  } finally {
    batchSubmitting.value = false
  }
}
async function printGroup(group:any){
  if (group.printing) return
  group.printing = true
  try {
    await printRows(group.rows, `已调起${group.category}类别打印`)
  } finally {
    group.printing = false
  }
}
async function printRows(inputRows:any[], successPrefix:string){
  if (!inputRows.length) return
  batchPrinting.value = true
  const rows = [...inputRows]
  try {
    const svgMap = await renderBarcodes(rows)
    await printLabelsInBrowser(rows, svgMap)
    ElMessage.success(`： 张`)
    for (const row of rows) {
      try { await post("/print-jobs", { taskNo:row.taskNo, printerName:(printerName.value.trim() || "浏览器打印"), printType:"WAREHOUSE_BARCODE_LABEL", printChannel:"BROWSER" }) } catch {}
    }
    await load()
  } catch (e:any) { ElMessage.error(e?.message || "无法调起浏览器打印，请重试") }
  finally { batchPrinting.value = false }
}
async function batchPrint(){
  await printRows(oneFactoryRows(batchRows()), '已调起筛选结果打印')
}
const barcodeSvgCache = new Map<string, string>()
const barcodeSvgInflight = new Map<string, Promise<string>>()
async function getBarcodeSvg(codeValue:any){
  const code = String(codeValue || '').trim()
  if (!code) return ''
  const cached = barcodeSvgCache.get(code)
  if (cached) return cached
  const existing = barcodeSvgInflight.get(code)
  if (existing) return existing
  const request = post('/labels/code/render', { text:code, format:'CODE_128', width:520, height:150, includeText:true })
    .then((result:any) => {
      const svg = String(result?.svg || '')
      if (svg) {
        barcodeSvgCache.set(code, svg)
        if (barcodeSvgCache.size > 200) barcodeSvgCache.delete(barcodeSvgCache.keys().next().value as string)
      }
      return svg
    })
    .finally(() => barcodeSvgInflight.delete(code))
  barcodeSvgInflight.set(code, request)
  return request
}
// 同一仓库代号只请求一次；预览、批量打印及并发调用共享 SVG 缓存。
async function renderBarcodes(rows:any[]){
  const map:Record<string, string> = {}
  await Promise.all(rows.map(async (row) => {
    const code = row.warehouseCode || row.barcodeValue
    if (!code) return
    try {
      const svg = await getBarcodeSvg(code)
      if (svg) map[row.taskNo] = svg
    } catch {}
  }))
  return map
}
async function previewPending(row:any){
  previewRow.value = row
  previewBarcodeSvg.value = ''
  previewDialog.value = true
  const code = row.warehouseCode || row.barcodeValue
  if (code) {
    try {
      previewBarcodeSvg.value = await getBarcodeSvg(code)
    } catch (e:any) {
      ElMessage.error(e?.response?.data?.message || e?.message || '条形码生成失败')
    }
  }
}
function isUrgentTask(row:any){
  const mode = String(row?.deliveryMode || '').toUpperCase()
  const prio = String(row?.priority || '').toUpperCase()
  const usage = String(row?.labelUsageType || '').toUpperCase()
  return mode === 'URGENT' || prio === 'URGENT' || usage === 'SPARE'
}
function groupCategory(row:any){
  const value = groupingMode.value === 'area' ? row?.deliveryArea : stationCategory(row)
  const text = String(value ?? '').trim()
  return text || '未分类'
}
function factoryName(row:any){ return String(row?.factory ?? '').trim() || '未维护工厂' }
function factoryKey(row:any){ return factoryName(row) }
function groupsFor(rows:any[], factory:string){
  const map = new Map<string, any[]>()
  for (const row of rows) { const category=groupCategory(row); if(!map.has(category)) map.set(category,[]); map.get(category)!.push(row) }
  return Array.from(map.entries()).sort(([a],[b])=>a.localeCompare(b,'zh-Hans-CN',{numeric:true})).map(([category,items])=>{ const key=factory+'|'+groupingMode.value+'|'+category; return {key,category,rows:sortRows(items,groupSortMap.value[key] || defaultGroupSort.value),printing:false,submitting:false} })
}
const availableFactories = computed(() => {
  const counts = new Map<string,number>()
  for (const row of filteredRows.value) { const name=factoryName(row); counts.set(name,(counts.get(name)||0)+1) }
  return Array.from(counts.entries())
    .sort(([a],[b])=>a.localeCompare(b,'zh-Hans-CN',{numeric:true}))
    .map(([key,count])=>({key,label:key,count}))
})
const factoryGroups = computed(() => availableFactories.value.map(f => { const items=filteredRows.value.filter(r=>factoryKey(r)===f.key); return {...f,rows:items,groups:groupsFor(items,f.key)} }))
const selectedFactoryLabel = computed(() => availableFactories.value.find(f=>f.key===selectedFactoryKey.value)?.label || '未选择工厂')
const factoryRows = computed(() => filteredRows.value.filter(r=>factoryKey(r)===selectedFactoryKey.value))
const availableAreas = computed(() => {
  const counts = new Map<string,number>()
  for (const row of factoryRows.value) { const area=groupCategory(row); counts.set(area,(counts.get(area)||0)+1) }
  return [{key:ALL_AREAS,label:'全部',count:factoryRows.value.length}, ...Array.from(counts.entries()).sort(([a],[b])=>a.localeCompare(b,'zh-Hans-CN',{numeric:true})).map(([key,count])=>({key,label:key,count}))]
})
const selectedAreaLabel = computed(() => selectedAreaKey.value===ALL_AREAS ? '全部' : selectedAreaKey.value)
const currentGroupKey = computed(() => `${selectedFactoryKey.value}|${groupingMode.value}|${selectedAreaKey.value}`)
const currentRows = computed(() => {
  const rows = selectedAreaKey.value===ALL_AREAS ? factoryRows.value : factoryRows.value.filter(r=>groupCategory(r)===selectedAreaKey.value)
  return sortRows(rows,groupSortMap.value[currentGroupKey.value] || defaultGroupSort.value)
})
function selectFactory(key:string){ selectedFactoryKey.value=key; selectedAreaKey.value=ALL_AREAS }
watch(availableFactories, factories=>{
  if (!factories.some(f=>f.key===selectedFactoryKey.value)) selectedFactoryKey.value=factories[0]?.key || ''
},{immediate:true})
watch(availableAreas, areas=>{
  if (!areas.some(a=>a.key===selectedAreaKey.value)) selectedAreaKey.value=ALL_AREAS
})
function isSelected(row:any){ return selectedTaskNos.value.has(String(row?.taskNo || '')) }
function toggleSelected(row:any, checked:boolean){
  const no=String(row?.taskNo || ''); if(!no) return
  const next=new Set(selectedTaskNos.value)
  if(checked){ const selectedRows=filteredRows.value.filter(r=>next.has(String(r.taskNo))); if(selectedRows.length && factoryKey(selectedRows[0])!==factoryKey(row)){ ElMessage.warning('禁止跨工厂勾选，请先取消当前工厂选择'); return } next.add(no) } else next.delete(no)
  selectedTaskNos.value=next
}
function batchRows(){ const chosen=filteredRows.value.filter(r=>selectedTaskNos.value.has(String(r.taskNo))); return chosen.length ? chosen : filteredRows.value }
watch(filteredRows, visible=>{ const allowed=new Set(visible.map(r=>String(r.taskNo))); selectedTaskNos.value=new Set([...selectedTaskNos.value].filter(no=>allowed.has(no))) })
function oneFactoryRows(rows:any[]){ const keys=new Set(rows.map(factoryKey)); if(keys.size>1){ ElMessage.warning('禁止跨工厂打印，请在单个工厂大类内操作'); return [] } return rows }
function sortRows(list:any[], mode:SortMode){
  return [...list].sort((a,b) => {
    if (mode === 'warehouseAsc' || mode === 'warehouseDesc') {
      const addressA=warehouseAddress(a), addressB=warehouseAddress(b)
      const emptyA=!addressA || addressA==='-', emptyB=!addressB || addressB==='-'
      if (emptyA !== emptyB) return emptyA ? 1 : -1
      const diff = naturalAddressCompare(addressA, addressB)
      if (diff !== 0) return mode === 'warehouseAsc' ? diff : -diff
    }
    if (mode === 'timeAsc' || mode === 'timeDesc') {
      const diff = taskTime(a) - taskTime(b)
      if (diff !== 0) return mode === 'timeAsc' ? diff : -diff
    }
    if (mode === 'alnum') {
      const diff = normalizeAlnum(stationName(a)).localeCompare(normalizeAlnum(stationName(b)), undefined, { numeric:true, sensitivity:'base' })
      if (diff !== 0) return diff
    } else {
      const diff = stationName(a).localeCompare(stationName(b), 'zh-Hans-CN', { numeric:true })
      if (diff !== 0) return diff
    }
    return String(a.taskNo || '').localeCompare(String(b.taskNo || ''))
  })
}
function warehouseAddress(row:any){ return String(previewField(row, 'warehouseAddress', 'warehouseLocation')) }
function naturalAddressCompare(a:string,b:string){ const emptyA=!a||a==='-', emptyB=!b||b==='-'; if(emptyA!==emptyB) return emptyA?1:-1; return a.localeCompare(b,undefined,{numeric:true,sensitivity:'base'}) }
function stationName(row:any){ return String(previewField(row, 'sendStationAddress', 'deliveryAddress', 'stationName', 'stationCode')) }
function stationCategory(row:any){
  const s = stationName(row).trim()
  if (!s || s === '-') return '未分类'
  const token = s.split(/[\s\-_\/\\|,，;；:：]+/).find(Boolean)
  return (token || s).slice(0, 3)
}
function normalizeAlnum(v:any){ return String(v ?? '').toLowerCase().replace(/[^a-z0-9\u4e00-\u9fa5]/g, '') }
function searchableText(row:any){
  return Object.values(row || {})
    .filter(v => v !== null && v !== undefined && typeof v !== 'object')
    .concat([stationName(row), stationCategory(row), isUrgentTask(row) ? '紧急 urgent' : '正常 normal'])
    .join(' ')
    .toLowerCase()
}
function taskTime(row:any){
  const d = new Date(row?.createdAt || row?.receivedAt || row?.acceptedAt || 0)
  return isNaN(d.getTime()) ? 0 : d.getTime()
}
function fmtTime(v:any){
  if (!v) return '-'
  const d = new Date(v)
  if (isNaN(d.getTime())) return String(v)
  return d.toLocaleString()
}
function previewField(row:any, ...keys:string[]){
  for (const k of keys) if (row?.[k] !== undefined && row?.[k] !== null && String(row[k]).trim()) return row[k]
  return '-'
}
async function browserPrint(){
  const row = previewRow.value
  if (!row) return
  await printLabelsInBrowser([row], { [row.taskNo]: previewBarcodeSvg.value })
  ElMessage.success("已调起浏览器打印：80×50mm，边距0，缩放100%")
  try {
    await post("/print-jobs", { taskNo:row.taskNo, printerName:(printerName.value.trim() || "浏览器打印"), printType:"WAREHOUSE_BARCODE_LABEL", printChannel:"BROWSER" })
    load()
  } catch {}
  previewDialog.value = false
}
async function printLabelsInBrowser(rows:any[], barcodes:Record<string,string>){
  browserPrintRows.value = rows
  browserPrintBarcodes.value = barcodes
  await nextTick()
  window.print()
}
const areaDialog = ref(false)
const areaRows = ref<any[]>([])
const knownAreas = ref<string[]>([])
const pickArea = ref("")
async function openAreaDialog(){
  try {
    const res:any = await get("/print-jobs/auto-print/area-schedules")
    knownAreas.value = res?.areas || []
    let parsed:any[] = []
    if (res?.json) { try { parsed = JSON.parse(res.json) || [] } catch { parsed = [] } }
    areaRows.value = parsed.map((r:any) => ({
      area:String(r.area ?? ""), enabled:r.enabled !== false,
      intervalMinutes:Number(r.intervalMinutes) > 0 ? Number(r.intervalMinutes) : 60,
      delayMinutes:Number(r.delayMinutes) >= 0 ? Number(r.delayMinutes) : 0,
      printer:r.printer || "", printType:r.printType || ""
    }))
    areaDialog.value = true
  } catch (e:any) { ElMessage.error(e?.response?.data?.message || e?.message || "读取分区域配置失败") }
}
function addAreaRow(){ areaRows.value.push({ area:'', enabled:true, intervalMinutes:60, delayMinutes:0, printer:'', printType:'' }) }
function onPickArea(v:string){
  if (!v) return
  if (!areaRows.value.some(r => String(r.area) === String(v))) areaRows.value.push({ area:v, enabled:true, intervalMinutes:60, delayMinutes:0, printer:'', printType:'' })
  pickArea.value = ''
}
async function saveAreaSchedules(){
  const seen = new Set<string>()
  for (const r of areaRows.value){
    const a = String(r.area || '').trim()
    if (!a){ ElMessage.warning('每个区域必须填写“配送区域”'); return }
    if (seen.has(a)){ ElMessage.warning(`配送区域重复：${a}`); return }
    seen.add(a)
    if (!(Number(r.intervalMinutes) >= 1)){ ElMessage.warning(`区域 ${a} 的间隔必须是不小于1的分钟数`); return }
  }
  const payload = areaRows.value.map(r => ({
    area: String(r.area).trim(),
    enabled: r.enabled !== false,
    intervalMinutes: Number(r.intervalMinutes),
    delayMinutes: Number(r.delayMinutes) >= 0 ? Number(r.delayMinutes) : 0,
    printer: String(r.printer || '').trim(),
    printType: String(r.printType || '').trim()
  }))
  try {
    await post('/print-jobs/auto-print/area-schedules', { json: JSON.stringify(payload) })
    ElMessage.success('分区域定时设置已保存，下一分钟内生效')
    areaDialog.value = false
  } catch (e:any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '保存失败')
  }
}
onMounted(()=>{
  load()
  setupAutoRefresh()
})
watch(previewDialog, open => {
  if (open) {
    if (refreshTimer) window.clearInterval(refreshTimer)
    refreshTimer = undefined
  } else {
    setupAutoRefresh()
  }
})
onUnmounted(()=>{
  if (refreshTimer) window.clearInterval(refreshTimer)
})
</script>
<style scoped src="./FactoryPrint.css"></style>
