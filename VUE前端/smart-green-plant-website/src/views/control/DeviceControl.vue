<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { 
  setDeviceWarning,
  fetchDeviceControl,
  getDeviceStatus, 
  setDeviceSwitch, 
  setDeviceThreshold,
  getDeviceThreshold,
  getDeviceByUserId
} from '@/api/deviceControl'
import { useUserStore, useDeviceStatusStore, useDeviceStore } from '@/stores'
import { WarningFilled } from '@element-plus/icons-vue'

const userStore = useUserStore()
const deviceStatusStore = useDeviceStatusStore()
const deviceStore = useDeviceStore()

// 监测花盆选择
const currentPot = ref('')
const plants = ref([]) // 存储植物实例数据
const deviceName = ref('')

// 使用 computed 从 store 中获取设备状态
const deviceStatus = computed(() => deviceStatusStore.deviceStatus)

// 使用 computed 从 store 中获取开关状态
const switches = computed(() => deviceStatusStore.switches)

// 控制设置
const controlSettings = ref({
  deviceId: 1,
  temperatureMin: 20,
  temperatureMax: 30,
  humidityMin: 40,
  humidityMax: 70,
  co2Min: 400,
  co2Max: 1000,
  lightIntensityMin: 1000,
  lightIntensityMax: 10000,
  soilMoistureMin: 30,
  soilMoistureMax: 70
})

// 自动调节开关
const autoControl = ref(false)

// 设备在线状态
const online = ref('离线')

// 添加预警状态
const warningEnabled = ref(false)

// 添加检查设备是否在线的计算属性
const isDeviceOnline = computed(() => online.value === '在线')

// 获取设备列表
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

// 修改获取设备状态数据的方法
const fetchDeviceData = async () => {
  try {
    const res = await fetchDeviceControl(deviceName.value)
    if (res.data.code === '1') {
      const data = res.data.data.body.data.list.propertyStatusInfo
      const newStatus = { ...deviceStatus.value }
      
      data.forEach(item => {
        switch (item.identifier) {
          case 'Temperature':
            newStatus.temperature = `${item.value}°C`
            break
          case 'EnvironmentHumidity':
            newStatus.EnvironmentHumidity = `${item.value}%`
            break
          case 'SoilMoisture':
            newStatus.soilMoisture = `${item.value}%`
            break
          case 'CO2Value':
            newStatus.co2Level = `${item.value}pm`
            break
          case 'LightLux':
            newStatus.lightIntensity = `${item.value}Lux`
            break
        }
      })
      
      deviceStatusStore.updateDeviceStatus(newStatus)
    }
  } catch (error) {
    console.error('获取设备数据失败:', error)
  }
}

// 定时更新数据
let timer = null

// 初始化设备状态
const initStatusFromBackend = async () => {
  try {
    const res = await fetchDeviceControl(deviceName.value)
    if (res.data.code === '1') {
      const data = res.data.data.body.data.list.propertyStatusInfo
      data.forEach(item => {
        switch (item.identifier) {
          case 'FanSwitch':
            switches.value.fan = item.value === '1'
            break
          case 'WifiSwitch':
            switches.value.wifi = item.value === '1'
            break
          case 'IrrigationPumpStatus':
            switches.value.pump = item.value === '1'
            break
          case 'LightStatus':
            switches.value.light = item.value === '1'
            break
          case 'TargetTemperature':
            controlSettings.value.temperature = Number(item.value)
            break
          case 'TargetSoilMoisture':
            controlSettings.value.soilMoisture = Number(item.value)
            break
          case 'TargetCO2Value':
            controlSettings.value.co2Level = Number(item.value)
            break
          case 'TargetLightLux':
            controlSettings.value.lightIntensity = Number(item.value)
            break
        }
      })
      console.log('成功获取设备最新状态:', switches.value, controlSettings.value)
    }
  } catch (error) {
    console.error('获取设备状态失败:', error)
  }
}

// 获取设备在线状态
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

// 获取设备阈值
const fetchDeviceThreshold = async () => {
  try {
    const selectedPlant = plants.value.find(plant => plant.displayId === currentPot.value)
    if (!selectedPlant) return

    const res = await getDeviceThreshold(selectedPlant.id)
    if (res.data.code === '1') {
      const thresholdData = res.data.data
      // 更新控制设置
      controlSettings.value = {
        deviceId: selectedPlant.id,
        temperatureMin: thresholdData.temperatureMin,
        temperatureMax: thresholdData.temperatureMax,
        humidityMin: thresholdData.humidityMin,
        humidityMax: thresholdData.humidityMax,
        co2Min: thresholdData.co2Min,
        co2Max: thresholdData.co2Max,
        lightIntensityMin: thresholdData.lightIntensityMin,
        lightIntensityMax: thresholdData.lightIntensityMax,
        soilMoistureMin: thresholdData.soilMoistureMin,
        soilMoistureMax: thresholdData.soilMoistureMax
      }
    }
  } catch (error) {
    console.error('获取设备阈值失败:', error)
    ElMessage.error('获取设备阈值失败')
  }
}

// 初始化预警状态
const initWarningStatus = (device) => {
  warningEnabled.value = device.warningStatus === 1
}

// 修改处理植物选择变化的函数
const handlePlantChange = async (val) => {
  deviceStore.setCurrentDevice(val) // 更新存储的设备
  currentPot.value = val
  deviceName.value = val
  initWarningStatus(plants.value.find(device => device.name === val)) // 初始化预警状态
  try {
    // 获取设备名称后初始化各种状态
    await Promise.all([
      initStatusFromBackend(),
      fetchStatus(),
      fetchDeviceThreshold(),
      fetchDeviceData() // 立即获取新设备的数据
    ])

    // 清除旧的定时器
    if (timer) {
      clearInterval(timer)
    }
    // 设置新的定时器
    timer = setInterval(() => {
      fetchDeviceData()
      fetchStatus()
    }, 2000)
  } catch (error) {
    console.error('初始化设备状态失败:', error)
  }
}

// 发送设备开关到后端
const sendSwitchToBackend = async () => {
  const currentDevice = plants.value.find(device => device.name === deviceName.value)
  if (!currentDevice) {
    ElMessage.error('未找到当前设备')
    return
  }

  const deviceStatus = {
    id: currentDevice.id,
    name: deviceName.value,
    fanSwitch: switches.value.fan ? 1 : 0,
    wifiSwitch: switches.value.wifi ? 1 : 0,
    irriogationPumpStatus: switches.value.pump ? 1 : 0,
    lightStatus: switches.value.light ? 1 : 0,
  }

  try {
    const res = await setDeviceSwitch(deviceStatus)
    if (res.data.code === '1') {
      console.log('设备开关更新成功:', deviceStatus)
      ElMessage.success('设备开关更新成功')
    }
  } catch (error) {
    console.error('设备开关更新失败:', error)
    ElMessage.error('设备开关更新失败')
  }
}

// 修改开关相关的方法
const handleWifiChange = async () => {
  await sendSwitchToBackend()
}

const handleFanChange = async () => {
  await sendSwitchToBackend()
}

const handlePumpChange = async () => {
  await sendSwitchToBackend()
}

const handleLightChange = async () => {
  await sendSwitchToBackend()
}

// 处理预警开关变化
const handleWarningChange = async (value) => {
  const selectedDevice = plants.value.find(device => device.name === deviceName.value)
  if (!selectedDevice) return

  try {
    const res = await setDeviceWarning({
      id: selectedDevice.id,
      warningStatus: value ? 1 : 0
    })
    
    if (res.data.code === '1') {
      ElMessage.success(`设备预警${value ? '开启' : '关闭'}成功`)
    } else {
      warningEnabled.value = !value // 恢复原状态
      ElMessage.error(`设备预警${value ? '开启' : '关闭'}失败`)
    }
  } catch (error) {
    console.error('设置设备预警状态失败:', error)
    warningEnabled.value = !value // 恢复原状态
    ElMessage.error('设置设备预警状态失败')
  }
}

onMounted(async () => {
  await fetchPlants()
  if (deviceName.value) {
    await handlePlantChange(deviceName.value)
  }
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
  }
})

// 修改保存设置函数
const saveSettings = async () => {
  try {
    const selectedPlant = plants.value.find(plant => plant.displayId === currentPot.value)
    if (!selectedPlant) return

    controlSettings.value.deviceId = selectedPlant.id
    const res = await setDeviceThreshold(controlSettings.value)
    if (res.data.code === '1') {
      ElMessage.success('阈值设置保存成功')
      await fetchDeviceThreshold()
    }
  } catch (error) {
    console.error('保存阈值设置失败:', error)
    ElMessage.error('保存阈值设置失败')
  }
}

// 验证阈值范围
const validateThreshold = (type) => {
  switch(type) {
    case 'temperature':
      if (controlSettings.value.temperatureMin > controlSettings.value.temperatureMax) {
        controlSettings.value.temperatureMin = controlSettings.value.temperatureMax
      }
      break
    case 'humidity':
      if (controlSettings.value.humidityMin > controlSettings.value.humidityMax) {
        controlSettings.value.humidityMin = controlSettings.value.humidityMax
      }
      break
    case 'co2':
      if (controlSettings.value.co2Min > controlSettings.value.co2Max) {
        controlSettings.value.co2Min = controlSettings.value.co2Max
      }
      break
    case 'lightIntensity':
      if (controlSettings.value.lightIntensityMin > controlSettings.value.lightIntensityMax) {
        controlSettings.value.lightIntensityMin = controlSettings.value.lightIntensityMax
      }
      break
    case 'soilMoisture':
      if (controlSettings.value.soilMoistureMin > controlSettings.value.soilMoistureMax) {
        controlSettings.value.soilMoistureMin = controlSettings.value.soilMoistureMax
      }
      break
  }
}

// 添加状态判断函数
const getStatusInfo = (value, type) => {
  const numValue = parseFloat(value.replace(/[^0-9.]/g, ''))
  
  // 根据不同类型获取对应的阈值
  let min, max
  switch(type) {
    case 'temperature':
      min = controlSettings.value.temperatureMin
      max = controlSettings.value.temperatureMax
      break
    case 'humidity':
      min = controlSettings.value.humidityMin
      max = controlSettings.value.humidityMax
      break
    case 'soilMoisture':
      min = controlSettings.value.soilMoistureMin
      max = controlSettings.value.soilMoistureMax
      break
    case 'co2':
      min = controlSettings.value.co2Min
      max = controlSettings.value.co2Max
      break
    case 'light':
      min = controlSettings.value.lightIntensityMin
      max = controlSettings.value.lightIntensityMax
      break
  }

  if (numValue < min) {
    return {
      type: 'warning',
      text: '偏低'
    }
  } else if (numValue > max) {
    return {
      type: 'danger',
      text: '偏高'
    }
  } else {
    return {
      type: 'success',
      text: '正常'
    }
  }
}

// 获取监测项的图标
const getItemIcon = (key) => {
  const icons = {
    temperature: new URL('@/assets/icons/temperature.svg', import.meta.url).href,
    humidity: new URL('@/assets/icons/humidity.svg', import.meta.url).href,
    soilMoisture: new URL('@/assets/icons/soil.svg', import.meta.url).href,
    co2Level: new URL('@/assets/icons/co2.svg', import.meta.url).href,
    lightIntensity: new URL('@/assets/icons/light.svg', import.meta.url).href
  }
  return icons[key]
}

// 获取设备开关图标
const getSwitchIcon = (key) => {
  const icons = {
    wifi: new URL('@/assets/icons/WIFI.svg', import.meta.url).href,
    fan: new URL('@/assets/icons/风扇.svg', import.meta.url).href,
    pump: new URL('@/assets/icons/水泵.svg', import.meta.url).href,
    light: new URL('@/assets/icons/灯光.svg', import.meta.url).href
  }
  return icons[key]
}
</script>

<template>
  <div class="control-container">
    <!-- 顶部控制栏 -->
    <el-card class="header-card">
      <div class="control-header">
        <div class="left-section">
          <span class="title">监测花盆</span>
          <el-select 
            v-model="currentPot" 
            class="pot-select" 
            size="small"
            @change="handlePlantChange"
          >
            <el-option 
              v-for="plant in plants" 
              :key="plant.id" 
              :label="plant.displayId" 
              :value="plant.displayId"
            />
          </el-select>
        </div>
        <div class="status-indicators">
          <el-tag :type="online === '在线' ? 'success' : 'danger'">
            {{ online }}
          </el-tag>
          <el-switch
            v-model="warningEnabled"
            active-text="设备预警"
            :active-value="true"
            :inactive-value="false"
            @change="handleWarningChange"
          />
        </div>
      </div>
    </el-card>

    <div class="content-grid">
      <!-- 设备状态 -->
      <el-card class="status-card">
        <template #header>
          <div class="card-header">设备状态</div>
        </template>
        <!-- 添加离线状态覆盖层 -->
        <!-- <div v-if="!isDeviceOnline" class="offline-overlay">
          <el-icon class="offline-icon"><WarningFilled /></el-icon>
          <div class="offline-message">设备当前处于离线状态，无法获取实时数据</div>
          <el-button size="small" type="primary" @click="fetchStatus">重新检查连接</el-button>
        </div> -->
        <div class="status-grid">
          <div class="status-item">
            <div class="label-wrapper">
              <img 
                :src="getItemIcon('temperature')" 
                class="item-icon" 
                alt="temperature"
              />
              <div class="label">温度</div>
            </div>
            <el-tag 
              :type="getStatusInfo(deviceStatus.temperature, 'temperature').type"
              size="small"
              class="status-tag"
            >
              {{ getStatusInfo(deviceStatus.temperature, 'temperature').text }}
            </el-tag>
            <div class="value">{{ deviceStatus.temperature }}</div>
          </div>

          <div class="status-item">
            <div class="label-wrapper">
              <img 
                :src="getItemIcon('humidity')" 
                class="item-icon" 
                alt="humidity"
              />
              <div class="label">空气湿度</div>
            </div>
            <el-tag 
              :type="getStatusInfo(deviceStatus.EnvironmentHumidity, 'humidity').type"
              size="small"
              class="status-tag"
            >
              {{ getStatusInfo(deviceStatus.EnvironmentHumidity, 'humidity').text }}
            </el-tag>
            <div class="value">{{ deviceStatus.EnvironmentHumidity }}</div>
          </div>

          <div class="status-item">
            <div class="label-wrapper">
              <img 
                :src="getItemIcon('soilMoisture')" 
                class="item-icon" 
                alt="soil"
              />
              <div class="label">土壤湿度</div>
            </div>
            <el-tag 
              :type="getStatusInfo(deviceStatus.soilMoisture, 'soilMoisture').type"
              size="small"
              class="status-tag"
            >
              {{ getStatusInfo(deviceStatus.soilMoisture, 'soilMoisture').text }}
            </el-tag>
            <div class="value">{{ deviceStatus.soilMoisture }}</div>
          </div>

          <div class="status-item">
            <div class="label-wrapper">
              <img 
                :src="getItemIcon('co2Level')" 
                class="item-icon" 
                alt="co2"
              />
              <div class="label">二氧化碳浓度</div>
            </div>
            <el-tag 
              :type="getStatusInfo(deviceStatus.co2Level, 'co2').type"
              size="small"
              class="status-tag"
            >
              {{ getStatusInfo(deviceStatus.co2Level, 'co2').text }}
            </el-tag>
            <div class="value">{{ deviceStatus.co2Level }}</div>
          </div>

          <div class="status-item">
            <div class="label-wrapper">
              <img 
                :src="getItemIcon('lightIntensity')" 
                class="item-icon" 
                alt="light"
              />
              <div class="label">光照强度</div>
            </div>
            <el-tag 
              :type="getStatusInfo(deviceStatus.lightIntensity, 'light').type"
              size="small"
              class="status-tag"
            >
              {{ getStatusInfo(deviceStatus.lightIntensity, 'light').text }}
            </el-tag>
            <div class="value">{{ deviceStatus.lightIntensity }}</div>
          </div>
        </div>
      </el-card>

      <!-- 设备开关 -->
      <el-card class="switch-card">
        <template #header>
          <div class="card-header">设备开关</div>
        </template>
        <!-- 添加离线状态覆盖层 -->
        <div v-if="!isDeviceOnline" class="offline-overlay">
          <el-icon class="offline-icon"><WarningFilled /></el-icon>
          <div class="offline-message">设备当前处于离线状态，无法控制设备</div>
        </div>
        <div class="switch-grid">
          <div class="switch-item">
            <div class="switch-label">
              <img 
                :src="getSwitchIcon('wifi')" 
                class="switch-icon" 
                alt="wifi"
              />
              <span>WiFi开关</span>
            </div>
            <el-switch 
              v-model="switches.wifi" 
              :disabled="autoControl || !isDeviceOnline"
              @change="handleWifiChange"
            />
          </div>
          <div class="switch-item">
            <div class="switch-label">
              <img 
                :src="getSwitchIcon('fan')" 
                class="switch-icon" 
                alt="fan"
              />
              <span>风扇开关</span>
            </div>
            <el-switch 
              v-model="switches.fan" 
              :disabled="autoControl || !isDeviceOnline"
              @change="handleFanChange"
            />
          </div>
          <div class="switch-item">
            <div class="switch-label">
              <img 
                :src="getSwitchIcon('pump')" 
                class="switch-icon" 
                alt="pump"
              />
              <span>水泵开关</span>
            </div>
            <el-switch 
              v-model="switches.pump" 
              :disabled="autoControl || !isDeviceOnline"
              @change="handlePumpChange"
            />
          </div>
          <div class="switch-item">
            <div class="switch-label">
              <img 
                :src="getSwitchIcon('light')" 
                class="switch-icon" 
                alt="light"
              />
              <span>灯光</span>
            </div>
            <el-switch 
              v-model="switches.light" 
              :disabled="autoControl || !isDeviceOnline"
              @change="handleLightChange"
            />
          </div>
        </div>
      </el-card>

      <!-- 控制面板 -->
      <el-card class="control-card">
        <template #header>
          <div class="card-header">
            <span>控制面板</span>
            <el-switch v-model="autoControl" active-text="自动调节" inactive-text="手动控制" :disabled="!isDeviceOnline" />
          </div>
        </template>

        <!-- 添加离线状态覆盖层 -->
        <div v-if="!isDeviceOnline" class="offline-overlay">
          <el-icon class="offline-icon"><WarningFilled /></el-icon>
          <div class="offline-message">设备当前处于离线状态，无法设置控制参数</div>
        </div>

        <div class="control-panel">
          <div class="slider-group">
            <!-- 温度阈值 -->
            <div class="slider-item">
              <div class="threshold-header">
                <span class="label">温度阈值</span>
              </div>
              <div class="threshold-content">
                <div class="threshold-row">
                  <span class="threshold-label">最小值</span>
                  <el-slider 
                    v-model="controlSettings.temperatureMin" 
                    :min="0" 
                    :max="50"
                    :max-value="controlSettings.temperatureMax"
                    @change="validateThreshold('temperature')"
                    :disabled="autoControl || !isDeviceOnline"
                  />
                  <span class="value">{{ controlSettings.temperatureMin }}°C</span>
                </div>
                <div class="threshold-row">
                  <span class="threshold-label">最大值</span>
                  <el-slider 
                    v-model="controlSettings.temperatureMax" 
                    :min="0" 
                    :max="50"
                    :min-value="controlSettings.temperatureMin"
                    @change="validateThreshold('temperature')"
                    :disabled="autoControl || !isDeviceOnline"
                  />
                  <span class="value">{{ controlSettings.temperatureMax }}°C</span>
                </div>
              </div>
            </div>

            <!-- 湿度阈值 -->
            <div class="slider-item">
              <div class="threshold-header">
                <span class="label">湿度阈值</span>
              </div>
              <div class="threshold-content">
                <div class="threshold-row">
                  <span class="threshold-label">最小值</span>
                  <el-slider 
                    v-model="controlSettings.humidityMin" 
                    :min="0" 
                    :max="100"
                    :min-value="controlSettings.humidityMin"
                    @change="validateThreshold('humidity')"
                    :disabled="autoControl || !isDeviceOnline"
                  />
                  <span class="value">{{ controlSettings.humidityMin }}%</span>
                </div>
                <div class="threshold-row">
                  <span class="threshold-label">最大值</span>
                  <el-slider 
                    v-model="controlSettings.humidityMax" 
                    :min="0" 
                    :max="100"
                    :min-value="controlSettings.humidityMin"
                    @change="validateThreshold('humidity')"
                    :disabled="autoControl || !isDeviceOnline"
                  />
                  <span class="value">{{ controlSettings.humidityMax }}%</span>
                </div>
              </div>
            </div>

            <!-- 土壤湿度阈值 -->
            <div class="slider-item">
              <div class="threshold-header">
                <span class="label">土壤湿度阈值</span>
              </div>
              <div class="threshold-content">
                <div class="threshold-row">
                  <span class="threshold-label">最小值</span>
                  <el-slider 
                    v-model="controlSettings.soilMoistureMin" 
                    :min="0" 
                    :max="100"
                    :min-value="controlSettings.soilMoistureMin"
                    @change="validateThreshold('soilMoisture')"
                    :disabled="autoControl || !isDeviceOnline"
                  />
                  <span class="value">{{ controlSettings.soilMoistureMin }}%</span>
                </div>
                <div class="threshold-row">
                  <span class="threshold-label">最大值</span>
                  <el-slider 
                    v-model="controlSettings.soilMoistureMax" 
                    :min="0" 
                    :max="100"
                    :min-value="controlSettings.soilMoistureMin"
                    @change="validateThreshold('soilMoisture')"
                    :disabled="autoControl || !isDeviceOnline"
                  />
                  <span class="value">{{ controlSettings.soilMoistureMax }}%</span>
                </div>
              </div>
            </div>

            <!-- CO2浓度阈值 -->
            <div class="slider-item">
              <div class="threshold-header">
                <span class="label">CO2浓度阈值</span>
              </div>
              <div class="threshold-content">
                <div class="threshold-row">
                  <span class="threshold-label">最小值</span>
                  <el-slider 
                    v-model="controlSettings.co2Min" 
                    :min="0" 
                    :max="1000"
                    :min-value="controlSettings.co2Min"
                    @change="validateThreshold('co2')"
                    :disabled="autoControl || !isDeviceOnline"
                  />
                  <span class="value">{{ controlSettings.co2Min }}pm</span>
                </div>
                <div class="threshold-row">
                  <span class="threshold-label">最大值</span>
                  <el-slider 
                    v-model="controlSettings.co2Max" 
                    :min="0" 
                    :max="1000"
                    :min-value="controlSettings.co2Min"
                    @change="validateThreshold('co2')"
                    :disabled="autoControl || !isDeviceOnline"
                  />
                  <span class="value">{{ controlSettings.co2Max }}pm</span>
                </div>
              </div>
            </div>

            <!-- 光照强度阈值 -->
            <div class="slider-item">
              <div class="threshold-header">
                <span class="label">光照强度阈值</span>
              </div>
              <div class="threshold-content">
                <div class="threshold-row">
                  <span class="threshold-label">最小值</span>
                  <el-slider 
                    v-model="controlSettings.lightIntensityMin" 
                    :min="0" 
                    :max="1000"
                    :min-value="controlSettings.lightIntensityMin"
                    @change="validateThreshold('lightIntensity')"
                    :disabled="autoControl || !isDeviceOnline"
                  />
                  <span class="value">{{ controlSettings.lightIntensityMin }}Lx</span>
                </div>
                <div class="threshold-row">
                  <span class="threshold-label">最大值</span>
                  <el-slider 
                    v-model="controlSettings.lightIntensityMax" 
                    :min="0" 
                    :max="1000"
                    :min-value="controlSettings.lightIntensityMin"
                    @change="validateThreshold('lightIntensity')"
                    :disabled="autoControl || !isDeviceOnline"
                  />
                  <span class="value">{{ controlSettings.lightIntensityMax }}Lx</span>
                </div>
              </div>
            </div>
          </div>

          <el-button 
            type="primary" 
            @click="saveSettings" 
            class="save-btn"
            :disabled="autoControl || !isDeviceOnline"
          >
            保存阈值设置
          </el-button>
        </div>
      </el-card>
    </div>
    </div>
</template>

<style lang="scss" scoped>
.control-container {
  padding: 20px;
  // background-color: var(--el-bg-color-page);
}

.header-card {
  margin-bottom: 20px;
  background: linear-gradient(135deg, #f0f9eb 0%, #e1f3d8 100%);

  .control-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .left-section {
      display: flex;
      align-items: center;
      gap: 12px;

      .title {
        font-size: 16px;
        font-weight: bold;
        color: var(--el-color-success-dark-2);
      }

      .pot-select {
        width: 100px;
      }
    }
  }
}

.content-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

// 通用离线覆盖层样式
.offline-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(4px);
  z-index: 10;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 20px;
  animation: fadeIn 0.3s ease-out;

  .offline-icon {
    font-size: 48px;
    color: var(--el-color-danger);
    margin-bottom: 16px;
  }

  .offline-message {
    font-size: 18px;
    color: var(--el-text-color-primary);
    text-align: center;
    margin-bottom: 20px;
    font-weight: 500;
  }
}

// 所有带离线覆盖层的卡片需要相对定位
.status-card,
.switch-card,
.control-card {
  position: relative;
}

.status-card {
  background: linear-gradient(135deg, #ecf5ff 0%, #e6f1fc 100%);

  .status-grid {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 20px;
  }

  .status-item {
    text-align: center;
    padding: 20px;
    background: rgba(255, 255, 255, 0.8);
    border-radius: 8px;
    backdrop-filter: blur(10px);
    transition: all 0.3s;
    position: relative;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
    }

    .label-wrapper {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      margin-bottom: 12px;

      .item-icon {
        position: absolute;
        left: 30px;
        width: 28px;
        height: 28px;
        filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1));
      }

      .label {
        color: var(--el-text-color-secondary);
        font-size: 14px;
      }
    }

    .value {
      font-size: 24px;
      font-weight: bold;
      color: var(--el-color-primary);
    }

    .status-tag {
      position: absolute;
      top: 12px;
      right: 12px;
      border-radius: 12px;
      padding: 0 12px;
    }
  }
}

.switch-card {
  background: linear-gradient(135deg, #fdf6ec 0%, #faecd8 100%);

  .switch-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 20px;
  }

  .switch-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px;
    background: rgba(255, 255, 255, 0.8);
    border-radius: 8px;
    transition: all 0.3s;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
    }

    .switch-label {
      display: flex;
      align-items: center;
      gap: 12px;

      .switch-icon {
        width: 24px;
        height: 24px;
      }

      span {
        font-size: 14px;
        color: var(--el-text-color-regular);
      }
    }
  }
}

.control-card {
  // background: linear-gradient(135deg, #f4f4f5 0%, #e9e9eb 100%);

  .control-panel {
    padding: 24px;
    opacity: v-bind('isDeviceOnline ? 1 : 0.6');
    pointer-events: v-bind('isDeviceOnline ? "auto" : "none"');
    transition: opacity 0.3s;

    .slider-group {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
      gap: 24px;
      margin-bottom: 32px;

      .slider-item {
        background: linear-gradient(145deg, #ffffff, #f5f5f5);
        border-radius: 16px;
        padding: 24px;
        box-shadow: 5px 5px 15px rgba(0, 0, 0, 0.05),
                    -5px -5px 15px rgba(255, 255, 255, 0.8);
        transition: all 0.3s ease;

        &:hover {
          transform: translateY(-2px);
          box-shadow: 8px 8px 20px rgba(0, 0, 0, 0.08),
                      -8px -8px 20px rgba(255, 255, 255, 0.9);
        }

        .threshold-header {
          display: flex;
          align-items: center;
          margin-bottom: 24px;
          padding-bottom: 16px;
          border-bottom: 1px solid rgba(0, 0, 0, 0.05);

          .label {
            font-size: 18px;
            font-weight: 600;
            color: var(--el-color-primary);
            position: relative;
            padding-left: 16px;

            &::before {
              content: '';
              position: absolute;
              left: 0;
              top: 50%;
              transform: translateY(-50%);
              width: 4px;
              height: 16px;
              background: var(--el-color-primary);
              border-radius: 2px;
            }
          }
        }

        .threshold-content {
          display: flex;
          flex-direction: column;
          gap: 20px;

          .threshold-row {
            display: grid;
            grid-template-columns: 80px 1fr 70px;
            align-items: center;
            gap: 16px;
            padding: 8px 16px;
            background: rgba(255, 255, 255, 0.6);
            border-radius: 12px;
            transition: all 0.3s ease;

            &:hover {
              background: rgba(255, 255, 255, 0.9);
            }

            .threshold-label {
              font-size: 14px;
              color: var(--el-text-color-secondary);
              font-weight: 500;
            }

            .value {
              text-align: right;
              font-size: 15px;
              font-weight: 600;
              color: var(--el-color-primary);
              background: rgba(64, 158, 255, 0.1);
              padding: 4px 12px;
              border-radius: 8px;
            }

            :deep(.el-slider) {
              margin: 0;
              
              .el-slider__runway {
                height: 4px;
                background-color: rgba(64, 158, 255, 0.1);
              }

              .el-slider__bar {
                height: 4px;
                background-color: var(--el-color-primary);
              }

              .el-slider__button-wrapper {
                width: 20px;
                height: 20px;
              }

              .el-slider__button {
                width: 20px;
                height: 20px;
                border: 2px solid var(--el-color-primary);
                background-color: #fff;
                transition: transform 0.3s ease;

                &:hover {
                  transform: scale(1.1);
                }
              }
            }
          }
        }
      }
    }

    .save-btn {
      width: 100%;
      height: 48px;
      font-size: 16px;
      font-weight: 600;
      letter-spacing: 1px;
      background: linear-gradient(135deg, var(--el-color-primary), #409eff);
      border: none;
      border-radius: 12px;
      box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
      transition: all 0.3s ease;

      &:hover:not(:disabled) {
        transform: translateY(-2px);
        box-shadow: 0 6px 16px rgba(64, 158, 255, 0.4);
      }

      &:active:not(:disabled) {
        transform: translateY(0);
        box-shadow: 0 2px 8px rgba(64, 158, 255, 0.3);
      }

      &:disabled {
        opacity: 0.7;
        cursor: not-allowed;
        background: linear-gradient(135deg, #a0cfff, #c6e2ff);
      }
    }
  }
}

// 添加一些动画效果
@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.4);
  }
  70% {
    box-shadow: 0 0 0 10px rgba(245, 108, 108, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(245, 108, 108, 0);
  }
}

.slider-item {
  animation: slideIn 0.3s ease-out forwards;
  @for $i from 1 through 5 {
    &:nth-child(#{$i}) {
      animation-delay: #{$i * 0.1}s;
    }
  }
}

// 通用卡片样式
:deep(.el-card) {
  border: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);

  .el-card__header {
    border-bottom: none;
    padding-bottom: 0;
  }

  .card-header {
    font-size: 16px;
    font-weight: bold;
    color: var(--el-text-color-primary);
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
}

// 选择器样式
:deep(.el-select) {
  .el-input__wrapper {
    background: rgba(255, 255, 255, 0.8);
    backdrop-filter: blur(10px);
    padding: 0 8px;
  }

  .el-input__inner {
    font-size: 14px;
  }
}

// 标签样式
:deep(.el-tag) {
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  border: none;
}

// 响应式布局
@media screen and (max-width: 1400px) {
  .status-grid {
    grid-template-columns: repeat(3, 1fr) !important;
  }
  
  .switch-grid {
    grid-template-columns: repeat(2, 1fr) !important;
  }
}

@media screen and (max-width: 768px) {
  .status-grid {
    grid-template-columns: repeat(2, 1fr) !important;
  }
  
  .switch-grid {
    grid-template-columns: 1fr !important;
  }
}

.threshold-row {
  :deep(.el-slider) {
    .el-slider__runway {
      background-color: rgba(64, 158, 255, 0.1);
    }

    .el-slider__bar {
      background-color: var(--el-color-primary);
    }

    .el-slider__button {
      border-color: var(--el-color-primary);
      
      &:hover {
        transform: scale(1.1);
      }
    }

    &.is-disabled {
      .el-slider__runway {
        background-color: var(--el-fill-color-light);
      }
      
      .el-slider__bar {
        background-color: var(--el-text-color-placeholder);
      }
      
      .el-slider__button {
        border-color: var(--el-text-color-placeholder);
      }
    }
  }
}

.status-indicators {
  display: flex;
  align-items: center;
  gap: 16px;

  .el-tag {
    padding: 0 12px;
    height: 28px;
    line-height: 26px;
    transition: all 0.3s;
    
    &.el-tag--danger {
      animation: pulse 2s infinite;
    }
  }

  .el-switch {
    margin-left: 8px;
  }
}
</style>