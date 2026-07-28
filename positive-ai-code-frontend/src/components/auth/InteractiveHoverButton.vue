<template>
  <button
    type="submit"
    class="interactive-hover-btn"
    :disabled="disabled"
    :class="{ loading }"
  >
    <span class="label-default">{{ label }}</span>
    <span class="label-hover" aria-hidden="true">
      {{ hoverLabel || label }}
      <svg class="arrow" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path
          d="M3 8h10M9 4l4 4-4 4"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </span>
  </button>
</template>

<script setup lang="ts">
defineProps<{
  label: string
  hoverLabel?: string
  disabled?: boolean
  loading?: boolean
}>()
</script>

<style scoped>
.interactive-hover-btn {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 48px;
  padding: 0 20px;
  border: 1.5px solid #0f172a;
  border-radius: 999px;
  background: #fff;
  color: #0f172a;
  font-family: inherit;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  overflow: hidden;
  transition: border-color 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}

.interactive-hover-btn:hover:not(:disabled) {
  border-color: transparent;
}

.interactive-hover-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.label-default {
  position: relative;
  z-index: 1;
  transition: all 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}

.interactive-hover-btn:hover:not(:disabled) .label-default {
  transform: translateX(48px);
  opacity: 0;
}

.label-hover {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  background: linear-gradient(145deg, #34d399, #38bdf8);
  transform: translateY(100%);
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}

.interactive-hover-btn:hover:not(:disabled) .label-hover {
  transform: translateY(0);
}

.arrow {
  width: 16px;
  height: 16px;
}

.interactive-hover-btn.loading .label-default,
.interactive-hover-btn.loading .label-hover {
  opacity: 0.7;
}
</style>
