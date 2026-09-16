import { post } from './api'

export type RealtimeHandler = (topic:string, payload:any) => void

async function wsUrl(signal?: AbortSignal) {
  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const ticket:any = await post('/auth/ws-ticket', undefined, signal ? { signal } : undefined)
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
  let ticketController:AbortController | null = null
  let connecting = false
  let generation = 0

  const scheduleReconnect = () => {
    if (closedByClient || connecting || reconnectTimer !== undefined) return
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = undefined
      void connect()
    }, 3000)
  }

  const connect = async () => {
    if (closedByClient || connecting) return
    connecting = true
    const currentGeneration = ++generation
    const controller = new AbortController()
    ticketController = controller
    try {
      const url = await wsUrl(controller.signal)
      // The component may have been unmounted while the ticket request was pending.
      // Never create a socket after cleanup; doing so leaks a connection permanently.
      if (closedByClient || currentGeneration !== generation) return

      const socket = new WebSocket(url)
      ws = socket
      socket.onopen = () => {
        if (closedByClient || ws !== socket) return
        socket.send(frame('CONNECT', {'accept-version':'1.2', 'heart-beat':'10000,10000', host:location.host}))
      }
      socket.onmessage = (event) => {
        String(event.data).split('\0').filter(Boolean).forEach(raw => {
          const [head, body = ''] = raw.split('\n\n')
          const lines = head.split('\n')
          const command = lines[0]
          if (command === 'CONNECTED') {
            topics.flatMap(t => destinations(t).map(destination => ({ t, destination })))
              .forEach((x, i) => {
                if (!closedByClient && ws === socket) socket.send(frame('SUBSCRIBE', { id:`sub-${i}`, destination:x.destination }))
              })
            return
          }
          if (command === 'MESSAGE') {
            const destLine = lines.find(x => x.startsWith('destination:'))
            const topic = destLine ? destLine.replace('destination:/topic/', '') : 'unknown'
            try { onMessage(topic, JSON.parse(body)) } catch { onMessage(topic, body) }
          }
        })
      }
      socket.onclose = () => {
        if (ws === socket) ws = null
        if (!closedByClient) scheduleReconnect()
      }
      socket.onerror = () => socket.close()
    } catch {
      if (!closedByClient) scheduleReconnect()
    } finally {
      if (ticketController === controller) ticketController = null
      if (currentGeneration === generation) connecting = false
    }
  }

  void connect()
  return () => {
    closedByClient = true
    generation++
    if (reconnectTimer) window.clearTimeout(reconnectTimer)
    reconnectTimer = undefined
    try { ticketController?.abort() } catch {}
    ticketController = null
    const socket = ws
    ws = null
    try { socket?.close() } catch {}
  }
}
