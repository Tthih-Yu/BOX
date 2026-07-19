<template>
  <CrudTable
    list-url="/mappings"
    save-url="/mappings"
    delete-url="/mappings"
    :columns="columns"
    :write-roles="ROLE_SETS.planner"
  >
    <template #toolbar-extra="{ reload }">
      <ImportButton
        type="mappings"
        template-url="/import_templates/mappings.csv"
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
  { prop:'mappingOrder', label:'序号', type:'number', width:90 },
  { prop:'lineMaterialCode', label:'物料号', width:160 },
  { prop:'warehouseCode', label:'仓库代号', width:160 },
  { prop:'boxSize', label:'盒子大小', width:120 },
  { prop:'quantity', label:'数量', type:'number', width:110 },
  { prop:'deliveryType', label:'用途', options:[{label:'使用（正常）',value:'NORMAL'},{label:'备用（紧急）',value:'URGENT'}], width:120 },
  { prop:'warehouseLocation', label:'仓库位置', width:160 },
  { prop:'deliveryAddress', label:'总装地址', width:200 },
  { prop:'deliveryArea', label:'配送区域', width:110 },
  { prop:'id', label:'ID', readonly:true, hiddenInTable:true },
  { prop:'enabled', label:'启用', type:'boolean', hiddenInTable:true },
  { prop:'warehouseMaterialCode', label:'兼容仓库料号', hiddenInTable:true },
  { prop:'description', label:'描述', hiddenInTable:true },
  { prop:'remark', label:'备注', hiddenInTable:true }
]
</script>
