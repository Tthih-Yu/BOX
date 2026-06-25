<template>
  <div class="card">
    <div class="toolbar">
      <div>
        <el-select v-model="status" clearable placeholder="状态筛选" style="width:190px" @change="load">
          <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
        </el-select>
        <el-button @click="load" style="margin-left:8px">刷新</el-button>
      </div>
      <div class="hint">标准流程：接单 → 开始拣料 → 拣料完成 → 配送 → 到达 → 完成。按钮提交会携带页面状态，后端发现状态已变化会立即拦截，避免重复点击造成卡死。</div>
    </div>
    <el-table :data="rows" border stripe height="680">
      <el-table-column type="expand">
        <template #default="{row}">
          <el-descriptions border :column="3">
            <el-descriptions-item label="任务号">{{row.taskNo}}</el-descriptions-item>
            <el-descriptions-item label="来源标签">{{row.sourceLabelCode}}</el-descriptions-item>
            <el-descriptions-item label="仓库代码">{{row.warehouseCode}}</el-descriptions-item>
            <el-descriptions-item label="条码值">{{row.barcodeValue}}</el-descriptions-item>
            <el-descriptions-item label="盒号">{{row.boxCode || row.containerNo}}</el-descriptions-item>
            <el-descriptions-item label="周转容器">{{row.containerNo}}</el-descriptions-item>
            <el-descriptions-item label="标签类型">{{row.labelUsageType}}</el-descriptions-item>
            <el-descriptions-item label="配送方式">{{row.deliveryMode}}</el-descriptions-item>
            <el-descriptions-item label="单盒序号">{{row.boxSeq && row.boxTotal ? row.boxSeq + '/' + row.boxTotal : ''}}</el-descriptions-item>
            <el-descriptions-item label="打印任务">{{row.printJobNo}}</el-descriptions-item>
            <el-descriptions-item label="AGV任务">{{row.agvJobNo}}</el-descriptions-item>
            <el-descriptions-item label="产线">{{row.lineCode}}</el-descriptions-item>
            <el-descriptions-item label="工位">{{row.stationName}}</el-descriptions-item>
            <el-descriptions-item label="仓库料号">{{row.warehouseMaterialCode}}</el-descriptions-item>
            <el-descriptions-item label="发送工位地址">{{row.sendStationAddress || row.deliveryAddress}}</el-descriptions-item>
            <el-descriptions-item label="仓库地址">{{row.warehouseAddress || row.warehouseLocation}}</el-descriptions-item>
            <el-descriptions-item label="盒子大小">{{row.boxSize}}</el-descriptions-item>
            <el-descriptions-item label="送料人工号">{{row.delivererEmployeeNo}}</el-descriptions-item>
            <el-descriptions-item label="接单人">{{row.acceptedBy}}</el-descriptions-item>
            <el-descriptions-item label="拣料人">{{row.picker}}</el-descriptions-item>
            <el-descriptions-item label="配送人">{{row.deliverer}}</el-descriptions-item>
            <el-descriptions-item label="异常原因" :span="3">{{row.exceptionReason}}</el-descriptions-item>
          </el-descriptions>
        </template>
      </el-table-column>
      <el-table-column prop="taskNo" label="任务号" width="235" />
      <el-table-column prop="warehouseCode" label="仓库代码" width="120" />
      <el-table-column prop="sendStationAddress" label="发送工位地址" width="160" />
      <el-table-column prop="warehouseAddress" label="仓库地址" width="130" />
      <el-table-column prop="boxSize" label="盒子大小" width="90" />
      <el-table-column prop="stationCode" label="工位" width="100" />
      <el-table-column prop="materialCode" label="物料编码" width="120" />
      <el-table-column prop="materialName" label="物料名称" width="130" />
      <el-table-column prop="requestQty" label="需求数量" width="100" />
      <el-table-column prop="priority" label="优先级" width="95"><template #default="{row}"><el-tag :type="tagType(row.priority)">{{row.priority}}</el-tag></template></el-table-column>
      <el-table-column prop="labelUsageType" label="标签" width="95" />
      <el-table-column prop="deliveryMode" label="配送" width="95" />
      <el-table-column prop="containerNo" label="周转容器" width="145" />
      <el-table-column prop="boxSeq" label="盒序" width="75"><template #default="{row}">{{row.boxSeq && row.boxTotal ? row.boxSeq + '/' + row.boxTotal : ''}}</template></el-table-column>
      <el-table-column prop="status" label="状态" width="120"><template #default="{row}"><el-tag :type="tagType(row.status)">{{row.status}}</el-tag></template></el-table-column>
      <el-table-column prop="deadlineAt" label="截止时间" width="180" />
      <el-table-column fixed="right" label="操作" width="380">
        <template #default="{row}">
          <el-button v-if="canDo(row,'accept')" size="small" @click="act(row,'accept')">接单</el-button>
          <el-button v-if="canDo(row,'startPick')" size="small" @click="act(row,'startPick')">开始拣料</el-button>
          <el-button v-if="canDo(row,'picked')" size="small" @click="act(row,'picked')">拣料完成</el-button>
          <el-button v-if="canDo(row,'deliver')" size="small" @click="act(row,'deliver')">配送</el-button>
          <el-button v-if="canDo(row,'arrive')" size="small" @click="act(row,'arrive')">到达</el-button>
          <el-button v-if="canDo(row,'complete')" size="small" type="success" @click="act(row,'complete')">完成</el-button>
          <el-button v-if="canDo(row,'receive')" size="small" type="success" plain @click="receive(row)">现场收货</el-button>
          <el-button v-if="canDo(row,'returnEmptyBox')" size="small" type="warning" plain @click="returnEmpty(row)">空盒回收</el-button>
          <el-button v-if="canDo(row,'exception')" size="small" type="danger" @click="exception(row)">异常</el-button>
          <el-button v-if="canDo(row,'retry')" size="small" type="warning" @click="act(row,'retry')">异常重试</el-button>
          <el-button v-if="canDo(row,'forceComplete')" size="small" type="danger" plain @click="forceComplete(row)">强制完成</el-button>
          <el-tag v-if="!hasAnyAction(row)" type="info">无可用操作</el-tag>
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
import { canTaskAction, TASK_TABLE_ACTIONS } from '../permissions'
const rows = ref<any[]>([])
const status = ref('')
const statuses = ref<string[]>([])
async function load(){ rows.value = await get('/tasks', status.value ? {status:status.value}:undefined) }
function canDo(row:any, action:string){ return canTaskAction(action, row.status) }
function hasAnyAction(row:any){ return TASK_TABLE_ACTIONS.some(a => canDo(row, a)) }
async function act(row:any, action:string){ await post(`/tasks/${row.taskNo}/${action}`, { expectedStatus: row.status }); ElMessage.success('操作成功'); load() }
async function exception(row:any){ const reason = await ElMessageBox.prompt('请输入异常原因','异常处理'); await post(`/tasks/${row.taskNo}/exception`, { exceptionReason:reason.value, expectedStatus: row.status }); load() }
async function forceComplete(row:any){ await ElMessageBox.confirm('强制完成会跳过部分状态校验，只建议在现场已确认补料完成时使用。确认继续？','强制完成确认'); await post(`/tasks/${row.taskNo}/forceComplete`, { expectedStatus: row.status, force:true }); load() }
async function receive(row:any){ const empty = await ElMessageBox.prompt('现场收货确认，可填写空盒编号','收货确认'); await post(`/tasks/${row.taskNo}/receive`, { receiveScanCode:row.taskNo, emptyContainerNo:empty.value, expectedStatus:row.status }); load() }
async function returnEmpty(row:any){ const empty = await ElMessageBox.prompt('请输入空盒编号','空盒回收'); await post(`/tasks/${row.taskNo}/returnEmptyBox`, { emptyContainerNo:empty.value, expectedStatus:row.status }); load() }
onMounted(async()=>{ const meta = await loadBusinessMeta(); statuses.value = meta.taskStatuses.map(x => x.value); load() })
</script>
