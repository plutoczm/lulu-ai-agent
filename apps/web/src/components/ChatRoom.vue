<template>
  <div class="chat-room" :class="aiType">
    <div ref="messagesContainer" class="chat-messages">
      <div
        v-for="(msg, index) in messages"
        :key="index"
        class="message-row"
        :class="{ user: msg.isUser, status: msg.type === 'agent-status' }"
      >
        <template v-if="msg.type === 'agent-status'">
          <div class="status-line">
            <span class="status-pulse"></span>
            <span>{{ displayContent(msg.content) }}</span>
          </div>
        </template>

        <template v-else>
          <div v-if="!msg.isUser" class="avatar">
            <AiAvatarFallback :type="aiType" />
          </div>

          <div class="bubble-wrap">
            <div class="message-bubble">
              <div class="message-content">{{ displayContent(msg.content) }}</div>
              <span
                v-if="connectionStatus === 'connecting' && index === messages.length - 1 && !msg.isUser"
                class="typing-indicator"
              >▋</span>
            </div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>

          <div v-if="msg.isUser" class="avatar user-avatar">
            <span>我</span>
          </div>
        </template>
      </div>
    </div>

    <div class="composer-shell">
      <div class="composer">
        <textarea
          v-model="inputMessage"
          class="input-box"
          :placeholder="placeholder"
          :disabled="connectionStatus === 'connecting'"
          rows="1"
          @keydown.enter.exact.prevent="sendMessage"
          @keydown.shift.enter.stop
        ></textarea>

        <div class="composer-bottom">
          <div class="composer-tools">
            <button type="button" class="circle-tool" title="附件">＋</button>
            <span class="input-tip">{{ aiType === 'love' ? 'Enter 发送 · Shift+Enter 换行' : '可直接描述任务或需要调用的工具' }}</span>
          </div>
          <button
            type="button"
            class="send-button"
            :disabled="connectionStatus === 'connecting' || !inputMessage.trim()"
            @click="sendMessage"
          >
            <span>{{ connectionStatus === 'connecting' ? '处理中' : '发送' }}</span>
            <span>↗</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref, watch } from 'vue'
import AiAvatarFallback from './AiAvatarFallback.vue'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  connectionStatus: { type: String, default: 'disconnected' },
  aiType: { type: String, default: 'default' },
  placeholder: { type: String, default: '请输入消息...' }
})

const emit = defineEmits(['send-message'])
const inputMessage = ref('')
const messagesContainer = ref(null)

const sendMessage = () => {
  const content = inputMessage.value.trim()
  if (!content || props.connectionStatus === 'connecting') return
  emit('send-message', content)
  inputMessage.value = ''
}

const displayContent = (content = '') =>
  content
    .replace(/\*\*/g, '')
    .replace(/^\s{0,3}#{1,6}\s+/gm, '')
    .replace(/^>\s?/gm, '')
    .replace(/^---+$/gm, '')
    .trim()

const formatTime = (timestamp) =>
  new Date(timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

watch(() => props.messages.length, scrollToBottom)
watch(() => props.messages.map(m => m.content).join(''), scrollToBottom)
onMounted(scrollToBottom)
</script>

<style scoped>
.chat-room {
  position: relative;
  height: 100%;
  min-height: 560px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 18px;
  background: rgba(255,255,255,.82);
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 24px 150px;
  scroll-behavior: smooth;
}
.message-row {
  width: 100%;
  max-width: 850px;
  margin: 0 auto 22px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.message-row.user { justify-content: flex-end; }
.avatar {
  width: 36px; height: 36px; flex: none;
  margin-top: 2px;
}
.user-avatar {
  display: grid; place-items: center; border-radius: 50%;
  color: #fff; background: linear-gradient(135deg, #524030, #a27036);
  font-size: 12px; font-weight: 700;
}
.bubble-wrap { max-width: min(78%, 720px); min-width: 0; }
.message-bubble {
  position: relative;
  padding: 13px 16px;
  border-radius: 16px;
  color: #5f4a38;
  background: #fbf8f4;
  border: 1px solid #f8f1ea;
  box-shadow: 0 6px 18px rgba(96,75,57,.035);
}
.user .message-bubble {
  color: #453628;
  background: #fdf7f0;
  border-color: #f9ede0;
}
.love .user .message-bubble {
  background: #fff0f2;
  border-color: #ffe0e4;
}
.message-content {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 14px;
  line-height: 1.8;
}
.message-time {
  margin-top: 5px;
  color: #a0a9b9;
  font-size: 10px;
  text-align: right;
}
.typing-indicator {
  display: inline-block; margin-left: 3px;
  color: #d4a671; animation: blink .75s infinite;
}
@keyframes blink { 50% { opacity: .15; } }

.message-row.status { max-width: 850px; margin-bottom: 10px; }
.status-line {
  display: inline-flex; align-items: center; gap: 8px;
  margin-left: 46px; padding: 7px 11px; border-radius: 999px;
  color: #72809a; background: #fbf7f3; border: 1px solid #f7efe6;
  font-size: 11px;
}
.status-pulse {
  width: 7px; height: 7px; border-radius: 50%;
  background: #e0aa6d; box-shadow: 0 0 0 4px rgba(224,170,109,.12);
}

.composer-shell {
  position: absolute;
  left: 0; right: 0; bottom: 0;
  padding: 18px 20px 20px;
  background: linear-gradient(180deg, rgba(255,255,255,0), rgba(255,255,255,.95) 24%, #fff 55%);
}
.composer {
  max-width: 850px;
  margin: 0 auto;
  padding: 11px 12px 10px;
  border: 1px solid #f3e8db;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 14px 34px rgba(98,76,57,.1);
}
.love .composer { border-color: #f4d9de; box-shadow: 0 14px 34px rgba(182,76,94,.08); }
.input-box {
  width: 100%;
  min-height: 48px;
  max-height: 120px;
  padding: 8px 8px 2px;
  resize: none;
  border: 0;
  outline: 0;
  color: #564332;
  background: transparent;
  font-size: 14px;
  line-height: 1.6;
}
.input-box::placeholder { color: #a7b0c0; }
.composer-bottom { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-top: 7px; }
.composer-tools { display: flex; align-items: center; gap: 8px; min-width: 0; }
.circle-tool {
  width: 30px; height: 30px; padding: 0;
  border: 1px solid #f5ebe0; border-radius: 9px;
  color: #b17a3b; background: #fefcf9;
}
.input-tip { color: #a1aabc; font-size: 10px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.send-button {
  min-width: 82px; height: 36px;
  display: inline-flex; align-items: center; justify-content: center; gap: 6px;
  padding: 0 13px; border: 0; border-radius: 11px;
  color: #fff; background: linear-gradient(135deg, #e1ac70, #e4a359);
  font-size: 12px; font-weight: 700;
}
.love .send-button { background: linear-gradient(135deg, #ff7a80, #ff6875); }
.send-button:disabled { opacity: .48; cursor: not-allowed; }

@media (max-width: 720px) {
  .chat-room { min-height: 520px; }
  .chat-messages { padding: 18px 12px 145px; }
  .bubble-wrap { max-width: 86%; }
  .message-content { font-size: 13px; }
  .composer-shell { padding: 12px; }
  .input-tip { display: none; }
}
</style>
