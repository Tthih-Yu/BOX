<template>
  <div>
    <div class="stat-grid">
      <div class="stat"><span>物料主数据</span><b>{{ s.materials }}</b></div>
      <div class="stat"><span>工位用料</span><b>{{ s.stationMaterials }}</b></div>
      <div class="stat"><span>当前补货任务</span><b>{{ s.tasksCreated + s.tasksProcessing }}</b></div>
      <div class="stat"><span>异常任务</span><b>{{ s.tasksException }}</b></div>
      <div class="stat"><span>低库存物料</span><b>{{ s.lowStockMaterials }}</b></div>
    </div>
    <el-row :gutter="16">
      <el-col :span="12">
        <div class="card"><h3>任务状态分布</h3><div ref="taskChart" style="height:320px"></div></div>
      </el-col>
      <el-col :span="12">
        <div class="card"><h3>盒子状态分布</h3><div ref="boxChart" style="height:320px"></div></div>
      </el-col>
    </el-row>
    <el-row :gutter="16">
      <el-col :span="15">
        <div class="card">
          <div class="toolbar"><h3>最新补货任务</h3><el-button @click="load">刷新</el-button></div>
          <el-table :data="data.latestTasks" border height="320">
            <el-table-column prop="taskNo" label="任务号" width="230" />
            <el-table-column prop="stationCode" label="工位" />
            <el-table-column prop="materialCode" label="物料" />
            <el-table-column prop="requestQty" label="数量" />
            <el-table-column prop="status" label="状态"><template #default="{row}"><el-tag :type="tagType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="180" />
          </el-table>
        </div>
      </el-col>
      <el-col :span="9">
        <div class="card">
          <h3>库存预警</h3>
          <el-table :data="data.warnings" border height="320">
            <el-table-column prop="warehouseMaterialCode" label="仓库料号" />
            <el-table-column prop="availableQty" label="可用库存" />
            <el-table-column prop="safetyStock" label="安全库存" />
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref, nextTick } from 'vue'
import * as echarts from 'echarts'
import { get, tagType } from '../api'
const data = ref<any>({ summary:{}, taskStatus:[], boxStatus:[], latestTasks:[], warnings:[] })
const s = ref<any>({})
const taskChart = ref()
const boxChart = ref()
async function load(){
  data.value = await get('/dashboard')
  s.value = data.value.summary || {}
  await nextTick()
  render(taskChart.value, data.value.taskStatus || [])
  render(boxChart.value, data.value.boxStatus || [])
}
function render(el:any, items:any[]){
  const chart = echarts.init(el)
  chart.setOption({ tooltip:{}, xAxis:{type:'category',data:items.map(i=>i.name)}, yAxis:{type:'value'}, series:[{type:'bar', data:items.map(i=>i.value)}] })
}
onMounted(load)
</script>
