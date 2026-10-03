<template>
  <div class="settings-page">
    <AppTopNav />

    <main class="page-shell settings-shell">
      <section class="settings-card">
        <header class="settings-head">
          <div>
            <p class="eyebrow">MODEL SETTINGS</p>
            <h1>模型配置</h1>
            <p>连接你的模型账号，让噜噜按场景选择合适的 AI。</p>
          </div>
          <router-link class="close-link" to="/status">×</router-link>
        </header>

        <div class="tabs">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            :class="{ active: activeTab === tab.key }"
            @click="activeTab = tab.key"
          >
            {{ tab.label }}
          </button>
        </div>
        <section v-if="activeTab === 'subscription'" class="tab-body">
          <div class="field">
            <label>模型服务商</label>
            <div class="select-like">
              <span class="provider-mark">O</span>
              <div>
                <strong>OpenAI · ChatGPT Plan</strong>
                <small>浏览器登录你的 ChatGPT 账号</small>
              </div>
              <span class="chevron">⌄</span>
            </div>
          </div>

          <div class="secure-note">
            <span class="shield">◇</span>
            <div>
              <strong>使用你的 ChatGPT 订阅账号连接</strong>
              <p>
                登录发生在 OpenAI 官方页面。授权凭据只保存在本机后端，
                不会进入浏览器 localStorage 或项目源码。
              </p>
            </div>
          </div>
          <div class="account-card">
            <template v-if="authStatus.signedIn">
              <div class="account-main">
                <span class="account-avatar">C</span>
                <div>
                  <strong>{{ authStatus.email || 'ChatGPT 账号' }}</strong>
                  <p>
                    {{ authStatus.planUsageEnabled
                      ? 'ChatGPT Plan 推理权限已授权'
                      : '已登录，但未授予 Plan 推理权限' }}
                  </p>
                </div>
                <span
                  class="state-pill"
                  :class="{ warn: !authStatus.planUsageEnabled }"
                >
                  {{ authStatus.planUsageEnabled ? '已连接' : '权限不足' }}
                </span>
              </div>
              <div class="account-actions">
                <button class="ghost-btn" :disabled="busy" @click="login(false)">
                  重新授权
                </button>
                <button class="ghost-btn" :disabled="busy" @click="login(true)">
                  切换账号
                </button>
                <button class="danger-btn" :disabled="busy" @click="logout">
                  退出登录
                </button>
              </div>
            </template>
            <template v-else>
              <div class="signed-out">
                <div>
                  <strong>尚未连接 ChatGPT</strong>
                  <p>点击下面按钮，在系统浏览器完成 OpenAI 授权。</p>
                </div>
                <button class="chatgpt-btn" :disabled="busy" @click="login(false)">
                  {{ busy ? '等待登录…' : 'Continue with ChatGPT' }}
                </button>
              </div>
            </template>
          </div>

          <template v-if="authStatus.signedIn && authStatus.planUsageEnabled">
            <div class="field model-field">
              <label>账号可用模型</label>
              <select v-model="selectedModel" :disabled="busy || !chatGptModels.length">
                <option
                  v-for="model in chatGptModels"
                  :key="model.slug"
                  :value="model.slug"
                >
                  {{ model.displayName }} · {{ model.slug }}
                </option>
              </select>
              <small>
                模型列表来自当前登录账号，不代表未实际调用前的最终 entitlement。
              </small>
            </div>
            <div class="route-grid">
              <article>
                <span>对话军师</span>
                <strong>{{ routeLabel('coach') }}</strong>
                <button
                  class="apply-btn"
                  :disabled="busy || !selectedModel"
                  @click="applyRoute('coach')"
                >
                  使用所选模型
                </button>
              </article>

              <article>
                <span>快速模型</span>
                <strong>{{ routeLabel('fast') }}</strong>
                <button
                  class="apply-btn"
                  :disabled="busy || !selectedModel"
                  @click="applyRoute('fast')"
                >
                  使用所选模型
                </button>
              </article>

              <article class="agent-route">
                <span>超级智能体</span>
                <strong>{{ routeLabel('agent') }}</strong>
                <button
                  class="apply-btn"
                  :disabled="busy || !selectedModel"
                  @click="applyRoute('agent')"
                >
                  使用 Codex Agent
                </button>
                <p>使用官方 Codex app-server，在本地工作目录内执行 Agent 任务。</p>
              </article>
            </div>
          </template>
        </section>

        <section v-else-if="activeTab === 'api'" class="tab-body">
          <div class="mode-intro">
            <span class="mode-icon"><AppIcon name="settings" /></span>
            <div>
              <h2>API Key 模式</h2>
              <p>
                当前 DeepSeek、DashScope 等密钥由后端 .env.local
                安全管理，不会回显到页面。
              </p>
            </div>
          </div>

          <div class="provider-list">
            <div v-for="model in apiModels" :key="model.providerId">
              <span>{{ model.providerName }}</span>
              <strong>{{ model.modelId }}</strong>
              <b>已配置</b>
            </div>
          </div>
        </section>

        <section v-else class="tab-body">
          <div class="mode-intro">
            <span class="mode-icon"><AppIcon name="globe" /></span>
            <div>
              <h2>自定义接口</h2>
              <p>
                预留 OpenAI-compatible Provider 接入位。
                后续可配置自定义 Base URL、模型目录和认证方式。
              </p>
            </div>
          </div>
        </section>

        <p v-if="message" class="result-message" :class="{ error: messageError }">
          {{ message }}
        </p>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AppTopNav from '../components/v2/AppTopNav.vue'
import AppIcon from '../components/v2/AppIcon.vue'
import {
  getChatGptAuthStatus,
  getChatGptModels,
  getModelStatus,
  logoutChatGpt,
  selectModelRoute,
  startChatGptLogin
} from '../api'

const tabs = [
  { key: 'subscription', label: '订阅登录' },
  { key: 'api', label: 'API Key' },
  { key: 'custom', label: '自定义接口' }
]

const activeTab = ref('subscription')
const authStatus = ref({ signedIn: false, planUsageEnabled: false })
const chatGptModels = ref([])
const modelStatus = ref({ models: [], routes: {} })
const selectedModel = ref('')
const busy = ref(false)
const message = ref('')
const messageError = ref(false)

const apiModels = computed(() => {
  const seen = new Set()
  return (modelStatus.value.models || [])
    .filter(item => item.providerId !== 'openai-chatgpt')
    .filter(item => {
      if (seen.has(item.providerId)) return false
      seen.add(item.providerId)
      return true
    })
})

const routeLabel = (route) => {
  return modelStatus.value.routes?.[route] || '—'
}

const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms))

const loadAll = async () => {
  const [auth, models] = await Promise.all([
    getChatGptAuthStatus(),
    getModelStatus()
  ])

  authStatus.value = auth.data
  modelStatus.value = models.data

  if (authStatus.value.signedIn && authStatus.value.planUsageEnabled) {
    const catalog = await getChatGptModels()
    chatGptModels.value = catalog.data || []

    const activeCoach = modelStatus.value.routes?.coach || ''
    const prefix = 'openai-chatgpt/'
    selectedModel.value = activeCoach.startsWith(prefix)
      ? activeCoach.slice(prefix.length)
      : (chatGptModels.value[0]?.slug || '')
  } else {
    chatGptModels.value = []
    selectedModel.value = ''
  }
}

const login = async (forceNew) => {
  message.value = ''
  messageError.value = false

  const popup = window.open(
    'about:blank',
    'lulu-chatgpt-login',
    'width=760,height=760'
  )

  busy.value = true
  try {
    const started = await startChatGptLogin(forceNew)
    if (!popup) {
      message.value = '浏览器阻止了弹窗，请允许此站点打开弹窗后重试。'
      messageError.value = true
      return
    }

    popup.location.href = started.data.authorizationUrl

    for (let i = 0; i < 120; i += 1) {
      await sleep(1500)
      const status = await getChatGptAuthStatus()
      authStatus.value = status.data

      if (status.data.signedIn) {
        await loadAll()
        message.value = status.data.planUsageEnabled
          ? 'ChatGPT 账号已连接，模型目录已刷新。'
          : '账号已登录，但没有 ChatGPT Plan 推理权限。'
        messageError.value = !status.data.planUsageEnabled
        return
      }
    }

    message.value = '登录等待超时，可以重新点击登录继续。'
    messageError.value = true
  } catch (error) {
    console.error('ChatGPT login error:', error)
    message.value = error?.response?.data?.message || 'ChatGPT 登录启动失败。'
    messageError.value = true
  } finally {
    busy.value = false
  }
}

const logout = async () => {
  busy.value = true
  message.value = ''
  try {
    const result = await logoutChatGpt()
    await loadAll()
    message.value = result.data.remoteRevoked
      ? '已退出 ChatGPT，并撤销可续期会话。'
      : '本地已退出；远端撤销未确认，可在 ChatGPT 设置中检查应用访问。'
    messageError.value = !result.data.remoteRevoked
  } catch (error) {
    console.error('ChatGPT logout error:', error)
    message.value = '退出登录失败。'
    messageError.value = true
  } finally {
    busy.value = false
  }
}

const applyRoute = async (route) => {
  if (!selectedModel.value) return

  busy.value = true
  message.value = ''
  messageError.value = false
  try {
    const candidate = `openai-chatgpt/${selectedModel.value}`
    await selectModelRoute(route, candidate)
    await loadAll()
    message.value = route === 'coach'
      ? '已将所选 ChatGPT 模型设为对话军师主模型。'
      : route === 'agent'
        ? '已将所选 ChatGPT 模型设为超级智能体的 Codex Agent。'
        : '已将所选 ChatGPT 模型设为快速模型。'
  } catch (error) {
    console.error('Model route error:', error)
    message.value = error?.response?.data?.message || '模型切换失败。'
    messageError.value = true
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  try {
    await loadAll()
  } catch (error) {
    console.error('Model settings load error:', error)
    message.value = '模型配置状态加载失败。'
    messageError.value = true
  }
})
</script>

<style scoped>
.settings-page {
  min-height: 100vh;
  padding-bottom: 60px;
  background:
    radial-gradient(circle at 50% 0, #fdf8f3 0, transparent 32%),
    #fcfaf7;
}

.settings-shell {
  display: flex;
  justify-content: center;
  padding-top: 34px;
}

.settings-card {
  width: min(800px, 100%);
  overflow: hidden;
  border: 1px solid #f5ebe0;
  border-radius: 22px;
  background: #fff;
  box-shadow: 0 26px 80px rgba(84,66,50,.12);
}

.settings-head {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 30px 34px 24px;
}

.eyebrow {
  margin: 0 0 12px;
  color: #d0a471;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: .18em;
}

.settings-head h1 {
  margin: 0;
  color: #362a20;
  font-size: 34px;
  letter-spacing: -.04em;
}

.settings-head > div > p:last-child {
  margin: 10px 0 0;
  color: #98a0af;
  font-size: 14px;
}

.close-link {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  color: #737987;
  font-size: 30px;
  line-height: 1;
}

.close-link:hover {
  background: #faf6f1;
}

.tabs {
  display: flex;
  gap: 28px;
  padding: 0 34px;
  border-bottom: 1px solid #f7f0e8;
}

.tabs button {
  position: relative;
  padding: 14px 4px 16px;
  border: 0;
  background: transparent;
  color: #777d8b;
  font-weight: 700;
  cursor: pointer;
}

.tabs button.active {
  color: #cea06c;
}

.tabs button.active::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 3px;
  border-radius: 3px;
  background: #d7ab78;
  content: '';
}

.tab-body {
  display: grid;
  gap: 22px;
  padding: 28px 34px 34px;
}

.field {
  display: grid;
  gap: 9px;
}

.field label {
  color: #4f3e2f;
  font-size: 14px;
  font-weight: 750;
}

.select-like,
.model-field select {
  width: 100%;
  min-height: 56px;
  border: 1px solid #f3e7d9;
  border-radius: 14px;
  background: #fff;
}

.select-like {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
}

.provider-mark,
.account-avatar {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  flex: none;
  border-radius: 50%;
  background: #231c15;
  color: #fff;
  font-weight: 800;
}

.select-like > div {
  flex: 1;
}

.select-like strong,
.select-like small {
  display: block;
}

.select-like strong {
  color: #48392b;
  font-size: 14px;
}

.select-like small {
  margin-top: 3px;
  color: #9aa1af;
  font-size: 11px;
}

.chevron {
  color: #a0a6b2;
  font-size: 20px;
}

.secure-note {
  display: flex;
  gap: 14px;
  padding: 18px;
  border-radius: 15px;
  background: #fef9f4;
  color: #c38b4a;
}

.secure-note strong {
  display: block;
  color: #ab7639;
  font-size: 13px;
}

.secure-note p {
  margin: 6px 0 0;
  color: #c8955b;
  font-size: 12px;
  line-height: 1.65;
}

.shield {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  flex: none;
  border: 2px solid #dfb88c;
  border-radius: 10px;
  font-weight: 800;
}

.account-card {
  padding: 18px;
  border: 1px solid #f5ebe0;
  border-radius: 16px;
  background: #fff;
}

.account-main,
.signed-out {
  display: flex;
  align-items: center;
  gap: 13px;
}

.account-main > div,
.signed-out > div {
  flex: 1;
}

.account-main strong,
.signed-out strong {
  color: #403225;
  font-size: 14px;
}

.account-main p,
.signed-out p {
  margin: 4px 0 0;
  color: #9299a8;
  font-size: 12px;
}

.state-pill {
  padding: 5px 9px;
  border-radius: 999px;
  background: #e9f8ef;
  color: #16875a;
  font-size: 11px;
  font-weight: 750;
}

.state-pill.warn {
  background: #fff2dc;
  color: #b26a00;
}

.account-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #f9f2eb;
}

button {
  font: inherit;
}

.chatgpt-btn,
.apply-btn,
.ghost-btn,
.danger-btn {
  min-height: 38px;
  padding: 0 14px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
}

.chatgpt-btn,
.apply-btn {
  border: 0;
  background: #241c15;
  color: #fff;
}

.ghost-btn {
  border: 1px solid #f2e5d7;
  background: #fff;
  color: #596277;
}

.danger-btn {
  border: 1px solid #f0d5d5;
  background: #fffafa;
  color: #b54c4c;
}

button:disabled,
select:disabled {
  cursor: not-allowed;
  opacity: .55;
}

.model-field select {
  padding: 0 14px;
  color: #4f3e2f;
  outline: none;
}

.model-field small {
  color: #9aa2b1;
  font-size: 11px;
  line-height: 1.5;
}

.route-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.route-grid article {
  min-height: 148px;
  padding: 16px;
  border: 1px solid #f6ede3;
  border-radius: 14px;
  background: #fefcfa;
}

.route-grid article > span {
  display: block;
  color: #8d95a5;
  font-size: 11px;
}

.route-grid article > strong {
  display: block;
  min-height: 36px;
  margin: 8px 0 12px;
  overflow-wrap: anywhere;
  color: #48392b;
  font-size: 12px;
  line-height: 1.45;
}

.route-grid .apply-btn {
  width: 100%;
  background: #d2a36e;
}

.agent-route p {
  margin: 7px 0 0;
  color: #929aab;
  font-size: 11px;
  line-height: 1.55;
}

.mode-intro {
  display: flex;
  gap: 14px;
  padding: 18px;
  border: 1px solid #f6ede3;
  border-radius: 15px;
  background: #fefcfa;
}

.mode-icon {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  flex: none;
  border-radius: 12px;
  background: #fdf7f0;
  color: #cb9b64;
}

.mode-intro h2 {
  margin: 0;
  color: #473729;
  font-size: 16px;
}

.mode-intro p {
  margin: 6px 0 0;
  color: #8d96a7;
  font-size: 12px;
  line-height: 1.65;
}

.provider-list {
  display: grid;
  gap: 9px;
}

.provider-list > div {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 12px;
  align-items: center;
  padding: 13px 15px;
  border: 1px solid #f6eee5;
  border-radius: 12px;
}

.provider-list span,
.provider-list strong,
.provider-list b {
  font-size: 12px;
}

.provider-list span {
  color: #657087;
}

.provider-list strong {
  color: #473729;
}

.provider-list b {
  color: #21855d;
}

.result-message {
  margin: 0;
  padding: 11px 14px;
  border-radius: 11px;
  background: #eef9f3;
  color: #217b58;
  font-size: 12px;
}

.result-message.error {
  background: #fff2f2;
  color: #b14a4a;
}

@media (max-width: 760px) {
  .settings-shell {
    padding-top: 14px;
  }

  .settings-card {
    border-radius: 18px;
  }

  .settings-head,
  .tab-body {
    padding-right: 20px;
    padding-left: 20px;
  }

  .tabs {
    gap: 18px;
    padding: 0 20px;
  }

  .route-grid {
    grid-template-columns: 1fr;
  }

  .signed-out {
    align-items: stretch;
    flex-direction: column;
  }

  .chatgpt-btn {
    width: 100%;
  }
}
</style>
