// app.js
App({
  globalData: {
    // 当前选中的花盆实例信息
    selectedPlantId: 4,
    selectedPlantDisplayId: null,
    // 登录后会被真实用户信息覆盖
    userInfo: null,
    // 兼容历史引用的占位用户数据
    user: {
      avatarUrl: '1',
      userName: 'smart',
      nickName: '小绿小绿',
      email: '',
      phone: ''
    }
  },
  setGlobalData: function (key, value) {
    this.globalData[key] = value;
  },
  getGlobalData: function (key) {
    return this.globalData[key];
  },
  onLaunch: function () {
    // 启动时把本地缓存的用户信息写回全局，避免重复登录
    const cachedUser = wx.getStorageSync('userInfo');
    if (cachedUser) {
      this.globalData.userInfo = cachedUser;
    }
  }
})
