<script setup>
import { ref, onMounted, onBeforeUnmount, computed } from 'vue';
import { useUserStore, useAiMessageStore } from '@/stores';
import { aiMessageStreamService } from '@/api/common';
import MarkdownIt from 'markdown-it';
import { Bottom, CloseBold } from '@element-plus/icons-vue';
import aiAvatar from '@/assets/img/ai-avatar.png';

// 初始化markdown-it
const md = new MarkdownIt({
  html: true,
  breaks: true,
  linkify: true,
  typographer: true,
  highlight: function (str, lang) {
    return `<pre class="language-${lang}"><code>${str}</code></pre>`;
  }
});

const userStore = useUserStore();
const messageStore = useAiMessageStore();
const inputMessage = ref('');
const isLoading = ref(false);
const messages = ref([]);
const chatBody = ref(null);
const showScrollButton = ref(false);
const isStreaming = ref(false); // 是否正在流式接收数据
let currentController = null; // 用于存储当前的AbortController

// 监听滚动事件
const handleScroll = () => {
  if (!chatBody.value) return;
  const { scrollTop, scrollHeight, clientHeight } = chatBody.value;
  // 当距离底部超过50px时显示按钮
  showScrollButton.value = scrollHeight - scrollTop - clientHeight > 50;
};

// 滚动到底部方法
const scrollToBottom = () => {
  if (!chatBody.value) return;
  chatBody.value.scrollTop = chatBody.value.scrollHeight;
};

// 关闭流式请求
const stopGeneration = () => {
  if (currentController) {
    currentController.abort();
    currentController = null;
    isLoading.value = false;
    isStreaming.value = false;
  }
};

// 清空所有消息
const clearAllMessages = () => {
  messageStore.clearMessages();
  messages.value = [];
};

// 加载存储的消息
const loadSavedMessages = () => {
  messageStore.loadMessages();
  
  // 不进行去重，直接加载
  messages.value = [...messageStore.messages];
  
  // 加载后滚动到底部
  setTimeout(scrollToBottom, 100);
};

// 发送消息
const sendMessage = () => {
  if (isLoading.value) {
    // 如果正在加载，则中断生成
    stopGeneration();
    return;
  }

  if (!inputMessage.value.trim()) return;

  const userMessage = {
    from: 'user',
    text: inputMessage.value
  };
  
  // 添加用户消息
  messages.value.push(userMessage);
  messageStore.addMessage(userMessage);
  
  const userMessageText = inputMessage.value;
  inputMessage.value = '';
  isLoading.value = true;
  isStreaming.value = false;

  // 关闭之前的连接
  if (currentController) {
    currentController.abort();
    currentController = null;
  }

  // 添加空AI消息
  const aiMessage = { 
    from: 'ai', 
    text: '', 
    rawText: '' 
  };
  messages.value.push(aiMessage);
  const currentMessageIndex = messages.value.length - 1;

  let fullContent = '';

  // 创建新的AbortController
  currentController = new AbortController();
  const signal = currentController.signal;

  // 使用aiMessageStreamService接口处理流式请求
  aiMessageStreamService({
    id: userStore.user.id,
    userMessage: userMessageText
  }, { signal })
  .then(response => {
    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8', { fatal: false });
    let buffer = ''; // 用于存储可能被分割的不完整数据
    
    function processStream() {
      reader.read().then(({ done, value }) => {
        if (done) {
          console.log('流式请求完成');
          // 处理缓冲区中剩余的数据
          if (buffer.length > 0) {
            console.log('处理缓冲区中剩余数据:', buffer);
            processBufferData(buffer);
            buffer = '';
          }
          
          isLoading.value = false;
          isStreaming.value = false;
          
          // 在完成时再保存到store，避免重复保存
          if (fullContent) {
            // 确保最终内容再渲染一次
            messages.value[currentMessageIndex].rawText = fullContent;
            messages.value[currentMessageIndex].text = md.render(fullContent);
            
            messageStore.addMessage({
              from: 'ai',
              text: md.render(fullContent),
              rawText: fullContent
            });
          }
          return;
        }
        
        try {
          // 使用更安全的解码方式
          const chunk = decoder.decode(value, { stream: true });
          console.log('接收到原始数据块:', chunk);
          
          // 将新数据添加到缓冲区
          buffer += chunk;
          
          // 处理缓冲区中的完整SSE消息
          processBufferData(buffer);
          
          // 继续处理流
          if (!signal.aborted) {
            processStream();
          }
        } catch (error) {
          console.error('数据解码错误:', error);
          buffer = ''; // 出错时清空缓冲区
          
          if (!signal.aborted) {
            processStream(); // 继续尝试处理后续数据
          }
        }
      }).catch(error => {
        if (error.name === 'AbortError') {
          console.log('请求被中止');
          isLoading.value = false;
          isStreaming.value = false;
          return;
        }
        
        console.error('流处理错误:', error);
        messages.value[currentMessageIndex].text = '连接出错，请重试';
        isLoading.value = false;
        isStreaming.value = false;
      });
    }
    
    // 处理缓冲区中的数据
    function processBufferData(bufferData) {
      // 寻找完整的SSE消息 (data: xxx\n\n 格式)
      const events = bufferData.split('\n\n');
      
      // 最后一段可能不完整，保留在缓冲区
      if (events.length > 0) {
        // 处理除最后一段外的所有完整消息
        for (let i = 0; i < events.length - 1; i++) {
          const event = events[i];
          processEventData(event);
        }
        
        // 检查最后一段是否是完整消息
        const lastEvent = events[events.length - 1];
        if (lastEvent.endsWith('\n')) {
          // 如果以\n结尾，说明是完整消息
          processEventData(lastEvent);
          buffer = '';
        } else {
          // 不完整的保留在缓冲区
          buffer = lastEvent;
        }
      }
    }
    
    // 处理单个事件数据
    function processEventData(eventData) {
      // 处理事件中的每一行
      const lines = eventData.split('\n');
      let receivedData = false;
      
      for (const line of lines) {
        if (line.startsWith('data:')) {
          const dataContent = line.substring(5);
          
          // 处理空行数据 - 确保空行也被当作换行符处理
          if (dataContent === '' || dataContent.trim() === '') {
            console.log('接收到空行数据 - 添加换行');
            fullContent += '\n';
            receivedData = true;
            isStreaming.value = true;
          } else {
            // 处理有内容的数据行
            const data = dataContent.trim();
            if (data) {
              // 解码后端可能发送的转义字符
              const decodedData = data
                .replace(/\\n/g, '\n')
                .replace(/\\r/g, '\r')
                .replace(/\\t/g, '\t')
                .replace(/\\\\/g, '\\');
              
              console.log('解码后数据:', decodedData);
              fullContent += decodedData;
              receivedData = true;
              isStreaming.value = true;
            }
          }
        } else if (line.includes('event: complete')) {
          console.log('收到完成信号');
          isLoading.value = false;
          isStreaming.value = false;
          return;
        }
      }
      
      // 只有在真正接收到数据时才更新消息和渲染
      if (receivedData) {
        // 更新消息文本并渲染
        messages.value[currentMessageIndex].rawText = fullContent;
        messages.value[currentMessageIndex].text = md.render(fullContent);
        console.log('更新后的累积内容:', fullContent);
        
        // 滚动到底部
        setTimeout(scrollToBottom, 0);
      }
    }
    
    // 开始处理流
    processStream();
  })
  .catch(error => {
    if (error.name === 'AbortError') {
      console.log('请求被中止');
      return;
    }
    
    console.error('请求错误:', error);
    messages.value[currentMessageIndex].text = '发送失败，请重试';
    isLoading.value = false;
    isStreaming.value = false;
  });
};

onMounted(() => {
  if (chatBody.value) {
    chatBody.value.addEventListener('scroll', handleScroll);
  }
  // 加载存储的消息
  loadSavedMessages();
});

onBeforeUnmount(() => {
  if (chatBody.value) {
    chatBody.value.removeEventListener('scroll', handleScroll);
  }
  if (currentController) {
    currentController.abort();
  }
});
</script>

<template>
  <div class="ai-page">
    <el-card class="chat-window">
      <!-- 头部 -->
      <div class="chat-header">
        <div class="header-title">
          <el-avatar :size="32" :src="aiAvatar" />
          <span>小绿助手</span>
        </div>
        <div class="status-actions">
          <el-button 
            v-if="messages.length > 0" 
            size="small" 
            type="danger" 
            @click="clearAllMessages" 
            class="clear-button"
          >
            清空对话
          </el-button>
          <div class="status-indicator" :class="{ active: isLoading }">
            {{ isLoading ? (isStreaming ? '正在回复...' : '思考中...') : '在线' }}
          </div>
        </div>
      </div>

      <!-- 聊天内容区 -->
      <div class="chat-body" ref="chatBody">
        <div v-if="messages.length === 0" class="empty-state">
          <el-empty description="有关于植物养护的问题吗？开始和智能助手对话吧" />
          <div class="chat-suggestions">
            <div class="suggestion-title">您可以问我这些问题:</div>
            <div class="suggestion-items">
              <div class="suggestion-item" @click="inputMessage = '如何养护多肉植物？'; sendMessage()">
                如何养护多肉植物？
              </div>
              <div class="suggestion-item" @click="inputMessage = '我的绿萝叶子发黄是什么原因？'; sendMessage()">
                我的绿萝叶子发黄是什么原因？
              </div>
              <div class="suggestion-item" @click="inputMessage = '适合新手种植的室内植物有哪些？'; sendMessage()">
                适合新手种植的室内植物有哪些？
              </div>
              <div class="suggestion-item" @click="inputMessage = '植物浇水的最佳时间是什么时候？'; sendMessage()">
                植物浇水的最佳时间是什么时候？
              </div>
            </div>
          </div>
        </div>
        <template v-else>
          <div v-for="(message, index) in messages" :key="index" :class="['message-item', message.from]">
            <div class="avatar">
              <el-avatar :size="40" :src="message.from === 'user' ? userStore.user.avatar : aiAvatar" />
            </div>
            <div class="message-content">
              <div class="message-text" v-html="message.text"></div>
            </div>
          </div>
        </template>
      </div>

      <!-- 输入区域 -->
      <div class="chat-footer">
        <div v-show="showScrollButton" class="scroll-bottom-btn" @click="scrollToBottom">
          <div class="circle-button">
            <el-icon><Bottom /></el-icon>
          </div>
        </div>
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="1"
          :autosize="{ minRows: 1, maxRows: 4 }"
          placeholder="请输入问题..."
          @keyup.enter.prevent="sendMessage"
          :disabled="isLoading"
          class="custom-input"
        />
        <el-button 
          :type="isLoading ? 'danger' : 'primary'" 
          @click="sendMessage" 
          class="custom-button"
        >
          <el-icon v-if="isLoading"><close-bold /></el-icon>
          <span>{{ isLoading ? '停止' : '发送' }}</span>
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.ai-page {
  padding: 20px;
  height: calc(85vh - 40px);
  display: flex;
  flex-direction: column;
}

.chat-window {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  :deep(.el-card__body) {
    height: 100%;
    padding: 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
}

.chat-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--el-color-primary-light-9);

  .header-title {
    display: flex;
    align-items: center;
    gap: 12px;
    font-size: 16px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  .status-actions {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .clear-button {
    font-size: 12px;
    height: 28px;
    padding: 0 10px;
  }

  .status-indicator {
    padding: 4px 12px;
    border-radius: 12px;
    font-size: 14px;
    color: var(--el-text-color-secondary);
    background: var(--el-color-primary-light-9);

    &.active {
      color: var(--el-color-success);
      background: var(--el-color-success-light-9);
    }
  }
}

.chat-body {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background: var(--el-bg-color-page);

  .empty-state {
    height: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 30px;
  }

  .chat-suggestions {
    max-width: 600px;
    width: 100%;
    
    .suggestion-title {
      font-size: 16px;
      font-weight: 500;
      margin-bottom: 12px;
      color: var(--el-text-color-primary);
      text-align: center;
    }
    
    .suggestion-items {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      justify-content: center;
    }
    
    .suggestion-item {
      padding: 10px 16px;
      background: var(--el-color-primary-light-9);
      border: 1px solid var(--el-color-primary-light-5);
      border-radius: 20px;
      cursor: pointer;
      font-size: 14px;
      color: var(--el-color-primary-dark-2);
      transition: all 0.3s;
      
      &:hover {
        background: var(--el-color-primary-light-7);
        transform: translateY(-2px);
      }
    }
  }

  .message-item {
    display: flex;
    gap: 12px;
    margin-bottom: 24px;

    &.user {
      flex-direction: row-reverse;

      .message-content {
        align-items: flex-end;
      }

      .message-text {
        background: var(--el-color-primary);
        color: white;
        border-radius: 8px 8px 0 8px;
      }
    }

    &.ai .message-text {
      background: var(--el-fill-color-light);
      color: var(--el-text-color-regular);
      border-radius: 8px 8px 8px 0;
      white-space: pre-wrap;
    }

    .message-content {
      max-width: 80%;
      display: flex;
      flex-direction: column;
    }

    .message-text {
      padding: 12px 16px;
      font-size: 14px;
      line-height: 1.5;
      word-break: break-word;

      :deep(pre) {
        background: var(--el-fill-color);
        padding: 12px;
        border-radius: 4px;
        margin: 8px 0;
        overflow-x: auto;
      }

      :deep(code) {
        font-family: monospace;
      }
      
      :deep(ul), :deep(ol) {
        padding-left: 20px;
        margin: 10px 0;
      }
      
      :deep(p) {
        margin: 8px 0;
        white-space: normal;
      }
      
      :deep(img) {
        max-width: 100%;
        border-radius: 4px;
      }
    }
  }
}

.chat-footer {
  position: relative;
  padding: 16px 20px;
  border-top: 1px solid var(--el-border-color-lighter);
  display: flex;
  gap: 12px;
  background: white;

  .custom-input {
    flex: 1;

    :deep(.el-textarea__inner) {
      border-radius: 8px;
      resize: none;
      box-shadow: 0 0 0 1px var(--el-border-color);
      transition: all 0.3s;
      padding: 10px 15px;

      &:focus {
        box-shadow: 0 0 0 1px var(--el-color-primary);
      }
    }
  }

  .custom-button {
    width: 90px;
    height: 100%;
    border-radius: 8px;
    transition: all 0.3s;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;

    &:hover {
      transform: translateY(-1px);
    }
  }
}

.scroll-bottom-btn {
  position: absolute;
  left: 50%;
  top: -70px;
  transform: translateX(-50%);
  z-index: 10;

  .circle-button {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background: var(--el-color-primary);
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: all 0.3s ease;
    box-shadow: 0 2px 8px rgba(16, 185, 129, 0.2);

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
    }

    .el-icon {
      font-size: 16px;
    }
  }
}
</style>
