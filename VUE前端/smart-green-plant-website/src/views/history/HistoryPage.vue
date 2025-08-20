<script setup>
import { ref, onMounted, watch, onUnmounted, nextTick, computed } from 'vue'
import * as echarts from 'echarts'
import { getPlantInstanceId } from '@/api/plantinstance'
import { fetchEnvironmentData, fetchHistoryData, exportEnvironmentData, analyzePlantHealth } from '@/api/environment'
import { userUploadImageService } from '@/api/common'
import { useUserStore, useHistoryStore } from '@/stores'
import { ElDatePicker, ElMessage } from 'element-plus'
import { Download, Upload } from '@element-plus/icons-vue'
import { formatText } from '@/utils/formattext'

const userStore = useUserStore()
const historyStore = useHistoryStore()

// 监测花盆选择
const currentPot = ref('1号')
const plants = ref([]) // 存储植物实例数据

// 添加历史数据的响应式引用
const historyData = ref([])
const selectedDataType = ref('temperature') // 默认显示温度数据

const dataTypeOptions = [
  { 
    label: '温度', 
    value: 'temperature', 
    unit: '°C',
    color: '#FF6B6B',
    gradientColors: ['rgba(255, 107, 107, 0.3)', 'rgba(255, 107, 107, 0.1)']
  },
  { 
    label: '湿度', 
    value: 'humidity', 
    unit: '%',
    color: '#4ECDC4',
    gradientColors: ['rgba(78, 205, 196, 0.3)', 'rgba(78, 205, 196, 0.1)']
  },
  { 
    label: '土壤湿度', 
    value: 'soilMoisture', 
    unit: '%',
    color: '#45B7D1',
    gradientColors: ['rgba(69, 183, 209, 0.3)', 'rgba(69, 183, 209, 0.1)']
  },
  { 
    label: 'CO2浓度', 
    value: 'co2Concentration', 
    unit: 'pm',
    color: '#96CEB4',
    gradientColors: ['rgba(150, 206, 180, 0.3)', 'rgba(150, 206, 180, 0.1)']
  },
  { 
    label: '光照强度', 
    value: 'lightIntensity', 
    unit: 'Lx',
    color: '#FFAD60',
    gradientColors: ['rgba(255, 173, 96, 0.3)', 'rgba(255, 173, 96, 0.1)']
  }
]

// 添加分页相关的数据
const tableData = ref([])
const pagination = computed({
  get: () => historyStore.pagination,
  set: (val) => historyStore.setPagination(val)
})

// 设置默认日期范围为最近三个月
const getDefaultDateRange = () => {
  const end = new Date()
  const start = new Date()
  start.setMonth(start.getMonth() - 3) // 向前推3个月
  
  // 格式化日期为 YYYY-MM-DD HH:mm:ss
  const formatDate = (date) => {
    return date.toISOString().slice(0, 19).replace('T', ' ')
  }
  
  return [formatDate(start), formatDate(end)]
}

// 日期范围
const dateRange = computed({
  get: () => historyStore.dateRange,
  set: (val) => historyStore.setDateRange(val)
})

// 日期选择器的快捷选项
const shortcuts = [
  {
    text: '最近一周',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 24 * 7)
      return [start.toISOString().slice(0, 19).replace('T', ' '), 
              end.toISOString().slice(0, 19).replace('T', ' ')]
    }
  },
  {
    text: '最近一个月',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setMonth(start.getMonth() - 1)
      return [start.toISOString().slice(0, 19).replace('T', ' '), 
              end.toISOString().slice(0, 19).replace('T', ' ')]
    }
  },
  {
    text: '最近三个月',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setMonth(start.getMonth() - 3)
      return [start.toISOString().slice(0, 19).replace('T', ' '), 
              end.toISOString().slice(0, 19).replace('T', ' ')]
    }
  }
]

// 添加 loading 状态
const loading = ref(false)

// 植物健康分析相关数据
const analysisDays = ref(10) // 默认分析10天
const customDays = ref(10) // 自定义天数
const uploadedImageUrl = ref('') // 上传的图片URL
const analysisResult = ref('') // 分析结果
const analysisLoading = ref(false) // 分析加载状态
const uploadRef = ref(null) // 上传组件引用

// 添加当前选中的植物实例 ID
const currentPlantInstanceId = computed({
  get: () => historyStore.currentPlantInstanceId,
  set: (val) => historyStore.setCurrentPlant(val)
})

// 获取植物列表
const fetchPlants = async () => {
  try {
    const res = await getPlantInstanceId(userStore.user.id)
    if (res.data.code === '1') {
      plants.value = res.data.data.map(plant => ({
        id: plant.id,
        plantId: plant.plantId,
        plantName: plant.plantName,
        deviceName: plant.deviceName,
        location: plant.location,
        datePlanted: plant.datePlanted
      }))

      // 如果没有选中的设备，才设置默认值
      if (!currentPlantInstanceId.value && plants.value.length > 0) {
        currentPlantInstanceId.value = plants.value[0].id
      }
      
      // 如果没有日期范围，设置默认值
      if (!dateRange.value.length) {
        dateRange.value = getDefaultDateRange()
      }

      await fetchHistoryTableData()
    }
  } catch (error) {
    console.error('获取设备列表失败:', error)
    ElMessage.error('获取设备列表失败')
  }
}

// 修改获取历史数据的方法
const fetchHistoryTableData = async () => {
  if (!currentPlantInstanceId.value || !dateRange.value.length) return

  try {
    loading.value = true
    const params = {
      plantInstanceId: currentPlantInstanceId.value,
      startDate: dateRange.value[0],
      endDate: dateRange.value[1],
      page: pagination.value.currentPage,
      pageSize: pagination.value.pageSize
    }

    const res = await fetchHistoryData(params)
    if (res.data.code === '1') {
      tableData.value = res.data.data.records
      pagination.value.total = res.data.data.total
    }
  } catch (error) {
    console.error('获取历史数据失败:', error)
    ElMessage.error('获取历史数据失败')
  } finally {
    loading.value = false
  }
}

// 处理植物选择变化
const handlePlantChange = async (plantInstanceId) => {
  historyStore.setCurrentPlant(plantInstanceId)
  historyStore.setPagination({ currentPage: 1 })
  await fetchHistoryTableData()
}

const fetchChartData = async () => {
  try {
    const selectedPlant = plants.value.find(plant => plant.id === currentPlantInstanceId.value)
    if (selectedPlant) {
      const res = await fetchEnvironmentData(selectedPlant.id)
      if (res.data.code === '1') {
        historyData.value = res.data.data
        // 获取数据后初始化图表
        nextTick(() => {
          initChart()
        })
      }
    }
  } catch (error) {
    console.error('获取历史数据失败:', error)
  }
}

const initChart = () => {
  const chartDom = document.getElementById('environmentChart')
  const myChart = echarts.init(chartDom)

  // 处理数据并按日期排序
  const sortedData = [...historyData.value].sort((a, b) => {
    return new Date(a.recordedTime) - new Date(b.recordedTime)
  })

  // 获取排序后的日期数组
  const dates = sortedData.map(item => item.recordedTime.split(' ')[0])
  const currentOption = dataTypeOptions.find(option => option.value === selectedDataType.value)
  
  const option = {
    title: {
      text: `七天${currentOption.label}趋势图(每日12点左右)`,
      left: 'center'
    },
    tooltip: {
      trigger: 'axis',
      formatter: function(params) {
        const param = params[0]
        return `${param.axisValue}<br/>${currentOption.label}: ${param.value}${currentOption.unit}`
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '10%',
      top: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLabel: {
        rotate: 45
      }
    },
    yAxis: {
      type: 'value',
      name: currentOption.label,
      axisLabel: {
        formatter: `{value}${currentOption.unit}`
      }
    },
    series: [
      {
        name: currentOption.label,
        type: 'line',
        // 使用排序后的数据
        data: sortedData.map(item => item[selectedDataType.value]),
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        itemStyle: {
          color: currentOption.color
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            {
              offset: 0,
              color: currentOption.gradientColors[0]
            },
            {
              offset: 1,
              color: currentOption.gradientColors[1]
            }
          ])
        }
      }
    ]
  }

  myChart.setOption(option)
  
  window.addEventListener('resize', () => {
    myChart.resize()
  })
}

// 添加监听器，当数据类型改变时重新渲染图表
watch(selectedDataType, () => {
  nextTick(() => {
    initChart()
  })
})

// 处理页码变化
const handleCurrentChange = (val) => {
  historyStore.setPagination({ currentPage: val })
  fetchHistoryTableData()
}

// 处理每页条数变化
const handleSizeChange = (val) => {
  historyStore.setPagination({ pageSize: val, currentPage: 1 })
  fetchHistoryTableData()
}

// 处理日期范围变化
const handleDateRangeChange = (val) => {
  if (val && Array.isArray(val) && val.length === 2) {
    historyStore.setPagination({ currentPage: 1 })
    historyStore.setDateRange(val)
    fetchHistoryTableData()
  }
}

// 在组件挂载时获取数据
onMounted(async () => {
  await fetchPlants()
})

// 监听花盆选择变化
watch(currentPot, () => {
  fetchChartData()
  if (dateRange.value && dateRange.value[0] && dateRange.value[1]) {
    fetchHistoryTableData()  // 只有在已选择日期范围时才获取表格数据
  }
})

// 在组件卸载时清除图表实例
onUnmounted(() => {
  const chartDom = document.getElementById('environmentChart')
  if (chartDom) {
    const myChart = echarts.getInstanceByDom(chartDom)
    myChart && myChart.dispose()
  }
  window.removeEventListener('resize', () => {})
})

// 处理导出环境数据
const handleExport = async () => {
  if (!currentPlantInstanceId.value) {
    ElMessage.warning('请先选择植物')
    return
  }

  try {
    const selectedPlant = plants.value.find(plant => plant.id === currentPlantInstanceId.value)
    if (!selectedPlant) return

    const params = {
      id: selectedPlant.id,
      plantId: selectedPlant.plantId,
      potNumber: '1' // 暂时写死
    }

    const res = await exportEnvironmentData(params)
    if (res.data.code === '1') {
      // 将 base64 转换为 blob
      const byteCharacters = atob(res.data.data.split(',')[1])
      const byteNumbers = new Array(byteCharacters.length)
      for (let i = 0; i < byteCharacters.length; i++) {
        byteNumbers[i] = byteCharacters.charCodeAt(i)
      }
      const byteArray = new Uint8Array(byteNumbers)
      const blob = new Blob([byteArray], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })

      // 创建下载链接并点击
      const link = document.createElement('a')
      link.href = window.URL.createObjectURL(blob)
      link.download = `${selectedPlant.deviceName}环境数据.xlsx`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(link.href)

      ElMessage.success('导出成功')
    } else {
      ElMessage.error('导出失败')
    }
  } catch (error) {
    console.error('导出环境数据失败:', error)
    ElMessage.error('导出环境数据失败')
  }
}

// 处理图片上传变化
const handleImageChange = async (file) => {
  try {
    const res = await userUploadImageService(file.raw)
    if (res.data.code === '1') {
      uploadedImageUrl.value = res.data.data
      ElMessage.success('图片上传成功')
    } else {
      ElMessage.error('图片上传失败')
    }
  } catch (error) {
    console.error('图片上传失败:', error)
    ElMessage.error('图片上传失败')
  }
}

// 移除图片
const removeImage = () => {
  uploadedImageUrl.value = ''
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
}

// 开始分析
const handleAnalyze = async () => {
  if (!currentPlantInstanceId.value) {
    ElMessage.warning('请先选择植物')
    return
  }
  
  if (!uploadedImageUrl.value) {
    ElMessage.warning('请先上传植物图片')
    return
  }

  try {
    analysisLoading.value = true
    const selectedPlant = plants.value.find(plant => plant.id === currentPlantInstanceId.value)
    if (!selectedPlant) return

    const params = {
      plantInstanceId: currentPlantInstanceId.value,
      plantId: selectedPlant.plantId,
      plantImg: uploadedImageUrl.value,
      analysisDays: analysisDays.value === 0 ? customDays.value : analysisDays.value
    }

    const res = await analyzePlantHealth(params)
    if (res.data.code === '1') {
      analysisResult.value = res.data.data
      ElMessage.success('分析完成')
    } else {
      ElMessage.error('分析失败')
    }
  } catch (error) {
    console.error('植物健康分析失败:', error)
    ElMessage.error('植物健康分析失败')
  } finally {
    analysisLoading.value = false
  }
}

// 格式化日期
const formatDate = (date) => {
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}
</script>

<template>
  <div class="history-container">
    <el-card class="history-table-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="title">历史数据记录</span>
          </div>
          <div class="header-right">
            <el-date-picker
              v-model="dateRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              :shortcuts="shortcuts"
              value-format="YYYY-MM-DD HH:mm:ss"
              format="YYYY-MM-DD HH:mm:ss"
              @change="handleDateRangeChange"
            />
            <el-select
              v-model="currentPlantInstanceId"
              class="plant-select"
              size="small"
              placeholder="选择设备"
              @change="handlePlantChange"
            >
              <el-option
                v-for="plant in plants"
                :key="plant.id"
                :label="plant.deviceName"
                :value="plant.id"
              >
                <span>{{ plant.deviceName }}</span>
              </el-option>
            </el-select>
            <el-button
              type="primary"
              :icon="Download"
              @click="handleExport"
            >
              导出数据
            </el-button>
          </div>
        </div>
      </template>

      <div class="table-container">
        <el-alert
          v-if="!dateRange.length"
          title="请选择日期范围查询数据"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 20px;"
        />

        <el-empty 
          v-else-if="!tableData.length" 
          description="该时间段暂无数据" 
          :image-size="200"
        />

        <template v-else>
          <el-table 
            v-loading="loading"
            :data="tableData" 
            style="width: 100%"
            border
            stripe
            height="500"
            class="data-table"
            element-loading-text="加载中..."
            element-loading-background="rgba(255, 255, 255, 0.9)"
          >
            <el-table-column 
              prop="recordedTime" 
              label="记录时间" 
              min-width="16%"
              align="center"
            />
            <el-table-column 
              prop="temperature" 
              label="温度(°C)" 
              min-width="16%"
              align="center"
            />
            <el-table-column 
              prop="humidity" 
              label="湿度(%)" 
              min-width="16%"
              align="center"
            />
            <el-table-column 
              prop="soilMoisture" 
              label="土壤湿度(%)" 
              min-width="16%"
              align="center"
            />
            <el-table-column 
              prop="co2Concentration" 
              label="CO2浓度(pm)" 
              min-width="16%"
              align="center"
            />
            <el-table-column 
              prop="lightIntensity" 
              label="光照强度(Lx)" 
              min-width="16%"
              align="center"
            />
          </el-table>

          <div class="pagination-container">
            <el-pagination
              v-model:current-page="pagination.currentPage"
              v-model:page-size="pagination.pageSize"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              :total="pagination.total"
              @size-change="handleSizeChange"
              @current-change="handleCurrentChange"
            />
          </div>
        </template>
      </div>
    </el-card>

    <!-- 植物健康状况分析区域 -->
    <el-card class="analysis-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="title">植物健康状况分析</span>
          </div>
          <div class="header-right">
            <el-select
              v-model="analysisDays"
              size="small"
              placeholder="选择分析天数"
              style="width: 120px; margin-right: 10px;"
            >
              <el-option label="10天" :value="10" />
              <el-option label="20天" :value="20" />
              <el-option label="30天" :value="30" />
              <el-option label="自定义天数" :value="0" />
            </el-select>
            
            <el-input-number
              v-if="analysisDays === 0"
              v-model="customDays"
              :min="1"
              :max="365"
              size="small"
              placeholder="自定义天数"
              style="width: 120px; margin-right: 10px;"
            />
            
            <el-upload
              ref="uploadRef"
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleImageChange"
              accept="image/*"
              style="display: inline-block; margin-right: 10px;"
            >
              <el-button size="small" type="primary" plain>
                <el-icon><Upload /></el-icon>
                上传植物图片
              </el-button>
            </el-upload>

            <el-button
              type="primary"
              size="small"
              :loading="analysisLoading"
              :disabled="!uploadedImageUrl || !currentPlantInstanceId"
              @click="handleAnalyze"
            >
              开始分析
            </el-button>
          </div>
        </div>
      </template>

      <div class="analysis-container">
        <!-- 上传的图片预览 -->
        <div v-if="uploadedImageUrl" class="image-preview">
          <img :src="uploadedImageUrl" alt="植物图片" />
          <el-button 
            size="small" 
            type="danger" 
            plain
            @click="removeImage"
            class="remove-btn"
          >
            移除图片
          </el-button>
        </div>

        <!-- 分析结果展示 -->
        <div v-if="analysisResult" class="analysis-result">
          <div class="result-header">
            <h3>分析结果</h3>
            <span class="analysis-time">分析时间：{{ formatDate(new Date()) }}</span>
          </div>
          <div class="result-content" v-html="formatText(analysisResult)"></div>
        </div>

        <!-- 空状态 -->
        <el-empty 
          v-else
          description="请上传植物图片并选择分析天数，然后点击开始分析"
          :image-size="200"
        />
      </div>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.history-container {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 40px;

  .history-table-card {
    background: rgba(255, 255, 255, 0.9);
    backdrop-filter: blur(10px);
    border-radius: 12px;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.05);
    transition: all 0.3s ease;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 16px 0;

      .header-left {
        .title {
          font-size: 16px;
          font-weight: bold;
        }
      }

      .header-right {
        display: flex;
        align-items: center;
        gap: 16px;

        .el-date-editor {
          width: 360px;
        }

        .plant-select {
          width: 100px;
        }
      }
    }

    .table-container {
      margin: 20px;
      overflow-x: auto;  // 添加横向滚动
    }

    .data-table {
      margin: 0;
      
      :deep(.el-table__header) {
        th {
          background-color: var(--el-color-primary-light-9);
          color: var(--el-text-color-primary);
          font-weight: bold;
          white-space: nowrap;  // 防止表头换行
        }
      }

      :deep(.el-table__row) {
        td {
          padding: 8px 0;
          white-space: nowrap;  // 防止内容换行
          transition: all 0.3s ease;
        }

        // 增强鼠标悬浮效果
        &:hover {
          background-color: var(--el-color-primary-light-9) !important;
          transform: translateY(-1px);
          box-shadow: 0 4px 12px rgba(103, 194, 58, 0.2);
          cursor: pointer;
          
          td {
            background-color: transparent !important;
            color: var(--el-color-primary);
            font-weight: 500;
            text-shadow: 0 1px 2px rgba(103, 194, 58, 0.1);
          }
        }

        // 为交替行添加不同的悬浮效果
        &.el-table__row--striped:hover {
          background-color: var(--el-color-primary-light-8) !important;
          
          td {
            background-color: transparent !important;
            color: var(--el-color-primary-dark-2);
          }
        }
      }

      // 表格边框增强
      :deep(.el-table--border) {
        border: 1px solid var(--el-border-color-light);
        border-radius: 8px;
        overflow: hidden;
      }

      // 表格头部样式增强
      :deep(.el-table__header-wrapper) {
        tr th {
          border-bottom: 2px solid var(--el-color-primary-light-7);
          position: relative;
          
          &::before {
            content: '';
            position: absolute;
            bottom: 0;
            left: 0;
            right: 0;
            height: 2px;
            background: linear-gradient(90deg, 
              var(--el-color-primary-light-9), 
              var(--el-color-primary), 
              var(--el-color-primary-light-9)
            );
            opacity: 0;
            transition: opacity 0.3s ease;
          }
          
          &:hover::before {
            opacity: 1;
          }
        }
      }

      // 添加表格单元格边框悬浮效果
      :deep(.el-table td) {
        border-right: 1px solid var(--el-border-color-lighter);
        position: relative;
        
        &:hover {
          border-right-color: var(--el-color-primary-light-5);
        }
      }
    }

    .pagination-container {
      margin-top: 20px;
      display: flex;
      justify-content: flex-end;
    }
  }

  :deep(.el-loading-mask) {
    border-radius: 8px;
    
    .el-loading-spinner {
      .el-loading-text {
        color: var(--el-color-primary);
        font-size: 14px;
        margin-top: 8px;
      }
      
      .circular {
        .path {
          stroke: var(--el-color-primary);
        }
      }
    }
  }

  .plant-select {
    width: 180px;

    :deep(.el-select-dropdown__item) {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0 12px;

      .plant-info {
        font-size: 12px;
        color: #909399;
        margin-left: 8px;
      }
    }
  }

  // 植物健康分析卡片样式
  .analysis-card {
    background: rgba(255, 255, 255, 0.9);
    backdrop-filter: blur(10px);
    border-radius: 12px;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.05);
    transition: all 0.3s ease;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 16px 0;

      .header-left {
        .title {
          font-size: 16px;
          font-weight: bold;
        }
      }

      .header-right {
        display: flex;
        align-items: center;
        gap: 10px;
      }
    }

    .analysis-container {
      min-height: 300px;
      padding: 20px;

      .image-preview {
        display: flex;
        align-items: center;
        gap: 15px;
        margin-bottom: 20px;
        padding: 15px;
        background: #f8f9fa;
        border-radius: 8px;
        border: 2px dashed #e1e6f0;

        img {
          width: 80px;
          height: 80px;
          object-fit: cover;
          border-radius: 8px;
          border: 2px solid #e1e6f0;
        }

        .remove-btn {
          margin-left: auto;
        }
      }

      .analysis-result {
        .result-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 20px;
          padding-bottom: 10px;
          border-bottom: 1px solid #e1e6f0;

          h3 {
            margin: 0;
            color: var(--el-color-primary);
          }

          .analysis-time {
            font-size: 12px;
            color: #909399;
          }
        }

        .result-content {
          line-height: 1.8;
          color: #333;

          :deep(h1) {
            color: var(--el-color-primary);
            font-size: 24px;
            margin: 25px 0 15px 0;
            padding-bottom: 8px;
            border-bottom: 3px solid var(--el-color-primary);
          }

          :deep(h2) {
            color: var(--el-color-primary);
            font-size: 20px;
            margin: 20px 0 12px 0;
            padding-bottom: 6px;
            border-bottom: 2px solid var(--el-color-primary-light-5);
          }

          :deep(h3) {
            color: var(--el-color-primary);
            font-size: 18px;
            margin: 20px 0 10px 0;
            padding-bottom: 5px;
            border-bottom: 2px solid var(--el-color-primary-light-8);
          }

          :deep(h4) {
            color: var(--el-color-primary);
            font-size: 16px;
            margin: 15px 0 8px 0;
            font-weight: 600;
          }

          :deep(h5) {
            color: var(--el-color-primary-light-3);
            font-size: 14px;
            margin: 12px 0 6px 0;
            font-weight: 600;
          }

          :deep(p) {
            margin: 12px 0;
            text-align: justify;
            text-indent: 0;
          }

          :deep(ul) {
            margin: 15px 0;
            padding-left: 25px;

            li {
              margin: 8px 0;
              list-style-type: disc;
              line-height: 1.6;
              
              // 嵌套列表样式
              ul {
                margin: 5px 0;
                padding-left: 20px;
                
                li {
                  margin: 4px 0;
                  list-style-type: circle;
                }
              }
              
              ol {
                margin: 5px 0;
                padding-left: 20px;
                
                li {
                  margin: 4px 0;
                  list-style-type: lower-alpha;
                }
              }
            }
          }

          :deep(ol) {
            margin: 15px 0;
            padding-left: 25px;
            counter-reset: none; // 允许自定义计数器

            li {
              margin: 8px 0;
              list-style-type: decimal;
              line-height: 1.6;
              display: list-item;
              
              // 嵌套列表样式
              ul {
                margin: 5px 0;
                padding-left: 20px;
                
                li {
                  margin: 4px 0;
                  list-style-type: disc;
                }
              }
              
              ol {
                margin: 5px 0;
                padding-left: 20px;
                
                li {
                  margin: 4px 0;
                  list-style-type: lower-roman;
                }
              }
            }
            
            // 确保 value 属性生效
            li[value] {
              counter-reset: list-item;
            }
          }

          :deep(strong) {
            color: var(--el-color-primary);
            font-weight: 600;
          }

          :deep(em) {
            color: var(--el-color-primary-light-3);
            font-style: italic;
          }

          :deep(code) {
            background-color: #f5f7fa;
            color: #e74c3c;
            padding: 2px 6px;
            border-radius: 4px;
            font-family: 'Courier New', monospace;
            font-size: 0.9em;
          }

          :deep(pre) {
            background-color: #f8f9fa;
            border: 1px solid #e9ecef;
            border-radius: 6px;
            padding: 15px;
            margin: 15px 0;
            overflow-x: auto;

            code {
              background: none;
              color: #333;
              padding: 0;
              border-radius: 0;
              font-size: 0.9em;
              line-height: 1.5;
            }
          }

          :deep(blockquote) {
            background-color: #f8f9fa;
            border-left: 4px solid var(--el-color-primary);
            margin: 15px 0;
            padding: 12px 20px;
            font-style: italic;
            color: #666;
          }

          :deep(hr) {
            border: none;
            height: 1px;
            background: linear-gradient(to right, transparent, var(--el-color-primary-light-7), transparent);
            margin: 25px 0;
          }

          :deep(a) {
            color: var(--el-color-primary);
            text-decoration: none;
            border-bottom: 1px dotted var(--el-color-primary);
            transition: all 0.3s ease;

            &:hover {
              color: var(--el-color-primary-light-3);
              border-bottom-style: solid;
            }
          }
        }
      }
    }
  }
}
</style>
