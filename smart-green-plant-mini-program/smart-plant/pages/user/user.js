// pages/user/user.js
const config = require('../../utils/config.js');
Page({
  data: {
    userInfo:{
      id: '',
      nickName: '',
      password: '',
      email: 'example@example.com',
      phone: '1234567890',
      avatar: '',
      username:''
    }
  },

  onLoad: function() {
    // this.getUserInfo();
    var userInfo = wx.getStorageSync('userInfo');
    console.log("用户中心信息:",wx.getStorageSync('userInfo'));
    this.setData({
      userInfo: userInfo
    });
  },
  onShow: function() {
    console.log("从后端获取用户信息");
    wx.request({
      url: config.BASE_URL + '/user/' + this.data.userInfo.id,
      method: 'GET',
      header: {
        token: wx.getStorageSync('token')
      },
      success: (res) => {
        if (res.statusCode === 200) {
          this.setData({
            userInfo: res.data.data
          });
        } else {
          console.error('用户信息获取失败:', res.statusCode, res.data);
        }
      },
      fail: (err) => {
        console.error('Request failed:', err);
      }
    });
  },
  getUserInfo: function() {
    wx.request({
      url: config.BASE_URL + '/user/' + this.data.userInfo.id,
      method: 'GET',
      header: {
        token: wx.getStorageSync('token')
      },
      success: (res) => {
        if (res.statusCode === 200) {
          this.setData({
            userInfo: res.data.data
          });
        } else {
          console.error('用户信息获取失败:', res.statusCode, res.data);
        }
      },
      fail: (err) => {
        console.error('Request failed:', err);
      }
    });
  },
  onPullDownRefresh: function(){
    console.log("下拉刷新");
    setTimeout(() => {
      wx.hideNavigationBarLoading(); // 隐藏导航条加载动画
      wx.stopPullDownRefresh(); // 停止下拉刷新
    }, 2000); // 延迟2秒
  },

  openAvatarPicker: function() {
    wx.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const tempFilePaths = res.tempFilePaths;
        this.setData({
          user: {
            ...this.data.user,
            avatarUrl: tempFilePaths[0]
          }
        });
      }
    });
  },

  navigateToEditUserInfo: function() {
    wx.navigateTo({
      url: '../editUserInfo/editUserInfo'
    });
  },

  /**
   * 生命周期函数--监听页面初次渲染完成
   */
  onReady() {

  },

  /**
   * 生命周期函数--监听页面显示
   */


  /**
   * 生命周期函数--监听页面隐藏
   */
  onHide() {

  },

  /**
   * 生命周期函数--监听页面卸载
   */
  onUnload() {

  },

  /**
   * 页面相关事件处理函数--监听用户下拉动作
   */
  onPullDownRefresh() {

  },

  /**
   * 页面上拉触底事件的处理函数
   */
  onReachBottom() {

  },

  /**
   * 用户点击右上角分享
   */
  onShareAppMessage() {

  }
})