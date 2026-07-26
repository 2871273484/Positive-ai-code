<template>
  <div id="categoryManagePage">
    <div class="head">
      <h2>案例分类管理</h2>
      <a-button type="primary" @click="openAdd">新增分类</a-button>
    </div>

    <a-table :columns="columns" :data-source="data" :pagination="false" row-key="id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button size="small" @click="openEdit(record)">编辑</a-button>
            <a-popconfirm title="确定删除该分类？" @confirm="remove(record.id)">
              <a-button danger size="small">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '编辑分类' : '新增分类'"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="saving"
      @ok="save"
    >
      <a-form layout="vertical">
        <a-form-item label="分类名称" required>
          <a-input v-model:value="form.name" placeholder="如：网站" :maxlength="20" />
        </a-form-item>
        <a-form-item label="排序（越小越靠前）">
          <a-input-number v-model:value="form.sortOrder" :min="0" :max="9999" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  addAppCategory,
  deleteAppCategory,
  listAppCategories,
  updateAppCategory,
} from '@/api/appCategoryController'

const columns = [
  { title: 'ID', dataIndex: 'id', width: 100 },
  { title: '名称', dataIndex: 'name' },
  { title: '排序', dataIndex: 'sortOrder', width: 100 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 160 },
]

const data = ref<API.AppCategoryVO[]>([])
const modalOpen = ref(false)
const saving = ref(false)
const editingId = ref<number | undefined>()
const form = reactive({
  name: '',
  sortOrder: 100,
})

const fetchData = async () => {
  const res = await listAppCategories()
  if (res.data.code === 0) {
    data.value = res.data.data || []
  } else {
    message.error(res.data.message || '加载失败')
  }
}

const openAdd = () => {
  editingId.value = undefined
  form.name = ''
  form.sortOrder = 100
  modalOpen.value = true
}

const openEdit = (record: API.AppCategoryVO) => {
  editingId.value = record.id
  form.name = record.name || ''
  form.sortOrder = record.sortOrder ?? 100
  modalOpen.value = true
}

const save = async () => {
  if (!form.name.trim()) {
    message.warning('请输入分类名称')
    return Promise.reject()
  }
  saving.value = true
  try {
    if (editingId.value) {
      const res = await updateAppCategory({
        id: editingId.value,
        name: form.name.trim(),
        sortOrder: form.sortOrder,
      })
      if (res.data.code !== 0) {
        message.error(res.data.message || '更新失败')
        return Promise.reject()
      }
      message.success('已更新')
    } else {
      const res = await addAppCategory({
        name: form.name.trim(),
        sortOrder: form.sortOrder,
      })
      if (res.data.code !== 0) {
        message.error(res.data.message || '新增失败')
        return Promise.reject()
      }
      message.success('已新增')
    }
    modalOpen.value = false
    await fetchData()
  } finally {
    saving.value = false
  }
}

const remove = async (id?: number) => {
  if (!id) return
  const res = await deleteAppCategory({ id })
  if (res.data.code === 0) {
    message.success('已删除')
    await fetchData()
  } else {
    message.error(res.data.message || '删除失败')
  }
}

onMounted(fetchData)
</script>

<style scoped>
#categoryManagePage {
  padding: 24px;
  background: white;
  margin-top: 16px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.head h2 {
  margin: 0;
  font-size: 20px;
}
</style>
