<template>
  <div class="home-v2">
    <AppTopNav />

    <main>
      <section class="hero page-shell">
        <div class="hero-copy">
          <p class="eyebrow">LULU AI · PERSONAL COPILOT</p>
          <h1>噜噜</h1>
          <h2>你的 AI 对话军师与超级智能体</h2>
          <p class="hero-desc">
            融合长期记忆、知识库增强与多工具协作，
            为工作、学习与生活提供持续进化的 AI 能力。
          </p>
          <div class="hero-chips">
            <span><AppIcon name="brain" />长期记忆</span>
            <span><AppIcon name="book" />知识增强</span>
            <span><AppIcon name="tools" />多工具协作</span>
          </div>
          <div class="hero-actions">
            <button class="primary-btn" @click="navigateTo('/super-agent')">
              开始使用 <span>→</span>
            </button>
            <button class="secondary-btn" @click="navigateTo('/status')">
              查看系统状态
            </button>
          </div>
        </div>

        <div class="hero-visual">
          <div class="hero-glow"></div>
          <img src="../assets/illustrations/lulu-home-hero.jpg" alt="噜噜智能助手" />

        </div>
      </section>

      <section class="apps page-shell">
        <article class="app-card love-card">
          <div class="app-card-copy">
            <div class="soft-tag love"><AppIcon name="heart" /> 真实聊天 · 即时回复建议</div>
            <h3>AI对话军师</h3>
            <p>面向微信、QQ真实聊天场景，下一句直接帮你想好</p>
            <ul>
              <li>A｜激进：更主动、更明确地推进</li>
              <li>B｜正常：自然、均衡的默认建议</li>
              <li>C｜保守：更克制、给对方更多空间</li>
            </ul>
            <button class="love-button" @click="navigateTo('/chat-coach')">
              进入应用 <span>→</span>
            </button>
          </div>
          <div class="app-card-image love-image">
            <img src="../assets/illustrations/lulu-chat-coach-hero.jpg" alt="AI对话军师" />
          </div>
        </article>

        <article class="app-card agent-card">
          <div class="app-card-copy">
            <div class="soft-tag agent"><AppIcon name="lightning" /> 高效生产力 · 完成复杂任务</div>
            <h3>AI超级智能体</h3>
            <p>搜索、工具、任务协作，一站式完成复杂工作</p>
            <ul>
              <li>深度搜索与多源信息整合</li>
              <li>多工具调用与任务自动化</li>
              <li>支持模型路由与不同任务场景</li>
            </ul>
            <button class="agent-button" @click="navigateTo('/super-agent')">
              进入应用 <span>→</span>
            </button>
          </div>
          <div class="app-card-image agent-image">
            <img src="../assets/illustrations/lulu-super-agent-hero.gif" alt="AI超级智能体" />
          </div>
        </article>
      </section>

      <section class="capabilities page-shell">
        <div class="section-heading">
          <div>
            <h3>平台核心能力</h3>
            <p>真实接入当前项目能力，不展示虚构指标。</p>
          </div>
          <button class="secondary-btn compact" @click="navigateTo('/status')">了解更多 →</button>
        </div>
        <div class="capability-grid">
          <div v-for="item in capabilities" :key="item.title" class="capability-card">
            <span class="capability-icon" :class="item.tone">
              <AppIcon :name="item.icon" :size="24" />
            </span>
            <div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.desc }}</p>
            </div>
          </div>
        </div>
      </section>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import AppTopNav from '../components/v2/AppTopNav.vue'
import AppIcon from '../components/v2/AppIcon.vue'
import AppFooter from '../components/AppFooter.vue'

const router = useRouter()
const navigateTo = (path) => router.push(path)

const capabilities = [
  { icon: 'brain', tone: 'purple', title: '长期记忆', desc: '基于 PGVector 保存用户主动开启的精简关系记忆。' },
  { icon: 'book', tone: 'blue', title: '知识库增强', desc: '百炼关系知识库为对话建议提供通用依据。' },
  { icon: 'bot', tone: 'orange', title: '模型路由', desc: 'DeepSeek、Qwen 与本地 Ollama 各司其职。' },
  { icon: 'tools', tone: 'green', title: '工具执行', desc: '支持搜索、网页、文件等多种工具能力。' }
]
</script>

<style scoped>
.home-v2 { min-height: 100vh; background: linear-gradient(180deg, #fefcf9 0%, #fff 56%, #fdfaf7 100%); }
main { overflow: hidden; }

.hero {
  min-height: 520px;
  display: grid;
  grid-template-columns: 1.03fr .97fr;
  align-items: center;
  gap: 42px;
  padding-top: 52px;
  padding-bottom: 36px;
}
.eyebrow { margin: 0 0 16px; color: #D97706; font-size: 14px; font-weight: 700; letter-spacing: .08em; }
.hero h1 { margin: 0; max-width: 760px; color: #422f1f; font-size: clamp(40px, 4.2vw, 58px); line-height: 1.08; letter-spacing: -2.2px; }
.hero h2 { margin: 16px 0 0; color: #433224; font-size: clamp(25px, 2.8vw, 38px); font-weight: 650; letter-spacing: -.8px; }
.hero-desc { max-width: 640px; margin: 22px 0 0; color: #65708a; font-size: 17px; line-height: 1.85; }
.hero-chips { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 26px; }
.hero-chips span {
  display: inline-flex; align-items: center; gap: 8px;
  min-height: 38px; padding: 0 14px; border-radius: 999px;
  background: #fff; border: 1px solid #f5ece1; color: #926531; font-size: 14px; font-weight: 600;
}
.hero-chips span:nth-child(1) { color: #c89357; background: #fdf7f1; }
.hero-chips span:nth-child(2) { color: #bb7d37; background: #fdf6ef; }
.hero-chips span:nth-child(3) { color: #d26a22; background: #fff3e9; }
.hero-actions { display: flex; gap: 12px; margin-top: 28px; }

.hero-visual { position: relative; min-height: 430px; display: grid; place-items: center; }
.hero-visual img {
  position: relative; z-index: 2; width: min(100%, 680px); max-height: 410px; object-fit: cover;
  object-position: center; border-radius: 30px; box-shadow: 0 28px 80px rgba(182,125,61,.14);
  -webkit-mask-image: linear-gradient(to right, transparent 0%, black 8%, black 92%, transparent 100%);
          mask-image: linear-gradient(to right, transparent 0%, black 8%, black 92%, transparent 100%);
}
.hero-glow { position: absolute; inset: 12% 12% 0; background: radial-gradient(circle, rgba(232,177,114,.24), transparent 65%); filter: blur(28px); }
.floating-chip {
  position: absolute; z-index: 3; display: inline-flex; align-items: center; gap: 8px;
  padding: 10px 14px; border-radius: 999px; color: #9b6b34; background: rgba(255,255,255,.9);
  border: 1px solid rgba(242,230,217,.9); box-shadow: 0 12px 32px rgba(145,100,48,.1);
  font-size: 13px; font-weight: 650; backdrop-filter: blur(12px);
}
.floating-chip.memory { top: 44px; right: 6px; }
.floating-chip.knowledge { top: 102px; right: -10px; }
.floating-chip.tools { top: 160px; right: 18px; }

.apps { display: grid; grid-template-columns: 1fr 1fr; gap: 22px; padding-top: 12px; }
.app-card {
  position: relative; min-height: 355px; overflow: hidden; display: grid;
  grid-template-columns: 1.03fr .97fr; border-radius: 26px; border: 1px solid #f6ede3;
  box-shadow: var(--shadow); background: #fff;
}
.love-card { background: linear-gradient(135deg, #fff 0%, #fff9f8 48%, #fff1f0 100%); }
.agent-card { background: linear-gradient(135deg, #fff 0%, #fefcf9 48%, #fdf7f0 100%); }
.app-card-copy { position: relative; z-index: 2; padding: 32px 0 30px 34px; }
.app-card h3 { margin: 20px 0 5px; color: #3b2b1d; font-size: 34px; letter-spacing: -1px; }
.app-card p { margin: 0; color: #5f6d87; font-size: 15px; line-height: 1.65; }
.app-card ul { list-style: none; margin: 22px 0 28px; padding: 0; display: grid; gap: 10px; color: #66758f; font-size: 14px; }
.app-card li::before { content: '✓'; margin-right: 9px; color: #dfa86a; font-weight: 800; }
.soft-tag { display: inline-flex; align-items: center; gap: 7px; padding: 7px 11px; border-radius: 999px; font-size: 12px; font-weight: 700; }
.soft-tag.love { color: #ee6471; background: #fff0f2; }
.soft-tag.agent { color: #ce995c; background: #fdf6ef; }
.love-button, .agent-button {
  min-height: 44px; padding: 0 20px; border: 0; border-radius: 12px; color: #fff;
  display: inline-flex; align-items: center; gap: 9px; font-weight: 700; box-shadow: 0 12px 24px rgba(0,0,0,.1);
}
.love-button { background: linear-gradient(135deg, #ff7a7f, #ff626f); }
.agent-button { background: linear-gradient(135deg, #F28C28, #FFB347); }
.app-card-image { position: relative; min-width: 0; overflow: hidden; }
.app-card-image img { width: 100%; height: 100%; object-fit: cover; }
.love-image img { object-position: center 47%; }
.agent-image img { object-position: center 54%; }
.app-card-image::after { content: ''; position: absolute; inset: 0; background: linear-gradient(90deg, currentColor 0%, transparent 34%); opacity: .08; }

.capabilities { margin-top: 26px; margin-bottom: 58px; padding: 26px; border: 1px solid #f6ede3; border-radius: 22px; background: rgba(255,255,255,.88); box-shadow: var(--shadow); }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 20px; }
.section-heading h3 { margin: 0; color: #403022; font-size: 22px; }
.section-heading p { margin: 6px 0 0; color: #8792a8; font-size: 13px; }
.compact { min-height: 36px; padding: 0 14px; font-size: 13px; }
.capability-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.capability-card { display: flex; gap: 14px; align-items: flex-start; padding: 17px; border: 1px solid #f7efe6; border-radius: 15px; background: #fff; }
.capability-card strong { display: block; margin: 1px 0 5px; color: #423427; font-size: 15px; }
.capability-card p { margin: 0; color: #7c889f; font-size: 12px; line-height: 1.6; }
.capability-icon { width: 42px; height: 42px; border-radius: 12px; display: grid; place-items: center; flex: none; }
.capability-icon.purple { color: #daa364; background: #fdf6ed; }
.capability-icon.blue { color: #dc984b; background: #fdf5ec; }
.capability-icon.orange { color: #f0782d; background: #fff1e7; }
.capability-icon.green { color: #12a868; background: #e9faf1; }

@media (max-width: 1080px) {
  .hero { grid-template-columns: 1fr; padding-top: 34px; }
  .hero-visual { min-height: 330px; }
  .floating-chip { display: none; }
  .apps { grid-template-columns: 1fr; }
}
@media (max-width: 760px) {
  .hero h1 { font-size: 42px; letter-spacing: -1.5px; }
  .hero h2 { font-size: 27px; }
  .hero-visual { min-height: 260px; }
  .app-card { grid-template-columns: 1fr; }
  .app-card-copy { padding: 26px; }
  .app-card-image { height: 210px; }
  .capability-grid { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 520px) {
  .hero-actions { flex-direction: column; align-items: stretch; }
  .capability-grid { grid-template-columns: 1fr; }
}
</style>
