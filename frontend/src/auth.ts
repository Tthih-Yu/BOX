export type UserRole = 'ADMIN' | 'SUB_ADMIN' | 'PLANNER' | 'WAREHOUSE' | 'LINE' | 'VIEWER' | 'SYSTEM' | ''

export interface LoginUser {
  username?: string
  realName?: string
  role?: UserRole
  roleLabel?: string
}

export function getLoginUser(): LoginUser {
  try { return JSON.parse(localStorage.getItem('loginUser') || '{}') } catch { return {} }
}

export function currentRole(): UserRole {
  return getLoginUser().role || ''
}

export function isAdmin() {
  return currentRole() === 'ADMIN'
}

export function hasAnyRole(roles?: UserRole[]) {
  const role = currentRole()
  if (role === 'ADMIN') return true
  // 普通管理员(SUB_ADMIN)是“准管理员”：除管理员专属功能(仅 ADMIN/SYSTEM)外一律放行。
  // 与后端 RoleInterceptor.isAdminOnly 保持一致；具体可见菜单再由“菜单权限”白名单收窄。
  if (role === 'SUB_ADMIN') {
    if (roles?.includes('SUB_ADMIN')) return true
    return !!roles?.some(r => r !== 'ADMIN' && r !== 'SYSTEM')
  }
  return !!roles?.includes(role)
}
