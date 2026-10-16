import { ref, onMounted, onBeforeUnmount } from 'vue'

export interface LiveCall {
  callId: string
  localParty: string
  remoteParty: string
  direction: string
  capturePort: string
  service: string
  startedAt: string
}

/**
 * 实时通话状态 WebSocket composable
 * 连接 /ws/live-calls,维护进行中通话列表,断线自动重连
 */
export function useLiveCalls() {
  const liveCalls = ref<LiveCall[]>([])
  const connected = ref(false)
  let ws: WebSocket | null = null
  let reconnectTimer: number | null = null
  let manuallyClosed = false

  function connect() {
    // 经 vite 代理(/ws -> ws://localhost:8081),生产同源
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${proto}://${location.host}/ws/live-calls`
    ws = new WebSocket(url)

    ws.onopen = () => {
      connected.value = true
    }

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        if (data.type === 'liveCalls') {
          liveCalls.value = data.calls
        }
      } catch (e) {
        console.error('liveCalls 消息解析失败', e)
      }
    }

    ws.onclose = () => {
      connected.value = false
      if (!manuallyClosed) {
        // 5 秒后重连
        reconnectTimer = window.setTimeout(connect, 5000)
      }
    }

    ws.onerror = () => {
      ws?.close()
    }
  }

  function disconnect() {
    manuallyClosed = true
    if (reconnectTimer) clearTimeout(reconnectTimer)
    ws?.close()
  }

  onMounted(connect)
  onBeforeUnmount(disconnect)

  return { liveCalls, connected }
}
