<template>
  <header class="top-nav">
    <div class="nav-inner page-shell">
      <BrandLogo />

      <nav class="links">
        <router-link to="/" exact-active-class="active"><AppIcon name="home" />首页</router-link>
        <router-link to="/chat-coach" active-class="active"><AppIcon name="heart" />对话军师</router-link>
        <router-link to="/super-agent" active-class="active"><AppIcon name="bot" />智能体</router-link>
        <router-link to="/knowledge" active-class="active"><AppIcon name="book" />知识库</router-link>
        <router-link to="/status" active-class="active"><AppIcon name="activity" />状态</router-link>
      </nav>

      <div class="nav-actions">
        <div ref="searchRoot" class="search-wrap">
          <div class="search-box" :class="{ focused: showSearch }">
            <AppIcon name="search" :size="17" />
            <input
              ref="searchInput"
              v-model="query"
              type="search"
              autocomplete="off"
              placeholder="搜索应用、知识库或功能"
              aria-label="全局搜索"
              @focus="openSearch"
              @input="scheduleSearch"
              @keydown.down.prevent="moveSelection(1)"
              @keydown.up.prevent="moveSelection(-1)"
              @keydown.enter.prevent="activateSelected"
              @keydown.esc.prevent="closeSearch"
            />
            <kbd>{{ shortcutLabel }}</kbd>
          </div>

          <div v-if="showSearch" class="search-panel">
            <div class="search-panel-head">
              <span>{{ query ? '搜索结果' : '快捷入口' }}</span>
              <small v-if="loading">检索中…</small>
              <small v-else>{{ results.length }} 项</small>
            </div>

            <button
              v-for="(item, index) in results"
              :key="`${item.type}-${item.title}-${index}`"
              type="button"
              class="search-result"
              :class="{ selected: selectedIndex === index }"
              @mouseenter="selectedIndex = index"
              @mousedown.prevent="chooseResult(item)"
            >
              <span class="result-icon" :class="item.type">
                <AppIcon :name="resultIcon(item.type)" :size="17" />
              </span>
              <span class="result-copy">
                <strong>{{ item.title }}</strong>
                <small>{{ item.subtitle }}</small>
              </span>
              <span class="result-type">{{ typeLabel(item.type) }}</span>
            </button>

            <div v-if="!loading && !results.length" class="empty-result">
              没找到结果。试试“对话军师”“模型”“沟通”“边界”等关键词。
            </div>

            <div class="search-help">
              <span>↑↓ 选择</span>
              <span>Enter 打开</span>
              <span>Esc 关闭</span>
            </div>
          </div>
        </div>

        <div ref="userRoot" class="user-wrap">
          <button
            class="user-chip"
            type="button"
            :aria-expanded="showUserMenu"
            @click="showUserMenu = !showUserMenu"
          >
            <img class="avatar-dot" src="../../assets/brand/lulu-capybara-mark.jpg" alt="" />
            <span>{{ user?.displayName || '我的账号' }}</span>
            <small>⌄</small>
          </button>

          <div v-if="showUserMenu" class="user-menu">
            <div class="account-summary">
              <img src="../../assets/brand/lulu-capybara-mark.jpg" alt="" />
              <div>
                <strong>{{ user?.displayName || '我的账号' }}</strong>
                <small>{{ user?.email || '未加载邮箱' }}</small>
              </div>
              <span v-if="user?.admin">管理员</span>
            </div>

            <router-link to="/status" @click="showUserMenu = false">
              <AppIcon name="activity" :size="16" />
              系统状态
            </router-link>
            <router-link to="/knowledge" @click="showUserMenu = false">
              <AppIcon name="book" :size="16" />
              关系知识库
            </router-link>
            <router-link v-if="user?.admin" to="/models" @click="showUserMenu = false">
              <AppIcon name="settings" :size="16" />
              模型配置
            </router-link>
            <button class="logout-action" type="button" @click="logout">
              <AppIcon name="user" :size="16" />
              退出登录
            </button>
          </div>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getCurrentUser, logoutAccount, searchGlobal } from '../../api'
import BrandLogo from './BrandLogo.vue'
import AppIcon from './AppIcon.vue'

const router = useRouter()
const user = ref(null)

const query = ref('')
const results = ref([])
const loading = ref(false)
const showSearch = ref(false)
const selectedIndex = ref(0)
const searchInput = ref(null)
const searchRoot = ref(null)
const userRoot = ref(null)
const showUserMenu = ref(false)
let searchTimer = null

const shortcutLabel = computed(() => {
  if (typeof navigator === 'undefined') return 'Ctrl K'
  return /Mac|iPhone|iPad/.test(navigator.platform) ? '⌘ K' : 'Ctrl K'
})

const resultIcon = (type) => {
  if (type === 'knowledge') return 'book'
  if (type === 'app') return 'grid'
  return 'tools'
}

const typeLabel = (type) => {
  if (type === 'knowledge') return '知识'
  if (type === 'app') return '应用'
  return '功能'
}

const loadUser = async () => {
  try {
    user.value = (await getCurrentUser()).data?.user || null
  } catch {
    user.value = null
  }
}

const runSearch = async () => {
  loading.value = true
  try {
    const response = await searchGlobal(query.value.trim(), 10)
    results.value = response.data?.results || []
    selectedIndex.value = results.value.length ? 0 : -1
  } catch (error) {
    console.error('Global search failed:', error)
    results.value = []
    selectedIndex.value = -1
  } finally {
    loading.value = false
  }
}

const scheduleSearch = () => {
  showSearch.value = true
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(runSearch, 160)
}

const openSearch = () => {
  showSearch.value = true
  runSearch()
}

const closeSearch = () => {
  showSearch.value = false
  selectedIndex.value = 0
  searchInput.value?.blur()
}

const moveSelection = (delta) => {
  if (!showSearch.value || !results.value.length) return
  const length = results.value.length
  selectedIndex.value = (selectedIndex.value + delta + length) % length
}

const activateSelected = () => {
  if (!showSearch.value) {
    openSearch()
    return
  }
  const item = results.value[selectedIndex.value]
  if (item) chooseResult(item)
}

const chooseResult = async (item) => {
  query.value = ''
  showSearch.value = false
  showUserMenu.value = false
  await router.push(item.path)
}

const logout = async () => {
  showUserMenu.value = false
  try {
    await logoutAccount()
  } finally {
    await router.replace('/login')
  }
}

const handleGlobalKeydown = async (event) => {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    showUserMenu.value = false
    showSearch.value = true
    await nextTick()
    searchInput.value?.focus()
    searchInput.value?.select()
    runSearch()
    return
  }
  if (event.key === 'Escape') {
    showSearch.value = false
    showUserMenu.value = false
  }
}

const handleDocumentClick = (event) => {
  if (showSearch.value && searchRoot.value && !searchRoot.value.contains(event.target)) {
    showSearch.value = false
  }
  if (showUserMenu.value && userRoot.value && !userRoot.value.contains(event.target)) {
    showUserMenu.value = false
  }
}

onMounted(() => {
  loadUser()
  document.addEventListener('keydown', handleGlobalKeydown)
  document.addEventListener('mousedown', handleDocumentClick)
})

onBeforeUnmount(() => {
  if (searchTimer) window.clearTimeout(searchTimer)
  document.removeEventListener('keydown', handleGlobalKeydown)
  document.removeEventListener('mousedown', handleDocumentClick)
})
</script>

<style scoped>
.top-nav {
  position: sticky;
  top: 0;
  z-index: 50;
  height: 66px;
  border-bottom: 1px solid rgba(243,233,221,.95);
  background: rgba(255,255,255,.92);
  backdrop-filter: blur(18px);
}
.nav-inner {
  height: 100%;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 22px;
}
.links { display: flex; align-items: center; gap: 4px; }
.links a {
  min-height: 38px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 11px;
  border-radius: 10px;
  color: #956a3b;
  font-size: 13px;
  font-weight: 650;
}
.links a:hover, .links a.active { color: #e9711c; background: #fff1de; }
.nav-actions { display: flex; align-items: center; gap: 10px; }

.search-wrap, .user-wrap { position: relative; }
.search-box {
  width: 320px;
  min-height: 40px;
  padding: 0 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #8a95aa;
  border: 1px solid #f0e3d5;
  border-radius: 12px;
  background: #fefcfa;
  transition: border-color .16s, box-shadow .16s, background .16s;
}
.search-box.focused {
  border-color: #efb879;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(239,184,121,.13);
}
.search-box input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: #4b3a2d;
  font: inherit;
  font-size: 13px;
}
.search-box input::placeholder { color: #9aa5b8; }
.search-box input::-webkit-search-cancel-button { display: none; }
.search-box kbd {
  flex: none;
  border: 1px solid #f1e3d4;
  border-bottom-width: 2px;
  border-radius: 6px;
  padding: 2px 5px;
  background: #fff;
  color: #9aa4b7;
  font-size: 10px;
}
.search-panel {
  position: absolute;
  top: calc(100% + 9px);
  right: 0;
  width: 430px;
  overflow: hidden;
  border: 1px solid #eee1d2;
  border-radius: 15px;
  background: #fff;
  box-shadow: 0 22px 60px rgba(77,55,34,.16);
}
.search-panel-head, .search-help {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 13px;
  color: #9a7c5d;
  background: #fdf9f5;
  font-size: 10px;
  font-weight: 700;
}
.search-panel-head small { color: #adb4c0; font-weight: 500; }
.search-result {
  width: 100%;
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  border: 0;
  border-top: 1px solid #faf3eb;
  background: #fff;
  text-align: left;
  cursor: pointer;
}
.search-result:hover, .search-result.selected { background: #fff8f0; }
.result-icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  color: #cf8d44;
  background: #fdf3e8;
}
.result-icon.knowledge { color: #4f8b75; background: #eef8f4; }
.result-icon.app { color: #dc7d34; background: #fff1e5; }
.result-copy { min-width: 0; }
.result-copy strong, .result-copy small { display: block; }
.result-copy strong { color: #4a392c; font-size: 12px; }
.result-copy small {
  margin-top: 3px;
  overflow: hidden;
  color: #929bab;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.result-type {
  padding: 3px 6px;
  border-radius: 999px;
  color: #a27a51;
  background: #faf2e9;
  font-size: 9px;
}
.empty-result { padding: 24px 18px; color: #969fad; font-size: 11px; text-align: center; }
.search-help { justify-content: flex-start; gap: 15px; border-top: 1px solid #f7eee5; }

.user-chip {
  min-height: 40px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 9px 3px 4px;
  border: 1px solid #f1e3d4;
  border-radius: 999px;
  color: #48392b;
  background: #fffaf4;
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
}
.user-chip:hover { border-color: #efc18e; background: #fff5e8; }
.user-chip small { color: #b27a43; font-size: 11px; }
.avatar-dot { width: 32px; height: 32px; border-radius: 50%; object-fit: cover; object-position: center 42%; }

.user-menu {
  position: absolute;
  top: calc(100% + 9px);
  right: 0;
  width: 255px;
  overflow: hidden;
  padding: 7px;
  border: 1px solid #eee1d2;
  border-radius: 15px;
  background: #fff;
  box-shadow: 0 20px 55px rgba(77,55,34,.16);
}
.account-summary {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  gap: 9px;
  align-items: center;
  padding: 9px;
  margin-bottom: 5px;
  border-bottom: 1px solid #f7eee5;
}
.account-summary img { width: 38px; height: 38px; border-radius: 50%; object-fit: cover; }
.account-summary strong, .account-summary small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.account-summary strong { color: #443428; font-size: 12px; }
.account-summary small { margin-top: 2px; color: #98a1af; font-size: 9px; }
.account-summary > span {
  padding: 3px 6px;
  border-radius: 999px;
  color: #ab7134;
  background: #fff3e4;
  font-size: 8px;
  font-weight: 750;
}
.user-menu a, .logout-action {
  width: 100%;
  min-height: 36px;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 10px;
  border: 0;
  border-radius: 9px;
  color: #667188;
  background: transparent;
  font: inherit;
  font-size: 11px;
  text-align: left;
  cursor: pointer;
}
.user-menu a:hover, .logout-action:hover { color: #c4792f; background: #fff7ed; }
.logout-action { margin-top: 3px; color: #b75656; }

@media (max-width: 1180px) {
  .links a { padding: 0 8px; font-size: 12px; }
  .search-box { width: 260px; }
}
@media (max-width: 980px) {
  .links a:nth-child(4), .links a:nth-child(5) { display: none; }
  .search-box { width: 230px; }
}
@media (max-width: 760px) {
  .top-nav { height: 60px; }
  .nav-inner { gap: 10px; grid-template-columns: auto 1fr auto; }
  .links { display: none; }
  .search-box { width: min(52vw, 250px); }
  .search-box kbd { display: none; }
  .search-panel { position: fixed; top: 66px; right: 10px; left: 10px; width: auto; }
  .user-chip span { display: none; }
}
</style>
