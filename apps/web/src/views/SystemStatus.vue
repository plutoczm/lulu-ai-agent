<template>
  <div class="status-page">
    <AppTopNav />

    <main class="page-shell">
      <section class="status-hero">
        <div>
          <p class="eyebrow">MODEL & SYSTEM STATUS</p>
          <h1>模型与系统状态</h1>
          <p>查看当前模型、知识库、长期记忆和搜索服务的真实运行状态。</p>
        </div>
        <img src="../assets/illustrations/lulu-system-status-hero.jpg" alt="噜噜系统状态" />
      </section>

      <section class="model-panel v2-card">
        <div class="panel-head">
          <div>
            <h2>模型配置</h2>
            <p>当前主模型来自后端 ModelRouter 实际状态。</p>
          </div>
          <span class="pill"><span class="status-dot"></span> 实时状态</span>
        </div>

        <div class="model-grid">
          <article
            v-for="model in models"
            :key="model.key"
            class="model-card"
            :class="{ selected: model.selected }"
          >
            <div class="model-icon" :class="model.tone">
              <AppIcon :name="model.icon" :size="25" />
            </div>
            <div class="model-copy">
              <div class="model-title">
                <strong>{{ model.name }}</strong>
                <span v-if="model.selected">当前主模型</span>
              </div>
              <p>{{ model.desc }}</p>
              <div class="model-status">
                <span class="status-dot" :class="{ off: !model.available }"></span>
                {{ model.available ? '运行中' : '未启用' }}
              </div>
            </div>
          </article>
        </div>
      </section>

      <section class="service-grid">
        <article class="service-card v2-card">
          <div class="service-title">
            <span class="service-icon purple"><AppIcon name="book" /></span>
            <div>
              <h3>知识库状态</h3>
              <p>百炼关系知识库 · 对话军师通用知识源</p>
            </div>
            <span class="state-badge" :class="{ offline: !status.bailianRagEnabled }">
              {{ status.bailianRagEnabled ? '正常' : '未启用' }}
            </span>
          </div>
          <div class="metric-row">
            <div>
              <span>精选文档</span>
              <strong>{{ status.knowledgeDocuments ?? '—' }}</strong>
            </div>
            <div>
              <span>RAG 服务</span>
              <strong>{{ status.bailianRagEnabled ? 'Connected' : 'Disabled' }}</strong>
            </div>
          </div>
          <div class="detail-list">
            <div><span>通用知识来源</span><b>Alibaba Bailian</b></div>
            <div><span>用途</span><b>关系知识增强</b></div>
          </div>
        </article>

        <article class="service-card v2-card">
          <div class="service-title">
            <span class="service-icon violet"><AppIcon name="brain" /></span>
            <div>
              <h3>长期记忆状态</h3>
              <p>用户主动开启的私有关系记忆</p>
            </div>
            <span class="state-badge" :class="{ offline: !status.pgvectorEnabled }">
              {{ status.pgvectorEnabled ? '运行中' : '未启用' }}
            </span>
          </div>
          <div class="metric-row">
            <div>
              <span>我的记忆</span>
              <strong>{{ status.relationshipMemoryCount ?? '—' }}</strong>
            </div>
            <div>
              <span>存储状态</span>
              <strong>{{ status.pgvectorEnabled ? 'Ready' : 'Disabled' }}</strong>
            </div>
          </div>
          <div class="detail-list">
            <div><span>数据库</span><b>PostgreSQL + PGVector</b></div>
            <div><span>提取模型</span><b>{{ status.memoryModel || 'qwen3:8b' }}</b></div>
          </div>
        </article>

        <article class="service-card v2-card">
          <div class="service-title">
            <span class="service-icon orange"><AppIcon name="search" /></span>
            <div>
              <h3>百度搜索状态</h3>
              <p>官方 Web Search API</p>
            </div>
            <span class="state-badge" :class="{ offline: !status.baiduSearchEnabled }">
              {{ status.baiduSearchEnabled ? '已连接' : '未启用' }}
            </span>
          </div>
          <div class="metric-row">
            <div>
              <span>搜索能力</span>
              <strong>Web</strong>
            </div>
            <div>
              <span>Agent 接入</span>
              <strong>{{ status.baiduSearchEnabled ? 'Ready' : 'Off' }}</strong>
            </div>
          </div>
          <div class="detail-list">
            <div><span>调用方式</span><b>工具调用</b></div>
            <div><span>用途</span><b>实时网页信息</b></div>
          </div>
        </article>
      </section>

      <section class="system-panel v2-card">
        <div class="panel-head">
          <div>
            <h2>系统服务状态</h2>
            <p>核心服务健康检查</p>
          </div>
          <span class="pill">
            <span class="status-dot" :class="{ off: !overallHealthy }"></span>
            {{ overallHealthy ? '所有核心服务正常' : '部分服务需检查' }}
          </span>
        </div>
        <div class="system-grid">
          <div v-for="service in systemServices" :key="service.name" class="system-item">
            <span class="service-icon" :class="service.tone">
              <AppIcon :name="service.icon" :size="20" />
            </span>
            <div>
              <strong>{{ service.name }}</strong>
              <span>{{ service.desc }}</span>
            </div>
            <span class="health-dot" :class="{ off: !service.ok }"></span>
            <b>{{ service.label || (service.ok ? '运行中' : '异常') }}</b>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AppTopNav from '../components/v2/AppTopNav.vue'
import AppIcon from '../components/v2/AppIcon.vue'
import { getSystemStatus } from '../api'

const status = ref({})

const models = computed(() => {
  const coach = status.value.coachPrimary || 'qwen-plus'
  const agent = status.value.agentPrimary || coach
  return [
    {
      key: 'deepseek',
      name: 'DeepSeek',
      icon: 'bot',
      tone: 'blue',
      available: !!status.value.deepSeekAvailable,
      selected: coach.includes('deepseek') || agent.includes('deepseek'),
      desc: '负责对话军师主推理与超级智能体复杂任务。'
    },
    {
      key: 'qwen',
      name: 'Qwen（通义千问）',
      icon: 'lightning',
      tone: 'purple',
      available: true,
      selected: !status.value.deepSeekAvailable,
      desc: '用于快速模型与 DeepSeek 不可用时的回退。'
    },
    {
      key: 'ollama',
      name: 'Ollama · qwen3:8b',
      icon: 'database',
      tone: 'slate',
      available: true,
      selected: false,
      desc: '本地运行，负责长期记忆的精简提取。'
    }
  ]
})

const systemServices = computed(() => [
  { name: '应用服务', desc: 'Spring Boot API', icon: 'bot', tone: 'blue', ok: true },
  { name: '向量数据库', desc: 'PostgreSQL + PGVector', icon: 'database', tone: 'violet', ok: !!status.value.databaseHealthy },
  { name: '知识库服务', desc: 'Alibaba Bailian RAG', icon: 'book', tone: 'purple', ok: !!status.value.bailianRagEnabled },
  { name: '搜索服务', desc: 'Baidu Web Search', icon: 'search', tone: 'orange', ok: !!status.value.baiduSearchEnabled },
  {
    name: 'QQ 机器人',
    desc: 'Android QQ · 官方机器人渠道',
    icon: 'user',
    tone: 'blue',
    ok: !status.value.qqBotEnabled || !!status.value.qqBotReady,
    label: status.value.qqBotReady
      ? '已连接'
      : (status.value.qqBotEnabled ? '连接异常' : '未启用')
  }
])

const overallHealthy = computed(() => systemServices.value.every(item => item.ok))

onMounted(async () => {
  try {
    status.value = (await getSystemStatus()).data
  } catch (error) {
    console.error('System status error:', error)
  }
})
</script>

<style scoped>
.status-page { min-height: 100vh; padding-bottom: 50px; }
main { padding-top: 22px; }

.status-hero {
  height: 160px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  overflow: hidden;
  margin-bottom: 18px;
  padding: 18px 28px;
  border-radius: 22px;
  border: 1px solid #f5ece2;
  background: linear-gradient(90deg, #fff 0%, #fefcf9 56%, #fdf6ef 100%);
  box-shadow: var(--shadow);
}
.status-hero h1 { margin: 2px 0 6px; color: #402e1e; font-size: 38px; letter-spacing: -1.4px; }
.status-hero p { margin: 0; color: #7a879d; }
.eyebrow { color: #d09f67 !important; font-size: 11px; font-weight: 800; letter-spacing: .18em; }
.status-hero img {
  width: 500px; height: 160px; object-fit: cover; object-position: center 46%;
  -webkit-mask-image: linear-gradient(90deg, transparent, black 18%, black 90%, transparent);
          mask-image: linear-gradient(90deg, transparent, black 18%, black 90%, transparent);
}

.model-panel, .system-panel { padding: 20px; }
.panel-head { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 18px; }
.panel-head h2 { margin: 0; color: #3f3125; font-size: 18px; }
.panel-head p { margin: 5px 0 0; color: #8a96ab; font-size: 12px; }

.model-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
.model-card {
  display: flex; gap: 14px; padding: 17px; min-height: 130px;
  border: 1px solid #f6ede3; border-radius: 15px; background: #fff;
}
.model-card.selected { border-color: #eabb86; background: linear-gradient(135deg, #fff, #fef9f4); box-shadow: inset 0 0 0 1px rgba(228,174,112,.12); }
.model-icon, .service-icon {
  width: 44px; height: 44px; flex: none; display: grid; place-items: center; border-radius: 13px;
}
.model-icon.blue, .service-icon.blue { color: #e19f54; background: #fdf5ec; }
.model-icon.purple, .service-icon.purple { color: #dba364; background: #fdf6ed; }
.model-icon.slate { color: #865c2d; background: #f9f3ec; }
.service-icon.violet { color: #d39b5c; background: #fdf5ec; }
.service-icon.orange { color: #ef7b2a; background: #fff0e5; }

.model-copy { flex: 1; }
.model-title { display: flex; gap: 8px; align-items: center; }
.model-title strong { color: #3a2d21; }
.model-title span { padding: 3px 7px; color: #cc9659; background: #fdf5ec; border-radius: 999px; font-size: 10px; font-weight: 700; }
.model-copy p { margin: 8px 0 10px; color: #7d899e; font-size: 12px; line-height: 1.55; }
.model-status { display: flex; align-items: center; gap: 7px; color: #26905b; font-size: 12px; font-weight: 650; }
.status-dot.off { background: #c4cad4; box-shadow: none; }

.service-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin: 16px 0; }
.service-card { padding: 18px; }
.service-title { display: flex; align-items: center; gap: 12px; }
.service-title h3 { margin: 0 0 4px; color: #3f3125; font-size: 16px; }
.service-title p { margin: 0; color: #8c97aa; font-size: 11px; }
.state-badge { margin-left: auto; padding: 5px 9px; border-radius: 999px; color: #158d55; background: #e8f9f0; font-size: 11px; font-weight: 700; }
.state-badge.offline { color: #7d8798; background: #f8f2eb; }
.metric-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin: 17px 0; }
.metric-row > div { padding: 12px; border: 1px solid #f8f1ea; border-radius: 12px; background: #fefcfa; }
.metric-row span { display: block; color: #8a96aa; font-size: 11px; }
.metric-row strong { display: block; margin-top: 6px; color: #4a3a2c; font-size: 20px; }
.detail-list { display: grid; gap: 8px; }
.detail-list div { display: flex; justify-content: space-between; gap: 10px; color: #7f8ca1; font-size: 11px; }
.detail-list b { color: #6d5540; font-weight: 600; text-align: right; }

.system-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.system-item { display: grid; grid-template-columns: auto 1fr auto auto; align-items: center; gap: 10px; padding: 12px; border: 1px solid #f7efe6; border-radius: 13px; background: #fff; }
.system-item strong { display: block; color: #4c3b2c; font-size: 12px; }
.system-item span { display: block; color: #8a95a7; font-size: 10px; }
.system-item b { color: #318963; font-size: 11px; }
.health-dot { width: 7px; height: 7px; border-radius: 50%; background: #19b36c; }
.health-dot.off { background: #c3cad5; }

@media (max-width: 1024px) {
  .model-grid, .service-grid { grid-template-columns: 1fr; }
  .system-grid { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 640px) {
  .status-hero img { display: none; }
  .status-hero h1 { font-size: 30px; }
  .system-grid { grid-template-columns: 1fr; }
}
</style>
