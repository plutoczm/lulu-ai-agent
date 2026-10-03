<template>
  <router-link class="brand" to="/">
    <img class="brand-mark" src="../../assets/brand/lulu-capybara-mark.jpg" alt="噜噜" />
    <div class="brand-copy">
      <strong>噜噜</strong>
      <span>LULU AI</span>
    </div>
  </router-link>
</template>

<style scoped>
.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: max-content;
}
.brand-mark {
  width: 40px;
  height: 40px;
  object-fit: cover;
  object-position: center 42%;
  border: 2px solid #fff0dc;
  border-radius: 50%;
  box-shadow: 0 4px 12px rgba(176, 111, 35, .16);
}
.brand-copy {
  display: grid;
  line-height: 1.05;
}
.brand-copy strong {
  color: #4b3521;
  font-size: 20px;
  letter-spacing: -.4px;
}
.brand-copy span {
  margin-top: 5px;
  color: #cfa26e;
  font-size: 11px;
  letter-spacing: .5px;
}
@media (max-width: 640px) {
  .brand-copy span { display: none; }
  .brand-mark { width: 40px; }
}
</style>
