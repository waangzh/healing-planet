<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import MarkdownIt from 'markdown-it'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Camera, ChatDotRound, CircleCheckFilled, Close, Connection, Delete,
  Document, Link, Paperclip, PictureFilled, Promotion, Search, WarningFilled
} from '@element-plus/icons-vue'
import { useAiMessageStore, useUserStore } from '@/stores'
import { diagnosePlant, getRagContext, ragChatStream, searchEvidence } from '@/api/rag'
import aiAvatar from '@/assets/img/ai-avatar.png'

const md = new MarkdownIt({ html: false, breaks: true, linkify: true, typographer: true })
const router = useRouter()
const userStore = useUserStore()
const messageStore = useAiMessageStore()

const MODES = [
  { value: 'chat', label: '证据问答', icon: ChatDotRound, hint: '融合百科与社区经验' },
  { value: 'search', label: '社区寻帖', icon: Search, hint: '按语义发现真实经验' },
  { value: 'diagnose', label: '病害会诊', icon: Camera, hint: '图像、知识与环境印证' }
]
const QUICK_QUESTIONS = {
  chat: ['绿萝黄叶通常有哪些原因？', '新手养多肉最容易忽略什么？', '社区里大家如何处理烂根？'],
  search: ['阳台暴晒后叶片焦边的处理经验', '适合宿舍养的耐阴植物', '多肉度夏通风与控水记录'],
  diagnose: ['请判断叶片可能存在的问题', '结合近期环境数据分析病害风险', '给我一份进一步排查清单']
}
const EVIDENCE_META = {
  PLANT_KNOWLEDGE: { label: '植物百科', tone: 'knowledge', icon: Document },
  CARE_GUIDE: { label: '养护指南', tone: 'knowledge', icon: Document },
  COMMUNITY_POST: { label: '社区原帖', tone: 'community', icon: ChatDotRound },
  LIVE_STATE: { label: '实时状态', tone: 'state', icon: Connection },
  SENSOR_HISTORY: { label: '历史趋势', tone: 'state', icon: Connection },
  VISUAL_OBSERVATION: { label: '图像观察', tone: 'visual', icon: PictureFilled },
  IMAGE_DETECTION: { label: '图像检测', tone: 'visual', icon: PictureFilled },
  DISEASE_KNOWLEDGE: { label: '病害知识', tone: 'warning', icon: WarningFilled },
  SENSOR_CONSISTENCY: { label: '环境印证', tone: 'state', icon: CircleCheckFilled }
}

const mode = ref('chat')
const messages = ref([])
const inputMessage = ref('')
const isLoading = ref(false)
const isStreaming = ref(false)
const chatBody = ref(null)
const showScrollButton = ref(false)
const ragContext = ref({ linked: false, backEndUserId: null, plants: [] })
const contextLoading = ref(false)
const selectedPlantId = ref(null)
const currentEvidence = ref([])
const activeEvidence = ref(null)
const imageFile = ref(null)
const imagePreview = ref('')
const imageName = ref('')
const fileInput = ref(null)
const cameraInput = ref(null)
const activeAttachmentId = ref('')
const attachmentNotice = ref('')
let currentController = null

const currentMode = computed(() => MODES.find((item) => item.value === mode.value))
const quickQuestions = computed(() => QUICK_QUESTIONS[mode.value])
const selectedPlant = computed(() => ragContext.value.plants?.find((plant) => plant.id === selectedPlantId.value))
const hasImageContext = computed(() => Boolean(imageFile.value || activeAttachmentId.value))
const ROUTE_LABELS = {
  DISEASE_DIAGNOSIS: '叶片诊断引擎',
  OCR: '图片文字识别',
  GENERAL_VISION: '通用视觉问答'
}
const observationItems = (observation) => ([
  ['颜色变化', observation?.colorChanges],
  ['病斑形状和分布', observation?.lesionShapeAndDistribution],
  ['叶缘 / 叶脉', observation?.leafEdgeAndVein],
  ['可见虫体', observation?.visiblePests],
  ['照片质量', observation?.imageQuality],
  ['不确定性', observation?.uncertainty]
]).filter(([, value]) => value)
const evidenceMeta = (type) => EVIDENCE_META[type] || { label: '补充来源', tone: 'neutral', icon: Document }
const renderMarkdown = (text = '') => md.render(text)
const sourceTitle = (evidence) => evidence.title || evidence.metadata?.plantName || evidence.sourceType || '未命名来源'
const scoreText = (evidence) => {
  const score = evidence.finalScore ?? evidence.rerankScore ?? evidence.retrievalScore
  return typeof score === 'number' ? `${Math.round(score * 100)}%` : '已采用'
}
const postIdOf = (evidence) => evidence?.type === 'COMMUNITY_POST'
  ? (evidence.metadata?.postId || evidence.sourceId || null)
  : null

const loadContext = async () => {
  contextLoading.value = true
  try {
    const response = await getRagContext()
    if (response.data.code === 200 && response.data.data) {
      ragContext.value = {
        linked: Boolean(response.data.data.linked),
        backEndUserId: response.data.data.backEndUserId || null,
        plants: response.data.data.plants || []
      }
      selectedPlantId.value = ragContext.value.plants[0]?.id || null
    }
  } catch {
    ragContext.value = { linked: false, backEndUserId: null, plants: [] }
    ElMessage.warning('花盆关联信息暂时不可用，仍可使用知识问答和社区寻帖')
  } finally {
    contextLoading.value = false
  }
}

const loadMessages = () => {
  messageStore.loadMessages()
  messages.value = messageStore.messages.map((message, index) => ({
    id: message.id || `saved-${index}`,
    from: message.from,
    rawText: message.rawText || message.text || '',
    evidence: message.evidence || [],
    mode: message.mode || 'chat',
    route: message.route || '',
    notice: message.notice || '',
    visualObservation: message.visualObservation || null
  }))
  currentEvidence.value = [...messages.value].reverse().find((item) => item.evidence?.length)?.evidence || []
}
const persistMessage = (message) => messageStore.addMessage({
  id: message.id, from: message.from, rawText: message.rawText, text: message.rawText,
  evidence: message.evidence || [], mode: message.mode,
  route: message.route || '', notice: message.notice || '',
  visualObservation: message.visualObservation || null
})
const scrollToBottom = () => nextTick(() => {
  if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight
})
const handleScroll = () => {
  if (!chatBody.value) return
  const { scrollTop, scrollHeight, clientHeight } = chatBody.value
  showScrollButton.value = scrollHeight - scrollTop - clientHeight > 80
}
const setMode = (nextMode) => {
  if (isLoading.value) return
  mode.value = nextMode
  activeEvidence.value = null
}
const useQuickQuestion = (question) => {
  inputMessage.value = question
  if (mode.value !== 'diagnose' || hasImageContext.value) sendMessage()
}
const chooseImage = () => fileInput.value?.click()
const takePhoto = () => cameraInput.value?.click()
const setImage = (file) => {
  if (!file) return
  if (!file.type.startsWith('image/')) return ElMessage.warning('请选择 JPG、PNG 等图片文件')
  if (file.size > 10 * 1024 * 1024) return ElMessage.warning('图片大小不能超过 10 MB')
  imageFile.value = file
  imageName.value = file.name || '现场拍摄图片'
  activeAttachmentId.value = ''
  attachmentNotice.value = '图片将在发送后临时保存 15 分钟，可用于后续追问。'
  const reader = new FileReader()
  reader.onload = () => { imagePreview.value = reader.result }
  reader.readAsDataURL(file)
}
const handleImageChange = (event) => setImage(event.target.files?.[0])
const handlePaste = (event) => {
  const file = [...(event.clipboardData?.files || [])].find((item) => item.type.startsWith('image/'))
  if (file) {
    event.preventDefault()
    setImage(file)
    ElMessage.success('已从剪贴板添加图片')
  }
}
const clearImage = () => {
  imageFile.value = null
  imagePreview.value = ''
  imageName.value = ''
  activeAttachmentId.value = ''
  attachmentNotice.value = ''
  if (fileInput.value) fileInput.value.value = ''
  if (cameraInput.value) cameraInput.value.value = ''
}
const stopGeneration = () => {
  currentController?.abort()
  currentController = null
  isLoading.value = false
  isStreaming.value = false
}
const createPayload = (query) => ({
  userId: selectedPlant.value ? ragContext.value.backEndUserId : null,
  plantInstanceId: selectedPlant.value?.id || null,
  canonicalPlantId: selectedPlant.value?.plantId ? String(selectedPlant.value.plantId) : null,
  query
})
const pushConversation = (query) => {
  const now = Date.now()
  const userMessage = {
    id: `user-${now}`, from: 'user', rawText: query, evidence: [], mode: mode.value,
    imageUrl: mode.value !== 'search' && hasImageContext.value ? imagePreview.value : ''
  }
  const aiMessage = { id: `ai-${now}`, from: 'ai', rawText: '', evidence: [], mode: mode.value, route: '', notice: '', visualObservation: null }
  messages.value.push(userMessage, aiMessage)
  persistMessage({ ...userMessage, imageUrl: '' })
  return aiMessage
}

const sendMessage = async () => {
  if (isLoading.value) return stopGeneration()
  const query = inputMessage.value.trim()
  const shouldAnalyzeImage = mode.value !== 'search' && hasImageContext.value
  if (!query && !shouldAnalyzeImage) return
  if (mode.value === 'diagnose' && !hasImageContext.value) return ElMessage.warning('请先上传需要分析的植物图片')

  const effectiveQuery = query || '请分析这张植物图片'
  const aiMessage = pushConversation(effectiveQuery)
  const diagnosisImage = imageFile.value
  inputMessage.value = ''
  isLoading.value = true
  isStreaming.value = mode.value === 'chat' && !shouldAnalyzeImage
  currentEvidence.value = []
  activeEvidence.value = null
  currentController = new AbortController()
  await scrollToBottom()
  try {
    if (shouldAnalyzeImage) {
      const response = await diagnosePlant({
        image: diagnosisImage,
        attachmentId: activeAttachmentId.value || null,
        requestedRoute: mode.value === 'diagnose' ? 'DISEASE_DIAGNOSIS' : 'AUTO',
        signal: currentController.signal,
        ...createPayload(effectiveQuery)
      })
      aiMessage.rawText = response.answer || '本次图片分析未生成文字结论。'
      aiMessage.evidence = response.evidence || []
      aiMessage.route = response.route || ''
      aiMessage.notice = response.notice || ''
      aiMessage.visualObservation = response.visualObservation || null
      currentEvidence.value = aiMessage.evidence
      activeAttachmentId.value = response.attachmentId || activeAttachmentId.value
      attachmentNotice.value = response.notice || attachmentNotice.value
      imageFile.value = null
      if (fileInput.value) fileInput.value.value = ''
      if (cameraInput.value) cameraInput.value.value = ''
    } else if (mode.value === 'chat') {
      await ragChatStream(createPayload(effectiveQuery), {
        signal: currentController.signal,
        onEvidence: (evidence) => { aiMessage.evidence = evidence; currentEvidence.value = evidence },
        onToken: (token) => { aiMessage.rawText += token; scrollToBottom() }
      })
    } else if (mode.value === 'search') {
      const evidence = await searchEvidence({
        query: effectiveQuery,
        canonicalPlantId: selectedPlant.value?.plantId ? String(selectedPlant.value.plantId) : null
      })
      aiMessage.evidence = evidence
      currentEvidence.value = evidence
      const postCount = evidence.filter((item) => item.type === 'COMMUNITY_POST').length
      aiMessage.rawText = evidence.length
        ? `已找到 **${evidence.length} 条相关证据**，其中有 **${postCount} 篇社区原帖**。结果已综合语义相关性、来源可信度与社区质量排序。`
        : '暂未找到足够相关的内容，可以补充植物名称、症状或养护场景后再试。'
    }
    persistMessage(aiMessage)
  } catch (error) {
    if (error.name === 'AbortError') {
      if (!aiMessage.rawText) aiMessage.rawText = '已停止本次生成。'
    } else {
      aiMessage.rawText = `暂时无法连接 GreenCare RAG。${error.message || '请稍后重试。'}`
      if (error.message?.includes('图片附件已过期')) clearImage()
      ElMessage.error(error.message || 'AI 服务请求失败')
    }
  } finally {
    isLoading.value = false
    isStreaming.value = false
    currentController = null
    scrollToBottom()
  }
}
const openPost = (evidence) => {
  const postId = postIdOf(evidence)
  if (postId) router.push(`/post/${postId}`)
}
const clearMessages = async () => {
  try {
    await ElMessageBox.confirm('确定清空此设备上的全部 AI 对话记录吗？', '清空对话', {
      confirmButtonText: '清空', cancelButtonText: '取消', type: 'warning'
    })
    messageStore.clearMessages()
    messages.value = []
    currentEvidence.value = []
    activeEvidence.value = null
    clearImage()
  } catch {
    // 用户取消
  }
}
onMounted(() => { loadMessages(); loadContext() })
onBeforeUnmount(() => { currentController?.abort(); clearImage() })
</script>

<template>
  <div class="rag-page">
    <section class="rag-shell">
      <aside class="context-panel">
        <div class="assistant-brand">
          <div class="avatar-wrap"><img :src="aiAvatar" alt="小绿助手"><i></i></div>
          <div><span>GREENCARE RAG</span><h1>小绿助手</h1></div>
        </div>
        <div class="context-block">
          <div class="block-title"><span>植物上下文</span><b>01</b></div>
          <el-select v-model="selectedPlantId" :loading="contextLoading" clearable placeholder="通用社区知识" class="plant-select">
            <el-option v-for="plant in ragContext.plants" :key="plant.id" :label="plant.plantName" :value="plant.id" />
          </el-select>
          <div v-if="selectedPlant" class="plant-card">
            <img v-if="selectedPlant.imgUrl" :src="selectedPlant.imgUrl" :alt="selectedPlant.plantName">
            <div v-else class="plant-fallback">叶</div>
            <div><strong>{{ selectedPlant.plantName }}</strong><span>{{ selectedPlant.location || '位置未设置' }}</span><small><i></i>已接入环境状态</small></div>
          </div>
          <div v-else class="general-note">
            <el-icon><Document /></el-icon>
            <span>{{ ragContext.linked ? '当前使用社区与植物百科，不读取个体环境。' : '尚未关联智能花盆，状态诊断功能暂不可用。' }}</span>
          </div>
        </div>
        <div class="context-block mode-block">
          <div class="block-title"><span>探索方式</span><b>02</b></div>
          <button v-for="item in MODES" :key="item.value" type="button" :class="['mode-button', { active: mode === item.value }]" @click="setMode(item.value)">
            <span class="mode-icon"><el-icon><component :is="item.icon" /></el-icon></span>
            <span><strong>{{ item.label }}</strong><small>{{ item.hint }}</small></span>
          </button>
        </div>
        <div class="trust-note"><el-icon><CircleCheckFilled /></el-icon><p><strong>回答有据可查</strong><span>社区内容仅作为经验参考，不会被当作系统指令。</span></p></div>
      </aside>

      <main class="conversation-panel">
        <header class="conversation-header">
          <div><span>{{ selectedPlant ? `正在关注 ${selectedPlant.plantName}` : '植愈星球知识与经验' }}</span><h2>{{ currentMode.label }}</h2></div>
          <div class="header-actions"><span class="service-status"><i></i>{{ isLoading ? (isStreaming ? '正在生成' : '正在检索') : '服务就绪' }}</span><el-button v-if="messages.length" text :icon="Delete" @click="clearMessages">清空</el-button></div>
        </header>
        <div ref="chatBody" class="chat-body" @scroll="handleScroll">
          <div v-if="!messages.length" class="welcome-state">
            <div class="botanical-mark"><span>AI</span></div>
            <p>COMMUNITY × KNOWLEDGE × STATE</p>
            <h3>从真实种植经验里，找到更可信的答案</h3>
            <div class="welcome-copy">我会同时查找植物百科、养护指南与社区帖子；选择已关联植物后，还能结合实时环境和历史趋势给出个性化判断。</div>
            <div class="quick-grid">
              <button v-for="question in quickQuestions" :key="question" type="button" @click="useQuickQuestion(question)"><span>{{ question }}</span><el-icon><Promotion /></el-icon></button>
            </div>
          </div>
          <template v-else>
            <article v-for="message in messages" :key="message.id" :class="['message-row', message.from]">
              <div class="message-avatar"><img :src="message.from === 'ai' ? aiAvatar : userStore.user.avatar" alt=""></div>
              <div class="message-column">
                <span class="speaker">{{ message.from === 'ai' ? '小绿助手' : (userStore.user.alias || '我') }}</span>
                <div class="message-bubble">
                  <img v-if="message.imageUrl" :src="message.imageUrl" class="message-image" alt="待诊断图片">
                  <div v-if="message.from === 'user'">{{ message.rawText }}</div>
                  <div v-else-if="message.rawText" class="markdown-body" v-html="renderMarkdown(message.rawText)"></div>
                  <div v-else class="thinking"><i></i><i></i><i></i><span>正在整理证据</span></div>
                </div>
                <div v-if="message.route || message.notice" class="route-note">
                  <strong v-if="message.route">{{ ROUTE_LABELS[message.route] || message.route }}</strong>
                  <span v-if="message.notice">{{ message.notice }}</span>
                </div>
                <dl v-if="message.visualObservation && message.from === 'ai'" class="visual-observation">
                  <div v-for="([label, value], index) in observationItems(message.visualObservation)" :key="`${message.id}-${index}`"><dt>{{ label }}</dt><dd>{{ value }}</dd></div>
                </dl>
                <div v-if="message.evidence?.length" class="message-evidence">
                  <button v-for="(evidence, index) in message.evidence.slice(0, 4)" :key="evidence.id || index" type="button" @click="activeEvidence = evidence"><b>[E{{ index + 1 }}]</b>{{ sourceTitle(evidence) }}</button>
                  <span v-if="message.evidence.length > 4">+{{ message.evidence.length - 4 }}</span>
                </div>
              </div>
            </article>
          </template>
        </div>
        <button v-show="showScrollButton" type="button" class="scroll-button" @click="scrollToBottom">↓</button>
        <footer class="composer-wrap">
          <div v-if="mode !== 'search'" class="image-composer">
            <input ref="fileInput" type="file" accept="image/*" hidden @change="handleImageChange">
            <input ref="cameraInput" type="file" accept="image/*" capture="environment" hidden @change="handleImageChange">
            <div v-if="!imagePreview" class="attachment-actions">
              <button type="button" @click="chooseImage"><el-icon><Paperclip /></el-icon><span>添加图片</span></button>
              <button type="button" @click="takePhoto"><el-icon><Camera /></el-icon><span>拍照</span></button>
              <small>也可直接粘贴图片 · 最大 10 MB</small>
            </div>
            <div v-else class="image-preview"><img :src="imagePreview" alt="预览"><div class="image-copy"><strong>{{ imageName }}</strong><small>{{ attachmentNotice }}</small></div><button type="button" @click="clearImage"><el-icon><Close /></el-icon></button></div>
          </div>
          <div class="composer" @paste="handlePaste">
            <el-input v-model="inputMessage" type="textarea" :autosize="{ minRows: 1, maxRows: 4 }" :placeholder="mode === 'search' ? '描述你想寻找的种植经历…' : mode === 'diagnose' ? '可直接发送图片，或补充症状、持续时间…' : '输入问题，也可添加、拍摄或粘贴植物图片…'" resize="none" @keydown.enter.exact.prevent="sendMessage" />
            <button type="button" :class="['send-button', { stop: isLoading }]" @click="sendMessage"><el-icon><Close v-if="isLoading"/><Promotion v-else/></el-icon><span>{{ isLoading ? '停止' : mode === 'search' ? '检索' : '发送' }}</span></button>
          </div>
          <p><span>Enter 发送 · Shift + Enter 换行</span><span>图片仅临时保存，不会写入知识库</span></p>
        </footer>
      </main>

      <aside class="evidence-panel">
        <header><div><span>EVIDENCE TRACE</span><h2>本轮依据</h2></div><b>{{ currentEvidence.length }}</b></header>
        <div v-if="currentEvidence.length" class="evidence-list">
          <button v-for="(evidence, index) in currentEvidence" :key="evidence.id || index" type="button" :class="['evidence-card', evidenceMeta(evidence.type).tone]" @click="activeEvidence = evidence">
            <span class="evidence-icon"><el-icon><component :is="evidenceMeta(evidence.type).icon" /></el-icon></span>
            <span class="evidence-main"><span class="evidence-top"><em>{{ evidenceMeta(evidence.type).label }}</em><b>{{ scoreText(evidence) }}</b></span><strong>{{ sourceTitle(evidence) }}</strong><small>{{ evidence.content }}</small></span>
          </button>
        </div>
        <div v-else class="evidence-empty"><div><el-icon><Search /></el-icon></div><h3>等待一次探索</h3><p>检索到的知识、社区经验、环境状态和图像观察会在这里按来源展示。</p></div>
        <div v-if="activeEvidence" class="evidence-detail">
          <div class="detail-heading"><span>证据详情</span><button type="button" @click="activeEvidence = null"><el-icon><Close /></el-icon></button></div>
          <div class="detail-tags"><span :class="evidenceMeta(activeEvidence.type).tone">{{ evidenceMeta(activeEvidence.type).label }}</span><span>{{ scoreText(activeEvidence) }}</span></div>
          <h3>{{ sourceTitle(activeEvidence) }}</h3>
          <p>{{ activeEvidence.content }}</p>
          <button v-if="postIdOf(activeEvidence)" type="button" class="post-link" @click="openPost(activeEvidence)"><el-icon><Link /></el-icon>查看社区原帖</button>
          <dl><div><dt>来源类型</dt><dd>{{ activeEvidence.sourceType || activeEvidence.type }}</dd></div><div><dt>来源编号</dt><dd>{{ activeEvidence.sourceId || '—' }}</dd></div></dl>
        </div>
      </aside>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.rag-page { --ink:#173e32;--muted:#70847c;--line:#dde9e3;--green:#35a872;--deep:#247c57;--paper:#fffefa;height:calc(100dvh - 120px);min-height:650px;color:var(--ink);font-family:"Microsoft YaHei","PingFang SC",sans-serif }
.rag-shell{height:100%;display:grid;grid-template-columns:240px minmax(420px,1fr) 300px;overflow:hidden;border:1px solid rgba(255,255,255,.8);border-radius:18px;background:var(--paper);box-shadow:0 20px 55px rgba(31,77,59,.11)}
.context-panel{min-height:0;display:flex;flex-direction:column;gap:24px;padding:23px 18px 17px;overflow-y:auto;border-right:1px solid var(--line);background:linear-gradient(160deg,#f7faf2 0%,#edf7ef 64%,#f6efdc 145%)}
.assistant-brand{display:flex;align-items:center;gap:11px}.assistant-brand h1{margin:1px 0 0;font-family:"STZhongsong","SimSun",serif;font-size:21px}.assistant-brand span,.evidence-panel header span{color:#8da098;font-size:9px;font-weight:700;letter-spacing:.15em}.avatar-wrap{position:relative;width:46px;height:46px}.avatar-wrap img{width:100%;height:100%;object-fit:cover;border-radius:15px 15px 15px 4px;background:#dff2e6}.avatar-wrap i{position:absolute;right:-2px;bottom:1px;width:9px;height:9px;border:3px solid #f5f9f3;border-radius:50%;background:#44bd79}
.context-block{display:flex;flex-direction:column;gap:10px}.block-title{display:flex;justify-content:space-between;color:#60766c;font-size:11px;font-weight:700}.block-title b{color:#a8b7b0;font-size:9px;letter-spacing:.12em}.plant-select{width:100%}.plant-select :deep(.el-select__wrapper){min-height:39px;border-radius:10px;background:rgba(255,255,255,.85);box-shadow:0 0 0 1px #dce7e1 inset}.plant-card{display:grid;grid-template-columns:54px 1fr;gap:10px;padding:9px;border:1px solid #d9e7df;border-radius:11px;background:rgba(255,255,255,.76)}.plant-card img,.plant-fallback{width:54px;height:58px;border-radius:8px;object-fit:cover}.plant-fallback{display:grid;place-items:center;color:#4a936c;background:#dceedd;font-family:serif;font-size:22px}.plant-card>div:last-child{min-width:0;display:flex;flex-direction:column;justify-content:center;gap:3px}.plant-card strong{overflow:hidden;font-size:13px;text-overflow:ellipsis;white-space:nowrap}.plant-card span,.plant-card small{color:var(--muted);font-size:9px}.plant-card small{color:#448565}.plant-card small i{display:inline-block;width:5px;height:5px;margin-right:4px;border-radius:50%;background:#46b879}.general-note{display:flex;gap:8px;padding:11px;border:1px dashed #c8dcd1;border-radius:10px;color:var(--muted);font-size:10px;line-height:1.55}.general-note .el-icon{flex:0 0 auto;margin-top:2px;color:var(--deep)}
.mode-block{gap:7px}.mode-block .block-title{margin-bottom:2px}.mode-button{display:grid;grid-template-columns:33px 1fr;align-items:center;gap:9px;width:100%;padding:8px 9px;border:1px solid transparent;border-radius:10px;color:#677d73;background:transparent;text-align:left;cursor:pointer;transition:.2s}.mode-button:hover{background:rgba(255,255,255,.68)}.mode-button.active{color:var(--deep);border-color:#cde2d6;background:rgba(255,255,255,.94);box-shadow:0 6px 16px rgba(45,105,78,.07)}.mode-icon{width:33px;height:33px;display:grid;place-items:center;border-radius:9px;background:rgba(53,168,114,.1);font-size:16px}.mode-button strong,.mode-button small{display:block}.mode-button strong{margin-bottom:2px;font-size:11px}.mode-button small{color:#8b9b94;font-size:8px}.trust-note{margin-top:auto;display:flex;gap:8px;padding:10px;border-radius:10px;color:#537465;background:rgba(226,241,226,.75)}.trust-note .el-icon{flex:0 0 auto;color:var(--green)}.trust-note p{margin:0}.trust-note strong,.trust-note span{display:block}.trust-note strong{margin-bottom:3px;font-size:10px}.trust-note span{font-size:8px;line-height:1.45}
.conversation-panel{position:relative;min-width:0;min-height:0;display:flex;flex-direction:column;overflow:hidden}.conversation-header{min-height:70px;display:flex;align-items:center;justify-content:space-between;gap:16px;padding:12px 22px;box-sizing:border-box;border-bottom:1px solid var(--line)}.conversation-header span{color:var(--muted);font-size:9px}.conversation-header h2{margin:3px 0 0;font-family:"STZhongsong","SimSun",serif;font-size:18px}.header-actions{display:flex;align-items:center;gap:7px}.service-status{display:flex;align-items:center;gap:6px;padding:6px 8px;border:1px solid #dbe7e1;border-radius:99px;background:#fafcf9}.service-status i{width:6px;height:6px;border-radius:50%;background:#49ba78;box-shadow:0 0 0 3px rgba(73,186,120,.12)}
.chat-body{flex:1;min-height:0;overflow-y:auto;padding:25px clamp(20px,4vw,55px);scroll-behavior:smooth;background-image:radial-gradient(rgba(64,119,91,.08) .6px,transparent .6px);background-size:18px 18px}.welcome-state{min-height:100%;max-width:620px;margin:0 auto;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center}.botanical-mark{width:65px;height:65px;display:grid;place-items:center;margin-bottom:15px;border-radius:52% 48% 52% 18%;transform:rotate(-8deg);color:#37885f;background:linear-gradient(145deg,#d9f2e3,#f2f3d4);box-shadow:0 12px 28px rgba(62,145,99,.13)}.botanical-mark span{transform:rotate(8deg);font-family:Georgia,serif;font-size:18px;font-weight:700}.welcome-state>p{margin:0;color:#8ba097;font-size:9px;font-weight:700;letter-spacing:.15em}.welcome-state h3{max-width:510px;margin:8px 0 10px;font-family:"STZhongsong","SimSun",serif;font-size:clamp(21px,2.1vw,29px);line-height:1.4}.welcome-copy{max-width:530px;color:var(--muted);font-size:11px;line-height:1.8}.quick-grid{width:100%;margin-top:24px;display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.quick-grid button{min-height:60px;display:flex;align-items:center;justify-content:space-between;gap:8px;padding:11px 12px;border:1px solid #dce8e1;border-radius:11px;color:#48695b;background:rgba(255,255,255,.86);text-align:left;font-size:10px;line-height:1.5;cursor:pointer;transition:.2s}.quick-grid button:hover{transform:translateY(-2px);border-color:#a9d4bd;box-shadow:0 8px 20px rgba(46,113,81,.08)}.quick-grid .el-icon{flex:0 0 auto;color:var(--green)}
.message-row{max-width:760px;margin:0 auto 23px;display:flex;gap:10px;animation:message-in .25s ease both}.message-row.user{flex-direction:row-reverse}.message-avatar,.message-avatar img{width:35px;height:35px}.message-avatar{flex:0 0 35px}.message-avatar img{object-fit:cover;border-radius:11px 11px 11px 3px;background:#dff1e4}.message-column{min-width:0;max-width:min(82%,660px);display:flex;flex-direction:column;align-items:flex-start}.message-row.user .message-column{align-items:flex-end}.speaker{margin:0 3px 5px;color:#899a93;font-size:8px}.message-bubble{max-width:100%;padding:11px 14px;border:1px solid #e1eae5;border-radius:4px 14px 14px;color:#29483d;background:#fff;box-shadow:0 5px 15px rgba(33,78,59,.04);font-size:11px;line-height:1.7;word-break:break-word}.message-row.user .message-bubble{border:0;border-radius:14px 4px 14px 14px;color:#fff;background:linear-gradient(135deg,#3ba978,#2e8c64)}.message-image{display:block;max-width:min(100%,300px);max-height:210px;margin-bottom:9px;border-radius:9px;object-fit:cover}.markdown-body :deep(p){margin:0 0 7px}.markdown-body :deep(p:last-child){margin-bottom:0}.markdown-body :deep(ul),.markdown-body :deep(ol){margin:6px 0;padding-left:19px}.markdown-body :deep(blockquote){margin:7px 0;padding:6px 9px;border-left:3px solid #79bd9b;background:#f4faf6;color:#58756a}.thinking{display:flex;align-items:center;gap:4px;color:#7b9087;font-size:9px}.thinking i{width:5px;height:5px;border-radius:50%;background:var(--green);animation:pulse 1.2s infinite}.thinking i:nth-child(2){animation-delay:.15s}.thinking i:nth-child(3){animation-delay:.3s}.thinking span{margin-left:4px}.message-evidence{max-width:100%;margin-top:6px;display:flex;flex-wrap:wrap;gap:5px;align-items:center}.message-evidence button{padding:4px 7px;border:1px solid #dbe7e1;border-radius:7px;color:#587269;background:#fafcf9;font-size:8px;cursor:pointer}.message-evidence button b{margin-right:3px;color:var(--deep)}.message-evidence>span{color:#8a9b94;font-size:8px}
.scroll-button{position:absolute;z-index:4;right:24px;bottom:132px;width:31px;height:31px;border:1px solid #cfe0d7;border-radius:50%;color:var(--deep);background:#fff;box-shadow:0 6px 15px rgba(39,92,68,.12);cursor:pointer}.composer-wrap{padding:11px 18px 9px;border-top:1px solid var(--line);background:rgba(255,255,252,.98)}.image-composer{margin-bottom:7px}.upload-tile{width:100%;min-height:46px;display:flex;align-items:center;justify-content:center;gap:9px;border:1px dashed #a8cdb9;border-radius:10px;color:#537267;background:#f7fbf7;cursor:pointer}.upload-tile .el-icon{font-size:19px;color:var(--green)}.upload-tile strong,.upload-tile small{display:block;text-align:left}.upload-tile strong{font-size:10px}.upload-tile small{margin-top:2px;color:#91a099;font-size:8px}.image-preview{display:grid;grid-template-columns:40px 1fr 25px;align-items:center;gap:8px;padding:5px 8px;border:1px solid #dce8e1;border-radius:9px;background:#f7faf7}.image-preview img{width:40px;height:36px;border-radius:6px;object-fit:cover}.image-preview span{overflow:hidden;color:#587168;font-size:9px;text-overflow:ellipsis;white-space:nowrap}.image-preview button{border:0;color:#81938c;background:transparent;cursor:pointer}.composer{display:grid;grid-template-columns:1fr auto;align-items:end;gap:8px;padding:5px 5px 5px 13px;border:1px solid #ccdfd5;border-radius:12px;background:#fff;box-shadow:0 7px 21px rgba(39,92,68,.06)}.composer:focus-within{border-color:#80c3a1}.composer :deep(.el-textarea__inner){min-height:33px!important;padding:8px 0;border:0;box-shadow:none;color:#29483d;background:transparent;font-size:11px}.send-button{min-width:75px;height:37px;display:flex;align-items:center;justify-content:center;gap:5px;border:0;border-radius:9px;color:#fff;background:linear-gradient(135deg,#43b981,#2c9065);box-shadow:0 5px 12px rgba(45,153,103,.2);cursor:pointer}.send-button.stop{background:linear-gradient(135deg,#dc756d,#c85953)}.send-button span{font-size:10px}.composer-wrap>p{display:flex;justify-content:space-between;margin:5px 3px 0;color:#99a8a1;font-size:8px}
.evidence-panel{position:relative;min-width:0;min-height:0;display:flex;flex-direction:column;padding:20px 16px 15px;overflow:hidden;border-left:1px solid var(--line);background:#fbfcf8}.evidence-panel>header{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}.evidence-panel header h2{margin:3px 0 0;font-family:"STZhongsong","SimSun",serif;font-size:17px}.evidence-panel>header>b{min-width:26px;height:26px;display:grid;place-items:center;border-radius:8px;color:var(--deep);background:#e8f4ed;font-size:10px}.evidence-list{flex:1;min-height:0;display:flex;flex-direction:column;gap:7px;overflow-y:auto;padding-right:3px}.evidence-card{width:100%;display:grid;grid-template-columns:29px 1fr;gap:8px;padding:10px;border:1px solid #e0e9e4;border-radius:11px;color:#47665a;background:#fff;text-align:left;cursor:pointer;transition:.2s}.evidence-card:hover{transform:translateY(-1px);border-color:#a8d4bd;box-shadow:0 7px 17px rgba(38,96,68,.07)}.evidence-icon{width:29px;height:29px;display:grid;place-items:center;border-radius:8px;color:#399568;background:#eaf6ef}.evidence-card.community .evidence-icon{color:#92733e;background:#f8f0df}.evidence-card.visual .evidence-icon{color:#557da5;background:#edf3f8}.evidence-card.warning .evidence-icon{color:#aa6741;background:#faeee5}.evidence-main{min-width:0;display:block}.evidence-top{display:flex;justify-content:space-between;gap:7px;margin-bottom:3px}.evidence-top em{color:#7b8e86;font-size:8px;font-style:normal}.evidence-top b{color:#3f9569;font-size:8px}.evidence-main strong{display:block;overflow:hidden;font-size:10px;text-overflow:ellipsis;white-space:nowrap}.evidence-main small{display:-webkit-box;margin-top:4px;overflow:hidden;color:#83948d;font-size:8px;line-height:1.5;-webkit-line-clamp:2;-webkit-box-orient:vertical}.evidence-empty{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:18px;text-align:center}.evidence-empty>div{width:58px;height:58px;display:grid;place-items:center;border:1px dashed #9fc8b3;border-radius:50%;color:#65a987;background:#f2f8f2;font-size:21px}.evidence-empty h3{margin:14px 0 5px;font-family:"STZhongsong","SimSun",serif;font-size:14px}.evidence-empty p{max-width:210px;margin:0;color:#8a9b94;font-size:8px;line-height:1.65}
.evidence-detail{position:absolute;z-index:5;inset:0;padding:21px 17px;overflow-y:auto;background:rgba(251,252,248,.98);backdrop-filter:blur(10px);animation:detail-in .2s ease}.detail-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;font-family:"STZhongsong","SimSun",serif;font-size:17px;font-weight:700}.detail-heading button{width:27px;height:27px;display:grid;place-items:center;border:0;border-radius:8px;color:#71867d;background:#edf3ef;cursor:pointer}.detail-tags{display:flex;gap:5px;margin-bottom:11px}.detail-tags span{padding:4px 7px;border-radius:6px;color:#587168;background:#edf3ef;font-size:8px}.detail-tags .knowledge,.detail-tags .state{color:#2f845d;background:#e7f5ec}.detail-tags .community{color:#8f703b;background:#f8f0df}.detail-tags .visual{color:#567ca5;background:#edf3f8}.detail-tags .warning{color:#a96943;background:#faeee5}.evidence-detail h3{margin:0 0 11px;font-size:14px;line-height:1.5}.evidence-detail>p{margin:0;padding:12px;border-left:3px solid #75bd98;border-radius:0 9px 9px 0;color:#526f63;background:#f1f8f3;font-size:9px;line-height:1.75;white-space:pre-wrap}.post-link{width:100%;margin-top:12px;padding:9px;display:flex;align-items:center;justify-content:center;gap:6px;border:1px solid #add2be;border-radius:9px;color:var(--deep);background:#edf8f1;cursor:pointer}.evidence-detail dl{margin-top:18px}.evidence-detail dl div{padding:8px 0;border-bottom:1px solid #e5ece8}.evidence-detail dt{margin-bottom:3px;color:#94a29c;font-size:8px}.evidence-detail dd{margin:0;color:#557066;font-size:9px;word-break:break-all}

.route-note{max-width:100%;margin-top:7px;display:flex;align-items:flex-start;gap:7px;color:#71877d;font-size:9px;line-height:1.5}.route-note strong{flex:0 0 auto;padding:3px 7px;border-radius:99px;color:#277b57;background:#e8f5ed}.route-note span{padding-top:3px}
.visual-observation{width:100%;margin:8px 0 0;padding:9px 10px;border:1px solid #dfe8e3;border-radius:10px;background:#f8fbf9}.visual-observation div{display:grid;grid-template-columns:72px 1fr;gap:8px;padding:4px 0}.visual-observation dt{color:#7d9188;font-size:9px}.visual-observation dd{margin:0;color:#506b60;font-size:10px;line-height:1.5}
.attachment-actions{display:flex;align-items:center;gap:7px}.attachment-actions button{height:31px;display:flex;align-items:center;gap:5px;padding:0 10px;border:1px solid #d3e3da;border-radius:9px;color:#4d7061;background:#f8fbf8;font-size:10px;cursor:pointer;transition:.2s}.attachment-actions button:hover{transform:translateY(-1px);border-color:#8dc7a8;color:var(--deep);background:#edf8f1}.attachment-actions small{margin-left:2px;color:#91a099;font-size:8px}.image-preview{border-color:#bcdaca;background:linear-gradient(90deg,#f0f8f2,#fbfcf8)}.image-copy{min-width:0}.image-copy strong,.image-copy small{display:block;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}.image-copy strong{color:#3d6454;font-size:10px}.image-copy small{margin-top:3px;color:#7d9288;font-size:8px}

/* 提升 RAG 长文本阅读性与信息层级 */
.assistant-brand h1 { font-size: 23px; font-weight: 800; }
.assistant-brand span,
.evidence-panel header span { font-size: 10px; font-weight: 800; }
.block-title { font-size: 13px; font-weight: 800; }
.block-title b { font-size: 10px; font-weight: 700; }
.plant-card strong { font-size: 15px; font-weight: 800; }
.plant-card span,
.plant-card small { font-size: 11px; font-weight: 600; }
.general-note { font-size: 12px; font-weight: 600; }
.mode-button strong { font-size: 13px; font-weight: 800; }
.mode-button small { font-size: 10px; font-weight: 600; }
.trust-note strong { font-size: 12px; font-weight: 800; }
.trust-note span { font-size: 10px; font-weight: 600; }
.conversation-header > div > span { font-size: 11px; font-weight: 600; }
.conversation-header h2 { font-size: 21px; font-weight: 800; }
.service-status { font-size: 11px; font-weight: 700; }
.welcome-state > p { font-size: 10px; font-weight: 800; }
.welcome-state h3 { font-weight: 800; }
.welcome-copy { font-size: 13px; font-weight: 500; }
.quick-grid button { font-size: 12px; font-weight: 700; }
.speaker { font-size: 10px; font-weight: 700; }
.message-bubble { font-size: 14px; font-weight: 500; line-height: 1.78; }
.markdown-body :deep(strong) { font-weight: 800; }
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) { font-weight: 800; }
.thinking { font-size: 11px; font-weight: 600; }
.message-evidence button { font-size: 11px; font-weight: 600; }
.message-evidence button b { font-weight: 800; }
.message-evidence > span { font-size: 10px; font-weight: 700; }
.upload-tile strong { font-size: 12px; font-weight: 800; }
.upload-tile small,
.image-preview span { font-size: 10px; font-weight: 600; }
.composer :deep(.el-textarea__inner) { font-size: 13px; font-weight: 500; }
.send-button span { font-size: 12px; font-weight: 800; }
.composer-wrap > p { font-size: 10px; font-weight: 600; }
.evidence-panel header h2 { font-size: 20px; font-weight: 800; }
.evidence-panel > header > b { font-size: 12px; font-weight: 800; }
.evidence-top em,
.evidence-top b { font-size: 10px; font-weight: 700; }
.evidence-main strong { font-size: 13px; font-weight: 800; }
.evidence-main small { font-size: 10px; font-weight: 500; }
.evidence-empty h3 { font-size: 16px; font-weight: 800; }
.evidence-empty p { font-size: 10px; font-weight: 500; }
.detail-heading { font-size: 20px; font-weight: 800; }
.detail-tags span { font-size: 10px; font-weight: 700; }
.evidence-detail h3 { font-size: 17px; font-weight: 800; }
.evidence-detail > p { font-size: 12px; font-weight: 500; }
.post-link { font-size: 12px; font-weight: 800; }
.evidence-detail dt { font-size: 10px; font-weight: 700; }
.evidence-detail dd { font-size: 11px; font-weight: 600; }
@keyframes message-in{from{opacity:0;transform:translateY(6px)}}@keyframes detail-in{from{opacity:0;transform:translateX(9px)}}@keyframes pulse{0%,60%,100%{opacity:.35;transform:translateY(0)}30%{opacity:1;transform:translateY(-3px)}}
@media(max-width:1180px){.rag-shell{grid-template-columns:210px minmax(390px,1fr) 270px}.quick-grid{grid-template-columns:1fr;max-width:450px}.quick-grid button{min-height:44px}}@media(max-width:920px){.rag-shell{grid-template-columns:200px minmax(0,1fr)}.evidence-panel{display:none}}@media(max-width:720px){.rag-page{height:auto;min-height:calc(100vh - 100px)}.rag-shell{min-height:inherit;grid-template-columns:1fr;overflow:visible;border-radius:13px}.context-panel{display:block;padding:13px;border-right:0;border-bottom:1px solid var(--line)}.assistant-brand,.mode-block,.trust-note{display:none}.context-block{gap:7px}.plant-card{display:none}.conversation-panel{min-height:660px}.conversation-header{padding:11px 14px}.service-status{display:none}.chat-body{padding:19px 13px}.message-column{max-width:88%}.composer-wrap{padding:9px}.composer-wrap>p span:last-child{display:none}}
</style>
