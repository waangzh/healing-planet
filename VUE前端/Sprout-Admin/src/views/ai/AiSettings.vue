<template>
  <div class="ai-settings">
    <div class="page-header">
      <h2 class="page-title">AI设置</h2>
    </div>
    
    <el-tabs v-model="activeTab" class="settings-tabs">
      <!-- 基础配置 -->
      <el-tab-pane label="基础配置" name="basic">
        <div class="admin-card">
          <div class="card-header">
            <h3 class="card-title">AI服务配置</h3>
          </div>
          <div class="card-body">
            <el-form :model="basicConfig" label-width="150px">
              <el-form-item label="服务提供商">
                <el-select v-model="basicConfig.provider" placeholder="选择AI服务提供商">
                  <el-option label="OpenAI" value="openai" />
                  <el-option label="百度文心" value="baidu" />
                  <el-option label="阿里通义" value="alibaba" />
                  <el-option label="腾讯混元" value="tencent" />
                </el-select>
              </el-form-item>
              
              <el-form-item label="API密钥">
                <el-input
                  v-model="basicConfig.apiKey"
                  type="password"
                  placeholder="请输入API密钥"
                  show-password
                />
              </el-form-item>
              
              <el-form-item label="API地址">
                <el-input
                  v-model="basicConfig.apiUrl"
                  placeholder="请输入API服务地址"
                />
              </el-form-item>
              
              <el-form-item label="模型版本">
                <el-select v-model="basicConfig.model" placeholder="选择模型版本">
                  <el-option label="GPT-4" value="gpt-4" />
                  <el-option label="GPT-3.5 Turbo" value="gpt-3.5-turbo" />
                  <el-option label="文心一言 4.0" value="ernie-4.0" />
                  <el-option label="通义千问" value="qwen-max" />
                </el-select>
              </el-form-item>
              
              <el-form-item label="请求超时">
                <el-input-number
                  v-model="basicConfig.timeout"
                  :min="5000"
                  :max="60000"
                  :step="1000"
                  controls-position="right"
                />
                <span style="margin-left: 8px; color: #909399;">毫秒</span>
              </el-form-item>
              
              <el-form-item label="最大重试次数">
                <el-input-number
                  v-model="basicConfig.maxRetries"
                  :min="0"
                  :max="5"
                  controls-position="right"
                />
              </el-form-item>
              
              <el-form-item label="启用日志">
                <el-switch v-model="basicConfig.enableLogging" />
                <span style="margin-left: 8px; color: #909399;">
                  记录API调用日志用于调试
                </span>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </el-tab-pane>
      
      <!-- 对话配置 -->
      <el-tab-pane label="对话配置" name="chat">
        <div class="admin-card">
          <div class="card-header">
            <h3 class="card-title">对话参数</h3>
          </div>
          <div class="card-body">
            <el-form :model="chatConfig" label-width="150px">
              <el-form-item label="创造性温度">
                <el-slider
                  v-model="chatConfig.temperature"
                  :min="0"
                  :max="2"
                  :step="0.1"
                  show-input
                  style="margin-right: 20px;"
                />
                <div class="config-tip">
                  值越高回复越有创造性，值越低回复越一致
                </div>
              </el-form-item>
              
              <el-form-item label="最大令牌数">
                <el-input-number
                  v-model="chatConfig.maxTokens"
                  :min="100"
                  :max="4000"
                  :step="100"
                  controls-position="right"
                />
                <div class="config-tip">
                  单次对话的最大字符数限制
                </div>
              </el-form-item>
              
              <el-form-item label="上下文长度">
                <el-input-number
                  v-model="chatConfig.contextLength"
                  :min="1"
                  :max="10"
                  controls-position="right"
                />
                <div class="config-tip">
                  保留的历史对话轮数
                </div>
              </el-form-item>
              
              <el-form-item label="系统提示词">
                <el-input
                  v-model="chatConfig.systemPrompt"
                  type="textarea"
                  :rows="4"
                  placeholder="请输入系统提示词"
                />
                <div class="config-tip">
                  定义AI助手的角色和行为规范
                </div>
              </el-form-item>
              
              <el-form-item label="禁用词汇">
                <el-tag
                  v-for="word in chatConfig.bannedWords"
                  :key="word"
                  closable
                  @close="removeBannedWord(word)"
                  style="margin-right: 8px; margin-bottom: 8px;"
                >
                  {{ word }}
                </el-tag>
                <el-input
                  v-if="showBannedWordInput"
                  ref="bannedWordInput"
                  v-model="newBannedWord"
                  size="small"
                  style="width: 120px;"
                  @keyup.enter="addBannedWord"
                  @blur="addBannedWord"
                />
                <el-button
                  v-else
                  size="small"
                  @click="showBannedWordInput = true"
                >
                  + 添加禁用词
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </el-tab-pane>
      
      <!-- 识别配置 -->
      <el-tab-pane label="识别配置" name="recognition">
        <div class="admin-card">
          <div class="card-header">
            <h3 class="card-title">植物识别参数</h3>
          </div>
          <div class="card-body">
            <el-form :model="recognitionConfig" label-width="150px">
              <el-form-item label="识别模型">
                <el-select v-model="recognitionConfig.model" placeholder="选择识别模型">
                  <el-option label="通用植物识别" value="general" />
                  <el-option label="多肉植物专用" value="succulent" />
                  <el-option label="花卉识别" value="flower" />
                  <el-option label="树木识别" value="tree" />
                </el-select>
              </el-form-item>
              
              <el-form-item label="置信度阈值">
                <el-slider
                  v-model="recognitionConfig.confidenceThreshold"
                  :min="0.1"
                  :max="1"
                  :step="0.05"
                  show-input
                  style="margin-right: 20px;"
                />
                <div class="config-tip">
                  低于此值的识别结果将被标记为不确定
                </div>
              </el-form-item>
              
              <el-form-item label="返回结果数">
                <el-input-number
                  v-model="recognitionConfig.topK"
                  :min="1"
                  :max="10"
                  controls-position="right"
                />
                <div class="config-tip">
                  返回最可能的识别结果数量
                </div>
              </el-form-item>
              
              <el-form-item label="图片预处理">
                <el-checkbox-group v-model="recognitionConfig.preprocessing">
                  <el-checkbox label="resize">自动缩放</el-checkbox>
                  <el-checkbox label="crop">智能裁剪</el-checkbox>
                  <el-checkbox label="enhance">增强对比度</el-checkbox>
                  <el-checkbox label="denoise">降噪处理</el-checkbox>
                </el-checkbox-group>
              </el-form-item>
              
              <el-form-item label="最大图片大小">
                <el-input-number
                  v-model="recognitionConfig.maxImageSize"
                  :min="1"
                  :max="10"
                  controls-position="right"
                />
                <span style="margin-left: 8px; color: #909399;">MB</span>
              </el-form-item>
              
              <el-form-item label="支持格式">
                <el-checkbox-group v-model="recognitionConfig.supportedFormats">
                  <el-checkbox label="jpg">JPG</el-checkbox>
                  <el-checkbox label="png">PNG</el-checkbox>
                  <el-checkbox label="webp">WebP</el-checkbox>
                  <el-checkbox label="bmp">BMP</el-checkbox>
                </el-checkbox-group>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </el-tab-pane>
      
      <!-- 安全配置 -->
      <el-tab-pane label="安全配置" name="security">
        <div class="admin-card">
          <div class="card-header">
            <h3 class="card-title">安全与审核</h3>
          </div>
          <div class="card-body">
            <el-form :model="securityConfig" label-width="150px">
              <el-form-item label="内容审核">
                <el-switch v-model="securityConfig.enableModeration" />
                <span style="margin-left: 8px; color: #909399;">
                  自动检测和过滤不当内容
                </span>
              </el-form-item>
              
              <el-form-item label="审核严格程度">
                <el-radio-group v-model="securityConfig.moderationLevel">
                  <el-radio label="low">宽松</el-radio>
                  <el-radio label="medium">中等</el-radio>
                  <el-radio label="high">严格</el-radio>
                </el-radio-group>
              </el-form-item>
              
              <el-form-item label="频率限制">
                <el-switch v-model="securityConfig.enableRateLimit" />
                <div class="config-tip">
                  限制单个用户的请求频率
                </div>
              </el-form-item>
              
              <el-form-item label="每分钟请求数">
                <el-input-number
                  v-model="securityConfig.requestsPerMinute"
                  :min="1"
                  :max="100"
                  controls-position="right"
                  :disabled="!securityConfig.enableRateLimit"
                />
              </el-form-item>
              
              <el-form-item label="IP白名单">
                <el-input
                  v-model="securityConfig.ipWhitelist"
                  type="textarea"
                  :rows="3"
                  placeholder="每行一个IP地址，支持CIDR格式"
                />
              </el-form-item>
              
              <el-form-item label="数据加密">
                <el-switch v-model="securityConfig.enableEncryption" />
                <span style="margin-left: 8px; color: #909399;">
                  加密存储用户对话数据
                </span>
              </el-form-item>
              
              <el-form-item label="日志保留期">
                <el-input-number
                  v-model="securityConfig.logRetentionDays"
                  :min="1"
                  :max="365"
                  controls-position="right"
                />
                <span style="margin-left: 8px; color: #909399;">天</span>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
    
    <!-- 保存按钮 -->
    <div class="settings-footer">
      <el-button type="primary" size="large" @click="saveSettings">
        <el-icon><Check /></el-icon>
        保存所有设置
      </el-button>
      <el-button size="large" @click="resetSettings">
        <el-icon><Refresh /></el-icon>
        重置设置
      </el-button>
      <el-button type="info" size="large" @click="testConnection">
        <el-icon><Link /></el-icon>
        测试连接
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Refresh, Link } from '@element-plus/icons-vue'

// 响应式数据
const activeTab = ref('basic')
const showBannedWordInput = ref(false)
const newBannedWord = ref('')
const bannedWordInput = ref(null)

const basicConfig = reactive({
  provider: 'openai',
  apiKey: '',
  apiUrl: 'https://api.openai.com/v1',
  model: 'gpt-3.5-turbo',
  timeout: 15000,
  maxRetries: 3,
  enableLogging: true
})

const chatConfig = reactive({
  temperature: 0.7,
  maxTokens: 1000,
  contextLength: 5,
  systemPrompt: '你是一个专业的植物养护助手，请用友好、专业的语气回答用户关于植物的问题。',
  bannedWords: ['不当词汇1', '不当词汇2']
})

const recognitionConfig = reactive({
  model: 'general',
  confidenceThreshold: 0.7,
  topK: 3,
  preprocessing: ['resize', 'crop'],
  maxImageSize: 5,
  supportedFormats: ['jpg', 'png', 'webp']
})

const securityConfig = reactive({
  enableModeration: true,
  moderationLevel: 'medium',
  enableRateLimit: true,
  requestsPerMinute: 20,
  ipWhitelist: '',
  enableEncryption: true,
  logRetentionDays: 30
})

// 方法
const addBannedWord = () => {
  if (newBannedWord.value && !chatConfig.bannedWords.includes(newBannedWord.value)) {
    chatConfig.bannedWords.push(newBannedWord.value)
    newBannedWord.value = ''
  }
  showBannedWordInput.value = false
}

const removeBannedWord = (word) => {
  const index = chatConfig.bannedWords.indexOf(word)
  if (index > -1) {
    chatConfig.bannedWords.splice(index, 1)
  }
}

const saveSettings = async () => {
  try {
    // 模拟保存API调用
    await new Promise(resolve => setTimeout(resolve, 1000))
    ElMessage.success('AI设置保存成功')
  } catch (error) {
    ElMessage.error('设置保存失败')
  }
}

const resetSettings = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要重置所有AI设置吗？此操作不可恢复！',
      '确认重置',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    // 重置为默认值
    Object.assign(basicConfig, {
      provider: 'openai',
      apiKey: '',
      apiUrl: 'https://api.openai.com/v1',
      model: 'gpt-3.5-turbo',
      timeout: 15000,
      maxRetries: 3,
      enableLogging: true
    })
    
    Object.assign(chatConfig, {
      temperature: 0.7,
      maxTokens: 1000,
      contextLength: 5,
      systemPrompt: '你是一个专业的植物养护助手，请用友好、专业的语气回答用户关于植物的问题。',
      bannedWords: []
    })
    
    Object.assign(recognitionConfig, {
      model: 'general',
      confidenceThreshold: 0.7,
      topK: 3,
      preprocessing: ['resize', 'crop'],
      maxImageSize: 5,
      supportedFormats: ['jpg', 'png', 'webp']
    })
    
    Object.assign(securityConfig, {
      enableModeration: true,
      moderationLevel: 'medium',
      enableRateLimit: true,
      requestsPerMinute: 20,
      ipWhitelist: '',
      enableEncryption: true,
      logRetentionDays: 30
    })
    
    ElMessage.success('设置已重置为默认值')
    
  } catch {
    // 用户取消操作
  }
}

const testConnection = async () => {
  if (!basicConfig.apiKey) {
    ElMessage.warning('请先配置API密钥')
    return
  }
  
  try {
    ElMessage.info('正在测试连接...')
    // 模拟连接测试
    await new Promise(resolve => setTimeout(resolve, 2000))
    ElMessage.success('AI服务连接测试成功')
  } catch (error) {
    ElMessage.error('连接测试失败，请检查配置')
  }
}

// 监听输入框显示状态
const watchBannedWordInput = () => {
  if (showBannedWordInput.value) {
    nextTick(() => {
      bannedWordInput.value?.focus()
    })
  }
}
</script>

<style lang="scss" scoped>
.ai-settings {
  .settings-tabs {
    .admin-card {
      margin-bottom: 0;
      border: none;
      box-shadow: none;
    }
  }
  
  .config-tip {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
    line-height: 1.4;
  }
  
  .settings-footer {
    margin-top: 40px;
    padding: 20px 0;
    text-align: center;
    border-top: 1px solid #e4e7ed;
    
    .el-button {
      margin: 0 8px;
      min-width: 120px;
    }
  }
}

:deep(.el-tabs__content) {
  padding-top: 20px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: #303133;
}

:deep(.el-slider) {
  width: 200px;
}

:deep(.el-checkbox-group) {
  .el-checkbox {
    margin-bottom: 8px;
    margin-right: 20px;
  }
}

@media (max-width: 768px) {
  .ai-settings {
    .settings-footer {
      .el-button {
        margin: 4px;
        width: 100%;
        max-width: 200px;
      }
    }
  }
  
  :deep(.el-form-item__label) {
    width: 100px !important;
  }
  
  :deep(.el-slider) {
    width: 150px;
  }
}
</style>
