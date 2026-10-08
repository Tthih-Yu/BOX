<template>
  <div class="card">
    <div class="toolbar">
      <div>
        <el-input v-model="keyword" clearable placeholder="关键字过滤" style="width:260px" />
        <el-button @click="load" style="margin-left:8px">刷新</el-button>
        <el-button v-if="filterable && Object.keys(columnFilters).length" @click="clearAllFilters" style="margin-left:8px">清除列筛选</el-button>
        <slot name="toolbar-extra" :reload="load" :selected-ids="Array.from(selectedIds)" :clear-selection="clearSelection" :reset-page="resetPage" />
      </div>
      <el-button v-if="canWrite" type="primary" @click="openEdit({})">新增</el-button>
    </div>
    <el-alert v-if="!canWrite" type="info" :closable="false" show-icon title="当前账号仅有查看权限，新增、编辑和删除已隐藏。" style="margin-bottom:12px" />
    <el-table :data="filtered" border stripe height="620">
      <el-table-column v-if="selectable && canWrite" width="55" fixed="left">
        <template #header>
          <el-checkbox :model-value="allSelectedOnPage" :indeterminate="someSelectedOnPage && !allSelectedOnPage" @change="togglePageSelection(Boolean($event))" />
        </template>
        <template #default="{row}">
          <el-checkbox :model-value="selectedIds.has(Number(row.id))" @change="toggleSelection(Number(row.id), Boolean($event))" />
        </template>
      </el-table-column>
      <el-table-column v-for="c in visibleColumns" :key="c.prop" :prop="c.prop" :label="c.label" :width="c.width || 150" show-overflow-tooltip>
        <template v-if="filterable" #header>
          <span>{{ c.label }}</span>
          <el-popover trigger="click" placement="bottom-start" :width="280" @show="openColumnFilter(c.prop)">
            <template #reference><el-button link size="small" :type="columnFilters[c.prop]?.length ? 'primary' : 'info'" @click.stop>筛{{ columnFilters[c.prop]?.length ? `(${columnFilters[c.prop].length})` : '' }}</el-button></template>
            <el-input v-model="filterSearch[c.prop]" clearable placeholder="搜索此列全部数据" size="small" @input="searchColumnOptions(c.prop)" />
            <div class="filter-summary">筛选全部料号 · {{ filterOptions[c.prop]?.length || 0 }} 个匹配值</div>
            <div class="filter-options" :ref="element => setFilterPanel(c.prop, element)" @scroll="onOptionScroll(c.prop, $event)">
              <el-checkbox-group v-model="draftFilters[c.prop]" class="filter-option-list" :style="{ height: `${optionChoices(c.prop).length * OPTION_ROW_HEIGHT}px` }">
                <div v-for="option in visibleOptionChoices(c.prop)" :key="option.value" class="filter-option" :style="{ top: `${option.index * OPTION_ROW_HEIGHT}px` }">
                  <el-checkbox :label="option.value" :title="option.value === EMPTY_FILTER_VALUE ? '(空白)' : option.value">{{ option.value === EMPTY_FILTER_VALUE ? '(空白)' : option.value }}</el-checkbox>
                </div>
              </el-checkbox-group>
              <span v-if="filterLoading[c.prop]">正在读取…</span>
              <span v-else-if="!optionChoices(c.prop).length">没有匹配值</span>
            </div>
            <div class="filter-actions">
              <el-button size="small" @click="clearColumnFilter(c.prop)">清除</el-button>
              <el-button size="small" type="primary" @click="applyColumnFilter(c.prop)">确定</el-button>
            </div>
          </el-popover>
        </template>
        <template #default="{row}">
          <el-tag v-if="c.tag" :type="tagType(String(row[c.prop] || ''))">{{ optionLabel(c, row[c.prop]) }}</el-tag>
          <span v-else-if="c.options">{{ optionLabel(c, row[c.prop]) }}</span>
          <span v-else>{{ row[c.prop] }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="canWrite" fixed="right" label="操作" width="160">
        <template #default="{row}">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)" v-if="deleteUrl">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-if="serverPagination"
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :page-sizes="[20, 50, 100, 200]"
      :total="total"
      layout="total, sizes, prev, pager, next, jumper"
      style="margin-top:12px; justify-content:flex-end"
      @current-change="load"
      @size-change="onPageSizeChange"
    />
    <el-dialog v-model="dialog" :title="form.id ? '编辑' : '新增'" width="760px">
      <el-form label-width="126px">
        <el-row :gutter="12">
          <el-col :span="12" v-for="c in editableColumns" :key="c.prop">
            <el-form-item :label="c.label">
              <el-switch v-if="c.type==='boolean'" v-model="form[c.prop]" />
              <el-input-number v-else-if="c.type==='number'" v-model="form[c.prop]" class="full" />
              <template v-else-if="c.type==='multi-dialog'">
                <el-button class="full area-summary" @click="openMultiDialog(c)">{{ selectedLabels(c).join('、') || '点击选择（可留空=全部）' }}</el-button>
              </template>
              <el-select v-else-if="c.options" v-model="form[c.prop]" class="full" clearable :multiple="Boolean(c.multiple)" collapse-tags collapse-tags-tooltip>
                <el-option
                  v-for="o in c.options"
                  :key="typeof o === 'object' ? o.value : o"
                  :label="typeof o === 'object' ? o.label : o"
                  :value="typeof o === 'object' ? o.value : o"
                />
              </el-select>
              <el-input v-else-if="c.type==='password'" v-model="form[c.prop]" type="password" show-password autocomplete="new-password" placeholder="留空则不修改" />
              <el-input v-else-if="c.type==='textarea'" v-model="form[c.prop]" type="textarea" :rows="2" maxlength="500" show-word-limit />
              <el-input v-else v-model="form[c.prop]" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog=false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="multiDialog" title="选择配送区域" width="620px" append-to-body>
      <el-input v-model="newOption" placeholder="手动新增区域后回车" @keyup.enter="addOption" clearable>
        <template #append><el-button @click="addOption">新增</el-button></template>
      </el-input>
      <el-checkbox-group v-if="multiColumn" v-model="form[multiColumn.prop]" style="margin-top:16px;display:grid;grid-template-columns:repeat(2,1fr);gap:8px">
        <el-checkbox v-for="o in multiColumn.options" :key="typeof o === 'object' ? o.value : o" :label="typeof o === 'object' ? o.value : o">
          {{ typeof o === 'object' ? o.label : o }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer><el-button @click="multiDialog=false">完成</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { get, post, del, tagType } from '../api'
import { ElMessageBox, ElMessage } from 'element-plus'
const props = defineProps<{ listUrl:string, saveUrl:string, deleteUrl?:string, columns:any[], writeRoles?:string[], serverPagination?:boolean, filterable?:boolean, selectable?:boolean }>()
const EMPTY_FILTER_VALUE = '__MAPPING_FILTER_EMPTY__'
const OPTION_ROW_HEIGHT = 32
const rows = ref<any[]>([])
const keyword = ref('')
const page = ref(1)
const pageSize = ref(50)
const total = ref(0)
const selectedIds = ref<Set<number>>(new Set())
const columnFilters = ref<Record<string, string[]>>({})
const draftFilters = ref<Record<string, string[]>>({})
const filterSearch = ref<Record<string, string>>({})
const filterOptions = ref<Record<string, string[]>>({})
const filterLoading = ref<Record<string, boolean>>({})
const filterScrollTop = ref<Record<string, number>>({})
const filterPanels = new Map<string, HTMLElement>()
const filterSequences:Record<string, number> = {}
const filterChoiceLists = computed(() => {
  const fields = new Set([...Object.keys(draftFilters.value), ...Object.keys(filterOptions.value)])
  return Object.fromEntries([...fields].map(field => [field, [...new Set([...(draftFilters.value[field] || []), ...(filterOptions.value[field] || [])])]])) as Record<string, string[]>
})
let filterTimer:number | undefined
let searchTimer:number | undefined
let loadSequence = 0
const dialog = ref(false)
const multiDialog = ref(false)
const multiColumn = ref<any>(null)
const newOption = ref('')
const form = ref<any>({})
const visibleColumns = computed(() => props.columns.filter(c => !c.hiddenInTable && c.type !== 'password'))
const editableColumns = computed(() => props.columns.filter(c => !c.readonly))
const currentRole = computed(() => { try { return JSON.parse(localStorage.getItem('loginUser') || '{}').role || '' } catch { return '' } })
const canWrite = computed(() => {
  if (!props.writeRoles || props.writeRoles.length === 0) return true
  return currentRole.value === 'ADMIN' || props.writeRoles.includes(currentRole.value)
})
const filtered = computed(() => {
  if (props.serverPagination) return rows.value
  if (!keyword.value) return rows.value
  const k = keyword.value.toLowerCase()
  return rows.value.filter(r => JSON.stringify(r).toLowerCase().includes(k))
})
const allSelectedOnPage = computed(() => filtered.value.length > 0 && filtered.value.every(row => selectedIds.value.has(Number(row.id))))
const someSelectedOnPage = computed(() => filtered.value.some(row => selectedIds.value.has(Number(row.id))))
function toggleSelection(id:number, checked:boolean){
  if (!Number.isFinite(id)) return
  const next = new Set(selectedIds.value)
  if (checked) next.add(id)
  else next.delete(id)
  selectedIds.value = next
}
function togglePageSelection(checked:boolean){
  const next = new Set(selectedIds.value)
  for (const row of filtered.value) {
    if (checked) next.add(Number(row.id))
    else next.delete(Number(row.id))
  }
  selectedIds.value = next
}
function clearSelection(){ selectedIds.value = new Set() }
function resetPage(){ page.value = 1; load() }
function optionChoices(field:string){ return filterChoiceLists.value[field] || [] }
function visibleOptionChoices(field:string){
  const choices = optionChoices(field)
  const start = Math.max(0, Math.floor((filterScrollTop.value[field] || 0) / OPTION_ROW_HEIGHT) - 4)
  return choices.slice(start, start + 18).map((value, offset) => ({ value, index:start + offset }))
}
function setFilterPanel(field:string, element:any){
  if (element instanceof HTMLElement) filterPanels.set(field, element)
  else filterPanels.delete(field)
}
function resetOptionScroll(field:string){
  filterScrollTop.value[field] = 0
  const panel = filterPanels.get(field)
  if (panel) panel.scrollTop = 0
}
function onOptionScroll(field:string, event:Event){
  filterScrollTop.value[field] = (event.target as HTMLElement).scrollTop
}
async function loadColumnOptions(field:string){
  const sequence = (filterSequences[field] || 0) + 1
  filterSequences[field] = sequence
  filterLoading.value[field] = true
  try {
    const result:any = await get(`${props.listUrl}/filter-options`, {
      field, keyword:keyword.value.trim(),
      ...(Object.keys(columnFilters.value).length ? { filters:JSON.stringify(columnFilters.value) } : {}),
      search:filterSearch.value[field] || ''
    })
    if (sequence === filterSequences[field]) {
      filterOptions.value[field] = Array.isArray(result) ? result.map(String) : []
      resetOptionScroll(field)
    }
  } finally { if (sequence === filterSequences[field]) filterLoading.value[field] = false }
}
function openColumnFilter(field:string){
  draftFilters.value[field] = [...(columnFilters.value[field] || [])]
  filterSearch.value[field] = ''
  resetOptionScroll(field)
  loadColumnOptions(field)
}
function searchColumnOptions(field:string){
  if (filterTimer) window.clearTimeout(filterTimer)
  filterTimer = window.setTimeout(() => loadColumnOptions(field), 250)
}
function applyColumnFilter(field:string){
  const next = {...columnFilters.value}
  const values = draftFilters.value[field] || []
  if (values.length) next[field] = [...values]
  else delete next[field]
  columnFilters.value = next
  clearSelection()
  resetPage()
}
function clearColumnFilter(field:string){ draftFilters.value[field] = []; applyColumnFilter(field) }
function clearAllFilters(){ columnFilters.value = {}; clearSelection(); resetPage() }
function optionLabel(col: any, val: any): string {
  if (!col.options || val == null) return val ?? ''
  if (Array.isArray(val)) return val.map(v => optionLabel(col, v)).join('、')
  const match = col.options.find((o: any) => (typeof o === 'object' ? o.value : o) === val)
  if (!match) return val
  return typeof match === 'object' ? match.label : match
}
function selectedLabels(col:any){ return (form.value[col.prop] || []).map((v:any) => optionLabel(col, v)) }
function openMultiDialog(col:any){ multiColumn.value = col; multiDialog.value = true; newOption.value = '' }
function addOption(){
  const value = newOption.value.trim()
  if (!value || !multiColumn.value) return
  const col = multiColumn.value
  if (!(col.options || []).some((o:any) => (typeof o === 'object' ? o.value : o) === value)) col.options.push({ label: value, value })
  if (!Array.isArray(form.value[col.prop])) form.value[col.prop] = []
  if (!form.value[col.prop].includes(value)) form.value[col.prop].push(value)
  newOption.value = ''
}
async function load(){
  const sequence = ++loadSequence
  const result:any = await get(props.listUrl, props.serverPagination
    ? { page: page.value - 1, size: pageSize.value, keyword: keyword.value.trim(),
        ...(props.filterable && Object.keys(columnFilters.value).length ? { filters:JSON.stringify(columnFilters.value) } : {}) }
    : undefined)
  if (sequence !== loadSequence) return
  if (props.serverPagination) {
    rows.value = result?.items || []
    total.value = Number(result?.total || 0)
  } else {
    rows.value = result || []
  }
}
function onPageSizeChange(){ page.value = 1; load() }
watch(keyword, () => {
  if (!props.serverPagination) return
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => { clearSelection(); page.value = 1; load() }, 300)
})
defineExpose({ load, clearSelection, resetPage })
function openEdit(row:any){
  if (!canWrite.value) return
  form.value = JSON.parse(JSON.stringify(row || {}))
  if ('password' in form.value) form.value.password = ''
  for (const c of props.columns) if (c.multiple && !Array.isArray(form.value[c.prop])) form.value[c.prop] = []
  dialog.value = true
}
async function save(){ if (!canWrite.value) return; await post(props.saveUrl, form.value); dialog.value=false; ElMessage.success('已保存'); load() }
async function remove(row:any){
  if (!canWrite.value) return
  await ElMessageBox.confirm('确认删除该数据？')
  await del(`${props.deleteUrl}/${row.id}`)
  ElMessage.success('已删除')
  toggleSelection(Number(row.id), false)
  if (props.serverPagination && rows.value.length === 1 && page.value > 1) page.value--
  load()
}
onMounted(load)
onUnmounted(() => { if (searchTimer) window.clearTimeout(searchTimer); if (filterTimer) window.clearTimeout(filterTimer) })
</script>
<style scoped>
.area-summary{display:block;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;text-align:left}
.filter-options{max-height:260px;overflow:auto;margin:8px 0;min-height:48px}
.filter-summary{font-size:12px;color:var(--el-text-color-secondary);margin-top:8px}
.filter-option-list{position:relative}
.filter-option{position:absolute;left:0;right:0;height:32px}
.filter-option :deep(.el-checkbox){max-width:100%;margin-right:0}
.filter-option :deep(.el-checkbox__label){overflow:hidden;text-overflow:ellipsis}
.filter-actions{display:flex;justify-content:flex-end;gap:6px}
</style>
