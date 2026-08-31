const DEFAULT_SERVER_BASE_URL = 'http://10.243.129.131/api'
const DEFAULT_EXTERNAL_BASE_URL = 'https://tthih.top'
const DEFAULT_SCAN_SUBMIT_PATH = '/scan/empty'
const DEFAULT_REQUEST_UNIT = '个'
const NETWORK_INTERNAL = 'INTERNAL'
const NETWORK_EXTERNAL = 'EXTERNAL'
const SOURCE = 'wechat_miniprogram'

function normalizeInternalBaseUrl(value) {
  const trimmed = String(value || '').trim().replace(/\/+$/, '')
  if (!trimmed) return DEFAULT_SERVER_BASE_URL
  return /\/api$/i.test(trimmed) ? trimmed : `${trimmed}/api`
}

function normalizeExternalBaseUrl(value) {
  const trimmed = String(value || '').trim().replace(/\/+$/, '')
  if (!trimmed) return DEFAULT_EXTERNAL_BASE_URL
  return trimmed.replace(/\/api$/i, '')
}

function normalizePath(value) {
  const trimmed = String(value || '').trim()
  if (!trimmed) return DEFAULT_SCAN_SUBMIT_PATH
  return trimmed.startsWith('/') ? trimmed : `/${trimmed}`
}

function actionFromPath(path) {
  const normalized = normalizePath(path)
  return normalized.substring(normalized.lastIndexOf('/') + 1).toLowerCase()
}

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

function requestId() {
  return `MINI-${uuid()}`
}

function parseJson(data) {
  if (data === null || data === undefined || data === '') return {}
  if (typeof data === 'string') {
    try {
      return JSON.parse(data)
    } catch (e) {
      return {}
    }
  }
  return data
}

function request(options) {
  const {
    url,
    method = 'GET',
    data,
    headers = {},
    timeout = 12000
  } = options

  return new Promise((resolve, reject) => {
    const header = Object.assign({}, headers)
    if (data !== undefined && data !== null) {
      header['content-type'] = 'application/json; charset=utf-8'
    }
    header['X-Request-Id'] = header['X-Request-Id'] || requestId()

    wx.request({
      url,
      method,
      data,
      header,
      timeout,
      success(res) {
        const json = parseJson(res.data)
        const ok = res.statusCode >= 200 && res.statusCode < 300 && json.success !== false
        if (ok) {
          resolve(json)
          return
        }

        const message = json.message || json.error || `服务器请求失败 HTTP ${res.statusCode}`
        const traceId = json.requestId || json.traceId
        reject(new Error(traceId ? `${message}\n追踪号：${traceId}` : message))
      },
      fail(err) {
        reject(new Error(err.errMsg || '网络请求失败'))
      }
    })
  })
}

function firstText(obj, ...keys) {
  if (!obj) return ''
  for (const key of keys) {
    const value = obj[key]
    if (value === null || value === undefined) continue
    const text = String(value).trim()
    if (text && text.toLowerCase() !== 'null') return text
  }
  return ''
}

function parseDecimal(value) {
  if (value === null || value === undefined) return null
  const text = String(value).trim()
  if (!/^-?\d+(\.\d+)?([eE][+-]?\d+)?$/.test(text)) return null
  const number = Number(text)
  return Number.isFinite(number) ? number : null
}

function toPlainDecimal(value) {
  const number = Number(value)
  if (Number.isFinite(number)) return String(number)
  return String(value)
}

function buildDeviceHeaders(deviceNo, deviceModel, employeeNo, extra) {
  const headers = Object.assign({}, extra || {})
  if (deviceNo) headers['X-Device-No'] = String(deviceNo).trim()
  if (deviceModel) headers['X-Device-Model'] = String(deviceModel).trim()
  if (employeeNo) headers['X-Employee-No'] = String(employeeNo).trim()
  return headers
}

function summarizeResponse(json) {
  const labels = {
    message: '结果',
    taskNo: '任务号',
    taskStatus: '任务状态',
    materialCode: '物料编码',
    requestQty: '申请数量',
    requestUnit: '申请单位'
  }

  const parts = Object.keys(labels)
    .map((key) => {
      const value = json && json[key]
      if (value === null || value === undefined || value === '') return ''
      const text = String(value).trim()
      return text && text !== 'null' ? `${labels[key]}：${text}` : ''
    })
    .filter(Boolean)

  return parts.length ? parts.join('\n') : (JSON.stringify(json) || '{}')
}

class MaterialPullApi {
  constructor(baseUrl) {
    this.baseUrl = normalizeInternalBaseUrl(baseUrl)
  }

  url(path) {
    return `${this.baseUrl.replace(/\/+$/, '')}${path}`
  }

  async healthReady() {
    const json = await request({ url: this.url('/health/ready') })
    return firstText(json, 'message') || 'OK'
  }

  async deviceLogin(deviceNo, deviceModel, employeeNo, remark = '') {
    const body = {
      deviceNo: String(deviceNo).trim(),
      deviceModel: String(deviceModel).trim(),
      employeeNo: String(employeeNo).trim(),
      source: SOURCE,
      remark: remark || ''
    }
    const response = await request({
      url: this.url('/device/login'),
      method: 'POST',
      data: body,
      headers: buildDeviceHeaders(deviceNo, deviceModel, employeeNo)
    })
    return response.data || response
  }

  async previewBarcodeScan(scanCode, format, deviceNo, deviceModel, employeeNo) {
    const body = {
      scanCode: String(scanCode).trim(),
      format,
      deviceNo: String(deviceNo).trim(),
      source: SOURCE,
      scannedAt: Date.now(),
      employeeNo: String(employeeNo).trim()
    }
    const response = await request({
      url: this.url('/scan/preview'),
      method: 'POST',
      data: body,
      headers: buildDeviceHeaders(deviceNo, deviceModel, employeeNo)
    })
    return response.data || response
  }

  async submitBarcodeScan(params) {
    const {
      path,
      scanCode,
      format,
      requestQty,
      requestUnit,
      deviceNo,
      deviceModel,
      employeeNo
    } = params
    const unit = String(requestUnit || '').trim() || DEFAULT_REQUEST_UNIT
    const body = {
      scanCode: String(scanCode).trim(),
      format,
      deviceNo: String(deviceNo).trim(),
      source: SOURCE,
      scannedAt: Date.now(),
      employeeNo: String(employeeNo).trim(),
      requestQty,
      requestUnit: unit
    }
    const headers = buildDeviceHeaders(deviceNo, deviceModel, employeeNo, {
      'X-Idempotency-Key': `APP-SCAN-${uuid()}`
    })
    const response = await request({
      url: this.url(normalizePath(path)),
      method: 'POST',
      data: body,
      headers
    })
    return response.data || response
  }
}

class ExternalScanRelayApi {
  constructor(baseUrl) {
    this.baseUrl = normalizeExternalBaseUrl(baseUrl)
  }

  url(path) {
    return `${this.baseUrl.replace(/\/+$/, '')}${path}`
  }

  async health() {
    const response = await request({ url: this.url('/health') })
    return firstText(response, 'status') || 'UP'
  }

  async registerDevice(deviceNo, deviceModel, employeeNo, enrollmentKey) {
    const key = String(enrollmentKey || '').trim()
    if (!key) throw new Error('请输入管理员提供的设备登记码')
    const response = await request({
      url: this.url('/app/v1/device/register'),
      method: 'POST',
      data: {
        deviceNo: String(deviceNo).trim(),
        deviceModel: String(deviceModel).trim(),
        employeeNo: String(employeeNo).trim()
      },
      headers: {
        'X-Enrollment-Key': key
      }
    })
    const token = firstText(response.data || {}, 'deviceToken')
    if (!token) throw new Error('云端未返回设备凭证')
    return token
  }

  async executeScan(params) {
    const {
      action,
      scanCode,
      format,
      requestQty,
      requestUnit,
      deviceNo,
      deviceToken
    } = params

    const token = String(deviceToken || '').trim()
    if (!token) throw new Error('设备尚未登记，请在设置中填写登记码并保存')

    const commandId = `APP-SCAN-${uuid()}`
    const body = {
      requestId: commandId,
      action: String(action).toLowerCase(),
      scanCode: String(scanCode).trim(),
      format
    }
    if (requestQty !== null && requestQty !== undefined) body.requestQty = requestQty
    if (requestUnit !== null && requestUnit !== undefined && String(requestUnit).trim()) {
      body.requestUnit = String(requestUnit).trim()
    }

    const headers = {
      Authorization: `Bearer ${token}`,
      'X-Device-No': String(deviceNo).trim()
    }

    await request({
      url: this.url('/app/v1/commands'),
      method: 'POST',
      data: body,
      headers
    })

    const deadline = Date.now() + 60000
    while (Date.now() < deadline) {
      await sleep(800)
      const response = await request({
        url: this.url(`/app/v1/commands/${commandId}`),
        headers
      })
      const command = response.data
      if (!command) throw new Error('云端未返回指令状态')

      switch (command.status) {
        case 'SUCCEEDED':
          if (command.result) return command.result
          throw new Error('任务成功但没有返回结果')
        case 'FAILED':
          throw new Error(command.error || '内网处理失败')
        case 'EXPIRED':
          throw new Error('任务等待超时，请检查公司网络')
        default:
          break
      }
    }

    throw new Error('等待内网处理超时，请稍后重试')
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function getDeviceModel() {
  let brand = ''
  let model = ''
  if (wx.getDeviceInfo) {
    try {
      const info = wx.getDeviceInfo()
      brand = info.brand || ''
      model = info.model || ''
    } catch (e) {
      brand = ''
      model = ''
    }
  }
  if (!brand || !model) {
    try {
      const info = wx.getSystemInfoSync()
      brand = brand || info.brand || ''
      model = model || info.model || ''
    } catch (e) {
      // ignore
    }
  }
  return [brand, model].map((item) => String(item).trim()).filter(Boolean).join(' ') || 'UNKNOWN'
}

module.exports = {
  DEFAULT_SERVER_BASE_URL,
  DEFAULT_EXTERNAL_BASE_URL,
  DEFAULT_SCAN_SUBMIT_PATH,
  DEFAULT_REQUEST_UNIT,
  NETWORK_INTERNAL,
  NETWORK_EXTERNAL,
  normalizeInternalBaseUrl,
  normalizeExternalBaseUrl,
  normalizePath,
  actionFromPath,
  firstText,
  parseDecimal,
  toPlainDecimal,
  summarizeResponse,
  getDeviceModel,
  MaterialPullApi,
  ExternalScanRelayApi
}
