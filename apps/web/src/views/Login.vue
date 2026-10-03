<template>
  <div class="auth-page">
    <section class="auth-shell">
      <div class="auth-visual">
        <img src="../assets/illustrations/lulu-home-hero.jpg" alt="噜噜" />
        <div class="visual-copy">
          <span>LULU AI</span>
          <h1>欢迎来到噜噜</h1>
          <p>登录后，你的聊天、长期记忆和关系数据都会按账号隔离。</p>
        </div>
      </div>

      <div class="auth-card">
        <div class="brand-row">
          <img src="../assets/brand/lulu-capybara-mark.jpg" alt="噜噜" />
          <div><strong>噜噜</strong><span>LULU AI</span></div>
        </div>

        <div class="mode-tabs">
          <button :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
          <button :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
        </div>

        <form @submit.prevent="submit">
          <label v-if="mode === 'register'">
            <span>昵称</span>
            <input v-model.trim="displayName" maxlength="80" placeholder="例如：小噜" />
          </label>
          <label>
            <span>邮箱</span>
            <input v-model.trim="email" type="email" autocomplete="email" required placeholder="you@example.com" />
          </label>
          <label>
            <span>密码</span>
            <input
              v-model="password"
              type="password"
              :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
              minlength="10"
              maxlength="128"
              required
              placeholder="至少 10 位"
            />
          </label>

          <div v-if="errorMessage" class="error-box">{{ errorMessage }}</div>

          <button class="submit-btn" :disabled="busy" type="submit">
            {{ busy ? '处理中…' : (mode === 'login' ? '登录并继续' : '创建账号') }}
          </button>
        </form>

        <p class="privacy-note">账号用于隔离你的会话和长期记忆数据。</p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { loginAccount, registerAccount } from '../api'

const route = useRoute()
const router = useRouter()
const mode = ref('login')
const displayName = ref('')
const email = ref('')
const password = ref('')
const busy = ref(false)
const errorMessage = ref('')

const submit = async () => {
  errorMessage.value = ''
  busy.value = true
  try {
    if (mode.value === 'login') {
      await loginAccount({ email: email.value, password: password.value })
    } else {
      await registerAccount({
        email: email.value,
        password: password.value,
        displayName: displayName.value
      })
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '操作失败，请检查输入后重试。'
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px;
  background: linear-gradient(145deg, #fff8ef, #fff1de 55%, #ffe5bf);
}
.auth-shell {
  width: min(1040px, 100%);
  min-height: 610px;
  display: grid;
  grid-template-columns: 1.08fr .92fr;
  overflow: hidden;
  border: 1px solid #f0dec8;
  border-radius: 30px;
  background: #fff;
  box-shadow: 0 30px 90px rgba(125, 76, 30, .16);
}
.auth-visual { position: relative; overflow: hidden; min-height: 610px; }
.auth-visual img { width: 100%; height: 100%; object-fit: cover; }
.auth-visual::after {
  content: ''; position: absolute; inset: 0;
  background: linear-gradient(180deg, transparent 40%, rgba(65, 39, 19, .72));
}
.visual-copy { position: absolute; z-index: 2; left: 36px; right: 36px; bottom: 34px; color: #fff; }
.visual-copy span { font-size: 12px; font-weight: 800; letter-spacing: .18em; }
.visual-copy h1 { margin: 8px 0; font-size: 38px; }
.visual-copy p { max-width: 470px; margin: 0; line-height: 1.75; opacity: .9; }

.auth-card { padding: 54px 48px; align-self: center; }
.brand-row { display: flex; align-items: center; gap: 12px; margin-bottom: 34px; }
.brand-row img { width: 52px; height: 52px; border-radius: 18px; object-fit: cover; }
.brand-row div { display: grid; }
.brand-row strong { color: #35271d; font-size: 24px; }
.brand-row span { color: #c78338; margin-top: 4px; font-size: 11px; letter-spacing: .12em; }
.mode-tabs { display: grid; grid-template-columns: 1fr 1fr; padding: 4px; margin-bottom: 28px; border-radius: 14px; background: #fff5e8; }
.mode-tabs button { min-height: 42px; border: 0; border-radius: 11px; background: transparent; color: #90643b; font-weight: 700; }
.mode-tabs button.active { background: #fff; color: #e9711c; box-shadow: 0 6px 18px rgba(126, 77, 29, .10); }
form { display: grid; gap: 18px; }
label { display: grid; gap: 8px; color: #5a4637; font-size: 13px; font-weight: 700; }
input { min-height: 48px; padding: 0 14px; border: 1px solid #ead8c5; border-radius: 12px; outline: none; color: #35271d; background: #fffdfa; }
input:focus { border-color: #f28c28; box-shadow: 0 0 0 3px rgba(242,140,40,.10); }
.error-box { padding: 11px 13px; border-radius: 11px; color: #b63e3e; background: #fff0f0; font-size: 12px; }
.submit-btn { min-height: 50px; border: 0; border-radius: 13px; color: #fff; background: linear-gradient(135deg, #f28c28, #ffb347); font-weight: 800; box-shadow: 0 12px 28px rgba(242,140,40,.25); }
.submit-btn:disabled { opacity: .65; }
.privacy-note { margin: 18px 0 0; color: #9b8979; font-size: 11px; text-align: center; }
@media (max-width: 760px) {
  .auth-page { padding: 18px; }
  .auth-shell { grid-template-columns: 1fr; min-height: 0; }
  .auth-visual { display: none; }
  .auth-card { padding: 38px 24px; }
}
</style>
