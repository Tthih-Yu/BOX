<template>
  <div>
    <div class="card">
      <h3>Excel导入</h3>
      <p class="hint">支持 .xlsx / .xlsm / .xls / .csv 导入。第一行作为表头，从第二行开始读取。导入失败的行会进入错误明细。</p>
      <el-form label-width="110px">
        <el-form-item label="导入类型">
          <el-radio-group v-model="type">
            <el-radio-button value="materials">物料主数据</el-radio-button>
            <el-radio-button value="mappings">料号映射</el-radio-button>
            <el-radio-button value="stationMaterials">工位用料</el-radio-button>
            <el-radio-button value="factoryLabels">真实工厂标签</el-radio-button>
            <el-radio-button value="siteLabels">旧看板卡标签</el-radio-button>
            <el-radio-button value="universalLabels">通用标签</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="选择文件">
          <el-upload :auto-upload="false" :limit="1" accept=".xlsx,.xlsm,.xls,.csv" :on-change="onChange">
            <el-button type="primary">选择导入文件</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item>
          <el-button type="success" @click="upload">开始导入</el-button>
          <el-button @click="load">刷新批次</el-button>
          <el-link href="/import_templates/factory-pull-labels.csv" target="_blank" style="margin-left:12px">现有 CSV 模板仍兼容；现场 Excel 文件可直接上传 .xlsx/.xlsm/.xls</el-link>
        </el-form-item>
      </el-form>
    </div>
    <div class="card">
      <h3>导入批次</h3>
      <el-table :data="batches" border height="420">
        <el-table-column prop="batchNo" label="批次号" width="230" />
        <el-table-column prop="importType" label="类型" />
        <el-table-column prop="fileName" label="文件名" />
        <el-table-column prop="totalRows" label="总行" />
        <el-table-column prop="successRows" label="成功" />
        <el-table-column prop="failedRows" label="失败" />
        <el-table-column prop="status" label="状态"><template #default="{row}"><el-tag :type="tagType(row.status)">{{row.status}}</el-tag></template></el-table-column>
        <el-table-column label="操作"><template #default="{row}"><el-button link @click="showErrors(row)">错误明细</el-button></template></el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="errDialog" title="错误明细" width="900px">
      <el-table :data="errors" border height="420">
        <el-table-column prop="rowNo" label="行号" width="90" />
        <el-table-column prop="rawData" label="原始数据" />
        <el-table-column prop="errorMessage" label="错误信息" />
      </el-table>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { get, post, tagType } from '../api'
import { ElMessage } from 'element-plus'
const type=ref('materials'), file=ref<any>(null), batches=ref<any[]>([]), errors=ref<any[]>([]), errDialog=ref(false)
function onChange(f:any){ file.value=f.raw }
async function upload(){
  if(!file.value){ ElMessage.warning('请选择文件'); return }
  const fd=new FormData(); fd.append('file',file.value)
  await post(`/imports/${type.value}`, fd, {headers:{'Content-Type':'multipart/form-data'}})
  ElMessage.success('导入完成'); load()
}
async function load(){ batches.value=await get('/imports') }
async function showErrors(row:any){ errors.value=await get(`/imports/${row.batchNo}/errors`); errDialog.value=true }
onMounted(load)
</script>