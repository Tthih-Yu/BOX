<template><CrudTable list-url="/users" save-url="/users" delete-url="/users" :columns="columns" :write-roles="ROLE_SETS.userAdmin" /></template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import CrudTable from '../components/CrudTable.vue'
import { ROLE_SETS } from '../permissions'
import { loadBusinessMeta } from '../meta'
import { get } from '../api'
const roleOptions = ref<{label:string;value:string}[]>([])
const factoryOptions = ref<{label:string;value:string}[]>([])
const areaOptions = ref<{label:string;value:string}[]>([])
const currentRole = (() => { try { return JSON.parse(localStorage.getItem('loginUser') || '{}').role || '' } catch { return '' } })()
const columns = computed(() => [
 {prop:'id',label:'ID',readonly:true,width:70},
 {prop:'username',label:'账号'},
 {prop:'realName',label:'姓名'},
 {prop:'password',label:'新密码',type:'password'},
 {prop:'role',label:'角色',options:roleOptions.value,tag:true},
 {prop:'roleLabel',label:'角色名称',readonly:true},
 {prop:'factory',label:'工厂',options:factoryOptions.value},
 {prop:'deliveryAreas',label:'配送区域（留空=全工厂）',options:areaOptions.value,multiple:true,width:260,type:'multi-dialog'},
 {prop:'phone',label:'电话'},
 {prop:'enabled',label:'启用',type:'boolean'},
 {prop:'changeReason',label:'变更说明',type:'textarea',hiddenInTable:true},
 {prop:'version',label:'版本',readonly:true,hiddenInTable:true},
 {prop:'lastLoginAt',label:'最后登录',readonly:true,width:180}
])
onMounted(async()=>{
 const [meta, scope]:any[] = await Promise.all([loadBusinessMeta(), get('/users/scope-options')])
 roleOptions.value = meta.userRoles
   .filter((x:any) => x.value !== 'SYSTEM' && (currentRole === 'ADMIN' || !['ADMIN', 'SUB_ADMIN'].includes(x.value)))
   .map((x:any) => ({ label: `${x.label}（${x.value}）`, value: x.value }))
 factoryOptions.value = (scope.factories || []).map((x:any) => ({ label: x.factoryName ? `${x.factoryName}（${x.factoryCode}）` : x.factoryCode, value: x.factoryCode }))
 const displayArea = (v:any) => String(v || '').replace(/EOVA/g, 'E0VA').replace(/EOY/g, 'E0Y')
 areaOptions.value = (scope.deliveryAreas || []).map((x:any) => ({ label: `${x.factoryCode} / ${displayArea(x.areaName || x.areaCode)}`, value: x.areaCode }))
})
</script>
