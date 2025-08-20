<script setup>
import { ref, onMounted, watch, onUnmounted, computed, nextTick } from 'vue'
import { fetchDeviceControl, getDeviceThreshold, getDeviceByUserId, getDeviceStatus } from '@/api/deviceControl'
import { getPlantAdvice } from '@/api/plantinstance'
import { useUserStore, useDeviceStatusStore } from '@/stores'
import { useRouter, useRoute } from 'vue-router'
import { fetchWeatherNow, fetchThreeDayForecast } from '@/api/weather'
import { ElMessageBox, ElMessage } from 'element-plus'
import { marked } from 'marked'
import sunnyIcon from '@/assets/icons/sunny.svg'
import cloudyIcon from '@/assets/icons/cloudy.svg'
import rainyIcon from '@/assets/icons/rainy.svg'
import overcastIcon from '@/assets/icons/overcast.svg'
import { fetchEnvironmentData } from '@/api/environment'
import * as echarts from 'echarts'
import { Calendar } from '@element-plus/icons-vue'
// import { Upload } from '@element-plus/icons-vue'
import { useDeviceStore } from '@/stores'
// import { checkDisease } from '@/api/check'

const userStore = useUserStore()
const deviceStatusStore = useDeviceStatusStore()
const deviceStore = useDeviceStore()

// 监测花盆选择
const currentPot = ref('1号')
const plants = ref([]) // 存储植物实例数据
const deviceName = ref('')

// 添加天气数据
const weatherData = ref({
  current: {
    temp: '--',
    weather: '--',
    humidity: '--',
    windSpeed: '--',
    windDir: '--'
  },
  forecast: []
})

// 使用 computed 从 store 中获取监测数据
const monitorData = computed(() => deviceStatusStore.monitorData)

// 添加历史环境数据相关的变量
const historyData = ref([])
const selectedDataType = ref('temperature')
const selectedDays = ref(7) // 默认7天
const customDays = ref(7) // 自定义天数

// 天数选择选项
const daysOptions = [
  { label: '7天', value: 7 },
  { label: '14天', value: 14 },
  { label: '21天', value: 21 },
  { label: '自定义天数', value: 0 }
]

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

// 添加在线状态变量
const online = ref('离线')

// 修改获取设备列表的方法
const fetchPlants = async () => {
  try {
    const res = await getDeviceByUserId(userStore.user.id)
    if (res.data.code === '1') {
      const processedPlants = res.data.data.map(device => ({
        id: device.id,
        plantId: device.plantInstanceId,
        displayId: device.name,
        name: device.name
      }))
      plants.value = processedPlants

      // 如果有存储的设备且该设备存在于当前设备列表中
      const savedDevice = deviceStore.currentDevice
      const deviceExists = plants.value.find(p => p.name === savedDevice)
      
      if (savedDevice && deviceExists) {
        currentPot.value = savedDevice
        deviceName.value = savedDevice
      } else if (processedPlants.length > 0) {
        // 如果没有存储的设备或设备不存在，使用第一个设备
        const defaultDevice = processedPlants[0].name
        currentPot.value = defaultDevice
        deviceName.value = defaultDevice
        deviceStore.setCurrentDevice(defaultDevice)
      }
      fetchStatus()
    }
  } catch (error) {
    console.error('获取设备信息失败:', error)
  }
}

// 添加获取设备状态的方法
const fetchStatus = async () => {
  try {
    const res = await getDeviceStatus(deviceName.value)
      if (res.data.code === '1') {
      online.value = res.data.data === 'ONLINE' ? '在线' : '离线'
      console.log('设备状态：', deviceName.value, online.value)
    }
  } catch (error) {
    console.error('获取设备状态失败:', error)
  }
}

// 修改设备切换处理函数
const handlePlantChange = async (val) => {
  deviceStore.setCurrentDevice(val)
  currentPot.value = val
  deviceName.value = val
  
  const selectedDevice = plants.value.find(device => device.name === val)
  if (selectedDevice) {
    // 立即获取新设备的状态
    await fetchStatus()
    await fetchDeviceThreshold(selectedDevice.id)

    // 清除旧的定时器
        if (timer) {
          clearInterval(timer)
        }
    // 设置新的定时器
        timer = setInterval(() => {
          fetchDeviceData()
      fetchStatus()
        }, 2000)
  }
}

// 修改获取设备数据的方法
const fetchDeviceData = async () => {
  try {
    const res = await fetchDeviceControl(deviceName.value)
    if (res.data.code === '1') {
      const data = res.data.data.body.data.list.propertyStatusInfo
      console.log(data);
      
      // 更新温度数据
      const temperature = findPropertyValue(data, 'Temperature')
      console.log(temperature);
      
      if (temperature) {
        const tempValue = parseFloat(temperature.value)
        console.log(tempValue);
        
        if (!isNaN(tempValue)) {
          deviceStatusStore.updateMonitorData('temperature', tempValue)
          updateMonitorStatus('temperature', tempValue)
        }
      }

      // 更新空气湿度
      const humidity = findPropertyValue(data, 'EnvironmentHumidity')
      if (humidity) {
        const humidityValue = parseFloat(humidity.value)
        if (!isNaN(humidityValue)) {
          deviceStatusStore.updateMonitorData('humidity', humidityValue)
          updateMonitorStatus('humidity', humidityValue)
        }
      }

      // 更新土壤湿度
      const soilMoisture = findPropertyValue(data, 'SoilMoisture')
      if (soilMoisture) {
        const soilValue = parseFloat(soilMoisture.value)
        if (!isNaN(soilValue)) {
          deviceStatusStore.updateMonitorData('soilMoisture', soilValue)
          updateMonitorStatus('soilMoisture', soilValue)
        }
      }

      // 更新CO2浓度
      const co2 = findPropertyValue(data, 'CO2Value')
      if (co2) {
        const co2Value = parseFloat(co2.value)
        if (!isNaN(co2Value)) {
          deviceStatusStore.updateMonitorData('co2', co2Value)
          updateMonitorStatus('co2', co2Value)
        }
      }

      // 更新光照强度
      const lightLux = findPropertyValue(data, 'LightLux')
      if (lightLux) {
        const lightValue = parseFloat(lightLux.value)
        if (!isNaN(lightValue)) {
          deviceStatusStore.updateMonitorData('lightLux', lightValue)
          updateMonitorStatus('lightLux', lightValue)
        }
      }
    }
  } catch (error) {
    console.error('获取设备数据失败:', error)
  }
}

// 更新监测数据状态
const updateMonitorStatus = (key, value) => {
  const item = monitorData.value[key]
  const numValue = parseFloat(value)

  // 判断状态
  if (numValue < item.min) {
    deviceStatusStore.updateMonitorStatus(key, 'low')
  } else if (numValue > item.max) {
    deviceStatusStore.updateMonitorStatus(key, 'high')
  } else {
    deviceStatusStore.updateMonitorStatus(key, 'normal')
  }
}

// 查找属性值
const findPropertyValue = (propertyList, identifier) => {
  return propertyList.find(item => item.identifier === identifier)
}

// 定时获取数据
let timer = null

// 获取天气数据
const fetchWeather = async () => {
  try {
    // 获取实时天气
    const nowRes = await fetchWeatherNow()
    if (nowRes.data.code === '200') {
      const now = nowRes.data.now
      weatherData.value.current = {
        temp: now.temp,
        weather: now.text,
        humidity: now.humidity,
        windSpeed: '--', // API中没有风速数据
        windDir: now.windDir
      }
    }

    // 获取天气预报
    const forecastRes = await fetchThreeDayForecast()
    if (forecastRes.data.code === '200') {
      weatherData.value.forecast = forecastRes.data.daily.map(day => ({
        date: formatDate(day.fxDate),
        dayWeather: day.textDay,
        nightWeather: day.textNight,
        dayTemp: day.tempMax,
        nightTemp: day.tempMin,
        humidity: day.humidity,
        precip: day.precip
      }))
    }
  } catch (error) {
    console.error('获取天气数据失败:', error)
  }
}

// 添加日期格式化函数
const formatDate = (dateStr) => {
  const date = new Date(dateStr)
  const month = date.getMonth() + 1
  const day = date.getDate()
  return `${month}/${day}`
}

// 添加获取设备阈值的方法
const fetchDeviceThreshold = async (id) => {
  try {
    const res = await getDeviceThreshold(id)
    if (res.data.code === '1') {
      const thresholdData = res.data.data
      // 更新监测数据的范围
      monitorData.value.temperature.range = `${thresholdData.temperatureMin}°C-${thresholdData.temperatureMax}°C`
      monitorData.value.temperature.min = thresholdData.temperatureMin
      monitorData.value.temperature.max = thresholdData.temperatureMax

      monitorData.value.humidity.range = `${thresholdData.humidityMin}%-${thresholdData.humidityMax}%`
      monitorData.value.humidity.min = thresholdData.humidityMin
      monitorData.value.humidity.max = thresholdData.humidityMax

      monitorData.value.soilMoisture.range = `${thresholdData.soilMoistureMin}%-${thresholdData.soilMoistureMax}%`
      monitorData.value.soilMoisture.min = thresholdData.soilMoistureMin
      monitorData.value.soilMoisture.max = thresholdData.soilMoistureMax

      monitorData.value.co2.range = `${thresholdData.co2Min}pm-${thresholdData.co2Max}pm`
      monitorData.value.co2.min = thresholdData.co2Min
      monitorData.value.co2.max = thresholdData.co2Max

      monitorData.value.lightLux.range = `${thresholdData.lightIntensityMin}Lx-${thresholdData.lightIntensityMax}Lx`
      monitorData.value.lightLux.min = thresholdData.lightIntensityMin
      monitorData.value.lightLux.max = thresholdData.lightIntensityMax
    }
  } catch (error) {
    console.error('获取设备阈值失败:', error)
  }
}

// 获取历史数据图表
const fetchChartData = async () => {
  try {
    const selectedPlant = plants.value.find(plant => plant.displayId === currentPot.value)
    if (selectedPlant) {
      const days = selectedDays.value === 0 ? customDays.value : selectedDays.value
      const res = await fetchEnvironmentData(selectedPlant.plantId, days)
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

// 初始化图表
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
      text: `${selectedDays.value === 0 ? customDays.value : selectedDays.value}天${currentOption.label}趋势图`,
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
        rotate: 0, // 不旋转，正常显示
        hideOverlap: true, // 隐藏重叠的标签
        showMinLabel: false, // 不显示起始标签
        showMaxLabel: false, // 不显示终点标签
        interval: 'auto' // 自动间隔显示
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
        data: sortedData.map(item => item[selectedDataType.value]),
        smooth: true,
        showSymbol: false,
        lineStyle: {
          color: currentOption.color,
          width: 3
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [{
              offset: 0,
              color: currentOption.gradientColors[0]
            }, {
              offset: 1,
              color: currentOption.gradientColors[1]
            }]
          }
        }
      }
    ]
  }

  myChart.setOption(option)
}

// 监听数据类型变化
watch(selectedDataType, () => {
  if (historyData.value.length > 0) {
    initChart()
  }
})

// 监听天数选择变化
watch(selectedDays, () => {
  fetchChartData()
})

// 监听自定义天数变化
watch(customDays, () => {
  if (selectedDays.value === 0) {
    fetchChartData()
  }
})

// 监听花盆选择变化
watch(currentPot, () => {
  fetchChartData()
})

const route = useRoute()

// 修改路由监听方式
watch(
  () => route.fullPath, // 使用 fullPath 而不是 path
  (newPath, oldPath) => {
    console.log('路由变化:', oldPath, '->', newPath) // 添加日志便于调试
    // 当离开首页时，清除定时器
    if (!newPath.startsWith('/home')) {
      console.log('离开首页，清除定时器') // 添加日志
      if (timer) {
        clearInterval(timer)
        timer = null
      }
    }
  },
  { immediate: true } // 立即执行一次
)

// 组件卸载时也要清除定时器
onUnmounted(() => {
  console.log('组件卸载，清除定时器') // 添加日志
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})

// 在设置定时器时也添加日志
const setupTimer = () => {
  console.log('设置定时器')
  if (timer) {
    clearInterval(timer)
  }
  timer = setInterval(() => {
    fetchDeviceData()
    fetchStatus()
  }, 2000)
}

// 修改 onMounted
onMounted(async () => {
  await fetchPlants()
  if (deviceName.value) {
    await handlePlantChange(deviceName.value)
  }
  fetchDeviceData()
  setupTimer()
  fetchWeather()
  fetchChartData()
})

// 定义事件
const emit = defineEmits(['update-abnormal'])

// 检查是否有异常数据
const checkAbnormalData = () => {
  const hasAbnormal = Object.values(monitorData.value).some(item => item.status !== 'normal')
  emit('update-abnormal', hasAbnormal)
  return hasAbnormal
}

// 监听数据变化
watch(monitorData, () => {
  checkAbnormalData()
}, { deep: true })

// 获取状态类型
const getStatusType = (status) => {
  const types = {
    normal: 'success',
    high: 'danger',
    low: 'warning'
  }
  return types[status]
}

// 获取状态文本
const getStatusText = (status) => {
  const texts = {
    normal: '正常',
    high: '偏高',
    low: '偏低'
  }
  return texts[status]
}

// 计算进度条百分比
const calculateProgress = (value, min, max) => {
  const numValue = parseFloat(value)
  if (isNaN(numValue)) return 0
  // 确保值在范围内
  const limitedValue = Math.max(min, Math.min(max, numValue))
  // 计算百分比
  return ((limitedValue - min) / (max - min) * 100).toFixed(1)
}

// 获取进度条状态
const getProgressStatus = (status) => {
  switch (status) {
    case 'low':
      return 'exception'
    case 'high':
      return 'warning'
    default:
      return 'success'
  }
}

const router = useRouter()

// 跳转到设备控制页面
const goToDeviceControl = (item) => {
  if (item.status !== 'normal') {
    router.push('/control')
  }
}

// 获取监测项的图标
const getItemIcon = (key) => {
  const icons = {
    temperature: new URL('@/assets/icons/temperature.svg', import.meta.url).href,
    humidity: new URL('@/assets/icons/humidity.svg', import.meta.url).href,
    soilMoisture: new URL('@/assets/icons/soil.svg', import.meta.url).href,
    co2: new URL('@/assets/icons/co2.svg', import.meta.url).href,
    lightLux: new URL('@/assets/icons/light.svg', import.meta.url).href
  }
  return icons[key]
}

// 添加 getItemClasses 方法
const getItemClasses = (item) => {
  return {
    'clickable': item.status !== 'normal',
    'abnormal': item.status !== 'normal',
    [`status-${item.status}`]: true
  }
}

// 添加获取天气图标组件的方法
const getWeatherIcon = (weather) => {
  if (!weather) return { icon: null, color: null }
  
  if (weather.includes('晴')) {
    return { 
      icon: sunnyIcon, 
      color: '#F6A30E' // 阳光黄色
    }
  }
  if (weather.includes('多云')) {
    return { 
      icon: cloudyIcon, 
      color: '#8FB4FF' // 淡蓝色
    }
  }
  if (weather.includes('雨')) {
    return { 
      icon: rainyIcon, 
      color: '#4B9EFF' // 深蓝色
    }
  }
  if (weather.includes('阴')) {
    return { 
      icon: overcastIcon, 
      color: '#B4C0D3' // 灰蓝色
    }
  }
  
  return { icon: null, color: null }
}

// 添加加载状态
const adviceLoading = ref(false)

// 修改获取养护建议方法
const getAdvice = async () => {
  try {
    const selectedPlant = plants.value.find(plant => plant.displayId === currentPot.value)
    if (!selectedPlant) return
    
    adviceLoading.value = true  // 开始加载
    
    const data = {
      id: selectedPlant.plantId,
      userId: userStore.user.id,
      plantId: selectedPlant.plantId
    }
    
    const res = await getPlantAdvice(data)
    if (res.data.code === '1') {
      const htmlContent = marked(res.data.data)
      
      ElMessageBox.alert(htmlContent, '养护建议', {
        dangerouslyUseHTMLString: true,
        customClass: 'advice-dialog',
        confirmButtonText: '我知道了'
      })
    }
  } catch (error) {
    console.error('获取养护建议失败:', error)
    ElMessage.error('获取养护建议失败')
  } finally {
    adviceLoading.value = false  // 结束加载
  }
}

// const detecting = ref(false)
// const imageUrl = ref('')
// const detectionData = ref(null)

// // 解析建议内容（将markdown转为html）
// const parsedSuggestion = computed(() => {
//   if (!detectionData.value?.suggestion) return ''
//   try {
//     const suggestionObj = JSON.parse(detectionData.value.suggestion)
//     return marked(suggestionObj.result)
//   } catch (e) {
//     return detectionData.value.suggestion
//   }
// })

// // 上传前检查
// const beforeUpload = async (file) => {
//   const isImage = file.type.startsWith('image/')
//   if (!isImage) {
//     ElMessage.error('只能上传图片文件！')
//     return false
//   }

//   const isLt5M = file.size / 1024 / 1024 < 5
//   if (!isLt5M) {
//     ElMessage.error('图片大小不能超过 5MB！')
//     return false
//   }

//   try {
//     detecting.value = true
//     imageUrl.value = URL.createObjectURL(file)
    
//     // 调用检测接口
//     const res = await checkDisease(1, file) // 这里的deviceId暂时写死为1
//     if (res.data.code === '1') {
//       detectionData.value = res.data.data
//       ElMessage.success('检测完成')
//     } else {
//       ElMessage.error('检测失败')
//     }
//   } catch (error) {
//     console.error('检测失败:', error)
//     ElMessage.error('检测失败')
//   } finally {
//     detecting.value = false
//   }

//   return false // 阻止自动上传
// }
</script>

<template>
  <div class="home-container">
    <el-card class="monitor-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <img src="@/assets/icons/monitor.svg" class="header-icon" alt="monitor" />
            <span class="title">实时监测数据</span>
          </div>
          <div class="header-right">
            <el-button 
              type="primary" 
              size="small" 
              @click="getAdvice"
              class="advice-btn"
              :loading="adviceLoading"
              :disabled="adviceLoading"
            >
              {{ adviceLoading ? '生成建议中...' : '一键生成养护建议' }}
            </el-button>
            <div class="device-selector">
              <span class="selector-label">监测花盆:</span>
              <el-select 
                v-model="deviceName" 
                class="pot-select" 
                size="small"
                @change="handlePlantChange"
              >
                <template #prefix>
                  <span class="current-status" :class="{ 'online': online === '在线' }">
                    {{ online }}
                  </span>
                </template>
                <el-option 
                  v-for="device in plants" 
                  :key="device.id" 
                  :label="`${device.name}`"
                  :value="device.name"
                />
          </el-select>
            </div>
          </div>
        </div>
      </template>

      <div class="monitor-grid">
        <!-- 天气卡片 -->
        <div class="monitor-item weather-item">
          <div class="weather-content">
            <div class="weather-icon">
              <img src="@/assets/icons/weather.svg" alt="weather" class="icon" />
            </div>
            <div class="weather-info">
              <div class="main-info">
                <span class="temperature">{{ weatherData.current.temp }}°</span>
                <div class="weather-text">
                  <span class="weather">{{ weatherData.current.weather }}</span>
                  <img 
                    v-if="getWeatherIcon(weatherData.current.weather).icon"
                    :src="getWeatherIcon(weatherData.current.weather).icon"
                    class="weather-status-icon"
                    :style="{ filter: `brightness(0) invert(1) drop-shadow(0 0 2px ${getWeatherIcon(weatherData.current.weather).color})` }"
                    alt="weather"
                  />
                </div>
              </div>
              <div class="weather-details">
                <div class="detail-item">
                  <span class="label">湿度</span>
                  <span class="value">{{ weatherData.current.humidity }}%</span>
                </div>
                <div class="detail-item">
                  <span class="label">风向</span>
                  <span class="value">{{ weatherData.current.windDir }}</span>
                </div>
              </div>
            </div>
            <div class="forecast">
              <div class="forecast-title">未来三天</div>
              <div 
                v-for="(day, index) in weatherData.forecast" 
                :key="index"
                class="forecast-item"
              >
                <div class="forecast-date">
                  <span class="date">{{ day.date }}</span>
                </div>
                <div class="forecast-weather">
                  <div class="day">
                    <span class="label">白天</span>
                    <span class="text">{{ day.dayWeather }}</span>
                  </div>
                  <div class="night">
                    <span class="label">夜间</span>
                    <span class="text">{{ day.nightWeather }}</span>
                  </div>
                </div>
                <div class="forecast-data">
                  <div class="temp">
                    <span class="high">{{ day.dayTemp }}°</span>
                    <span class="divider">/</span>
                    <span class="low">{{ day.nightTemp }}°</span>
                  </div>
                  <div class="extra-info">
                    <span class="humidity">湿度 {{ day.humidity }}%</span>
                    <span class="precip">降水 {{ day.precip }}mm</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 监测数据卡片 -->
        <div 
          v-for="(item, key) in monitorData" 
          :key="key" 
          class="monitor-item"
          :class="getItemClasses(item)"
          @click="goToDeviceControl(item)"
        >
          <div class="item-content">
          <div class="item-header">
              <div class="label-wrapper">
                <img 
                  :src="getItemIcon(key)" 
                  class="item-icon" 
                  :alt="key"
                />
            <span class="label">{{ item.label }}</span>
              </div>
            <el-tag :type="getStatusType(item.status)" size="small" class="status-tag">
              {{ getStatusText(item.status) }}
            </el-tag>
          </div>
          <div class="value-container">
              <div class="value-wrapper">
                <span class="value">{{ item.value }}</span>
                <span class="unit">{{ item.unit }}</span>
              </div>
              <div class="progress-wrapper">
            <div class="progress-bar">
                  <div 
                    class="progress" 
                    :style="{ 
                      width: calculateProgress(item.value, item.min, item.max) + '%', 
                      background: getProgressStatus(item.status) === 'success' ? 'var(--el-color-success)' : getProgressStatus(item.status) === 'warning' ? 'var(--el-color-warning)' : 'var(--el-color-danger)'
                    }"
                  />
            </div>
                <span class="range-text">最佳范围: {{ item.range }}</span>
          </div>
            </div>
          </div>
          <div class="item-background"></div>
        </div>
      </div>
    </el-card>

    <!-- 历史数据卡片 -->
    <el-card class="history-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="title">历史环境数据</span>
          </div>
          <div class="header-right">
            <el-select 
              v-model="selectedDays" 
              class="days-select" 
              size="small"
              placeholder="选择天数"
              style="width: 120px; margin-right: 10px;"
            >
              <template #prefix>
                <el-icon class="select-icon">
                  <Calendar />
                </el-icon>
              </template>
              <el-option
                v-for="option in daysOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            
            <el-input-number
              v-if="selectedDays === 0"
              v-model="customDays"
              :min="1"
              :max="365"
              size="small"
              placeholder="自定义"
              style="width: 120px; margin-right: 10px;"
            />
            
            <el-select 
              v-model="selectedDataType" 
              class="data-type-select" 
              size="small"
              placeholder="选择数据类型"
              style="width: 120px;"
            >
              <el-option
                v-for="option in dataTypeOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </div>
        </div>
      </template>
      <el-empty 
        v-if="!historyData.length" 
        description="没有数据" 
        :image-size="200"
      />
      <div 
        v-else 
        id="environmentChart" 
        style="width: 100%; height: 600px;"
      />
    </el-card>

    <!-- 病症检测卡片 -->
    <!-- <el-card class="detection-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="title">植物病症检测</span>
          </div>
          <div class="header-right">
            <el-upload
              class="image-upload"
              :show-file-list="false"
              :before-upload="beforeUpload"
              accept="image/*"
            >
              <el-button 
                type="primary" 
                class="upload-btn"
                size="small"
              >
                <el-icon><Upload /></el-icon>上传图片
              </el-button>
            </el-upload>
          </div>
        </div>
      </template>

      <div class="detection-content" v-loading="detecting">
        <div class="image-preview" v-if="imageUrl">
          <img :src="imageUrl" alt="预览图">
        </div>
        
        <div class="detection-result" v-if="detectionData">
          <div class="result-item">
            <span class="label">检测时间：</span>
            <span>{{ detectionData.detectionTime }}</span>
          </div>
          <div class="result-item">
            <span class="label">植物名称：</span>
            <span>{{ detectionData.plantName }}</span>
          </div>
          <div class="result-item">
            <span class="label">检测结果：</span>
            <span>{{ detectionData.detectionResult }}</span>
          </div>
          <div class="result-item">
            <span class="label">位置信息：</span>
            <span>{{ detectionData.location }}</span>
          </div>
          <div class="suggestion" v-if="detectionData.suggestion">
            <div v-html="parsedSuggestion"></div>
          </div>
        </div>

        <el-empty v-else description="请上传植物图片进行检测" />
      </div>
    </el-card> -->
    </div>
</template>

<style lang="scss" scoped>
.home-container {
  padding: 20px;
  // background: linear-gradient(135deg, #f5f7fa 0%, #e4e7eb 100%);
  min-height: calc(100vh - 40px);

.monitor-card {
    background: rgba(255, 255, 255, 0.9);
    backdrop-filter: blur(10px);
    border: none;
    border-radius: 16px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);

    :deep(.el-card__header) {
      border-bottom: 1px solid rgba(0, 0, 0, 0.05);
      padding: 20px;
    }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

      .header-left {
      display: flex;
      align-items: center;
      gap: 12px;

        .header-icon {
          width: 24px;
          height: 24px;
          filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1));
        }

        .title {
          font-size: 18px;
          font-weight: 600;
          background: linear-gradient(45deg, var(--el-color-primary), #409eff);
          background-clip: text;
          -webkit-background-clip: text;
          color: transparent;
        }
      }

      .pot-select {
        width: 130px;
        :deep(.el-input__wrapper) {
          border-radius: 20px;
        }
    }
  }

  .monitor-grid {
    display: grid;
      grid-template-columns: 300px repeat(2, 1fr);
      gap: 24px;
      padding: 24px;

      .weather-item {
        grid-row: span 3;
        background: linear-gradient(135deg, #6b8cce, #60a9f6) !important;
        color: white !important;
        border: none !important;
        overflow: hidden;
        position: relative;

        &::before {
          content: '';
          position: absolute;
          top: 0;
          left: 0;
          right: 0;
          bottom: 0;
          background: linear-gradient(45deg, rgba(255, 255, 255, 0.1), transparent);
          pointer-events: none;
        }

        .weather-content {
          position: relative;
          height: 100%;
          display: flex;
          flex-direction: column;
          gap: 24px;
          z-index: 1;

          .weather-icon {
            text-align: center;
            margin-bottom: 16px;

            .icon {
              width: 64px;
              height: 64px;
              filter: drop-shadow(0 4px 6px rgba(0, 0, 0, 0.1));
            }
          }

          .weather-info {
            text-align: center;

            .main-info {
              margin-bottom: 20px;
              display: flex;
              align-items: center;
              justify-content: center;

              .temperature {
                font-size: 48px;
                font-weight: 600;
                text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.1);
              }

              .weather-text {
                display: flex;
                align-items: center;
                gap: 4px;
                margin-left: 8px;

                .weather {
                  font-size: 20px;
                  opacity: 0.9;
                }

                .weather-status-icon {
                  width: 60px;
                  height: 60px;
                  filter: brightness(0) invert(1);
                }
              }
            }

            .weather-details {
              display: flex;
              justify-content: center;
              gap: 32px;

              .detail-item {
                text-align: center;

                .label {
                  display: block;
                  font-size: 14px;
                  opacity: 0.8;
                  margin-bottom: 4px;
                }

                .value {
                  font-size: 16px;
                  font-weight: 500;
                }
              }
            }
          }

          .forecast {
            margin-top: 10px;
            padding-top: 20px;
            border-top: 1px solid rgba(255, 255, 255, 0.2);

            .forecast-title {
              font-size: 16px;
              font-weight: 500;
              margin-bottom: 16px;
              opacity: 0.9;
            }

            .forecast-item {
              display: grid;
              grid-template-columns: 70px 1fr 120px;
              align-items: start;
              padding: 16px 0;
              border-bottom: 1px solid rgba(255, 255, 255, 0.1);
              
              &:last-child {
                border-bottom: none;
              }

              .forecast-date {
                .date {
                  font-size: 15px;
                  opacity: 0.8;
                }
              }

              .forecast-weather {
                display: flex;
                flex-direction: column;
                gap: 8px;

                .day, .night {
                  display: flex;
                  align-items: center;
                  gap: 8px;

                  .label {
                    font-size: 13px;
                    opacity: 0.7;
                    width: 36px;
                  }

                  .text {
                    font-size: 14px;
                    opacity: 0.9;
                  }
                }

                .night {
                  opacity: 0.8;
                }
              }

              .forecast-data {
                text-align: right;

                .temp {
                  font-size: 15px;
                  font-weight: 500;
                  margin-bottom: 4px;

                  .high {
                    color: rgba(255, 255, 255, 0.95);
                  }

                  .divider {
                    margin: 0 6px;
                    opacity: 0.5;
                  }

                  .low {
                    opacity: 0.7;
                  }
                }

                .extra-info {
                  display: flex;
                  flex-direction: column;
                  gap: 4px;
                  font-size: 12px;
                  opacity: 0.8;
                }
              }
            }
          }
        }
  }

  .monitor-item {
        position: relative;
    background: white;
        border-radius: 16px;
        padding: 20px;
        overflow: hidden;
        transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
        border: 1px solid rgba(0, 0, 0, 0.05);

        .item-content {
          position: relative;
          z-index: 2;
        }

        .item-background {
          position: absolute;
          top: 0;
          left: 0;
          right: 0;
          bottom: 0;
          opacity: 0.05;
          background-size: cover;
          transition: all 0.3s ease;
        }

        &.status-normal .item-background {
          background: linear-gradient(45deg, var(--el-color-success), #95d475);
        }

        &.status-high .item-background {
          background: linear-gradient(45deg, var(--el-color-danger), #ff9896);
        }

        &.status-low .item-background {
          background: linear-gradient(45deg, var(--el-color-warning), #ffd04b);
        }

    .item-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
          margin-bottom: 20px;

          .label-wrapper {
            display: flex;
            align-items: center;
            gap: 8px;

            .item-icon {
              width: 28px;
              height: 28px;
              filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1));
            }

      .label {
        font-size: 16px;
              font-weight: 500;
        color: var(--el-text-color-primary);
            }
      }

      .status-tag {
            padding: 4px 12px;
            border-radius: 12px;
            font-weight: 500;
      }
    }

    .value-container {
          .value-wrapper {
            display: flex;
            align-items: baseline;
            gap: 4px;
            margin-bottom: 16px;

      .value {
              font-size: 32px;
              font-weight: 600;
        color: var(--el-text-color-primary);
              line-height: 1;
            }

            .unit {
              font-size: 14px;
              color: var(--el-text-color-secondary);
            }
          }

          .progress-wrapper {
      .progress-bar {
              height: 8px;
        background-color: var(--el-fill-color-light);
              border-radius: 4px;
        overflow: hidden;
        margin-bottom: 8px;

        .progress {
          height: 100%;
          transition: all 0.3s ease;
                border-radius: 4px;
                background-size: 200% 100%;
                animation: gradientMove 2s linear infinite;
        }
      }

      .range-text {
        font-size: 12px;
        color: var(--el-text-color-secondary);
            }
      }
    }

    &.clickable {
      cursor: pointer;

      &:hover {
        transform: translateY(-4px);
            box-shadow: 0 12px 24px rgba(0, 0, 0, 0.1);

            .item-background {
              opacity: 0.1;
              transform: scale(1.05);
            }
      }
    }

    &.abnormal {
      &::after {
        content: '点击查看详情';
        position: absolute;
            bottom: 12px;
            right: 12px;
        font-size: 12px;
            color: var(--el-color-primary);
        opacity: 0;
        transition: opacity 0.3s ease;
      }

      &:hover::after {
        opacity: 1;
          }
      }
    }
  }
}

  .history-card {
    margin-top: 24px;
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

      .header-left {
        display: flex;
        align-items: center;
        gap: 20px;

        .title {
          font-size: 16px;
          font-weight: bold;
        }

        .pot-select {
          width: 120px;
        }
      }

      .data-type-select {
        width: 120px;
      }
    }
  }
}

@keyframes pulse {
  0% { transform: scale(1); }
  50% { transform: scale(1.1); }
  100% { transform: scale(1); }
}

// 响应式布局
@media screen and (max-width: 1400px) {
  .forecast-item {
    grid-template-columns: 60px 1fr 110px;
  }
}

@media screen and (max-width: 768px) {
  .forecast-item {
    grid-template-columns: 50px 1fr 100px;
    
    .forecast-weather {
      .day, .night {
        .label {
          width: 32px;
          font-size: 12px;
        }
        
        .text {
          font-size: 13px;
        }
      }
    }
    
    .forecast-data {
      .temp {
        font-size: 14px;
      }
      
      .extra-info {
        font-size: 11px;
      }
    }
  }
}

.weather-text {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 8px;

  .weather {
    font-size: 20px;
    opacity: 0.9;
  }

  .weather-status-icon {
    width: 60px;
    height: 60px;
    transition: filter 0.3s ease;
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 24px;

  .advice-btn {
    margin-right: 8px;
  }

  .device-selector {
    display: flex;
    align-items: center;
    gap: 12px;

    .selector-label {
      font-size: 14px;
      color: var(--el-text-color-regular);
      white-space: nowrap;
    }

    .pot-select {
      width: 150px;

      :deep(.el-input__wrapper) {
        padding: 0 30px 0 8px;
        height: 32px;
      }

      :deep(.el-input__prefix) {
        margin-right: 4px;
      }
    }
  }
}

.current-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  background-color: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  display: inline-block;
  line-height: 1.4;
  margin-left: 4px;

  &.online {
    background-color: var(--el-color-success-light-9);
    color: var(--el-color-success);
  }
}

:deep(.advice-dialog) {
  width: 1000px !important;
  max-width: 90vw !important;
  
  .el-message-box {
    width: 100% !important;
    max-width: none !important;
  }
  
  .el-message-box__content {
    padding: 24px;
    
    h3 {
      color: var(--el-color-primary);
      margin: 20px 0 12px;
      font-size: 18px;
    }
    
    ul {
      padding-left: 24px;
      margin: 12px 0;
    }
    
    p {
      margin: 12px 0;
      line-height: 1.8;
      font-size: 15px;
    }
  }

  &::-webkit-scrollbar {
    width: 8px;
  }

  &::-webkit-scrollbar-track {
    background: #f1f1f1;
    border-radius: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: #888;
    border-radius: 4px;
    
    &:hover {
      background: #555;
    }
  }
}

:global(.el-message-box) {
  width: auto !important;
  max-width: none !important;
}

.location-info {
  .location-text {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 12px;
    background: rgba(255, 255, 255, 0.1);
    border-radius: 20px;
    cursor: pointer;
    transition: all 0.3s ease;
    
    &:hover {
      background: rgba(255, 255, 255, 0.2);
    }
    
    i {
      font-size: 16px;
      color: var(--el-color-primary);
    }
    
    span {
      font-size: 14px;
      color: var(--el-text-color-regular);
      max-width: 300px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

@keyframes gradientMove {
  0% {
    background-position: 100% 0;
  }
  100% {
    background-position: 0 0;
  }
}

.device-status {
  float: right;
  font-size: 12px;
  color: #909399;
  margin-left: 8px;

  &.online {
    color: #67C23A;
  }
}

.device-selector {
  display: inline-flex;
  align-items: center;

  .pot-select {
    width: 120px;

    :deep(.el-input__wrapper) {
      padding-right: 30px;
    }

    :deep(.el-input__prefix) {
      margin-right: 8px;
    }
  }
}

.current-status {
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 10px;
  background-color: var(--el-color-danger-light-9);
  color: var(--el-color-danger);

  &.online {
    background-color: var(--el-color-success-light-9);
    color: var(--el-color-success);
  }
}

.detection-card {
  margin-top: 24px !important;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 12px 36px rgba(0, 0, 0, 0.12);
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px 0;

    .header-left {
      display: flex;
      align-items: center;

      .title {
        font-size: 18px;
        font-weight: 600;
        color: var(--el-text-color-primary);
      }
    }

    .header-right {
      .upload-btn {
        padding: 8px 16px;
        transition: all 0.3s ease;

        &:hover {
          transform: translateY(-2px);
          box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
        }

        .el-icon {
          margin-right: 4px;
        }
      }
    }
  }

  .detection-content {
    padding: 20px;
    min-height: 300px;

    .image-preview {
      text-align: center;
      margin-bottom: 20px;

      img {
        max-width: 100%;
        max-height: 300px;
        border-radius: 8px;
      }
    }

    .detection-result {
      padding: 20px;
      background: var(--el-color-primary-light-9);
      border-radius: 8px;

      .result-item {
        margin-bottom: 12px;
        
        .label {
          font-weight: bold;
          margin-right: 8px;
          color: var(--el-color-primary);
        }
      }

      .suggestion {
        margin-top: 20px;
        padding: 16px;
        background: white;
        border-radius: 8px;
        
        :deep(h2) {
          color: var(--el-color-primary);
          margin-bottom: 16px;
        }

        :deep(ul) {
          padding-left: 20px;
        }
      }
    }
  }
}
</style>