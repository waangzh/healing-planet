import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useHistoryStore = defineStore('history', () => {
  // 当前选中的植物实例ID
  const currentPlantInstanceId = ref(null)
  
  // 选中的日期范围
  const dateRange = ref([])
  
  // 当前页码和每页数量
  const pagination = ref({
    currentPage: 1,
    pageSize: 10
  })

  // 更新函数
  const setCurrentPlant = (id) => {
    currentPlantInstanceId.value = id
  }

  const setDateRange = (range) => {
    dateRange.value = range
  }

  const setPagination = (page) => {
    pagination.value = { ...pagination.value, ...page }
  }

  // reset
  const $reset = () => {
    currentPlantInstanceId.value = null
    dateRange.value = []
    pagination.value = { currentPage: 1, pageSize: 10 }
  }

  return {
    currentPlantInstanceId,
    dateRange,
    pagination,
    setCurrentPlant,
    setDateRange,
    setPagination,
    $reset
  }
}, {
  persist: {
    enabled: true,
    strategies: [
      {
        key: 'history',
        storage: localStorage
      }
    ]
  }
}) 