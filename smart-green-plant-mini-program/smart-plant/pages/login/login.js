const config = require('../../utils/config.js');
Page({
  data: {
    agreed: false
  },

  // 协议勾选
  onAgreementChange: function (e) {
    this.setData({
      agreed: e.detail.value.includes('agree')
    });
  },

  // 跳转到账号密码登录页面
  goAccountLogin: function () {
    wx.navigateTo({
      url: '/UserPages/login/login'
    });
  },

  /**
   * 微信一键登录（授权获取头像昵称 + code）
   * 通过 open-type="getUserProfile" 触发
   */
  onWxOneTapLogin: function (e) {
    const that = this;
    const userProfile = e.detail.userInfo;
    if (!userProfile) {
      wx.showToast({
        title: '未授权无法登录',
        icon: 'none'
      });
      return;
    }

    // 调用 wx.login 获取临时登录凭证 code
    wx.login({
      success(loginRes) {
        if (!loginRes.code) {
          wx.showToast({
            title: '登录失败，请重试',
            icon: 'none'
          });
          return;
        }
        // 调用后端微信登录接口
        that.wxLoginWithServer(loginRes.code, userProfile);
      },
      fail() {
        wx.showToast({
          title: '微信登录失败',
          icon: 'none'
        });
      }
    });
  },

  /**
   * 调用后端微信登录接口
   * @param {string} code wx.login 获取的 code
   * @param {object} userProfile wx.getUserProfile 返回的用户基础信息
   */
  wxLoginWithServer: function (code, userProfile) {
    wx.showLoading({ title: '登录中...', mask: true });
    wx.request({
      url: config.BASE_URL + '/user/wxLogin',
      method: 'POST',
      data: {
        code: code,
        nickName: userProfile.nickName,
        avatar: userProfile.avatarUrl,
        gender: userProfile.gender,
        country: userProfile.country,
        province: userProfile.province,
        city: userProfile.city,
      },
      success: function (res) {
        wx.hideLoading();
        console.log('wxLogin 返回：', res);
        if (res.data && res.data.code === '1') {
          // 与账号密码登录保持同一返回结构
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

          wx.setStorageSync('userInfo', userInfo);
          getApp().setGlobalData('userInfo', userInfo);

          wx.switchTab({
            url: '/pages/index/index'
          });
        } else {
          wx.showToast({
            title: (res.data && res.data.msg) || '微信登录失败',
            icon: 'none'
          });
        }
      },
      fail: function (err) {
        wx.hideLoading();
        console.log('微信登录请求失败', err);
        wx.showToast({
          title: '网络异常，请稍后再试',
          icon: 'none'
        });
      }
    });
  }
});