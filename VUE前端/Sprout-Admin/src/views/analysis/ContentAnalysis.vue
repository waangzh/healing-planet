<template>
  <div class="content-analysis">
    <div class="page-header">
      <h2 class="page-title">内容分析</h2>
    </div>
    
    <!-- 内容概览 -->
    <div class="overview-section">
      <el-row :gutter="20">
        <el-col :xs="24" :sm="12" :md="6">
          <div class="metric-card primary">
            <div class="metric-icon">
              <el-icon><Document /></el-icon>
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
          <div class="metric-card success">
            <div class="metric-icon">
              <el-icon><ChatDotRound /></el-icon>
            </div>
            <div class="metric-content">
              <div class="metric-value">{{ overview.totalComments.toLocaleString() }}</div>
              <div class="metric-label">总评论数</div>
              <div class="metric-change positive">
                <el-icon><ArrowUp /></el-icon>
                +{{ overview.commentGrowth }}%
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
              <el-icon><Star /></el-icon>
            </div>
            <div class="metric-content">
              <div class="metric-value">{{ overview.totalLikes.toLocaleString() }}</div>
              <div class="metric-label">总点赞数</div>
              <div class="metric-change positive">
                <el-icon><ArrowUp /></el-icon>
                +{{ overview.likeGrowth }}%
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
    
    <!-- 内容分析图表 -->
    <div class="charts-section">
      <el-row :gutter="20">
        <!-- 内容发布趋势 -->
        <el-col :xs="24" :lg="12">
          <div class="admin-card">
            <div class="card-header">
              <h3>内容发布趋势</h3>
              <el-radio-group v-model="publishTrendPeriod" size="small" @change="updatePublishTrendChart">
                <el-radio-button label="7d">7天</el-radio-button>
                <el-radio-button label="30d">30天</el-radio-button>
                <el-radio-button label="90d">90天</el-radio-button>
              </el-radio-group>
            </div>
            <div class="card-body">
              <div ref="publishTrendChart" style="height: 300px;"></div>
            </div>
          </div>
        </el-col>
        
        <!-- 内容互动趋势 -->
        <el-col :xs="24" :lg="12">
          <div class="admin-card">
            <div class="card-header">
              <h3>内容互动趋势</h3>
              <el-radio-group v-model="interactionTrendPeriod" size="small" @change="updateInteractionTrendChart">
                <el-radio-button label="7d">7天</el-radio-button>
                <el-radio-button label="30d">30天</el-radio-button>
                <el-radio-button label="90d">90天</el-radio-button>
              </el-radio-group>
            </div>
            <div class="card-body">
              <div ref="interactionTrendChart" style="height: 300px;"></div>
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
        
        <!-- 热门标签云 -->
        <el-col :xs="24" :lg="12">
          <div class="admin-card">
            <div class="card-header">
              <h3>热门标签云</h3>
            </div>
            <div class="card-body">
              <div ref="tagCloudChart" style="height: 300px;"></div>
            </div>
          </div>
        </el-col>
        
        <!-- 内容质量分析 -->
        <el-col :xs="24">
          <div class="admin-card">
            <div class="card-header">
              <h3>内容质量分析</h3>
            </div>
            <div class="card-body">
              <div ref="contentQualityChart" style="height: 400px;"></div>
            </div>
          </div>
        </el-col>
        
        <!-- 发布时间分布 -->
        <el-col :xs="24" :lg="12">
          <div class="admin-card">
            <div class="card-header">
              <h3>发布时间分布</h3>
            </div>
            <div class="card-body">
              <div ref="publishTimeChart" style="height: 350px;"></div>
            </div>
          </div>
        </el-col>
        
        <!-- 内容长度分布 -->
        <el-col :xs="24" :lg="12">
          <div class="admin-card">
            <div class="card-header">
              <h3>内容长度分布</h3>
            </div>
            <div class="card-body">
              <div ref="contentLengthChart" style="height: 350px;"></div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
    
    <!-- 热门内容排行 -->
    <div class="popular-content-section">
      <div class="admin-card">
        <div class="card-header">
          <h3>热门内容排行</h3>
          <div class="header-actions">
            <el-select v-model="popularType" placeholder="选择类型" style="width: 150px" @change="fetchPopularContent">
              <el-option label="浏览量排行" value="views" />
              <el-option label="点赞数排行" value="likes" />
              <el-option label="评论数排行" value="comments" />
              <el-option label="分享数排行" value="shares" />
            </el-select>
            <el-button type="primary" @click="exportPopularContent">导出数据</el-button>
          </div>
        </div>
        <div class="card-body">
          <el-table :data="popularContent" v-loading="popularContentLoading" stripe>
            <el-table-column prop="rank" label="排名" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.rank <= 3" :type="getRankType(row.rank)" size="small">
                  {{ row.rank }}
                </el-tag>
                <span v-else>{{ row.rank }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="200">
              <template #default="{ row }">
                <div class="content-preview">
                  <h4 class="content-title">{{ row.title }}</h4>
                  <p class="content-excerpt">{{ row.excerpt }}</p>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="author" label="作者" width="120">
              <template #default="{ row }">
                <div class="author-info">
                  <el-avatar :src="row.author.avatar" :size="24" />
                  <span>{{ row.author.username }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="type" label="类型" width="100">
              <template #default="{ row }">
                <el-tag :type="getContentTypeTag(row.type)" size="small">
                  {{ getContentTypeText(row.type) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="views" label="浏览量" width="100" />
            <el-table-column prop="likes" label="点赞数" width="80" />
            <el-table-column prop="comments" label="评论数" width="80" />
            <el-table-column prop="publishTime" label="发布时间" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.publishTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button type="text" size="small" @click="viewContent(row)">
                  查看
                </el-button>
                <el-button type="text" size="small" @click="analyzeContent(row)">
                  分析
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="popularContentPagination.current"
              v-model:page-size="popularContentPagination.size"
              :total="popularContentPagination.total"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="fetchPopularContent"
              @current-change="fetchPopularContent"
            />
          </div>
        </div>
      </div>
    </div>
    
    <!-- 内容详情对话框 -->
    <el-dialog
      v-model="contentDetailDialog.visible"
      title="内容详情"
      width="800px"
      destroy-on-close
    >
      <div v-if="contentDetailDialog.content" class="content-detail">
        <div class="content-header">
          <h3>{{ contentDetailDialog.content.title }}</h3>
          <div class="content-meta">
            <el-tag :type="getContentTypeTag(contentDetailDialog.content.type)">
              {{ getContentTypeText(contentDetailDialog.content.type) }}
            </el-tag>
            <span class="publish-time">发布时间: {{ formatDateTime(contentDetailDialog.content.publishTime) }}</span>
          </div>
        </div>
        
        <div class="content-author">
          <el-avatar :src="contentDetailDialog.content.author.avatar" :size="40" />
          <div class="author-info">
            <h4>{{ contentDetailDialog.content.author.username }}</h4>
            <p>作者ID: {{ contentDetailDialog.content.author.id }}</p>
          </div>
        </div>
        
        <div class="content-body">
          <p>{{ contentDetailDialog.content.content }}</p>
        </div>
        
        <div class="content-stats">
          <el-row :gutter="20">
            <el-col :span="6">
              <div class="stat-item">
                <div class="stat-value">{{ contentDetailDialog.content.views }}</div>
                <div class="stat-label">浏览量</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="stat-item">
                <div class="stat-value">{{ contentDetailDialog.content.likes }}</div>
                <div class="stat-label">点赞数</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="stat-item">
                <div class="stat-value">{{ contentDetailDialog.content.comments }}</div>
                <div class="stat-label">评论数</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="stat-item">
                <div class="stat-value">{{ contentDetailDialog.content.shares }}</div>
                <div class="stat-label">分享数</div>
              </div>
            </el-col>
          </el-row>
        </div>
        
        <div class="content-tags">
          <h5>标签:</h5>
          <el-tag
            v-for="tag in contentDetailDialog.content.tags"
            :key="tag"
            type="info"
            size="small"
            style="margin-right: 8px;"
          >
            {{ tag }}
          </el-tag>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { 
  Document,
  ChatDotRound,
  View,
  Star,
  ArrowUp
} from '@element-plus/icons-vue'

// 响应式数据
const publishTrendChart = ref()
const interactionTrendChart = ref()
const contentTypeChart = ref()
const tagCloudChart = ref()
const contentQualityChart = ref()
const publishTimeChart = ref()
const contentLengthChart = ref()

const publishTrendPeriod = ref('30d')
const interactionTrendPeriod = ref('30d')
const popularType = ref('views')
const popularContentLoading = ref(false)

let chartInstances = {}

const overview = reactive({
  totalPosts: 45320,
  postGrowth: 8.7,
  totalComments: 156780,
  commentGrowth: 12.3,
  totalViews: 2345678,
  viewGrowth: 15.8,
  totalLikes: 892340,
  likeGrowth: 9.2
})

const popularContent = ref([])
const popularContentPagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

const contentDetailDialog = reactive({
  visible: false,
  content: null
})

// 方法
const initCharts = async () => {
  await nextTick()
  
  // 内容发布趋势图
  if (publishTrendChart.value) {
    chartInstances.publishTrend = echarts.init(publishTrendChart.value)
    updatePublishTrendChart()
  }
  
  // 内容互动趋势图
  if (interactionTrendChart.value) {
    chartInstances.interactionTrend = echarts.init(interactionTrendChart.value)
    updateInteractionTrendChart()
  }
  
  // 内容类型分布图
  if (contentTypeChart.value) {
    chartInstances.contentType = echarts.init(contentTypeChart.value)
    initContentTypeChart()
  }
  
  // 标签云图
  if (tagCloudChart.value) {
    chartInstances.tagCloud = echarts.init(tagCloudChart.value)
    initTagCloudChart()
  }
  
  // 内容质量分析图
  if (contentQualityChart.value) {
    chartInstances.contentQuality = echarts.init(contentQualityChart.value)
    initContentQualityChart()
  }
  
  // 发布时间分布图
  if (publishTimeChart.value) {
    chartInstances.publishTime = echarts.init(publishTimeChart.value)
    initPublishTimeChart()
  }
  
  // 内容长度分布图
  if (contentLengthChart.value) {
    chartInstances.contentLength = echarts.init(contentLengthChart.value)
    initContentLengthChart()
  }
}

const updatePublishTrendChart = () => {
  if (!chartInstances.publishTrend) return
  
  const days = publishTrendPeriod.value === '7d' ? 7 : publishTrendPeriod.value === '30d' ? 30 : 90
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
      data: ['新增帖子', '新增评论']
    },
    xAxis: {
      type: 'category',
      data: dates
    },
    yAxis: {
      type: 'value',
      name: '数量'
    },
    series: [
      {
        name: '新增帖子',
        type: 'bar',
        data: posts,
        itemStyle: { color: '#409eff' }
      },
      {
        name: '新增评论',
        type: 'line',
        data: comments,
        itemStyle: { color: '#67c23a' },
        smooth: true
      }
    ]
  }
  
  chartInstances.publishTrend.setOption(option)
}

const updateInteractionTrendChart = () => {
  if (!chartInstances.interactionTrend) return
  
  const days = interactionTrendPeriod.value === '7d' ? 7 : interactionTrendPeriod.value === '30d' ? 30 : 90
  const dates = []
  const likes = []
  const shares = []
  const views = []
  
  for (let i = days - 1; i >= 0; i--) {
    const date = new Date()
    date.setDate(date.getDate() - i)
    dates.push(date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' }))
    likes.push(Math.floor(Math.random() * 1000) + 200)
    shares.push(Math.floor(Math.random() * 200) + 50)
    views.push(Math.floor(Math.random() * 5000) + 1000)
  }
  
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: ['点赞', '分享', '浏览']
    },
    xAxis: {
      type: 'category',
      data: dates
    },
    yAxis: [
      {
        type: 'value',
        name: '点赞/分享',
        position: 'left'
      },
      {
        type: 'value',
        name: '浏览量',
        position: 'right'
      }
    ],
    series: [
      {
        name: '点赞',
        type: 'line',
        data: likes,
        itemStyle: { color: '#f56c6c' },
        smooth: true
      },
      {
        name: '分享',
        type: 'line',
        data: shares,
        itemStyle: { color: '#e6a23c' },
        smooth: true
      },
      {
        name: '浏览',
        type: 'line',
        yAxisIndex: 1,
        data: views,
        itemStyle: { color: '#409eff' },
        smooth: true
      }
    ]
  }
  
  chartInstances.interactionTrend.setOption(option)
}

const initContentTypeChart = () => {
  if (!chartInstances.contentType) return
  
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
        }
      }
    ]
  }
  
  chartInstances.contentType.setOption(option)
}

const initTagCloudChart = () => {
  if (!chartInstances.tagCloud) return
  
  const tags = [
    '绿植', '多肉', '花卉', '盆栽', '室内植物',
    '浇水', '施肥', '换盆', '修剪', '病虫害',
    '阳台种植', '办公室绿植', '新手入门', '经验分享', '植物护理'
  ]
  
  const data = tags.map(tag => ({
    name: tag,
    value: Math.floor(Math.random() * 100) + 20
  }))
  
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c}次提及'
    },
    series: [
      {
        type: 'wordCloud',
        gridSize: 20,
        sizeRange: [12, 50],
        rotationRange: [-90, 90],
        shape: 'pentagon',
        width: '100%',
        height: '100%',
        drawOutOfBound: true,
        textStyle: {
          color: () => {
            const colors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399']
            return colors[Math.floor(Math.random() * colors.length)]
          }
        },
        data: data
      }
    ]
  }
  
  chartInstances.tagCloud.setOption(option)
}

const initContentQualityChart = () => {
  if (!chartInstances.contentQuality) return
  
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: ['高质量内容', '中质量内容', '低质量内容']
    },
    xAxis: {
      type: 'category',
      data: ['第1周', '第2周', '第3周', '第4周', '第5周', '第6周', '第7周', '第8周']
    },
    yAxis: {
      type: 'value',
      name: '内容数量'
    },
    series: [
      {
        name: '高质量内容',
        type: 'bar',
        stack: 'quality',
        data: [120, 132, 101, 134, 90, 230, 210, 182],
        itemStyle: { color: '#67c23a' }
      },
      {
        name: '中质量内容',
        type: 'bar',
        stack: 'quality',
        data: [220, 182, 191, 234, 290, 330, 310, 283],
        itemStyle: { color: '#e6a23c' }
      },
      {
        name: '低质量内容',
        type: 'bar',
        stack: 'quality',
        data: [150, 232, 201, 154, 190, 330, 410, 302],
        itemStyle: { color: '#f56c6c' }
      }
    ]
  }
  
  chartInstances.contentQuality.setOption(option)
}

const initPublishTimeChart = () => {
  if (!chartInstances.publishTime) return
  
  const hours = Array.from({ length: 24 }, (_, i) => i)
  const data = hours.map(hour => Math.floor(Math.random() * 100) + 20)
  
  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: '{b}点: {c}篇'
    },
    xAxis: {
      type: 'category',
      data: hours.map(h => `${h}:00`),
      axisLabel: {
        interval: 2
      }
    },
    yAxis: {
      type: 'value',
      name: '发布数量'
    },
    series: [
      {
        type: 'line',
        data: data,
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
  
  chartInstances.publishTime.setOption(option)
}

const initContentLengthChart = () => {
  if (!chartInstances.contentLength) return
  
  const option = {
    tooltip: {
      trigger: 'item'
    },
    xAxis: {
      type: 'category',
      data: ['0-50字', '51-100字', '101-200字', '201-500字', '500字以上']
    },
    yAxis: {
      type: 'value',
      name: '内容数量'
    },
    series: [
      {
        type: 'bar',
        data: [2340, 4560, 7890, 5640, 2130],
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#67c23a' },
            { offset: 1, color: '#409eff' }
          ])
        }
      }
    ]
  }
  
  chartInstances.contentLength.setOption(option)
}

const fetchPopularContent = async () => {
  popularContentLoading.value = true
  try {
    // 模拟API调用
    await new Promise(resolve => setTimeout(resolve, 500))
    
    const contentTypes = ['plant_share', 'experience', 'qa', 'tools', 'other']
    const mockContent = Array.from({ length: 100 }, (_, index) => ({
      id: index + 1,
      rank: index + 1,
      title: `热门内容标题${index + 1}`,
      excerpt: `这是内容摘要${index + 1}，包含了主要的内容概述...`,
      content: `这是完整的内容正文${index + 1}，详细描述了相关的植物知识和经验分享...`,
      author: {
        id: Math.floor(Math.random() * 1000) + 1,
        username: `author${index + 1}`,
        avatar: `https://api.dicebear.com/7.x/avataaars/svg?seed=author${index + 1}`
      },
      type: contentTypes[index % contentTypes.length],
      views: Math.floor(Math.random() * 10000) + 1000,
      likes: Math.floor(Math.random() * 1000) + 100,
      comments: Math.floor(Math.random() * 500) + 50,
      shares: Math.floor(Math.random() * 200) + 20,
      tags: ['绿植', '多肉', '种植经验'].slice(0, Math.floor(Math.random() * 3) + 1),
      publishTime: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString()
    }))
    
    // 根据类型排序
    if (popularType.value === 'views') {
      mockContent.sort((a, b) => b.views - a.views)
    } else if (popularType.value === 'likes') {
      mockContent.sort((a, b) => b.likes - a.likes)
    } else if (popularType.value === 'comments') {
      mockContent.sort((a, b) => b.comments - a.comments)
    } else if (popularType.value === 'shares') {
      mockContent.sort((a, b) => b.shares - a.shares)
    }
    
    // 重新设置排名
    mockContent.forEach((item, index) => {
      item.rank = index + 1
    })
    
    // 分页
    const start = (popularContentPagination.current - 1) * popularContentPagination.size
    const end = start + popularContentPagination.size
    
    popularContent.value = mockContent.slice(start, end)
    popularContentPagination.total = mockContent.length
    
  } catch (error) {
    ElMessage.error('获取热门内容失败')
  } finally {
    popularContentLoading.value = false
  }
}

const getRankType = (rank) => {
  if (rank === 1) return 'danger'
  if (rank === 2) return 'warning'
  if (rank === 3) return 'success'
  return 'info'
}

const getContentTypeTag = (type) => {
  const typeMap = {
    plant_share: 'success',
    experience: 'primary',
    qa: 'warning',
    tools: 'info',
    other: ''
  }
  return typeMap[type] || 'info'
}

const getContentTypeText = (type) => {
  const textMap = {
    plant_share: '植物分享',
    experience: '种植经验',
    qa: '问答求助',
    tools: '园艺工具',
    other: '其他'
  }
  return textMap[type] || '未知'
}

const formatDateTime = (date) => {
  return new Date(date).toLocaleString('zh-CN')
}

const viewContent = (content) => {
  contentDetailDialog.content = content
  contentDetailDialog.visible = true
}

const analyzeContent = (content) => {
  ElMessage.info(`分析内容: ${content.title}`)
}

const exportPopularContent = () => {
  ElMessage.success('热门内容数据导出中，请稍候...')
  
  setTimeout(() => {
    ElMessage.success('热门内容数据导出完成！')
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
  fetchPopularContent()
  
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
.content-analysis {
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
  
  .popular-content-section {
    .header-actions {
      display: flex;
      gap: 12px;
      align-items: center;
    }
    
    .content-preview {
      .content-title {
        margin: 0 0 8px 0;
        color: #303133;
        font-size: 14px;
        font-weight: 500;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      
      .content-excerpt {
        margin: 0;
        color: #606266;
        font-size: 12px;
        line-height: 1.4;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }
    
    .author-info {
      display: flex;
      align-items: center;
      gap: 8px;
      
      span {
        font-size: 14px;
        color: #606266;
      }
    }
    
    .pagination-wrapper {
      display: flex;
      justify-content: center;
      margin-top: 20px;
    }
  }
  
  .content-detail {
    .content-header {
      margin-bottom: 20px;
      
      h3 {
        margin: 0 0 12px 0;
        color: #303133;
        font-size: 18px;
        font-weight: 600;
      }
      
      .content-meta {
        display: flex;
        align-items: center;
        gap: 16px;
        
        .publish-time {
          color: #909399;
          font-size: 14px;
        }
      }
    }
    
    .content-author {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 20px;
      padding: 16px;
      background: #f8f9fa;
      border-radius: 8px;
      
      .author-info {
        h4 {
          margin: 0 0 4px 0;
          color: #303133;
          font-size: 16px;
          font-weight: 500;
        }
        
        p {
          margin: 0;
          color: #909399;
          font-size: 12px;
        }
      }
    }
    
    .content-body {
      margin-bottom: 24px;
      padding: 16px;
      background: #fafafa;
      border-radius: 8px;
      border-left: 4px solid #409eff;
      
      p {
        margin: 0;
        color: #606266;
        line-height: 1.6;
      }
    }
    
    .content-stats {
      margin-bottom: 24px;
      
      .stat-item {
        text-align: center;
        padding: 16px;
        background: #f0f9ff;
        border-radius: 8px;
        
        .stat-value {
          font-size: 24px;
          font-weight: 600;
          color: #409eff;
          margin-bottom: 8px;
        }
        
        .stat-label {
          font-size: 14px;
          color: #606266;
        }
      }
    }
    
    .content-tags {
      h5 {
        margin: 0 0 12px 0;
        color: #303133;
        font-size: 14px;
        font-weight: 600;
      }
    }
  }
}

@media (max-width: 768px) {
  .content-analysis {
    .charts-section {
      .admin-card {
        .card-header {
          flex-direction: column;
          gap: 12px;
          align-items: stretch;
        }
      }
    }
    
    .popular-content-section {
      .header-actions {
        flex-direction: column;
        gap: 8px;
        align-items: stretch;
      }
    }
    
    .content-detail {
      .content-stats {
        .el-col {
          margin-bottom: 12px;
        }
      }
    }
  }
}
</style>
