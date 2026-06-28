<template>
  <router-view v-if="$route.path === '/login'" />
  <el-container v-else class="app-shell">
    <el-aside width="238px" class="aside">
      <div class="brand">
        <div class="logo">MP</div>
        <div>
          <b>{{ APP_CONFIG.title }}</b>
          <small>Factory Landing {{ APP_CONFIG.version }}</small>
        </div>
      </div>
      <el-menu router :default-active="$route.path" class="menu">
        <el-menu-item v-if="can('/dashboard')" index="/dashboard"><el-icon><DataBoard /></el-icon>首页看板</el-menu-item>
        <el-menu-item v-if="can('/big-screen')" index="/big-screen"><el-icon><Monitor /></el-icon>现场大屏</el-menu-item>
        <el-menu-item v-if="can('/scan')" index="/scan"><el-icon><FullScreen /></el-icon>现场扫码</el-menu-item>
        <el-menu-item v-if="can('/tasks')" index="/tasks"><el-icon><List /></el-icon>仓库补货任务</el-menu-item>
        <el-menu-item v-if="can('/box-pool')" index="/box-pool"><el-icon><Box /></el-icon>周转盒池/空盒回收</el-menu-item>
        <el-menu-item v-if="can('/print-jobs')" index="/print-jobs"><el-icon><Tickets /></el-icon>出货标签打印</el-menu-item>
        <el-menu-item v-if="can('/agv-jobs')" index="/agv-jobs"><el-icon><Monitor /></el-icon>AGV任务接口</el-menu-item>
        <el-menu-item v-if="can('/planning')" index="/planning"><el-icon><DataBoard /></el-icon>PPC/MRP/MPC</el-menu-item>
        <el-menu-item v-if="can('/integrations')" index="/integrations"><el-icon><Grid /></el-icon>SAP/IMS接口</el-menu-item>
        <el-menu-item v-if="can('/boxes')" index="/boxes"><el-icon><Box /></el-icon>A/B双盒状态</el-menu-item>
        <el-sub-menu index="master" v-if="canAny(['/materials','/mappings','/station-materials','/inventory'])">
          <template #title><el-icon><Grid /></el-icon>基础数据</template>
          <el-menu-item v-if="can('/materials')" index="/materials">物料主数据</el-menu-item>
          <el-menu-item v-if="can('/mappings')" index="/mappings">料号映射</el-menu-item>
          <el-menu-item v-if="can('/station-materials')" index="/station-materials">工位用料</el-menu-item>
          <el-menu-item v-if="can('/inventory')" index="/inventory">仓库库存</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="label" v-if="canAny(['/labels','/label-templates','/label-scan-rules'])">
          <template #title><el-icon><Tickets /></el-icon>标签中心</template>
          <el-menu-item v-if="can('/labels')" index="/labels">标签管理</el-menu-item>
          <el-menu-item v-if="can('/label-templates')" index="/label-templates">标签模板</el-menu-item>
          <el-menu-item v-if="can('/label-scan-rules')" index="/label-scan-rules">扫码规则</el-menu-item>
        </el-sub-menu>
        <el-menu-item v-if="can('/logs')" index="/logs"><el-icon><Document /></el-icon>日志追溯</el-menu-item>
        <el-menu-item v-if="can('/health')" index="/health"><el-icon><FirstAidKit /></el-icon>健康检查</el-menu-item>
        <el-menu-item v-if="can('/maintenance')" index="/maintenance"><el-icon><Tools /></el-icon>维护恢复</el-menu-item>
        <el-sub-menu index="system" v-if="canAny(['/users','/configs'])">
          <template #title><el-icon><Setting /></el-icon>系统管理</template>
          <el-menu-item v-if="can('/users')" index="/users">用户角色</el-menu-item>
          <el-menu-item v-if="can('/configs')" index="/configs">系统参数</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container class="content-shell">
      <el-header class="topbar">
        <div class="page-title">{{ routeTitle }}</div>
        <div class="top-actions">
          <el-tag type="success">加固版 {{ APP_CONFIG.version }}</el-tag>
          <el-tag>{{ loginUser }}</el-tag>
          <el-tag>{{ loginRoleLabel }}</el-tag>
          <el-button link type="primary" @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main"><router-view /></el-main>
    </el-container>
  </el-container>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { APP_CONFIG } from './config'
import { getLoginUser } from './auth'
import { canRoute, canAnyRoute, routeTitle as getRouteTitle } from './permissions'
const route = useRoute()
const router = useRouter()
const current = computed(() => getLoginUser())
const routeTitle = computed(() => getRouteTitle(route.path))
const loginUser = computed(() => current.value.realName || current.value.username || '已登录')
const loginRoleLabel = computed(() => current.value.roleLabel || current.value.role || '未知角色')
function can(path:string){ return canRoute(path) }
function canAny(paths:string[]){ return canAnyRoute(paths) }
function logout(){ localStorage.removeItem('token'); localStorage.removeItem('loginUser'); router.push('/login') }
</script>
