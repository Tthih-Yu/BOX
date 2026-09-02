<template>
  <div class="card">
    <div class="toolbar">
      <el-radio-group v-model="tab" @change="onTabChange">
        <el-radio-button label="scans">扫码日志</el-radio-button>
        <el-radio-button label="tasks">任务日志</el-radio-button>
        <el-radio-button label="prints">打印日志</el-radio-button>
        <el-radio-button label="interfaces">接口日志</el-radio-button>
      </el-radio-group>
      <el-date-picker
        v-model="timeRange"
        type="datetimerange"
        value-format="YYYY-MM-DDTHH:mm:ss"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        range-separator="至"
        :clearable="true"
      />
      <el-button type="primary" @click="applyFilter">筛选</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <div style="flex:1"></div>
      <el-button @click="load" :loading="loading">刷新</el-button>
      <el-button type="primary" :icon="Download" @click="exportXlsx" :loading="exporting">
        导出 Excel
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
    <div class="log-tip">
      {{ timeRange?.length ? '已按所选时间范围查询和导出全部匹配日志。' : '未选择时间范围时，Excel 默认只导出最近 1000 条。' }}
    </div>
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[50, 100, 200, 500]"
      layout="total, sizes, prev, pager, next, jumper"
      @current-change="load"
      @size-change="onPageSizeChange"
    />
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
const timeRange = ref<[string, string] | null>(null)
const page = ref(1)
const pageSize = ref(100)
const total = ref(0)
const cols = computed(() => rows.value[0] ? Object.keys(rows.value[0]).filter(k => k !== 'id') : [])
const canDelete = computed(() => currentRole() === 'ADMIN')

async function load() {
  loading.value = true
  try {
    const result:any = await get(`/logs/${tab.value}`, queryParams(true))
    rows.value = result?.records || []
    total.value = Number(result?.total || 0)
    selectedIds.value = []
    tableRef.value?.clearSelection()
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  page.value = 1
  load()
}

function applyFilter() {
  page.value = 1
  load()
}

function resetFilter() {
  timeRange.value = null
  page.value = 1
  load()
}

function onPageSizeChange() {
  page.value = 1
  load()
}

function queryParams(withPaging:boolean) {
  const params:any = {}
  if (withPaging) {
    params.page = page.value - 1
    params.size = pageSize.value
  }
  if (timeRange.value?.length === 2) {
    params.startAt = timeRange.value[0]
    params.endAt = timeRange.value[1]
  }
  return params
}

function onSelectionChange(rowsSelected: any[]) {
  selectedIds.value = rowsSelected.map(r => r.id).filter(v => v != null)
}

async function exportXlsx() {
  exporting.value = true
  try {
    await downloadBlob(`/logs/${tab.value}/export`, `${TAB_FILE[tab.value]}.xlsx`, queryParams(false))
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
.log-tip {
  color: #64748b;
  font-size: 13px;
  margin: 0 0 12px;
}
.el-pagination {
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
