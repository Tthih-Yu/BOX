const {
  DEFAULT_SERVER_BASE_URL,
  DEFAULT_EXTERNAL_BASE_URL,
  DEFAULT_SCAN_SUBMIT_PATH,
  NETWORK_INTERNAL,
  normalizeInternalBaseUrl,
  normalizeExternalBaseUrl,
  normalizePath
} = require('./api.js')

const STORAGE_KEY = 'material_pull_app_settings'

function generatedDeviceNo() {
  return `APP-${Date.now().toString(36).toUpperCase()}`
}

function defaults() {
  return {
    networkMode: NETWORK_INTERNAL,
    serverBaseUrl: DEFAULT_SERVER_BASE_URL,
    externalBaseUrl: DEFAULT_EXTERNAL_BASE_URL,
    relayDeviceToken: '',
    relayRegisteredDeviceNo: '',
    scanSubmitPath: DEFAULT_SCAN_SUBMIT_PATH,
    employeeNo: '',
    deviceNo: ''
  }
}

function getSettings() {
  const stored = wx.getStorageSync(STORAGE_KEY)
  const raw = stored && typeof stored === 'object' ? stored : {}
  const settings = Object.assign(defaults(), raw)

  settings.serverBaseUrl = normalizeInternalBaseUrl(settings.serverBaseUrl)
  settings.externalBaseUrl = normalizeExternalBaseUrl(settings.externalBaseUrl)
  settings.scanSubmitPath = normalizePath(settings.scanSubmitPath)
  settings.deviceNo = String(settings.deviceNo || '').trim()
  settings.employeeNo = String(settings.employeeNo || '').trim()
  settings.relayDeviceToken = String(settings.relayDeviceToken || '').trim()
  settings.relayRegisteredDeviceNo = String(settings.relayRegisteredDeviceNo || '').trim()

  if (!settings.deviceNo) {
    settings.deviceNo = generatedDeviceNo()
  }
  return settings
}

function saveSettings(settings) {
  wx.setStorageSync(STORAGE_KEY, settings)
  return settings
}

function updateSettings(patch) {
  const next = Object.assign(getSettings(), patch || {})
  next.serverBaseUrl = normalizeInternalBaseUrl(next.serverBaseUrl)
  next.externalBaseUrl = normalizeExternalBaseUrl(next.externalBaseUrl)
  next.scanSubmitPath = normalizePath(next.scanSubmitPath)
  next.deviceNo = String(next.deviceNo || '').trim()
  next.employeeNo = String(next.employeeNo || '').trim()
  next.relayDeviceToken = String(next.relayDeviceToken || '').trim()
  next.relayRegisteredDeviceNo = String(next.relayRegisteredDeviceNo || '').trim()
  if (!next.deviceNo) next.deviceNo = generatedDeviceNo()
  return saveSettings(next)
}

module.exports = {
  STORAGE_KEY,
  getSettings,
  saveSettings,
  updateSettings
}
