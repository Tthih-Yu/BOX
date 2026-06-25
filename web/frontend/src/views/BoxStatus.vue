<template>
  <div class="card">
    <div class="toolbar"><el-input v-model="keyword" placeholder="按工位/料号/盒号过滤" clearable style="width:320px" /><el-button @click="load">刷新</el-button></div>
    <el-table :data="filtered" border stripe height="680">
      <el-table-column prop="pairCode" label="AB组" width="210" />
      <el-table-column prop="boxSide" label="盒别" width="70" />
      <el-table-column prop="boxCode" label="盒号" width="220" />
      <el-table-column prop="labelCode" label="标签" width="220" />
      <el-table-column prop="warehouseCode" label="仓库代码" width="120" />
      <el-table-column prop="sendStationAddress" label="发送工位地址" width="150" />
      <el-table-column prop="warehouseAddress" label="仓库地址" width="130" />
      <el-table-column prop="boxSize" label="盒子大小" width="90" />
      <el-table-column prop="lineCode" label="产线" width="80" />
      <el-table-column prop="stationCode" label="工位" width="90" />
      <el-table-column prop="materialCode" label="物料" width="110" />
      <el-table-column prop="materialName" label="名称" width="140" />
      <el-table-column prop="standardQty" label="标准盒量" width="100" />
      <el-table-column prop="currentQty" label="当前数量" width="100" />
      <el-table-column prop="status" label="状态" width="130"><template #default="{row}"><el-tag :type="tagType(row.status)">{{row.status}}</el-tag></template></el-table-column>
      <el-table-column prop="healthStatus" label="健康" width="110"><template #default="{row}"><el-tag :type="tagType(row.healthStatus)">{{row.healthStatus}}</el-tag></template></el-table-column>
      <el-table-column prop="lockReason" label="锁定原因" width="220" show-overflow-tooltip />
      <el-table-column prop="lastError" label="最近错误" width="220" show-overflow-tooltip />
      <el-table-column prop="cycleCount" label="循环次数" width="100" />
      <el-table-column prop="lastScanAt" label="最近扫码" width="180" />
      <el-table-column prop="lastReplenishedAt" label="最近补料" width="180" />
    </el-table>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { get, tagType } from '../api'
const rows=ref<any[]>([]), keyword=ref('')
const filtered = computed(()=> keyword.value ? rows.value.filter(r=>JSON.stringify(r).includes(keyword.value)) : rows.value)
async function load(){ rows.value = await get('/boxes') }
onMounted(load)
</script>
