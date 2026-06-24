// pages/device/device.js
const config = require('../../utils/config.js');
Page({
  /**
   * 页面的初始数据
   */
  data: {
    online: '离线', //设备在线状态, 默认离线
    alarm: '正常',
    deviceName: 'ESP8266', // 默认设备名
    wifiSwitch: true, //wifi开关
    fanSwitch: false, //风扇开关
    irriogationPumpStatus: false, //水泵开关
    lightStatus: false, //灯光开关
    plants: [],
    plantIds: [], // 用于 picker 的显示数组
    selectedPlantIndex: 0, // 默认选择第一个盆栽
    selectedPlantId: null, // 当前选中的后端植物ID
    autoSwitch: false, // 智能调节开关

    // 新的阈值范围数据结构
    tempRange: [20, 30],      // 温度范围 [下限, 上限]
    soilRange: [40, 60],      // 土壤湿度范围
    lightRange: [800, 1500],  // 光照强度范围
    co2Range: [400, 600],     // CO₂浓度范围
  },

  /**
   * 生命周期函数--监听页面加载
   */
  onLoad: function () {
    // 页面加载时，执行异步流程：获取植物列表 -> 获取设备名 -> 初始化设备状态
    this.fetchPlants().then(() => {
      this.fetchDeviceName().then(() => {
        this.initStatusFromBackend();
        this.fetchStatus();
      });
    });
  },

  /**
   * 生命周期函数--监听页面显示
   */
  onShow() {
    // 每次进入页面时，都刷新一次设备状态，确保数据最新
    if (this.data.selectedPlantId) { // 确保已经有选中的植物
      this.initStatusFromBackend();
      this.fetchStatus();
    }
  },

  /**
   * 从后端获取植物列表
   */
  fetchPlants: function () {
    return new Promise((resolve, reject) => {
      wx.request({
        url: config.BASE_URL + '/plantinstance/1',
        method: 'GET',
        header: { token: wx.getStorageSync('token') },
        success: (res) => {
          if (res.statusCode === 200 && res.data.code === '1' && res.data.data && res.data.data.length > 0) {
            const processedPlants = res.data.data.map((plant, index) => ({
              id: plant.id,
              displayId: `${index + 1}`
            }));
            const plantIds = processedPlants.map(plant => plant.displayId);
            
            this.setData({
              plants: processedPlants,
              plantIds: plantIds,
              selectedPlantId: processedPlants[0].id // 默认选中第一个植物的ID
            }, () => {
              getApp().setGlobalData('selectedPlantId', this.data.selectedPlantId);
              getApp().setGlobalData('selectedPlantDisplayId', processedPlants[0].displayId);
              resolve(); // Promise成功
            });
          } else {
            console.error('获取绿植信息失败或列表为空:', res);
            wx.showToast({ title: '获取盆栽列表失败', icon: 'none' });
            reject('获取绿植信息失败');
          }
        },
        fail: (err) => {
          console.error('请求失败:', err);
          wx.showToast({ title: '网络请求失败', icon: 'none' });
          reject(err);
        }
      });
    });
  },

  /**
   * 切换监测花盆
   */
  onPickerChange: function (e) {
    const index = e.detail.value;
    const selectedPlant = this.data.plants[index];
    this.setData({
      selectedPlantIndex: index,
      selectedPlantId: selectedPlant.id
    });
    
    getApp().setGlobalData('selectedPlantId', selectedPlant.id);
    getApp().setGlobalData('selectedPlantDisplayId', selectedPlant.displayId);
    
    // 切换后，重新获取所有信息
    this.fetchDeviceName().then(() => {
      this.initStatusFromBackend();
      this.fetchStatus();
    });
  },

  /**
   * 根据植物ID获取设备名称
   */
  fetchDeviceName: function () {
    return new Promise((resolve, reject) => {
      const plantid = this.data.selectedPlantId;
      if (!plantid) {
        console.error("plantid 为空，无法获取设备名称");
        return reject("plantid is null");
      }
      wx.request({
        url: config.BASE_URL + '/device/getDeviceName/' + plantid,
        method: 'GET',
        header: { token: wx.getStorageSync('token') },
        success: (res) => {
          if (res.statusCode === 200 && res.data.code === '1') {
            this.setData({ deviceName: res.data.data });
            console.log('获取设备名称:', this.data.deviceName);
            resolve();
          } else {
            console.error('设备名称获取失败:', res.statusCode, res.data);
            reject();
          }
        },
        fail: reject
      });
    });
  },

  /**
   * 查询设备在线状态
   */
  fetchStatus: function() {
    return new Promise((resolve, reject) => {
      wx.request({
        url: config.BASE_URL + '/device/status/' + this.data.deviceName,
        method: 'GET',
        header: { token: wx.getStorageSync('token') },
        success: (res) => {
          if (res.statusCode === 200 && res.data.code == '1') {
            const status = res.data.data === 'ONLINE' ? '在线' : '离线';
            this.setData({ online: status });
            console.log('设备状态：', this.data.deviceName, status);
          } else {
            this.setData({ online: '离线' });
            console.error('Failed to fetch status:', res.statusCode, res.data);
          }
          resolve();
        },
        fail: (err) => {
          this.setData({ online: '离线' });
          console.error('Request failed:', err);
          reject(err);
        }
      });
    });
  },

  /**
   * 初始化设备所有属性状态（包括开关和阈值）
   */
  initStatusFromBackend: function() {
    wx.request({
      url: config.BASE_URL + '/device/found/' + this.data.deviceName,
      method: 'GET',
      header: { token: wx.getStorageSync('token') },
      success: (res) => {
        if (res.statusCode === 200 && res.data.code === '1' && res.data.data.body) {
          const data = res.data.data.body.data.list.propertyStatusInfo;
          const parsedData = {};
          let temp = {}, soil = {}, light = {}, co2 = {};

          data.forEach(item => {
            switch(item.identifier) {
              case 'FanSwitch': parsedData.fanSwitch = item.value === '1'; break;
              case 'WifiSwitch': parsedData.wifiSwitch = item.value === '1'; break;
              case 'IrrigationPumpStatus': parsedData.irriogationPumpStatus = item.value === '1'; break;
              case 'LightStatus': parsedData.lightStatus = item.value === '1'; break;
              // 解析阈值范围
              case 'TargetTemperatureMin': temp.min = item.value; break;
              case 'TargetTemperatureMax': temp.max = item.value; break;
              case 'TargetSoilMoistureMin': soil.min = item.value; break;
              case 'TargetSoilMoistureMax': soil.max = item.value; break;
              case 'TargetLightLuxMin': light.min = item.value; break;
              case 'TargetLightLuxMax': light.max = item.value; break;
              case 'TargetCO2ValueMin': co2.min = item.value; break;
              case 'TargetCO2ValueMax': co2.max = item.value; break;
            }
          });
          
          // 组合成Range数组, 如果后端只返回单边，则使用默认值填充
          parsedData.tempRange = [temp.min || this.data.tempRange[0], temp.max || this.data.tempRange[1]];
          parsedData.soilRange = [soil.min || this.data.soilRange[0], soil.max || this.data.soilRange[1]];
          parsedData.lightRange = [light.min || this.data.lightRange[0], light.max || this.data.lightRange[1]];
          parsedData.co2Range = [co2.min || this.data.co2Range[0], co2.max || this.data.co2Range[1]];

          this.setData(parsedData);
          console.log('成功获取设备最新状态并解析：', parsedData);
        } else {
          console.error('Failed to fetch init status:', res.statusCode, res.data);
        }
      },
      fail: (err) => { console.error('Request failed:', err); }
    });
  },

  /**
   * 统一处理阈值输入框事件
   */
  handleThresholdInput(e) {
    const { type, index } = e.currentTarget.dataset;
    let value = e.detail.value;
    const rangeKey = `${type}Range`;
    const dataPath = `${rangeKey}[${index}]`;

    // 实时更新输入值
    this.setData({ [dataPath]: value });

    // 延迟校验，防止因setData异步导致的数据不一致问题
    setTimeout(() => {
      const currentRange = this.data[rangeKey];
      const min = parseFloat(currentRange[0]);
      const max = parseFloat(currentRange[1]);

      if (index == 0 && min > max) { // 校验下限
        wx.showToast({ title: '下限不能大于上限', icon: 'none' });
        this.setData({ [`${rangeKey}[0]`]: max }); // 自动修正为上限值
      } else if (index == 1 && min > max) { // 校验上限
        wx.showToast({ title: '上限不能小于下限', icon: 'none' });
        this.setData({ [`${rangeKey}[1]`]: min }); // 自动修正为下限值
      }
    }, 50);
  },

  /**
   * 保存所有目标阈值
   */
  saveTargets: function () {
    if (this.data.online === '离线') {
      wx.showToast({ title: '设备离线，无法操作', icon: 'none' });
      return;
    }
    const { tempRange, soilRange, co2Range, lightRange } = this.data;
    
    // !!注意: 这里的字段名需要和你的后端接口要求一致!!
    const targets = {
        name: this.data.deviceName,
        temperatureMin: tempRange[0],
        temperatureMax: tempRange[1],
        soilMoistureMin: soilRange[0],
        soilMoistureMax: soilRange[1],
        co2Min: co2Range[0],
        co2Max: co2Range[1],
        lightIntensityMin: lightRange[0],
        lightIntensityMax: lightRange[1]
    };

    console.log("准备保存的目标值:", targets);
    wx.showLoading({ title: '正在保存...' });
    
    // 建议使用一个专门设置目标值的接口
    wx.request({
      url: config.BASE_URL + '/device/setThreshold',
      method: 'PUT',
      data: targets,
      header: { token: wx.getStorageSync('token') },
      success: (res) => {
        wx.hideLoading();
        if (res.statusCode === 200 && res.data.code == "1") {
          console.log('目标值保存成功:', res);
          wx.showToast({ title: '保存成功', icon: 'success' });
        } else {
          console.error("保存失败:", res.data.msg);
          wx.showToast({ title: res.data.msg || '保存失败', icon: 'none' });
        }
      },
      fail: (err) => {
        wx.hideLoading();
        console.error('请求失败:', err);
        wx.showToast({ title: '请求失败', icon: 'none' });
      }
    });
  },

  /**
   * 智能调节开关切换
   */
  autoChange: function(e){
    const newStatus = e.detail.value;
    if (this.data.online === '离线') {
      wx.showToast({ title: '设备离线，无法操作', icon: 'none' });
      this.setData({ autoSwitch: !newStatus }); // 状态回滚
      return;
    }
    this.setData({ autoSwitch: newStatus });
    // TODO: 调用后端接口切换设备的自动/手动模式
    console.log(`向设备 ${this.data.deviceName} 发送自动模式指令: ${newStatus}`);
  },

  /**
   * 发送所有手动开关的状态到后端
   */
  sendAllStatusesToBackend: function () {
    const deviceStatus = {
        id: 3, 
        name: this.data.deviceName,
        fanSwitch: this.data.fanSwitch ? '1' : '0',
        wifiSwitch: this.data.wifiSwitch ? '1' : '0',
        irriogationPumpStatus: this.data.irriogationPumpStatus ? '1' : '0',
        lightStatus: this.data.lightStatus ? '1' : '0'
    };
    wx.request({
        url: config.BASE_URL + '/device/setSwitch',
        method: 'PUT',
        data: deviceStatus,
        header: { token: wx.getStorageSync('token') },
        success: (res) => {
          if (res.statusCode === 200 && res.data.code == "1") {
            console.log('开关状态成功更新:', res);
          } else {
            console.error('开关更新失败:', res.statusCode, res.data);
          }
        },
        fail: (err) => {
          console.error('开关设置请求失败:', err);
        }
    });
  },

  /**
   * 通用的开关处理函数模板
   */
  handleSwitchChange: function(e, switchName) {
    const newStatus = e.detail.value;
    if (this.data.online === '在线') {
        this.setData({ [switchName]: newStatus }, () => {
          this.sendAllStatusesToBackend();
          console.log(`设置 ${switchName}:`, newStatus);
        });
    } else {
        wx.showToast({ title: '设备离线，无法操作', icon: 'none' });
        this.setData({ [switchName]: !newStatus }); // 状态回滚
    }
  },
  
  // 各个开关调用通用处理函数
  handleFanChange: function (e) { this.handleSwitchChange(e, 'fanSwitch'); },
  handlePumpChange: function (e) { this.handleSwitchChange(e, 'irriogationPumpStatus'); },
  handleLightChange: function (e) { this.handleSwitchChange(e, 'lightStatus'); },

  /**
   * 页面相关事件处理函数--监听用户下拉动作
   */
  onPullDownRefresh() {
    console.log("下拉刷新");
    this.fetchPlants().then(() => {
        this.fetchDeviceName().then(() => {
            this.initStatusFromBackend();
            this.fetchStatus()
              .catch(() => {}) // 已在内部 toast 提示，这里保证流程不被中断
              .finally(() => wx.stopPullDownRefresh()); // 确保所有请求完成后再停止刷新动画
        });
    });
  },
  
  /**
   * 其他生命周期和事件函数
   */
  onReady() {},
  onHide() {},
  onUnload() {},
  onReachBottom() {}
});