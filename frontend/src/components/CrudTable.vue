<template>
  <div class="card">
    <div class="toolbar">
      <div>
        <el-input v-model="keyword" clearable placeholder="关键字过滤" style="width:260px" />
        <el-button @click="load" style="margin-left:8px">刷新</el-button>
        <slot name="toolbar-extra" :reload="load" />
      </div>
      <el-button v-if="canWrite" type="primary" @click="openEdit({})">新增</el-button>
    </div>
    <el-alert v-if="!canWrite" type="info" :closable="false" show-icon title="当前账号仅有查看权限，新增、编辑和删除已隐藏。" style="margin-bottom:12px" />
    <el-table :data="filtered" border stripe height="620">
      <el-table-column v-for="c in visibleColumns" :key="c.prop" :prop="c.prop" :label="c.label" :width="c.width || 150" show-overflow-tooltip>
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
const props = defineProps<{ listUrl:string, saveUrl:string, deleteUrl?:string, columns:any[], writeRoles?:string[], serverPagination?:boolean }>()
const rows = ref<any[]>([])
const keyword = ref('')
const page = ref(1)
const pageSize = ref(50)
const total = ref(0)
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
    ? { page: page.value - 1, size: pageSize.value, keyword: keyword.value.trim() }
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
  searchTimer = window.setTimeout(() => { page.value = 1; load() }, 300)
})
defineExpose({ load })
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
  if (props.serverPagination && rows.value.length === 1 && page.value > 1) page.value--
  load()
}
onMounted(load)
onUnmounted(() => { if (searchTimer) window.clearTimeout(searchTimer) })
</script>
<style scoped>
.area-summary{display:block;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;text-align:left}
</style>
