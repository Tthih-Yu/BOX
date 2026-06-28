import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { canRoute } from './permissions'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/dashboard' },
  { path: '/login', component: () => import('./views/Login.vue') },
  { path: '/dashboard', component: () => import('./views/Dashboard.vue') },
  { path: '/big-screen', component: () => import('./views/BigScreen.vue') },
  { path: '/scan', component: () => import('./views/SiteScan.vue') },
  { path: '/tasks', component: () => import('./views/WarehouseTasks.vue') },
  { path: '/box-pool', component: () => import('./views/BoxPool.vue') },
  { path: '/print-jobs', component: () => import('./views/FactoryPrint.vue') },
  { path: '/agv-jobs', component: () => import('./views/AgvJobs.vue') },
  { path: '/planning', component: () => import('./views/Planning.vue') },
  { path: '/integrations', component: () => import('./views/Integrations.vue') },
  { path: '/boxes', component: () => import('./views/BoxStatus.vue') },
  { path: '/materials', component: () => import('./views/Materials.vue') },
  { path: '/mappings', component: () => import('./views/Mappings.vue') },
  { path: '/station-materials', component: () => import('./views/StationMaterials.vue') },
  { path: '/inventory', component: () => import('./views/Inventory.vue') },
  { path: '/labels', component: () => import('./views/Labels.vue') },
  { path: '/label-templates', component: () => import('./views/LabelTemplates.vue') },
  { path: '/label-scan-rules', component: () => import('./views/LabelScanRules.vue') },
  { path: '/logs', component: () => import('./views/Logs.vue') },
  { path: '/health', component: () => import('./views/FactoryHealth.vue') },
  { path: '/maintenance', component: () => import('./views/Maintenance.vue') },
  { path: '/users', component: () => import('./views/Users.vue') },
  { path: '/configs', component: () => import('./views/SystemConfig.vue') }
]
const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  if (to.path !== '/login' && !localStorage.getItem('token')) return '/login'
  if (to.path === '/login' && localStorage.getItem('token')) return '/dashboard'
  if (to.path !== '/login' && !canRoute(to.path)) return '/dashboard'
})

export default router
