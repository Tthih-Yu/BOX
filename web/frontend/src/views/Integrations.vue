<template>
  <el-row :gutter="16">
    <el-col :span="8">
      <div class="card">
        <h3>SAP / IMS / PPC 接口接收</h3>
        <el-form label-width="105px">
          <el-form-item label="产品编码"><el-input v-model="form.productCode" /></el-form-item>
          <el-form-item label="物料编码"><el-input v-model="form.materialCode" /></el-form-item>
          <el-form-item label="物料名称"><el-input v-model="form.materialName" /></el-form-item>
          <el-form-item label="仓库料号"><el-input v-model="form.warehouseMaterialCode" /></el-form-item>
          <el-form-item label="仓库代码"><el-input v-model="form.warehouseCode" /></el-form-item>
          <el-form-item label="Bundle"><el-input v-model="form.bundleCode" /></el-form-item>
          <el-form-item label="工艺编码"><el-input v-model="form.processCode" /></el-form-item>
          <el-form-item label="产线/工位"><el-input v-model="form.lineCode" style="width:110px" /><el-input v-model="form.stationCode" style="width:110px;margin-left:8px" /></el-form-item>
          <el-form-item label="架位/层位"><el-input v-model="form.rackCode" style="width:110px" /><el-input v-model="form.shelfCode" style="width:110px;margin-left:8px" /></el-form-item>
          <el-form-item label="计划数量"><el-input-number v-model="form.planQty" :min="1" /></el-form-item>
          <el-form-item label="单盒数量"><el-input-number v-model="form.singleBoxQty" :min="0" /></el-form-item>
          <el-form-item label="生产周期"><el-input-number v-model="form.productionCycleMinutes" :min="0" /></el-form-item>
          <el-form-item label="送料周期"><el-input-number v-model="form.deliveryCycleMinutes" :min="0" /></el-form-item>
          <el-form-item label="库存"><el-input-number v-model="form.stockQty" :min="0" /></el-form-item>
          <el-form-item label="安全库存"><el-input-number v-model="form.safetyStock" :min="0" /></el-form-item>
          <el-form-item label="MPC额定值"><el-input-number v-model="form.mpcThresholdQty" :min="0" /></el-form-item>
        </el-form>
        <el-space wrap>
          <el-button v-if="canIntegrationWrite('sap')" type="primary" @click="sapBom">接收SAP BOM</el-button>
          <el-button v-if="canIntegrationWrite('ims')" type="success" @click="imsInventory">同步IMS库存</el-button>
          <el-button v-if="canIntegrationWrite('ppc')" type="warning" @click="ppcPlan">导入PPC计划</el-button>
        </el-space>
      </div>
    </el-col>
    <el-col :span="16">
      <div class="card">
        <div class="toolbar"><h3>SAP/IMS关联关系</h3><el-button @click="load">刷新</el-button></div>
        <el-table :data="links" border stripe height="680">
          <el-table-column prop="linkNo" label="关联号" width="210" />
          <el-table-column prop="systemCode" label="系统" width="90" />
          <el-table-column prop="externalKey" label="外部主键" width="160" />
          <el-table-column prop="productCode" label="产品" width="120" />
          <el-table-column prop="materialCode" label="线边料号" width="130" />
          <el-table-column prop="warehouseMaterialCode" label="仓库料号" width="130" />
          <el-table-column prop="lineCode" label="产线" width="100" />
          <el-table-column prop="stationCode" label="工位" width="100" />
          <el-table-column prop="lastSyncAt" label="同步时间" width="180" />
        </el-table>
      </div>
    </el-col>
  </el-row>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { get, post } from '../api'
import { canIntegrationWrite } from '../permissions'
import { ElMessage } from 'element-plus'
const links = ref<any[]>([])
const form = reactive<any>({ productCode:'', materialCode:'', materialName:'', warehouseMaterialCode:'', warehouseCode:'', bundleCode:'', processCode:'', lineCode:'', stationCode:'', rackCode:'', shelfCode:'', planQty:1, stockQty:0, usageQty:1, singleBoxQty:0, safetyStock:0, mpcThresholdQty:0, productionCycleMinutes:0, deliveryCycleMinutes:0, rawPayload:'' })
async function load(){ links.value = await get('/integrations/links') }
async function sapBom(){ await post('/integrations/sap/bom', form); ElMessage.success('SAP BOM已接收'); load() }
async function imsInventory(){ await post('/integrations/ims/inventory', form); ElMessage.success('IMS库存已同步'); load() }
async function ppcPlan(){ await post('/integrations/ppc/plan', form); ElMessage.success('PPC计划已导入'); load() }
onMounted(load)
</script>
