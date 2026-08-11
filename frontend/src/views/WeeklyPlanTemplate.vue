<template>
  <div class="card">
    <div class="hint">
      填写下面的信息后下载模板：系统会按你选的<b>起始日</b>自动算出连续七天的日期（月/日）填进表头，
      客户、生产工厂、项目、客户号会预填到每一行，计划员只需再填 8D号、描述、标包和每天白/夜的数量。
    </div>

    <el-form label-width="110px" class="form">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="起始日">
            <el-date-picker
              v-model="startDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择这一周的第一天"
              style="width:100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="周次">
            <el-input :model-value="weekPreview" readonly placeholder="选择起始日后自动计算" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="客户">
            <el-input v-model="customer" placeholder="如 Chery" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="生产工厂">
            <el-input v-model="factory" placeholder="如 弋江 / 三山" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="项目">
            <el-input v-model="project" placeholder="如 E0Y / E0Y-BEV" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="客户号">
            <el-input v-model="customerNo" placeholder="如 806008676AA" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="标题标识">
            <el-input v-model="factoryTag" placeholder="标题里的前缀，默认 E0Y&T2Y" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="空行数">
            <el-input-number v-model="blankRows" :min="1" :max="200" style="width:100%" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <div class="preview" v-if="datePreview.length">
      <div class="section-title">将写入表头的七天日期</div>
      <el-tag v-for="d in datePreview" :key="d" class="date-tag">{{ d }}</el-tag>
    </div>

    <div class="actions">
      <el-button type="primary" :icon="Download" :loading="downloading" @click="download">下载模板</el-button>
      <el-button @click="reset">重置</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { downloadBlob } from '../api'

const startDate = ref('')
const customer = ref('')
const factory = ref('')
const project = ref('')
const customerNo = ref('')
const factoryTag = ref('')
const blankRows = ref(10)
const downloading = ref(false)

/** 起始日往后连续七天，显示成模板里的“月/日”。 */
const datePreview = computed(() => {
  if (!startDate.value) return [] as string[]
  const base = new Date(startDate.value)
  if (isNaN(base.getTime())) return [] as string[]
  const out:string[] = []
  for (let i = 0; i < 7; i++) {
    const d = new Date(base)
    d.setDate(base.getDate() + i)
    out.push(`${d.getMonth() + 1}/${d.getDate()}`)
  }
  return out
})

/** ISO 周次：与后端 WeekFields.ISO 一致，仅作展示。 */
const weekPreview = computed(() => {
  if (!startDate.value) return ''
  const d = new Date(startDate.value)
  if (isNaN(d.getTime())) return ''
  const target = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()))
  const dayNum = target.getUTCDay() || 7
  target.setUTCDate(target.getUTCDate() + 4 - dayNum)
  const yearStart = new Date(Date.UTC(target.getUTCFullYear(), 0, 1))
  const week = Math.ceil(((target.getTime() - yearStart.getTime()) / 86400000 + 1) / 7)
  return `WK${week}`
})

async function download(){
  if (!startDate.value) { ElMessage.warning('请先选择起始日'); return }
  try {
    downloading.value = true
    await downloadBlob('/weekly-plan/template', `weekly-plan-template-${weekPreview.value || 'WK'}.xlsx`, {
      startDate: startDate.value,
      customer: customer.value || undefined,
      factory: factory.value || undefined,
      project: project.value || undefined,
      customerNo: customerNo.value || undefined,
      factoryTag: factoryTag.value || undefined,
      blankRows: blankRows.value
    })
  } catch { /* 拦截器已提示 */ } finally {
    downloading.value = false
  }
}

function reset(){
  startDate.value = ''
  customer.value = ''
  factory.value = ''
  project.value = ''
  customerNo.value = ''
  factoryTag.value = ''
  blankRows.value = 10
}
</script>

<style scoped>
.hint { color:#475569; background:#f8fafc; border:1px solid #e2e8f0; border-radius:8px; padding:12px 14px; margin-bottom:18px; line-height:1.7 }
.form { max-width:900px }
.section-title { font-weight:600; margin:6px 0 10px }
.preview { margin:6px 0 18px }
.date-tag { margin-right:8px; margin-bottom:8px }
.actions { display:flex; gap:10px; margin-top:8px }
</style>
