<template>
  <div class="card">
    <el-alert type="info" :closable="false" style="margin-bottom:12px" title="模板不再只服务一种看板卡。labelType 决定业务字段，carrierType 决定扫码介质，previewRenderer 决定前端预览样式。" />
    <CrudTable list-url="/labels/templates" save-url="/labels/templates" delete-url="/labels/templates" :columns="columns" :write-roles="ROLE_SETS.admin" />
  </div>
</template>
<script setup lang="ts">
import CrudTable from '../components/CrudTable.vue'
import { ROLE_SETS } from '../permissions'
const columns = [
 {prop:'id',label:'ID',readonly:true,width:70},
 {prop:'templateCode',label:'模板编码'},
 {prop:'templateName',label:'模板名称'},
 {prop:'labelType',label:'标签类型'},
 {prop:'carrierType',label:'码制'},
 {prop:'previewRenderer',label:'预览渲染器'},
 {prop:'widthMm',label:'宽mm',type:'number'},
 {prop:'heightMm',label:'高mm',type:'number'},
 {prop:'content',label:'模板内容'},
 {prop:'schemaJson',label:'字段Schema'},
 {prop:'printCommandTemplate',label:'打印指令模板'},
 {prop:'enabled',label:'启用',type:'boolean'},
 {prop:'remark',label:'备注'}
]
</script>
