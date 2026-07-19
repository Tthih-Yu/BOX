<template>
  <div class="card">
    <div class="toolbar">
      <el-radio-group v-model="tab" @change="onTabChange">
        <el-radio-button label="scans">扫码日志</el-radio-button>
        <el-radio-button label="tasks">任务日志</el-radio-button>
        <el-radio-button label="prints">打印日志</el-radio-button>
        <el-radio-button label="interfaces">接口日志</el-radio-button>
      </el-radio-group>
      <div style="flex:1"></div>
      <el-button @click="load" :loading="loading">刷新</el-button>
      <el-button type="primary" :icon="Download" @click="exportXlsx" :loading="exporting">
        导出 Excel{{ selectedIds.length ? `（已选 ${selectedIds.length}）` : '' }}
      </el-button>
      <el-button
        type="danger"
        :icon="Delete"
        :disabled="!selectedIds.length || !canDelete"
        @click="removeSelected"
      >批量删除</el-button>
      <el-button
        type="danger"
        plain
        :disabled="!rows.length || !canDelete"
        @click="removeAll"
      >清空本表</el-button>
    </div>
    <el-table
      ref="tableRef"
      :data="rows"
      border
      stripe
      height="650"
      v-loading="loading"
      row-key="id"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="48" :reserve-selection="true" />
      <el-table-column
        v-for="c in cols"
        :key="c"
        :prop="c"
        :label="c"
        min-width="150"
        show-overflow-tooltip
      />
      <el-table-column label="操作" width="100" fixed="right" v-if="canDelete">
        <template #default="{ row }">
          <el-button type="danger" link size="small" @click="removeOne(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox, type TableInstance } from 'element-plus'
import { Delete, Download } from '@element-plus/icons-vue'
import { api, downloadBlob, get } from '../api'
import { currentRole } from '../auth'

type LogTab = 'scans' | 'tasks' | 'prints' | 'interfaces'
const TAB_LABEL: Record<LogTab, string> = {
  scans: '扫码日志',
  tasks: '任务日志',
  prints: '打印日志',
  interfaces: '接口日志'
}
const TAB_FILE: Record<LogTab, string> = {
  scans: 'scan-logs',
  tasks: 'task-logs',
  prints: 'print-logs',
  interfaces: 'interface-logs'
}

const tab = ref<LogTab>('scans')
const rows = ref<any[]>([])
const loading = ref(false)
const exporting = ref(false)
const selectedIds = ref<number[]>([])
const tableRef = ref<TableInstance>()
const cols = computed(() => rows.value[0] ? Object.keys(rows.value[0]).filter(k => k !== 'id') : [])
const canDelete = computed(() => currentRole() === 'ADMIN')

async function load() {
  loading.value = true
  try {
    rows.value = await get(`/logs/${tab.value}`)
    selectedIds.value = []
    tableRef.value?.clearSelection()
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  load()
}

function onSelectionChange(rowsSelected: any[]) {
  selectedIds.value = rowsSelected.map(r => r.id).filter(v => v != null)
}

async function exportXlsx() {
  exporting.value = true
  try {
    await downloadBlob(`/logs/${tab.value}/export`, `${TAB_FILE[tab.value]}.xlsx`)
    ElMessage.success(`${TAB_LABEL[tab.value]} 已导出`)
  } catch (e:any) {
    if (e?.message) ElMessage.error(e.message)
  } finally {
    exporting.value = false
  }
}

async function removeSelected() {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(
      `确定删除 ${TAB_LABEL[tab.value]} 选中的 ${selectedIds.value.length} 条记录吗？此操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await api.delete(`/logs/${tab.value}`, { data: { ids: selectedIds.value } })
  ElMessage.success(`已删除 ${selectedIds.value.length} 条`)
  await load()
}

async function removeOne(row: any) {
  if (row?.id == null) return
  try {
    await ElMessageBox.confirm(
      `确定删除这条 ${TAB_LABEL[tab.value]} 记录吗？此操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await api.delete(`/logs/${tab.value}`, { data: { ids: [row.id] } })
  ElMessage.success('已删除')
  await load()
}

async function removeAll() {
  if (!rows.value.length) return
  try {
    await ElMessageBox.confirm(
      `确定清空当前 ${TAB_LABEL[tab.value]} 的全部数据吗？该操作会删除整张日志表，且不可恢复。`,
      '危险操作确认',
      { type: 'error', confirmButtonText: '我已了解，全部删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  const res:any = await api.delete(`/logs/${tab.value}`, { data: { all: true } })
  ElMessage.success(`已清空 ${res?.deleted ?? 0} 条记录`)
  await load()
}

onMounted(load)
</script>
<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
</style>
