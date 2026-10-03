<template>
  <div class="avatar-wrap" :class="type">
    <img src="../assets/brand/lulu-capybara-mark.jpg" alt="噜噜" />
  </div>
</template>

<script setup>
defineProps({
  type: { type: String, default: 'default' }
})
</script>

<style scoped>
.avatar-wrap {
  width: 100%; height: 100%; border-radius: 50%;
  display: grid; place-items: center; overflow: hidden;
  border: 1px solid #f3e8db; background: #fdf8f3;
}
.avatar-wrap.love { background: #fff0f2; border-color: #ffd9df; }
.avatar-wrap.super, .avatar-wrap.default { background: #fdf7f0; border-color: #fbeddd; }
img { width: 100%; height: 100%; object-fit: cover; object-position: center 42%; }
</style>
