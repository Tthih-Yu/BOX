<template>
  <div class="card">
    <div class="toolbar">
      <div>
        <el-input v-model="keyword" clearable placeholder="关键字过滤" style="width:260px" />
        <el-button @click="load" style="margin-left:8px">刷新</el-button>
      </div>
      <el-button v-if="canWrite" type="primary" @click="openEdit({})">新增</el-button>
    </div>
    <el-alert v-if="!canWrite" type="info" :closable="false" show-icon title="当前账号仅有查看权限，新增、编辑和删除已隐藏。" style="margin-bottom:12px" />
    <el-table :data="filtered" border stripe height="620">
      <el-table-column v-for="c in visibleColumns" :key="c.prop" :prop="c.prop" :label="c.label" :width="c.width || 150" show-overflow-tooltip>
        <template #default="{row}" v-if="c.tag">
          <el-tag :type="tagType(String(row[c.prop] || ''))">{{ row[c.prop] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canWrite" fixed="right" label="操作" width="160">
        <template #default="{row}">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)" v-if="deleteUrl">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="dialog" :title="form.id ? '编辑' : '新增'" width="760px">
      <el-form label-width="126px">
        <el-row :gutter="12">
          <el-col :span="12" v-for="c in editableColumns" :key="c.prop">
            <el-form-item :label="c.label">
              <el-switch v-if="c.type==='boolean'" v-model="form[c.prop]" />
              <el-input-number v-else-if="c.type==='number'" v-model="form[c.prop]" class="full" />
              <el-select v-else-if="c.options" v-model="form[c.prop]" class="full" clearable>
                <el-option v-for="o in c.options" :key="o" :label="o" :value="o" />
              </el-select>
              <el-input v-else-if="c.type==='password'" v-model="form[c.prop]" type="password" show-password autocomplete="new-password" placeholder="留空则不修改" />
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
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { get, post, del, tagType } from '../api'
import { ElMessageBox, ElMessage } from 'element-plus'
const props = defineProps<{ listUrl:string, saveUrl:string, deleteUrl?:string, columns:any[], writeRoles?:string[] }>()
const rows = ref<any[]>([])
const keyword = ref('')
const dialog = ref(false)
const form = ref<any>({})
const visibleColumns = computed(() => props.columns.filter(c => !c.hiddenInTable && c.type !== 'password'))
const editableColumns = computed(() => props.columns.filter(c => !c.readonly))
const currentRole = computed(() => { try { return JSON.parse(localStorage.getItem('loginUser') || '{}').role || '' } catch { return '' } })
const canWrite = computed(() => {
  if (!props.writeRoles || props.writeRoles.length === 0) return true
  return currentRole.value === 'ADMIN' || props.writeRoles.includes(currentRole.value)
})
const filtered = computed(() => {
  if (!keyword.value) return rows.value
  const k = keyword.value.toLowerCase()
  return rows.value.filter(r => JSON.stringify(r).toLowerCase().includes(k))
})
async function load(){ rows.value = await get(props.listUrl) }
function openEdit(row:any){ if (!canWrite.value) return; form.value = JSON.parse(JSON.stringify(row || {})); if ('password' in form.value) form.value.password = ''; dialog.value = true }
async function save(){ if (!canWrite.value) return; await post(props.saveUrl, form.value); dialog.value=false; ElMessage.success('已保存'); load() }
async function remove(row:any){
  if (!canWrite.value) return
  await ElMessageBox.confirm('确认删除该数据？')
  await del(`${props.deleteUrl}/${row.id}`)
  ElMessage.success('已删除')
  load()
}
onMounted(load)
</script>
