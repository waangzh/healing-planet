<template>
  <div class="user-analysis">
    <div class="page-header">
      <h2 class="page-title">用户分析</h2>
    </div>
    
    <!-- 用户概览 -->
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
              <el-icon><Link /></el-icon>
            </div>
            <div class="metric-content">
              <div class="metric-value">{{ overview.activeUsers.toLocaleString() }}</div>
              <div class="metric-label">活跃用户</div>
              <div class="metric-change positive">
                <el-icon><ArrowUp /></el-icon>
                +{{ overview.activeGrowth }}%
              </div>
            </div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <div class="metric-card warning">
            <div class="metric-icon">
              <el-icon><UserFilled /></el-icon>
            </div>
            <div class="metric-content">
              <div class="metric-value">{{ overview.newUsers.toLocaleString() }}</div>
              <div class="metric-label">新增用户</div>
              <div class="metric-change positive">
                <el-icon><ArrowUp /></el-icon>
                +{{ overview.newGrowth }}%
              </div>
            </div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <div class="metric-card info">
            <div class="metric-icon">
              <el-icon><Clock /></el-icon>
            </div>
            <div class="metric-content">
              <div class="metric-value">{{ overview.retentionRate }}%</div>
              <div class="metric-label">留存率</div>
              <div class="metric-change positive">
                <el-icon><ArrowUp /></el-icon>
                +{{ overview.retentionGrowth }}%
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
    
    <!-- 用户分析图表 -->
    <div class="charts-section">
      <el-row :gutter="20">
        <!-- 用户注册趋势 -->
        <el-col :xs="24" :lg="12">
          <div class="admin-card">
            <div class="card-header">
              <h3>用户注册趋势</h3>
              <el-radio-group v-model="registrationPeriod" size="small" @change="updateRegistrationChart">
                <el-radio-button label="7d">7天</el-radio-button>
                <el-radio-button label="30d">30天</el-radio-button>
                <el-radio-button label="90d">90天</el-radio-button>
              </el-radio-group>
            </div>
            <div class="card-body">
              <div ref="registrationChart" style="height: 300px;"></div>
            </div>
          </div>
        </el-col>
        
        <!-- 用户活跃度趋势 -->
        <el-col :xs="24" :lg="12">
          <div class="admin-card">
            <div class="card-header">
              <h3>用户活跃度趋势</h3>
              <el-radio-group v-model="activityPeriod" size="small" @change="updateActivityChart">
                <el-radio-button label="7d">7天</el-radio-button>
                <el-radio-button label="30d">30天</el-radio-button>
                <el-radio-button label="90d">90天</el-radio-button>
              </el-radio-group>
            </div>
            <div class="card-body">
              <div ref="activityChart" style="height: 300px;"></div>
            </div>
          </div>
        </el-col>
        
        <!-- 用户年龄分布 -->
        <el-col :xs="24" :lg="12">
          <div class="admin-card">
            <div class="card-header">
              <h3>用户年龄分布</h3>
            </div>
            <div class="card-body">
              <div ref="ageDistributionChart" style="height: 300px;"></div>
            </div>
          </div>
        </el-col>
        
        <!-- 用户性别分布 -->
        <el-col :xs="24" :lg="12">
          <div class="admin-card">
            <div class="card-header">
              <h3>用户性别分布</h3>
            </div>
            <div class="card-body">
              <div ref="genderDistributionChart" style="height: 300px;"></div>
            </div>
          </div>
        </el-col>
        
        <!-- 用户留存分析 -->
        <el-col :xs="24">
          <div class="admin-card">
            <div class="card-header">
              <h3>用户留存分析</h3>
            </div>
            <div class="card-body">
              <div ref="retentionChart" style="height: 400px;"></div>
            </div>
          </div>
        </el-col>
        
        <!-- 用户行为漏斗 -->
        <el-col :xs="24" :lg="12">
          <div class="admin-card">
            <div class="card-header">
              <h3>用户行为漏斗</h3>
            </div>
            <div class="card-body">
              <div ref="behaviorFunnelChart" style="height: 350px;"></div>
            </div>
          </div>
        </el-col>
        
        <!-- 用户设备分布 -->
        <el-col :xs="24" :lg="12">
          <div class="admin-card">
            <div class="card-header">
              <h3>用户设备分布</h3>
            </div>
            <div class="card-body">
              <div ref="deviceDistributionChart" style="height: 350px;"></div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
    
    <!-- 用户列表分析 -->
    <div class="user-list-section">
      <div class="admin-card">
        <div class="card-header">
          <h3>活跃用户排行</h3>
          <div class="header-actions">
            <el-select v-model="listType" placeholder="选择类型" style="width: 150px">
              <el-option label="活跃度排行" value="activity" />
              <el-option label="发帖数排行" value="posts" />
              <el-option label="粉丝数排行" value="followers" />
              <el-option label="新用户" value="new" />
            </el-select>
            <el-button type="primary" @click="exportUserList">导出列表</el-button>
          </div>
        </div>
        <div class="card-body">
          <el-table :data="userList" v-loading="userListLoading" stripe>
            <el-table-column prop="rank" label="排名" width="80" />
            <el-table-column prop="avatar" label="头像" width="80">
              <template #default="{ row }">
                <el-avatar :src="row.avatar" :size="40" />
              </template>
            </el-table-column>
            <el-table-column prop="username" label="用户名" min-width="120" />
            <el-table-column prop="score" label="活跃分数" width="100">
              <template #default="{ row }">
                <el-tag :type="getScoreType(row.score)" size="small">
                  {{ row.score }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="postsCount" label="发帖数" width="80" />
            <el-table-column prop="followersCount" label="粉丝数" width="80" />
            <el-table-column prop="lastLoginTime" label="最后登录" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.lastLoginTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="registrationTime" label="注册时间" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.registrationTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button type="text" size="small" @click="viewUserDetail(row)">
                  详情
                </el-button>
                <el-button type="text" size="small" @click="sendMessage(row)">
                  发消息
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="userListPagination.current"
              v-model:page-size="userListPagination.size"
              :total="userListPagination.total"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="fetchUserList"
              @current-change="fetchUserList"
            />
          </div>
        </div>
      </div>
    </div>
    
    <!-- 用户详情对话框 -->
    <el-dialog
      v-model="userDetailDialog.visible"
      title="用户详情"
      width="600px"
      destroy-on-close
    >
      <div v-if="userDetailDialog.user" class="user-detail">
        <div class="user-basic-info">
          <div class="user-avatar">
            <el-avatar :src="userDetailDialog.user.avatar" :size="80" />
          </div>
          <div class="user-info">
            <h3>{{ userDetailDialog.user.username }}</h3>
            <p>用户ID: {{ userDetailDialog.user.id }}</p>
            <p>注册时间: {{ formatDateTime(userDetailDialog.user.registrationTime) }}</p>
            <p>最后登录: {{ formatDateTime(userDetailDialog.user.lastLoginTime) }}</p>
          </div>
        </div>
        
        <el-divider />
        
        <el-descriptions :column="2" border>
          <el-descriptions-item label="活跃分数">
            <el-tag :type="getScoreType(userDetailDialog.user.score)">
              {{ userDetailDialog.user.score }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="用户状态">
            <el-tag :type="userDetailDialog.user.status === 'active' ? 'success' : 'danger'">
              {{ userDetailDialog.user.status === 'active' ? '正常' : '禁用' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="发帖数量">
            {{ userDetailDialog.user.postsCount }}
          </el-descriptions-item>
          <el-descriptions-item label="评论数量">
            {{ userDetailDialog.user.commentsCount }}
          </el-descriptions-item>
          <el-descriptions-item label="粉丝数量">
            {{ userDetailDialog.user.followersCount }}
          </el-descriptions-item>
          <el-descriptions-item label="关注数量">
            {{ userDetailDialog.user.followingCount }}
          </el-descriptions-item>
          <el-descriptions-item label="累计浏览">
            {{ userDetailDialog.user.totalViews }}
          </el-descriptions-item>
          <el-descriptions-item label="累计点赞">
            {{ userDetailDialog.user.totalLikes }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { 
  User,
  Link,
  UserFilled,
  Clock,
  ArrowUp
} from '@element-plus/icons-vue'

// 响应式数据
const registrationChart = ref()
const activityChart = ref()
const ageDistributionChart = ref()
const genderDistributionChart = ref()
const retentionChart = ref()
const behaviorFunnelChart = ref()
const deviceDistributionChart = ref()

const registrationPeriod = ref('30d')
const activityPeriod = ref('30d')
const listType = ref('activity')
const userListLoading = ref(false)

let chartInstances = {}

const overview = reactive({
  totalUsers: 125680,
  userGrowth: 12.5,
  activeUsers: 45280,
  activeGrowth: 8.7,
  newUsers: 2340,
  newGrowth: 15.3,
  retentionRate: 68.5,
  retentionGrowth: 3.2
})

const userList = ref([])
const userListPagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

const userDetailDialog = reactive({
  visible: false,
  user: null
})

// 方法
const initCharts = async () => {
  await nextTick()
  
  // 用户注册趋势图
  if (registrationChart.value) {
    chartInstances.registration = echarts.init(registrationChart.value)
    updateRegistrationChart()
  }
  
  // 用户活跃度趋势图
  if (activityChart.value) {
    chartInstances.activity = echarts.init(activityChart.value)
    updateActivityChart()
  }
  
  // 年龄分布图
  if (ageDistributionChart.value) {
    chartInstances.ageDistribution = echarts.init(ageDistributionChart.value)
    initAgeDistributionChart()
  }
  
  // 性别分布图
  if (genderDistributionChart.value) {
    chartInstances.genderDistribution = echarts.init(genderDistributionChart.value)
    initGenderDistributionChart()
  }
  
  // 留存分析图
  if (retentionChart.value) {
    chartInstances.retention = echarts.init(retentionChart.value)
    initRetentionChart()
  }
  
  // 行为漏斗图
  if (behaviorFunnelChart.value) {
    chartInstances.behaviorFunnel = echarts.init(behaviorFunnelChart.value)
    initBehaviorFunnelChart()
  }
  
  // 设备分布图
  if (deviceDistributionChart.value) {
    chartInstances.deviceDistribution = echarts.init(deviceDistributionChart.value)
    initDeviceDistributionChart()
  }
}

const updateRegistrationChart = () => {
  if (!chartInstances.registration) return
  
  const days = registrationPeriod.value === '7d' ? 7 : registrationPeriod.value === '30d' ? 30 : 90
  const dates = []
  const registrations = []
  
  for (let i = days - 1; i >= 0; i--) {
    const date = new Date()
    date.setDate(date.getDate() - i)
    dates.push(date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' }))
    registrations.push(Math.floor(Math.random() * 150) + 50)
  }
  
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    xAxis: {
      type: 'category',
      data: dates
    },
    yAxis: {
      type: 'value',
      name: '注册用户数'
    },
    series: [
      {
        type: 'line',
        data: registrations,
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
  
  chartInstances.registration.setOption(option)
}

const updateActivityChart = () => {
  if (!chartInstances.activity) return
  
  const days = activityPeriod.value === '7d' ? 7 : activityPeriod.value === '30d' ? 30 : 90
  const dates = []
  const dau = []
  const mau = []
  
  for (let i = days - 1; i >= 0; i--) {
    const date = new Date()
    date.setDate(date.getDate() - i)
    dates.push(date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' }))
    dau.push(Math.floor(Math.random() * 5000) + 2000)
    mau.push(Math.floor(Math.random() * 15000) + 8000)
  }
  
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: ['日活跃用户', '月活跃用户']
    },
    xAxis: {
      type: 'category',
      data: dates
    },
    yAxis: {
      type: 'value',
      name: '活跃用户数'
    },
    series: [
      {
        name: '日活跃用户',
        type: 'line',
        data: dau,
        itemStyle: { color: '#67c23a' },
        smooth: true
      },
      {
        name: '月活跃用户',
        type: 'line',
        data: mau,
        itemStyle: { color: '#e6a23c' },
        smooth: true
      }
    ]
  }
  
  chartInstances.activity.setOption(option)
}

const initAgeDistributionChart = () => {
  if (!chartInstances.ageDistribution) return
  
  const option = {
    tooltip: {
      trigger: 'item'
    },
    xAxis: {
      type: 'category',
      data: ['18-25', '26-30', '31-35', '36-40', '41-45', '46-50', '50+']
    },
    yAxis: {
      type: 'value',
      name: '用户数量'
    },
    series: [
      {
        type: 'bar',
        data: [15420, 23650, 18930, 12450, 8760, 5420, 3210],
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#409eff' },
            { offset: 1, color: '#67c23a' }
          ])
        }
      }
    ]
  }
  
  chartInstances.ageDistribution.setOption(option)
}

const initGenderDistributionChart = () => {
  if (!chartInstances.genderDistribution) return
  
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
        name: '性别分布',
        type: 'pie',
        radius: '50%',
        data: [
          { value: 45680, name: '女性', itemStyle: { color: '#f56c6c' } },
          { value: 52340, name: '男性', itemStyle: { color: '#409eff' } },
          { value: 12860, name: '未知', itemStyle: { color: '#909399' } }
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
  
  chartInstances.genderDistribution.setOption(option)
}

const initRetentionChart = () => {
  if (!chartInstances.retention) return
  
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: ['次日留存', '7日留存', '30日留存']
    },
    xAxis: {
      type: 'category',
      data: ['第1周', '第2周', '第3周', '第4周', '第5周', '第6周', '第7周', '第8周']
    },
    yAxis: {
      type: 'value',
      name: '留存率(%)',
      max: 100
    },
    series: [
      {
        name: '次日留存',
        type: 'line',
        data: [85, 82, 78, 75, 73, 70, 68, 65],
        itemStyle: { color: '#67c23a' },
        smooth: true
      },
      {
        name: '7日留存',
        type: 'line',
        data: [65, 62, 58, 55, 52, 48, 45, 42],
        itemStyle: { color: '#e6a23c' },
        smooth: true
      },
      {
        name: '30日留存',
        type: 'line',
        data: [35, 32, 28, 25, 22, 18, 15, 12],
        itemStyle: { color: '#f56c6c' },
        smooth: true
      }
    ]
  }
  
  chartInstances.retention.setOption(option)
}

const initBehaviorFunnelChart = () => {
  if (!chartInstances.behaviorFunnel) return
  
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c}'
    },
    series: [
      {
        name: '用户行为',
        type: 'funnel',
        left: '10%',
        top: 60,
        bottom: 60,
        width: '80%',
        min: 0,
        max: 100,
        minSize: '0%',
        maxSize: '100%',
        sort: 'descending',
        gap: 2,
        label: {
          show: true,
          position: 'inside'
        },
        labelLine: {
          length: 10,
          lineStyle: {
            width: 1,
            type: 'solid'
          }
        },
        itemStyle: {
          borderColor: '#fff',
          borderWidth: 1
        },
        emphasis: {
          label: {
            fontSize: 20
          }
        },
        data: [
          { value: 100, name: '访问用户', itemStyle: { color: '#409eff' } },
          { value: 80, name: '注册用户', itemStyle: { color: '#67c23a' } },
          { value: 65, name: '活跃用户', itemStyle: { color: '#e6a23c' } },
          { value: 45, name: '发帖用户', itemStyle: { color: '#f56c6c' } },
          { value: 25, name: '付费用户', itemStyle: { color: '#909399' } }
        ]
      }
    ]
  }
  
  chartInstances.behaviorFunnel.setOption(option)
}

const initDeviceDistributionChart = () => {
  if (!chartInstances.deviceDistribution) return
  
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      top: '5%',
      left: 'center'
    },
    series: [
      {
        name: '设备类型',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['50%', '60%'],
        data: [
          { value: 65420, name: '手机', itemStyle: { color: '#409eff' } },
          { value: 28350, name: '电脑', itemStyle: { color: '#67c23a' } },
          { value: 15230, name: '平板', itemStyle: { color: '#e6a23c' } },
          { value: 8560, name: '其他', itemStyle: { color: '#f56c6c' } }
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
  
  chartInstances.deviceDistribution.setOption(option)
}

const fetchUserList = async () => {
  userListLoading.value = true
  try {
    // 模拟API调用
    await new Promise(resolve => setTimeout(resolve, 500))
    
    const mockUsers = Array.from({ length: 100 }, (_, index) => ({
      id: index + 1,
      rank: index + 1,
      username: `user${index + 1}`,
      avatar: `https://api.dicebear.com/7.x/avataaars/svg?seed=user${index + 1}`,
      score: Math.floor(Math.random() * 1000) + 100,
      postsCount: Math.floor(Math.random() * 200) + 10,
      commentsCount: Math.floor(Math.random() * 500) + 20,
      followersCount: Math.floor(Math.random() * 1000) + 5,
      followingCount: Math.floor(Math.random() * 500) + 10,
      totalViews: Math.floor(Math.random() * 10000) + 100,
      totalLikes: Math.floor(Math.random() * 5000) + 50,
      status: Math.random() > 0.1 ? 'active' : 'banned',
      lastLoginTime: new Date(Date.now() - Math.random() * 7 * 24 * 60 * 60 * 1000).toISOString(),
      registrationTime: new Date(Date.now() - Math.random() * 365 * 24 * 60 * 60 * 1000).toISOString()
    }))
    
    // 分页
    const start = (userListPagination.current - 1) * userListPagination.size
    const end = start + userListPagination.size
    
    userList.value = mockUsers.slice(start, end)
    userListPagination.total = mockUsers.length
    
  } catch (error) {
    ElMessage.error('获取用户列表失败')
  } finally {
    userListLoading.value = false
  }
}

const getScoreType = (score) => {
  if (score >= 800) return 'success'
  if (score >= 500) return 'warning'
  return 'info'
}

const formatDateTime = (date) => {
  return new Date(date).toLocaleString('zh-CN')
}

const viewUserDetail = (user) => {
  userDetailDialog.user = user
  userDetailDialog.visible = true
}

const sendMessage = (user) => {
  ElMessage.info(`发送消息给用户: ${user.username}`)
}

const exportUserList = () => {
  ElMessage.success('用户列表导出中，请稍候...')
  
  setTimeout(() => {
    ElMessage.success('用户列表导出完成！')
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
  fetchUserList()
  
  // 监听窗口大小变化
  window.addEventListener('resize', resizeCharts)
})

onUnmounted(() => {
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
.user-analysis {
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
  
  .user-list-section {
    .header-actions {
      display: flex;
      gap: 12px;
      align-items: center;
    }
    
    .pagination-wrapper {
      display: flex;
      justify-content: center;
      margin-top: 20px;
    }
  }
  
  .user-detail {
    .user-basic-info {
      display: flex;
      gap: 20px;
      align-items: center;
      margin-bottom: 20px;
      
      .user-avatar {
        flex-shrink: 0;
      }
      
      .user-info {
        h3 {
          margin: 0 0 8px 0;
          color: #303133;
          font-size: 20px;
          font-weight: 600;
        }
        
        p {
          margin: 4px 0;
          color: #606266;
          font-size: 14px;
        }
      }
    }
  }
}

@media (max-width: 768px) {
  .user-analysis {
    .charts-section {
      .admin-card {
        .card-header {
          flex-direction: column;
          gap: 12px;
          align-items: stretch;
        }
      }
    }
    
    .user-list-section {
      .header-actions {
        flex-direction: column;
        gap: 8px;
        align-items: stretch;
      }
    }
    
    .user-detail {
      .user-basic-info {
        flex-direction: column;
        text-align: center;
      }
    }
  }
}
</style>
