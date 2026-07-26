/** 案例广场分类（前端按关键词推断） */
export const APP_CATEGORIES = [
  '全部',
  '工具',
  '网站',
  '数据分析',
  '活动页面',
  '管理平台',
  '用户应用',
  '个人管理',
  '游戏',
] as const

export type AppCategory = (typeof APP_CATEGORIES)[number]

const RULES: { category: Exclude<AppCategory, '全部'>; keywords: string[] }[] = [
  { category: '游戏', keywords: ['游戏', '闯关', 'puzzle', 'game'] },
  { category: '数据分析', keywords: ['数据', '看板', '分析', '图表', 'dashboard', '统计'] },
  { category: '管理平台', keywords: ['后台', '管理', '运营', 'admin', '中台'] },
  { category: '活动页面', keywords: ['活动', '落地页', '促销', '报名', 'landing', '营销'] },
  { category: '个人管理', keywords: ['待办', '笔记', '日记', '个人管理', '清单', 'todo'] },
  { category: '用户应用', keywords: ['社区', '论坛', '社交', '聊天', '用户'] },
  { category: '工具', keywords: ['工具', '计算器', '转换', '生成器', '工具箱'] },
  { category: '网站', keywords: ['网站', '官网', '博客', '商城', '电商', '作品集', '主页'] },
]

export function resolveAppCategory(app: API.AppVO): Exclude<AppCategory, '全部'> {
  const text = `${app.appName || ''} ${app.initPrompt || ''}`.toLowerCase()
  for (const rule of RULES) {
    if (rule.keywords.some((k) => text.includes(k.toLowerCase()))) {
      return rule.category
    }
  }
  return '网站'
}

export function matchAppCategory(app: API.AppVO, category: AppCategory): boolean {
  if (category === '全部') return true
  return resolveAppCategory(app) === category
}
