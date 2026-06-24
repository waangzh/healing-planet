const { parseMarkdown } = require('../../utils/markdown.js');
const config = require('../../utils/config.js');
Page({
  data: {
    imagePath: '',
    markdown: "",
    htmlNodes: null,
    loading: false
  },

  // 关闭识别结果
  closeResult() {
    this.setData({
      imagePath: '',
      markdown: "",
      htmlNodes: null
    });
  },

  chooseImage() {
    if (this.data.loading) return;

    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const tempFilePath = res.tempFiles[0].tempFilePath;
        this.setData({ 
          imagePath: tempFilePath, 
          markdown: '', 
          htmlNodes: null,
          loading: true 
        });
        this.uploadImage(tempFilePath);
      },
      fail: (err) => {
        console.error('选择图片失败:', err);
        wx.showToast({ title: '选择图片失败', icon: 'none' });
      }
    });
  },

  uploadImage(filePath) {
    wx.uploadFile({
      url: config.BASE_URL + '/common/upload',
      method: 'POST',
      header: {
        token: wx.getStorageSync('token')
      },
      filePath: filePath,
      name: 'file',
      success: (res) => {
        try {
          const data = JSON.parse(res.data);
          if (data.code === 200 || data.code === '1') { // 兼容不同后端返回码
            const imageUrl = data.data;
            console.log("上传成功，图片URL:", imageUrl);
            this.identifyPlant(imageUrl);
          } else {
            throw new Error(data.msg || '上传失败');
          }
        } catch (e) {
          console.error('解析上传结果失败:', e);
          this.setData({ 
            markdown: `上传失败: ${e.message}`, 
            loading: false 
          });
          wx.showToast({ title: '上传失败', icon: 'none' });
        }
      },
      fail: (err) => {
        console.error('上传失败:', err);
        this.setData({ 
          markdown: '上传失败，请检查网络', 
          loading: false 
        });
        wx.showToast({ title: '上传失败', icon: 'none' });
      }
    });
  },

  // 将HTML直接传递给rich-text组件
  processHtml(html) {
    if (!html) return '';
    // 直接返回HTML字符串，由rich-text组件处理
    return html;
  },

  identifyPlant(imageUrl) {
    const encodedUrl = encodeURIComponent(imageUrl);
    wx.request({
      url: config.PLANT_IDENTIFY_URL + `/plants/identify?imgUrl=${encodedUrl}`,
      method: 'POST',
      data: {}, 
      header: {
        'content-type': 'application/x-www-form-urlencoded',
        'token': wx.getStorageSync('token') 
      },
      success: (res) => {
        if (res.data.code === 200) {
          console.log("识别成功:", res.data.data);
          const markdown = res.data.data;
          const html = parseMarkdown(markdown);
          
          this.setData({
            markdown: markdown,
            htmlNodes: html,
            loading: false
          });
        } else {
          console.error('识别失败:', res.data.msg);
          this.setData({
            markdown: '',
            loading: false
          });
          wx.showToast({ 
            title: res.data.msg || '识别失败', 
            icon: 'none' 
          });
        }
      },
      fail: (err) => {
        console.error('识别请求失败:', err);
        this.setData({
          markdown: '',
          loading: false
        });
        wx.showToast({ title: '识别失败', icon: 'none' });
      }
    });
  }
});