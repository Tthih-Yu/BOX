<template>
  <div class="card">
    <div class="toolbar">
      <div>
        <el-input v-model="printerName" placeholder="打印机名称" style="width:220px" />
        <el-input v-model="searchKeyword" clearable placeholder="搜索：任务号/工位/物料/仓库/数量等全部关键字" style="width:340px" />
        <el-button type="primary" @click="batchPrint" :disabled="!filteredRows.length" :loading="batchPrinting">一键浏览器打印</el-button>
        <el-button type="success" plain @click="batchSubmitPrintJobs" :disabled="!filteredRows.length || !printerName.trim()" :loading="batchSubmitting">一键提交到本地代理队列</el-button>
        <el-select v-model="defaultGroupSort" style="width:170px">
          <el-option label="分类默认：拼音" value="pinyin" />
          <el-option label="分类默认：字母数字" value="alnum" />
          <el-option label="分类默认：时间早→晚" value="timeAsc" />
          <el-option label="分类默认：时间晚→早" value="timeDesc" />
        </el-select>
        <el-button @click="load">刷新</el-button>
        <el-button type="warning" plain @click="runAutoPrint">立即执行定时打印</el-button>
        <el-button type="success" plain @click="openAreaDialog">定时打印设置(按区域)</el-button>
      </div>
      <div class="hint">定时自动打印支持全局与按区域两种模式，按区域设置优先于全局。</div>
    </div>
    <div class="pending-panel">
      <div class="pending-head">
        <div>
          <h3>待打印补货任务</h3>
        </div>
        <div class="pending-actions">
          <el-tag type="warning" effect="plain">{{ filteredRows.length }} / {{ rawRows.length }} 条待生成</el-tag>
          <el-tag type="info" effect="plain">{{ groupedRows.length }} 个分类</el-tag>
          <el-button size="small" type="primary" plain :disabled="!filteredRows.length" :loading="batchPrinting" @click="batchPrint">一键浏览器打印</el-button>
          <el-button size="small" type="success" plain :disabled="!filteredRows.length || !printerName.trim()" :loading="batchSubmitting" @click="batchSubmitPrintJobs">提交到代理队列</el-button>
          <el-button size="small" @click="loadPending">刷新待打印</el-button>
        </div>
      </div>
      <el-empty v-if="!filteredRows.length" :description="rawRows.length ? '没有匹配搜索条件的待打印标签' : '暂无待打印标签'" />
      <div v-else class="station-groups">
        <section v-for="group in groupedRows" :key="group.category" class="station-group">
          <div class="station-title">
            <div><b>分类：{{ group.category }}</b><span>{{ group.rows.length }} 张标签</span></div>
            <div class="group-sort">
              <small>工位前三字段/字符分类</small>
              <el-select v-model="groupSortMap[group.category]" size="small" style="width:132px">
                <el-option label="拼音" value="pinyin" />
                <el-option label="字母数字" value="alnum" />
                <el-option label="时间早→晚" value="timeAsc" />
                <el-option label="时间晚→早" value="timeDesc" />
              </el-select>
            </div>
          </div>
          <div class="thumb-grid">
        <article v-for="row in group.rows" :key="row.taskNo" class="thumb-card" :class="{ urgent: isUrgentTask(row) }">
          <div class="thumb-tags">
            <el-tag :type="isUrgentTask(row) ? 'danger' : 'success'" effect="dark" size="small">{{ isUrgentTask(row) ? '紧急' : '正常' }}</el-tag>
            <el-tag :type="tagType(row.status)" size="small">{{ row.status }}</el-tag>
          </div>
          <div class="label-thumb" @click="previewPending(row)">
            <div class="thumb-usage" :class="{ urgent: isUrgentTask(row) }">{{ isUrgentTask(row) ? '紧急配送(备用)' : '正常配送(使用)' }}</div>
            <div class="thumb-barcode"><div class="fake-bars"></div><b>{{ previewField(row, 'warehouseCode', 'barcodeValue') }}</b></div>
            <div class="thumb-row material"><span>物料</span><b>{{ previewField(row, 'materialCode', 'materialName') }}</b></div>
            <div class="thumb-row"><span>仓库</span><b>{{ previewField(row, 'warehouseAddress', 'warehouseLocation') }}</b></div>
            <div class="thumb-row station"><span>工位</span><b>{{ stationName(row) }}</b></div>
            <div class="thumb-two"><div><span>盒子</span><b>{{ previewField(row, 'boxSize') }}</b></div><div><span>数量</span><b>{{ previewField(row, 'requestQty') }}</b></div></div>
            <div class="thumb-foot">{{ row.taskNo }}</div>
          </div>
          <div class="thumb-meta"><div><span>申请时间</span><b>{{ fmtTime(row.createdAt || row.receivedAt) }}</b></div><div><span>仓库代号</span><b>{{ row.warehouseCode || '-' }}</b></div></div>
          <div class="thumb-actions">
            <el-button size="small" plain @click="previewPending(row)">预览</el-button>
            <el-button size="small" type="primary" @click="previewPending(row)">提交打印</el-button>
          </div>
        </article>
          </div>
        </section>
      </div>
    </div>

    <div class="records-panel">
      <div class="records-head">
        <div>
          <h3>打印记录</h3>
        </div>
        <div class="records-actions">
          <el-select v-model="recordChannel" size="small" style="width:150px" @change="loadRecords">
            <el-option label="全部方式" value="" />
            <el-option label="浏览器打印" value="BROWSER" />
            <el-option label="代理自动打印" value="AGENT" />
          </el-select>
          <el-select v-model="recordStatus" size="small" style="width:150px" @change="loadRecords">
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
        <el-table-column prop="taskNo" label="任务号" min-width="220" show-overflow-tooltip />
        <el-table-column prop="printJobNo" label="打印作业号" min-width="200" show-overflow-tooltip />
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
      </el-table>
    </div>

    <el-dialog v-model="previewDialog" title="待打印标签预览" width="560px">
      <div v-if="previewRow" class="warehouse-label-preview">
        <div class="label-usage" :class="{ urgent: isUrgentTask(previewRow) }">{{ isUrgentTask(previewRow) ? '紧急配送(备用)' : '正常配送(使用)' }}</div>
        <div class="label-barcode" v-html="previewBarcodeSvg || ''"></div>
        <div class="label-line material"><span>物料名称</span><b>{{ previewField(previewRow, 'materialCode', 'materialName') }}</b></div>
        <div class="label-line addr"><span>仓库地址</span><b>{{ previewField(previewRow, 'warehouseAddress', 'warehouseLocation') }}</b></div>
        <div class="label-line station"><span>发送工位地址</span><b>{{ previewField(previewRow, 'sendStationAddress', 'deliveryAddress', 'stationName', 'stationCode') }}</b></div>
        <div class="label-two">
          <div><span>盒子大小</span><b>{{ previewField(previewRow, 'boxSize') }}</b></div>
          <div><span>数量</span><b>{{ previewField(previewRow, 'requestQty') }}</b></div>
        </div>
        <div class="label-line worker"><span>送料人工号</span><b>{{ previewField(previewRow, 'delivererEmployeeNo') }}</b></div>
        <div class="label-foot">任务号：{{ previewRow.taskNo }}</div>
      </div>
      <el-form label-width="90px" style="margin-top:14px">
        <el-form-item label="打印机"><el-input v-model="printerName" placeholder="后端打印服务用；浏览器打印无需填写" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="previewDialog=false">取消</el-button>
        <el-button type="primary" @click="browserPrint">浏览器打印(本机打印机)</el-button>
        <el-button plain @click="printPending(previewRow)">提交到本地代理队列</el-button>
      </template>
    </el-dialog>

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
import { computed, onMounted, ref } from 'vue'
import { get, post, tagType } from '../api'
import { ElMessage } from 'element-plus'
import { runtimePrinterName, saveRuntimePrinterName } from '../config'
const rawRows = ref<any[]>([])
const printerName = ref(runtimePrinterName())
const previewDialog = ref(false)
const previewRow = ref<any>(null)
const previewBarcodeSvg = ref('')
const searchKeyword = ref('')
const defaultGroupSort = ref<'pinyin'|'alnum'|'timeAsc'|'timeDesc'>('pinyin')
const groupSortMap = ref<Record<string, 'pinyin'|'alnum'|'timeAsc'|'timeDesc'>>({})
const batchPrinting = ref(false)
const batchSubmitting = ref(false)
const records = ref<any[]>([])
const recordChannel = ref('')
const recordStatus = ref('')
const printableStatuses = ['CREATED','ACCEPTED','PICKING','PICKED']
async function load(){ await loadPending(); await loadRecords() }
async function loadRecords(){
  try {
    const list:any[] = await get('/print-jobs', recordStatus.value ? { status: recordStatus.value } : undefined)
    let rows = Array.isArray(list) ? list : []
    if (recordChannel.value) rows = rows.filter(r => String(r.printChannel || '').toUpperCase() === recordChannel.value)
    records.value = rows
  } catch (e:any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '读取打印记录失败')
  }
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
async function loadPending(){
  const list:any[] = await get('/tasks')
  rawRows.value = (Array.isArray(list) ? list : [])
    .filter(t => printableStatuses.includes(String(t.status || '').toUpperCase()))
    .filter(t => !t.printGenerated && !t.printJobNo)
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
  if (!filteredRows.value.length) return
  const printer = printerName.value.trim()
  if (!printer) { ElMessage.warning('请先填写打印机名称，用于本地 exe 代理匹配和打印'); return }
  saveRuntimePrinterName(printer)
  batchSubmitting.value = true
  let ok = 0
  let fail = 0
  try {
    for (const row of [...filteredRows.value]) {
      try {
        await post('/print-jobs', { taskNo: row.taskNo, printerName: printer, printType: 'WAREHOUSE_BARCODE_LABEL' })
        ok++
      } catch {
        fail++
      }
    }
    if (ok) ElMessage.success(`已提交 ${ok} 条到本地代理打印队列${fail ? `，失败 ${fail} 条` : ''}`)
    else if (fail) ElMessage.error(`提交失败 ${fail} 条，请检查后端日志或打印机名称`)
    await load()
  } finally {
    batchSubmitting.value = false
  }
}
async function batchPrint(){
  if (!filteredRows.value.length) return
  batchPrinting.value = true
  try {
    const rows = [...filteredRows.value]
    const w = window.open('', '_blank', 'width=760,height=900')
    if (!w) { ElMessage.warning('浏览器拦截了打印窗口，请允许本站弹出窗口后重试'); return }
    w.document.open()
    w.document.write(batchPrintHtml(rows))
    w.document.close()
    ElMessage.success(`已调起浏览器一键打印：${rows.length} 张，请选择本机斑马打印机`)
    for (const row of rows) {
      try {
        await post('/print-jobs', { taskNo: row.taskNo, printerName: (printerName.value.trim() || '浏览器打印'), printType: 'WAREHOUSE_BARCODE_LABEL', printChannel: 'BROWSER' })
      } catch {}
    }
    await load()
  } finally {
    batchPrinting.value = false
  }
}
async function previewPending(row:any){
  previewRow.value = row
  previewBarcodeSvg.value = ''
  previewDialog.value = true
  const code = row.warehouseCode || row.barcodeValue
  if (code) {
    try {
      const result:any = await post('/labels/code/render', { text: String(code), format: 'CODE_128', width: 760, height: 230, includeText: true })
      previewBarcodeSvg.value = result.svg
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
const groupedRows = computed(() => {
  const map = new Map<string, any[]>()
  for (const row of filteredRows.value) {
    const key = stationCategory(row)
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(row)
  }
  return Array.from(map.entries())
    .sort(([a], [b]) => a.localeCompare(b, 'zh-Hans-CN', { numeric:true }))
    .map(([category, rows]) => ({ category, rows: sortRows(rows, groupSortMap.value[category] || defaultGroupSort.value) }))
})
function sortRows(list:any[], mode:'pinyin'|'alnum'|'timeAsc'|'timeDesc'){
  return [...list].sort((a,b) => {
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
  const r = previewRow.value
  if (!r) return
  const w = window.open('', '_blank', 'width=420,height=760')
  if (!w) { ElMessage.warning('浏览器拦截了打印窗口，请允许本站弹出窗口后重试'); return }
  w.document.open()
  w.document.write(singlePrintHtml(r, previewBarcodeSvg.value || fakeBarcodeHtml(r)))
  w.document.close()
  ElMessage.success('已调起浏览器打印，请选择本机斑马打印机，纸张 35×95mm，缩放100%、边距无')
  try {
    await post('/print-jobs', { taskNo: r.taskNo, printerName: (printerName.value.trim() || '浏览器打印'), printType: 'WAREHOUSE_BARCODE_LABEL', printChannel: 'BROWSER' })
    load()
  } catch {}
  previewDialog.value = false
}
function singlePrintHtml(row:any, barcodeHtml:string){
  return `<!doctype html><html><head><meta charset="utf-8"><title>标签_${escHtml(row.taskNo)}</title>${printStyle()}</head><body>${labelHtml(row, barcodeHtml)}<script>window.onload=function(){setTimeout(function(){window.print()},150)};window.onafterprint=function(){window.close()};<\/script></body></html>`
}
function batchPrintHtml(rows:any[]){
  return `<!doctype html><html><head><meta charset="utf-8"><title>一键打印_${rows.length}张</title>${printStyle()}</head><body>${rows.map(r => labelHtml(r, fakeBarcodeHtml(r))).join('')}<script>window.onload=function(){setTimeout(function(){window.print()},300)};<\/script></body></html>`
}
function labelHtml(r:any, barcodeHtml:string){
  const urgent = isUrgentTask(r)
  return `<div class="lbl"><div class="use ${urgent?'u':''}">${urgent?'紧急配送(备用)':'正常配送(使用)'}</div><div class="bc">${barcodeHtml}</div><div class="row material"><div class="k">物料名称</div><div class="v">${escHtml(previewField(r,'materialCode','materialName'))}</div></div><div class="row addr"><div class="k">仓库地址</div><div class="v">${escHtml(previewField(r,'warehouseAddress','warehouseLocation'))}</div></div><div class="row station"><div class="k">发送工位地址</div><div class="v">${escHtml(previewField(r,'sendStationAddress','deliveryAddress','stationName','stationCode'))}</div></div><div class="two"><div><div class="k">盒子大小</div><div class="v">${escHtml(previewField(r,'boxSize'))}</div></div><div><div class="k">数量</div><div class="v">${escHtml(previewField(r,'requestQty'))}</div></div></div><div class="row worker"><div class="k">送料人工号</div><div class="v">${escHtml(previewField(r,'delivererEmployeeNo'))}</div></div><div class="foot">任务号：${escHtml(r.taskNo)}</div></div>`
}
function fakeBarcodeHtml(row:any){
  return `<div class="print-bars"></div><div class="print-code">${escHtml(previewField(row, 'warehouseCode', 'barcodeValue'))}</div>`
}
function printStyle(){
  return `<style>@page{size:35mm 95mm;margin:0}*{box-sizing:border-box;-webkit-print-color-adjust:exact;print-color-adjust:exact}html,body{margin:0;padding:0}.lbl{width:35mm;height:95mm;border:.4mm solid #000;font-family:'Microsoft YaHei',Arial,sans-serif;display:flex;flex-direction:column;break-after:page;page-break-after:always}.lbl:last-child{break-after:auto;page-break-after:auto}.use{height:7mm;display:flex;align-items:center;justify-content:center;font-size:3.8mm;font-weight:800;border-bottom:.4mm solid #000}.use.u{background:#000;color:#fff}.bc{height:18mm;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:1mm 2mm;border-bottom:.4mm solid #000;overflow:hidden}.bc svg{width:100%;height:15mm}.print-bars{width:100%;height:11mm;background:repeating-linear-gradient(90deg,#000 0 .6mm,#fff .6mm 1mm,#000 1mm 1.25mm,#fff 1.25mm 1.9mm)}.print-code{font-size:2.3mm;font-weight:700;margin-top:.5mm;max-width:100%;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.row{padding:.8mm 2mm;border-bottom:.4mm solid #000;display:flex;flex-direction:column;justify-content:center;overflow:hidden}.material{flex:1.15}.addr{flex:1}.station{flex:1.35}.worker{flex:1}.k{font-size:2.5mm;font-weight:600}.v{font-size:3.5mm;font-weight:800;word-break:break-all;line-height:1.1}.station .v{font-size:3.1mm}.two{display:flex;flex:1.25;border-bottom:.4mm solid #000}.two>div{flex:1;padding:.8mm 2mm;display:flex;flex-direction:column;justify-content:center;overflow:hidden}.two>div:first-child{border-right:.4mm solid #000}.foot{height:6mm;display:flex;align-items:center;padding:0 2mm;font-size:2.6mm;font-weight:600;white-space:nowrap;overflow:hidden}</style>`
}
function escHtml(v:any){ return String(v ?? '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;') }

const areaDialog = ref(false)
const areaRows = ref<any[]>([])
const knownAreas = ref<string[]>([])
const pickArea = ref('')
async function openAreaDialog(){
  try {
    const res:any = await get('/print-jobs/auto-print/area-schedules')
    knownAreas.value = res?.areas || []
    let parsed:any[] = []
    if (res?.json){
      try { parsed = JSON.parse(res.json) || [] } catch { parsed = [] }
    }
    areaRows.value = parsed.map((r:any) => ({
      area: String(r.area ?? ''),
      enabled: r.enabled !== false,
      intervalMinutes: Number(r.intervalMinutes) > 0 ? Number(r.intervalMinutes) : 60,
      delayMinutes: Number(r.delayMinutes) >= 0 ? Number(r.delayMinutes) : 0,
      printer: r.printer || '',
      printType: r.printType || ''
    }))
    areaDialog.value = true
  } catch (e:any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '读取分区域配置失败')
  }
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
onMounted(load)
</script>
<style scoped>
.station-groups{display:flex;flex-direction:column;gap:18px}.station-group{border:1px solid #e2e8f0;border-radius:12px;background:#fff;padding:12px}.station-title{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:12px;padding:8px 10px;background:#f8fafc;border-radius:8px}.station-title b{font-size:15px;color:#0f172a}.station-title span{margin-left:10px;color:#64748b;font-size:12px}.station-title small{color:#94a3b8}
.thumb-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:16px;align-items:start}.thumb-card{background:#fff;border:1px solid #e2e8f0;border-radius:14px;padding:12px;box-shadow:0 8px 24px rgba(15,23,42,.06);transition:.18s}.thumb-card:hover{transform:translateY(-2px);border-color:#93c5fd}.thumb-card.urgent{border-color:#fecaca;background:linear-gradient(180deg,#fff,#fff7ed)}.thumb-tags{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px}.label-thumb{width:132px;height:360px;margin:0 auto;border:2px solid #111;background:#fff;color:#111;display:flex;flex-direction:column;cursor:pointer;font-family:Arial,'Microsoft YaHei',sans-serif}.thumb-usage{height:28px;display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:800;border-bottom:2px solid #111}.thumb-usage.urgent{background:#111;color:#fff}.thumb-barcode{height:66px;border-bottom:2px solid #111;padding:5px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px}.fake-bars{width:100%;height:34px;background:repeating-linear-gradient(90deg,#111 0 2px,#fff 2px 4px,#111 4px 5px,#fff 5px 8px)}.thumb-barcode b{font-size:10px;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.thumb-row{border-bottom:2px solid #111;padding:4px 6px;display:flex;flex-direction:column;justify-content:center;overflow:hidden;flex:1}.thumb-row.material{flex:1.1}.thumb-row.station{flex:1.25}.thumb-row span,.thumb-two span{font-size:10px;color:#333;font-weight:600}.thumb-row b,.thumb-two b{font-size:13px;font-weight:800;line-height:1.1;word-break:break-all;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.thumb-two{height:54px;display:flex;border-bottom:2px solid #111}.thumb-two>div{flex:1;padding:4px 6px;display:flex;flex-direction:column;justify-content:center;gap:2px;overflow:hidden}.thumb-two>div:first-child{border-right:2px solid #111}.thumb-foot{height:24px;display:flex;align-items:center;padding:0 6px;font-size:10px;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.thumb-meta{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:10px}.thumb-meta div{background:#f8fafc;border:1px solid #eef2f7;border-radius:8px;padding:6px}.thumb-meta span{display:block;font-size:11px;color:#64748b}.thumb-meta b{display:block;font-size:13px;color:#0f172a;margin-top:2px;word-break:break-all}.thumb-actions{display:flex;justify-content:center;gap:8px;margin-top:10px}
.pending-panel{border:1px solid #f3d19e;background:#fffbeb;border-radius:12px;padding:14px;margin:12px 0 16px;box-shadow:0 1px 2px rgba(146,64,14,.06)}
.records-panel{border:1px solid #e2e8f0;background:#fff;border-radius:12px;padding:14px;margin:12px 0 16px;box-shadow:0 1px 2px rgba(15,23,42,.04)}
.records-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:10px}
.records-head h3{margin:0;font-size:16px;color:#1f2937;font-weight:800}
.records-head p{margin:4px 0 0;color:#64748b;font-size:12.5px}
.records-actions{display:flex;align-items:center;gap:8px;flex-shrink:0;flex-wrap:wrap}
.pending-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:10px}
.pending-head h3,.section-title h3{margin:0;font-size:16px;color:#1f2937;font-weight:800}
.pending-head p{margin:4px 0 0;color:#92400e;font-size:12.5px}
.pending-actions{display:flex;align-items:center;gap:8px;flex-shrink:0}
.section-title{display:flex;align-items:baseline;gap:10px;margin:8px 0 10px}
.section-title span{font-size:12.5px;color:#64748b}
.warehouse-label-preview{width:245px;height:665px;border:2px solid #111;background:#fff;color:#111;margin:0 auto;font-family:Arial,'Microsoft YaHei',sans-serif;box-sizing:border-box;display:flex;flex-direction:column}.label-usage{height:50px;flex:none;display:flex;align-items:center;justify-content:center;font-size:22px;font-weight:800;letter-spacing:1px;border-bottom:2px solid #111}.label-usage.urgent{background:#111;color:#fff}.label-barcode{height:126px;flex:none;border-bottom:2px solid #111;display:flex;align-items:center;justify-content:center;padding:6px 12px;overflow:hidden}.label-barcode :deep(svg){width:100%;height:105px;display:block}.label-line{display:flex;flex-direction:column;justify-content:center;gap:2px;border-bottom:2px solid #111;min-height:0;overflow:hidden;padding:4px 12px}.label-line.material{flex:1.15}.label-line.addr{flex:1}.label-line.station{flex:1.35}.label-line.worker{flex:1}.label-line span{font-size:13px;font-weight:600;color:#333;line-height:1}.label-line b{font-size:22px;font-weight:800;word-break:break-all;line-height:1.1}.label-line.station b{font-size:19px}.label-two{display:flex;flex:1.25;border-bottom:2px solid #111;min-height:0}.label-two>div{flex:1;display:flex;flex-direction:column;justify-content:center;gap:2px;min-height:0;overflow:hidden;padding:4px 12px}.label-two>div:first-child{border-right:2px solid #111}.label-two span{font-size:13px;font-weight:600;color:#333;line-height:1}.label-two b{font-size:21px;font-weight:800;word-break:break-all;line-height:1.1}.label-foot{height:32px;flex:none;font-size:13px;font-weight:600;color:#333;display:flex;align-items:center;padding:0 12px;overflow:hidden;white-space:nowrap}
.zpl{white-space:pre-wrap;background:#111827;color:#d1d5db;padding:12px;border-radius:8px;margin:0;max-height:280px;overflow:auto}
.pj-detail{display:flex;flex-direction:column;gap:12px;padding:14px 16px;background:linear-gradient(180deg,#f8fafc 0%,#f1f5f9 100%);border:1px solid #e2e8f0;border-radius:10px}
.pj-card{background:#fff;border:1px solid #e2e8f0;border-radius:8px;padding:12px 14px;box-shadow:0 1px 2px rgba(15,23,42,.04)}
.pj-card-title{margin:0 0 10px;font-size:13px;font-weight:700;color:#1e293b;padding-left:8px;border-left:3px solid #2563eb;line-height:1.2}
.pj-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:8px}
.pj-field{display:grid;grid-template-columns:100px 1fr;align-items:center;min-height:30px;border:1px solid #eef2f6;border-radius:6px;overflow:hidden;font-size:12.5px}
.pj-field span{background:#f8fafc;color:#64748b;font-weight:600;padding:6px 8px;height:100%;display:flex;align-items:center;white-space:nowrap;border-right:1px solid #eef2f6}
.pj-field b{padding:6px 10px;color:#0f172a;font-weight:600;word-break:break-all}
</style>