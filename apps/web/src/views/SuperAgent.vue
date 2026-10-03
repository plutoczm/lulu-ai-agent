<template>
  <div class="agent-page">
    <AppTopNav />

    <div class="agent-layout page-shell">
      <aside class="agent-sidebar v2-card">
        <button class="new-task" @click="newConversation">
          <span>＋</span> 新建任务
        </button>

        <div class="side-section">
          <div class="side-title">AI 应用</div>
          <router-link class="side-link active" to="/super-agent">
            <span class="side-icon"><AppIcon name="bot" /></span>
            AI超级智能体
          </router-link>
          <router-link class="side-link" to="/chat-coach">
            <span class="side-icon love"><AppIcon name="heart" /></span>
            AI对话军师
          </router-link>
          <router-link class="side-link" to="/status">
            <span class="side-icon"><AppIcon name="activity" /></span>
            系统状态
          </router-link>
        </div>

        <div class="side-divider"></div>
        <div class="side-section">
          <div class="side-title">工具能力</div>
          <button v-for="tool in tools" :key="tool.label" class="tool-link" @click="sendMessage(tool.prompt)">
            <span class="side-icon" :class="tool.tone">
              <AppIcon :name="tool.icon" :size="16" />
            </span>
            <span>{{ tool.label }}</span>
          </button>
        </div>

        <div class="side-tip">
          <span class="tip-icon lulu-tip"><img src="../assets/brand/lulu-capybara-mark.jpg" alt="" /></span>
          <strong>复杂任务交给噜噜</strong>
          <p>简单问题直接回答，需要外部信息时才启动工具。</p>
        </div>
      </aside>

      <main class="agent-main">
        <section class="agent-hero v2-card">
          <div>
            <span class="agent-kicker"><AppIcon name="bot" :size="18" /> AI超级智能体</span>
            <h1>搜索、工具、任务协作，<br />一站式完成复杂工作</h1>
            <p>简单问题走 Fast Path；真正需要外部信息、文件或网页时才进入 Agent 工具流程。</p>
          </div>
          <img src="../assets/illustrations/lulu-super-agent-hero.gif" alt="AI超级智能体" />
          <span class="model-chip"><span class="status-dot"></span> 当前模型：{{ modelName }}</span>
        </section>

        <div class="capability-strip">
          <button v-for="tool in tools" :key="tool.label" @click="sendMessage(tool.prompt)">
            <AppIcon :name="tool.icon" :size="16" />
            {{ tool.label }}
          </button>
        </div>

        <section class="agent-chat v2-card">
          <ChatRoom
            :messages="messages"
            :connection-status="connectionStatus"
            ai-type="super"
            placeholder="告诉我你想完成什么任务，例如：搜索资料、整理网页、生成报告…"
            @send-message="sendMessage"
          />
        </section>
      </main>

      <aside class="task-panel">
        <section class="execution-card v2-card">
          <div class="panel-heading">
            <div>
              <span class="panel-icon"><AppIcon name="tools" /></span>
              <strong>工具执行状态</strong>
            </div>
            <span class="task-state" :class="{ running: connectionStatus === 'connecting' }">
              {{ connectionStatus === 'connecting' ? '执行中' : '就绪' }}
            </span>
          </div>

          <div v-if="toolStatuses.length" class="timeline">
            <div v-for="(status, index) in toolStatuses" :key="index" class="timeline-item">
              <span class="timeline-dot">✓</span>
              <div>
                <strong>{{ status.title }}</strong>
                <p>{{ status.detail }}</p>
              </div>
            </div>
          </div>

          <div v-else class="empty-execution">
            <span class="empty-icon lulu-empty"><img src="../assets/brand/lulu-capybara-mark.jpg" alt="" /></span>
            <strong>噜噜正在等一个任务</strong>
            <p>普通问答不会启动 Agent；需要搜索、网页、文件等操作时，这里会显示真实执行状态。</p>
          </div>
        </section>

        <section class="result-card v2-card">
          <div class="panel-heading">
            <div>
              <span class="panel-icon result"><AppIcon name="file" /></span>
              <strong>任务结果</strong>
            </div>
          </div>
          <div class="result-content">
            <template v-if="lastAnswer">
              <span class="result-check">✓</span>
              <div>
                <strong>{{ connectionStatus === 'connecting' ? '正在生成结果' : '结果已返回' }}</strong>
                <p>{{ previewAnswer }}</p>
              </div>
            </template>
            <template v-else>
              <p class="result-placeholder">完成任务后，这里会给出结果摘要；文件型任务后续也可以在这里集中管理产物。</p>
            </template>
          </div>
        </section>

        <section class="agent-info v2-card">
          <div class="info-row">
            <span>主模型</span><b>{{ modelName }}</b>
          </div>
          <div class="info-row">
            <span>实时搜索</span><b>百度 Web Search</b>
          </div>
          <div class="info-row">
            <span>当前时间</span><b>系统时钟</b>
          </div>
          <div class="info-row">
            <span>执行模式</span><b>Semantic Router + Workflow</b>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import AppTopNav from '../components/v2/AppTopNav.vue'
import AppIcon from '../components/v2/AppIcon.vue'
import ChatRoom from '../components/ChatRoom.vue'
import { chatWithManus, getModelStatus } from '../api'

const messages = ref([])
const connectionStatus = ref('disconnected')
const modelName = ref('qwen-plus')
const toolStatuses = ref([])
let eventSource = null

const tools = [
  { label: '搜索', icon: 'search', tone: 'blue', prompt: '请搜索并整理我接下来描述的主题，先告诉我你需要我补充什么信息。' },
  { label: '文件', icon: 'file', tone: 'purple', prompt: '我有一个文件处理任务，请先告诉我你支持哪些文件操作。' },
  { label: '网页', icon: 'globe', tone: 'cyan', prompt: '我需要读取并整理网页内容，请告诉我可以如何提供网址。' },
  { label: '地图', icon: 'map', tone: 'green', prompt: '我有一个地图或路线相关任务，请先说明你目前可以使用的能力。' },
  { label: '图片', icon: 'image', tone: 'orange', prompt: '我需要搜索或处理图片，请先告诉我你目前可以完成哪些操作。' }
]

const addMessage = (content, isUser, type = '') => {
  messages.value.push({ content, isUser, type, time: Date.now() })
}

const parseStatus = (raw) => {
  const text = raw.replace('[STATUS]', '').trim()
  const match = text.match(/已完成工具调用[:：]\s*(.+)/)
  if (match) {
    return { title: '工具调用完成', detail: match[1] }
  }
  return { title: '任务执行', detail: text }
}

const sendMessage = (message) => {
  if (!message || connectionStatus.value === 'connecting') return
  addMessage(message, true, 'user-question')
  toolStatuses.value = []

  if (eventSource) eventSource.close()

  connectionStatus.value = 'connecting'
  let answerIndex = -1
  eventSource = chatWithManus(message)

  eventSource.onmessage = (event) => {
    const data = event.data
    if (!data) return

    if (data === '[DONE]') {
      connectionStatus.value = 'disconnected'
      eventSource.close()
      return
    }

    if (data.startsWith('[STATUS]')) {
      toolStatuses.value.push(parseStatus(data))
      return
    }

    if (answerIndex === -1) {
      addMessage('', false, 'ai-answer')
      answerIndex = messages.value.length - 1
    }
    messages.value[answerIndex].content += data
  }

  eventSource.onerror = (error) => {
    console.error('SSE Error:', error)
    connectionStatus.value = 'error'
    eventSource.close()
  }
}

const newConversation = () => {
  if (eventSource) eventSource.close()
  connectionStatus.value = 'disconnected'
  toolStatuses.value = []
  messages.value = []
  addMessage('新的任务已经准备好。直接告诉我最终想得到什么结果，我会判断是否需要调用工具。', false, 'ai-answer')
}

const lastAnswer = computed(() => {
  const found = [...messages.value].reverse().find(item => !item.isUser && item.type !== 'agent-status' && item.content)
  return found?.content || ''
})

const previewAnswer = computed(() => {
  const text = lastAnswer.value.replace(/\s+/g, ' ').trim()
  return text.length > 105 ? text.slice(0, 105) + '…' : text
})

onMounted(async () => {
  try {
    modelName.value = (await getModelStatus()).data.agentPrimary || 'qwen-plus'
  } catch (error) {
    console.error('Model status error:', error)
  }
  addMessage('你好。普通问题我会直接回答；只有真的需要搜索、网页、文件或多步执行时，我才会启动工具。', false, 'ai-answer')
})

onBeforeUnmount(() => {
  if (eventSource) eventSource.close()
})
</script>

<style scoped>
.agent-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at 72% 8%, rgba(234,183,125,.14), transparent 27%),
    linear-gradient(180deg, #fefcf9 0%, #fcf8f4 100%);
}
.agent-layout {
  display: grid;
  grid-template-columns: 210px minmax(0, 1fr) 290px;
  gap: 14px;
  padding-top: 16px;
  padding-bottom: 28px;
}
.agent-sidebar { padding: 14px 12px; align-self: start; position: sticky; top: 82px; }
.new-task {
  width: 100%; min-height: 42px; border: 1px solid #f3e7da; border-radius: 11px;
  color: #cc9250; background: #fff; font-size: 12px; font-weight: 700;
}
.new-task span { margin-right: 5px; font-size: 18px; vertical-align: -1px; }
.side-section { margin-top: 14px; }
.side-title { padding: 7px 9px; color: #9aa5b6; font-size: 10px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.side-link, .tool-link {
  width: 100%; min-height: 40px; padding: 0 9px; display: flex; align-items: center; gap: 9px;
  border: 0; border-radius: 10px; color: #63708a; background: transparent; font-size: 12px; font-weight: 600; text-align: left;
}
.side-link:hover, .tool-link:hover { color: #c89356; background: #fefaf5; }
.side-link.active { color: #cc9250; background: #fdf7f0; }
.side-icon {
  width: 28px; height: 28px; display: grid; place-items: center; border-radius: 9px;
  color: #d49c5c; background: #fdf6ef;
}
.side-icon.love { color: #ec6370; background: #fff0f2; }
.side-icon.purple { color: #d19d62; background: #fdf6ef; }
.side-icon.cyan { color: #b9803e; background: #fdf5ec; }
.side-icon.green { color: #1f9d68; background: #eafaf2; }
.side-icon.orange { color: #ee7c31; background: #fff1e8; }
.side-divider { height: 1px; margin: 14px 8px; background: #f8f1ea; }
.side-tip { margin-top: 18px; padding: 13px; border-radius: 14px; background: linear-gradient(145deg, #fdf7f0, #fdf8f3); }
.tip-icon { width: 32px; height: 32px; display: grid; place-items: center; border-radius: 9px; color: #d0995a; background: #fff; }
.tip-icon.lulu-tip { overflow: hidden; border-radius: 11px; }
.tip-icon.lulu-tip img { width: 100%; height: 100%; object-fit: cover; object-position: center 42%; }
.side-tip strong { display: block; margin-top: 9px; color: #5d4937; font-size: 12px; }
.side-tip p { margin: 4px 0 0; color: #8390a5; font-size: 10px; line-height: 1.55; }

.agent-main { min-width: 0; display: grid; grid-template-rows: auto auto 1fr; gap: 12px; }
.agent-hero {
  position: relative; min-height: 175px; display: grid; grid-template-columns: 1.05fr .95fr;
  align-items: center; overflow: hidden; padding-left: 30px;
  background: linear-gradient(110deg, #fff 0%, #fefbf8 50%, #fdf6ef 100%);
}
.agent-hero > div { z-index: 2; padding: 24px 0; }
.agent-kicker { display: inline-flex; align-items: center; gap: 7px; color: #ce985a; font-size: 12px; font-weight: 800; }
.agent-hero h1 { margin: 8px 0; color: #412f20; font-size: 30px; line-height: 1.2; letter-spacing: -1px; }
.agent-hero p { max-width: 530px; margin: 0; color: #7b879c; font-size: 11px; line-height: 1.65; }
.agent-hero img {
  width: 100%; height: 100%; object-fit: cover; object-position: center 54%;
  -webkit-mask-image: linear-gradient(90deg, transparent, black 18%, black 100%);
          mask-image: linear-gradient(90deg, transparent, black 18%, black 100%);
}
.model-chip {
  position: absolute; right: 15px; bottom: 14px; z-index: 3;
  display: inline-flex; align-items: center; gap: 7px; min-height: 30px; padding: 0 10px;
  border: 1px solid #f4eade; border-radius: 999px; color: #a77338; background: rgba(255,255,255,.9);
  font-size: 10px; font-weight: 650;
}

.capability-strip { display: flex; flex-wrap: wrap; gap: 8px; }
.capability-strip button {
  min-height: 34px; display: inline-flex; align-items: center; gap: 6px; padding: 0 12px;
  border: 1px solid #f3e8db; border-radius: 10px; color: #63708a; background: #fff;
  font-size: 11px; font-weight: 600;
}
.capability-strip button:hover { color: #cd9555; border-color: #eeddcb; background: #fefbf8; }

.agent-chat { min-height: 650px; overflow: hidden; }

.task-panel { align-self: start; display: grid; gap: 12px; position: sticky; top: 82px; }
.execution-card, .result-card, .agent-info { padding: 14px; }
.panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.panel-heading > div { display: flex; align-items: center; gap: 8px; }
.panel-heading strong { color: #5d4836; font-size: 12px; }
.panel-icon { width: 30px; height: 30px; display: grid; place-items: center; border-radius: 9px; color: #d4a064; background: #fdf6ef; }
.panel-icon.result { color: #159566; background: #eafaf2; }
.task-state { padding: 4px 8px; border-radius: 999px; color: #738097; background: #f9f4ee; font-size: 9px; font-weight: 700; }
.task-state.running { color: #cc9556; background: #fdf6ef; }

.timeline { margin-top: 14px; display: grid; gap: 11px; }
.timeline-item { display: grid; grid-template-columns: 20px 1fr; gap: 8px; align-items: start; }
.timeline-dot { width: 18px; height: 18px; display: grid; place-items: center; border-radius: 50%; color: #fff; background: #19b36c; font-size: 9px; }
.timeline-item strong { display: block; color: #6f5641; font-size: 10px; }
.timeline-item p { margin: 3px 0 0; color: #949eae; font-size: 9px; line-height: 1.45; overflow-wrap: anywhere; }

.empty-execution { margin-top: 14px; padding: 18px 12px; text-align: center; border-radius: 13px; background: #fefcf9; }
.empty-icon { width: 42px; height: 42px; margin: 0 auto 9px; display: grid; place-items: center; border-radius: 12px; color: #d7a367; background: #fdf5ec; }
.empty-icon.lulu-empty { overflow: hidden; border-radius: 14px; border: 1px solid #f1dfca; }
.empty-icon.lulu-empty img { width: 100%; height: 100%; object-fit: cover; object-position: center 42%; }
.empty-execution strong { display: block; color: #8d612f; font-size: 11px; }
.empty-execution p { margin: 6px 0 0; color: #929daf; font-size: 9px; line-height: 1.55; }

.result-content { margin-top: 13px; display: flex; gap: 9px; }
.result-check { width: 25px; height: 25px; flex: none; display: grid; place-items: center; border-radius: 8px; color: #16985e; background: #e8f9ef; font-weight: 800; }
.result-content strong { display: block; color: #705741; font-size: 10px; }
.result-content p { margin: 4px 0 0; color: #909bac; font-size: 9px; line-height: 1.55; }
.result-placeholder { margin: 0 !important; padding: 10px; border-radius: 10px; background: #fefcfa; }
.agent-info { display: grid; gap: 8px; }
.info-row { display: flex; justify-content: space-between; gap: 8px; color: #8a96a8; font-size: 9px; }
.info-row b { color: #8f6230; font-weight: 650; text-align: right; }

@media (max-width: 1180px) {
  .agent-layout { grid-template-columns: 190px minmax(0, 1fr); }
  .task-panel { display: none; }
}
@media (max-width: 820px) {
  .agent-layout { grid-template-columns: 1fr; }
  .agent-sidebar { display: none; }
  .agent-hero { grid-template-columns: 1fr; padding: 0 22px; }
  .agent-hero img { display: none; }
}
</style>
