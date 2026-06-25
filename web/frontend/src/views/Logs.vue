<template>
  <div class="card">
    <div class="toolbar">
      <el-radio-group v-model="tab" @change="load">
        <el-radio-button label="scans">扫码日志</el-radio-button>
        <el-radio-button label="tasks">任务日志</el-radio-button>
        <el-radio-button label="prints">打印日志</el-radio-button>
        <el-radio-button label="interfaces">接口日志</el-radio-button>
      </el-radio-group>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table :data="rows" border stripe height="690">
      <el-table-column v-for="c in cols" :key="c" :prop="c" :label="c" min-width="150" show-overflow-tooltip />
    </el-table>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { get } from '../api'
const tab=ref('scans'), rows=ref<any[]>([])
const cols=computed(()=> rows.value[0] ? Object.keys(rows.value[0]) : [])
async function load(){ rows.value = await get(`/logs/${tab.value}`) }
onMounted(load)
</script>
