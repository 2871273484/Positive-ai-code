<template>
  <div id="appManagePage">
    <!-- 搜索表单 -->
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="应用名称">
        <a-input v-model:value="searchParams.appName" placeholder="输入应用名称" />
      </a-form-item>
      <a-form-item label="创建者">
        <a-input v-model:value="searchParams.userId" placeholder="输入用户ID" />
      </a-form-item>
      <a-form-item label="生成类型">
        <a-select
          v-model:value="searchParams.codeGenType"
          placeholder="选择生成类型"
          style="width: 150px"
        >
          <a-select-option value="">全部</a-select-option>
          <a-select-option
            v-for="option in CODE_GEN_TYPE_OPTIONS"
            :key="option.value"
            :value="option.value"
          >
            {{ option.label }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
    </a-form>
    <a-divider />

    <!-- 表格 -->
    <a-table
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      @change="doTableChange"
      :scroll="{ x: 1200 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'cover'">
          <a-image v-if="record.cover" :src="record.cover" :width="80" :height="60" />
          <div v-else class="no-cover">无封面</div>
        </template>
        <template v-else-if="column.dataIndex === 'initPrompt'">
          <a-tooltip :title="record.initPrompt">
            <div class="prompt-text">{{ record.initPrompt }}</div>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'codeGenType'">
          {{ formatCodeGenType(record.codeGenType) }}
        </template>
        <template v-else-if="column.dataIndex === 'priority'">
          <a-tag v-if="record.priority === 99" color="gold">精选</a-tag>
          <span v-else>{{ record.priority || 0 }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'categoryIds'">
          <div class="tag-cell">
            <template v-if="editingTagAppId === record.id">
              <a-select
                class="tag-select"
                mode="multiple"
                allow-clear
                placeholder="选标签，最多3个"
                :value="draftTagIds"
                :options="categoryOptions"
                :max-tag-count="3"
                style="min-width: 180px; flex: 1"
                @change="(vals: unknown) => onDraftTagChange(vals as number[])"
              />
              <a-button type="link" size="small" @click="saveTags(record)">保存</a-button>
              <a-button type="link" size="small" @click="cancelEditTags">取消</a-button>
            </template>
            <template v-else>
              <div class="tag-view">
                <a-tag v-for="name in record.categoryNames || []" :key="name" color="blue">
                  {{ name }}
                </a-tag>
                <span v-if="!record.categoryNames?.length" class="text-gray">未分类</span>
              </div>
              <a-button type="link" size="small" class="tag-edit-btn" @click="startEditTags(record)">
                编辑
              </a-button>
            </template>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'deployedTime'">
          <span v-if="record.deployedTime">
            {{ formatTime(record.deployedTime) }}
          </span>
          <span v-else class="text-gray">未部署</span>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ formatTime(record.createTime) }}
        </template>
        <template v-else-if="column.dataIndex === 'user'">
          <UserInfo :user="record.user" size="small" />
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="primary" size="small" @click="editApp(record)"> 编辑 </a-button>
            <a-button
              type="default"
              size="small"
              @click="toggleFeatured(record)"
              :class="{ 'featured-btn': record.priority === 99 }"
            >
              {{ record.priority === 99 ? '取消精选' : '精选' }}
            </a-button>
            <a-popconfirm title="确定要删除这个应用吗？" @confirm="deleteApp(record.id)">
              <a-button danger size="small">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { listAppVoByPageByAdmin, deleteAppByAdmin, updateAppByAdmin } from '@/api/appController'
import { listAppCategories } from '@/api/appCategoryController'
import { CODE_GEN_TYPE_OPTIONS, formatCodeGenType } from '@/utils/codeGenTypes'
import { formatTime } from '@/utils/time'
import UserInfo from '@/components/UserInfo.vue'

const router = useRouter()
const categoryOptions = ref<{ label: string; value: number }[]>([])
/** 正在编辑标签的应用 id；未编辑时只展示标签，不显示可删除的 X */
const editingTagAppId = ref<number | undefined>()
const draftTagIds = ref<number[]>([])

const columns = [
  {
    title: 'ID',
    dataIndex: 'id',
    width: 80,
    fixed: 'left',
  },
  {
    title: '应用名称',
    dataIndex: 'appName',
    width: 150,
  },
  {
    title: '封面',
    dataIndex: 'cover',
    width: 100,
  },
  {
    title: '初始提示词',
    dataIndex: 'initPrompt',
    width: 200,
  },
  {
    title: '生成类型',
    dataIndex: 'codeGenType',
    width: 100,
  },
  {
    title: '优先级',
    dataIndex: 'priority',
    width: 80,
  },
  {
    title: '广场分类',
    dataIndex: 'categoryIds',
    width: 280,
  },
  {
    title: '部署时间',
    dataIndex: 'deployedTime',
    width: 160,
  },
  {
    title: '创建者',
    dataIndex: 'user',
    width: 120,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 160,
  },
  {
    title: '操作',
    key: 'action',
    width: 200,
    fixed: 'right',
  },
]

// 数据
const data = ref<API.AppVO[]>([])
const total = ref(0)

// 搜索条件
const searchParams = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

// 获取数据
const fetchData = async () => {
  try {
    const res = await listAppVoByPageByAdmin({
      ...searchParams,
    })
    if (res.data.data) {
      data.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0
    } else {
      message.error('获取数据失败，' + res.data.message)
    }
  } catch (error) {
    console.error('获取数据失败：', error)
    message.error('获取数据失败')
  }
}

const loadCategories = async () => {
  try {
    const res = await listAppCategories()
    if (res.data.code === 0 && res.data.data) {
      categoryOptions.value = res.data.data
        .filter((c) => c.id != null && c.name)
        .map((c) => ({ label: c.name!, value: c.id! }))
    }
  } catch (error) {
    console.error('加载分类失败：', error)
  }
}

const startEditTags = (app: API.AppVO) => {
  editingTagAppId.value = app.id
  draftTagIds.value = [...(app.categoryIds || [])].slice(0, 3)
}

const cancelEditTags = () => {
  editingTagAppId.value = undefined
  draftTagIds.value = []
}

const onDraftTagChange = (values: number[]) => {
  if ((values || []).length > 3) {
    message.warning('最多选择 3 个标签')
  }
  draftTagIds.value = (values || []).slice(0, 3)
}

const saveTags = async (app: API.AppVO) => {
  if (!app.id) return
  const next = draftTagIds.value.slice(0, 3)
  try {
    const res = await updateAppByAdmin({
      id: app.id,
      categoryIds: next,
    })
    if (res.data.code === 0) {
      message.success('标签已更新')
      cancelEditTags()
      fetchData()
    } else {
      message.error(res.data.message || '更新失败')
    }
  } catch (error) {
    console.error(error)
    message.error('更新失败')
  }
}

// 页面加载时请求一次
onMounted(() => {
  void loadCategories()
  fetchData()
})

// 分页参数
const pagination = computed(() => {
  return {
    current: searchParams.pageNum ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
  }
})

// 表格变化处理
const doTableChange = (page: { current: number; pageSize: number }) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

// 搜索
const doSearch = () => {
  // 重置页码
  searchParams.pageNum = 1
  fetchData()
}

// 编辑应用
const editApp = (app: API.AppVO) => {
  router.push(`/app/edit/${app.id}`)
}

// 切换精选状态
const toggleFeatured = async (app: API.AppVO) => {
  if (!app.id) return

  const newPriority = app.priority === 99 ? 0 : 99

  try {
    const res = await updateAppByAdmin({
      id: app.id,
      priority: newPriority,
    })

    if (res.data.code === 0) {
      message.success(
        newPriority === 99 ? '已设为精选，案例广场将立即展示' : '已取消精选，已从案例广场移除',
      )
      // 刷新数据
      fetchData()
    } else {
      message.error('操作失败：' + res.data.message)
    }
  } catch (error) {
    console.error('操作失败：', error)
    message.error('操作失败')
  }
}

// 删除应用
const deleteApp = async (id: number | undefined) => {
  if (!id) return

  try {
    const res = await deleteAppByAdmin({ id })
    if (res.data.code === 0) {
      message.success('删除成功')
      // 刷新数据
      fetchData()
    } else {
      message.error('删除失败：' + res.data.message)
    }
  } catch (error) {
    console.error('删除失败：', error)
    message.error('删除失败')
  }
}
</script>

<style scoped>
#appManagePage {
  padding: 24px;
  background: white;
  margin-top: 16px;
}

.no-cover {
  width: 80px;
  height: 60px;
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 12px;
  border-radius: 4px;
}

.prompt-text {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.text-gray {
  color: #999;
}

.featured-btn {
  background: #faad14;
  border-color: #faad14;
  color: white;
}

.featured-btn:hover {
  background: #d48806;
  border-color: #d48806;
}

:deep(.ant-table-tbody > tr > td) {
  vertical-align: middle;
}

.tag-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.tag-view {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.tag-edit-btn {
  flex-shrink: 0;
  padding-inline: 4px !important;
}

.tag-select :deep(.ant-select-selector) {
  border-radius: 10px !important;
}
</style>
