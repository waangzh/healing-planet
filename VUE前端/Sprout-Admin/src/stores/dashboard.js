import { defineStore } from 'pinia'
import { reactive, computed } from 'vue'

export const useDashboardStore = defineStore('dashboard', () => {
  // state
  const overview = reactive({
    totalUsers: 0,
    activeUsers: 0,
    totalPosts: 0,
    todayPosts: 0,
    totalPlants: 0,
    aiConversations: 0,
    systemStatus: 'normal'
  })

  const charts = reactive({
    userGrowth: [],
    postTrends: [],
    plantRecognition: [],
    hotTags: []
  })

  const pendingItems = reactive({
    pendingPosts: 0,
    pendingComments: 0,
    pendingReports: 0,
    systemMessages: 0
  })

  // getters
  const systemStatusColor = computed(() => {
    const statusMap = { normal: 'success', warning: 'warning', error: 'danger' }
    return statusMap[overview.systemStatus] || 'info'
  })

  const systemStatusText = computed(() => {
    const statusMap = { normal: '正常运行', warning: '运行异常', error: '系统故障' }
    return statusMap[overview.systemStatus] || '未知状态'
  })

  const totalPendingCount = computed(() => {
    return Object.values(pendingItems).reduce((sum, count) => sum + count, 0)
  })

  // actions
  async function fetchDashboardData() {
    try {
      const data = await mockDashboardAPI()
      Object.assign(overview, data.overview)
      Object.assign(charts, data.charts)
      Object.assign(pendingItems, data.pendingItems)
      return { success: true }
    } catch (error) {
      return { success: false, message: error.message }
    }
  }

  function updateOverview(data) { Object.assign(overview, data) }
  function updateCharts(data) { Object.assign(charts, data) }
  function updatePendingItems(data) { Object.assign(pendingItems, data) }

  // mock helpers
  function mockDashboardAPI() {
    return new Promise((resolve) => {
      setTimeout(() => {
        resolve({
          overview: {
            totalUsers: 12,
            activeUsers: 2,
            totalPosts: 30,
            todayPosts: 124,
            totalPlants: 19,
            aiConversations: 25,
            systemStatus: 'normal'
          },
          charts: {
            userGrowth: generateUserGrowthData(),
            postTrends: generatePostTrendsData(),
            plantRecognition: generatePlantRecognitionData(),
            hotTags: generateHotTagsData()
          },
          pendingItems: {
            pendingPosts: 23,
            pendingComments: 67,
            pendingReports: 12,
            systemMessages: 8
          }
        })
      }, 800)
    })
  }

  function generateUserGrowthData() {
    const data = []
    const now = new Date()
    for (let i = 29; i >= 0; i--) {
      const date = new Date(now)
      date.setDate(date.getDate() - i)
      data.push({
        date: date.toISOString().split('T')[0],
        users: Math.floor(Math.random() * 100) + 50,
        activeUsers: Math.floor(Math.random() * 50) + 20
      })
    }
    return data
  }

  function generatePostTrendsData() {
    const data = []
    const now = new Date()
    for (let i = 6; i >= 0; i--) {
      const date = new Date(now)
      date.setDate(date.getDate() - i)
      data.push({
        date: date.toISOString().split('T')[0],
        posts: Math.floor(Math.random() * 200) + 100,
        comments: Math.floor(Math.random() * 500) + 200
      })
    }
    return data
  }

  function generatePlantRecognitionData() {
    return [
      { name: '成功识别', value: 85, color: '#67c23a' },
      { name: '识别失败', value: 12, color: '#f56c6c' },
      { name: '待确认', value: 3, color: '#e6a23c' }
    ]
  }

  function generateHotTagsData() {
    return [
      { name: '多肉植物', count: 1234 },
      { name: '绿萝', count: 987 },
      { name: '花卉养护', count: 856 },
      { name: '盆栽', count: 743 },
      { name: '室内植物', count: 689 },
      { name: '植物病虫害', count: 567 },
      { name: '施肥技巧', count: 456 },
      { name: '浇水方法', count: 345 }
    ]
  }

  return {
    // state
    overview, charts, pendingItems,
    // getters
    systemStatusColor, systemStatusText, totalPendingCount,
    // actions
    fetchDashboardData, updateOverview, updateCharts, updatePendingItems,
    // mock helpers (可选导出, 若外部不需要可移除)
    mockDashboardAPI, generateUserGrowthData, generatePostTrendsData, generatePlantRecognitionData, generateHotTagsData
  }
})
