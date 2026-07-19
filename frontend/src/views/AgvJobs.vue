<template>
  <div class="card">
    <div class="toolbar">
      <div>
        <el-input v-model="form.taskNo" placeholder="任务号" style="width:230px" />
        <el-input v-model="form.containerNo" placeholder="容器编号" style="width:160px;margin-left:8px" />
        <el-button v-if="canAgvDispatch()" type="primary" @click="dispatch">下发AGV</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
      <div class="hint">AGV状态由真实调度系统通过回调接口更新。</div>
    </div>
    <el-table :data="rows" border stripe height="680">
      <el-table-column prop="agvJobNo" label="AGV任务" width="230" />
      <el-table-column prop="externalJobNo" label="外部任务号" width="180" />
      <el-table-column prop="taskNo" label="补货任务" width="220" />
      <el-table-column prop="jobType" label="类型" width="150" />
      <el-table-column prop="containerNo" label="容器" width="160" />
      <el-table-column prop="fromLocation" label="起点" />
      <el-table-column prop="toLocation" label="终点" />
      <el-table-column prop="priority" label="优先级" width="100" />
      <el-table-column prop="status" label="状态" width="120"><template #default="{row}"><el-tag :type="tagType(row.status)">{{row.status}}</el-tag></template></el-table-column>
      <el-table-column prop="responsePayload" label="接口返回" min-width="220" show-overflow-tooltip />
      <el-table-column prop="lastError" label="异常说明" min-width="220" show-overflow-tooltip />
    </el-table>
  </div>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { get, post, tagType } from '../api'
import { canAgvDispatch } from '../permissions'
import { ElMessage } from 'element-plus'
const rows = ref<any[]>([])
const form = reactive({ taskNo:'', containerNo:'' })
async function load(){ rows.value = await get('/agv-jobs') }
async function dispatch(){ await post('/agv-jobs/dispatch', { ...form }); ElMessage.success('AGV任务已下发'); load() }
onMounted(load)
</script>
