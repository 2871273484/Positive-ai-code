<script setup lang="ts">
import BasicLayout from '@/layouts/BasicLayout.vue'
</script>

<template>
  <BasicLayout />
</template>

<style>
/* 全局背景与主页渐变一致，避免 ant-layout 灰底露出来 */
html,
body,
#app {
  min-height: 100%;
  margin: 0;
  background:
    radial-gradient(1100px 640px at 8% -4%, rgba(167, 243, 208, 0.72), transparent 58%),
    radial-gradient(980px 560px at 92% 6%, rgba(186, 230, 253, 0.7), transparent 55%),
    radial-gradient(900px 520px at 78% 88%, rgba(199, 210, 254, 0.45), transparent 58%),
    radial-gradient(760px 480px at 18% 78%, rgba(165, 243, 252, 0.4), transparent 55%),
    linear-gradient(165deg, #f0fdf9 0%, #e8f6ff 38%, #eef2ff 72%, #f5f3ff 100%);
  background-attachment: fixed;
}

.ant-layout,
.ant-layout-header,
.ant-layout-content,
.ant-layout-footer {
  background: transparent !important;
}
</style>
