<template>
  <div class="card">
    <div class="toolbar">
      <div>
        <el-select v-model="status" clearable placeholder="容器状态" style="width:190px" @change="load">
          <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
        </el-select>
        <el-button @click="load" style="margin-left:8px">刷新</el-button>
        <el-button type="primary" @click="quickCreate">新增周转盒</el-button>
      </div>
      <div class="hint">仓库统一发盒、现场收货后空盒回收。现场无专用盒时，从这里分配可用周转容器。</div>
    </div>
    <el-table :data="rows" border stripe height="680">
      <el-table-column prop="containerNo" label="容器编号" width="210" />
      <el-table-column prop="containerType" label="容器类型" width="110" />
      <el-table-column prop="boxSize" label="盒型" width="90" />
      <el-table-column prop="taskNo" label="关联任务" width="220" />
      <el-table-column prop="stationCode" label="工位" width="120" />
      <el-table-column prop="warehouseMaterialCode" label="仓库料号" width="140" />
      <el-table-column prop="currentLocation" label="当前位置" />
      <el-table-column prop="status" label="状态" width="130"><template #default="{row}"><el-tag :type="tagType(row.status)">{{row.status}}</el-tag></template></el-table-column>
      <el-table-column prop="cycleCount" label="周转次数" width="100" />
      <el-table-column fixed="right" label="操作" width="260">
        <template #default="{row}">
          <el-button size="small" @click="returnEmpty(row)">空盒回收</el-button>
          <el-button size="small" type="success" @click="back(row)">回仓可用</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { get, post, tagType } from '../api'
import { loadBusinessMeta } from '../meta'
import { ElMessage, ElMessageBox } from 'element-plus'
const rows = ref<any[]>([])
const status = ref('')
const statuses = ref<string[]>([])
const defaultBoxStatus = ref('')
async function load(){ rows.value = await get('/box-pool', status.value ? {status:status.value}:undefined) }
async function quickCreate(){
  const containerType = await ElMessageBox.prompt('请输入容器类型','新增周转盒')
  const boxSize = await ElMessageBox.prompt('请输入盒型，可为空','新增周转盒', { inputValue:'' })
  const currentLocation = await ElMessageBox.prompt('请输入当前位置，可为空','新增周转盒', { inputValue:'' })
  await post('/box-pool', { containerType:containerType.value, boxSize:boxSize.value, currentLocation:currentLocation.value, status: defaultBoxStatus.value })
  ElMessage.success('已新增'); load()
}
async function returnEmpty(row:any){
  const taskNo = await ElMessageBox.prompt('关联任务号，可为空','空盒回收', { inputValue:'' })
  const location = await ElMessageBox.prompt('当前位置，可为空','空盒回收', { inputValue:'' })
  await post(`/box-pool/${row.containerNo}/return-empty?taskNo=${encodeURIComponent(taskNo.value || '')}&location=${encodeURIComponent(location.value || '')}`)
  load()
}
async function back(row:any){ const location = await ElMessageBox.prompt('请输入回仓位置','回仓可用', { inputValue: row.currentLocation || '' }); await post(`/box-pool/${row.containerNo}/back-warehouse?location=${encodeURIComponent(location.value || '')}`); load() }
onMounted(async()=>{ const meta = await loadBusinessMeta(); statuses.value = meta.boxPoolStatuses.map(x => x.value); defaultBoxStatus.value = statuses.value[0] || ''; load() })
</script>
