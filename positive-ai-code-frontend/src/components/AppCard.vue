<template>
  <!-- 我的作品：大图 + 标题 + 管理 / 删除 -->
  <div
    v-if="variant === 'mine'"
    class="app-card app-card--mine"
    @click="handleViewChat"
  >
    <div class="cover">
      <img v-if="app.cover" :src="app.cover" :alt="displayName" />
      <div v-else class="cover-placeholder">AI</div>
      <div class="mine-hover">
        <button type="button" class="hover-btn" @click.stop="handleViewChat">管理</button>
        <button
          v-if="app.deployKey"
          type="button"
          class="hover-btn ghost"
          @click.stop="handleViewWork"
        >
          查看作品
        </button>
        <button
          v-if="deletable"
          type="button"
          class="hover-btn danger"
          @click.stop="handleDelete"
        >
          删除
        </button>
      </div>
    </div>
    <div class="mine-foot">
      <div class="mine-text">
        <h3 class="title">{{ displayName }}</h3>
        <p class="time">创建于 {{ relativeTime }}</p>
      </div>
      <div class="mine-actions" @click.stop>
        <button type="button" class="action-btn" @click="handleViewChat">管理</button>
        <button
          v-if="deletable"
          type="button"
          class="action-btn danger"
          @click="handleDelete"
        >
          删除
        </button>
      </div>
    </div>
  </div>

  <!-- 案例广场：封面 + 标题 + 创建者（头像+昵称） -->
  <div
    v-else-if="variant === 'plaza'"
    class="app-card app-card--plaza"
    @click="handleViewChat"
  >
    <div class="cover">
      <img v-if="app.cover" :src="app.cover" :alt="displayName" />
      <div v-else class="cover-placeholder">AI</div>
      <div class="plaza-hover">
        <button type="button" class="hover-btn" @click.stop="handleViewChat">查看对话</button>
        <button
          v-if="app.deployKey"
          type="button"
          class="hover-btn ghost"
          @click.stop="handleViewWork"
        >
          查看部署
        </button>
      </div>
    </div>
    <div class="plaza-body">
      <div class="title-row">
        <h3 class="title">{{ displayName }}</h3>
        <div v-if="categoryLabels.length" class="cat-tags">
          <span v-for="tag in categoryLabels" :key="tag" class="cat-tag">{{ tag }}</span>
        </div>
      </div>
      <div class="creator-row">
        <a-avatar :src="app.user?.userAvatar" :size="24" class="avatar">
          {{ authorInitial }}
        </a-avatar>
        <span class="creator-name" :title="authorName">{{ authorName }}</span>
        <span class="creator-date">{{ dateText }}</span>
      </div>
    </div>
  </div>

  <!-- 兼容旧用法 -->
  <div v-else class="app-card app-card--default" @click="handleViewChat">
    <div class="cover">
      <img v-if="app.cover" :src="app.cover" :alt="displayName" />
      <div v-else class="cover-placeholder">AI</div>
    </div>
    <div class="plaza-body">
      <h3 class="title">{{ displayName }}</h3>
      <div class="creator-row">
        <a-avatar :src="app.user?.userAvatar" :size="24" class="avatar">
          {{ authorInitial }}
        </a-avatar>
        <span class="creator-name">{{ authorName }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatDate, formatRelativeTime } from '@/utils/time'

interface Props {
  app: API.AppVO
  featured?: boolean
  deletable?: boolean
  /** mine=我的作品；plaza=案例广场 */
  variant?: 'mine' | 'plaza' | 'default'
}

interface Emits {
  (e: 'view-chat', appId: string | number | undefined): void
  (e: 'view-work', app: API.AppVO): void
  (e: 'delete', app: API.AppVO): void
}

const props = withDefaults(defineProps<Props>(), {
  featured: false,
  deletable: false,
  variant: 'default',
})

const emit = defineEmits<Emits>()

const displayName = computed(() => {
  const name = props.app.appName?.trim()
  const prompt = props.app.initPrompt?.trim() || ''
  if (name && !looksLikeRawPrompt(name) && name.length <= 20) {
    return name
  }
  if (prompt) {
    return deriveShortName(prompt)
  }
  return name || '未命名应用'
})

const authorName = computed(() => {
  const name = props.app.user?.userName?.trim()
  if (!name || name === '未登录' || name === '未知用户') {
    return props.variant === 'plaza' || props.featured ? '官方精选' : '无名'
  }
  // 「无名」也展示出来，方便识别创建者
  return name
})

const authorInitial = computed(() => authorName.value.charAt(0) || 'U')
const relativeTime = computed(() => formatRelativeTime(props.app.createTime) || '刚刚')
const dateText = computed(() => formatDate(props.app.createTime) || '')
const categoryLabels = computed(() => {
  if (props.app.categoryNames?.length) {
    return props.app.categoryNames.slice(0, 3)
  }
  return props.app.categoryName ? [props.app.categoryName] : []
})

const looksLikeRawPrompt = (text: string) =>
  /^(做一个|帮我|创建|生成一个|设计一个|设计|请做|使用)/.test(text)

const deriveShortName = (prompt: string) => {
  let text = prompt.replace(/[\r\n]+/g, ' ').replace(/\s+/g, '')
  text = text.split(/[，,。.!！？?；;：:]/)[0] || text
  text = text
    .replace(/^(帮我|请|麻烦|使用\S{0,12})?(创建|做|生成|制作|搭建|开发|设计)(一个|个|一)?/, '')
    .replace(/^(简单的|简单|基础的|基础|小型的|小型|精美的|精美|专业的|专业|现代化的|现代|简洁的|简洁)/, '')
    .replace(/^的/, '')
  const match = text.match(
    /([\u4e00-\u9fa5A-Za-z0-9]{1,12}(?:网站|应用|系统|平台|商城|博客|官网|主页|首页|工具|计算器))/,
  )
  return (match?.[1] || text || '未命名应用').slice(0, 16)
}

const handleViewChat = () => emit('view-chat', props.app.id)
const handleViewWork = () => emit('view-work', props.app)
const handleDelete = () => emit('delete', props.app)
</script>

<style scoped>
.app-card {
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid rgba(15, 23, 42, 0.06);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
  transition:
    transform 0.3s cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}

.app-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.1);
}

.cover {
  position: relative;
  width: 100%;
  background: #f1f5f9;
  overflow: hidden;
}

.app-card--mine .cover {
  aspect-ratio: 16 / 10;
}

.app-card--plaza .cover,
.app-card--default .cover {
  aspect-ratio: 16 / 11;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  min-height: 140px;
  display: grid;
  place-items: center;
  font-family: 'Varela Round', 'Nunito Sans', sans-serif;
  font-weight: 700;
  color: #94a3b8;
  letter-spacing: 0.08em;
}

.mine-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 14px 16px;
}

.mine-text {
  min-width: 0;
  flex: 1;
}

.mine-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.action-btn {
  height: 30px;
  padding: 0 12px;
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 999px;
  background: #f8fafc;
  color: #0f172a;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.action-btn:hover {
  background: #eef2f7;
}

.action-btn.danger {
  border-color: rgba(185, 28, 28, 0.2);
  background: #fef2f2;
  color: #b91c1c;
}

.action-btn.danger:hover {
  background: #fee2e2;
}

.title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: 'Nunito Sans', 'PingFang SC', sans-serif;
}

.time {
  margin: 6px 0 0;
  font-size: 12px;
  color: #94a3b8;
}

.mine-hover,
.plaza-hover {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: rgba(15, 23, 42, 0.36);
  opacity: 0;
  transition: opacity 0.25s cubic-bezier(0.22, 1, 0.36, 1);
}

.app-card--mine:hover .mine-hover,
.app-card--plaza:hover .plaza-hover {
  opacity: 1;
}

.hover-btn {
  height: 32px;
  padding: 0 12px;
  border: none;
  border-radius: 999px;
  background: #fff;
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.hover-btn.ghost {
  background: rgba(255, 255, 255, 0.88);
}

.hover-btn.danger {
  background: #fee2e2;
  color: #b91c1c;
}

.plaza-body {
  padding: 12px 12px 14px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.title-row .title {
  flex: 1;
  min-width: 0;
}

.cat-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  flex-shrink: 0;
  max-width: 50%;
  justify-content: flex-end;
}

.cat-tag {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  color: #2563eb;
  background: #eff6ff;
  padding: 2px 8px;
  border-radius: 999px;
}

.creator-row {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #a7f3d0, #bfdbfe);
}

.creator-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  color: #334155;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.creator-date {
  flex-shrink: 0;
  font-size: 12px;
  color: #94a3b8;
}
</style>
