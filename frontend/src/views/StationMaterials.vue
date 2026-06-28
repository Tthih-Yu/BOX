<template>
  <CrudTable
    list-url="/station-materials"
    save-url="/station-materials"
    delete-url="/station-materials"
    :columns="columns"
    :write-roles="ROLE_SETS.planner"
  >
    <template #toolbar-extra="{ reload }">
      <ImportButton
        type="stationMaterials"
        template-url="/import_templates/station-materials.csv"
        :write-roles="ROLE_SETS.planner"
        @imported="reload"
      />
    </template>
  </CrudTable>
</template>

<script setup lang="ts">
import CrudTable from '../components/CrudTable.vue'
import ImportButton from '../components/ImportButton.vue'
import { ROLE_SETS } from '../permissions'

const columns = [
  { prop:'id', label:'ID', readonly:true, width:70 },
  { prop:'lineCode', label:'产线' },
  { prop:'stationCode', label:'工位编码' },
  { prop:'stationName', label:'工位名称' },
  { prop:'processCode', label:'工艺编码' },
  { prop:'processName', label:'工艺名称' },
  { prop:'bundleCode', label:'Bundle' },
  { prop:'rackCode', label:'架位' },
  { prop:'shelfCode', label:'层位' },
  { prop:'deliveryCycleMinutes', label:'送料周期(分钟)', type:'number' },
  { prop:'productionCycleMinutes', label:'生产周期(分钟)', type:'number' },
  { prop:'materialCode', label:'产线料号' },
  { prop:'materialName', label:'物料名称' },
  { prop:'warehouseMaterialCode', label:'仓库料号' },
  { prop:'singleBoxQty', label:'单盒数量', type:'number' },
  { prop:'standardBoxQty', label:'标准盒量', type:'number' },
  { prop:'dailyUsage', label:'日用量', type:'number' },
  { prop:'safetyStock', label:'安全库存', type:'number' },
  { prop:'mpcThresholdQty', label:'MPC额定值', type:'number' },
  { prop:'triggerQty', label:'触发量', type:'number' },
  { prop:'forecastEnabled', label:'参与预测', type:'boolean' },
  { prop:'enabled', label:'启用', type:'boolean' },
  { prop:'deliveryAddress', label:'配送地址' },
  { prop:'warehouseLocation', label:'仓库库位' },
  { prop:'remark', label:'备注' }
]
</script>
