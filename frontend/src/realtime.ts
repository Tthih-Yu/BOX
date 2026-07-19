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
            topics.forEach((t, i) => ws?.send(frame('SUBSCRIBE', { id:`sub-${i}`, destination:`/topic/${t}` })))
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
