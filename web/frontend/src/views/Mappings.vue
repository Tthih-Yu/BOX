<template><CrudTable list-url="/mappings" save-url="/mappings" delete-url="/mappings" :columns="columns" :write-roles="ROLE_SETS.planner" /></template>
<script setup lang="ts">
import CrudTable from '../components/CrudTable.vue'
import { ROLE_SETS } from '../permissions'
const columns = [
 {prop:'id',label:'ID',readonly:true,width:70},
 {prop:'lineMaterialCode',label:'产线料号'},
 {prop:'warehouseMaterialCode',label:'仓库料号'},
 {prop:'description',label:'描述'},
 {prop:'enabled',label:'启用',type:'boolean'},
 {prop:'remark',label:'备注'}
]
</script>
