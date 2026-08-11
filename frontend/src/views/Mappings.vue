<template>
  <CrudTable
    ref="tableRef"
    list-url="/mappings"
    save-url="/mappings"
    delete-url="/mappings"
    :columns="columns"
    :write-roles="ROLE_SETS.planner"
  >
    <template #toolbar-extra="{ reload }">
      <ImportButton
        type="mappings"
        template-url="/import_templates/mappings.csv"
        :write-roles="ROLE_SETS.mappingImport"
        @imported="reload"
      />
      <ImportButton
        v-if="canWrite"
        type="mappings"
        label="覆盖上传"
        :overwrite="true"
        :template-url="''"
        :write-roles="ROLE_SETS.planner"
        :before-pick="confirmOverwrite"
        @imported="reload"
      />
      <el-button
        type="primary"
        :icon="Download"
        :loading="exporting"
        style="margin-left:8px"
        @click="exportAll"
      >导出</el-button>
      <el-dropdown
        v-if="canWrite"
        trigger="click"
        style="margin-left:8px"
        @command="onBatchDelete"
      >
        <el-button type="danger" :icon="Delete">
          批量删除<el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="ALL">删除全部数据</el-dropdown-item>
            <el-dropdown-item command="AREA">按配送区域删除</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </template>
  </CrudTable>

  <el-dialog v-model="areaDialog" title="按配送区域删除" width="420px">
    <el-form label-width="96px">
      <el-form-item label="配送区域">
        <el-select v-model="areaValue" filterable allow-create default-first-option placeholder="选择或输入配送区域" style="width:100%">
          <el-option v-for="a in areaOptions" :key="a" :label="a" :value="a" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="areaDialog=false">取消</el-button>
      <el-button type="danger" @click="confirmAreaDelete">确认删除</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Delete, ArrowDown } from '@element-plus/icons-vue'
import CrudTable from '../components/CrudTable.vue'
import ImportButton from '../components/ImportButton.vue'
import { ROLE_SETS } from '../permissions'
import { get, del, downloadBlob } from '../api'
import { hasAnyRole } from '../auth'
import { doubleCountdownConfirm } from '../confirm'

const tableRef = ref<any>(null)
const exporting = ref(false)
const areaDialog = ref(false)
const areaValue = ref('')
const areaOptions = ref<string[]>([])

const canWrite = computed(() => hasAnyRole(ROLE_SETS.planner as any))

function reloadTable() {
  tableRef.value?.load?.()
}

async function exportAll() {
  try {
    exporting.value = true
    await downloadBlob('/mappings/export', 'mappings.xlsx')
  } finally {
    exporting.value = false
  }
}

async function confirmOverwrite(): Promise<boolean> {
  try {
    await doubleCountdownConfirm('覆盖上传', '先清空全部料号映射，再导入新数据')
    return true
  } catch {
    return false
  }
}

async function onBatchDelete(command: string) {
  if (command === 'ALL') {
    try {
      await doubleCountdownConfirm('批量删除全部', '将删除全部料号映射数据')
    } catch { return }
    const res: any = await del('/mappings', { scope: 'ALL' })
    ElMessage.success(`已删除 ${res?.deleted ?? 0} 条数据`)
    reloadTable()
  } else if (command === 'AREA') {
    await loadAreaOptions()
    areaValue.value = ''
    areaDialog.value = true
  }
}

async function loadAreaOptions() {
  try {
    const rows: any[] = await get('/mappings')
    const set = new Set<string>()
    rows.forEach(r => { if (r.deliveryArea != null && r.deliveryArea !== '') set.add(String(r.deliveryArea)) })
    areaOptions.value = Array.from(set).sort()
  } catch {
    areaOptions.value = []
  }
}

async function confirmAreaDelete() {
  const area = (areaValue.value || '').trim()
  if (!area) { ElMessage.warning('请先选择或输入配送区域'); return }
  try {
    await doubleCountdownConfirm('按区域批量删除', `将删除配送区域「${area}」的全部数据`)
  } catch { return }
  const res: any = await del('/mappings', { scope: 'AREA', deliveryArea: area })
  ElMessage.success(`已删除 ${res?.deleted ?? 0} 条数据`)
  areaDialog.value = false
  reloadTable()
}

const columns = [
  { prop:'mappingOrder', label:'序号', type:'number', width:90 },
  { prop:'lineMaterialCode', label:'物料号', width:160 },
  { prop:'warehouseCode', label:'仓库代号', width:160 },
  { prop:'boxSize', label:'盒子大小', width:120 },
  { prop:'quantity', label:'数量', type:'number', width:110 },
  { prop:'singleUnitUsage', label:'单根用量', type:'number', width:110 },
  { prop:'deliveryType', label:'用途', options:[{label:'使用（正常）',value:'NORMAL'},{label:'备用（紧急）',value:'URGENT'}], width:120 },
  { prop:'warehouseLocation', label:'仓库位置', width:160 },
  { prop:'deliveryAddress', label:'总装地址', width:200 },
  { prop:'deliveryArea', label:'配送区域', width:110 },
  { prop:'id', label:'ID', readonly:true, hiddenInTable:true },
  { prop:'enabled', label:'启用', type:'boolean', hiddenInTable:true },
  { prop:'warehouseMaterialCode', label:'兼容仓库料号', hiddenInTable:true },
  { prop:'description', label:'描述', hiddenInTable:true },
  { prop:'remark', label:'备注', hiddenInTable:true }
]
</script>
