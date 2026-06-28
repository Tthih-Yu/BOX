import { get } from './api'

export interface MetaOption { label:string; value:string; type?:string }
export interface BusinessMeta {
  userRoles:MetaOption[]
  taskStatuses:MetaOption[]
  boxPoolStatuses:MetaOption[]
  planStatuses:MetaOption[]
  demandStatuses:MetaOption[]
  purchaseStatuses:MetaOption[]
  labelUsageTypes:MetaOption[]
  deliveryModes:MetaOption[]
  exceptionTypes:MetaOption[]
  boxSides:MetaOption[]
  agvCallbackStatuses:MetaOption[]
}

export const emptyMeta:BusinessMeta = {
  userRoles: [],
  taskStatuses: [],
  boxPoolStatuses: [],
  planStatuses: [],
  demandStatuses: [],
  purchaseStatuses: [],
  labelUsageTypes: [],
  deliveryModes: [],
  exceptionTypes: [],
  boxSides: [],
  agvCallbackStatuses: [],
}

let cache:BusinessMeta | null = null
export async function loadBusinessMeta() {
  if (cache) return cache
  cache = await get<BusinessMeta>('/meta/business')
  return cache
}
