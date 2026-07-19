<template>
  <div class="card">
    <div class="toolbar">
      <div class="hint">
        按角色配置各角色登录后可见的菜单。管理员始终可见全部菜单，不受此处限制。勾选后点“保存”生效，用户下次进入或刷新后按新权限显示。
      </div>
      <div>
        <el-button @click="load">重置</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </div>

    <el-tabs v-model="activeRole" class="role-tabs">
      <el-tab-pane v-for="r in editableRoles" :key="r.value" :label="r.label" :name="r.value">
        <div class="role-panel">
          <div class="role-panel-head">
            <span>为「{{ r.label }}」勾选可见菜单</span>
            <div>
              <el-button link type="primary" size="small" @click="selectAll(r.value)">全选</el-button>
              <el-button link type="info" size="small" @click="clearAll(r.value)">清空</el-button>
              <el-button link type="warning" size="small" @click="applyDefault(r.value)">恢复默认(仓库任务/出货打印/料号映射)</el-button>
            </div>
          </div>
          <el-checkbox-group v-model="model[r.value]">
            <div class="menu-grid">
              <el-checkbox v-for="m in menuOptions" :key="m.path" :label="m.path" border class="menu-check">
                {{ m.title }}
              </el-checkbox>
            </div>
          </el-checkbox-group>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { get, post } from '../api'
import { ElMessage } from 'element-plus'
import { ROUTE_META } from '../permissions'
import { loadBusinessMeta } from '../meta'

const DEFAULT_MENUS = ['/tasks', '/print-jobs', '/mappings']
const menuOptions = computed(() => ROUTE_META.map(r => ({ path: r.path, title: r.title })))
const editableRoles = ref<{label:string;value:string}[]>([])
const activeRole = ref('')
const model = ref<Record<string, string[]>>({})
const saving = ref(false)

async function load(){
  const meta = await loadBusinessMeta()
  editableRoles.value = meta.userRoles.filter(x => x.value !== 'ADMIN' && x.value !== 'SYSTEM')
  if (!activeRole.value && editableRoles.value.length) activeRole.value = editableRoles.value[0].value
  const data:any = await get('/menu-permissions')
  const next:Record<string, string[]> = {}
  for (const r of editableRoles.value){
    const cfg = data && Array.isArray(data[r.value]) ? data[r.value] : (r.value === 'VIEWER' ? DEFAULT_MENUS : allPaths())
    next[r.value] = [...cfg]
  }
  model.value = next
}
function allPaths(){ return menuOptions.value.map(m => m.path) }
function selectAll(role:string){ model.value[role] = allPaths() }
function clearAll(role:string){ model.value[role] = [] }
function applyDefault(role:string){ model.value[role] = [...DEFAULT_MENUS] }
async function save(){
  saving.value = true
  try {
    await post('/menu-permissions', model.value)
    ElMessage.success('菜单权限已保存，相关用户下次进入或刷新后生效')
  } catch (e:any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
<style scoped>
.toolbar{ display:flex; align-items:flex-start; justify-content:space-between; gap:16px; margin-bottom:12px; }
.toolbar .hint{ color:#64748b; font-size:13px; line-height:1.7; max-width:70%; }
.role-panel-head{ display:flex; align-items:center; justify-content:space-between; margin-bottom:14px; color:#1e293b; font-weight:600; font-size:14px; }
.menu-grid{ display:grid; grid-template-columns:repeat(auto-fill, minmax(200px, 1fr)); gap:10px; }
.menu-check{ margin:0 !important; height:40px; }
</style>
