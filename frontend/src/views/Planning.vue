<template>
  <div class="planning-page">
    <el-row :gutter="16">
      <el-col :span="7">
        <div class="card">
          <h3>PPC生产计划</h3>
          <el-form label-width="110px">
            <el-form-item label="产品编码"><el-input v-model="plan.productCode" /></el-form-item>
            <el-form-item label="产品名称"><el-input v-model="plan.productName" /></el-form-item>
            <el-form-item label="产线"><el-input v-model="plan.lineCode" /></el-form-item>
            <el-form-item label="工位"><el-input v-model="plan.stationCode" /></el-form-item>
            <el-form-item label="Bundle"><el-input v-model="plan.bundleCode" /></el-form-item>
            <el-form-item label="计划数量"><el-input-number v-model="plan.planQty" :min="1" style="width:100%" /></el-form-item>
            <el-form-item label="默认盒量"><el-input-number v-model="plan.defaultBoxQty" :min="1" style="width:100%" /></el-form-item>
          </el-form>
          <el-button type="primary" @click="savePlan">保存计划</el-button>
          <el-button @click="load">刷新</el-button>
        </div>

        <div class="card">
          <h3>SAP BOM / 工艺BOM</h3>
          <el-form label-width="110px">
            <el-form-item label="产品"><el-input v-model="bom.productCode" /></el-form-item>
            <el-form-item label="BOM版本"><el-input v-model="bom.bomVersion" /></el-form-item>
            <el-form-item label="Bundle"><el-input v-model="bom.bundleCode" /></el-form-item>
            <el-form-item label="工艺编码"><el-input v-model="bom.processCode" /></el-form-item>
            <el-form-item label="组件料号"><el-input v-model="bom.componentMaterialCode" /></el-form-item>
            <el-form-item label="组件名称"><el-input v-model="bom.componentMaterialName" /></el-form-item>
            <el-form-item label="仓库料号"><el-input v-model="bom.warehouseMaterialCode" /></el-form-item>
            <el-form-item label="用量"><el-input-number v-model="bom.usageQty" :min="1" style="width:100%" /></el-form-item>
            <el-form-item label="单盒数量"><el-input-number v-model="bom.loadPerBoxQty" :min="0" style="width:100%" /></el-form-item>
            <el-form-item label="安全库存"><el-input-number v-model="bom.safetyStock" :min="0" style="width:100%" /></el-form-item>
            <el-form-item label="产线/工位">
              <el-input v-model="bom.lineCode" style="width:48%" />
              <el-input v-model="bom.stationCode" style="width:48%;margin-left:4%" />
            </el-form-item>
            <el-form-item label="架位/层位">
              <el-input v-model="bom.rackCode" style="width:48%" />
              <el-input v-model="bom.shelfCode" style="width:48%;margin-left:4%" />
            </el-form-item>
          </el-form>
          <el-button type="primary" @click="saveBom">保存BOM</el-button>
        </div>
      </el-col>

      <el-col :span="17">
        <div class="card">
          <div class="section-title"><h3>工艺、仓库录入信息</h3><span>Bundle、生产周期、架位、送料周期、负荷单盒数量、MPC额定值</span></div>
          <el-form label-width="100px" class="compact-form">
            <el-row :gutter="10">
              <el-col :span="6"><el-form-item label="产线"><el-input v-model="processForm.lineCode" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="工位"><el-input v-model="processForm.stationCode" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="工位名"><el-input v-model="processForm.stationName" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="Bundle"><el-input v-model="processForm.bundleCode" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="工艺"><el-input v-model="processForm.processCode" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="物料号"><el-input v-model="processForm.materialCode" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="物料名"><el-input v-model="processForm.materialName" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="仓库料号"><el-input v-model="processForm.warehouseMaterialCode" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="单盒数量"><el-input-number v-model="processForm.singleBoxQty" :min="1" style="width:100%" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="安全库存"><el-input-number v-model="processForm.safetyStock" :min="0" style="width:100%" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="MPC额定值"><el-input-number v-model="processForm.mpcThresholdQty" :min="0" style="width:100%" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="送料周期"><el-input-number v-model="processForm.deliveryCycleMinutes" :min="0" style="width:100%" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="生产周期"><el-input-number v-model="processForm.productionCycleMinutes" :min="0" style="width:100%" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="架位"><el-input v-model="processForm.rackCode" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="层位"><el-input v-model="processForm.shelfCode" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="配送地址"><el-input v-model="processForm.deliveryAddress" /></el-form-item></el-col>
            </el-row>
          </el-form>
          <el-button type="primary" @click="saveProcess">保存工艺信息</el-button>
        </div>

        <div class="card">
          <h3>计划列表</h3>
          <el-table :data="plans" border stripe height="230">
            <el-table-column prop="planNo" label="计划号" width="210" />
            <el-table-column prop="productCode" label="产品" width="120" />
            <el-table-column prop="lineCode" label="产线" width="90" />
            <el-table-column prop="stationCode" label="工位" width="90" />
            <el-table-column prop="bundleCode" label="Bundle" width="110" />
            <el-table-column prop="planQty" label="数量" width="90" />
            <el-table-column prop="defaultBoxQty" label="默认盒量" width="100" />
            <el-table-column prop="status" label="状态" width="130"><template #default="{row}"><el-tag :type="tagType(row.status)">{{row.status}}</el-tag></template></el-table-column>
            <el-table-column fixed="right" label="操作" width="160"><template #default="{row}"><el-button size="small" type="success" @click="generate(row)">生成需求/任务</el-button></template></el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="card">
          <h3>单盒物料需求</h3>
          <el-table :data="demands" border stripe height="300">
            <el-table-column prop="demandNo" label="需求号" width="200" />
            <el-table-column prop="planNo" label="计划" width="190" />
            <el-table-column prop="bundleCode" label="Bundle" width="110" />
            <el-table-column prop="stationCode" label="工位" width="95" />
            <el-table-column prop="materialCode" label="物料" width="120" />
            <el-table-column prop="demandQty" label="总需求" width="90" />
            <el-table-column prop="singleBoxQty" label="单盒" width="80" />
            <el-table-column prop="taskBoxCount" label="盒数" width="80" />
            <el-table-column prop="taskNos" label="任务号" width="260" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="130" />
          </el-table>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="card">
          <h3>库存调整对照表</h3>
          <el-table :data="adjustments" border stripe height="300">
            <el-table-column prop="warehouseMaterialCode" label="仓库料号" width="130" />
            <el-table-column prop="materialName" label="物料" width="150" />
            <el-table-column prop="demandQty" label="需求" width="80" />
            <el-table-column prop="currentStock" label="可用" width="80" />
            <el-table-column prop="safetyStock" label="安全" width="80" />
            <el-table-column prop="inventoryDifferenceQty" label="差异" width="90" />
            <el-table-column prop="adjustmentQty" label="建议补充" width="100" />
            <el-table-column prop="printTarget" label="输出位置" min-width="150" />
          </el-table>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="card">
          <h3>MPC物料预测</h3>
          <el-table :data="forecasts" border stripe height="260">
            <el-table-column prop="warehouseMaterialCode" label="仓库料号" width="140" />
            <el-table-column prop="materialName" label="物料" width="150" />
            <el-table-column prop="forecastQty" label="预测需求" width="100" />
            <el-table-column prop="currentStock" label="当前库存" width="100" />
            <el-table-column prop="thresholdQty" label="额定值" width="90" />
            <el-table-column prop="purchaseSuggestionQty" label="采购建议" width="100" />
            <el-table-column prop="source" label="来源" min-width="170" />
          </el-table>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="card">
          <h3>MPC采购需求</h3>
          <el-table :data="purchases" border stripe height="260">
            <el-table-column prop="purchaseNo" label="采购需求" width="210" />
            <el-table-column prop="warehouseMaterialCode" label="仓库料号" width="140" />
            <el-table-column prop="forecastQty" label="预测" width="90" />
            <el-table-column prop="thresholdQty" label="额定值" width="90" />
            <el-table-column prop="requestQty" label="建议采购" width="110" />
            <el-table-column prop="status" label="状态" width="120" />
            <el-table-column fixed="right" label="操作" width="130"><template #default="{row}"><el-button size="small" @click="submitPurchase(row)">提交MPC</el-button></template></el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { get, post, tagType } from '../api'
import { ElMessage } from 'element-plus'

const plan = reactive<any>({ productCode:'', productName:'', lineCode:'', stationCode:'', bundleCode:'', planQty:1, defaultBoxQty:1 })
const bom = reactive<any>({ productCode:'', bomVersion:'', bundleCode:'', processCode:'', componentMaterialCode:'', componentMaterialName:'', warehouseMaterialCode:'', usageQty:1, loadPerBoxQty:0, safetyStock:0, lineCode:'', stationCode:'', rackCode:'', shelfCode:'' })
const processForm = reactive<any>({ lineCode:'', stationCode:'', stationName:'', bundleCode:'', processCode:'', materialCode:'', materialName:'', warehouseMaterialCode:'', singleBoxQty:1, standardBoxQty:1, safetyStock:0, mpcThresholdQty:0, deliveryCycleMinutes:0, productionCycleMinutes:0, rackCode:'', shelfCode:'', deliveryAddress:'', enabled:true, forecastEnabled:true })
const plans = ref<any[]>([])
const demands = ref<any[]>([])
const purchases = ref<any[]>([])
const adjustments = ref<any[]>([])
const forecasts = ref<any[]>([])

async function load(){
  plans.value = await get('/planning/plans')
  demands.value = await get('/planning/demands')
  purchases.value = await get('/planning/purchases')
  adjustments.value = await get('/planning/inventory-adjustments')
  forecasts.value = await get('/planning/material-forecasts')
}
async function savePlan(){ await post('/planning/plans', plan); ElMessage.success('计划已保存'); load() }
async function saveBom(){ await post('/planning/boms', bom); ElMessage.success('BOM已保存'); load() }
async function saveProcess(){ processForm.standardBoxQty = processForm.singleBoxQty; await post('/planning/process-configs', processForm); ElMessage.success('工艺/仓库信息已保存'); load() }
async function generate(row:any){ const r:any = await post(`/planning/plans/${row.planNo}/generate?createPullTasks=true`); ElMessage.success(`已生成需求${r?.demands?.length || 0}条、单盒任务${r?.tasks?.length || 0}条、采购建议${r?.purchases?.length || 0}条`); load() }
async function submitPurchase(row:any){ await post(`/planning/purchases/${row.purchaseNo}/submit`); ElMessage.success('已提交MPC'); load() }
onMounted(load)
</script>
<style scoped>
.planning-page{display:flex;flex-direction:column;gap:16px}.section-title{display:flex;align-items:baseline;gap:12px}.section-title h3{margin-top:0}.section-title span{font-size:12px;color:#64748b}.compact-form :deep(.el-form-item){margin-bottom:10px}
</style>
