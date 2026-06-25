<template>
  <div>
    <div class="stat-grid">
      <div class="stat"><span>系统状态</span><b><el-tag size="large" :type="tagType(report.status)">{{ report.status || '-' }}</el-tag></b></div>
      <div class="stat"><span>错误项</span><b>{{ report.errorCount || 0 }}</b></div>
      <div class="stat"><span>预警项</span><b>{{ report.warningCount || 0 }}</b></div>
      <div class="stat"><span>未关闭告警</span><b>{{ report.openAlertCount || 0 }}</b></div>
      <div class="stat"><span>检查时间</span><b style="font-size:15px">{{ report.checkedAt || '-' }}</b></div>
    </div>

    <div class="card">
      <div class="toolbar">
        <div>
          <el-button type="primary" @click="load(false)">立即检查</el-button>
          <el-button type="warning" @click="load(true)">检查并生成告警</el-button>
        </div>
        <div class="hint">检查AB配对、重复未完成任务、库存负数、库存安全线、标签绑定等问题。这里只做检查，不会自动改业务状态。</div>
      </div>
      <el-table :data="report.items || []" border stripe height="520">
        <el-table-column prop="level" label="级别" width="100"><template #default="{row}"><el-tag :type="tagType(row.level)">{{row.level}}</el-tag></template></el-table-column>
        <el-table-column prop="code" label="编码" width="170" />
        <el-table-column prop="title" label="标题" width="220" />
        <el-table-column prop="message" label="说明" min-width="360" />
        <el-table-column prop="detail" label="关联对象" width="220" />
      </el-table>
    </div>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { get, tagType } from '../api'
const report = ref<any>({ items: [] })
async function load(createAlerts = false){ report.value = await get('/health/factory', { createAlerts }) }
onMounted(() => load(false))
</script>
