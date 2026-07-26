// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 案例广场分类列表 GET /app/category/list */
export async function listAppCategories(options?: { [key: string]: any }) {
  return request<API.BaseResponseListAppCategoryVO>('/app/category/list', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 管理员新增分类 POST /app/category/add */
export async function addAppCategory(
  body: API.AppCategoryAddRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseLong>('/app/category/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 管理员更新分类 POST /app/category/update */
export async function updateAppCategory(
  body: API.AppCategoryUpdateRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean>('/app/category/update', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 管理员删除分类 POST /app/category/delete */
export async function deleteAppCategory(
  body: API.DeleteRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean>('/app/category/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
