<template>
  <div class="data-analysis">
    <div class="page-header">
      <h2 class="page-title">数据分析</h2>
    </div>
    
    <!-- 数据概览 -->
    <div class="overview-section">
      <el-row :gutter="20">
        <el-col :xs="24" :sm="12" :md="6">
          <div class="metric-card primary">
            <div class="metric-icon">
              <el-icon><User /></el-icon>
            </div>
            <div class="metric-content">
              <div class="metric-value">{{ overview.totalUsers.toLocaleString() }}</div>
              <div class="metric-label">总用户数</div>
              <div class="metric-change positive">
                <el-icon><ArrowUp /></el-icon>
                +{{ overview.userGrowth }}%
              </div>
            </div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <div class="metric-card success">
            <div class="metric-icon">
              <el-icon><ChatDotRound /></el-icon>
            </div>
            <div class="metric-content">
              <div class="metric-value">{{ overview.totalPosts.toLocaleString() }}</div>
              <div class="metric-label">总帖子数</div>
              <div class="metric-change positive">
                <el-icon><ArrowUp /></el-icon>
                +{{ overview.postGrowth }}%
              </div>
            </div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <div class="metric-card warning">
            <div class="metric-icon">
              <el-icon><View /></el-icon>
            </div>
            <div class="metric-content">
              <div class="metric-value">{{ overview.totalViews.toLocaleString() }}</div>
              <div class="metric-label">总浏览量</div>
              <div class="metric-change positive">
                <el-icon><ArrowUp /></el-icon>
                +{{ overview.viewGrowth }}%
              </div>
            </div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <div class="metric-card info">
            <div class="metric-icon">
              <el-icon><Timer /></el-icon>
            </div>
            <div class="metric-content">
              <div class="metric-value">{{ overview.avgOnlineTime }}</div>
              <div class="metric-label">平均在线时长</div>
              <div class="metric-change positive">
                <el-icon><ArrowUp /></el-icon>
                +{{ overview.timeGrowth }}%
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
    
    <!-- 图表分析 -->
    <div class="charts-section">
      <el-row :gutter="20">
        <!-- 用户增长趋势 -->
        <el-col :xs="24" :lg="12">
          <div class="admin-card">
            <div class="card-header">
              <h3>用户增长趋势</h3>
              <el-radio-group v-model="userTrendPeriod" size="small" @change="updateUserTrendChart">
                <el-radio-button label="7d">7天</el-radio-button>
                <el-radio-button label="30d">30天</el-radio-button>
                <el-radio-button label="90d">90天</el-radio-button>
              </el-radio-group>
            </div>
            <div class="card-body">
              <div ref="userTrendChart" style="height: 300px;"></div>
            </div>
          </div>
        </el-col>
        
        <!-- 内容发布趋势 -->
        <el-col :xs="24" :lg="12">
          <div class="admin-card">
            <div class="card-header">
              <h3>内容发布趋势</h3>
              <el-radio-group v-model="contentTrendPeriod" size="small" @change="updateContentTrendChart">
                <el-radio-button label="7d">7天</el-radio-button>
                <el-radio-button label="30d">30天</el-radio-button>
                <el-radio-button label="90d">90天</el-radio-button>
              </el-radio-group>
            </div>
            <div class="card-body">
              <div ref="contentTrendChart" style="height: 300px;"></div>
            </div>
          </div>
        </el-col>
        
        <!-- 用户活跃度分布 -->
        <el-col :xs="24" :lg="12">
          <div class="admin-card">
            <div class="card-header">
              <h3>用户活跃度分布</h3>
            </div>
            <div class="card-body">
              <div ref="userActivityChart" style="height: 300px;"></div>
            </div>
          </div>
        </el-col>
        
        <!-- 内容类型分布 -->
        <el-col :xs="24" :lg="12">
          <div class="admin-card">
            <div class="card-header">
              <h3>内容类型分布</h3>
            </div>
            <div class="card-body">
              <div ref="contentTypeChart" style="height: 300px;"></div>
            </div>
          </div>
        </el-col>
        
        <!-- 地域分布 -->
        <el-col :xs="24">
          <div class="admin-card">
            <div class="card-header">
              <h3>用户地域分布</h3>
            </div>
            <div class="card-body">
              <div ref="geoDistributionChart" style="height: 400px;"></div>
            </div>
          </div>
        </el-col>
        
        <!-- 实时数据监控 -->
        <el-col :xs="24">
          <div class="admin-card">
            <div class="card-header">
              <h3>实时数据监控</h3>
              <el-button 
                type="primary" 
                size="small" 
                :icon="isRealTimeActive ? 'VideoPause' : 'VideoPlay'"
                @click="toggleRealTimeMonitoring"
              >
                {{ isRealTimeActive ? '暂停' : '开始' }}监控
              </el-button>
            </div>
            <div class="card-body">
              <div class="realtime-metrics">
                <el-row :gutter="16">
                  <el-col :xs="12" :sm="6" :md="3">
                    <div class="realtime-card">
                      <div class="realtime-value">{{ realtimeData.onlineUsers }}</div>
                      <div class="realtime-label">在线用户</div>
                    </div>
                  </el-col>
                  <el-col :xs="12" :sm="6" :md="3">
                    <div class="realtime-card">
                      <div class="realtime-value">{{ realtimeData.todayPosts }}</div>
                      <div class="realtime-label">今日发帖</div>
                    </div>
                  </el-col>
                  <el-col :xs="12" :sm="6" :md="3">
                    <div class="realtime-card">
                      <div class="realtime-value">{{ realtimeData.todayViews }}</div>
                      <div class="realtime-label">今日浏览</div>
                    </div>
                  </el-col>
                  <el-col :xs="12" :sm="6" :md="3">
                    <div class="realtime-card">
                      <div class="realtime-value">{{ realtimeData.todayRegistrations }}</div>
                      <div class="realtime-label">今日注册</div>
                    </div>
                  </el-col>
                </el-row>
              </div>
              <div ref="realtimeChart" style="height: 250px; margin-top: 20px;"></div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
    
    <!-- 数据导出 -->
    <div class="export-section">
      <div class="admin-card">
        <div class="card-header">
          <h3>数据导出</h3>
        </div>
        <div class="card-body">
          <el-row :gutter="20">
            <el-col :xs="24" :sm="12" :md="8">
              <div class="export-item">
                <h4>用户数据报告</h4>
                <p>包含用户注册、活跃度、地域分布等统计数据</p>
                <el-button type="primary" @click="exportData('user')">
                  <el-icon><Download /></el-icon>
                  导出用户数据
                </el-button>
              </div>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <div class="export-item">
                <h4>内容数据报告</h4>
                <p>包含帖子发布、浏览量、互动数据等统计信息</p>
                <el-button type="success" @click="exportData('content')">
                  <el-icon><Download /></el-icon>
                  导出内容数据
                </el-button>
              </div>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <div class="export-item">
                <h4>综合数据报告</h4>
                <p>包含平台整体运营数据的综合分析报告</p>
                <el-button type="warning" @click="exportData('comprehensive')">
                  <el-icon><Download /></el-icon>
                  导出综合报告
                </el-button>
              </div>
            </el-col>
          </el-row>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { 
  User,
  ChatDotRound,
  View,
  Timer,
  ArrowUp,
  Download
} from '@element-plus/icons-vue'

// 响应式数据
const userTrendChart = ref()
const contentTrendChart = ref()
const userActivityChart = ref()
const contentTypeChart = ref()
const geoDistributionChart = ref()
const realtimeChart = ref()

const userTrendPeriod = ref('30d')
const contentTrendPeriod = ref('30d')
const isRealTimeActive = ref(false)
let realtimeInterval = null
let chartInstances = {}

const overview = reactive({
  totalUsers: 125680,
  userGrowth: 12.5,
  totalPosts: 45320,
  postGrowth: 8.7,
  totalViews: 2345678,
  viewGrowth: 15.3,
  avgOnlineTime: '45min',
  timeGrowth: 6.2
})

const realtimeData = reactive({
  onlineUsers: 1234,
  todayPosts: 156,
  todayViews: 8765,
  todayRegistrations: 23
})

// 方法
const initCharts = async () => {
  await nextTick()
  
  // 用户增长趋势图
  if (userTrendChart.value) {
    chartInstances.userTrend = echarts.init(userTrendChart.value)
    updateUserTrendChart()
  }
  
  // 内容发布趋势图
  if (contentTrendChart.value) {
    chartInstances.contentTrend = echarts.init(contentTrendChart.value)
    updateContentTrendChart()
  }
  
  // 用户活跃度分布图
  if (userActivityChart.value) {
    chartInstances.userActivity = echarts.init(userActivityChart.value)
    initUserActivityChart()
  }
  
  // 内容类型分布图
  if (contentTypeChart.value) {
    chartInstances.contentType = echarts.init(contentTypeChart.value)
    initContentTypeChart()
  }
  
  // 地域分布图
  if (geoDistributionChart.value) {
    chartInstances.geoDistribution = echarts.init(geoDistributionChart.value)
    initGeoDistributionChart()
  }
  
  // 实时监控图
  if (realtimeChart.value) {
    chartInstances.realtime = echarts.init(realtimeChart.value)
    initRealtimeChart()
  }
}

const updateUserTrendChart = () => {
  if (!chartInstances.userTrend) return
  
  const days = userTrendPeriod.value === '7d' ? 7 : userTrendPeriod.value === '30d' ? 30 : 90
  const dates = []
  const newUsers = []
  const totalUsers = []
  
  for (let i = days - 1; i >= 0; i--) {
    const date = new Date()
    date.setDate(date.getDate() - i)
    dates.push(date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' }))
    newUsers.push(Math.floor(Math.random() * 200) + 50)
    totalUsers.push(Math.floor(Math.random() * 500) + 1000)
  }
  
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: ['新增用户', '累计用户']
    },
    xAxis: {
      type: 'category',
      data: dates
    },
    yAxis: [
      {
        type: 'value',
        name: '新增用户',
        position: 'left'
      },
      {
        type: 'value',
        name: '累计用户',
        position: 'right'
      }
    ],
    series: [
      {
        name: '新增用户',
        type: 'bar',
        data: newUsers,
        itemStyle: { color: '#409eff' }
      },
      {
        name: '累计用户',
        type: 'line',
        yAxisIndex: 1,
        data: totalUsers,
        itemStyle: { color: '#67c23a' },
        smooth: true
      }
    ]
  }
  
  chartInstances.userTrend.setOption(option)
}

const updateContentTrendChart = () => {
  if (!chartInstances.contentTrend) return
  
  const days = contentTrendPeriod.value === '7d' ? 7 : contentTrendPeriod.value === '30d' ? 30 : 90
  const dates = []
  const posts = []
  const comments = []
  
  for (let i = days - 1; i >= 0; i--) {
    const date = new Date()
    date.setDate(date.getDate() - i)
    dates.push(date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' }))
    posts.push(Math.floor(Math.random() * 100) + 20)
    comments.push(Math.floor(Math.random() * 300) + 50)
  }
  
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: ['发帖数', '评论数']
    },
    xAxis: {
      type: 'category',
      data: dates
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '发帖数',
        type: 'line',
        data: posts,
        itemStyle: { color: '#e6a23c' },
        smooth: true,
        areaStyle: { opacity: 0.3 }
      },
      {
        name: '评论数',
        type: 'line',
        data: comments,
        itemStyle: { color: '#f56c6c' },
        smooth: true,
        areaStyle: { opacity: 0.3 }
      }
    ]
  }
  
  chartInstances.contentTrend.setOption(option)
}

const initUserActivityChart = () => {
  if (!chartInstances.userActivity) return
  
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [
      {
        name: '用户活跃度',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['60%', '50%'],
        data: [
          { value: 35, name: '高活跃用户', itemStyle: { color: '#67c23a' } },
          { value: 45, name: '中活跃用户', itemStyle: { color: '#e6a23c' } },
          { value: 20, name: '低活跃用户', itemStyle: { color: '#f56c6c' } }
        ],
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  }
  
  chartInstances.userActivity.setOption(option)
}

const initContentTypeChart = () => {
  if (!chartInstances.contentType) return
  
  const option = {
    tooltip: {
      trigger: 'item'
    },
    legend: {
      top: '5%',
      left: 'center'
    },
    series: [
      {
        name: '内容类型',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['50%', '60%'],
        data: [
          { value: 40, name: '植物分享', itemStyle: { color: '#67c23a' } },
          { value: 25, name: '种植经验', itemStyle: { color: '#409eff' } },
          { value: 20, name: '问答求助', itemStyle: { color: '#e6a23c' } },
          { value: 10, name: '园艺工具', itemStyle: { color: '#f56c6c' } },
          { value: 5, name: '其他', itemStyle: { color: '#909399' } }
        ],
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        },
        labelLine: {
          show: false
        },
        label: {
          show: true,
          position: 'center'
        }
      }
    ]
  }
  
  chartInstances.contentType.setOption(option)
}

const initGeoDistributionChart = () => {
  if (!chartInstances.geoDistribution) return
  
  const data = [
    { name: '北京', value: 2500 },
    { name: '上海', value: 2200 },
    { name: '广东', value: 1800 },
    { name: '浙江', value: 1500 },
    { name: '江苏', value: 1400 },
    { name: '四川', value: 1200 },
    { name: '湖北', value: 1000 },
    { name: '湖南', value: 900 },
    { name: '福建', value: 800 },
    { name: '河南', value: 700 }
  ]
  
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} 用户'
    },
    xAxis: {
      type: 'category',
      data: data.map(item => item.name),
      axisLabel: {
        interval: 0,
        rotate: 45
      }
    },
    yAxis: {
      type: 'value',
      name: '用户数量'
    },
    series: [
      {
        type: 'bar',
        data: data.map(item => item.value),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#409eff' },
            { offset: 1, color: '#67c23a' }
          ])
        },
        emphasis: {
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#67c23a' },
              { offset: 1, color: '#409eff' }
            ])
          }
        }
      }
    ]
  }
  
  chartInstances.geoDistribution.setOption(option)
}

const initRealtimeChart = () => {
  if (!chartInstances.realtime) return
  
  const times = []
  const values = []
  
  for (let i = 23; i >= 0; i--) {
    const time = new Date()
    time.setMinutes(time.getMinutes() - i)
    times.push(time.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }))
    values.push(Math.floor(Math.random() * 100) + 50)
  }
  
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    xAxis: {
      type: 'category',
      data: times,
      axisLabel: {
        interval: 4
      }
    },
    yAxis: {
      type: 'value',
      name: '在线用户数'
    },
    series: [
      {
        type: 'line',
        data: values,
        itemStyle: { color: '#409eff' },
        smooth: true,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.1)' }
          ])
        }
      }
    ]
  }
  
  chartInstances.realtime.setOption(option)
}

const toggleRealTimeMonitoring = () => {
  isRealTimeActive.value = !isRealTimeActive.value
  
  if (isRealTimeActive.value) {
    realtimeInterval = setInterval(() => {
      // 更新实时数据
      realtimeData.onlineUsers = Math.floor(Math.random() * 200) + 1100
      realtimeData.todayPosts = Math.floor(Math.random() * 50) + 150
      realtimeData.todayViews = Math.floor(Math.random() * 1000) + 8000
      realtimeData.todayRegistrations = Math.floor(Math.random() * 10) + 20
      
      // 更新实时图表
      if (chartInstances.realtime) {
        const option = chartInstances.realtime.getOption()
        const newTime = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
        const newValue = Math.floor(Math.random() * 100) + 50
        
        option.xAxis[0].data.push(newTime)
        option.xAxis[0].data.shift()
        option.series[0].data.push(newValue)
        option.series[0].data.shift()
        
        chartInstances.realtime.setOption(option)
      }
    }, 3000)
  } else {
    if (realtimeInterval) {
      clearInterval(realtimeInterval)
      realtimeInterval = null
    }
  }
}

const exportData = (type) => {
  const typeMap = {
    user: '用户数据',
    content: '内容数据',
    comprehensive: '综合数据'
  }
  
  ElMessage.success(`${typeMap[type]}报告导出中，请稍候...`)
  
  // 模拟导出过程
  setTimeout(() => {
    ElMessage.success(`${typeMap[type]}报告导出完成！`)
  }, 2000)
}

const resizeCharts = () => {
  Object.values(chartInstances).forEach(chart => {
    if (chart && typeof chart.resize === 'function') {
      chart.resize()
    }
  })
}

// 生命周期
onMounted(() => {
  initCharts()
  
  // 监听窗口大小变化
  window.addEventListener('resize', resizeCharts)
})

onUnmounted(() => {
  // 清理定时器
  if (realtimeInterval) {
    clearInterval(realtimeInterval)
  }
  
  // 销毁图表实例
  Object.values(chartInstances).forEach(chart => {
    if (chart && typeof chart.dispose === 'function') {
      chart.dispose()
    }
  })
  
  // 移除事件监听
  window.removeEventListener('resize', resizeCharts)
})
</script>

<style lang="scss" scoped>
.data-analysis {
  .overview-section {
    margin-bottom: 24px;
  }
  
  .charts-section {
    margin-bottom: 24px;
    
    .admin-card {
      margin-bottom: 20px;
      
      .card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 16px 20px;
        border-bottom: 1px solid #e4e7ed;
        
        h3 {
          margin: 0;
          color: #303133;
          font-size: 16px;
          font-weight: 600;
        }
      }
      
      .card-body {
        padding: 20px;
      }
    }
  }
  
  .export-section {
    .export-item {
      text-align: center;
      padding: 20px;
      border: 1px solid #e4e7ed;
      border-radius: 8px;
      background: #fafafa;
      transition: all 0.3s ease;
      
      &:hover {
        border-color: #409eff;
        background: #f0f9ff;
      }
      
      h4 {
        margin: 0 0 12px 0;
        color: #303133;
        font-size: 16px;
        font-weight: 600;
      }
      
      p {
        margin: 0 0 16px 0;
        color: #606266;
        font-size: 14px;
        line-height: 1.6;
      }
    }
  }
  
  .realtime-metrics {
    margin-bottom: 20px;
    
    .realtime-card {
      text-align: center;
      padding: 20px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      border-radius: 8px;
      color: white;
      
      .realtime-value {
        font-size: 24px;
        font-weight: 600;
        margin-bottom: 8px;
      }
      
      .realtime-label {
        font-size: 14px;
        opacity: 0.9;
      }
    }
  }
}

@media (max-width: 768px) {
  .data-analysis {
    .charts-section {
      .admin-card {
        .card-header {
          flex-direction: column;
          gap: 12px;
          align-items: stretch;
        }
      }
    }
    
    .export-section {
      .el-col {
        margin-bottom: 16px;
      }
    }
    
    .realtime-metrics {
      .el-col {
        margin-bottom: 12px;
      }
    }
  }
}
</style>
