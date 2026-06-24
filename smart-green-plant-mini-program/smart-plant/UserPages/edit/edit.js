const config = require('../../utils/config.js');
// pages/edit/edit.js
Page({

  /**
   * 页面的初始数据
   */
  data: {
    userInfo:{
      id: '',
      nickName: '',
      password: '',
      email: 'example@example.com',
      phone: '1234567890',
      avatar: '',
      username:''
    },
    introduction: ''
  },
  onLoad() {
    var userInfo = wx.getStorageSync('userInfo');
    console.log("用户中心信息:",wx.getStorageSync('userInfo'));
    this.setData({
      userInfo: userInfo
    });
  },
  onNicknameInput(e) {
    this.setData({
      'userInfo.nickName': e.detail.value
    });
  },
  onEmailAddressInput(e){
    this.setData({
      'userInfo.email': e.detail.value
    });
  },
  onPhoneInput(e){
    this.setData({
      'userInfo.phone': e.detail.value
    });
  },
  onIntroductionInput(e) {
    this.setData({
      introduction: e.detail.value
    });
  },
  //更换头像和上传
  chooseAndUploadAvatar() {
    const that = this;
    wx.chooseImage({
      count: 1, // 只允许选择一张图片
      sizeType: ['compressed'], // 选择压缩后的图片
      sourceType: ['album', 'camera'], // 可以从相册或相机选择
      success(res) {
        const tempFilePaths = res.tempFilePaths; // 获取选中图片的临时路径
        const filePath = tempFilePaths[0]; // 取第一张图片的路径
        that.setData({
          avatarUrl: filePath
        });
        wx.uploadFile({
          url: config.BASE_URL + '/common/upload',
          header:{
            token: wx.getStorageSync('token')
          },
          method: 'POST',
          filePath: filePath,
          name: 'file', 
          success(uploadRes) {
            console.log("上传头像后返回数据:",uploadRes.data);
            const res = JSON.parse(uploadRes.data);
            console.log("json解析的数据：",res);
            if (res.code === '1') { 
              wx.showToast({
                title: '上传成功',
                icon: 'success',
                duration: 2000
              });
              that.setData({
                'userInfo.avatar': res.data
              });
              console.log("上传后的头像路径:",that.data.userInfo.avatar);
            } else {
              wx.showToast({
                title: '上传失败1：' ,
                icon: 'none',
                duration: 2000
              });
            }
          },
          fail(err) {
            console.log("上传失败信息:",err);
            wx.showToast({
              title: '上传失败2：' + err.message,
              icon: 'none',
              duration: 2000
            });
          }
        });
      },
      fail(err) {
        wx.showToast({
          title: '选择图片失败：' + err.message,
          icon: 'none',
          duration: 2000
        });
      }
    });
  },
  saveUserInfo() {
    // 将编辑后的用户信息发送到服务器保存
    var userInfo = this.data.userInfo;
    wx.request({
      url: config.BASE_URL + '/user/update',
      method: 'PUT',
      header: {
        token: wx.getStorageSync('token')
      },
      data: {
        id: userInfo.id,
        nickName: userInfo.nickName,
        avatar: userInfo.avatar,
        email: userInfo.email,
        phone: userInfo.phone
      },
      success: (res) => {
        if (res.statusCode === 200) {
          wx.setStorageSync('userInfo', this.data.userInfo);
          console.log("编辑后用户信息:",userInfo);
          wx.showToast({
            title: '保存成功',
            icon: 'success'
          });
        } else {
          wx.showToast({
            title: '保存失败',
            icon: 'none'
          });
        }
      }
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