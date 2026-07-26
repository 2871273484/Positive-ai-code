<template>
  <div id="myAppsPage">
    <div class="ambient ambient-a" aria-hidden="true" />
    <div class="ambient ambient-b" aria-hidden="true" />

    <div class="page-shell">
      <header class="page-head">
        <div>
          <p class="eyebrow">Workspace</p>
          <h1 class="title">我的应用</h1>
          <p class="desc">管理作品，不想要的可直接删掉</p>
        </div>
        <RouterLink to="/" class="create-link">去创建</RouterLink>
      </header>

      <section class="list-card">
        <div class="list-head">
          <h2>全部作品</h2>
          <span>共 {{ page.total }} 个</span>
        </div>

        <div v-if="loading" class="empty-hint">加载中…</div>
        <div v-else-if="!apps.length" class="empty-hint">
          还没有应用
          <RouterLink to="/">回主页说一句话</RouterLink>
        </div>
        <div v-else class="app-grid">
          <AppCard
            v-for="app in apps"
            :key="app.id"
            :app="app"
            variant="mine"
            :deletable="true"
            @view-chat="viewChat"
            @view-work="viewWork"
            @delete="onDelete"
          />
        </div>

        <div v-if="page.total > page.pageSize" class="pagination-wrapper">
          <a-pagination
            v-model:current="page.current"
            v-model:page-size="page.pageSize"
            :total="page.total"
            :show-size-changer="false"
            @change="loadApps"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import AppCard from '@/components/AppCard.vue'
import { deleteApp, listMyAppVoByPage } from '@/api/appController'
import { getDeployUrl } from '@/config/env'
import { useLoginUserStore } from '@/stores/loginUser'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const apps = ref<API.AppVO[]>([])
const loading = ref(false)
const page = reactive({
  current: 1,
  pageSize: 9,
  total: 0,
})

const loadApps = async () => {
  if (!loginUserStore.loginUser.id) {
    return
  }
  loading.value = true
  try {
    const res = await listMyAppVoByPage({
      pageNum: page.current,
      pageSize: page.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })
    if (res.data.code === 0 && res.data.data) {
      apps.value = res.data.data.records || []
      page.total = res.data.data.totalRow || 0
    } else {
      message.error(res.data.message || '加载失败')
    }
  } catch (error) {
    console.error(error)
    message.error('加载失败，请重试')
  } finally {
    loading.value = false
  }
}

const viewChat = (appId: string | number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}`)
  }
}

const viewWork = (app: API.AppVO) => {
  if (app.deployKey) {
    window.open(getDeployUrl(app.deployKey), '_blank')
  }
}

const onDelete = (app: API.AppVO) => {
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
        if (apps.value.length === 1 && page.current > 1) {
          page.current -= 1
        }
        await loadApps()
      } else {
        message.error(res.data.message || '删除失败')
        return Promise.reject()
      }
    },
  })
}

onMounted(async () => {
  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push('/user/login')
    return
  }
  await loadApps()
})
</script>

<style scoped>
#myAppsPage {
  --ease: cubic-bezier(0.22, 1, 0.36, 1);
  position: relative;
  min-height: calc(100vh - 120px);
  overflow-x: hidden;
  padding: 32px 20px 64px;
  font-family: 'Nunito Sans', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: #0f172a;
  background:
    radial-gradient(800px 420px at 12% 10%, rgba(125, 211, 192, 0.35), transparent 60%),
    radial-gradient(700px 380px at 90% 16%, rgba(147, 197, 253, 0.32), transparent 55%),
    linear-gradient(180deg, #ffffff 0%, #f4faf8 50%, #e8f4ff 100%);
}

.ambient {
  position: fixed;
  border-radius: 50%;
  filter: blur(40px);
  pointer-events: none;
  opacity: 0.4;
  z-index: 0;
}

.ambient-a {
  width: 240px;
  height: 240px;
  top: 18%;
  left: 8%;
  background: rgba(125, 211, 192, 0.5);
}

.ambient-b {
  width: 260px;
  height: 260px;
  top: 28%;
  right: 10%;
  background: rgba(147, 197, 253, 0.45);
}

.page-shell {
  position: relative;
  z-index: 1;
  max-width: 1080px;
  margin: 0 auto;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
}

.eyebrow {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: #64748b;
  font-family: 'Varela Round', 'Nunito Sans', sans-serif;
}

.title {
  margin: 0 0 8px;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.03em;
  font-family: 'Varela Round', 'Nunito Sans', sans-serif;
}

.desc {
  margin: 0;
  color: #64748b;
  font-size: 15px;
}

.create-link {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  height: 40px;
  padding: 0 18px;
  border-radius: 999px;
  background: linear-gradient(135deg, #5eead4, #7dd3fc);
  color: #0f172a;
  text-decoration: none;
  font-weight: 700;
  font-size: 14px;
  box-shadow: 0 10px 24px rgba(14, 165, 233, 0.2);
}

.list-card {
  padding: 24px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
}

.list-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
}

.list-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  font-family: 'Varela Round', 'Nunito Sans', sans-serif;
}

.list-head span {
  font-size: 13px;
  color: #94a3b8;
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}

.empty-hint {
  text-align: center;
  color: #94a3b8;
  padding: 48px 12px;
  font-size: 14px;
}

.empty-hint a {
  display: inline-block;
  margin-left: 6px;
  color: #0f172a;
  font-weight: 600;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

@media (max-width: 900px) {
  .app-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .page-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .app-grid {
    grid-template-columns: 1fr;
  }
}
</style>
