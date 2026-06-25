<template>
  <div class="login-page">
    <div class="login-bg" aria-hidden="true">
      <div class="login-bg-gradient" />
      <div class="login-bg-grid" />
      <div class="login-bg-orb login-bg-orb--1" />
      <div class="login-bg-orb login-bg-orb--2" />
      <div class="login-bg-orb login-bg-orb--3" />
    </div>

    <div class="login-shell">
      <aside class="login-brand">
        <div class="brand-badge">
          <span class="brand-logo">MP</span>
          <div class="brand-text">
            <strong>{{ APP_CONFIG.title }}</strong>
            <small>Material Pull System</small>
          </div>
        </div>

        <div class="brand-hero">
          <h1>工厂物料拉动<br /><em>一体化管控平台</em></h1>
          <p>覆盖现场扫码、仓库补货、周转盒池、标签打印与 AGV 调度，助力产线物料精准配送。</p>
        </div>

        <ul class="brand-features">
          <li><el-icon><FullScreen /></el-icon><span>现场扫码与双盒状态</span></li>
          <li><el-icon><List /></el-icon><span>仓库补货任务联动</span></li>
          <li><el-icon><Monitor /></el-icon><span>大屏看板与接口集成</span></li>
        </ul>

        <footer class="brand-footer">
          <span class="version-tag">{{ APP_CONFIG.version }}</span>
          <span>Secure Factory Access</span>
        </footer>
      </aside>

      <main class="login-panel">
        <div class="login-card">
          <header class="login-card__header">
            <div class="login-card__icon">
              <el-icon><UserFilled /></el-icon>
            </div>
            <h2>欢迎登录</h2>
            <p>请输入系统账号和密码</p>
          </header>

          <el-form class="login-form" @submit.prevent="login" @keyup.enter="login">
            <div class="field">
              <label for="username">账号</label>
              <el-input
                id="username"
                v-model="form.username"
                size="large"
                placeholder="请输入账号"
                autocomplete="username"
                :prefix-icon="User"
              />
            </div>

            <div class="field">
              <label for="password">密码</label>
              <el-input
                id="password"
                v-model="form.password"
                size="large"
                type="password"
                placeholder="请输入密码"
                show-password
                autocomplete="current-password"
                :prefix-icon="Lock"
              />
            </div>

            <p class="login-hint">
              首次管理员由部署环境变量创建；生产环境请使用正式账号并按权限操作。
            </p>

            <el-button
              type="primary"
              size="large"
              class="login-btn"
              :loading="loading"
              @click="login"
            >
              <span v-if="!loading">登 录</span>
              <span v-else>登录中…</span>
              <el-icon v-if="!loading" class="login-btn__arrow"><Right /></el-icon>
            </el-button>
          </el-form>
        </div>

        <p class="login-copyright">© {{ year }} {{ APP_CONFIG.title }} · 内部系统</p>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { User, Lock } from '@element-plus/icons-vue'
import { post } from '../api'
import { APP_CONFIG } from '../config'
import router from '../router'

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const year = new Date().getFullYear()

async function login() {
  loading.value = true
  try {
    const r: any = await post('/auth/login', form)
    localStorage.setItem('token', r.token)
    localStorage.setItem(
      'loginUser',
      JSON.stringify({
        username: r.username,
        realName: r.realName,
        role: r.role,
        roleLabel: r.roleLabel,
        expiresAt: r.expiresAt,
      }),
    )
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  color: #e7ecf5;
}

.login-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
}

.login-bg-gradient {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 80% 60% at 15% 20%, rgba(37, 99, 235, 0.45), transparent 55%),
    radial-gradient(ellipse 70% 50% at 85% 80%, rgba(6, 182, 212, 0.28), transparent 50%),
    linear-gradient(145deg, #0b1220 0%, #111827 42%, #0f172a 100%);
}

.login-bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse 90% 80% at 50% 50%, #000 20%, transparent 100%);
}

.login-bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.55;
  animation: orb-float 18s ease-in-out infinite;
}

.login-bg-orb--1 {
  width: 420px;
  height: 420px;
  top: -80px;
  left: -60px;
  background: #2563eb;
}

.login-bg-orb--2 {
  width: 360px;
  height: 360px;
  right: 10%;
  bottom: -100px;
  background: #06b6d4;
  animation-delay: -6s;
}

.login-bg-orb--3 {
  width: 280px;
  height: 280px;
  top: 40%;
  left: 45%;
  background: #3b82f6;
  opacity: 0.25;
  animation-delay: -12s;
}

@keyframes orb-float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(24px, -18px) scale(1.06); }
}

.login-shell {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(320px, 1.05fr) minmax(380px, 0.95fr);
  min-height: 100vh;
  max-width: 1180px;
  margin: 0 auto;
  padding: 32px 28px;
  gap: 28px;
  align-items: center;
}

.login-brand {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 64px);
  padding: 12px 8px 12px 16px;
}

.brand-badge {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-logo {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: 16px;
  background: linear-gradient(135deg, #3b82f6, #06b6d4);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
  font-size: 18px;
  letter-spacing: 0.4px;
  box-shadow: 0 16px 36px rgba(37, 99, 235, 0.4);
}

.brand-text strong {
  display: block;
  font-size: 20px;
  font-weight: 800;
  color: #fff;
  letter-spacing: 0.2px;
}

.brand-text small {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #94a3b8;
  letter-spacing: 0.6px;
  text-transform: uppercase;
}

.brand-hero {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 48px 0 32px;
}

.brand-hero h1 {
  margin: 0;
  font-size: clamp(32px, 4vw, 44px);
  font-weight: 800;
  line-height: 1.18;
  letter-spacing: -0.5px;
  color: #f8fafc;
}

.brand-hero h1 em {
  font-style: normal;
  background: linear-gradient(90deg, #60a5fa, #22d3ee);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.brand-hero p {
  margin: 20px 0 0;
  max-width: 460px;
  font-size: 15px;
  line-height: 1.85;
  color: #94a3b8;
}

.brand-features {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.brand-features li {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(12px);
  font-size: 14px;
  font-weight: 600;
  color: #cbd5e1;
  transition: background 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}

.brand-features li:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(96, 165, 250, 0.35);
  transform: translateX(4px);
}

.brand-features .el-icon {
  font-size: 18px;
  color: #60a5fa;
}

.brand-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 28px;
  font-size: 12px;
  color: #64748b;
}

.version-tag {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.2);
  border: 1px solid rgba(96, 165, 250, 0.35);
  color: #93c5fd;
  font-weight: 700;
}

.login-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 12px 0;
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: 36px 36px 32px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.97);
  border: 1px solid rgba(255, 255, 255, 0.65);
  box-shadow:
    0 32px 64px rgba(0, 0, 0, 0.28),
    0 0 0 1px rgba(255, 255, 255, 0.06) inset;
  backdrop-filter: blur(24px);
  animation: card-rise 0.55s cubic-bezier(0.22, 1, 0.36, 1);
}

@keyframes card-rise {
  from {
    opacity: 0;
    transform: translateY(20px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.login-card__header {
  text-align: center;
  margin-bottom: 28px;
}

.login-card__icon {
  width: 56px;
  height: 56px;
  margin: 0 auto 16px;
  border-radius: 18px;
  background: linear-gradient(135deg, #eff6ff, #dbeafe);
  color: #2563eb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.15);
}

.login-card__header h2 {
  margin: 0;
  font-size: 26px;
  font-weight: 800;
  color: #111827;
  letter-spacing: 0.2px;
}

.login-card__header p {
  margin: 8px 0 0;
  font-size: 14px;
  color: #667085;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.field label {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 700;
  color: #344054;
  letter-spacing: 0.2px;
}

.login-hint {
  margin: 0;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e7eaf0;
  font-size: 12px;
  line-height: 1.7;
  color: #667085;
}

.login-btn {
  width: 100%;
  height: 48px;
  margin-top: 4px;
  border-radius: 12px !important;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 2px;
  background: linear-gradient(135deg, #2563eb, #0ea5e9) !important;
  border: none !important;
  box-shadow: 0 14px 28px rgba(37, 99, 235, 0.32);
}

.login-btn:hover {
  background: linear-gradient(135deg, #1d4ed8, #0284c7) !important;
  box-shadow: 0 18px 32px rgba(37, 99, 235, 0.38) !important;
}

.login-btn__arrow {
  margin-left: 6px;
  font-size: 16px;
  transition: transform 0.2s ease;
}

.login-btn:hover .login-btn__arrow {
  transform: translateX(3px);
}

.login-copyright {
  margin: 20px 0 0;
  font-size: 12px;
  color: #64748b;
}

@media (max-width: 960px) {
  .login-shell {
    grid-template-columns: 1fr;
    max-width: 480px;
    padding: 24px 20px;
    gap: 0;
  }

  .login-brand {
    min-height: auto;
    padding: 8px 4px 24px;
  }

  .brand-hero {
    padding: 24px 0 20px;
  }

  .brand-hero h1 {
    font-size: 28px;
  }

  .brand-features {
    display: none;
  }

  .brand-footer {
    margin-top: 0;
  }

  .login-card {
    padding: 28px 24px 24px;
    border-radius: 20px;
  }
}
</style>
