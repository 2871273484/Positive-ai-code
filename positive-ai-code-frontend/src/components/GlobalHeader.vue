<template>
  <a-layout-header class="header">
    <div class="header-inner">
      <RouterLink to="/" class="brand">
        <img
          class="logo"
          src="https://lf-flow-web-cdn.doubao.com/obj/flow-doubao/samantha/logo-icon-white-bg.png"
          alt="Positive"
        />
        <span class="site-title">Positive</span>
      </RouterLink>

      <nav class="nav-links">
        <a
          v-for="item in menuItems"
          :key="String(item?.key)"
          href="javascript:void(0)"
          class="nav-link"
          :class="{ active: selectedKeys.includes(String(item?.key)) }"
          @click="onNavClick(item)"
        >
          {{ item?.title }}
        </a>
      </nav>

      <div class="user-area">
        <template v-if="isLoggedIn">
          <a-dropdown>
            <button type="button" class="user-chip">
              <a-avatar :size="36" :src="loginUser.userAvatar" class="user-avatar">
                {{ loginUser.userName?.charAt(0) || 'U' }}
              </a-avatar>
              <span class="user-name">{{ loginUser.userName ?? '无名' }}</span>
            </button>
            <template #overlay>
              <a-menu>
                <a-menu-item @click="goProfile">
                  <UserOutlined />
                  个人中心
                </a-menu-item>
                <a-menu-item @click="doLogout">
                  <LogoutOutlined />
                  退出登录
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>
        <RouterLink v-else to="/user/login" class="login-btn">登录</RouterLink>
      </div>
    </div>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { userLogout } from '@/api/userController.ts'
import { LogoutOutlined, UserOutlined } from '@ant-design/icons-vue'

const loginUserStore = useLoginUserStore()
const { loginUser } = storeToRefs(loginUserStore)
const router = useRouter()
const selectedKeys = ref<string[]>(['/'])

router.afterEach((to) => {
  selectedKeys.value = [to.path]
})

const isAdmin = computed(() => loginUser.value?.userRole === 'admin')
const isLoggedIn = computed(() => !!loginUser.value?.id)

const menuItems = computed(() => {
  const items = [
    { key: '/', label: '主页', title: '主页' },
    ...(isLoggedIn.value ? [{ key: '/my/apps', label: '我的应用', title: '我的应用' }] : []),
    // 用户管理 / 应用管理 / 案例分类：仅管理员可见
    ...(isAdmin.value
      ? [
          { key: '/admin/userManage', label: '用户管理', title: '用户管理' },
          { key: '/admin/appManage', label: '应用管理', title: '应用管理' },
          { key: '/admin/categoryManage', label: '案例分类', title: '案例分类' },
        ]
      : []),
  ]
  return items
})

const goProfile = () => {
  router.push('/user/profile')
}

const onNavClick = (item: { key?: string | number } | null | undefined) => {
  const key = String(item?.key ?? '')
  selectedKeys.value = [key]
  if (key.startsWith('/')) {
    router.push(key)
  }
}

const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
.header {
  background: transparent !important;
  padding: 0;
  height: auto;
  line-height: normal;
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-inner {
  max-width: 1120px;
  margin: 0 auto;
  padding: 18px 24px;
  display: flex;
  align-items: center;
  gap: 28px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  flex-shrink: 0;
}

.logo {
  height: 36px;
  width: 36px;
  border-radius: 10px;
  object-fit: cover;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.08);
}

.site-title {
  margin: 0;
  font-family: 'Varela Round', 'Nunito Sans', 'PingFang SC', sans-serif;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: -0.02em;
}

.nav-links {
  flex: 1;
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}

.nav-link {
  padding: 8px 14px;
  border-radius: 999px;
  color: #64748b;
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition:
    color 0.25s cubic-bezier(0.22, 1, 0.36, 1),
    background 0.25s cubic-bezier(0.22, 1, 0.36, 1);
}

.nav-link:hover,
.nav-link.active {
  color: #0f172a;
  background: rgba(255, 255, 255, 0.55);
}

.user-area {
  flex-shrink: 0;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: none;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(12px);
  border-radius: 999px;
  padding: 4px 12px 4px 4px;
  cursor: pointer;
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.06);
}

.user-avatar {
  background: linear-gradient(135deg, #7dd3c0, #93c5fd, #f9a8d4);
}

.user-name {
  font-size: 14px;
  color: #334155;
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.login-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 36px;
  padding: 0 18px;
  border-radius: 999px;
  background: #0f172a;
  color: #fff;
  text-decoration: none;
  font-size: 14px;
  font-weight: 600;
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.16);
}

@media (max-width: 768px) {
  .header-inner {
    flex-wrap: wrap;
    gap: 12px;
  }

  .nav-links {
    order: 3;
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
