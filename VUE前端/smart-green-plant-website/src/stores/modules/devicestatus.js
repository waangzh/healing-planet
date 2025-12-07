import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useDeviceStatusStore = defineStore(
  'deviceStatus',
  () => {
    // 监测数据状态
    const monitorData = ref({
      temperature: {
        value: '0',
        unit: '°C',
        status: 'normal'
      },
      humidity: {
        value: '0',
        unit: '%',
        status: 'normal'
      },
      soilMoisture: {
        value: '0',
        unit: '%',
        status: 'normal'
      },
      co2: {
        value: '0',
        unit: 'ppm',
        status: 'normal'
      },
      lightLux: {
        value: '0',
        unit: 'Lx',
        status: 'normal'
      }
    })

    // 设备控制状态
    const deviceStatus = ref({
      temperature: '0.0°C',
      EnvironmentHumidity: '0.0%',
      soilMoisture: '0.0%',
      co2Level: '0ppm',
      lightIntensity: '0.0Lux',
    })

    // 设备开关状态
    const switches = ref({
      wifi: false,
      fan: false,
      pump: false,
      light: false
    })

    // 更新监测数据
    const updateMonitorData = (key, value) => {
      if (monitorData.value[key]) {
        monitorData.value[key].value = value
      }
    }

    // 更新设备状态
    const updateDeviceStatus = (newStatus) => {
      deviceStatus.value = { ...newStatus }
    }

    // 更新开关状态
    const updateSwitches = (newSwitches) => {
      switches.value = { ...newSwitches }
    }

    // 添加更新监测数据状态的方法
    const updateMonitorStatus = (key, status) => {
      if (monitorData.value[key]) {
        monitorData.value[key].status = status
      }
    }

    // 重置所有状态
    const $reset = () => {
      monitorData.value = {
        temperature: { value: '0', unit: '°C', status: 'normal' },
        humidity: { value: '0', unit: '%', status: 'normal' },
        soilMoisture: { value: '0', unit: '%', status: 'normal' },
        co2: { value: '0', unit: 'ppm', status: 'normal' },
        lightLux: { value: '0', unit: 'Lx', status: 'normal' }
      }
      deviceStatus.value = {
        temperature: '0.0°C',
        EnvironmentHumidity: '0.0%',
        soilMoisture: '0.0%',
        co2Level: '0ppm',
        lightIntensity: '0.0Lux',
      }
      switches.value = {
        wifi: false,
        fan: false,
        pump: false,
        light: false
      }
    }

    return {
      monitorData,
      deviceStatus,
      switches,
      updateMonitorData,
      updateDeviceStatus,
      updateSwitches,
      updateMonitorStatus,
      $reset
    }
  },
  {
    persist: {
      enabled: true,
      strategies: [
        {
          storage: localStorage,
          paths: ['monitorData', 'deviceStatus', 'switches']
        }
      ]
    }
  }
)
