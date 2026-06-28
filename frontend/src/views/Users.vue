<template><CrudTable list-url="/users" save-url="/users" delete-url="/users" :columns="columns" :write-roles="ROLE_SETS.admin" /></template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import CrudTable from '../components/CrudTable.vue'
import { ROLE_SETS } from '../permissions'
import { loadBusinessMeta } from '../meta'
const roleOptions = ref<string[]>([])
const columns = computed(() => [
 {prop:'id',label:'ID',readonly:true,width:70},
 {prop:'username',label:'账号'},
 {prop:'realName',label:'姓名'},
 {prop:'password',label:'新密码',type:'password'},
 {prop:'role',label:'角色',options:roleOptions.value,tag:true},
 {prop:'roleLabel',label:'角色名称',readonly:true},
 {prop:'phone',label:'电话'},
 {prop:'enabled',label:'启用',type:'boolean'},
 {prop:'lastLoginAt',label:'最后登录',readonly:true,width:180}
])
onMounted(async()=>{ const meta = await loadBusinessMeta(); roleOptions.value = meta.userRoles.filter(x => x.value !== 'SYSTEM').map(x => x.value) })
</script>
