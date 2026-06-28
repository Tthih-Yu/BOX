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
  { prop:'stationCode', label:'工位', width:150 },
  { prop:'warehouseCode', label:'仓库代号', width:160 },
  { prop:'boxSize', label:'盒子大小', width:140 },
  { prop:'quantity', label:'数量', type:'number', width:140 },
  { prop:'id', label:'ID', readonly:true, hiddenInTable:true },
  { prop:'deliveryType', label:'用途', options:['NORMAL','URGENT'], hiddenInTable:true },
  { prop:'enabled', label:'启用', type:'boolean', hiddenInTable:true },
  { prop:'warehouseMaterialCode', label:'兼容仓库料号', hiddenInTable:true },
  { prop:'description', label:'描述', hiddenInTable:true },
  { prop:'remark', label:'备注', hiddenInTable:true }
]
</script>
