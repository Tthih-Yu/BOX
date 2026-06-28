<template>
  <CrudTable
    list-url="/materials"
    save-url="/materials"
    delete-url="/materials"
    :columns="columns"
    :write-roles="ROLE_SETS.planner"
  >
    <template #toolbar-extra="{ reload }">
      <ImportButton
        type="materials"
        template-url="/import_templates/materials.csv"
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
  { prop:'materialCode', label:'产线物料编码' },
  { prop:'warehouseMaterialCode', label:'仓库物料编码' },
  { prop:'materialName', label:'物料名称' },
  { prop:'spec', label:'规格型号' },
  { prop:'unit', label:'单位' },
  { prop:'category', label:'类别' },
  { prop:'supplier', label:'供应商' },
  { prop:'materialImageUrl', label:'物料图片URL' },
  { prop:'safetyStock', label:'安全库存', type:'number' },
  { prop:'minPackageQty', label:'最小包装量', type:'number' },
  { prop:'enabled', label:'启用', type:'boolean' },
  { prop:'remark', label:'备注' }
]
</script>
