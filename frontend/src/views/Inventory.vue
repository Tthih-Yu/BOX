<template><CrudTable list-url="/inventory" save-url="/inventory" delete-url="/inventory" :columns="columns" :write-roles="ROLE_SETS.warehouse" /></template>
<script setup lang="ts">
import CrudTable from '../components/CrudTable.vue'
import { ROLE_SETS } from '../permissions'
const columns = [
 {prop:'id',label:'ID',readonly:true,width:70},
 {prop:'warehouseCode',label:'仓库'},
 {prop:'locationCode',label:'库位'},
 {prop:'warehouseMaterialCode',label:'仓库料号'},
 {prop:'materialCode',label:'产线料号'},
 {prop:'materialName',label:'物料名称'},
 {prop:'stockQty',label:'账面库存',type:'number'},
 {prop:'lockedQty',label:'锁定数量',readonly:true},
 {prop:'availableQty',label:'可用库存',readonly:true},
 {prop:'safetyStock',label:'安全库存',type:'number'},
 {prop:'batchNo',label:'批次'},
 {prop:'frozen',label:'冻结',type:'boolean'},
 {prop:'freezeReason',label:'冻结原因'},
 {prop:'remark',label:'调整备注'}
]
</script>
