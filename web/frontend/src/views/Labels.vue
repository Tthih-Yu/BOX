<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="17">
        <div class="card">
          <div class="toolbar">
            <div>
              <el-input v-model="keyword" placeholder="仓库代码/条码/物料名称/仓库地址/工位地址/送料人工号" clearable style="width:440px" />
              <el-button @click="load" style="margin-left:8px">刷新</el-button>
              <el-input v-model="printerName" placeholder="打印机名称" style="width:220px;margin-left:8px" />
            </div>
            <div>
              <el-button type="primary" @click="factoryDialog=true">真实工厂标签建档</el-button>
              <el-button @click="universalDialog=true">扩展标签建档</el-button>
              <el-button @click="dialog=true">批量生成内部标签</el-button>
            </div>
          </div>
         
          <el-table :data="filtered" border stripe height="660" @row-click="select">
            <el-table-column prop="labelType" label="标签类型" width="170" />
            <el-table-column prop="codeCarrierType" label="码制" width="100" />
            <el-table-column prop="warehouseCode" label="仓库代码/条码" width="160" />
            <el-table-column prop="primaryScanValue" label="主扫码值" width="160" />
            <el-table-column prop="materialName" label="物料名称" width="140" />
            <el-table-column prop="labelUsageType" label="使用/备用" width="105" />
            <el-table-column prop="deliveryMode" label="配送方式" width="105" />
            <el-table-column prop="warehouseAddress" label="仓库地址" width="160" />
            <el-table-column prop="sendStationAddress" label="发送工位地址" width="180" />
            <el-table-column prop="boxSize" label="盒子大小" width="100" />
            <el-table-column prop="standardQty" label="数量" width="90" />
            <el-table-column prop="delivererEmployeeNo" label="送料人工号" width="120" />
            <el-table-column prop="templateCode" label="模板" width="130" />
            <el-table-column prop="status" label="状态" width="110"><template #default="{row}"><el-tag :type="tagType(row.status)">{{row.status}}</el-tag></template></el-table-column>
            <el-table-column fixed="right" label="操作" width="220">
              <template #default="{row}">
                <el-button size="small" @click.stop="select(row)">预览</el-button>
                <el-button size="small" @click.stop="print(row,false)">打印</el-button>
                <el-button size="small" @click.stop="print(row,true)">重打</el-button>
                <el-button size="small" type="danger" @click.stop="voidLabel(row)">作废</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
      <el-col :span="7">
        <div class="card">
          <h3>文本/数字生成二维码或条形码</h3>
          <p class="hint">可输入任意文本、数字、物料编码、工位编码、仓库代码，也可从左侧选中标签自动带出主扫码值。生成后可保存 SVG 或直接打印页面。</p>
          <el-form label-width="88px" :model="codeForm">
            <el-form-item label="内容"><el-input v-model="codeForm.text" type="textarea" :rows="3" placeholder="条形码或二维码内容，例如 111 / WH-001 / JSON" /></el-form-item>
            <el-form-item label="码类型">
              <el-radio-group v-model="codeForm.format">
                <el-radio-button label="QR_CODE">二维码</el-radio-button>
                <el-radio-button label="CODE_128">条形码</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-alert v-if="codeForm.format==='CODE_128'" type="warning" :closable="false" title="条形码建议只放英文、数字、仓库代码；中文物料名称、工位地址请使用二维码。" style="margin-bottom:12px" />
            <el-form-item label="尺寸">
              <el-input-number v-model="codeForm.width" :min="80" :max="1200" style="width:120px" />
              <span style="margin:0 8px">×</span>
              <el-input-number v-model="codeForm.height" :min="60" :max="800" style="width:120px" />
            </el-form-item>
            <el-form-item label="显示文本"><el-switch v-model="codeForm.includeText" /></el-form-item>
            <el-divider content-position="left">Android扫码拉动建档</el-divider>
            <el-form-item label="物料名称"><el-input v-model="quickLabel.materialName" placeholder="必填，例如 螺丝M6" /></el-form-item>
            <el-form-item label="物料编码"><el-input v-model="quickLabel.materialCode" placeholder="可选，默认取扫码内容" /></el-form-item>
            <el-form-item label="仓库地址"><el-input v-model="quickLabel.warehouseAddress" placeholder="可选" /></el-form-item>
            <el-form-item label="工位地址"><el-input v-model="quickLabel.sendStationAddress" placeholder="必填，用于送达位置" /></el-form-item>
            <el-form-item label="数量"><el-input-number v-model="quickLabel.standardQty" :min="1" style="width:100%" /></el-form-item>
            <el-form-item label="用途">
              <el-select v-model="quickLabel.labelUsageType" style="width:100%">
                <el-option label="正常使用标签" value="USE" />
                <el-option label="备用/紧急标签" value="SPARE" />
              </el-select>
            </el-form-item>
          </el-form>
          <el-space wrap>
            <el-button type="primary" @click="renderCode">生成码</el-button>
            <el-button type="success" @click="createQuickLabelAndRender">建档并生成码</el-button>
            <el-button @click="fillFromCurrent" :disabled="!current">取选中标签扫码值</el-button>
            <el-button @click="fillJsonFromCurrent" :disabled="!current">按选中标签生成二维码JSON</el-button>
            <el-button @click="downloadCode" :disabled="!codeResult?.svg">下载SVG</el-button>
          </el-space>
          <div v-if="codeResult?.svg" class="code-preview" v-html="codeResult.svg"></div>
        </div>
        <div class="card">
          <h3>标签预览</h3>
          <FactoryLabelPreview v-if="current && current.labelType==='FACTORY_PULL_BARCODE'" :row="current" />
          <GenericPreview v-else-if="current" :row="current" />
          <el-empty v-else description="点击左侧标签查看预览" />
        </div>
      </el-col>
    </el-row>

    <el-dialog v-model="factoryDialog" title="真实工厂物料拉动标签建档" width="820px">
      <el-alert type="info" :closable="false" style="margin-bottom:12px" title="按负责人确认的标签录入：条形码扫出来就是仓库代码。该标签默认不绑定 A/B 双盒，扫码后直接生成补货任务。" />
      <el-form label-width="125px" :model="factoryForm">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="仓库代码"><el-input v-model="factoryForm.warehouseCode" placeholder="条码扫码值" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="物料编码"><el-input v-model="factoryForm.materialCode" placeholder="可空，默认取仓库代码" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="物料名称"><el-input v-model="factoryForm.materialName" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="物料图片URL"><el-input v-model="factoryForm.materialImageUrl" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="仓库地址"><el-input v-model="factoryForm.warehouseAddress" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="发送工位地址"><el-input v-model="factoryForm.sendStationAddress" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="盒子大小"><el-input v-model="factoryForm.boxSize" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="数量"><el-input-number v-model="factoryForm.standardQty" :min="1" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="单位"><el-input v-model="factoryForm.unit" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="送料人工号"><el-input v-model="factoryForm.delivererEmployeeNo" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="产线"><el-input v-model="factoryForm.lineCode" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="工位编码"><el-input v-model="factoryForm.stationCode" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="打印日期"><el-date-picker v-model="factoryForm.printDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="标签用途"><el-select v-model="factoryForm.labelUsageType" clearable><el-option v-for="item in labelUsageTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="配送方式"><el-select v-model="factoryForm.deliveryMode" clearable><el-option v-for="item in deliveryModeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="绑定A/B双盒"><el-switch v-model="factoryForm.bindBox" /></el-form-item></el-col>
          <el-col :span="8" v-if="factoryForm.bindBox"><el-form-item label="A/B盒"><el-select v-model="factoryForm.boxSide"><el-option v-for="item in boxSideOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="factoryDialog=false">取消</el-button><el-button type="primary" @click="createFactoryLabel">建档</el-button></template>
    </el-dialog>

    <el-dialog v-model="universalDialog" title="扩展标签建档 / 二维码 / 旧模板兼容" width="880px">
      <el-form label-width="130px" :model="universalForm">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="标签类型"><el-input v-model="universalForm.labelType" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="码制"><el-input v-model="universalForm.codeCarrierType" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="主扫码值"><el-input v-model="universalForm.primaryScanValue" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="物料编码"><el-input v-model="universalForm.materialCode" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="物料名称"><el-input v-model="universalForm.materialName" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="物料图片URL"><el-input v-model="universalForm.materialImageUrl" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="数量"><el-input-number v-model="universalForm.standardQty" :min="1" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="仓库代码"><el-input v-model="universalForm.warehouseCode" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="仓库地址"><el-input v-model="universalForm.warehouseAddress" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="工位地址"><el-input v-model="universalForm.sendStationAddress" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="标签用途"><el-select v-model="universalForm.labelUsageType" clearable><el-option v-for="item in labelUsageTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="配送方式"><el-select v-model="universalForm.deliveryMode" clearable><el-option v-for="item in deliveryModeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="二维码原文"><el-input v-model="universalForm.rawPayload" type="textarea" :rows="3" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="universalDialog=false">取消</el-button><el-button type="primary" @click="createUniversalLabel">建档</el-button></template>
    </el-dialog>

    <el-dialog v-model="dialog" title="批量生成内部标签" width="520px">
      <el-form label-width="100px" :model="form">
        <el-form-item label="工位"><el-input v-model="form.stationCode" /></el-form-item>
        <el-form-item label="物料"><el-input v-model="form.materialCode" /></el-form-item>
        <el-form-item label="数量"><el-input-number v-model="form.count" :min="1" :max="200" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="generate">生成</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import { get, post, tagType } from '../api'
import { ElMessage } from 'element-plus'
import { runtimePrinterName, saveRuntimePrinterName } from '../config'
import { loadBusinessMeta, type MetaOption } from '../meta'

const rows = ref<any[]>([])
const current = ref<any>(null)
const keyword = ref('')
const dialog = ref(false)
const factoryDialog = ref(false)
const universalDialog = ref(false)
const printerName = ref(runtimePrinterName())
const boxSideOptions = ref<MetaOption[]>([])
const labelUsageTypeOptions = ref<MetaOption[]>([])
const deliveryModeOptions = ref<MetaOption[]>([])
const form = reactive({ stationCode:'', materialCode:'', count:2 })
const codeForm = reactive({ text:'', format:'QR_CODE', width:360, height:140, includeText:true })
const quickLabel = reactive({ materialCode:'', materialName:'', warehouseAddress:'', sendStationAddress:'', standardQty:1, labelUsageType:'USE' })
const codeResult = ref<any>(null)
const factoryForm = reactive({ warehouseCode:'', barcodeValue:'', primaryScanValue:'', materialCode:'', materialName:'', materialImageUrl:'', warehouseAddress:'', sendStationAddress:'', boxSize:'', standardQty:1, unit:'', delivererEmployeeNo:'', lineCode:'', stationCode:'', stationName:'', printDate:'', bindBox:false, boxSide:'', labelUsageType:'', deliveryMode:'' })
const universalForm = reactive({ labelType:'', codeCarrierType:'', templateCode:'', primaryScanValue:'', rawPayload:'', warehouseCode:'', warehouseAddress:'', sendStationAddress:'', materialCode:'', materialName:'', materialImageUrl:'', standardQty:1, bindBox:false, labelUsageType:'', deliveryMode:'' })
const filtered = computed(() => keyword.value ? rows.value.filter(r => JSON.stringify(r).toLowerCase().includes(keyword.value.toLowerCase())) : rows.value)
function fmtDate(v:string){ return v ? String(v).split('-').join('/') : '' }
async function load(){ rows.value = await get('/labels'); if(!current.value && rows.value.length) current.value = rows.value[0] }
function select(row:any){ current.value = row }
function scanKey(row:any){ return row.primaryScanValue || row.warehouseCode || row.barcodeValue || row.kanbanCardNo || row.labelCode }
async function print(row:any,reprint:boolean){ if (printerName.value) saveRuntimePrinterName(printerName.value); await post(`/labels/${encodeURIComponent(scanKey(row))}/print`, { printerName: printerName.value, reprint }); ElMessage.success(reprint ? '重打任务已提交' : '打印任务已提交'); load() }
async function voidLabel(row:any){ await post(`/labels/${encodeURIComponent(scanKey(row))}/void`); ElMessage.success('已作废'); load() }
async function generate(){ await post('/labels/generate', form); dialog.value=false; ElMessage.success('已生成'); load() }
async function createFactoryLabel(){ await post('/labels/factory', factoryForm); factoryDialog.value=false; ElMessage.success('真实工厂标签已建档'); load() }
async function createUniversalLabel(){ await post('/labels/universal', universalForm); universalDialog.value=false; ElMessage.success('扩展标签已建档'); load() }
async function renderCode(){
  try {
    codeResult.value = await post('/labels/code/render', codeForm)
    ElMessage.success('已生成可扫码图形')
  } catch (e:any) {
    codeResult.value = null
    ElMessage.error(e?.response?.data?.message || e?.message || '生成码失败')
  }
}
async function createQuickLabelAndRender(){
  const scanValue = String(codeForm.text || '').trim()
  if (!scanValue) { ElMessage.warning('请先填写扫码内容'); return }
  if (!quickLabel.materialName.trim()) { ElMessage.warning('请填写物料名称'); return }
  if (!quickLabel.sendStationAddress.trim()) { ElMessage.warning('请填写工位地址'); return }
  const isQr = codeForm.format === 'QR_CODE'
  await post('/labels/universal', {
    labelType: 'ANDROID_PULL_LABEL',
    codeCarrierType: isQr ? 'QR_CODE' : 'BARCODE_1D',
    templateCode: 'ANDROID_PULL',
    primaryScanValue: scanValue,
    barcodeValue: isQr ? '' : scanValue,
    rawPayload: isQr ? scanValue : '',
    materialCode: quickLabel.materialCode || scanValue,
    materialName: quickLabel.materialName,
    warehouseMaterialCode: quickLabel.materialCode || scanValue,
    warehouseCode: scanValue,
    warehouseAddress: quickLabel.warehouseAddress,
    warehouseLocation: quickLabel.warehouseAddress,
    sendStationAddress: quickLabel.sendStationAddress,
    deliveryAddress: quickLabel.sendStationAddress,
    standardQty: quickLabel.standardQty,
    labelUsageType: quickLabel.labelUsageType,
    deliveryMode: quickLabel.labelUsageType === 'SPARE' ? 'URGENT' : 'NORMAL',
    bindBox: false,
  })
  await renderCode()
  await load()
  ElMessage.success('已建档，Android 扫这个码可以触发拉动')
}
function fillFromCurrent(){
  if (!current.value) return
  codeForm.text = scanKey(current.value) || current.value.materialCode || current.value.stationCode || ''
  if (!codeForm.text) ElMessage.warning('当前标签没有可生成的扫码值')
}
function fillJsonFromCurrent(){
  if (!current.value) return
  const row = current.value
  codeForm.format = 'QR_CODE'
  codeForm.text = JSON.stringify({
    code: scanKey(row),
    labelCode: row.labelCode,
    warehouseCode: row.warehouseCode,
    materialCode: row.materialCode,
    materialName: row.materialName,
    stationCode: row.stationCode,
    stationName: row.stationName,
    warehouseAddress: row.warehouseAddress || row.warehouseLocation,
    sendStationAddress: row.sendStationAddress || row.deliveryAddress,
    boxSize: row.boxSize,
    qty: row.standardQty,
  })
}
function downloadCode(){
  if (!codeResult.value?.svg) return
  const blob = new Blob([codeResult.value.svg], { type:'image/svg+xml;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${codeResult.value.format || 'code'}-${Date.now()}.svg`
  a.click()
  URL.revokeObjectURL(url)
}

const FactoryLabelPreview = defineComponent({ props:{ row:{type:Object, required:true}}, setup(props:any){ return () => h('div',{class:'factory-label'},[
  h('div',{class:'factory-row barcode-box'},[h('span','条形码'),h('b',props.row.warehouseCode || props.row.primaryScanValue || '-')]),
  h('div',{class:'barcode-text'}, props.row.warehouseCode || props.row.primaryScanValue || '-'),
  h('div',{class:'factory-row'},[h('span','物料名称'),h('b',props.row.materialName || '-')]),
  props.row.materialImageUrl ? h('div',{class:'material-image'},[h('img',{src:props.row.materialImageUrl, alt:'material image'})]) : null,
  h('div',{class:'factory-row'},[h('span','仓库地址'),h('b',props.row.warehouseAddress || props.row.warehouseLocation || '-')]),
  h('div',{class:'factory-row'},[h('span','发送工位地址'),h('b',props.row.sendStationAddress || props.row.deliveryAddress || '-')]),
  h('div',{class:'factory-two'},[h('div',[h('span','盒子大小'),h('b',props.row.boxSize || props.row.containerType || '-')]), h('div',[h('span','数量'),h('b',props.row.standardQty || '-')])]),
  h('div',{class:'factory-row'},[h('span','送料人工号'),h('b',props.row.delivererEmployeeNo || '-')]),
  h('div',{class:'factory-foot'},[h('span','模板：FACTORY_PULL'), h('span',fmtDate(props.row.printDate))])
])}})
const GenericPreview = defineComponent({ props:{ row:{type:Object, required:true}}, setup(props:any){ return () => h('div',{class:'generic-card'},[
  h('h3',props.row.labelType || 'GENERIC_LABEL'),
  ...['primaryScanValue','warehouseCode','barcodeValue','materialCode','materialName','materialImageUrl','labelUsageType','deliveryMode','warehouseAddress','sendStationAddress','boxSize','standardQty','delivererEmployeeNo','deliveryAddress','warehouseLocation','status'].map(k=>h('div',{class:'generic-row'},[h('span',k),h('b',props.row[k] || '-')]))
])}})
onMounted(async()=>{ const meta = await loadBusinessMeta(); boxSideOptions.value = meta.boxSides; labelUsageTypeOptions.value = meta.labelUsageTypes; deliveryModeOptions.value = meta.deliveryModes; load() })
</script>

<style scoped>
.factory-label,.generic-card{border:2px solid #222;background:#f8f8f8;color:#222;padding:10px;max-width:360px;margin:auto;font-family:Arial,'Microsoft YaHei',sans-serif}.code-preview{margin-top:14px;padding:16px;border:1px dashed #cbd5e1;border-radius:12px;background:#fff;text-align:center;overflow:auto}.code-preview :deep(svg){max-width:100%;height:auto}.factory-row{display:grid;grid-template-columns:120px 1fr;min-height:54px;border-bottom:1px solid #222;align-items:center}.factory-row span{font-size:22px;text-align:center;border-right:1px solid #222;height:100%;display:flex;align-items:center;justify-content:center}.factory-row b{font-size:24px;text-align:center;word-break:break-all}.barcode-box{min-height:58px}.barcode-text{min-height:58px;background:#fff;border-bottom:1px solid #222;display:flex;align-items:center;justify-content:center;font-size:22px;letter-spacing:1px;word-break:break-all;padding:0 8px;text-align:center}.factory-two{display:grid;grid-template-columns:1fr 1fr;border-bottom:1px solid #222}.factory-two>div{display:grid;grid-template-columns:1fr 1fr;min-height:64px;align-items:center}.factory-two>div:first-child{border-right:1px solid #222}.factory-two span{font-size:22px;text-align:center;border-right:1px solid #222;height:100%;display:flex;align-items:center;justify-content:center}.factory-two b{text-align:center;font-size:24px}.factory-foot{display:flex;justify-content:space-between;font-size:13px;padding-top:6px}.generic-row{display:grid;grid-template-columns:150px 1fr;border-bottom:1px solid #ccc;padding:6px}.generic-row span{color:#666}.generic-row b{word-break:break-all}.material-image{display:flex;align-items:center;justify-content:center;border-bottom:1px solid #222;background:#fff;padding:8px}.material-image img{max-width:120px;max-height:90px;object-fit:contain}
</style>
