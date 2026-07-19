import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// 网络相关配置全部走环境变量，换部署环境(IP/域名变化)时无需改代码：
//   VITE_DEV_HOST         开发服务器监听地址，默认 127.0.0.1；局域网访问用 npm run dev:lan(0.0.0.0)
//   VITE_DEV_PORT         开发服务器端口，默认 5173
//   VITE_ALLOWED_HOSTS    允许访问 dev 服务器的主机名/IP，逗号分隔；
//                         留空或设为 all 表示允许全部(内网开发最省心)
//   VITE_API_TARGET       /api 反向代理目标后端，默认 http://localhost:8080
//   VITE_HMR_CLIENT_PORT  HMR 客户端端口，经反代/隧道访问时可设为 80
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const host = env.VITE_DEV_HOST || '127.0.0.1'
  const port = Number(env.VITE_DEV_PORT || 5173)
  const apiTarget = env.VITE_API_TARGET || 'http://localhost:8080'

  const rawHosts = (env.VITE_ALLOWED_HOSTS || '').trim()
  // 'all'/空 → 允许所有主机(true)；否则按逗号拆分为白名单数组。
  const allowedHosts: true | string[] =
    rawHosts === '' || rawHosts.toLowerCase() === 'all'
      ? true
      : rawHosts.split(',').map(s => s.trim()).filter(Boolean)

  const hmr = env.VITE_HMR_CLIENT_PORT
    ? { clientPort: Number(env.VITE_HMR_CLIENT_PORT) }
    : undefined

  return {
    plugins: [vue()],
    server: {
      host,
      port,
      strictPort: true,
      allowedHosts,
      ...(hmr ? { hmr } : {}),
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true,
          ws: true
        }
      }
    },
    preview: {
      host,
      port: Number(env.VITE_PREVIEW_PORT || 4173),
      strictPort: true
    }
  }
})
