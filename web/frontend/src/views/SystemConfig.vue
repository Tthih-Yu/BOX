<template><CrudTable list-url="/configs" save-url="/configs" :columns="columns" :write-roles="ROLE_SETS.admin" /></template>
<script setup lang="ts">
import CrudTable from '../components/CrudTable.vue'
import { ROLE_SETS } from '../permissions'
const columns = [
 {prop:'id',label:'ID',readonly:true,width:70},
 {prop:'configKey',label:'参数键'},
 {prop:'configName',label:'参数名称'},
 {prop:'configValue',label:'参数值'},
 {prop:'editable',label:'可编辑',type:'boolean'},
 {prop:'remark',label:'备注'}
]
</script>
