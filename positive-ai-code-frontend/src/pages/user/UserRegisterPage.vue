<template>
  <div id="userRegisterPage" class="auth-page">
    <div class="ambient ambient-a" />
    <div class="ambient ambient-b" />
    <div class="ambient ambient-c" />
    <div class="aurora" />
    <div class="noise" />

    <div class="auth-shell">
      <aside class="auth-visual">
        <RouterLink to="/" class="brand">
          <img
            class="brand-logo"
            src="https://lf-flow-web-cdn.doubao.com/obj/flow-doubao/samantha/logo-icon-white-bg.png"
            alt="Positive"
          />
          <span>Positive</span>
        </RouterLink>

        <div class="visual-main">
          <AuthCharacters
            :is-typing="isTypingAccount"
            :show-password="showPassword"
            :password-length="Math.max(
              formState.userPassword?.length || 0,
              formState.checkPassword?.length || 0,
            )"
            :is-password-guard-mode="isTypingPassword"
          />
          <p class="visual-caption">一句话呈所想</p>
        </div>

        <div class="visual-footer">
          <a href="javascript:void(0)">隐私政策</a>
          <a href="javascript:void(0)">服务条款</a>
        </div>
      </aside>

      <section class="auth-form-panel">
        <div class="form-inner">
          <h1 class="title">创建账号</h1>
          <p class="desc">填写信息，马上开始创作</p>

          <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
            <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
              <label class="field-label">账号</label>
              <a-input
                v-model:value="formState.userAccount"
                class="auth-input"
                placeholder="请输入账号"
                @focus="onAccountFocus"
                @blur="isTypingAccount = false"
              />
            </a-form-item>

            <a-form-item
              name="userPassword"
              :rules="[
                { required: true, message: '请输入密码' },
                { min: 8, message: '密码不能小于 8 位' },
              ]"
            >
              <label class="field-label">密码</label>
              <a-input-password
                v-model:value="formState.userPassword"
                v-model:visible="passwordVisible"
                class="auth-input"
                placeholder="请输入密码"
                @focus="onPasswordFocus"
                @blur="isTypingPassword = false"
              />
            </a-form-item>

            <a-form-item
              name="checkPassword"
              :rules="[
                { required: true, message: '请确认密码' },
                { min: 8, message: '密码不能小于 8 位' },
                { validator: validateCheckPassword },
              ]"
            >
              <label class="field-label">确认密码</label>
              <a-input-password
                v-model:value="formState.checkPassword"
                v-model:visible="checkPasswordVisible"
                class="auth-input"
                placeholder="请确认密码"
                @focus="onPasswordFocus"
                @blur="isTypingPassword = false"
              />
            </a-form-item>

            <div class="tips">
              已有账号？
              <RouterLink to="/user/login">去登录</RouterLink>
            </div>

            <a-form-item class="submit-item">
              <InteractiveHoverButton label="注册" hover-label="马上加入" />
            </a-form-item>
          </a-form>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { userRegister } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import AuthCharacters from '@/components/auth/AuthCharacters.vue'
import InteractiveHoverButton from '@/components/auth/InteractiveHoverButton.vue'

const router = useRouter()

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

const isTypingAccount = ref(false)
const isTypingPassword = ref(false)
const passwordVisible = ref(false)
const checkPasswordVisible = ref(false)

const showPassword = computed(() => passwordVisible.value || checkPasswordVisible.value)

const onAccountFocus = () => {
  isTypingAccount.value = true
  isTypingPassword.value = false
}

const onPasswordFocus = () => {
  isTypingPassword.value = true
  isTypingAccount.value = false
}

/**
 * 验证确认密码
 * @param rule
 * @param value
 * @param callback
 */
const validateCheckPassword = (rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value && value !== formState.userPassword) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: API.UserRegisterRequest) => {
  const res = await userRegister(values)
  // 注册成功，跳转到登录页面
  if (res.data.code === 0) {
    message.success('注册成功')
    router.push({
      path: '/user/login',
      replace: true,
    })
  } else {
    message.error('注册失败，' + res.data.message)
  }
}
</script>

<style scoped>
.auth-page {
  --color-fg: #0f172a;
  --color-muted: #64748b;
  --ease: cubic-bezier(0.22, 1, 0.36, 1);

  position: relative;
  min-height: calc(100vh - 120px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px 40px;
  overflow: hidden;
  font-family: 'Nunito Sans', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: var(--color-fg);
  background: transparent;
}

.ambient {
  position: absolute;
  border-radius: 50%;
  filter: blur(48px);
  pointer-events: none;
  z-index: 0;
  opacity: 0.55;
}

.ambient-a {
  width: 360px;
  height: 360px;
  top: -4%;
  left: -2%;
  background: rgba(110, 231, 183, 0.55);
}

.ambient-b {
  width: 400px;
  height: 400px;
  top: 8%;
  right: -6%;
  background: rgba(125, 211, 252, 0.5);
}

.ambient-c {
  width: 320px;
  height: 320px;
  bottom: 4%;
  left: 30%;
  background: rgba(196, 181, 253, 0.28);
}

.aurora {
  position: absolute;
  inset: -20% -10%;
  z-index: 0;
  pointer-events: none;
  opacity: 0.55;
  background:
    linear-gradient(
      118deg,
      transparent 0%,
      transparent 28%,
      rgba(255, 255, 255, 0.55) 36%,
      rgba(186, 230, 253, 0.28) 42%,
      transparent 50%,
      transparent 58%,
      rgba(255, 255, 255, 0.4) 64%,
      rgba(167, 243, 208, 0.22) 70%,
      transparent 78%,
      transparent 100%
    );
  mix-blend-mode: soft-light;
  transform: rotate(-6deg);
}

.noise {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 1;
  opacity: 0.055;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
}

.auth-shell {
  position: relative;
  z-index: 2;
  display: grid;
  grid-template-columns: minmax(280px, 0.95fr) minmax(320px, 1.05fr);
  width: min(920px, 100%);
  min-height: 600px;
  border-radius: 28px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.12);
}

.auth-visual {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 28px 24px;
  background: #4a5160;
  color: #fff;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #fff;
  text-decoration: none;
  font-weight: 700;
  font-size: 16px;
}

.brand-logo {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  background: #fff;
}

.visual-main {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 12px 0 8px;
  flex: 1;
  justify-content: flex-end;
}

.visual-caption {
  margin: 0;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.72);
  text-align: center;
}

.visual-footer {
  display: flex;
  gap: 16px;
  font-size: 12px;
}

.visual-footer a {
  color: rgba(255, 255, 255, 0.45);
  text-decoration: none;
}

.visual-footer a:hover {
  color: rgba(255, 255, 255, 0.75);
}

.auth-form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 36px 36px;
  background: #fff;
}

.form-inner {
  width: 100%;
  max-width: 360px;
}

.title {
  margin: 0 0 8px;
  font-size: 32px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #0f172a;
}

.desc {
  margin: 0 0 24px;
  color: var(--color-muted);
  font-size: 15px;
}

.field-label {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.auth-input {
  height: 44px;
  border-radius: 12px !important;
}

.auth-form-panel :deep(.ant-input),
.auth-form-panel :deep(.ant-input-affix-wrapper) {
  border-radius: 12px;
  border-color: #e2e8f0;
  padding-top: 8px;
  padding-bottom: 8px;
}

.auth-form-panel :deep(.ant-input-affix-wrapper-focused),
.auth-form-panel :deep(.ant-input:focus) {
  border-color: #38bdf8;
  box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.18);
}

.auth-form-panel :deep(.ant-form-item) {
  margin-bottom: 16px;
}

.tips {
  margin: -4px 0 18px;
  color: #94a3b8;
  font-size: 13px;
  text-align: right;
}

.tips a {
  color: #0ea5e9;
  font-weight: 600;
}

.submit-item {
  margin-bottom: 0 !important;
}

@media (max-width: 768px) {
  .auth-shell {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .auth-visual {
    min-height: 280px;
  }

  .auth-form-panel {
    padding: 28px 22px 32px;
  }

  .title {
    font-size: 26px;
  }
}
</style>
