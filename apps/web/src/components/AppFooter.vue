<template>
  <footer class="footer">
    <div class="page-shell footer-inner">
      <div class="brand-block">
        <BrandLogo />
        <p>让 AI 成为你的长期智能助手。</p>
      </div>
      <div class="footer-links">
        <div>
          <strong>产品</strong>
          <router-link to="/chat-coach">AI对话军师</router-link>
          <router-link to="/super-agent">AI超级智能体</router-link>
        </div>
        <div>
          <strong>系统</strong>
          <router-link to="/status">模型与状态</router-link>
          <span>长期记忆</span>
        </div>
      </div>
      <div class="copyright">© {{ currentYear }} 噜噜 · LULU AI</div>
    </div>
  </footer>
</template>

<script setup>
import { computed } from 'vue'
import BrandLogo from './v2/BrandLogo.vue'

const currentYear = computed(() => new Date().getFullYear())
</script>

<style scoped>
.footer {
  margin-top: 26px;
  padding: 28px 0;
  border-top: 1px solid #f7efe6;
  background: rgba(255,255,255,.72);
}
.footer-inner {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 24px;
  align-items: start;
}
.brand-block p { margin: 10px 0 0; color: #8a96aa; font-size: 12px; }
.footer-links { display: flex; gap: 50px; }
.footer-links > div { display: grid; gap: 7px; min-width: 100px; }
.footer-links strong { margin-bottom: 2px; color: #68513d; font-size: 12px; }
.footer-links a, .footer-links span { color: #8a95a8; font-size: 11px; }
.footer-links a:hover { color: #cf995c; }
.copyright {
  grid-column: 1 / -1;
  padding-top: 18px;
  border-top: 1px solid #f8f1e9;
  color: #adb5c3;
  font-size: 10px;
}
@media (max-width: 640px) {
  .footer-inner { grid-template-columns: 1fr; }
  .footer-links { gap: 28px; }
}
</style>
