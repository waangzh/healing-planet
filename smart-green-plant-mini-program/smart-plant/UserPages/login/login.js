// UserPages/login/login.js
const config = require('../../utils/config.js');
Page({
  data: {
    userName: '',
    password: ''
  },

  // 账号输入
  inputUserName: function (e) {
    this.setData({
      userName: e.detail.value
    });
  },

  // 密码输入
  inputPassword: function (e) {
    this.setData({
      password: e.detail.value
    });
  },

  // 登录按钮
  login: function () {
    const userName = this.data.userName;
    const password = this.data.password;

    if (userName && password) {
      this.verifyWithServer(userName, password);
    } else {
      wx.showToast({
        title: '请输入账号和密码',
        icon: 'none'
      });
    }
  },

  // 账号密码登录请求（逻辑与原 pages/login/login 相同）
  verifyWithServer: function (userName, password) {
    wx.request({
      url: config.BASE_URL + '/user/login',
      method: 'POST',
      data: {
        username: userName,
        password: password
      },
      success: function (res) {
        console.log(res);
        if (res.data.code === '1') {
          // 存储Token
          console.log("返回信息:", res.data.data);
          wx.setStorageSync('token', res.data.data.token);
          var userInfo = {
            id: '',
            nickName: '',
            password: '',
            email: 'example@example.com',
            phone: '1234567890',
            username: '',
            avatar: ''
          };
          userInfo.id = res.data.data.id;
          userInfo.nickName = res.data.data.nickName;
          userInfo.password = res.data.data.password;
          userInfo.email = res.data.data.email;
          userInfo.phone = res.data.data.phone;
          userInfo.username = res.data.data.username;
          userInfo.avatar = res.data.data.avatar;

          //存储用户信息
          wx.setStorageSync('userInfo', userInfo);
          // 同步全局用户信息，便于其他页面直接读取
          getApp().setGlobalData('userInfo', userInfo);
          console.log("用户信息:", wx.getStorageSync('userInfo'));

          // 跳转到首页
          wx.switchTab({
            url: '/pages/index/index'
          });
        } else {
          wx.showToast({
            title: '账号或密码错误',
            icon: 'none'
          });
        }
      },
      fail: function (err) {
        console.log('登录请求失败', err);
        wx.showToast({
          title: '网络异常，请稍后再试',
          icon: 'none'
        });
      }
    });
  }
})