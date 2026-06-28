export const APP_CONFIG = {
  title: import.meta.env.VITE_APP_TITLE || '物料拉动系统',
  version: import.meta.env.VITE_APP_VERSION || 'V0.8.4-production',
  requestIdPrefix: import.meta.env.VITE_REQUEST_ID_PREFIX || 'WEB',
  idempotencyPrefix: import.meta.env.VITE_IDEMPOTENCY_PREFIX || 'WEB-IDEMP',
  deviceNo: import.meta.env.VITE_DEVICE_NO || '',
  printerName: import.meta.env.VITE_PRINTER_NAME || '',
}

export function runtimeDeviceNo() {
  const configured = localStorage.getItem('deviceNo') || APP_CONFIG.deviceNo
  if (configured && configured.trim()) return configured.trim()
  const key = 'browserDeviceNo'
  let value = localStorage.getItem(key)
  if (!value) {
    const random = globalThis.crypto?.randomUUID?.() || Math.random().toString(16).slice(2)
    value = `${APP_CONFIG.requestIdPrefix}-${random}`
    localStorage.setItem(key, value)
  }
  return value
}

export function runtimePrinterName() {
  return (localStorage.getItem('printerName') || APP_CONFIG.printerName || '').trim()
}

export function saveRuntimePrinterName(value:string) {
  if (value && value.trim()) localStorage.setItem('printerName', value.trim())
}
