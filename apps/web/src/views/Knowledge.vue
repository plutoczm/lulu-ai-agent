<template>
  <div class="knowledge-page">
    <AppTopNav />

    <main class="page-shell knowledge-shell">
      <section class="knowledge-hero">
        <div>
          <p class="eyebrow">RELATIONSHIP KNOWLEDGE</p>
          <h1>关系知识库</h1>
          <p>浏览当前对话军师使用的关系、沟通、边界与实战资料。</p>
        </div>
        <span class="count-pill">{{ total }} 条资料</span>
      </section>

      <section class="knowledge-search">
        <AppIcon name="search" :size="18" />
        <input
          v-model.trim="query"
          type="search"
          placeholder="搜索：沟通、依恋、边界、分手、接话……"
          @input="scheduleSearch"
          @keydown.enter.prevent="runSearch"
        />
        <button v-if="query" type="button" @click="clearSearch">清空</button>
      </section>

      <div v-if="loading" class="state-card">正在检索知识库…</div>
      <div v-else-if="errorMessage" class="state-card error">{{ errorMessage }}</div>
      <div v-else-if="!results.length" class="state-card">
        没有找到匹配内容，换一个更短的关键词试试。
      </div>

      <section v-else class="knowledge-grid">
        <article v-for="item in results" :key="item.document" class="knowledge-card">
          <div class="card-head">
            <span class="kind">{{ item.document?.startsWith('practical-') ? '实战' : '知识' }}</span>
            <AppIcon name="book" :size="18" />
          </div>
          <h2>{{ item.title }}</h2>
          <p>{{ item.subtitle }}</p>
          <small>{{ item.document }}</small>
        </article>
      </section>
    </main>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchGlobal } from '../api'
import AppTopNav from '../components/v2/AppTopNav.vue'
import AppIcon from '../components/v2/AppIcon.vue'

const route = useRoute()
const router = useRouter()
const query = ref(String(route.query.q || ''))
const results = ref([])
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')
let timer = null

const runSearch = async () => {
  if (timer) {
    window.clearTimeout(timer)
    timer = null
  }

  loading.value = true
  errorMessage.value = ''
  try {
    const response = await searchGlobal(query.value, 30, 'knowledge')
    results.value = response.data?.results || []
    total.value = response.data?.total || 0

    const current = String(route.query.q || '')
    if (current !== query.value) {
      await router.replace({
        path: '/knowledge',
        query: query.value ? { q: query.value } : {}
      })
    }
  } catch (error) {
    console.error('Knowledge search failed:', error)
    errorMessage.value = '知识库检索失败，请确认后端服务正常。'
  } finally {
    loading.value = false
  }
}

const scheduleSearch = () => {
  if (timer) window.clearTimeout(timer)
  timer = window.setTimeout(runSearch, 220)
}

const clearSearch = () => {
  query.value = ''
  runSearch()
}

watch(
  () => route.query.q,
  (value) => {
    const next = String(value || '')
    if (next !== query.value) {
      query.value = next
      runSearch()
    }
  }
)

onMounted(runSearch)
onBeforeUnmount(() => {
  if (timer) window.clearTimeout(timer)
})
</script>

<style scoped>
.knowledge-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #fefcf9 0%, #fbf8f4 100%);
}
.knowledge-shell {
  padding-top: 34px;
  padding-bottom: 64px;
}
.knowledge-hero {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 30px;
  border: 1px solid #f3e8dc;
  border-radius: 22px;
  background: linear-gradient(135deg, #fff, #fdf6ee);
}
.eyebrow {
  margin: 0 0 8px;
  color: #d38f43;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: .14em;
}
.knowledge-hero h1 {
  margin: 0;
  color: #3e2d20;
  font-size: 34px;
}
.knowledge-hero p {
  margin: 8px 0 0;
  color: #818b9d;
  font-size: 14px;
}
.count-pill {
  flex: none;
  padding: 7px 11px;
  border-radius: 999px;
  color: #ad7335;
  background: #fff6e9;
  font-size: 12px;
  font-weight: 700;
}
.knowledge-search {
  margin-top: 18px;
  min-height: 54px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border: 1px solid #f0e4d6;
  border-radius: 15px;
  background: #fff;
  color: #9a7a59;
  box-shadow: 0 12px 30px rgba(98, 70, 43, .05);
}
.knowledge-search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  color: #4c3d31;
  background: transparent;
  font: inherit;
}
.knowledge-search input::placeholder { color: #a4adbc; }
.knowledge-search button {
  border: 0;
  background: transparent;
  color: #c27f39;
  cursor: pointer;
}
.knowledge-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}
.knowledge-card {
  min-height: 190px;
  padding: 18px;
  border: 1px solid #f2e7da;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 10px 28px rgba(88, 62, 38, .04);
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #cf9351;
}
.kind {
  padding: 4px 8px;
  border-radius: 999px;
  background: #fdf4e9;
  font-size: 10px;
  font-weight: 800;
}
.knowledge-card h2 {
  margin: 14px 0 8px;
  color: #4c3828;
  font-size: 16px;
  line-height: 1.45;
}
.knowledge-card p {
  margin: 0;
  color: #7f899a;
  font-size: 12px;
  line-height: 1.7;
}
.knowledge-card small {
  display: block;
  margin-top: 14px;
  color: #b0b7c3;
  font-size: 9px;
  overflow-wrap: anywhere;
}
.state-card {
  margin-top: 18px;
  padding: 28px;
  border: 1px solid #f2e7da;
  border-radius: 16px;
  background: #fff;
  color: #7f8999;
  text-align: center;
}
.state-card.error { color: #b74e4e; }
@media (max-width: 980px) {
  .knowledge-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .knowledge-shell { padding-top: 16px; }
  .knowledge-hero { align-items: flex-start; flex-direction: column; padding: 22px; }
  .knowledge-grid { grid-template-columns: 1fr; }
}
</style>
