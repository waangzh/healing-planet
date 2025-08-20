import { defineStore } from 'pinia';

export const useAiMessageStore = defineStore('aiMessage', {
  state: () => ({
    messages: [],
  }),
  
  actions: {
    // 添加消息到历史记录
    addMessage(message) {
      this.messages.push(message);
      // 保存到本地存储
      this.saveMessages();
    },
    
    // 添加多条消息
    addMessages(messageArray) {
      this.messages = [...this.messages, ...messageArray];
      this.saveMessages();
    },
    
    // 清空消息历史
    clearMessages() {
      this.messages = [];
      localStorage.removeItem('ai_chat_messages');
    },
    
    // 保存消息到localStorage
    saveMessages() {
      localStorage.setItem('ai_chat_messages', JSON.stringify(this.messages));
    },
    
    // 从localStorage加载消息
    loadMessages() {
      const savedMessages = localStorage.getItem('ai_chat_messages');
      if (savedMessages) {
        try {
          this.messages = JSON.parse(savedMessages);
        } catch (e) {
          console.error('Error parsing saved messages:', e);
          this.messages = [];
        }
      }
    }
  }
});
