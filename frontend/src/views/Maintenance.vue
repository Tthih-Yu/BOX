<template>
  <div>
    <div class="card">
      <div class="toolbar">
        <div>
          <el-button type="warning" @click="recoverStuck">标记卡住任务</el-button>
          <el-button type="primary" @click="rebuildInventory">重算库存可用量</el-button>
          <el-button type="danger" plain @click="lockPairs">标记AB配对异常</el-button>
        </div>
        <div class="hint">维护动作带有保护逻辑，不直接强制改完成状态。卡住任务会提升优先级并生成告警，库存重算只刷新 availableQty。</div>
      </div>
      <el-alert title="建议在低峰期执行维护动作，执行前先查看健康检查结果。所有维护动作都会保留系统日志或告警，方便追溯。" type="info" show-icon :closable="false" />
      <el-table :data="last.messages || []" border style="margin-top:14px">
        <el-table-column label="最近维护结果"><template #default="{row}">{{ row }}</template></el-table-column>
      </el-table>
      <div class="hint" style="margin-top:10px">扫描：{{last.scanned || 0}}，处理：{{last.fixed || 0}}，预警：{{last.warned || 0}}</div>
    </div>

    <div class="card">
      <div class="toolbar">
        <b>未关闭告警</b>
        <el-button @click="loadAlerts">刷新告警</el-button>
      </div>
      <el-table :data="alerts" border stripe height="500">
        <el-table-column prop="level" label="级别" width="90"><template #default="{row}"><el-tag :type="tagType(row.level)">{{row.level}}</el-tag></template></el-table-column>
        <el-table-column prop="alertNo" label="告警号" width="220" />
        <el-table-column prop="category" label="类别" width="120" />
        <el-table-column prop="businessNo" label="业务对象" width="180" />
        <el-table-column prop="title" label="标题" width="180" />
        <el-table-column prop="content" label="内容" min-width="300" />
        <el-table-column fixed="right" label="操作" width="110"><template #default="{row}"><el-button size="small" type="success" @click="close(row)">关闭</el-button></template></el-table-column>
      </el-table>
    </div>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { get, post, tagType } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'
const last = ref<any>({ messages: [] })
const alerts = ref<any[]>([])
async function recoverStuck(){ last.value = await post('/maintenance/recover/stuck-tasks'); ElMessage.success('处理完成'); loadAlerts() }
async function rebuildInventory(){ last.value = await post('/maintenance/recover/inventory-available'); ElMessage.success('处理完成') }
async function lockPairs(){ await ElMessageBox.confirm('该操作会把异常AB配对标记为需要复核，不会自动修复数据。确认执行？','维护确认'); last.value = await post('/maintenance/recover/box-pairs'); loadAlerts() }
async function loadAlerts(){ alerts.value = await get('/maintenance/alerts') }
async function close(row:any){ const r = await ElMessageBox.prompt('关闭备注','关闭告警', { inputValue:'已处理' }); await post(`/maintenance/alerts/${row.id}/close`, { remark:r.value }); loadAlerts() }
onMounted(loadAlerts)
</script>
