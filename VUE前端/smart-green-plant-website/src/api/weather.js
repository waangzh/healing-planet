import request from '@/utils/request'

// 获取实时天气数据
export const fetchWeatherNow = () => {
  return request.get('/weatherNow')
}

// 获取三天天气预报
export const fetchThreeDayForecast = () => {
  return request.get('/weather')
}