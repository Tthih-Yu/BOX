import { post } from './api'

export type RealtimeHandler = (topic:string, payload:any) => void

async function wsUrl() {
  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const ticket:any = await post('/auth/ws-ticket')
  const query = ticket?.ticket ? `?ticket=${encodeURIComponent(ticket.ticket)}` : ''
  return `${proto}//${location.host}/api/ws${query}`
}

function frame(command:string, headers:Record<string,string> = {}, body = '') {
  const lines = [command, ...Object.entries(headers).map(([k,v]) => `${k}:${v}`), '', body]
  return lines.join('\n') + '\0'
}

function enc(value:string) {
  const bytes = new TextEncoder().encode(value)
  let binary = ''
  bytes.forEach(b => { binary += String.fromCharCode(b) })
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function destinations(topic:string) {
  let user:any = {}
  try { user = JSON.parse(localStorage.getItem('loginUser') || '{}') } catch {}
  const base = `/topic/${topic}`
  if (user.role === 'ADMIN' && !user.factory) return [`${base}/@global`]
  if (!user.factory) return []
  return [
    `${base}/@factory/${enc(user.factory)}`,
    ...(user.deliveryAreas || []).map((area:string) => `${base}/@area/${enc(user.factory)}/${enc(area)}`),
  ]
}

export function connectRealtime(topics:string[], onMessage:RealtimeHandler) {
  let closedByClient = false
  let ws:WebSocket | null = null
  let reconnectTimer:number | undefined

  const connect = async () => {
    try {
      ws = new WebSocket(await wsUrl())
      ws.onopen = () => {
        ws?.send(frame('CONNECT', {'accept-version':'1.2', 'heart-beat':'10000,10000', host:location.host}))
      }
      ws.onmessage = (event) => {
        String(event.data).split('\0').filter(Boolean).forEach(raw => {
          const [head, body = ''] = raw.split('\n\n')
          const lines = head.split('\n')
          const command = lines[0]
          if (command === 'CONNECTED') {
            topics.flatMap(t => destinations(t).map(destination => ({ t, destination })))
              .forEach((x, i) => ws?.send(frame('SUBSCRIBE', { id:`sub-${i}`, destination:x.destination })))
            return
          }
          if (command === 'MESSAGE') {
            const destLine = lines.find(x => x.startsWith('destination:'))
            const topic = destLine ? destLine.replace('destination:/topic/', '') : 'unknown'
            try { onMessage(topic, JSON.parse(body)) } catch { onMessage(topic, body) }
          }
        })
      }
      ws.onclose = () => {
        if (!closedByClient) reconnectTimer = window.setTimeout(connect, 3000)
      }
      ws.onerror = () => ws?.close()
    } catch {
      if (!closedByClient) reconnectTimer = window.setTimeout(connect, 3000)
    }
  }

  connect()
  return () => {
    closedByClient = true
    if (reconnectTimer) window.clearTimeout(reconnectTimer)
    try { ws?.close() } catch {}
  }
}
