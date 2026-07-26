<template>
  <div id="userProfilePage">
    <div class="ambient ambient-a" aria-hidden="true" />
    <div class="ambient ambient-b" aria-hidden="true" />

    <div class="profile-shell">
      <header class="profile-head">
        <p class="eyebrow">Account</p>
        <h1 class="title">个人中心</h1>
        <p class="desc">改昵称、换头像、更新密码</p>
      </header>

      <section class="profile-card">
        <div class="card-head">
          <h2>基本资料</h2>
          <span>账号 {{ loginUserStore.loginUser.userAccount || '—' }}</span>
        </div>

        <div class="avatar-row">
          <a-avatar :size="72" :src="profileForm.userAvatar" class="preview-avatar">
            {{ profileForm.userName?.charAt(0) || 'U' }}
          </a-avatar>
          <div class="avatar-hint">
            <p class="hint-title">头像预览</p>
            <p class="hint-text">填图片 URL，右侧即时预览</p>
          </div>
        </div>

        <a-form layout="vertical" :model="profileForm" @finish="saveProfile">
          <a-form-item
            label="昵称"
            name="userName"
            :rules="[
              { required: true, message: '请输入昵称' },
              { max: 20, message: '昵称不能超过 20 个字' },
            ]"
          >
            <a-input v-model:value="profileForm.userName" placeholder="怎么称呼你" allow-clear />
          </a-form-item>
          <a-form-item
            label="头像链接"
            name="userAvatar"
            :rules="[{ validator: validateAvatarUrl }]"
          >
            <a-input
              v-model:value="profileForm.userAvatar"
              placeholder="https://example.com/avatar.png"
              allow-clear
            />
          </a-form-item>
          <a-form-item>
            <button type="submit" class="primary-btn" :disabled="savingProfile">
              {{ savingProfile ? '保存中…' : '保存资料' }}
            </button>
          </a-form-item>
        </a-form>
      </section>

      <section class="profile-card">
        <div class="card-head">
          <h2>修改密码</h2>
          <span>至少 8 位</span>
        </div>
        <a-form layout="vertical" :model="passwordForm" @finish="savePassword">
          <a-form-item
            label="原密码"
            name="oldPassword"
            :rules="[
              { required: true, message: '请输入原密码' },
              { min: 8, message: '密码不能小于 8 位' },
            ]"
          >
            <a-input-password v-model:value="passwordForm.oldPassword" placeholder="当前密码" />
          </a-form-item>
          <a-form-item
            label="新密码"
            name="newPassword"
            :rules="[
              { required: true, message: '请输入新密码' },
              { min: 8, message: '密码不能小于 8 位' },
            ]"
          >
            <a-input-password v-model:value="passwordForm.newPassword" placeholder="新密码" />
          </a-form-item>
          <a-form-item
            label="确认新密码"
            name="checkPassword"
            :rules="[
              { required: true, message: '请再次输入新密码' },
              { min: 8, message: '密码不能小于 8 位' },
              { validator: validateCheckPassword },
            ]"
          >
            <a-input-password v-model:value="passwordForm.checkPassword" placeholder="再输一次" />
          </a-form-item>
          <a-form-item>
            <button type="submit" class="primary-btn ghost" :disabled="savingPassword">
              {{ savingPassword ? '提交中…' : '更新密码' }}
            </button>
          </a-form-item>
        </a-form>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { updateMyPassword, updateMyProfile } from '@/api/userController'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const profileForm = reactive({
  userName: '',
  userAvatar: '',
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  checkPassword: '',
})

const savingProfile = ref(false)
const savingPassword = ref(false)

const syncFromStore = () => {
  profileForm.userName = loginUserStore.loginUser.userName || ''
  profileForm.userAvatar = loginUserStore.loginUser.userAvatar || ''
}

onMounted(async () => {
  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push('/user/login')
    return
  }
  await loginUserStore.fetchLoginUser()
  syncFromStore()
})

const validateAvatarUrl = async (_rule: unknown, value: string) => {
  if (!value || !value.trim()) {
    return Promise.resolve()
  }
  const url = value.trim()
  if (!/^https?:\/\//i.test(url)) {
    return Promise.reject('头像须为 http/https 链接')
  }
  return Promise.resolve()
}

const validateCheckPassword = async (_rule: unknown, value: string) => {
  if (value && value !== passwordForm.newPassword) {
    return Promise.reject('两次输入的新密码不一致')
  }
  return Promise.resolve()
}

const saveProfile = async () => {
  savingProfile.value = true
  try {
    const res = await updateMyProfile({
      userName: profileForm.userName.trim(),
      userAvatar: profileForm.userAvatar.trim(),
    })
    if (res.data.code === 0 && res.data.data) {
      loginUserStore.setLoginUser(res.data.data)
      syncFromStore()
      message.success('资料已更新')
    } else {
      message.error(res.data.message || '更新失败')
    }
  } catch (error) {
    console.error(error)
    message.error('更新失败，请重试')
  } finally {
    savingProfile.value = false
  }
}

const savePassword = async () => {
  savingPassword.value = true
  try {
    const res = await updateMyPassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
      checkPassword: passwordForm.checkPassword,
    })
    if (res.data.code === 0) {
      message.success('密码已更新')
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
      passwordForm.checkPassword = ''
    } else {
      message.error(res.data.message || '修改失败')
    }
  } catch (error) {
    console.error(error)
    message.error('修改失败，请重试')
  } finally {
    savingPassword.value = false
  }
}
</script>

<style scoped>
#userProfilePage {
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

.profile-shell {
  position: relative;
  z-index: 1;
  max-width: 560px;
  margin: 0 auto;
}

.profile-head {
  text-align: left;
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

.profile-card {
  margin-bottom: 18px;
  padding: 22px 22px 8px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
}

.card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.card-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  font-family: 'Varela Round', 'Nunito Sans', sans-serif;
}

.card-head span {
  font-size: 13px;
  color: #94a3b8;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 8px;
}

.preview-avatar {
  background: linear-gradient(135deg, #7dd3c0, #93c5fd, #f9a8d4);
  flex-shrink: 0;
}

.hint-title {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.hint-text {
  margin: 0;
  font-size: 13px;
  color: #94a3b8;
}

.primary-btn {
  width: 100%;
  height: 44px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #5eead4, #7dd3fc);
  color: #0f172a;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 10px 24px rgba(14, 165, 233, 0.22);
  transition: transform 0.25s var(--ease), opacity 0.25s var(--ease);
}

.primary-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}

.primary-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.primary-btn.ghost {
  background: #0f172a;
  color: #fff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.18);
}

:deep(.ant-form-item-label > label) {
  color: #475569;
  font-weight: 600;
}

:deep(.ant-input),
:deep(.ant-input-password) {
  border-radius: 14px !important;
}
</style>
