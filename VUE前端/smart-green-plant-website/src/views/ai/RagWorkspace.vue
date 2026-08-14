<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { marked } from 'marked'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Bottom,
  Camera,
  ChatDotRound,
  CircleCheckFilled,
  Close,
  Connection,
  DataAnalysis,
  Delete,
  Document,
  PictureFilled,
  Promotion,
  Search,
  UploadFilled,
  WarningFilled
} from '@element-plus/icons-vue'
import { useAiMessageStore, useUserStore } from '@/stores'
import { getPlantInstanceId } from '@/api/plantinstance'
import { diagnosePlant, ragChatStream, searchEvidence } from '@/api/rag'
import aiAvatar from '@/assets/ai-avatar.png'

marked.setOptions({ gfm: true, breaks: true })

const MODES = [
  { value: 'chat', label: '养护问答', icon: ChatDotRound, hint: '结合知识与实时状态' },
  { value: 'diagnose', label: '叶片诊断', icon: Camera, hint: '融合图像、知识和传感器' },
  { value: 'search', label: '知识检索', icon: Search, hint: '查找可信养护资料' }
]

const EVIDENCE_META = {
  PLANT_KNOWLEDGE: { label: '植物百科', tone: 'knowledge', icon: Document },
  CARE_GUIDE: { label: '养护指南', tone: 'knowledge', icon: Document },
  COMMUNITY_POST: { label: '社区经验', tone: 'community', icon: ChatDotRound },
  LIVE_STATE: { label: '实时状态', tone: 'state', icon: Connection },
  SENSOR_HISTORY: { label: '历史趋势', tone: 'state', icon: DataAnalysis },
  VISUAL_OBSERVATION: { label: '图像观察', tone: 'visual', icon: PictureFilled },
  DISEASE_KNOWLEDGE: { label: '病害知识', tone: 'warning', icon: Document },
  SENSOR_CONSISTENCY: { label: '传感器印证', tone: 'state', icon: CircleCheckFilled }
}

const QUICK_QUESTIONS = {
  chat: ['今天需要浇水吗？', '最近的生长环境有异常吗？', '叶片发黄可能是什么原因？'],
  diagnose: ['这片叶子可能是什么问题？', '请结合环境数据判断病害风险', '给出进一步检查建议'],
  search: ['适合室内耐阴的植物', '绿萝黄叶的常见原因', '多肉植物夏季养护方法']
}

const userStore = useUserStore()
const messageStore = useAiMessageStore()
const mode = ref('chat')
const plants = ref([])
const selectedPlantId = ref(null)
const plantLoading = ref(false)
const messages = ref([])
const inputMessage = ref('')
const isLoading = ref(false)
const isStreaming = ref(false)
const chatBody = ref(null)
const showScrollButton = ref(false)
const activeEvidence = ref(null)
const currentEvidence = ref([])
const imageFile = ref(null)
const imagePreview = ref('')
const fileInput = ref(null)
let currentController = null

const selectedPlant = computed(() => plants.value.find((plant) => plant.id === selectedPlantId.value))
const currentMode = computed(() => MODES.find((item) => item.value === mode.value))
const quickQuestions = computed(() => QUICK_QUESTIONS[mode.value])
const userId = computed(() => userStore.user?.id)

const evidenceMeta = (type) => EVIDENCE_META[type] || { label: '其他证据', tone: 'neutral', icon: Document }
const scoreText = (evidence) => {
  const score = evidence.finalScore ?? evidence.rerankScore ?? evidence.retrievalScore
  return typeof score === 'number' ? `${Math.round(score * 100)}%` : '已采用'
}
const renderMarkdown = (text = '') => marked.parse(text)
const formatSource = (evidence) => evidence.title || evidence.metadata?.plantName || evidence.sourceType || '未命名来源'
const formatTime = (timestamp) => {
  if (!timestamp) return '本次会话'
  const date = new Date(timestamp)
  return Number.isNaN(date.getTime()) ? '本次会话' : date.toLocaleString('zh-CN', { hour12: false })
}

const loadPlants = async () => {
  if (!userId.value) return
  plantLoading.value = true
  try {
    const response = await getPlantInstanceId(userId.value)
    if (response.data.code === '1') {
      plants.value = response.data.data || []
      if (plants.value.length) selectedPlantId.value = plants.value[0].id
    }
  } catch {
    ElMessage.warning('植物列表暂时无法加载，仍可使用通用知识检索')
  } finally {
    plantLoading.value = false
  }
}

const loadSavedMessages = () => {
  messageStore.loadMessages()
  messages.value = messageStore.messages
    .filter((message) => message.from === 'user' || message.from === 'ai')
    .map((message, index) => ({
      id: message.id || `saved-${index}`,
      from: message.from,
      rawText: message.rawText || (message.from === 'user' ? message.text : ''),
      text: message.rawText || message.text || '',
      evidence: message.evidence || [],
      mode: message.mode || 'chat'
    }))
  const lastEvidence = [...messages.value].reverse().find((message) => message.evidence?.length)?.evidence || []
  currentEvidence.value = lastEvidence
}

const persistMessage = (message) => {
  messageStore.addMessage({
    id: message.id,
    from: message.from,
    text: message.text,
    rawText: message.rawText,
    evidence: message.evidence || [],
    mode: message.mode
  })
}

const scrollToBottom = () => nextTick(() => {
  if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight
})

const handleScroll = () => {
  if (!chatBody.value) return
  const { scrollTop, scrollHeight, clientHeight } = chatBody.value
  showScrollButton.value = scrollHeight - scrollTop - clientHeight > 80
}

const selectEvidence = (evidence) => {
  activeEvidence.value = evidence
}

const useQuickQuestion = (question) => {
  inputMessage.value = question
  if (mode.value !== 'diagnose' || imageFile.value) sendMessage()
}

const setMode = (nextMode) => {
  if (isLoading.value) return
  mode.value = nextMode
  activeEvidence.value = null
  if (nextMode !== 'diagnose') clearImage()
}

const chooseImage = () => fileInput.value?.click()
const handleImageChange = (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择 JPG、PNG 等图片文件')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 10 MB')
    return
  }
  imageFile.value = file
  const reader = new FileReader()
  reader.onload = () => {
    imagePreview.value = reader.result
  }
  reader.readAsDataURL(file)
}

const clearImage = () => {
  imageFile.value = null
  imagePreview.value = ''
  if (fileInput.value) fileInput.value.value = ''
}

const stopGeneration = () => {
  currentController?.abort()
  currentController = null
  isLoading.value = false
  isStreaming.value = false
}

const createPayload = (query) => ({
  userId: userId.value,
  plantInstanceId: selectedPlant.value?.id || null,
  canonicalPlantId: selectedPlant.value?.plantId ? String(selectedPlant.value.plantId) : null,
  query
})

const pushConversation = (query) => {
  const now = Date.now()
  const userMessage = {
    id: `user-${now}`,
    from: 'user',
    rawText: query,
    text: query,
    evidence: [],
    mode: mode.value,
    imageUrl: mode.value === 'diagnose' ? imagePreview.value : ''
  }
  const aiMessage = {
    id: `ai-${now}`,
    from: 'ai',
    rawText: '',
    text: '',
    evidence: [],
    mode: mode.value
  }
  messages.value.push(userMessage, aiMessage)
  persistMessage({ ...userMessage, imageUrl: '' })
  return aiMessage
}

const sendMessage = async () => {
  if (isLoading.value) {
    stopGeneration()
    return
  }

  const query = inputMessage.value.trim()
  if (!query) return
  if (mode.value === 'diagnose' && !selectedPlant.value) {
    ElMessage.warning('图片诊断需要先选择一盆植物')
    return
  }
  if (mode.value === 'diagnose' && !imageFile.value) {
    ElMessage.warning('请先上传需要分析的叶片图片')
    return
  }

  const aiMessage = pushConversation(query)
  const diagnosisImage = imageFile.value
  inputMessage.value = ''
  isLoading.value = true
  isStreaming.value = mode.value === 'chat'
  currentEvidence.value = []
  activeEvidence.value = null
  currentController = new AbortController()
  await scrollToBottom()

  try {
    if (mode.value === 'chat') {
      await ragChatStream(createPayload(query), {
        signal: currentController.signal,
        onEvidence: (evidence) => {
          aiMessage.evidence = evidence
          currentEvidence.value = evidence
        },
        onToken: (token) => {
          aiMessage.rawText += token
          aiMessage.text = aiMessage.rawText
          scrollToBottom()
        }
      })
    } else if (mode.value === 'diagnose') {
      const response = await diagnosePlant({
        image: diagnosisImage,
        ...createPayload(query)
      })
      aiMessage.rawText = response.answer || '本次分析未生成文字结论。'
      aiMessage.text = aiMessage.rawText
      aiMessage.evidence = response.evidence || []
      currentEvidence.value = aiMessage.evidence
      clearImage()
    } else {
      const evidence = await searchEvidence({
        query,
        canonicalPlantId: selectedPlant.value?.plantId ? String(selectedPlant.value.plantId) : null
      })
      aiMessage.rawText = evidence.length
        ? `为你找到 **${evidence.length} 条相关资料**。已按相关性与来源可信度排序，可在右侧查看完整证据。`
        : '暂未检索到足够相关的资料，可以换一种描述再试试。'
      aiMessage.text = aiMessage.rawText
      aiMessage.evidence = evidence
      currentEvidence.value = evidence
    }

    persistMessage(aiMessage)
  } catch (error) {
    if (error.name === 'AbortError') {
      if (!aiMessage.rawText) aiMessage.rawText = '已停止本次生成。'
      aiMessage.text = aiMessage.rawText
    } else {
      aiMessage.rawText = `暂时无法连接 AI 服务。${error.message || '请稍后重试。'}`
      aiMessage.text = aiMessage.rawText
      ElMessage.error(error.message || 'AI 服务请求失败')
    }
  } finally {
    isLoading.value = false
    isStreaming.value = false
    currentController = null
    scrollToBottom()
  }
}

const clearMessages = async () => {
  try {
    await ElMessageBox.confirm('确定清空当前设备上的全部 AI 对话记录吗？', '清空对话', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning'
    })
    messageStore.clearMessages()
    messages.value = []
    currentEvidence.value = []
    activeEvidence.value = null
  } catch {
    // 用户取消清空
  }
}

onMounted(() => {
  loadSavedMessages()
  loadPlants()
})

onBeforeUnmount(() => {
  currentController?.abort()
  clearImage()
})
</script>

<template>
  <div class="rag-page">
    <div class="ambient ambient-one"></div>
    <div class="ambient ambient-two"></div>

    <section class="workspace-shell">
      <aside class="context-panel">
        <div class="brand-block">
          <div class="assistant-avatar">
            <img :src="aiAvatar" alt="小绿助手" />
            <span class="online-dot"></span>
          </div>
          <div>
            <p class="eyebrow">GREENCARE RAG</p>
            <h1>小绿助手</h1>
          </div>
        </div>

        <div class="context-section">
          <div class="section-heading">
            <span>咨询对象</span>
            <span class="step-number">01</span>
          </div>
          <el-select
            v-model="selectedPlantId"
            :loading="plantLoading"
            clearable
            placeholder="通用植物知识"
            class="plant-select"
          >
            <el-option v-for="plant in plants" :key="plant.id" :label="plant.plantName" :value="plant.id" />
          </el-select>

          <div v-if="selectedPlant" class="plant-card">
            <el-image v-if="selectedPlant.imgUrl" :src="selectedPlant.imgUrl" fit="cover" class="plant-photo">
              <template #error><div class="plant-placeholder">叶</div></template>
            </el-image>
            <div v-else class="plant-placeholder">叶</div>
            <div class="plant-copy">
              <strong>{{ selectedPlant.plantName }}</strong>
              <span>{{ selectedPlant.location || '位置未设置' }}</span>
              <small><i></i>{{ selectedPlant.deviceName ? `${selectedPlant.deviceName} 已关联` : '未关联设备' }}</small>
            </div>
          </div>
          <div v-else class="general-context">
            <el-icon><Document /></el-icon>
            <span>当前使用通用知识库，不读取个体传感器状态</span>
          </div>
        </div>

        <div class="context-section mode-section">
          <div class="section-heading">
            <span>工作模式</span>
            <span class="step-number">02</span>
          </div>
          <button
            v-for="item in MODES"
            :key="item.value"
            type="button"
            class="mode-button"
            :class="{ active: mode === item.value }"
            @click="setMode(item.value)"
          >
            <span class="mode-icon"><el-icon><component :is="item.icon" /></el-icon></span>
            <span><strong>{{ item.label }}</strong><small>{{ item.hint }}</small></span>
          </button>
        </div>

        <div class="safety-note">
          <el-icon><CircleCheckFilled /></el-icon>
          <p><strong>证据驱动建议</strong><span>回答仅用于辅助养护，不会直接控制设备。</span></p>
        </div>
      </aside>

      <main class="conversation-panel">
        <header class="conversation-header">
          <div>
            <p>{{ selectedPlant ? `正在了解 ${selectedPlant.plantName}` : '通用植物顾问' }}</p>
            <h2>{{ currentMode.label }}</h2>
          </div>
          <div class="header-actions">
            <span class="service-status"><i></i>{{ isLoading ? (isStreaming ? '正在生成' : '正在分析') : 'AI 服务就绪' }}</span>
            <el-button v-if="messages.length" text :icon="Delete" @click="clearMessages">清空</el-button>
          </div>
        </header>

        <div ref="chatBody" class="chat-body" @scroll="handleScroll">
          <div v-if="!messages.length" class="welcome-state">
            <div class="botanical-mark"><span>AI</span></div>
            <p class="eyebrow">PERSONAL PLANT CARE</p>
            <h3>{{ selectedPlant ? `今天想为${selectedPlant.plantName}做些什么？` : '从一个植物问题开始' }}</h3>
            <p class="welcome-copy">我会综合养护知识、社区经验与植物实时状态，并把支撑建议的证据一起交给你。</p>
            <div class="quick-grid">
              <button v-for="question in quickQuestions" :key="question" type="button" @click="useQuickQuestion(question)">
                <span>{{ question }}</span><el-icon><Promotion /></el-icon>
              </button>
            </div>
          </div>

          <template v-else>
            <article v-for="message in messages" :key="message.id" class="message-row" :class="message.from">
              <div class="message-avatar">
                <el-avatar v-if="message.from === 'user'" :size="36" :src="userStore.user?.avatar">
                  {{ userStore.user?.nickName?.slice(0, 1) || '我' }}
                </el-avatar>
                <img v-else :src="aiAvatar" alt="小绿助手" />
              </div>
              <div class="message-column">
                <span class="speaker">{{ message.from === 'user' ? '我' : '小绿助手' }}</span>
                <div class="message-bubble" :class="{ thinking: message.from === 'ai' && isLoading && !message.rawText && message === messages[messages.length - 1] }">
                  <img v-if="message.imageUrl" :src="message.imageUrl" alt="待诊断植物" class="message-image" />
                  <div v-if="message.rawText || message.text" class="markdown-body" v-html="renderMarkdown(message.rawText || message.text)"></div>
                  <div v-else class="thinking-dots"><i></i><i></i><i></i><span>正在整理证据</span></div>
                </div>
                <div v-if="message.evidence?.length" class="message-evidence">
                  <button v-for="(evidence, index) in message.evidence.slice(0, 4)" :key="evidence.id || index" type="button" @click="selectEvidence(evidence)">
                    <span>[E{{ index + 1 }}]</span>{{ evidenceMeta(evidence.type).label }}
                  </button>
                  <span v-if="message.evidence.length > 4">+{{ message.evidence.length - 4 }} 条</span>
                </div>
              </div>
            </article>
          </template>
        </div>

        <button v-show="showScrollButton" type="button" class="scroll-button" @click="scrollToBottom">
          <el-icon><Bottom /></el-icon>
        </button>

        <footer class="composer-wrap">
          <div v-if="mode === 'diagnose'" class="image-composer">
            <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/webp" hidden @change="handleImageChange" />
            <button v-if="!imagePreview" type="button" class="upload-tile" @click="chooseImage">
              <el-icon><UploadFilled /></el-icon><span><strong>添加叶片照片</strong><small>JPG / PNG，最大 10 MB</small></span>
            </button>
            <div v-else class="image-preview">
              <img :src="imagePreview" alt="待分析图片" />
              <span>{{ imageFile?.name }}</span>
              <button type="button" aria-label="移除图片" @click="clearImage"><el-icon><Close /></el-icon></button>
            </div>
          </div>
          <div class="composer">
            <el-input
              v-model="inputMessage"
              type="textarea"
              resize="none"
              :autosize="{ minRows: 1, maxRows: 4 }"
              :placeholder="mode === 'search' ? '描述你想查找的植物知识…' : mode === 'diagnose' ? '补充叶片表现、持续时间等信息…' : '询问植物养护、当前状态或异常原因…'"
              @keydown.enter.exact.prevent="sendMessage"
            />
            <button type="button" class="send-button" :class="{ stop: isLoading }" @click="sendMessage">
              <el-icon><component :is="isLoading ? Close : Promotion" /></el-icon>
              <span>{{ isLoading ? '停止' : mode === 'search' ? '检索' : '发送' }}</span>
            </button>
          </div>
          <p class="composer-tip"><span>Enter 发送 · Shift + Enter 换行</span><span>AI 建议请结合植物实际情况判断</span></p>
        </footer>
      </main>

      <aside class="evidence-panel">
        <header>
          <div><p class="eyebrow">EVIDENCE CHAIN</p><h2>本轮证据</h2></div>
          <span>{{ currentEvidence.length }}</span>
        </header>

        <div v-if="currentEvidence.length" class="evidence-list">
          <button
            v-for="(evidence, index) in currentEvidence"
            :key="evidence.id || index"
            type="button"
            class="evidence-card"
            :class="[{ active: activeEvidence === evidence }, evidenceMeta(evidence.type).tone]"
            @click="selectEvidence(evidence)"
          >
            <span class="evidence-index">E{{ index + 1 }}</span>
            <span class="evidence-icon"><el-icon><component :is="evidenceMeta(evidence.type).icon" /></el-icon></span>
            <span class="evidence-main">
              <span class="evidence-topline"><em>{{ evidenceMeta(evidence.type).label }}</em><b>{{ scoreText(evidence) }}</b></span>
              <strong>{{ formatSource(evidence) }}</strong>
              <small>{{ evidence.content }}</small>
            </span>
          </button>
        </div>

        <div v-else class="evidence-empty">
          <div class="evidence-orbit"><el-icon><Connection /></el-icon></div>
          <h3>证据将在这里汇集</h3>
          <p>提问后，系统会展示知识、社区、传感器与视觉模型提供的依据。</p>
        </div>

        <div v-if="activeEvidence" class="evidence-detail">
          <div class="detail-heading">
            <span>证据详情</span>
            <button type="button" @click="activeEvidence = null"><el-icon><Close /></el-icon></button>
          </div>
          <div class="detail-tags">
            <span :class="evidenceMeta(activeEvidence.type).tone">{{ evidenceMeta(activeEvidence.type).label }}</span>
            <span>{{ scoreText(activeEvidence) }}</span>
          </div>
          <h3>{{ formatSource(activeEvidence) }}</h3>
          <p>{{ activeEvidence.content }}</p>
          <dl>
            <div><dt>来源标识</dt><dd>{{ activeEvidence.sourceId || '—' }}</dd></div>
            <div><dt>更新时间</dt><dd>{{ formatTime(activeEvidence.timestamp) }}</dd></div>
          </dl>
        </div>

        <div v-if="currentEvidence.some((item) => item.type === 'SENSOR_CONSISTENCY')" class="consistency-note">
          <el-icon><CircleCheckFilled /></el-icon>
          <span><strong>环境数据已参与印证</strong><small>图像判断已与近期传感器趋势交叉核验</small></span>
        </div>
        <div v-else-if="currentEvidence.some((item) => item.type === 'VISUAL_OBSERVATION')" class="consistency-note warning">
          <el-icon><WarningFilled /></el-icon>
          <span><strong>视觉结果仅为候选</strong><small>请结合后续检查，不应视为病害确诊</small></span>
        </div>
      </aside>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.rag-page {
  --ink: #163c31;
  --muted: #6c817a;
  --line: #dfe9e4;
  --paper: rgba(255, 255, 252, 0.96);
  --mint: #3cad7a;
  --mint-deep: #24815a;
  --cream: #f7f6ee;
  position: relative;
  height: calc(100vh - 148px);
  min-height: 620px;
  overflow: hidden;
  color: var(--ink);
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
}

.ambient { position: absolute; border-radius: 50%; filter: blur(2px); pointer-events: none; }
.ambient-one { width: 340px; height: 340px; right: 18%; top: -180px; background: rgba(88, 192, 142, 0.18); }
.ambient-two { width: 260px; height: 260px; left: -120px; bottom: -130px; background: rgba(228, 190, 111, 0.14); }

.workspace-shell {
  position: relative;
  z-index: 1;
  height: 100%;
  display: grid;
  grid-template-columns: 250px minmax(420px, 1fr) 310px;
  background: var(--paper);
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 18px;
  box-shadow: 0 18px 50px rgba(30, 82, 63, 0.1);
  overflow: hidden;
}

.eyebrow { margin: 0 0 4px; color: #8aa398; font-size: 10px; font-weight: 700; letter-spacing: 0.16em; }

.context-panel {
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding: 24px 20px 18px;
  background: linear-gradient(165deg, #f8faf4 0%, #eef7f0 62%, #f6f0df 150%);
  border-right: 1px solid var(--line);
  overflow-y: auto;
}

.brand-block { display: flex; align-items: center; gap: 12px; }
.brand-block h1 { margin: 0; font-family: "STZhongsong", "SimSun", serif; font-size: 22px; font-weight: 700; letter-spacing: 0.04em; }
.assistant-avatar { position: relative; width: 48px; height: 48px; border-radius: 15px 15px 15px 5px; background: #d8f1e3; box-shadow: inset 0 0 0 1px rgba(47, 139, 93, 0.12); }
.assistant-avatar img { width: 100%; height: 100%; object-fit: cover; border-radius: inherit; }
.online-dot { position: absolute; right: -2px; bottom: 2px; width: 10px; height: 10px; background: #42bd78; border: 3px solid #f5f9f3; border-radius: 50%; }

.context-section { display: flex; flex-direction: column; gap: 11px; }
.section-heading { display: flex; justify-content: space-between; align-items: center; color: #5c746a; font-size: 12px; font-weight: 700; }
.step-number { color: #a7b8b0; font-size: 10px; letter-spacing: 0.12em; }
.plant-select { width: 100%; }
.plant-select :deep(.el-select__wrapper) { min-height: 40px; border-radius: 10px; background: rgba(255,255,255,.82); box-shadow: 0 0 0 1px #dfe9e4 inset; }

.plant-card { display: grid; grid-template-columns: 58px 1fr; gap: 11px; padding: 10px; border: 1px solid #dce9e1; border-radius: 12px; background: rgba(255,255,255,.74); }
.plant-photo, .plant-placeholder { width: 58px; height: 62px; border-radius: 9px; }
.plant-placeholder { display: grid; place-items: center; background: linear-gradient(145deg, #cbe9d8, #edf5dc); color: #4a966d; font-family: serif; font-size: 24px; }
.plant-copy { min-width: 0; display: flex; flex-direction: column; justify-content: center; gap: 4px; }
.plant-copy strong { font-size: 14px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.plant-copy span { color: var(--muted); font-size: 11px; }
.plant-copy small { display: flex; align-items: center; gap: 5px; color: #4a856a; font-size: 10px; }
.plant-copy i { width: 6px; height: 6px; border-radius: 50%; background: #4fbd7f; }
.general-context { display: flex; gap: 9px; align-items: flex-start; padding: 12px; border: 1px dashed #cbded3; border-radius: 11px; color: var(--muted); font-size: 11px; line-height: 1.55; }
.general-context .el-icon { flex: 0 0 auto; margin-top: 2px; color: var(--mint-deep); }

.mode-section { gap: 8px; }
.mode-section .section-heading { margin-bottom: 3px; }
.mode-button { width: 100%; display: grid; grid-template-columns: 34px 1fr; align-items: center; gap: 10px; padding: 9px 10px; border: 1px solid transparent; border-radius: 11px; color: #667c73; background: transparent; text-align: left; cursor: pointer; transition: .2s ease; }
.mode-button:hover { background: rgba(255,255,255,.7); }
.mode-button.active { color: var(--mint-deep); border-color: #cfe4d8; background: rgba(255,255,255,.92); box-shadow: 0 6px 16px rgba(45, 105, 78, .07); }
.mode-icon { width: 34px; height: 34px; display: grid; place-items: center; border-radius: 9px; background: rgba(59, 172, 121, .1); font-size: 17px; }
.mode-button strong, .mode-button small { display: block; }
.mode-button strong { margin-bottom: 2px; font-size: 12px; }
.mode-button small { color: #8a9b94; font-size: 9px; }

.safety-note { margin-top: auto; display: flex; gap: 9px; padding: 11px; border-radius: 11px; color: #58776a; background: rgba(228, 241, 228, .7); }
.safety-note .el-icon { margin-top: 1px; color: var(--mint); }
.safety-note p { margin: 0; }
.safety-note strong, .safety-note span { display: block; }
.safety-note strong { margin-bottom: 3px; font-size: 11px; }
.safety-note span { font-size: 9px; line-height: 1.45; }

.conversation-panel { position: relative; min-width: 0; display: flex; flex-direction: column; background: rgba(255,255,252,.92); }
.conversation-header { min-height: 72px; box-sizing: border-box; display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 13px 24px; border-bottom: 1px solid var(--line); }
.conversation-header p { margin: 0 0 3px; color: var(--muted); font-size: 10px; }
.conversation-header h2 { margin: 0; font-family: "STZhongsong", "SimSun", serif; font-size: 19px; }
.header-actions { display: flex; align-items: center; gap: 8px; }
.service-status { display: flex; align-items: center; gap: 6px; padding: 6px 9px; border: 1px solid #dce9e2; border-radius: 99px; color: #577168; background: #f9fbf8; font-size: 10px; }
.service-status i { width: 6px; height: 6px; border-radius: 50%; background: #4bbf7b; box-shadow: 0 0 0 3px rgba(75,191,123,.12); }

.chat-body { flex: 1; min-height: 0; overflow-y: auto; scroll-behavior: smooth; padding: 26px clamp(24px, 5vw, 64px); background-image: radial-gradient(rgba(72, 122, 98, .08) .6px, transparent .6px); background-size: 18px 18px; }
.welcome-state { min-height: 100%; max-width: 600px; margin: 0 auto; display: flex; flex-direction: column; justify-content: center; align-items: center; text-align: center; }
.botanical-mark { width: 68px; height: 68px; display: grid; place-items: center; margin-bottom: 18px; border-radius: 52% 48% 52% 18%; transform: rotate(-8deg); background: linear-gradient(145deg, #d9f2e3, #f2f5d8); box-shadow: 0 12px 30px rgba(62, 145, 99, .13); }
.botanical-mark span { transform: rotate(8deg); font-family: Georgia, serif; font-size: 18px; font-weight: 700; color: #378b62; }
.welcome-state h3 { max-width: 500px; margin: 3px 0 10px; font-family: "STZhongsong", "SimSun", serif; font-size: clamp(22px, 2.2vw, 31px); line-height: 1.35; }
.welcome-copy { max-width: 530px; margin: 0; color: var(--muted); font-size: 12px; line-height: 1.8; }
.quick-grid { width: 100%; margin-top: 26px; display: grid; grid-template-columns: repeat(3, 1fr); gap: 9px; }
.quick-grid button { display: flex; align-items: center; justify-content: space-between; gap: 9px; min-height: 60px; padding: 12px 13px; border: 1px solid #dce8e1; border-radius: 12px; background: rgba(255,255,255,.84); color: #46685a; text-align: left; font-size: 11px; line-height: 1.5; cursor: pointer; transition: .2s ease; }
.quick-grid button:hover { transform: translateY(-2px); border-color: #aad6bf; box-shadow: 0 8px 20px rgba(46, 113, 81, .08); }
.quick-grid .el-icon { flex: 0 0 auto; color: var(--mint); }

.message-row { max-width: 760px; margin: 0 auto 24px; display: flex; gap: 11px; animation: message-in .28s ease both; }
.message-row.user { flex-direction: row-reverse; }
.message-avatar { flex: 0 0 36px; width: 36px; height: 36px; }
.message-avatar img { width: 36px; height: 36px; object-fit: cover; border-radius: 11px 11px 11px 3px; background: #dff1e4; }
.message-column { min-width: 0; max-width: min(82%, 660px); display: flex; flex-direction: column; align-items: flex-start; }
.message-row.user .message-column { align-items: flex-end; }
.speaker { margin: 0 3px 5px; color: #879991; font-size: 9px; }
.message-bubble { max-width: 100%; padding: 12px 15px; border: 1px solid #e2eae5; border-radius: 4px 14px 14px 14px; background: #fff; color: #29483d; box-shadow: 0 5px 16px rgba(33, 78, 59, .04); }
.message-row.user .message-bubble { border: none; border-radius: 14px 4px 14px 14px; color: white; background: linear-gradient(135deg, #3ba979, #2f9167); }
.message-image { display: block; max-width: min(100%, 320px); max-height: 220px; margin-bottom: 10px; border-radius: 9px; object-fit: cover; }
.markdown-body { font-size: 12px; line-height: 1.72; word-break: break-word; }
.markdown-body :deep(p) { margin: 0 0 8px; }
.markdown-body :deep(p:last-child) { margin-bottom: 0; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { margin: 7px 0; padding-left: 20px; }
.markdown-body :deep(blockquote) { margin: 8px 0; padding: 6px 10px; border-left: 3px solid #79bd9b; background: #f4faf6; color: #58756a; }
.markdown-body :deep(code) { padding: 2px 5px; border-radius: 4px; background: #eef5f0; }
.thinking-dots { display: flex; align-items: center; gap: 4px; color: #7b9087; font-size: 10px; }
.thinking-dots i { width: 5px; height: 5px; border-radius: 50%; background: var(--mint); animation: dot-pulse 1.2s infinite; }
.thinking-dots i:nth-child(2) { animation-delay: .15s; }.thinking-dots i:nth-child(3) { animation-delay: .3s; }
.thinking-dots span { margin-left: 5px; }
.message-evidence { max-width: 100%; margin-top: 7px; display: flex; flex-wrap: wrap; gap: 5px; align-items: center; }
.message-evidence button { padding: 4px 7px; border: 1px solid #dbe8e1; border-radius: 7px; background: #f9fbf8; color: #577268; font-size: 9px; cursor: pointer; }
.message-evidence button span { margin-right: 3px; color: var(--mint-deep); font-weight: 700; }
.message-evidence > span { color: #879991; font-size: 9px; }

.scroll-button { position: absolute; z-index: 5; right: 28px; bottom: 142px; width: 32px; height: 32px; display: grid; place-items: center; border: 1px solid #cfe0d7; border-radius: 50%; color: var(--mint-deep); background: white; box-shadow: 0 6px 16px rgba(39,92,68,.12); cursor: pointer; }
.composer-wrap { padding: 12px 20px 10px; border-top: 1px solid var(--line); background: rgba(255,255,252,.98); }
.image-composer { margin-bottom: 8px; }
.upload-tile { width: 100%; min-height: 48px; display: flex; align-items: center; justify-content: center; gap: 10px; border: 1px dashed #a9cdbb; border-radius: 11px; color: #527266; background: #f7fbf7; cursor: pointer; }
.upload-tile .el-icon { font-size: 20px; color: var(--mint); }
.upload-tile strong, .upload-tile small { display: block; text-align: left; }.upload-tile strong { font-size: 11px; }.upload-tile small { margin-top: 2px; color: #91a099; font-size: 8px; }
.image-preview { display: grid; grid-template-columns: 42px 1fr 26px; align-items: center; gap: 9px; padding: 5px 8px; border: 1px solid #dce8e1; border-radius: 10px; background: #f7faf7; }
.image-preview img { width: 42px; height: 38px; border-radius: 7px; object-fit: cover; }.image-preview span { min-width: 0; color: #587168; font-size: 10px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.image-preview button, .detail-heading button { display: grid; place-items: center; padding: 0; border: 0; color: #81938c; background: transparent; cursor: pointer; }
.composer { display: grid; grid-template-columns: 1fr auto; align-items: end; gap: 9px; padding: 6px 6px 6px 14px; border: 1px solid #cfe0d7; border-radius: 13px; background: white; box-shadow: 0 7px 22px rgba(39, 92, 68, .06); transition: .2s ease; }
.composer:focus-within { border-color: #83c5a4; box-shadow: 0 7px 22px rgba(39, 132, 87, .1); }
.composer :deep(.el-textarea__inner) { min-height: 34px !important; padding: 8px 0; border: none; color: #29483d; background: transparent; box-shadow: none; font-size: 12px; line-height: 1.5; }
.send-button { min-width: 78px; height: 38px; display: flex; align-items: center; justify-content: center; gap: 6px; border: 0; border-radius: 10px; color: white; background: linear-gradient(135deg, #43b981, #2c9265); box-shadow: 0 5px 12px rgba(45, 153, 103, .22); cursor: pointer; }
.send-button.stop { background: linear-gradient(135deg, #dd756d, #c85953); }
.send-button span { font-size: 11px; }
.composer-tip { display: flex; justify-content: space-between; margin: 6px 3px 0; color: #9aa9a2; font-size: 8px; }

.evidence-panel { position: relative; display: flex; flex-direction: column; min-width: 0; padding: 21px 17px 16px; border-left: 1px solid var(--line); background: #fbfcf8; overflow: hidden; }
.evidence-panel > header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 15px; }
.evidence-panel h2 { margin: 0; font-family: "STZhongsong", "SimSun", serif; font-size: 18px; }
.evidence-panel > header > span { min-width: 26px; height: 26px; display: grid; place-items: center; border-radius: 9px; color: var(--mint-deep); background: #e8f5ed; font-size: 11px; font-weight: 700; }
.evidence-list { display: flex; flex-direction: column; gap: 8px; overflow-y: auto; padding-right: 2px; }
.evidence-card { position: relative; width: 100%; display: grid; grid-template-columns: 30px 1fr; gap: 9px; padding: 11px; border: 1px solid #e0e9e4; border-radius: 12px; color: #47665a; background: white; text-align: left; cursor: pointer; transition: .2s ease; }
.evidence-card:hover, .evidence-card.active { transform: translateY(-1px); border-color: #a8d4bd; box-shadow: 0 7px 18px rgba(38, 96, 68, .07); }
.evidence-index { position: absolute; top: 7px; right: 8px; color: #9aaba3; font-family: Georgia, serif; font-size: 8px; }
.evidence-icon { width: 30px; height: 30px; display: grid; place-items: center; border-radius: 9px; background: #eaf6ef; color: #3a9569; }
.evidence-card.community .evidence-icon { color: #9a7944; background: #f8f0df; }.evidence-card.visual .evidence-icon { color: #567ca5; background: #edf3f8; }.evidence-card.warning .evidence-icon { color: #a96943; background: #faeee5; }
.evidence-main { min-width: 0; display: block; }
.evidence-topline { display: flex; justify-content: space-between; gap: 20px; margin-bottom: 4px; }
.evidence-topline em { color: #789087; font-size: 8px; font-style: normal; }.evidence-topline b { color: #54a77c; font-size: 8px; }
.evidence-main strong, .evidence-main small { display: block; overflow: hidden; }
.evidence-main strong { padding-right: 24px; font-size: 11px; white-space: nowrap; text-overflow: ellipsis; }
.evidence-main small { display: -webkit-box; margin-top: 5px; color: #82938c; font-size: 9px; line-height: 1.5; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.evidence-empty { flex: 1; display: flex; flex-direction: column; justify-content: center; align-items: center; padding: 20px; text-align: center; }
.evidence-orbit { width: 62px; height: 62px; display: grid; place-items: center; border: 1px dashed #9fc8b3; border-radius: 50%; color: #65a987; background: #f2f8f2; font-size: 23px; }
.evidence-empty h3 { margin: 16px 0 6px; font-family: "STZhongsong", "SimSun", serif; font-size: 15px; }.evidence-empty p { max-width: 210px; margin: 0; color: #8a9b94; font-size: 9px; line-height: 1.65; }
.evidence-detail { position: absolute; z-index: 6; inset: 0; padding: 22px 18px; background: rgba(251,252,248,.98); backdrop-filter: blur(10px); animation: detail-in .22s ease both; overflow-y: auto; }
.detail-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 22px; font-family: "STZhongsong", "SimSun", serif; font-size: 18px; font-weight: 700; }
.detail-heading button { width: 28px; height: 28px; border-radius: 8px; background: #edf3ef; }
.detail-tags { display: flex; gap: 6px; margin-bottom: 12px; }.detail-tags span { padding: 4px 7px; border-radius: 6px; color: #587168; background: #edf3ef; font-size: 9px; }.detail-tags .knowledge, .detail-tags .state { color: #2f845d; background: #e7f5ec; }.detail-tags .visual { color: #567ca5; background: #edf3f8; }.detail-tags .warning { color: #a96943; background: #faeee5; }
.evidence-detail h3 { margin: 0 0 12px; font-size: 15px; line-height: 1.5; }.evidence-detail > p { margin: 0; padding: 13px; border-left: 3px solid #75bd98; border-radius: 0 9px 9px 0; color: #526f63; background: #f1f8f3; font-size: 10px; line-height: 1.75; white-space: pre-wrap; }
.evidence-detail dl { margin: 20px 0 0; }.evidence-detail dl div { padding: 9px 0; border-bottom: 1px solid #e5ece8; }.evidence-detail dt { margin-bottom: 3px; color: #94a29c; font-size: 8px; }.evidence-detail dd { margin: 0; color: #557066; font-size: 10px; word-break: break-all; }
.consistency-note { margin-top: auto; display: flex; gap: 9px; padding: 11px; border: 1px solid #cfe4d8; border-radius: 11px; color: #3e785a; background: #edf8f1; }.consistency-note.warning { color: #93613e; border-color: #edd8c8; background: #fbf2e9; }
.consistency-note .el-icon { flex: 0 0 auto; margin-top: 1px; }.consistency-note strong, .consistency-note small { display: block; }.consistency-note strong { font-size: 10px; }.consistency-note small { margin-top: 3px; color: #7c9388; font-size: 8px; line-height: 1.4; }

@keyframes message-in { from { opacity: 0; transform: translateY(7px); } }
@keyframes detail-in { from { opacity: 0; transform: translateX(10px); } }
@keyframes dot-pulse { 0%, 60%, 100% { opacity: .35; transform: translateY(0); } 30% { opacity: 1; transform: translateY(-3px); } }

@media (max-width: 1280px) {
  .workspace-shell { grid-template-columns: 220px minmax(400px, 1fr) 270px; }
  .quick-grid { grid-template-columns: 1fr; max-width: 440px; }
  .quick-grid button { min-height: 46px; }
}

@media (max-width: 980px) {
  .workspace-shell { grid-template-columns: 205px minmax(0, 1fr); }
  .evidence-panel { display: none; }
}

@media (max-width: 720px) {
  .rag-page { height: auto; min-height: calc(100vh - 130px); overflow: visible; }
  .workspace-shell { min-height: inherit; grid-template-columns: 1fr; border-radius: 13px; overflow: visible; }
  .context-panel { display: block; padding: 14px; border-right: 0; border-bottom: 1px solid var(--line); }
  .brand-block, .mode-section, .safety-note { display: none; }
  .context-section { gap: 7px; }.plant-card { display: none; }
  .conversation-panel { min-height: 650px; }
  .conversation-header { padding: 12px 15px; }.service-status { display: none; }
  .chat-body { padding: 20px 14px; }.message-column { max-width: 88%; }
  .composer-wrap { padding: 10px; }.composer-tip span:last-child { display: none; }
}
</style>
