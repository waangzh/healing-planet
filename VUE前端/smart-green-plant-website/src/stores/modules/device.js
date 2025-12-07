import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useDeviceStore = defineStore('device', () => {
  const currentDevice = ref('')

  const setCurrentDevice = (deviceName) => {
    currentDevice.value = deviceName
  }

  // reset
  const $reset = () => {
    currentDevice.value = ''
  }

  return {
    currentDevice,
    setCurrentDevice,
    $reset
  }
}, {
  persist: {
    enabled: true,
    strategies: [
      {
        key: 'device',
        storage: localStorage
      }
    ]
  }
}) 