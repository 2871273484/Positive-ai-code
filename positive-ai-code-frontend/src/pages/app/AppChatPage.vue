<template>
  <div id="appChatPage">
    <div class="ambient ambient-a" aria-hidden="true" />
    <div class="ambient ambient-b" aria-hidden="true" />
    <div class="noise" aria-hidden="true" />

    <div class="page-shell">
      <!-- 顶部栏 -->
      <div class="header-bar">
        <div class="header-left">
          <h1 class="app-name">{{ appInfo?.appName || '网站生成器' }}</h1>
          <span v-if="appInfo?.codeGenType" class="code-gen-type-tag">
            {{ formatCodeGenType(appInfo.codeGenType) }}
          </span>
        </div>
        <div class="header-right">
          <button type="button" class="btn-ghost" @click="showAppDetail">
            <InfoCircleOutlined />
            应用详情
          </button>
          <button
            type="button"
            class="btn-ghost"
            :disabled="downloading || !isOwner"
            @click="downloadCode"
          >
            <DownloadOutlined />
            {{ downloading ? '下载中…' : '下载代码' }}
          </button>
          <button type="button" class="btn-primary" :disabled="deploying" @click="deployApp">
            <CloudUploadOutlined />
            {{ deploying ? '部署中…' : '部署' }}
          </button>
        </div>
      </div>

      <!-- 主要内容区域 -->
      <div class="main-content">
        <!-- 左侧对话区域 -->
        <div class="chat-section glass-panel">
          <div class="messages-container" ref="messagesContainer">
            <div v-if="hasMoreHistory" class="load-more-container">
              <button type="button" class="link-btn" :disabled="loadingHistory" @click="loadMoreHistory">
                {{ loadingHistory ? '加载中…' : '加载更多历史消息' }}
              </button>
            </div>
            <div v-for="(message, index) in messages" :key="index" class="message-item">
              <div v-if="message.type === 'user'" class="user-message">
                <div class="message-content">{{ message.content }}</div>
                <div class="message-avatar">
                  <a-avatar :src="loginUserStore.loginUser.userAvatar" />
                </div>
              </div>
              <div v-else class="ai-message">
                <div class="message-avatar">
                  <a-avatar :src="aiAvatar" />
                </div>
                <div class="message-content">
                  <!-- 原生 HTML/多文件：流式时用纯文本，避免 Markdown+HTML 注入卡死页面 -->
                  <pre
                    v-if="message.content && (message.plain || isNativeCodeGen)"
                    class="stream-code"
                    :data-streaming="isGenerating && index === messages.length - 1 ? '1' : undefined"
                  >{{ message.content }}</pre>
                  <MarkdownRenderer
                    v-else-if="message.content"
                    :content="message.content"
                  />
                  <div v-if="message.loading" class="loading-indicator">
                    <a-spin size="small" />
                    <span>AI 正在思考...</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <a-alert
            v-if="selectedElementInfo"
            class="selected-element-alert"
            type="info"
            closable
            @close="clearSelectedElement"
          >
            <template #message>
              <div class="selected-element-info">
                <div class="element-header">
                  <span class="element-tag">
                    选中元素：{{ selectedElementInfo.tagName.toLowerCase() }}
                  </span>
                  <span v-if="selectedElementInfo.id" class="element-id">
                    #{{ selectedElementInfo.id }}
                  </span>
                  <span v-if="selectedElementInfo.className" class="element-class">
                    .{{ selectedElementInfo.className.split(' ').join('.') }}
                  </span>
                </div>
                <div class="element-details">
                  <div v-if="selectedElementInfo.textContent" class="element-item">
                    内容: {{ selectedElementInfo.textContent.substring(0, 50) }}
                    {{ selectedElementInfo.textContent.length > 50 ? '...' : '' }}
                  </div>
                  <div v-if="selectedElementInfo.pagePath" class="element-item">
                    页面路径: {{ selectedElementInfo.pagePath }}
                  </div>
                  <div class="element-item">
                    选择器:
                    <code class="element-selector-code">{{ selectedElementInfo.selector }}</code>
                  </div>
                </div>
              </div>
            </template>
          </a-alert>

          <!-- 输入框：主页同款玻璃壳 + 小吉祥物 -->
          <div class="input-container">
            <div class="prompt-shell">
              <div class="mascot" aria-hidden="true">
                <span class="mascot-sparkle" />
                <svg class="mascot-body" viewBox="0 0 120 120" xmlns="http://www.w3.org/2000/svg">
                  <defs>
                    <linearGradient id="chatMascotGrad" x1="20%" y1="10%" x2="80%" y2="90%">
                      <stop offset="0%" stop-color="#7dd3fc" />
                      <stop offset="55%" stop-color="#38bdf8" />
                      <stop offset="100%" stop-color="#0284c7" />
                    </linearGradient>
                  </defs>
                  <ellipse cx="60" cy="68" rx="38" ry="34" fill="url(#chatMascotGrad)" />
                  <ellipse cx="60" cy="42" rx="30" ry="28" fill="url(#chatMascotGrad)" />
                  <circle cx="48" cy="40" r="4.2" fill="#0f172a" class="mascot-eye" />
                  <circle cx="72" cy="40" r="4.2" fill="#0f172a" class="mascot-eye" />
                  <path
                    d="M50 52 Q60 60 70 52"
                    fill="none"
                    stroke="#0f172a"
                    stroke-width="2.5"
                    stroke-linecap="round"
                  />
                  <ellipse cx="44" cy="86" rx="9" ry="6" fill="#0369a1" opacity="0.35" />
                  <ellipse cx="76" cy="86" rx="9" ry="6" fill="#0369a1" opacity="0.35" />
                </svg>
              </div>
              <a-tooltip v-if="!isOwner" title="无法在别人的作品下对话哦~" placement="top">
                <a-textarea
                  v-model:value="userInput"
                  class="prompt-input"
                  :placeholder="getInputPlaceholder()"
                  :rows="3"
                  :maxlength="1000"
                  :disabled="isGenerating || !isOwner"
                  @keydown.enter.prevent="sendMessage"
                />
              </a-tooltip>
              <a-textarea
                v-else
                v-model:value="userInput"
                class="prompt-input"
                :placeholder="getInputPlaceholder()"
                :rows="3"
                :maxlength="1000"
                :disabled="isGenerating"
                @keydown.enter.prevent="sendMessage"
              />
              <div class="prompt-toolbar">
                <span class="hint-chip">Enter 发送</span>
                <button
                  type="button"
                  class="submit-btn"
                  :disabled="isGenerating || !isOwner"
                  @click="sendMessage"
                >
                  <span v-if="isGenerating" class="submit-loading" />
                  <SendOutlined v-else />
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 右侧网页展示区域 -->
        <div class="preview-section glass-panel">
          <div class="preview-header">
            <h3>生成后的网页展示</h3>
            <div class="preview-actions">
              <button
                v-if="isOwner && previewUrl"
                type="button"
                class="link-btn"
                :class="{ 'edit-mode-active': isEditMode }"
                @click="toggleEditMode"
              >
                <EditOutlined />
                {{ isEditMode ? '退出编辑' : '编辑模式' }}
              </button>
              <button v-if="previewUrl" type="button" class="link-btn" @click="openInNewTab">
                <ExportOutlined />
                新窗口打开
              </button>
            </div>
          </div>
          <div class="preview-content">
            <div v-if="!previewUrl && !isGenerating" class="preview-placeholder">
              <div class="placeholder-orb" aria-hidden="true" />
              <p>网站生成完成后会出现在这里</p>
            </div>
            <template v-else>
              <iframe
                v-if="previewUrl"
                :src="previewUrl"
                class="preview-iframe"
                :class="{ 'preview-iframe--dimmed': isGenerating }"
                frameborder="0"
                @load="onIframeLoad"
              ></iframe>
              <div
                v-if="isGenerating"
                class="preview-loading"
                :class="{ 'preview-loading--overlay': !!previewUrl }"
              >
                <a-spin size="large" />
                <p>{{ generatingStatus || '正在生成网站…' }}</p>
                <span class="preview-loading-tip">若约 40 秒无新代码会自动结束并展示已生成内容</span>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>

    <AppDetailModal
      v-model:open="appDetailVisible"
      :app="appInfo"
      :show-actions="isOwner || isAdmin"
      @edit="editApp"
      @delete="deleteApp"
    />

    <DeploySuccessModal
      v-model:open="deployModalVisible"
      :deploy-url="deployUrl"
      @open-site="openDeployedSite"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  getAppVoById,
  deployApp as deployAppApi,
  deleteApp as deleteAppApi,
  generateAppCover,
} from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import { CodeGenTypeEnum, formatCodeGenType } from '@/utils/codeGenTypes'
import request from '@/request'

import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import DeploySuccessModal from '@/components/DeploySuccessModal.vue'
import aiAvatar from '@/assets/aiAvatar.png'
import { API_BASE_URL, getStaticPreviewUrl } from '@/config/env'
import { VisualEditor, type ElementInfo } from '@/utils/visualEditor'

import {
  CloudUploadOutlined,
  SendOutlined,
  ExportOutlined,
  InfoCircleOutlined,
  DownloadOutlined,
  EditOutlined,
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

// 应用信息
const appInfo = ref<API.AppVO>()
const appId = ref<any>()

// 对话相关
interface Message {
  type: 'user' | 'ai'
  content: string
  loading?: boolean
  createTime?: string
  /** 原生 HTML/多文件消息：用 pre 展示，避免重渲染卡顿 */
  plain?: boolean
}


const messages = ref<Message[]>([])
const userInput = ref('')
const isGenerating = ref(false)
const generatingStatus = ref('')
const messagesContainer = ref<HTMLElement>()
const PROGRESS_PREFIX = '[[PROGRESS]]'

// 对话历史相关
const loadingHistory = ref(false)
const hasMoreHistory = ref(false)
const lastCreateTime = ref<string>()
const historyLoaded = ref(false)

// 预览相关
const previewUrl = ref('')
const previewReady = ref(false)

// 部署相关
const deploying = ref(false)
const deployModalVisible = ref(false)
const deployUrl = ref('')

// 下载相关
const downloading = ref(false)

// 可视化编辑相关
const isEditMode = ref(false)
const selectedElementInfo = ref<ElementInfo | null>(null)
const visualEditor = new VisualEditor({
  onElementSelected: (elementInfo: ElementInfo) => {
    selectedElementInfo.value = elementInfo
  },
})

// 权限相关
const isOwner = computed(() => {
  return appInfo.value?.userId === loginUserStore.loginUser.id
})

const isAdmin = computed(() => {
  return loginUserStore.loginUser.userRole === 'admin'
})

/** 原生 HTML / 多文件：流式内容大，不能走 Markdown 实时渲染 */
const isNativeCodeGen = computed(() => {
  const t = appInfo.value?.codeGenType
  return t === CodeGenTypeEnum.HTML || t === CodeGenTypeEnum.MULTI_FILE
})

// 应用详情相关
const appDetailVisible = ref(false)

// 显示应用详情
const showAppDetail = () => {
  appDetailVisible.value = true
}

// 加载对话历史
const loadChatHistory = async (isLoadMore = false) => {
  if (!appId.value || loadingHistory.value) return
  loadingHistory.value = true
  try {
    const params: API.listAppChatHistoryParams = {
      appId: appId.value,
      pageSize: 10,
    }
    // 如果是加载更多，传递最后一条消息的创建时间作为游标
    if (isLoadMore && lastCreateTime.value) {
      params.lastCreateTime = lastCreateTime.value
    }
    const res = await listAppChatHistory(params)
    if (res.data.code === 0 && res.data.data) {
      const chatHistories = res.data.data.records || []
      if (chatHistories.length > 0) {
        // 将对话历史转换为消息格式，并按时间正序排列（老消息在前）
        const historyMessages: Message[] = chatHistories
          .map((chat) => ({
            type: (chat.messageType === 'user' ? 'user' : 'ai') as 'user' | 'ai',
            content: chat.message || '',
            createTime: chat.createTime,
            plain: chat.messageType !== 'user' && isNativeCodeGen.value,
          }))
          .reverse() // 反转数组，让老消息在前
        if (isLoadMore) {
          // 加载更多时，将历史消息添加到开头
          messages.value.unshift(...historyMessages)
        } else {
          // 初始加载，直接设置消息列表
          messages.value = historyMessages
        }
        // 更新游标
        lastCreateTime.value = chatHistories[chatHistories.length - 1]?.createTime
        // 检查是否还有更多历史
        hasMoreHistory.value = chatHistories.length === 10
      } else {
        hasMoreHistory.value = false
      }
      historyLoaded.value = true
    }
  } catch (error) {
    console.error('加载对话历史失败：', error)
    message.error('加载对话历史失败')
  } finally {
    loadingHistory.value = false
  }
}

// 加载更多历史消息
const loadMoreHistory = async () => {
  await loadChatHistory(true)
}

// 获取应用信息
const fetchAppInfo = async () => {
  const id = route.params.id as string
  if (!id) {
    message.error('应用ID不存在')
    router.push('/')
    return
  }

  appId.value = id

  try {
    const res = await getAppVoById({ id: id as unknown as number })
    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data

      // 先加载对话历史
      await loadChatHistory()
      // 如果有至少2条对话记录，展示对应的网站
      if (messages.value.length >= 2) {
        updatePreview()
      }
      // 检查是否需要自动发送初始提示词
      // 只有在是自己的应用且没有对话历史时才自动发送
      if (
        appInfo.value.initPrompt &&
        isOwner.value &&
        messages.value.length === 0 &&
        historyLoaded.value
      ) {
        await sendInitialMessage(appInfo.value.initPrompt)
      }
    } else {
      message.error('获取应用信息失败')
      router.push('/')
    }
  } catch (error) {
    console.error('获取应用信息失败：', error)
    message.error('获取应用信息失败')
    router.push('/')
  }
}

// 发送初始消息
const sendInitialMessage = async (prompt: string) => {
  // 添加用户消息
  messages.value.push({
    type: 'user',
    content: prompt,
  })

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
    plain: isNativeCodeGen.value,
  })

  await nextTick()
  scrollToBottom()

  // 开始生成
  isGenerating.value = true
  await generateCode(prompt, aiMessageIndex)
}

// 发送消息
const sendMessage = async () => {
  if (!userInput.value.trim() || isGenerating.value) {
    return
  }

  let message = userInput.value.trim()
  // 如果有选中的元素，将元素信息添加到提示词中
  if (selectedElementInfo.value) {
    let elementContext = `\n\n选中元素信息：`
    if (selectedElementInfo.value.pagePath) {
      elementContext += `\n- 页面路径: ${selectedElementInfo.value.pagePath}`
    }
    elementContext += `\n- 标签: ${selectedElementInfo.value.tagName.toLowerCase()}\n- 选择器: ${selectedElementInfo.value.selector}`
    if (selectedElementInfo.value.textContent) {
      elementContext += `\n- 当前内容: ${selectedElementInfo.value.textContent.substring(0, 100)}`
    }
    message += elementContext
  }
  userInput.value = ''
  // 添加用户消息（包含元素信息）
  messages.value.push({
    type: 'user',
    content: message,
  })

  // 发送消息后，清除选中元素并退出编辑模式
  if (selectedElementInfo.value) {
    clearSelectedElement()
    if (isEditMode.value) {
      toggleEditMode()
    }
  }

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
    plain: isNativeCodeGen.value,
  })

  await nextTick()
  scrollToBottom()

  // 开始生成
  isGenerating.value = true
  generatingStatus.value = '已提交请求，正在连接…'
  await generateCode(message, aiMessageIndex)
}

// 生成代码 - 使用 EventSource 处理流式响应
const generateCode = async (userMessage: string, aiMessageIndex: number) => {
  let eventSource: EventSource | null = null
  let streamCompleted = false
  const usePlainStream = isNativeCodeGen.value

  try {
    // 获取 axios 配置的 baseURL
    const baseURL = request.defaults.baseURL || API_BASE_URL

    // 构建URL参数
    const params = new URLSearchParams({
      appId: appId.value || '',
      message: userMessage,
    })

    const url = `${baseURL}/app/chat/gen/code?${params}`

    // 创建 EventSource 连接
    eventSource = new EventSource(url, {
      withCredentials: true,
    })

    let fullContent = ''
    let lastProgressAt = Date.now()
    let lastCodeAt = Date.now()
    let flushTimer: number | null = null

    const flushStreamContent = () => {
      flushTimer = null
      messages.value[aiMessageIndex].content = fullContent
      messages.value[aiMessageIndex].loading = false
      if (usePlainStream) {
        messages.value[aiMessageIndex].plain = true
      }
      // 必须滚代码块内部，否则只能看到开头，跟不上最新生成
      void scrollToLatestCode(true)
    }

    const scheduleFlush = () => {
      // 原生大文件：短节流，兼顾实时跟进与主线程流畅
      if (usePlainStream) {
        if (flushTimer == null) {
          flushTimer = window.setTimeout(flushStreamContent, 100)
        }
        return
      }
      flushStreamContent()
    }

    const finishStream = (opts?: { keepContent?: boolean; tip?: string }) => {
      if (streamCompleted) return
      streamCompleted = true
      if (flushTimer != null) {
        window.clearTimeout(flushTimer)
        flushTimer = null
      }
      window.clearInterval(stallTimer)
      if (opts?.tip) {
        messages.value[aiMessageIndex].content =
          (fullContent ? fullContent + '\n\n' : '') + opts.tip
      } else if (opts?.keepContent !== false) {
        messages.value[aiMessageIndex].content = fullContent
      }
      messages.value[aiMessageIndex].loading = false
      if (usePlainStream) {
        messages.value[aiMessageIndex].plain = true
      }
      isGenerating.value = false
      generatingStatus.value = ''
      eventSource?.close()
      void scrollToLatestCode(true)
    }

    const showPreviewAfterStream = (delayMs = 400) => {
      setTimeout(async () => {
        await fetchAppInfo()
        updatePreview()
        // 封面截图走 Selenium，延后且不阻塞预览
        setTimeout(() => {
          void refreshAppCover()
        }, 3000)
      }, delayMs)
    }

    // 进度心跳不能无限续命：无新代码约 40s 则结束，避免右侧一直转圈
    const CODE_IDLE_MS = 40000
    const TOTAL_IDLE_MS = 90000
    const stallTimer = window.setInterval(() => {
      if (streamCompleted) {
        window.clearInterval(stallTimer)
        return
      }
      const now = Date.now()
      const codeIdle = now - lastCodeAt > CODE_IDLE_MS && fullContent.length > 0
      const totalIdle = now - lastProgressAt > TOTAL_IDLE_MS
      if (codeIdle || totalIdle) {
        finishStream({
          tip: codeIdle
            ? '⏳ 代码流已较久无更新，已自动结束。可预览已生成内容，或继续说明要改哪里。'
            : '❌ 生成中断：长时间无响应。可点重试，或缩小需求后再生成',
        })
        if (codeIdle) {
          message.warning('已自动结束空闲生成，正在加载预览')
          showPreviewAfterStream(600)
        } else {
          message.error('生成中断，请重试')
        }
      }
    }, 2000)

    // 处理接收到的消息
    eventSource.onmessage = function (event) {
      if (streamCompleted) return

      try {
        const parsed = JSON.parse(event.data)
        const content = parsed.d

        if (content === undefined || content === null) {
          return
        }

        lastProgressAt = Date.now()

        // 进度：只更新右侧状态；已有代码时不要覆盖左侧正文
        if (typeof content === 'string' && content.startsWith(PROGRESS_PREFIX)) {
          const statusText = content.slice(PROGRESS_PREFIX.length)
          generatingStatus.value = statusText
          if (!fullContent) {
            messages.value[aiMessageIndex].loading = true
            messages.value[aiMessageIndex].content = statusText
          }
          scrollToBottom()
          return
        }

        fullContent += content
        lastCodeAt = Date.now()
        generatingStatus.value = '正在流式生成网站代码…'
        scheduleFlush()
      } catch (error) {
        console.error('解析消息失败:', error)
        finishStream({ tip: '❌ 解析流式消息失败，请重试' })
        message.error('生成失败，请重试')
      }
    }

    // 处理done事件
    eventSource.addEventListener('done', function () {
      if (streamCompleted) return
      finishStream()
      showPreviewAfterStream(400)
    })

    // 处理business-error事件（后端限流等错误）
    eventSource.addEventListener('business-error', function (event: MessageEvent) {
      if (streamCompleted) return

      try {
        const errorData = JSON.parse(event.data)
        console.error('SSE业务错误事件:', errorData)

        const errorMessage = errorData.message || '生成过程中出现错误'
        finishStream({ tip: `❌ ${errorMessage}` })
        message.error(errorMessage)
      } catch (parseError) {
        console.error('解析错误事件失败:', parseError, '原始数据:', event.data)
        finishStream({ tip: '❌ 服务器返回错误' })
        message.error('服务器返回错误')
      }
    })

    // 处理错误
    eventSource.onerror = function () {
      if (streamCompleted || !isGenerating.value) return
      if (eventSource?.readyState === EventSource.CONNECTING && fullContent) {
        finishStream()
        showPreviewAfterStream(1000)
      } else if (eventSource?.readyState === EventSource.CLOSED) {
        finishStream({ tip: '❌ 生成连接已断开，请重试' })
        message.error('生成连接已断开，请重试')
      } else if (!fullContent && Date.now() - lastProgressAt > 20000) {
        finishStream({ tip: '❌ SSE连接错误' })
        message.error('生成失败，请重试')
      }
    }
  } catch (error) {
    console.error('创建 EventSource 失败：', error)
    handleError(error, aiMessageIndex)
  }
}

// 错误处理函数
const handleError = (error: unknown, aiMessageIndex: number) => {
  console.error('生成代码失败：', error)
  messages.value[aiMessageIndex].content = '抱歉，生成过程中出现了错误，请重试。'
  messages.value[aiMessageIndex].loading = false
  message.error('生成失败，请重试')
  isGenerating.value = false
  generatingStatus.value = ''
}

// 更新预览
const updatePreview = () => {
  if (appId.value) {
    const codeGenType = appInfo.value?.codeGenType || CodeGenTypeEnum.HTML
    const newPreviewUrl = getStaticPreviewUrl(codeGenType, appId.value)
    previewUrl.value = newPreviewUrl
    previewReady.value = true
  }
}

// 生成 / 刷新应用封面（后端截取网站主页）
const refreshAppCover = async () => {
  if (!appId.value) return
  try {
    const res = await generateAppCover({ appId: appId.value })
    if (res.data.code === 0 && res.data.data) {
      if (appInfo.value) {
        appInfo.value.cover = res.data.data
      }
      console.log('应用封面已更新:', res.data.data)
    } else {
      console.warn('封面生成未成功:', res.data.message)
    }
  } catch (error) {
    console.warn('封面生成请求失败（不影响预览）:', error)
  }
}

// 滚动到底部
const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

/** 原生代码块跟滚到最新一行（流式生成时） */
const scrollToLatestCode = async (forceFollow = false) => {
  await nextTick()
  scrollToBottom()
  const root = messagesContainer.value
  if (!root) return
  const active =
    (root.querySelector('.stream-code[data-streaming="1"]') as HTMLElement | null) ||
    (root.querySelector('.ai-message:last-child .stream-code') as HTMLElement | null)
  if (!active) return
  // 生成中强制跟随；结束后若用户已上滚则不强拉
  if (forceFollow || isGenerating.value) {
    active.scrollTop = active.scrollHeight
  }
}

// 下载代码
const downloadCode = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }
  downloading.value = true
  try {
    const API_BASE_URL = request.defaults.baseURL || ''
    const url = `${API_BASE_URL}/app/download/${appId.value}`
    const response = await fetch(url, {
      method: 'GET',
      credentials: 'include',
    })
    if (!response.ok) {
      throw new Error(`下载失败: ${response.status}`)
    }
    // 获取文件名
    const contentDisposition = response.headers.get('Content-Disposition')
    const fileName = contentDisposition?.match(/filename="(.+)"/)?.[1] || `app-${appId.value}.zip`
    // 下载文件
    const blob = await response.blob()
    const downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = fileName
    link.click()
    // 清理
    URL.revokeObjectURL(downloadUrl)
    message.success('代码下载成功')
  } catch (error) {
    console.error('下载失败：', error)
    message.error('下载失败，请重试')
  } finally {
    downloading.value = false
  }
}

// 部署应用
const deployApp = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }

  deploying.value = true
  try {
    const res = await deployAppApi({
      appId: appId.value as unknown as number,
    })

    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = res.data.data
      deployModalVisible.value = true
      message.success('部署成功')
    } else {
      message.error('部署失败：' + res.data.message)
    }
  } catch (error) {
    console.error('部署失败：', error)
    message.error('部署失败，请重试')
  } finally {
    deploying.value = false
  }
}

// 在新窗口打开预览
const openInNewTab = () => {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank')
  }
}

// 打开部署的网站
const openDeployedSite = () => {
  if (deployUrl.value) {
    window.open(deployUrl.value, '_blank')
  }
}

// iframe加载完成
const onIframeLoad = () => {
  previewReady.value = true
  const iframe = document.querySelector('.preview-iframe') as HTMLIFrameElement
  if (iframe) {
    visualEditor.init(iframe)
    visualEditor.onIframeLoad()
  }
}

// 编辑应用
const editApp = () => {
  if (appInfo.value?.id) {
    router.push(`/app/edit/${appInfo.value.id}`)
  }
}

// 删除应用
const deleteApp = async () => {
  if (!appInfo.value?.id) return

  try {
    const res = await deleteAppApi({ id: appInfo.value.id })
    if (res.data.code === 0) {
      message.success('删除成功')
      appDetailVisible.value = false
      router.push('/')
    } else {
      message.error('删除失败：' + res.data.message)
    }
  } catch (error) {
    console.error('删除失败：', error)
    message.error('删除失败')
  }
}

// 可视化编辑相关函数
const toggleEditMode = () => {
  // 检查 iframe 是否已经加载
  const iframe = document.querySelector('.preview-iframe') as HTMLIFrameElement
  if (!iframe) {
    message.warning('请等待页面加载完成')
    return
  }
  // 确保 visualEditor 已初始化
  if (!previewReady.value) {
    message.warning('请等待页面加载完成')
    return
  }
  const newEditMode = visualEditor.toggleEditMode()
  isEditMode.value = newEditMode
}

const clearSelectedElement = () => {
  selectedElementInfo.value = null
  visualEditor.clearSelection()
}

const getInputPlaceholder = () => {
  if (selectedElementInfo.value) {
    return `正在编辑 ${selectedElementInfo.value.tagName.toLowerCase()} 元素，描述您想要的修改...`
  }
  return '请描述你想生成的网站，越详细效果越好哦'
}

// 页面加载时获取应用信息
onMounted(() => {
  fetchAppInfo()

  // 监听 iframe 消息
  window.addEventListener('message', (event) => {
    visualEditor.handleIframeMessage(event)
  })
})

// 清理资源
onUnmounted(() => {
  // EventSource 会在组件卸载时自动清理
})
</script>

<style scoped>
#appChatPage {
  --color-fg: #0f172a;
  --color-muted: #64748b;
  --glass: rgba(255, 255, 255, 0.72);
  --radius-xl: 28px;
  --radius-pill: 999px;
  --shadow-soft: 0 18px 50px rgba(15, 23, 42, 0.08);
  --ease: cubic-bezier(0.22, 1, 0.36, 1);

  position: relative;
  /* 扣除顶栏 + 底栏，避免再出现整页滚动条 */
  height: calc(100dvh - 140px);
  min-height: 560px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
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
  top: 14%;
  left: 6%;
  background: rgba(125, 211, 192, 0.55);
  animation: floatA 12s var(--ease) infinite alternate;
}

.ambient-b {
  width: 320px;
  height: 320px;
  top: 22%;
  right: 4%;
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

.page-shell {
  position: relative;
  z-index: 2;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 16px 20px 20px;
  gap: 12px;
}

.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 12px 18px;
  border-radius: 20px;
  background: var(--glass);
  border: 1px solid rgba(255, 255, 255, 0.85);
  box-shadow: var(--shadow-soft);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.code-gen-type-tag {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 12px;
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 700;
  color: #0f766e;
  background: rgba(167, 243, 208, 0.55);
}

.app-name {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--color-fg);
  font-family: 'Varela Round', 'Nunito Sans', sans-serif;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.header-right {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.btn-ghost,
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border-radius: var(--radius-pill);
  font-size: 13px;
  font-weight: 700;
  font-family: inherit;
  cursor: pointer;
  transition:
    transform 0.2s var(--ease),
    opacity 0.2s var(--ease),
    background 0.2s var(--ease);
}

.btn-ghost {
  border: 1px solid rgba(255, 255, 255, 0.95);
  background: rgba(255, 255, 255, 0.65);
  color: #475569;
}

.btn-ghost:hover:not(:disabled) {
  background: #fff;
  color: #0f172a;
  transform: translateY(-1px);
}

.btn-primary {
  border: none;
  color: #0f172a;
  background: linear-gradient(135deg, #5eead4, #7dd3fc);
  box-shadow: 0 10px 24px rgba(14, 165, 233, 0.22);
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-1px) scale(1.02);
}

.btn-ghost:disabled,
.btn-primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
}

.main-content {
  flex: 1;
  display: flex;
  gap: 16px;
  min-height: 0;
  overflow: hidden;
}

.glass-panel {
  background: var(--glass);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-soft);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  overflow: hidden;
}

.chat-section {
  flex: 2;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.messages-container {
  flex: 1;
  padding: 16px 18px;
  overflow-y: auto;
  scroll-behavior: smooth;
  min-height: 0;
}

.message-item {
  margin-bottom: 12px;
}

.user-message,
.ai-message {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.user-message {
  justify-content: flex-end;
}

.ai-message {
  justify-content: flex-start;
}

.message-content {
  max-width: 78%;
  padding: 12px 16px;
  border-radius: 16px;
  line-height: 1.55;
  word-wrap: break-word;
}

.user-message .message-content {
  background: linear-gradient(145deg, #34d399, #38bdf8);
  color: #fff;
  box-shadow: 0 8px 20px rgba(56, 189, 248, 0.28);
}

.ai-message .message-content {
  background: rgba(248, 250, 252, 0.92);
  border: 1px solid rgba(226, 232, 240, 0.9);
  color: var(--color-fg);
  padding: 8px 12px;
}

.stream-code {
  margin: 0;
  max-height: min(62vh, 720px);
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.45;
  color: #0f172a;
  background: transparent;
  scroll-behavior: auto;
}

.message-avatar {
  flex-shrink: 0;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-muted);
}

.load-more-container {
  text-align: center;
  padding: 4px 0 12px;
}

.link-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: #0f766e;
  font-size: 13px;
  font-weight: 700;
  font-family: inherit;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 8px;
  transition: background 0.2s var(--ease), color 0.2s var(--ease);
}

.link-btn:hover:not(:disabled) {
  background: rgba(167, 243, 208, 0.35);
}

.link-btn:disabled {
  opacity: 0.55;
  cursor: wait;
}

.input-container {
  padding: 12px 16px 16px;
  background: transparent;
}

.prompt-shell {
  position: relative;
  padding: 14px 14px 10px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.06);
  transition:
    transform 0.25s var(--ease),
    box-shadow 0.25s var(--ease);
}

.prompt-shell:focus-within {
  transform: translateY(-1px);
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.1);
}

.mascot {
  position: absolute;
  top: -36px;
  right: 18px;
  width: 64px;
  height: 64px;
  z-index: 3;
  pointer-events: none;
  animation: mascotFloat 3.2s var(--ease) infinite;
  transform-origin: 50% 85%;
}

.mascot-body {
  width: 100%;
  height: 100%;
  display: block;
  filter: drop-shadow(0 8px 14px rgba(14, 165, 233, 0.28));
}

.mascot-sparkle {
  position: absolute;
  top: 4px;
  right: 2px;
  width: 10px;
  height: 10px;
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
    transform: translateY(-8px) rotate(2deg);
  }
  70% {
    transform: translateY(-3px) rotate(-1deg);
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

.prompt-input {
  width: 100%;
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  resize: none;
  font-size: 15px;
  line-height: 1.55;
  color: #0f172a;
  padding: 4px 6px 4px !important;
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

.hint-chip {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  background: rgba(241, 245, 249, 0.9);
}

.submit-btn {
  width: 40px;
  height: 40px;
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
  opacity: 0.65;
  cursor: not-allowed;
}

.submit-loading {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.preview-section {
  flex: 3;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.85);
}

.preview-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  font-family: 'Varela Round', 'Nunito Sans', sans-serif;
}

.preview-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.preview-content {
  flex: 1;
  position: relative;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.35);
}

.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-muted);
  gap: 14px;
}

.placeholder-orb {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background:
    radial-gradient(circle at 30% 30%, #fff, transparent 45%),
    linear-gradient(145deg, #5eead4, #38bdf8);
  box-shadow: 0 12px 28px rgba(56, 189, 248, 0.28);
  animation: mascotFloat 3.6s var(--ease) infinite;
}

.preview-placeholder p {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-muted);
  padding: 24px;
  text-align: center;
  box-sizing: border-box;
}

.preview-loading--overlay {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(4px);
  z-index: 2;
}

.preview-loading p {
  margin-top: 16px;
  margin-bottom: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-fg);
  max-width: 320px;
  line-height: 1.5;
}

.preview-loading-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #94a3b8;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
  background: #fff;
}

.preview-iframe--dimmed {
  filter: saturate(0.85);
}

.selected-element-alert {
  margin: 0 16px;
}

.selected-element-info {
  line-height: 1.4;
}

.element-header {
  margin-bottom: 8px;
}

.element-details {
  margin-top: 8px;
}

.element-item {
  margin-bottom: 4px;
  font-size: 13px;
}

.element-item:last-child {
  margin-bottom: 0;
}

.element-tag {
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 13px;
  font-weight: 600;
  color: #0ea5e9;
}

.element-id {
  color: #059669;
  margin-left: 4px;
}

.element-class {
  color: #d97706;
  margin-left: 4px;
}

.element-selector-code {
  font-family: 'Monaco', 'Menlo', monospace;
  background: rgba(241, 245, 249, 0.95);
  padding: 2px 6px;
  border-radius: 6px;
  font-size: 12px;
  color: #e11d48;
  border: 1px solid #e2e8f0;
}

.edit-mode-active {
  color: #fff !important;
  background: linear-gradient(135deg, #34d399, #10b981) !important;
  padding: 4px 10px !important;
}

.edit-mode-active:hover {
  filter: brightness(1.05);
}

@media (max-width: 1024px) {
  #appChatPage {
    height: auto;
    min-height: calc(100dvh - 140px);
    overflow: visible;
  }

  .main-content {
    flex-direction: column;
  }

  .chat-section,
  .preview-section {
    flex: none;
    min-height: 48vh;
  }
}

@media (max-width: 768px) {
  .page-shell {
    padding: 12px;
  }

  .header-bar {
    flex-direction: column;
    align-items: flex-start;
  }

  .app-name {
    font-size: 16px;
  }

  .message-content {
    max-width: 85%;
  }

  .mascot {
    width: 52px;
    height: 52px;
    top: -30px;
    right: 12px;
  }
}
</style>
