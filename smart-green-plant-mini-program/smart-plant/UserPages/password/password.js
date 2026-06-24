// pages/password/password.js
const config = require('../../utils/config.js');
Page({

  data: {
    origin:'',
    oldPassword: '',
    newPassword: '',
    confirmNewPassword: ''
  },
  onOldPasswordInput(e) {
    this.setData({
      oldPassword: e.detail.value
    });
  },
  onNewPasswordInput(e) {
    this.setData({
      newPassword: e.detail.value
    });
  },
  onConfirmNewPasswordInput(e) {
    this.setData({
      confirmNewPassword: e.detail.value
    });
  },
  saveNewPassword() {
    const {oldPassword,origin,newPassword, confirmNewPassword } = this.data;
    if (newPassword!== confirmNewPassword) {
      wx.showToast({
        title: '新密码与确认密码不一致',
        icon: 'error'
      });
      return;
    }
    if (oldPassword!== origin) {
      console.log("origin:",origin),
      wx.showToast({
        title: '旧密码输入错误',
        icon: 'error'
      });
      return;
    }
    // 发送请求到服务器修改密码
    var id = wx.getStorageSync('userInfo').id;
    var password = this.data.newPassword;
    wx.request({
      url: config.BASE_URL + '/user',
      method: 'PUT',
      header: {
        token: wx.getStorageSync('token')
      },
      data: {
        id,
        password,
      },
      success: (res) => {
        console.log("res:",res);
        if (res.data.code === "1") {
          this.setData({
            'origin':password,
            'oldPassword': '',
            'newPassword':'',
            'confirmNewPassword':''
          });
          var userInfo = wx.getStorageSync('userInfo');
          userInfo.password = password;
          wx.setStorageSync('userInfo', userInfo);
          wx.showToast({
            title: '密码修改成功',
            icon: 'success'
          });
        } else {
          wx.showToast({
            title: '密码修改失败',
            icon: 'none'
          });
        }
      }
    });
  },

  /**
   * 生命周期函数--监听页面加载
   */
  onLoad(options) {
    var userInfo = wx.getStorageSync('userInfo');
    this.data.origin = userInfo.password;
  },

  /**
   * 生命周期函数--监听页面初次渲染完成
   */
  onReady() {

  },

  /**
   * 生命周期函数--监听页面显示
   */
  onShow() {

  },

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