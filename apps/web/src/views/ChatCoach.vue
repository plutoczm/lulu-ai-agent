<template>
  <div class="coach-page">
    <AppTopNav />

    <main class="page-shell coach-shell">
      <section class="coach-hero v2-card">
        <div>
          <span class="coach-kicker">
            <AppIcon name="heart" :size="18" /> AI 对话军师
          </span>
          <h1>正在聊天，不知道下一句怎么回？</h1>
          <p>
            粘贴最近几句真实对话，直接给你 A 激进、B 正常、C 保守三条回复。
            三条都只供参考，点击只会复制，不会替你自动发送。
          </p>
        </div>
        <div class="coach-hero-side">
          <img src="../assets/illustrations/lulu-chat-coach-hero.jpg" alt="情绪陪伴中的噜噜" />
          <div class="hero-channels">
            <span class="channel-badge web">Web 对话军师</span>
          </div>
        </div>
      </section>

      <section class="coach-grid">
        <section class="input-card v2-card">
          <div class="section-head">
            <div>
              <span class="step">01</span>
              <div>
                <h2>贴上最近对话</h2>
                <p>只需要和当前问题有关的几句，不必上传整段聊天历史。</p>
              </div>
            </div>
            <button class="example-btn" @click="useExample">填入示例</button>
          </div>

          <div class="form-grid">
            <label>
              <span>对方称呼</span>
              <input v-model="otherAlias" placeholder="例如：小林" />
            </label>
            <label>
              <span>关系阶段</span>
              <select v-model="relationshipStage">
                <option>刚认识</option>
                <option>朋友</option>
                <option>暧昧期</option>
                <option>恋爱中</option>
                <option>冲突 / 冷淡</option>
                <option>分手后</option>
                <option>其他</option>
              </select>
            </label>
            <label>
              <span>这轮目标</span>
              <select v-model="goal">
                <option>自动判断</option>
                <option>承接情绪</option>
                <option>轻松调侃</option>
                <option>自然推进</option>
                <option>提出邀约</option>
                <option>澄清关系</option>
                <option>体面收线</option>
              </select>
            </label>
            <label>
              <span>你的聊天风格</span>
              <input v-model="userStyle" placeholder="短句、自然、少油腻" />
            </label>
          </div>
          <label class="conversation-field">
            <span>最近对话</span>
            <textarea
              v-model="conversationText"
              rows="11"
              placeholder="我：今天还顺利吗&#10;对方：累死了，今天开了一天会"
            ></textarea>
            <small>建议每行一条消息，用“我：”和“对方：”标记说话人。</small>
          </label>

          <div v-if="errorMessage" class="error-box">{{ errorMessage }}</div>

          <button
            class="generate-btn"
            :disabled="loading"
            @click="generateSuggestion"
          >
            <span>{{ loading ? '正在分析当前对话…' : '生成回复建议' }}</span>
            <span>→</span>
          </button>
        </section>

        <section class="result-card v2-card">
          <div class="section-head">
            <div>
              <span class="step">02</span>
              <div>
                <h2>这一句怎么回</h2>
                <p>优先给能直接发送的成品，不先讲一大堆理论。</p>
              </div>
            </div>
            <span v-if="result?.mainStrategy" class="strategy-badge">
              {{ result.mainStrategy }}
            </span>
          </div>

          <div v-if="!result && !loading" class="empty-result">
            <span class="empty-icon lulu-avatar"><img src="../assets/brand/lulu-capybara-mark.jpg" alt="" /></span>
            <strong>等你贴一段真实对话</strong>
            <p>
              军师会先判断这一轮更适合承接、降压、调侃、推进、邀约、
              澄清还是收线，然后只解决一个主要问题。
            </p>
          </div>

          <div v-else-if="loading" class="loading-result">
            <span class="loading-dot"></span>
            <strong>正在判断当前聊天节奏</strong>
            <p>会结合 DeepSeek、关系知识库和你提供的真实上下文。</p>
          </div>

          <div v-else class="reply-plan">
            <div v-if="result.needsClarification" class="clarify-card">
              <strong>还差一个关键信息</strong>
              <p>{{ result.clarificationQuestion }}</p>
            </div>

            <template v-else>
              <p class="diagnosis">{{ result.diagnosis }}</p>
              <div class="reply-options">
                <article
                  v-for="option in replyOptions"
                  :key="option.key"
                  class="reply-option-card"
                  :class="option.tone"
                >
                  <div class="reply-label">
                    <span>{{ option.label }}</span>
                    <button @click="copyText(option.text, option.key)">
                      {{ copiedKey === option.key ? '已复制' : '复制' }}
                    </button>
                  </div>
                  <p>{{ option.text }}</p>
                  <small>{{ option.hint }}</small>
                </article>
              </div>

              <div class="branches">
                <h3>对方接下来怎么回，你怎么接</h3>
                <div class="branch-grid">
                  <div class="branch positive">
                    <span>积极</span>
                    <p>{{ result.branches?.positive }}</p>
                  </div>
                  <div class="branch ambiguous">
                    <span>含糊</span>
                    <p>{{ result.branches?.ambiguous }}</p>
                  </div>
                  <div class="branch reject">
                    <span>拒绝 / 不适</span>
                    <p>{{ result.branches?.reject }}</p>
                  </div>
                </div>
              </div>

              <div class="next-step">
                <strong>现在只做这一件事</strong>
                <p>{{ result.nextStep }}</p>
              </div>

              <details class="evidence">
                <summary>查看事实与不确定项</summary>
                <div class="evidence-grid">
                  <div>
                    <strong>当前能确认</strong>
                    <ul>
                      <li v-for="item in result.facts || []" :key="item">{{ item }}</li>
                    </ul>
                  </div>
                  <div>
                    <strong>仍然未知</strong>
                    <ul>
                      <li v-for="item in result.uncertainties || []" :key="item">{{ item }}</li>
                    </ul>
                  </div>
                </div>
              </details>
            </template>
          </div>
        </section>

        <aside class="channel-card v2-card">
          <div class="channel-title">
            <AppIcon name="tools" :size="18" />
            <div>
              <strong>渠道接入</strong>
              <span>同一套军师引擎，多渠道复用</span>
            </div>
          </div>
          <div class="channel-list">
            <div class="channel-item">
              <span class="channel-logo mobile">A</span>
              <div>
                <strong>Android 手机聊天助手</strong>
                <p>同一个手机端覆盖微信和 QQ：分享/选中文字后，在当前聊天界面上方显示 A/B/C 三条建议。</p>
              </div>
              <span class="state building">开发中</span>
            </div>

            <div class="channel-item">
              <span class="channel-logo web">W</span>
              <div>
                <strong>Web 对话军师</strong>
                <p>当前网页版不区分聊天来源，直接粘贴对话并生成 A/B/C。</p>
              </div>
              <span class="state ready">可用</span>
            </div>

            <div class="channel-item">
              <span class="channel-logo qq">Q</span>
              <div>
                <strong>QQ 官方机器人（可选）</strong>
                <p>
                  {{ systemStatus.qqBotReady
                    ? '已连接，可作为额外的 QQ 入口。'
                    : '保留为可选渠道，不替代 Android 手机助手。'
                  }}
                </p>
              </div>
              <span
                class="state"
                :class="systemStatus.qqBotReady ? 'ready' : 'planned'"
              >
                {{ systemStatus.qqBotReady
                  ? '已连接'
                  : (systemStatus.qqBotEnabled ? '连接中' : '可选')
                }}
              </span>
            </div>
          </div>
          <div class="privacy-note">
            <AppIcon name="brain" :size="16" />
            <p>
              默认不读取、解密或批量导出个人微信/QQ数据库。
              只分析你主动提供的聊天内容。
            </p>
          </div>
        </aside>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AppTopNav from '../components/v2/AppTopNav.vue'
import AppIcon from '../components/v2/AppIcon.vue'
import { getSystemStatus, suggestConversationReply } from '../api'

const otherAlias = ref('')
const relationshipStage = ref('暧昧期')
const goal = ref('自动判断')
const userStyle = ref('短句、自然、不油腻')
const conversationText = ref('')
const result = ref(null)
const loading = ref(false)
const errorMessage = ref('')
const copiedKey = ref('')

const replyOptions = computed(() => {
  if (!result.value || result.value.needsClarification) return []
  const alternatives = result.value.alternatives || []
  return [
    {
      key: 'A',
      label: 'A｜激进',
      tone: 'aggressive',
      text: alternatives[0]?.text || '',
      hint: '更主动、更明确地推进，但仍尊重对方边界。'
    },
    {
      key: 'B',
      label: 'B｜正常',
      tone: 'normal',
      text: result.value.bestReply || '',
      hint: '自然、均衡，默认最适合当前聊天节奏。'
    },
    {
      key: 'C',
      label: 'C｜保守',
      tone: 'conservative',
      text: alternatives[1]?.text || '',
      hint: '更克制，降低推进强度，给对方更多空间。'
    }
  ].filter(option => option.text)
})
const systemStatus = ref({
  qqBotEnabled: false,
  qqBotReady: false
})

const useExample = () => {
  otherAlias.value = '小林'
  relationshipStage.value = '暧昧期'
  goal.value = '自动判断'
  userStyle.value = '短句、自然、偶尔开玩笑，不喜欢油腻'
  conversationText.value =
    '我：今天还顺利吗\n对方：累死了，今天开了一天会'
}

const parseMessages = () => {
  const lines = conversationText.value
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)

  return lines.map((line, index) => {
    const match = line.match(/^([^：:]{1,20})[：:]\s*(.+)$/)
    if (!match) {
      return {
        sender: otherAlias.value || '对方',
        text: line,
        time: ''
      }
    }

    const rawSender = match[1].trim()
    const sender =
      rawSender === '我'
        ? '我'
        : rawSender === '对方'
          ? (otherAlias.value || '对方')
          : rawSender

    return {
      sender,
      text: match[2].trim(),
      time: String(index + 1)
    }
  })
}

const generateSuggestion = async () => {
  errorMessage.value = ''
  result.value = null
  const messages = parseMessages()
  if (!messages.length) {
    errorMessage.value = '请先贴上最近几句对话。'
    return
  }

  loading.value = true
  try {
    const scope = [
      'coach',
      'web',
      otherAlias.value || 'unknown'
    ].join(':')

    const response = await suggestConversationReply({
      platform: 'web',
      conversationId: scope,
      userAlias: '我',
      otherAlias: otherAlias.value || '对方',
      relationshipStage: relationshipStage.value,
      goal: goal.value,
      userStyle: userStyle.value,
      messages
    })

    result.value = response.data
  } catch (error) {
    console.error('Conversation coach error:', error)
    errorMessage.value =
      error?.response?.data?.message ||
      '生成建议失败，请确认后端服务正常。'
  } finally {
    loading.value = false
  }
}

const copyText = async (text, key = '') => {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    copiedKey.value = key
    window.setTimeout(() => {
      if (copiedKey.value === key) copiedKey.value = ''
    }, 1400)
  } catch (error) {
    console.error('Copy failed:', error)
  }
}

onMounted(async () => {
  try {
    systemStatus.value = (await getSystemStatus()).data
  } catch (error) {
    console.error('System status error:', error)
  }
})
</script>
<style scoped>
.coach-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at 72% 9%, rgba(222,172,114,.12), transparent 26%),
    linear-gradient(180deg, #fefcf9 0%, #fcf8f4 100%);
}
.coach-shell { padding-top: 18px; padding-bottom: 40px; }
.coach-hero {
  min-height: 150px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 28px;
  padding: 25px 30px;
  background: linear-gradient(120deg, #fff, #fefcf9 58%, #fdf7f0);
}
.coach-kicker {
  display: inline-flex; align-items: center; gap: 7px;
  color: #d09b5e; font-size: 12px; font-weight: 800;
}
.coach-hero h1 {
  margin: 9px 0 7px; color: #403022;
  font-size: 32px; letter-spacing: -1px;
}
.coach-hero p {
  max-width: 720px; margin: 0; color: #748199;
  font-size: 13px; line-height: 1.7;
}
.coach-hero-side {
  position: relative;
  width: 360px;
  height: 126px;
  flex: none;
  overflow: hidden;
  border-radius: 20px;
  box-shadow: 0 12px 28px rgba(166, 104, 39, .12);
}
.coach-hero-side > img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 45%;
}
.hero-channels {
  position: absolute;
  right: 10px;
  bottom: 9px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.channel-badge {
  min-width: 48px; min-height: 32px;
  display: grid; place-items: center;
  padding: 0 10px; border-radius: 999px;
  font-size: 11px; font-weight: 800;
}
.channel-badge.web { color: #cb9a63; background: #fdf6ef; }

.coach-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 285px;
  gap: 14px;
  margin-top: 14px;
  align-items: start;
}
.input-card, .result-card, .channel-card { padding: 18px; }
.section-head {
  display: flex; justify-content: space-between;
  align-items: flex-start; gap: 12px;
  margin-bottom: 16px;
}
.section-head > div { display: flex; gap: 10px; }
.step {
  width: 30px; height: 30px; display: grid; place-items: center;
  border-radius: 9px; color: #fff;
  background: linear-gradient(135deg, #e1ad72, #e2a35b);
  font-size: 10px; font-weight: 800;
}
.section-head h2 { margin: 0; color: #4a392b; font-size: 16px; }
.section-head p { margin: 4px 0 0; color: #8b96a9; font-size: 10px; }
.example-btn {
  min-height: 30px; padding: 0 10px;
  border: 1px solid #f3e7da; border-radius: 9px;
  color: #cb9b64; background: #fefcf9;
  font-size: 10px; font-weight: 700;
}
.form-grid {
  display: grid; grid-template-columns: 1fr 1fr;
  gap: 11px; margin-bottom: 13px;
}
label > span {
  display: block; margin-bottom: 6px;
  color: #66738b; font-size: 10px; font-weight: 700;
}
input, select, textarea {
  width: 100%; border: 1px solid #f3e8db;
  border-radius: 10px; color: #564332;
  background: #fff; outline: none;
}
input, select { height: 38px; padding: 0 10px; font-size: 11px; }
textarea {
  resize: vertical; min-height: 220px; padding: 11px 12px;
  font-size: 12px; line-height: 1.7;
}
input:focus, select:focus, textarea:focus {
  border-color: #e6be90;
  box-shadow: 0 0 0 3px rgba(228,174,112,.08);
}
.conversation-field small {
  display: block; margin-top: 6px; color: #9aa4b4; font-size: 9px;
}
.error-box {
  margin-top: 10px; padding: 9px 11px;
  border-radius: 9px; color: #c84a58;
  background: #fff0f2; font-size: 10px;
}
.generate-btn {
  width: 100%; min-height: 44px;
  display: flex; justify-content: center; align-items: center; gap: 8px;
  margin-top: 13px; border: 0; border-radius: 11px;
  color: #fff; background: linear-gradient(135deg, #e1ac70, #e3a35a);
  font-size: 12px; font-weight: 800;
}
.generate-btn:disabled { opacity: .6; cursor: wait; }
.strategy-badge {
  padding: 5px 9px; border-radius: 999px;
  color: #cc995e; background: #fdf7f0;
  font-size: 10px; font-weight: 800;
}
.empty-result, .loading-result {
  min-height: 430px; display: grid; place-items: center;
  align-content: center; text-align: center; padding: 30px;
  border-radius: 14px; background: #fefcfa;
}
.empty-icon {
  width: 52px; height: 52px; display: grid; place-items: center;
  margin-bottom: 11px; border-radius: 15px;
  color: #d3a26b; background: #fdf5ec;
}
.empty-icon.lulu-avatar {
  overflow: hidden;
  border-radius: 18px;
  border: 1px solid #f3dfc6;
  background: #fff3df;
}
.empty-icon.lulu-avatar img {
  width: 100%; height: 100%; object-fit: cover; object-position: center 42%;
}
.empty-result strong, .loading-result strong {
  color: #6e5540; font-size: 13px;
}
.empty-result p, .loading-result p {
  max-width: 360px; margin: 7px 0 0;
  color: #8d98aa; font-size: 10px; line-height: 1.7;
}
.loading-dot {
  width: 13px; height: 13px; margin-bottom: 13px;
  border-radius: 50%; background: #dca86c;
  box-shadow: 0 0 0 8px rgba(220,168,108,.10);
  animation: pulse 1s infinite alternate;
}
@keyframes pulse { to { transform: scale(1.16); opacity: .65; } }
.diagnosis {
  margin: 0 0 12px; color: #68758c;
  font-size: 11px; line-height: 1.65;
}
.reply-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 9px;
}
.reply-option-card {
  padding: 13px;
  border: 1px solid #f3e8db;
  border-radius: 13px;
  background: #fff;
}
.reply-option-card.aggressive { background: #fff8f5; border-color: #f4ddd3; }
.reply-option-card.normal { background: #fefbf6; border-color: #edd7ba; }
.reply-option-card.conservative { background: #f8fbfb; border-color: #dbe9e7; }
.reply-label {
  display: flex; justify-content: space-between; align-items: center;
  gap: 10px; color: #cfa26e; font-size: 10px; font-weight: 800;
}
.reply-label button {
  min-height: 26px; padding: 0 8px;
  border: 1px solid #f4e8da; border-radius: 8px;
  color: #cc9d67; background: #fff; font-size: 9px;
}
.reply-option-card > p {
  margin: 10px 0 0;
  color: #524030;
  font-size: 12px;
  line-height: 1.7;
}
.reply-option-card small {
  display: block;
  margin-top: 8px;
  color: #98a2b2;
  font-size: 8px;
  line-height: 1.5;
}
.branches { margin-top: 15px; }
.branches h3 {
  margin: 0 0 8px; color: #6f5641;
  font-size: 11px;
}
.branch-grid { display: grid; gap: 7px; }
.branch {
  display: grid; grid-template-columns: 70px 1fr;
  gap: 9px; padding: 9px 10px;
  border-radius: 10px; background: #fefcfa;
}
.branch > span {
  font-size: 9px; font-weight: 800;
}
.branch p {
  margin: 0; color: #728096;
  font-size: 9px; line-height: 1.55;
}
.branch.positive > span { color: #15955a; }
.branch.ambiguous > span { color: #c7801e; }
.branch.reject > span { color: #ca5260; }

.next-step {
  margin-top: 12px; padding: 11px 12px;
  border-left: 3px solid #d9a973;
  border-radius: 0 10px 10px 0; background: #fefbf7;
}
.next-step strong { color: #c58e50; font-size: 10px; }
.next-step p {
  margin: 5px 0 0; color: #68758b;
  font-size: 10px; line-height: 1.6;
}
.evidence {
  margin-top: 12px; border-top: 1px solid #f8f1ea;
  padding-top: 10px;
}
.evidence summary {
  color: #748197; font-size: 9px; cursor: pointer;
}
.evidence-grid {
  display: grid; grid-template-columns: 1fr 1fr;
  gap: 10px; margin-top: 9px;
}
.evidence-grid strong { color: #59667f; font-size: 9px; }
.evidence-grid ul { margin: 6px 0 0; padding-left: 15px; }
.evidence-grid li {
  margin-bottom: 4px; color: #8a95a7;
  font-size: 8px; line-height: 1.45;
}
.clarify-card {
  padding: 16px; border-radius: 12px;
  color: #6c5a2e; background: #fff8e9;
}
.clarify-card strong { font-size: 11px; }
.clarify-card p { margin: 6px 0 0; font-size: 10px; line-height: 1.6; }

.channel-title {
  display: flex; align-items: center; gap: 9px;
  color: #ca985f;
}
.channel-title strong { display: block; color: #5b4735; font-size: 12px; }
.channel-title span { display: block; margin-top: 3px; color: #98a2b2; font-size: 8px; }
.channel-list {
  display: grid; gap: 9px; margin-top: 14px;
}
.channel-item {
  display: grid; grid-template-columns: 34px 1fr auto;
  gap: 9px; align-items: center;
  padding: 10px; border: 1px solid #f7efe6;
  border-radius: 11px; background: #fefcfa;
}
.channel-logo {
  width: 34px; height: 34px; display: grid; place-items: center;
  border-radius: 10px; color: #fff; font-size: 11px; font-weight: 900;
}
.channel-logo.mobile { background: #6f8f7d; }
.channel-logo.qq { background: #dc9c52; }
.channel-logo.web { background: #d1a471; }
.channel-item strong {
  display: block; color: #6f5641; font-size: 10px;
}
.channel-item p {
  margin: 3px 0 0; color: #939dae;
  font-size: 8px; line-height: 1.45;
}
.state {
  padding: 4px 7px; border-radius: 999px;
  font-size: 8px; font-weight: 800; white-space: nowrap;
}
.state.planned { color: #9c721c; background: #fff5db; }
.state.building { color: #c8955a; background: #fdf7f0; }
.state.ready { color: #168c55; background: #e8f9ef; }
.privacy-note {
  display: flex; gap: 8px; margin-top: 13px;
  padding: 11px; border-radius: 11px;
  color: #6b7186; background: #fbf6f2;
}
.privacy-note p {
  margin: 0; font-size: 8px; line-height: 1.55;
}

@media (max-width: 1180px) {
  .coach-grid {
    grid-template-columns: 1fr 1fr;
  }
  .channel-card { grid-column: 1 / -1; }
  .channel-list { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 820px) {
  .coach-hero { align-items: flex-start; flex-direction: column; }
  .coach-hero-side { width: 100%; height: 150px; }
  .coach-hero h1 { font-size: 26px; }
  .coach-grid { grid-template-columns: 1fr; }
  .channel-card { grid-column: auto; }
  .channel-list { grid-template-columns: 1fr; }
  .form-grid, .reply-options, .evidence-grid { grid-template-columns: 1fr; }
}
</style>
