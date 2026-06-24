// index.js
import * as echarts from '../../ec-canvas/echarts';
const config = require('../../utils/config.js');
let chartInstance = null; // 全局保存图表实例
function initChart(canvas, width, height, dpr) {
  chartInstance = echarts.init(canvas, null, {
    width: width,
    height: height,
    devicePixelRatio: dpr
  });
  canvas.setChart(chartInstance);
  // 初始化基础配置
  const option = {
    responsive: { width: '100%' },
    tooltip: { trigger: 'axis' },
    legend: { data: [] }, 
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: [],
      axisLabel: { 
        margin: 30, 
        interval: 0,
      }
    },
    yAxis: {
      type: 'value',
      name: '', // 动态设置
      axisLabel: { 
        fontSize: 10,
        margin: 13, 
        interval: 0 
      }
    },
    series: []// 动态生成
  };
  
  chartInstance.setOption(option);
  // console.log("init",chartInstance);
  return chartInstance;
}
Page({
  data: {
    welcome:'晴天',
    location:'湖北省-宜昌市',
    weathertemperature:'22',
    currentWeather:{
      weatherIcon:'sun',
      temp:23,
      windDir:"西南风",
      humidity:45,
      precip:33
    },
    weatherForecast:[
      {
        date: '05-03',
        weatherIcon: 'sun', // 假设这是图标名称
        tempRange: '20~28'
      },
      {
        date: '05-04',
        weatherIcon: 'sun',
        tempRange: '20~28'
      },
      {
        date: '05-05',
        weatherIcon: 'sun',
        tempRange: '23~30'
      },
    ],
    deviceName:'ESP8266',
    temperature:35,
    humidity:23,
    soilMoisture:45,
    CO2Value:46,
    lightLux:56,
    precip:55,//降水量
    potNumber:1,
    dataTypes:[
      {name:'温度湿度',type:'temp_humidity'},
      { name: '光照强度', type: 'light' },
      { name: '二氧化碳', type: 'co2' },
      { name: '土壤湿度', type: 'soil' }
    ],
    currentType: 0,
    ec:{
    },
    rawData:[]
  }, 
  onLoad: function () {
    // 获取实时天气数据
    this.fetchWeatherNow();
    this.fetchThreeDayForecast();
    this.fetchDeviceName();
    const selectedPlantDisplayId = getApp().getGlobalData('selectedPlantDisplayId');
    if (selectedPlantDisplayId) {
      console.log('Selected Plant ID in another page:', selectedPlantDisplayId);
    }
    //获取表格数据
    console.log("获取表格数据");
     // 先获取数据，成功后再初始化图表
    this.fetchChartData(() => {
      this.initECharts(); // 数据到位后初始化
    });
    
     // 设置定时器，每隔两秒获取数据
     const fetchDataInterval = setInterval(() => {
      this.fetchValue();
     }, 60000); // 每隔两秒执行一次

    // 保存定时器句柄
     this.setData({
       fetchDataInterval: fetchDataInterval
     });
    console.log("load");
  },
  onUnload: function () {
    //清除定时器
    clearInterval(this.data.fetchDataInterval);
  },
  onShow: function () {
    // 获取表格数据
    this.fetchChartData(() => {
    });

    // 获取实时天气数据
    this.fetchWeatherNow();
    // 获取三天天气数据
    // this.fetchThreeDayForecast();
     // 获取全局变量
    const selectedPlantId = getApp().getGlobalData('selectedPlantId');
    const selectedPlantDisplayId = getApp().getGlobalData('selectedPlantDisplayId');
    this.fetchDeviceName();
    setTimeout(() => {
      this.fetchValue();
    }, 500); // 等待0.5秒，以确保设备名已经被设置
     if (1) {
       this.setData({
         potNumber: selectedPlantDisplayId
       });
     }
  },
  onPullDownRefresh: function(){
    console.log("下拉刷新");
    setTimeout(() => {
      wx.hideNavigationBarLoading(); // 隐藏导航条加载动画
      wx.stopPullDownRefresh(); // 停止下拉刷新
    }, 2000); // 延迟2秒
  },
  // 初始化表格
  initECharts() {
    console.log("初始化图表",this.rawData);
    const ecComponent = this.selectComponent('#myEchart');
    ecComponent.init((canvas, width, height, dpr) => {
      chartInstance = echarts.init(canvas, null, {
        width: width,
        height: height,
        devicePixelRatio: dpr
      });
      canvas.setChart(chartInstance);
      const typeConfig = {
        'temp_humidity': {
          yAxisName: '数值',
          series: [
            { name: '温度 (°C)', key: 'temperature', color: '#337ab7' },
            { name: '空气湿度 (%)', key: 'humidity', color: '#5cb85c' }
          ]
        }};
        const config = typeConfig['temp_humidity'];
        const xAxisData = this.rawData.map(item => item.recordedTime.split(' ')[0]);
        // 初始化基础配置
        const newSeries = config.series.map(series => ({
          name: series.name,
          type: 'line',
          data: this.rawData.map(item => item[series.key]),
          itemStyle: { color: series.color },
          label: {
            show: true,
            position: 'top',
            textStyle: { fontSize: 10 }
          },
          animationDuration: 3000,//绘制时间
          animationEasing: 'quadraticOut'
        }));
      
      // 更新图表配置
      chartInstance.setOption({
        legend: {
          data: config.series.map(s => s.name)
        },
        yAxis: {
          name: config.yAxisName,
          type: 'value'
        },
        xAxis: {
          type: 'category',
          boundaryGap: false,
          data: xAxisData,
          axisLabel: { 
            margin: 12, 
            interval: 0,
          }  
        },
        series: newSeries
      }, false);
      console.log("init",chartInstance);
      return chartInstance;
    });
  },
  // 获取表格数据
  fetchChartData(callback) {
    const plantId = getApp().getGlobalData('selectedPlantId') || 4;
    wx.request({
      url: config.BASE_URL + `/getSevenDayData?plantInstanceId=${plantId}&days=7`,
      method: 'GET',
      header: { token: wx.getStorageSync('token') },
      success: (res) => {
        if (res.statusCode === 200 && res.data.code === "1") {
          this.processChartData(res.data.data);
          callback?.();
        } else {
          wx.showToast({ title: res.data.msg || '获取数据失败', icon: 'none' });
        }
      },
      fail: (err) => {
        wx.showToast({ title: '网络错误', icon: 'none' });
      }
    });
  },
  // 切换展示类型
   changeDataType(e) {
    const index = e.currentTarget.dataset.index;
    this.setData({
      currentType: parseInt(index)
    });
    this.updateChartData();
  },
  // 更新表格数据
  updateChartData() {
    console.log("初始化更新-charInstance",chartInstance);
    console.log("初始更新-rawData",this.rawData);
    if ( !this.rawData) return;
    const typeConfig = {
      'temp_humidity': {
        yAxisName: '数值',
        series: [
          { name: '温度 (°C)', key: 'temperature', color: '#337ab7' },
          { name: '空气湿度 (%)', key: 'humidity', color: '#5cb85c' }
        ]
      },
      'light': {
        yAxisName: '数值',
        series: [
          { name: '光照强度 (lux)', key: 'lightIntensity', color: '#f0ad4e' }
        ]
      },
      'co2': {
        yAxisName: '数值',
        series: [
          { name: 'CO₂浓度 (ppm)', key: 'co2Concentration', color: '#d9534f'}
        ]
      },
      'soil': {
        yAxisName: '数值',
        series: [
          { name: '土壤湿度 (%)', key: 'soilMoisture', color: '#5bc0de' }
        ]
      }
    };
    
    const currentType = this.data.dataTypes[this.data.currentType].type;
    const config = typeConfig[currentType] || typeConfig['temp_humidity'];
    
    // 准备图表数据
    const xAxisData = this.rawData.map(item => item.recordedTime.split(' ')[0]);
    // 先清空所有系列
    chartInstance.setOption({
      series: []
    }, true); // 使用 notMerge: true 完全替换系列

  // 创建新系列配置
  const newSeries = config.series.map(series => ({
    name: series.name,
    type: 'line',
    data: this.rawData.map(item => item[series.key]),
    itemStyle: { color: series.color },
    label: {
      show: true,
      position: 'top',
      textStyle: { fontSize: 10 }
    },
    animationDuration: 3000,//绘制时间
    animationEasing: 'quadraticOut'
  }));

  // 更新图表配置
  chartInstance.setOption({
    legend: {
      data: config.series.map(s => s.name)
    },
    yAxis: {
      name: config.yAxisName,
      type: 'value'
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: xAxisData,
      axisLabel: { 
        margin: 12, 
        interval: 0,
      }  
    },
    series: newSeries
  }, false); // 使用 merge: false 合并配置
  },
  // 加载表格数据
  processChartData(rawData) {
    console.log("初始加载表格数据");
    if (!rawData || rawData.length === 0) return;
    
    // 保存原始数据
    this.rawData = rawData; // 倒序显示最新数据在前
    // 更新页面数据
    // const latestData = rawData[0];
    this.setData({
      // temperature: latestData.temperature,
      // humidity: latestData.humidity,
      // soilMoisture: latestData.soilMoisture,
      // CO2Value: latestData.co2Concentration,
      // lightLux: latestData.lightIntensity,
      rawData:rawData
    });
  },
  fetchDeviceName:function(){
    const plantid = getApp().getGlobalData('selectedPlantId')==null?4:getApp().getGlobalData('selectedPlantId');
    wx.request({
      url: config.BASE_URL + '/device/getDeviceName/' + plantid,
      method: 'GET',
      header: {
        token: wx.getStorageSync('token')
      },
      success: (res) => {
        if (res.statusCode === 200 && res.data.code === '1') {
          const devicename=res.data.data;
          this.setData({
            deviceName:devicename,
          });
          console.log('获取设备名称:', devicename);
        } else {
          console.error('设备名称获取失败:', res.statusCode, devicename);
        }
      }
    })
  },
  fetchValue: function () {
    const name = 'ESP8266'; // 假设设备ID
    wx.request({
      url: config.BASE_URL + '/device/found/' + this.data.deviceName,
      method: 'GET',
      header: {
        token: wx.getStorageSync('token')
      },
      success: (res) => {
        if (res.statusCode === 200 && res.data.code === '1') {
          const data = res.data.data.body.data.list.propertyStatusInfo;
          const temperature = this.findPropertyValue(data, 'temperature');
          const environmentHumidity = this.findPropertyValue(data, 'EnvironmentHumidity');
          const co2value = this.findPropertyValue(data,'CO2Value');
          const soilmoisture = this.findPropertyValue(data,'SoilMoisture');
          const lightlux = this.findPropertyValue(data,'LightLux');
          this.setData({
            temperature: temperature ? parseFloat(temperature.value) : 0,
            humidity: environmentHumidity ? parseFloat(environmentHumidity.value) : 0,
            soilMoisture: soilmoisture ? parseFloat(soilmoisture.value) : 0,
            CO2Value: co2value ? parseFloat(co2value.value) : 0,
            lightLux: lightlux ? parseFloat(lightlux.value) : 0,
            // potNumber:
          });
          console.log("name:",this.data.deviceName);
          console.log('获取监测数据:', environmentHumidity, temperature);
        } else {
          console.error('监测数据获取失败:', res.statusCode, res.data);
        }
      },
      fail: (err) => {
        console.error('请求失败:', err);
      }
    });
  },
  findPropertyValue: function (propertyList, identifier) {
    for (let i = 0; i < propertyList.length; i++) {
      if (propertyList[i].identifier === identifier) {
        return propertyList[i];
      }
    }
    return null;
  },
  // 三天天气
  fetchThreeDayForecast: function () {
    wx.request({
      url: config.BASE_URL + '/weather',
      method: 'GET',
      header: {
        token: wx.getStorageSync('token')
      },
      success: (res) => {
        if (res.statusCode === 200) {
          const forecastData = res.data.daily;
          const processedForecast = forecastData.map(item => ({
            date: item.fxDate.substring(5), // 提取月份和日期部分
            weatherIcon: this.getWeatherIcon3(item.textDay),
            tempRange: `${item.tempMin}~${item.tempMax}`
          }));
          this.setData({
            weatherForecast: processedForecast,

          });
          console.log('三天天气预报:', forecastData);
        } else {
          console.error('三天天气预报获取失败:', res.statusCode, res.data);
        }
      },
      fail: (err) => {
        console.error('请求失败:', err);
      }
    });
  },
  getWeatherIcon3: function (textDay) {
    // 根据天气情况返回对应的图标名称
    if (textDay.includes('晴')) return 'clear-day';
    if (textDay.includes('雨')) return 'rain';
    if (textDay.includes('阴')) return 'cloudy';
    if (textDay.includes('多云')) return 'partly-cloudy-day';
    return 'unknown'; // 如果没有匹配的情况
  },
  fetchWeatherNow: function () {
    wx.request({
      url: config.BASE_URL + '/weatherNow',
      method: 'GET',
      header: {
        token: wx.getStorageSync('token')
      },
      success: (res) => {
        if (res.statusCode === 200) {
          console.log('Response:', res);
          const weatherData = res.data.now;
          const currentWeather = {
            weatherIcon: this.getWeatherIcon(weatherData.text),
            temp: weatherData.temp,
            windDir: weatherData.windDir,
            humidity: weatherData.humidity,
            precip: weatherData.precip
          };
          this.setData({
            welcome:weatherData.text,
            currentWeather: currentWeather,
          });
          console.log('实时天气预报:', weatherData);
        } else {
          console.error('获取实时天气数据失败:', res.statusCode, res.data);
        }
      },
      fail: (err) => {
        console.error('请求失败:', err);
      }
    });
  },
  getWeatherIcon: function (text) {
    // 根据天气情况返回对应的图标名称
    if (text.includes('晴')) return 'clear-day';
    if (text.includes('雨')) return 'rain';
    if (text.includes('阴')) return 'partly-cloudy-day';
    if (text.includes('多云')) return 'cloudy';
    return 'unknown'; // 如果没有匹配的情况
  },
  onChooseAvatar(e) {
    const { avatarUrl } = e.detail
    const { nickName } = this.data.userInfo
    this.setData({
      "userInfo.avatarUrl": avatarUrl,
      hasUserInfo: nickName && avatarUrl && avatarUrl !== defaultAvatarUrl,
    })
  },
  onInputChange(e) {
    const nickName = e.detail.value
    const { avatarUrl } = this.data.userInfo
    this.setData({
      "userInfo.nickName": nickName,
      hasUserInfo: nickName && avatarUrl && avatarUrl !== defaultAvatarUrl,
    })
  },
  getUserProfile(e) {

  },
  
})
