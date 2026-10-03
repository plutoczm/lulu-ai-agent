<template>
  <div v-if="publicDemo" class="public-demo-banner">
    公网展示模式：页面可浏览，AI 对话、数据写入、模型切换与账号授权已临时关闭
  </div>
  <router-view />
</template>

<script setup>
const publicDemo = import.meta.env.VITE_PUBLIC_DEMO === 'true'
</script>

<style scoped>
.public-demo-banner {
  position: fixed;
  left: 50%;
  top: 10px;
  z-index: 9999;
  transform: translateX(-50%);
  max-width: calc(100vw - 28px);
  padding: 8px 14px;
  border: 1px solid rgba(242, 140, 40, .28);
  border-radius: 999px;
  color: #6b421f;
  background: rgba(255, 248, 239, .94);
  box-shadow: 0 8px 24px rgba(90, 58, 34, .10);
  backdrop-filter: blur(10px);
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;
}
@media (max-width: 760px) {
  .public-demo-banner {
    top: 6px;
    font-size: 10px;
    white-space: normal;
    text-align: center;
  }
}
</style>
