const { parseMarkdown } = require('../../utils/markdown.js');
const config = require('../../utils/config.js');

Page({
  data: {
    messages: [],
    inputMessage: "",
    isLoading: false,
    userAvatar: "/static/user/user1.jpg",
    scrollToView: "",
    showScrollButton: false,
  },

  onLoad: function () {
    // 获取用户头像等信息
    var userInfo = wx.getStorageSync('userInfo');
    if (userInfo) {
      this.setData({
        userAvatar: userInfo.avatar,
      });
    }
    // 从本地存储加载历史消息
    const historyMessages = wx.getStorageSync("aiChatHistory") || [];
    // 处理历史消息中的markdown
    historyMessages.forEach(msg => {
      if (msg.from === 'ai') {
        msg.html = parseMarkdown(msg.text);
      }
    });
    this.setData({
      messages: historyMessages,
    });
  },
  onPullDownRefresh: function(){
    console.log("下拉刷新");
    setTimeout(() => {
      wx.hideNavigationBarLoading(); // 隐藏导航条加载动画
      wx.stopPullDownRefresh(); // 停止下拉刷新
    }, 2000); // 延迟2秒
  },
  // 保存消息到本地存储
  saveMessages: function (messages) {
    wx.setStorageSync("aiChatHistory", messages);
  },

  handleInput: function (e) {
    this.setData({
      inputMessage: e.detail.value,
    });
  },

  sendMessage: async function () {
    if (!this.data.inputMessage.trim() || this.data.isLoading) return;

    const userMessage = this.data.inputMessage;
    const messages = [...this.data.messages];

    messages.push({
      from: "user",
      text: userMessage,
    });

    this.setData({
      messages,
      inputMessage: "",
      isLoading: true,
    });

    // 保存消息到本地存储
    this.saveMessages(messages);
    this.scrollToBottom();

    try {
      // 创建 FormData 对象
      const data = {
        userMessage: userMessage,
        id: getApp().globalData.userInfo?.id || "1",
      };

      // 使用回调方式发送请求
      wx.request({
        url: config.BASE_URL + "/common/chat",
        method: "POST",
        header: {
          "content-type": "application/x-www-form-urlencoded",
          token: wx.getStorageSync("token") || "",
        },
        data: {
          userMessage: userMessage,
          id: getApp().globalData.userInfo?.id || "1",
        },
        success: (res) => {
          console.log(res);
          if (res.data?.code === "1") {
            const aiReply = res.data.data;
            // 解析markdown为HTML
            const htmlContent = parseMarkdown(aiReply);
            messages.push({
              from: "ai",
              text: aiReply,
              html: htmlContent
            });
            // 保存包含AI回复的消息到本地存储
            this.saveMessages(messages);
          } else {
            wx.showToast({
              title: res.data.msg || "请求失败",
              icon: "none",
            });
          }
          this.setData({
            messages,
            isLoading: false,
          });
          this.scrollToBottom();
        },
        fail: (error) => {
          console.error("发送消息失败：", error);
          wx.showToast({
            title: "发送失败，请重试",
            icon: "none",
          });
          this.setData({
            isLoading: false,
          });
        },
        complete: () => {
          // 如果有需要在完成时执行的代码
        },
      });
    } catch (error) {
      wx.showToast({
        title: "发送失败，请重试",
        icon: "none",
      });
      console.error("发送消息失败：", error);
      this.setData({
        isLoading: false,
      });
    }
  },

  handleScroll: function (e) {
    const { scrollTop, scrollHeight } = e.detail;
    const clientHeight = wx.getSystemInfoSync().windowHeight - 160; // 减去头部和底部高度

    this.setData({
      showScrollButton: scrollHeight - scrollTop - clientHeight > 50,
    });
  },

  scrollToBottom: function () {
    const messages = this.data.messages;
    if (messages.length > 0) {
      this.setData({
        scrollToView: `msg-${messages.length - 1}`,
      });
    }
  },
});
