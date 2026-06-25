<template>
  <el-row :gutter="18">
    <el-col :span="10">
      <div class="card">
        <h3>现场扫码工作台</h3>
        <p class="hint">支持三类现场动作：盒子用完拉动、现场收货确认、异常上报。使用标签走正常配送；备用标签或 action=URGENT 会进入紧急配送。</p>
        <el-tabs v-model="mode">
          <el-tab-pane label="用完拉动" name="empty" />
          <el-tab-pane label="收货确认" name="receive" />
          <el-tab-pane label="异常上报" name="exception" />
        </el-tabs>
        <el-form label-width="110px">
          <el-form-item label="扫码内容"><el-input v-model="form.scanCode" clearable placeholder="仓库代码/看板卡/二维码/任务号" autofocus /></el-form-item>
          <el-form-item v-if="mode!=='empty'" label="任务号"><el-input v-model="form.taskNo" clearable placeholder="可选；不填则按扫码内容匹配配送任务" /></el-form-item>
          <el-form-item v-if="mode==='receive'" label="空盒编号"><el-input v-model="form.emptyContainerNo" clearable placeholder="现场放入AGV的空盒编号，可选" /></el-form-item>
          <el-form-item v-if="mode==='exception'" label="异常类型"><el-select v-model="form.exceptionType"><el-option v-for="item in exceptionTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item v-if="mode==='exception'" label="异常说明"><el-input v-model="form.reason" type="textarea" /></el-form-item>
          <el-form-item v-if="mode==='empty'" label="紧急拉动"><el-switch v-model="urgent" active-text="按紧急配送" inactive-text="普通拉动" /></el-form-item>
          <el-form-item label="设备编号"><el-input v-model="form.deviceNo" clearable /></el-form-item>
          <el-form-item v-if="mode==='empty'" label="允许重复"><el-switch v-model="form.allowRepeat" inactive-text="默认拦截重复扫码" /></el-form-item>
        </el-form>
        <el-button type="primary" size="large" class="full" :loading="loading" @click="submit">提交扫码</el-button>
      </div>
      <div class="card">
        <h3>可选扫码值</h3>
        <el-space wrap>
          <el-button v-for="l in scanCandidates" :key="l" @click="form.scanCode=l">{{l}}</el-button>
        </el-space>
        <p class="hint">候选值来自标签管理里的主扫码值、仓库代码、条码值或看板卡号。</p>
      </div>
    </el-col>
    <el-col :span="14">
      <div class="card">
        <h3>扫码结果</h3>
        <div v-if="result">
          <el-descriptions border :column="2">
            <el-descriptions-item label="原始扫码">{{ result.scannedCode }}</el-descriptions-item>
            <el-descriptions-item label="解析标签">{{ result.resolvedLabelCode }}</el-descriptions-item>
            <el-descriptions-item label="任务号">{{ result.taskNo }}</el-descriptions-item>
            <el-descriptions-item label="任务状态"><el-tag :type="tagType(result.taskStatus)">{{ result.taskStatus }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="优先级"><el-tag :type="tagType(result.priority)">{{ result.priority }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="异常事件">{{ result.exceptionNo }}</el-descriptions-item>
            <el-descriptions-item label="AGV任务">{{ result.agvJobNo }}</el-descriptions-item>
            <el-descriptions-item label="打印任务">{{ result.printJobNo }}</el-descriptions-item>
            <el-descriptions-item label="仓库代码">{{ result.warehouseCode }}</el-descriptions-item>
            <el-descriptions-item label="物料号">{{ result.materialCode }}</el-descriptions-item>
            <el-descriptions-item label="物料名称">{{ result.materialName }}</el-descriptions-item>
            <el-descriptions-item label="仓库地址">{{ result.warehouseAddress || result.warehouseLocation }}</el-descriptions-item>
            <el-descriptions-item label="发送工位地址">{{ result.sendStationAddress || result.deliveryAddress }}</el-descriptions-item>
            <el-descriptions-item label="当前盒">{{ result.currentBoxCode }}</el-descriptions-item>
            <el-descriptions-item label="当前盒状态"><el-tag :type="tagType(result.currentBoxStatus)">{{ result.currentBoxStatus }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="备用盒">{{ result.standbyBoxCode }}</el-descriptions-item>
            <el-descriptions-item label="备用盒状态"><el-tag :type="tagType(result.standbyBoxStatus)">{{ result.standbyBoxStatus }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="消息" :span="2">{{ result.message }}</el-descriptions-item>
          </el-descriptions>
          <el-alert v-if="result?.warnings?.length" style="margin-top:12px" type="warning" :title="result.warnings.join('；')" show-icon :closable="false" />
        </div>
        <el-empty v-else description="暂无扫码结果" />
      </div>
    </el-col>
  </el-row>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { get, post, tagType } from '../api'
import { runtimeDeviceNo } from '../config'
import { loadBusinessMeta, type MetaOption } from '../meta'
import { ElMessage } from 'element-plus'
const scanCandidates = ref<string[]>([])
const mode = ref('empty')
const urgent = ref(false)
const exceptionTypeOptions = ref<MetaOption[]>([])
const urgentAction = ref('')
const form = reactive<any>({ scanCode:'', taskNo:'', emptyContainerNo:'', exceptionType:'', reason:'', deviceNo:runtimeDeviceNo(), allowRepeat:false })
const result = ref<any>(null)
const loading = ref(false)
async function loadCandidates(){
  const labels:any[] = await get('/labels')
  scanCandidates.value = labels.flatMap((x:any) => [x.primaryScanValue, x.warehouseCode, x.barcodeValue, x.kanbanCardNo, x.labelCode]).filter(Boolean).slice(0, 20)
}
async function submit(){
  loading.value = true
  try {
    const payload = { ...form, action: urgent.value ? urgentAction.value : undefined }
    const url = mode.value === 'empty' ? '/scan/empty' : (mode.value === 'receive' ? '/scan/receive' : '/scan/exception')
    result.value = await post(url, payload)
    ElMessage.success(result.value?.message || '扫码成功')
  } finally { loading.value = false }
}
onMounted(async()=>{ const meta = await loadBusinessMeta(); exceptionTypeOptions.value = meta.exceptionTypes; urgentAction.value = meta.deliveryModes.find(x => x.label.includes('紧急'))?.value || ''; loadCandidates() })
</script>
