import axios, { AxiosError, AxiosHeaders, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { APP_CONFIG } from './config'

function rid() {
  const rand = Math.random().toString(16).slice(2)
  return `${APP_CONFIG.requestIdPrefix}-${Date.now()}-${rand}`
}

function stableStringify(value:any):string {
  if (value == null) return ''
  if (typeof FormData !== 'undefined' && value instanceof FormData) {
    const parts:string[] = []
    value.forEach((v:any, k:string) => {
      if (typeof File !== 'undefined' && v instanceof File) parts.push(`${k}:file:${v.name}:${v.size}:${v.lastModified}`)
      else if (typeof Blob !== 'undefined' && v instanceof Blob) parts.push(`${k}:blob:${v.size}:${v.type}`)
      else parts.push(`${k}:${String(v)}`)
    })
    return parts.sort().join('|')
  }
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`
  if (typeof value === 'object') return `{${Object.keys(value).sort().map(k => `${k}:${stableStringify(value[k])}`).join(',')}}`
  return String(value)
}

function hashText(text:string) {
  let h = 2166136261
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return (h >>> 0).toString(16)
}

function idempotencyKey(config:InternalAxiosRequestConfig) {
  const method = (config.method || 'get').toLowerCase()
  if (method === 'get') return ''
  const data:any = config.data
  const bucket = Math.floor(Date.now() / 5000)
  const source = `${method}:${config.url || ''}:${stableStringify(config.params)}:${stableStringify(data)}`
  return `${APP_CONFIG.idempotencyPrefix}-${hashText(source)}-${bucket}`
}

export const api = axios.create({ baseURL: '/api', timeout: 30000 })

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const requestId = rid()
  config.headers = AxiosHeaders.from(config.headers)
  config.headers['X-Request-Id'] = requestId
  const token = localStorage.getItem('token')
  if (token) config.headers['Authorization'] = `Bearer ${token}`
  const idem = idempotencyKey(config)
  if (idem) config.headers['X-Idempotency-Key'] = idem
  ;(config as any).__retryCount = (config as any).__retryCount || 0
  return config
})

api.interceptors.response.use(
  res => {
    const body = res.data
    if (body && body.success === false) {
      const msg = `${body.message || '请求失败'}${body.requestId ? `｜追踪号：${body.requestId}` : ''}`
      ElMessage.error(msg)
      return Promise.reject(new Error(msg))
    }
    return body?.data ?? body
  },
  async (err: AxiosError<any>) => {
    const config:any = err.config || {}
    const method = (config.method || '').toLowerCase()
    const retryable = method === 'get' && !err.response && config.__retryCount < 1
    if (retryable) {
      config.__retryCount += 1
      await new Promise(resolve => setTimeout(resolve, 500))
      return api(config)
    }
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      if (!location.pathname.includes('/login')) location.href = '/login'
    }
    const msg = err.response?.data?.message || err.message || '网络异常'
    const trace = err.response?.headers?.['x-request-id'] || err.response?.data?.requestId
    ElMessage.error(`${msg}${trace ? `｜追踪号：${trace}` : ''}`)
    return Promise.reject(err)
  }
)

export const get = <T=any>(url:string, params?:any) => api.get<any,T>(url, { params })
export const post = <T=any>(url:string, data?:any, config?:any) => api.post<any,T>(url, data, config)
export const del = <T=any>(url:string, data?:any) => api.delete<any,T>(url, data !== undefined ? { data } : undefined)

export async function downloadBlob(url:string, fallbackName:string, params?:any) {
  const token = localStorage.getItem('token')
  const response = await axios.get(url, {
    baseURL: '/api',
    params,
    responseType: 'blob',
    timeout: 60000,
    headers: {
      'X-Request-Id': rid(),
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    }
  })
  let blob:Blob = response.data
  if (blob && blob.type && blob.type.includes('json')) {
    const text = await blob.text()
    try {
      const body = JSON.parse(text)
      if (body && body.success === false) {
        const msg = `${body.message || '下载失败'}${body.requestId ? `｜追踪号：${body.requestId}` : ''}`
        ElMessage.error(msg)
        throw new Error(msg)
      }
    } catch (e:any) {
      if (e?.message) throw e
    }
  }
  const disposition = response.headers?.['content-disposition'] || ''
  let filename = fallbackName
  const starMatch = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  const plainMatch = /filename="?([^";]+)"?/i.exec(disposition)
  if (starMatch && starMatch[1]) {
    try { filename = decodeURIComponent(starMatch[1]) } catch {}
  } else if (plainMatch && plainMatch[1]) {
    filename = plainMatch[1]
  }
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
}

export const tagType = (v:string) => {
  if (!v) return ''
  if (v.includes('COMPLETED') || v.includes('FULL') || v.includes('SUCCESS') || v === 'UP' || v === 'OK') return 'success'
  if (v.includes('EXCEPTION') || v.includes('ABNORMAL') || v.includes('VOID') || v.includes('FAILED') || v.includes('URGENT') || v === 'DOWN' || v === 'ERROR') return 'danger'
  if (v.includes('CREATED') || v.includes('WAITING') || v.includes('EMPTY') || v === 'WARN') return 'warning'
  if (v.includes('DELIVER') || v.includes('TRANSIT') || v.includes('PICK')) return 'primary'
  return 'info'
}
