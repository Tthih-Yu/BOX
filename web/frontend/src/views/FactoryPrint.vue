<template>
  <div class="card">
    <div class="toolbar">
      <div>
        <el-input v-model="taskNo" placeholder="任务号" style="width:260px" />
        <el-input v-model="printerName" placeholder="打印机名称" style="width:220px;margin-left:8px" />
        <el-button type="primary" @click="create">提交出货标签/拣料单</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
      <div class="hint">打印作业会提交到后端配置的真实打印服务，接口失败时不进入已下发状态。</div>
    </div>
    <el-table :data="rows" border stripe height="680">
      <el-table-column type="expand"><template #default="{row}"><pre class="zpl">{{row.zplContent}}</pre></template></el-table-column>
      <el-table-column prop="printJobNo" label="打印任务" width="230" />
      <el-table-column prop="taskNo" label="补货任务" width="220" />
      <el-table-column prop="printType" label="类型" width="130" />
      <el-table-column prop="printerName" label="打印机" width="170" />
      <el-table-column prop="status" label="状态" width="120"><template #default="{row}"><el-tag :type="tagType(row.status)">{{row.status}}</el-tag></template></el-table-column>
      <el-table-column prop="externalJobNo" label="外部打印号" width="180" />
      <el-table-column prop="lastError" label="状态说明" min-width="220" />
    </el-table>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { get, post, tagType } from '../api'
import { ElMessage } from 'element-plus'
import { runtimePrinterName, saveRuntimePrinterName } from '../config'
const rows = ref<any[]>([])
const taskNo = ref('')
const printerName = ref(runtimePrinterName())
async function load(){ rows.value = await get('/print-jobs') }
async function create(){ if (printerName.value) saveRuntimePrinterName(printerName.value); await post('/print-jobs', { taskNo: taskNo.value, printerName: printerName.value }); ElMessage.success('打印任务已提交'); taskNo.value=''; load() }
onMounted(load)
</script>
<style scoped>.zpl{white-space:pre-wrap;background:#111827;color:#d1d5db;padding:12px;border-radius:8px}</style>
