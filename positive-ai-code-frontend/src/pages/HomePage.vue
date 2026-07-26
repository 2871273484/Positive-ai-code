<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  PaperClipOutlined,
  ThunderboltOutlined,
  ArrowUpOutlined,
  DownOutlined,
  AppstoreOutlined,
} from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  addApp,
  deleteApp,
  listMyAppVoByPage,
  listGoodAppVoByPage,
  generateAppCover,
} from '@/api/appController'
import { listAppCategories } from '@/api/appCategoryController'
import { getDeployUrl } from '@/config/env'
import AppCard from '@/components/AppCard.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const userPrompt = ref('')
const creating = ref(false)

const myApps = ref<API.AppVO[]>([])
const myAppsPage = reactive({
  current: 1,
  pageSize: 3,
  total: 0,
})

const featuredApps = ref<API.AppVO[]>([])
const featuredAppsPage = reactive({
  current: 1,
  pageSize: 12,
  total: 0,
})
const plazaLoading = ref(false)
const plazaLoadingMore = ref(false)

/** 案例广场：后端分类标签 + 排序 */
const plazaCategories = ref<API.AppCategoryVO[]>([])
/** null = 全部 */
const plazaCategoryId = ref<number | null>(null)
const plazaSort = ref<'default' | 'newest'>('default')

const displayedPlazaApps = computed(() => {
  let list = [...featuredApps.value]
  if (plazaSort.value === 'newest') {
    list.sort((a, b) => {
      const ta = a.createTime ? new Date(a.createTime).getTime() : 0
      const tb = b.createTime ? new Date(b.createTime).getTime() : 0
      return tb - ta
    })
  }
  return list
})

const hasMorePlazaApps = computed(
  () => featuredApps.value.length < (featuredAppsPage.total || 0),
)

/** 输入框空态占位文案轮播 */
const placeholderExamples = [
  '使用 Positive 创建一个数据看板网站…',
  '帮我做一个简洁的个人作品集…',
  '生成一个在线商城，支持购物车…',
  '设计一个企业官网，商务风格…',
  '做一个计算器网站，界面干净现代…',
  '创建一个博客，带文章列表和搜索…',
]
const placeholderIndex = ref(0)
let placeholderTimer: ReturnType<typeof setInterval> | undefined

const quickPrompts = [
  {
    label: '波普风电商',
    prompt:
      '创建一个波普风电商首页：大胆撞色、粗描边、大号商品卡片。含导航、促销横幅、商品网格、购物车入口，风格俏皮有冲击力。',
  },
  {
    label: '企业官网',
    prompt:
      '创建一个企业网站，风格大气、商务、专业。首页含导航栏、hero 区域、服务介绍、公司优势、客户评价与联系我们。',
  },
  {
    label: '电商后台',
    prompt:
      '做一个电商运营后台：数据概览、商品管理、订单列表、用户管理。左侧导航 + 右侧表格卡片，界面清晰好用。',
  },
  {
    label: '暗黑社区',
    prompt:
      '创建一个暗黑风格话题社区：深色背景、霓虹点缀。含话题列表、帖子详情、热门标签与发帖入口，适合深夜刷帖。',
  },
  {
    label: '个人博客',
    prompt:
      '创建一个现代化个人博客，含文章列表、详情页、分类标签、搜索。简洁排版，支持响应式。',
  },
  {
    label: '作品集',
    prompt:
      '制作设计师作品集网站：作品画廊、项目详情、个人简介与联系方式。大图网格，留白充足。',
  },
  {
    label: '在线商城',
    prompt:
      '构建在线商城：商品展示、购物车、登录注册、订单管理。现代化商品卡片，移动端友好。',
  },
  {
    label: '音乐官网',
    prompt:
      '做一个音乐人官网：专辑封面墙、试听列表、演出日程、关于与联系。氛围感强，视觉偏舞台灯光。',
  },
  {
    label: '数据看板',
    prompt:
      '创建一个运营数据看板：关键指标卡片、折线/柱状图区域、近期动态表。干净专业，适合汇报。',
  },
  {
    label: '餐厅点餐',
    prompt:
      '做一个餐厅点餐落地页：菜品分类、菜品卡片、购物车与下单区。暖色食欲感，照片占位清晰。',
  },
  {
    label: '旅行攻略',
    prompt:
      '创建一个旅行攻略网站：目的地推荐、行程卡片、游记列表与搜索。清新明亮，大图+短文案。',
  },
  {
    label: '计算器',
    prompt:
      '做一个计算器网站，界面干净现代，支持基础四则运算，键盘友好，适合快速计算。',
  },
]

const setPrompt = (prompt: string) => {
  userPrompt.value = prompt
}

/** 精选案例「做同款」：回填主页输入框，与主页生成流程一致 */
const remixFromFeatured = (prompt: string) => {
  userPrompt.value = prompt
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const createApp = async () => {
  // 未输入时，用当前轮播占位文案直接生成
  const prompt =
    userPrompt.value.trim() || placeholderExamples[placeholderIndex.value]?.trim() || ''
  if (!prompt) {
    message.warning('请输入应用描述')
    return
  }
  if (!userPrompt.value.trim()) {
    userPrompt.value = prompt
  }

  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push('/user/login')
    return
  }

  creating.value = true
  try {
    const res = await addApp({
      initPrompt: prompt,
    })

    if (res.data.code === 0 && res.data.data) {
      message.success('应用创建成功')
      const appId = String(res.data.data)
      await router.push(`/app/chat/${appId}`)
    } else {
      message.error('创建失败：' + res.data.message)
    }
  } catch (error) {
    console.error('创建应用失败：', error)
    message.error('创建失败，请重试')
  } finally {
    creating.value = false
  }
}

const loadMyApps = async () => {
  if (!loginUserStore.loginUser.id) {
    return
  }

  try {
    const res = await listMyAppVoByPage({
      pageNum: myAppsPage.current,
      pageSize: myAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      myApps.value = res.data.data.records || []
      myAppsPage.total = res.data.data.totalRow || 0
      void backfillMissingCovers(myApps.value)
    }
  } catch (error) {
    console.error('加载我的应用失败：', error)
  }
}

const backfillMissingCovers = async (apps: API.AppVO[]) => {
  const targets = apps.filter((app) => app.id && !app.cover).slice(0, 3)
  for (const app of targets) {
    try {
      const res = await generateAppCover({ appId: app.id! })
      if (res.data.code === 0 && res.data.data) {
        app.cover = res.data.data
      }
    } catch (error) {
      console.warn('补生成封面失败:', app.id, error)
    }
  }
}

const loadPlazaCategories = async () => {
  try {
    const res = await listAppCategories()
    if (res.data.code === 0 && res.data.data) {
      plazaCategories.value = res.data.data
    }
  } catch (error) {
    console.error('加载分类失败：', error)
  }
}

const loadFeaturedApps = async (append = false) => {
  if (append) {
    if (plazaLoadingMore.value || !hasMorePlazaApps.value) return
    plazaLoadingMore.value = true
  } else {
    plazaLoading.value = true
    featuredAppsPage.current = 1
  }
  try {
    const res = await listGoodAppVoByPage({
      pageNum: featuredAppsPage.current,
      pageSize: featuredAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
      categoryId: plazaCategoryId.value ?? undefined,
    })

    if (res.data.code === 0 && res.data.data) {
      const records = res.data.data.records || []
      featuredAppsPage.total = res.data.data.totalRow || 0
      if (append) {
        const exists = new Set(featuredApps.value.map((a) => a.id))
        featuredApps.value = [
          ...featuredApps.value,
          ...records.filter((a) => a.id != null && !exists.has(a.id)),
        ]
      } else {
        featuredApps.value = records
      }
      void backfillMissingCovers(records)
    }
  } catch (error) {
    console.error('加载精选应用失败：', error)
  } finally {
    plazaLoading.value = false
    plazaLoadingMore.value = false
  }
}

const loadMorePlazaApps = async () => {
  if (!hasMorePlazaApps.value || plazaLoadingMore.value) return
  featuredAppsPage.current += 1
  await loadFeaturedApps(true)
}

const setPlazaCategory = (categoryId: number | null) => {
  plazaCategoryId.value = categoryId
  featuredAppsPage.current = 1
  void loadFeaturedApps(false)
}

const onPlazaSort = ({ key }: { key: string | number }) => {
  plazaSort.value = String(key) === 'newest' ? 'newest' : 'default'
  featuredAppsPage.current = 1
  void loadFeaturedApps(false)
}

const goAllCases = () => {
  setPlazaCategory(null)
  const el = document.getElementById('casePlaza')
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const viewChat = (appId: string | number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}`)
  }
}

const viewPlazaChat = (appId: string | number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}?view=1`)
  }
}

const viewWork = (app: API.AppVO) => {
  if (app.deployKey) {
    window.open(getDeployUrl(app.deployKey), '_blank')
  }
}

const deleteMyApp = (app: API.AppVO) => {
  if (!app.id) {
    return
  }
  Modal.confirm({
    title: '删除应用',
    content: `确定删除「${app.appName || '未命名应用'}」？删除后不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      const res = await deleteApp({ id: app.id })
      if (res.data.code === 0) {
        message.success('已删除')
        if (myApps.value.length === 1 && myAppsPage.current > 1) {
          myAppsPage.current -= 1
        }
        await loadMyApps()
      } else {
        message.error(res.data.message || '删除失败')
        return Promise.reject()
      }
    },
  })
}

let removeMouseListener: (() => void) | undefined

onMounted(() => {
  loadMyApps()
  void loadPlazaCategories()
  void loadFeaturedApps()

  placeholderTimer = setInterval(() => {
    placeholderIndex.value = (placeholderIndex.value + 1) % placeholderExamples.length
  }, 3200)

  const handleMouseMove = (e: MouseEvent) => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
    const x = (e.clientX / window.innerWidth) * 100
    const y = (e.clientY / window.innerHeight) * 100
    document.documentElement.style.setProperty('--mouse-x', `${x}%`)
    document.documentElement.style.setProperty('--mouse-y', `${y}%`)
  }

  document.addEventListener('mousemove', handleMouseMove)
  removeMouseListener = () => document.removeEventListener('mousemove', handleMouseMove)
})

onUnmounted(() => {
  removeMouseListener?.()
  if (placeholderTimer) clearInterval(placeholderTimer)
})
</script>

<template>
  <div id="homePage">
    <div class="ambient ambient-a" aria-hidden="true" />
    <div class="ambient ambient-b" aria-hidden="true" />
    <div class="noise" aria-hidden="true" />

    <div class="container">
      <section class="hero">
        <p class="brand-mark">Positive</p>
        <h1 class="hero-title">一句话，呈所想</h1>
        <p class="hero-desc">与 AI 对话，创建应用和网站</p>

        <div class="prompt-shell">
          <!-- 输入框右上角吉祥物动画（参考秒哒坐姿角色） -->
          <div class="mascot" aria-hidden="true">
            <span class="mascot-sparkle" />
            <svg class="mascot-body" viewBox="0 0 120 120" xmlns="http://www.w3.org/2000/svg">
              <defs>
                <linearGradient id="mascotGrad" x1="20%" y1="10%" x2="80%" y2="90%">
                  <stop offset="0%" stop-color="#7dd3fc" />
                  <stop offset="55%" stop-color="#38bdf8" />
                  <stop offset="100%" stop-color="#0284c7" />
                </linearGradient>
                <filter id="mascotSoft" x="-20%" y="-20%" width="140%" height="140%">
                  <feDropShadow dx="0" dy="6" stdDeviation="4" flood-color="#0284c7" flood-opacity="0.25" />
                </filter>
              </defs>
              <ellipse cx="60" cy="68" rx="38" ry="34" fill="url(#mascotGrad)" filter="url(#mascotSoft)" />
              <ellipse cx="60" cy="42" rx="30" ry="28" fill="url(#mascotGrad)" />
              <circle cx="48" cy="40" r="4.2" fill="#0f172a" class="mascot-eye left" />
              <circle cx="72" cy="40" r="4.2" fill="#0f172a" class="mascot-eye right" />
              <path
                d="M50 52 Q60 60 70 52"
                fill="none"
                stroke="#0f172a"
                stroke-width="3"
                stroke-linecap="round"
                class="mascot-mouth"
              />
              <ellipse cx="38" cy="50" rx="6" ry="3.5" fill="#7dd3fc" opacity="0.55" />
              <ellipse cx="82" cy="50" rx="6" ry="3.5" fill="#7dd3fc" opacity="0.55" />
              <ellipse cx="44" cy="86" rx="9" ry="6" fill="#0369a1" opacity="0.35" class="mascot-foot" />
              <ellipse cx="76" cy="86" rx="9" ry="6" fill="#0369a1" opacity="0.35" class="mascot-foot" />
            </svg>
          </div>

          <div class="prompt-field">
            <Transition name="ph-slide" mode="out-in">
              <span
                v-if="!userPrompt.trim()"
                :key="placeholderIndex"
                class="prompt-placeholder"
              >
                {{ placeholderExamples[placeholderIndex] }}
              </span>
            </Transition>
            <a-textarea
              v-model:value="userPrompt"
              placeholder=" "
              :rows="3"
              :maxlength="1000"
              class="prompt-input"
              aria-label="应用描述"
            />
          </div>
          <div class="prompt-toolbar">
            <div class="toolbar-left">
              <span class="tool-chip" title="附件（即将支持）">
                <PaperClipOutlined />
              </span>
              <span class="tool-chip accent" title="AI 生成">
                <ThunderboltOutlined />
                <span>AI 生成</span>
              </span>
            </div>
            <button
              type="button"
              class="submit-btn"
              :disabled="creating"
              aria-label="开始生成"
              @click="createApp"
            >
              <ArrowUpOutlined v-if="!creating" />
              <span v-else class="submit-loading" />
            </button>
          </div>
        </div>

        <div class="quick-actions">
          <button
            v-for="item in quickPrompts"
            :key="item.label"
            type="button"
            class="pill"
            @click="setPrompt(item.prompt)"
          >
            {{ item.label }}
          </button>
        </div>
      </section>

      <!-- 我的作品：一行 3 卡 -->
      <section v-if="loginUserStore.loginUser.id" class="gallery-section">
        <div class="section-head">
          <h2 class="section-title">我的作品</h2>
          <RouterLink to="/my/apps" class="section-more">管理全部作品 ></RouterLink>
        </div>
        <div v-if="myApps.length" class="mine-grid">
          <AppCard
            v-for="app in myApps"
            :key="app.id"
            :app="app"
            variant="mine"
            :deletable="true"
            @view-chat="viewChat"
            @view-work="viewWork"
            @delete="deleteMyApp"
          />
        </div>
        <div v-else class="empty-hint">还没有作品，上面输入一句话开始创建吧</div>
      </section>

      <!-- 案例广场：筛选条 + 两行四列 -->
      <section id="casePlaza" class="gallery-section">
        <div class="section-head">
          <h2 class="section-title">案例广场</h2>
        </div>

        <div class="plaza-toolbar">
          <a-dropdown :trigger="['click']">
            <button type="button" class="sort-btn">
              {{ plazaSort === 'newest' ? '最新创建' : '默认排序' }}
              <DownOutlined class="sort-icon" />
            </button>
            <template #overlay>
              <a-menu @click="onPlazaSort">
                <a-menu-item key="default">默认排序</a-menu-item>
                <a-menu-item key="newest">最新创建</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>

          <div class="plaza-tags">
            <button
              type="button"
              class="plaza-tag"
              :class="{ active: plazaCategoryId === null }"
              @click="setPlazaCategory(null)"
            >
              全部
            </button>
            <button
              v-for="cat in plazaCategories"
              :key="cat.id"
              type="button"
              class="plaza-tag"
              :class="{ active: plazaCategoryId === cat.id }"
              @click="setPlazaCategory(cat.id ?? null)"
            >
              {{ cat.name }}
            </button>
          </div>

          <button type="button" class="all-cases-btn" @click="goAllCases">
            <AppstoreOutlined />
            全部案例 >
          </button>
        </div>

        <div v-if="displayedPlazaApps.length" class="plaza-grid">
          <AppCard
            v-for="app in displayedPlazaApps"
            :key="app.id"
            :app="app"
            variant="plaza"
            :featured="true"
            @view-chat="viewPlazaChat"
            @view-work="viewWork"
            @remix="remixFromFeatured"
          />
        </div>
        <div v-else-if="plazaLoading" class="empty-hint">加载中…</div>
        <div v-else class="empty-hint">这个分类暂时没有案例，换一个试试</div>

        <div v-if="hasMorePlazaApps" class="plaza-more-wrap">
          <button
            type="button"
            class="plaza-more-btn"
            :disabled="plazaLoadingMore"
            @click="loadMorePlazaApps"
          >
            {{ plazaLoadingMore ? '加载中…' : '查看更多' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
#homePage {
  --color-bg: #f4faf8;
  --color-fg: #0f172a;
  --color-muted: #64748b;
  --color-mint: #7dd3c0;
  --color-sky: #93c5fd;
  --color-cream: #ffffff;
  --glass: rgba(255, 255, 255, 0.72);
  --radius-xl: 28px;
  --radius-pill: 999px;
  --shadow-soft: 0 18px 50px rgba(15, 23, 42, 0.08);
  --ease: cubic-bezier(0.22, 1, 0.36, 1);

  position: relative;
  min-height: 100vh;
  overflow-x: hidden;
  font-family: 'Nunito Sans', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: var(--color-fg);
  background:
    radial-gradient(900px 520px at 12% 8%, rgba(125, 211, 192, 0.45), transparent 60%),
    radial-gradient(800px 480px at 88% 18%, rgba(147, 197, 253, 0.4), transparent 55%),
    radial-gradient(700px 420px at 50% 92%, rgba(186, 230, 253, 0.35), transparent 55%),
    linear-gradient(180deg, #ffffff 0%, #f4faf8 42%, #e8f4ff 100%);
}

.ambient {
  position: fixed;
  border-radius: 50%;
  filter: blur(40px);
  pointer-events: none;
  z-index: 0;
  opacity: 0.45;
}

.ambient-a {
  width: 280px;
  height: 280px;
  top: 12%;
  left: 8%;
  background: rgba(125, 211, 192, 0.55);
  animation: floatA 12s var(--ease) infinite alternate;
}

.ambient-b {
  width: 320px;
  height: 320px;
  top: 20%;
  right: 6%;
  background: rgba(147, 197, 253, 0.5);
  animation: floatB 14s var(--ease) infinite alternate;
}

.noise {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 1;
  opacity: 0.035;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
}

.container {
  position: relative;
  z-index: 2;
  max-width: 1180px;
  margin: 0 auto;
  padding: 8px 24px 72px;
}

.hero {
  text-align: center;
  padding: 48px 0 36px;
}

.brand-mark {
  margin: 0 0 14px;
  font-family: 'Varela Round', 'Nunito Sans', sans-serif;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #0f172a;
}

.hero-title {
  margin: 0 0 12px;
  font-family: 'Varela Round', 'Nunito Sans', sans-serif;
  font-size: clamp(36px, 6vw, 56px);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.15;
  color: #0f172a;
}

.hero-desc {
  margin: 0 0 56px;
  font-size: 17px;
  color: var(--color-muted);
}

.prompt-shell {
  position: relative;
  max-width: 720px;
  margin: 0 auto 18px;
  padding: 16px 16px 12px;
  border-radius: var(--radius-xl);
  background: var(--glass);
  border: 1px solid rgba(255, 255, 255, 0.85);
  box-shadow: var(--shadow-soft);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  text-align: left;
  transition:
    transform 0.25s var(--ease),
    box-shadow 0.25s var(--ease);
}

.mascot {
  position: absolute;
  top: -52px;
  right: 28px;
  width: 96px;
  height: 96px;
  z-index: 3;
  pointer-events: none;
  animation: mascotFloat 3.2s var(--ease) infinite;
  transform-origin: 50% 85%;
}

.mascot-body {
  width: 100%;
  height: 100%;
  display: block;
  filter: drop-shadow(0 10px 18px rgba(14, 165, 233, 0.28));
}

.mascot-sparkle {
  position: absolute;
  top: 6px;
  right: 4px;
  width: 14px;
  height: 14px;
  background: #f472b6;
  clip-path: polygon(50% 0%, 62% 38%, 100% 50%, 62% 62%, 50% 100%, 38% 62%, 0% 50%, 38% 38%);
  animation: sparklePop 2.4s var(--ease) infinite;
}

.mascot-eye {
  transform-box: fill-box;
  transform-origin: center;
  animation: mascotBlink 4.5s steps(1, end) infinite;
}

@keyframes mascotFloat {
  0%,
  100% {
    transform: translateY(0) rotate(-2deg);
  }
  40% {
    transform: translateY(-10px) rotate(2deg);
  }
  70% {
    transform: translateY(-4px) rotate(-1deg);
  }
}

@keyframes sparklePop {
  0%,
  100% {
    opacity: 0.35;
    transform: scale(0.7) rotate(0deg);
  }
  45% {
    opacity: 1;
    transform: scale(1.15) rotate(18deg);
  }
}

@keyframes mascotBlink {
  0%,
  42%,
  46%,
  100% {
    transform: scaleY(1);
  }
  44% {
    transform: scaleY(0.12);
  }
}

.prompt-shell:focus-within {
  transform: translateY(-2px);
  box-shadow: 0 22px 56px rgba(15, 23, 42, 0.12);
}

.prompt-field {
  position: relative;
  min-height: 78px;
}

.prompt-placeholder {
  position: absolute;
  top: 4px;
  left: 6px;
  right: 6px;
  z-index: 1;
  pointer-events: none;
  font-size: 16px;
  line-height: 1.55;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ph-slide-enter-active,
.ph-slide-leave-active {
  transition:
    opacity 0.35s var(--ease),
    transform 0.35s var(--ease);
}

.ph-slide-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.ph-slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.prompt-input {
  position: relative;
  z-index: 2;
  width: 100%;
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  resize: none;
  font-size: 16px;
  line-height: 1.55;
  color: #0f172a;
  padding: 4px 6px 8px !important;
}

.prompt-input:focus {
  box-shadow: none !important;
}

.prompt-input :deep(textarea) {
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
  padding: 0 !important;
  font-family: inherit;
}

.prompt-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 4px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tool-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 12px;
  border-radius: var(--radius-pill);
  color: #64748b;
  background: rgba(241, 245, 249, 0.9);
  font-size: 13px;
  cursor: default;
}

.tool-chip.accent {
  color: #0f766e;
  background: rgba(167, 243, 208, 0.55);
}

.submit-btn {
  width: 42px;
  height: 42px;
  border: none;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #fff;
  background: linear-gradient(145deg, #34d399, #38bdf8);
  box-shadow: 0 10px 24px rgba(56, 189, 248, 0.35);
  transition:
    transform 0.2s var(--ease),
    opacity 0.2s var(--ease);
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px) scale(1.03);
}

.submit-btn:disabled {
  opacity: 0.7;
  cursor: wait;
}

.submit-loading {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  animation: spin 0.7s linear infinite;
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  max-width: 920px;
  margin: 0 auto 40px;
}

.pill {
  border: 1px solid rgba(255, 255, 255, 0.9);
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(10px);
  color: #475569;
  border-radius: var(--radius-pill);
  padding: 8px 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition:
    background 0.2s var(--ease),
    transform 0.2s var(--ease),
    color 0.2s var(--ease);
}

.pill:hover {
  background: #fff;
  color: #0f172a;
  transform: translateY(-1px);
}

.gallery-section {
  position: relative;
  z-index: 1;
  margin-bottom: 40px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.section-title {
  margin: 0;
  font-family: 'Varela Round', 'Nunito Sans', sans-serif;
  font-size: 26px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: -0.02em;
}

.section-more {
  font-size: 13px;
  color: #64748b;
  text-decoration: none;
}

.section-more:hover {
  color: #0f172a;
}

.mine-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.plaza-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.sort-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 10px;
  background: #fff;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;
}

.sort-icon {
  font-size: 10px;
  color: #94a3b8;
}

.plaza-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.plaza-tag {
  height: 32px;
  padding: 0 14px;
  border: none;
  border-radius: 999px;
  background: #eef2f6;
  color: #475569;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background 0.2s var(--ease),
    color 0.2s var(--ease);
}

.plaza-tag:hover {
  background: #e2e8f0;
}

.plaza-tag.active {
  background: #0f172a;
  color: #fff;
}

.all-cases-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 10px;
  background: #fff;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;
  white-space: nowrap;
}

.all-cases-btn:hover {
  background: #f8fafc;
}

.plaza-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.plaza-more-wrap {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

.plaza-more-btn {
  min-width: 148px;
  height: 40px;
  padding: 0 28px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 12px;
  background: #fff;
  color: #334155;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.06);
  transition:
    background 0.2s var(--ease),
    transform 0.2s var(--ease);
}

.plaza-more-btn:hover:not(:disabled) {
  background: #f8fafc;
  transform: translateY(-1px);
}

.plaza-more-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.empty-hint {
  text-align: center;
  color: #94a3b8;
  padding: 36px 12px;
  font-size: 14px;
  background: rgba(255, 255, 255, 0.55);
  border-radius: 16px;
}

@keyframes floatA {
  from {
    transform: translate(0, 0);
  }
  to {
    transform: translate(24px, 18px);
  }
}

@keyframes floatB {
  from {
    transform: translate(0, 0);
  }
  to {
    transform: translate(-20px, 22px);
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1100px) {
  .plaza-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .mine-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .plaza-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .quick-actions {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .all-cases-btn {
    display: none;
  }
}

@media (max-width: 640px) {
  .container {
    padding: 4px 16px 56px;
  }

  .hero {
    padding-top: 28px;
  }

  .mine-grid,
  .plaza-grid {
    grid-template-columns: 1fr;
  }

  .quick-actions {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .plaza-toolbar {
    flex-direction: column;
    align-items: stretch;
  }
}

@media (max-width: 640px) {
  .mascot {
    width: 72px;
    height: 72px;
    top: -38px;
    right: 12px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ambient-a,
  .ambient-b,
  .submit-loading,
  .prompt-shell,
  .pill,
  .submit-btn,
  .mascot,
  .mascot-sparkle,
  .mascot-eye,
  .ph-slide-enter-active,
  .ph-slide-leave-active {
    animation: none !important;
    transition: none !important;
  }
}
</style>
