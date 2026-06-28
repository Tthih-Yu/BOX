import { currentRole, hasAnyRole, UserRole } from './auth'

export const ROLE_SETS:Record<string, UserRole[]> = {
  all: ['ADMIN','PLANNER','WAREHOUSE','LINE','VIEWER'],
  masterRead: ['ADMIN','PLANNER','WAREHOUSE','LINE','VIEWER'],
  admin: ['ADMIN'],
  planner: ['ADMIN','PLANNER'],
  warehouse: ['ADMIN','WAREHOUSE'],
  warehousePlanner: ['ADMIN','PLANNER','WAREHOUSE'],
  lineOps: ['ADMIN','WAREHOUSE','LINE'],
}

export interface RouteMetaItem { path:string; title:string; roles:UserRole[]; menu?:boolean; group?:string; icon?:string }

export const ROUTE_META:RouteMetaItem[] = [
  { path:'/dashboard', title:'首页看板', roles:ROLE_SETS.all, icon:'DataBoard' },
  { path:'/big-screen', title:'现场大屏', roles:ROLE_SETS.all, icon:'Monitor' },
  { path:'/scan', title:'现场扫码', roles:ROLE_SETS.lineOps, icon:'FullScreen' },
  { path:'/tasks', title:'仓库补货任务', roles:ROLE_SETS.all, icon:'List' },
  { path:'/box-pool', title:'周转盒池/空盒回收', roles:ROLE_SETS.all, icon:'Box' },
  { path:'/print-jobs', title:'出货标签打印', roles:ROLE_SETS.warehousePlanner, icon:'Tickets' },
  { path:'/agv-jobs', title:'AGV任务接口', roles:ROLE_SETS.warehousePlanner, icon:'Monitor' },
  { path:'/planning', title:'PPC/MRP/MPC', roles:ROLE_SETS.warehousePlanner, icon:'DataBoard' },
  { path:'/integrations', title:'SAP/IMS接口', roles:ROLE_SETS.warehousePlanner, icon:'Grid' },
  { path:'/boxes', title:'A/B双盒状态', roles:ROLE_SETS.all, icon:'Box' },
  { path:'/materials', title:'物料主数据', roles:ROLE_SETS.masterRead, group:'master' },
  { path:'/mappings', title:'料号映射', roles:ROLE_SETS.warehousePlanner, group:'master' },
  { path:'/station-materials', title:'工位用料', roles:ROLE_SETS.masterRead, group:'master' },
  { path:'/inventory', title:'仓库库存', roles:ROLE_SETS.warehousePlanner, group:'master' },
  { path:'/labels', title:'标签管理', roles:ROLE_SETS.all, group:'label' },
  { path:'/label-templates', title:'标签模板', roles:ROLE_SETS.warehousePlanner, group:'label' },
  { path:'/label-scan-rules', title:'扫码规则', roles:ROLE_SETS.warehousePlanner, group:'label' },
  { path:'/logs', title:'日志追溯', roles:ROLE_SETS.warehousePlanner, icon:'Document' },
  { path:'/health', title:'健康检查', roles:ROLE_SETS.warehousePlanner, icon:'FirstAidKit' },
  { path:'/maintenance', title:'维护恢复', roles:ROLE_SETS.admin, icon:'Tools' },
  { path:'/users', title:'用户角色', roles:ROLE_SETS.admin, group:'system' },
  { path:'/configs', title:'系统参数', roles:ROLE_SETS.admin, group:'system' },
]

export const routeByPath = Object.fromEntries(ROUTE_META.map(x => [x.path, x])) as Record<string, RouteMetaItem>
export function canRoute(path:string) { return hasAnyRole(routeByPath[path]?.roles) }
export function canAnyRoute(paths:string[]) { return paths.some(canRoute) }
export function routeTitle(path:string) { return routeByPath[path]?.title || '物料拉动系统' }

export const TASK_TABLE_ACTIONS = ['accept','startPick','picked','deliver','arrive','complete','receive','returnEmptyBox','exception','retry','forceComplete']

const taskPermissions:Record<string, {roles:UserRole[]; statuses?:string[]}> = {
  accept: {roles:['WAREHOUSE'], statuses:['CREATED']},
  startPick: {roles:['WAREHOUSE'], statuses:['ACCEPTED']},
  picked: {roles:['WAREHOUSE'], statuses:['ACCEPTED','PICKING']},
  deliver: {roles:['WAREHOUSE'], statuses:['PICKED']},
  arrive: {roles:['WAREHOUSE'], statuses:['DELIVERING']},
  complete: {roles:['WAREHOUSE'], statuses:['PICKED','DELIVERING','ARRIVED']},
  cancel: {roles:['WAREHOUSE'], statuses:['CREATED','ACCEPTED','PICKING','PICKED','DELIVERING','ARRIVED','EXCEPTION']},
  retry: {roles:['WAREHOUSE'], statuses:['EXCEPTION']},
  receive: {roles:['WAREHOUSE','LINE'], statuses:['PICKED','DELIVERING','ARRIVED']},
  returnEmptyBox: {roles:['WAREHOUSE','LINE'], statuses:['CREATED','ACCEPTED','PICKING','PICKED','DELIVERING','ARRIVED','COMPLETED','EXCEPTION','CANCELLED']},
  exception: {roles:['WAREHOUSE','LINE'], statuses:['CREATED','ACCEPTED','PICKING','PICKED','DELIVERING','ARRIVED']},
  remark: {roles:['WAREHOUSE','LINE']},
  archive: {roles:['WAREHOUSE','LINE'], statuses:['COMPLETED','CANCELLED']},
  forceComplete: {roles:['ADMIN'], statuses:['CREATED','ACCEPTED','PICKING','PICKED','DELIVERING','ARRIVED','EXCEPTION']},
}

export function canTaskAction(action:string, status?:string) {
  const p = taskPermissions[action]
  if (!p) return false
  const role = currentRole()
  const roleOk = role === 'ADMIN' ? p.roles.includes('ADMIN') || action !== 'forceComplete' : p.roles.includes(role)
  const statusOk = !p.statuses || !status || p.statuses.includes(status)
  return roleOk && statusOk
}

export function canAgvDispatch() { return hasAnyRole(['WAREHOUSE']) }
export function canIntegrationWrite(type:'sap'|'ims'|'ppc') {
  if (type === 'ims') return hasAnyRole(['ADMIN','WAREHOUSE'])
  return hasAnyRole(['ADMIN','PLANNER'])
}
