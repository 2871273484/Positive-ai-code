import { useLoginUserStore } from '@/stores/loginUser'
import { message } from 'ant-design-vue'
import router from '@/router'

// 是否为首次获取登录用户
let firstFetchLoginUser = true

/**
 * 全局权限校验
 */
router.beforeEach(async (to, from, next) => {
  const loginUserStore = useLoginUserStore()
  let loginUser = loginUserStore.loginUser
  // 确保页面刷新，首次加载时，能够等后端返回用户信息后再校验权限
  if (firstFetchLoginUser) {
    await loginUserStore.fetchLoginUser()
    loginUser = loginUserStore.loginUser
    firstFetchLoginUser = false
  }
  // 用户管理 / 应用管理 / 案例分类等后台页：仅管理员
  if (to.path.startsWith('/admin')) {
    if (!loginUser?.id || loginUser.userRole !== 'admin') {
      message.error('仅管理员可访问')
      next(loginUser?.id ? '/' : `/user/login?redirect=${to.fullPath}`)
      return
    }
  }
  if ((to.path === '/user/profile' || to.path === '/my/apps') && !loginUser?.id) {
    message.warning('请先登录')
    next(`/user/login?redirect=${to.fullPath}`)
    return
  }
  next()
})
