<template>
  <svg class="app-icon" :style="{ width: `${size}px`, height: `${size}px` }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <template v-if="name === 'home'"><path d="M3 10.7 12 3l9 7.7"/><path d="M5.5 9.8V21h13V9.8"/><path d="M9.5 21v-6h5v6"/></template>
    <template v-else-if="name === 'grid'"><rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/></template>
    <template v-else-if="name === 'book'"><path d="M4 5.5A3.5 3.5 0 0 1 7.5 2H11v17H7.5A3.5 3.5 0 0 0 4 22z"/><path d="M20 5.5A3.5 3.5 0 0 0 16.5 2H13v17h3.5A3.5 3.5 0 0 1 20 22z"/></template>
    <template v-else-if="name === 'activity'"><path d="M3 12h4l2.5-6 5 12 2.5-6H21"/></template>
    <template v-else-if="name === 'settings'"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.83 2.83-.06-.06A1.7 1.7 0 0 0 15 19.4a1.7 1.7 0 0 0-1 .6 1.7 1.7 0 0 0-.4 1.1V21h-4v-.1A1.7 1.7 0 0 0 8.6 19.4a1.7 1.7 0 0 0-1.88.34l-.06.06-2.83-2.83.06-.06A1.7 1.7 0 0 0 4.2 15a1.7 1.7 0 0 0-.6-1A1.7 1.7 0 0 0 2.5 13.6H2.4v-4h.1A1.7 1.7 0 0 0 4.2 8.6a1.7 1.7 0 0 0-.34-1.88l-.06-.06 2.83-2.83.06.06A1.7 1.7 0 0 0 8.6 4.2a1.7 1.7 0 0 0 1-.6A1.7 1.7 0 0 0 10 2.5V2.4h4v.1A1.7 1.7 0 0 0 15 4.2a1.7 1.7 0 0 0 1.88-.34l.06-.06 2.83 2.83-.06.06A1.7 1.7 0 0 0 19.4 8.6a1.7 1.7 0 0 0 .6 1 1.7 1.7 0 0 0 1.1.4h.1v4h-.1A1.7 1.7 0 0 0 19.4 15z"/></template>
    <template v-else-if="name === 'heart'"><path d="M20.8 4.6c-2-2-5.2-1.8-7 .5L12 7.2 10.2 5c-1.8-2.2-5-2.4-7-.5-2.1 2-2.2 5.3-.3 7.4L12 21l9.1-9.1c1.9-2.1 1.8-5.4-.3-7.3z"/></template>
    <template v-else-if="name === 'bot'"><rect x="5" y="7" width="14" height="11" rx="4"/><path d="M12 3v4M8.5 12h.01M15.5 12h.01M9 15.5h6"/></template>
    <template v-else-if="name === 'brain'"><path d="M9.5 4a3 3 0 0 0-5 2.2A3 3 0 0 0 3 11.8 3.5 3.5 0 0 0 6.5 18H9V4z"/><path d="M14.5 4a3 3 0 0 1 5 2.2A3 3 0 0 1 21 11.8a3.5 3.5 0 0 1-3.5 6.2H15V4z"/><path d="M9 8H7M15 8h2M9 13H6.5M15 13h2.5"/></template>
    <template v-else-if="name === 'search'"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></template>
    <template v-else-if="name === 'tools'"><path d="m14 6 4-4 4 4-4 4z"/><path d="M3 13h8v8H3z"/><path d="m14 18 4-4 4 4-4 4z"/></template>
    <template v-else-if="name === 'user'"><circle cx="12" cy="8" r="4"/><path d="M4 22a8 8 0 0 1 16 0"/></template>
    <template v-else-if="name === 'file'"><path d="M6 2h8l4 4v16H6z"/><path d="M14 2v5h5M9 13h6M9 17h6"/></template>
    <template v-else-if="name === 'globe'"><circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18"/></template>
    <template v-else-if="name === 'map'"><path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3z"/><path d="M9 3v15M15 6v15"/></template>
    <template v-else-if="name === 'image'"><rect x="3" y="4" width="18" height="16" rx="3"/><circle cx="8.5" cy="9" r="1.5"/><path d="m4 18 5-5 4 4 2-2 5 5"/></template>
    <template v-else-if="name === 'database'"><ellipse cx="12" cy="5" rx="7" ry="3"/><path d="M5 5v7c0 1.7 3.1 3 7 3s7-1.3 7-3V5"/><path d="M5 12v7c0 1.7 3.1 3 7 3s7-1.3 7-3v-7"/></template>
    <template v-else-if="name === 'lightning'"><path d="m13 2-8 12h6l-1 8 9-13h-6z"/></template>
    <template v-else><circle cx="12" cy="12" r="8"/></template>
  </svg>
</template>

<script setup>
defineProps({
  name: { type: String, default: 'grid' },
  size: { type: [String, Number], default: 20 }
})
</script>

<style scoped>
.app-icon { flex: none; }
</style>
