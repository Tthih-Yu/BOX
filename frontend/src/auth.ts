export type UserRole = 'ADMIN' | 'PLANNER' | 'WAREHOUSE' | 'LINE' | 'VIEWER' | 'SYSTEM' | ''

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
  return !!roles?.includes(role)
}
