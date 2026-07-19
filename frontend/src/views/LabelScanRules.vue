<template>
  <div class="card">
    <el-alert type="warning" :closable="false" style="margin-bottom:12px" title="扫码规则用于兼容不同现场标签：纯数字一维码、二维码JSON、键值对、POU前缀码。生产环境禁止多个规则把同一个码解析到多张标签。" />
    <CrudTable list-url="/labels/scan-rules" save-url="/labels/scan-rules" delete-url="/labels/scan-rules" :columns="columns" :write-roles="ROLE_SETS.admin" />
  </div>
</template>
<script setup lang="ts">
import CrudTable from '../components/CrudTable.vue'
import { ROLE_SETS } from '../permissions'
const columns = [
 {prop:'id',label:'ID',readonly:true,width:70},
 {prop:'ruleCode',label:'规则编码'},
 {prop:'ruleName',label:'规则名称'},
 {prop:'labelType',label:'标签类型'},
 {prop:'carrierType',label:'码制'},
 {prop:'priorityNo',label:'优先级',type:'number'},
 {prop:'matcherType',label:'匹配方式'},
 {prop:'matcherPattern',label:'匹配表达式'},
 {prop:'payloadFormat',label:'载荷格式'},
 {prop:'targetField',label:'目标字段'},
 {prop:'allowMultipleMatch',label:'允许多命中',type:'boolean'},
 {prop:'allowCreateStub',label:'允许占位',type:'boolean'},
 {prop:'enabled',label:'启用',type:'boolean'},
 {prop:'remark',label:'备注'}
]
</script>
