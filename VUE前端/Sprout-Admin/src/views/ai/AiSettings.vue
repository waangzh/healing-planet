<template>
  <div class="rag-settings" v-loading="loading">
    <section class="hero">
      <div>
        <p class="eyebrow">RETRIEVAL CONTROL ROOM</p>
        <h2 class="page-title">RAG 检索配置</h2>
        <p class="hero-copy">调整低风险检索参数；发布后新请求会使用完整的新版本快照。</p>
      </div>
      <div class="revision-chip">
        <span>当前生效</span>
        <strong>v{{ activeRevision?.revision ?? '—' }}</strong>
        <small>{{ activeRevision?.status === 'ACTIVE' ? '运行中' : '未加载' }}</small>
      </div>
    </section>

    <el-alert
      title="本页仅管理检索策略、TopK、阈值、重排开关与排序权重；模型、Embedding、Qdrant 连接和索引结构不在本阶段开放。"
      type="info"
      :closable="false"
      show-icon
      class="scope-alert"
    />

    <div class="admin-card config-card">
      <div class="card-header card-header--split">
        <div>
          <p class="section-kicker">DRAFT EDITOR</p>
          <h3 class="card-title">检索策略草稿</h3>
        </div>
        <el-input v-model="description" maxlength="500" show-word-limit placeholder="本次调整说明（会写入审计日志）" class="description-input" />
      </div>

      <div class="card-body">
        <el-form :model="form" label-position="top" class="rag-form">
          <section class="form-section">
            <div class="form-section__title">
              <span>01</span>
              <div><h4>召回与融合</h4><p>控制候选池规模与混合检索策略。</p></div>
            </div>
            <div class="form-grid form-grid--four">
              <el-form-item label="检索模式">
                <el-select v-model="form.retrievalMode">
                  <el-option label="混合检索（Dense + BM25 + RRF）" value="HYBRID_RRF" />
                  <el-option label="仅向量检索（Dense）" value="DENSE_ONLY" />
                  <el-option label="仅关键词检索（BM25）" value="BM25_ONLY" />
                </el-select>
              </el-form-item>
              <el-form-item label="Dense TopK"><el-input-number v-model="form.denseTopK" :min="1" :max="100" controls-position="right" /></el-form-item>
              <el-form-item label="BM25 TopK"><el-input-number v-model="form.sparseTopK" :min="1" :max="100" controls-position="right" /></el-form-item>
              <el-form-item label="最终证据数"><el-input-number v-model="form.finalTopK" :min="1" :max="30" controls-position="right" /></el-form-item>
              <el-form-item label="向量相似度阈值"><el-input-number v-model="form.similarityThreshold" :min="0" :max="1" :step="0.01" :precision="2" controls-position="right" /></el-form-item>
              <el-form-item label="RRF K"><el-input-number v-model="form.rrfK" :min="1" :max="500" controls-position="right" /></el-form-item>
              <el-form-item label="重排服务"><div class="switch-line"><el-switch v-model="form.rerankerEnabled" active-text="启用" inactive-text="关闭" /></div></el-form-item>
              <el-form-item label="证据覆盖选择器"><div class="switch-line"><el-switch v-model="form.evidenceSelectorEnabled" active-text="启用" inactive-text="关闭" /></div></el-form-item>
              <el-form-item label="社区证据上限"><el-input-number v-model="form.mixedSourceCommunityLimit" :min="0" :max="30" controls-position="right" /></el-form-item>
            </div>
          </section>

          <section class="form-section">
            <div class="form-section__title">
              <span>02</span>
              <div><h4>来源感知排序</h4><p>各组权重必须分别相加为 1，发布前会由服务端再次校验。</p></div>
              <el-switch v-model="form.sourceAwareRanking.enabled" active-text="启用排序" inactive-text="关闭排序" />
            </div>
            <div class="weight-groups">
              <div class="weight-group">
                <h5>检索得分融合</h5>
                <NumberField v-model="form.sourceAwareRanking.rrfNormalizationFactor" label="RRF 归一化系数" :min="1" :max="100" />
                <NumberField v-model="form.sourceAwareRanking.denseWeight" label="Dense 权重" :min="0" :max="1" :step="0.01" />
                <NumberField v-model="form.sourceAwareRanking.rrfWeight" label="RRF 权重" :min="0" :max="1" :step="0.01" />
              </div>
              <div class="weight-group">
                <h5>植物知识排序</h5>
                <NumberField v-model="form.sourceAwareRanking.plantSemanticWeight" label="语义相关性" :min="0" :max="1" :step="0.01" />
                <NumberField v-model="form.sourceAwareRanking.plantTrustWeight" label="可信度" :min="0" :max="1" :step="0.01" />
                <NumberField v-model="form.sourceAwareRanking.plantMatchWeight" label="植物匹配度" :min="0" :max="1" :step="0.01" />
              </div>
              <div class="weight-group">
                <h5>社区内容排序</h5>
                <NumberField v-model="form.sourceAwareRanking.communitySemanticWeight" label="语义相关性" :min="0" :max="1" :step="0.01" />
                <NumberField v-model="form.sourceAwareRanking.communityTrustWeight" label="可信度" :min="0" :max="1" :step="0.01" />
                <NumberField v-model="form.sourceAwareRanking.communityQualityWeight" label="内容质量" :min="0" :max="1" :step="0.01" />
                <NumberField v-model="form.sourceAwareRanking.communityRecencyWeight" label="新鲜度" :min="0" :max="1" :step="0.01" />
                <NumberField v-model="form.sourceAwareRanking.communityPlantMatchWeight" label="植物匹配度" :min="0" :max="1" :step="0.01" />
              </div>
              <div class="weight-group">
                <h5>社区质量计算</h5>
                <NumberField v-model="form.sourceAwareRanking.communityEssenceWeight" label="精华系数" :min="0" :max="1" :step="0.01" />
                <NumberField v-model="form.sourceAwareRanking.communityEngagementWeight" label="互动系数" :min="0" :max="1" :step="0.01" />
                <NumberField v-model="form.sourceAwareRanking.collectWeight" label="收藏权重" :min="0" :max="10" :step="0.1" />
                <NumberField v-model="form.sourceAwareRanking.commentWeight" label="评论权重" :min="0" :max="10" :step="0.1" />
                <NumberField v-model="form.sourceAwareRanking.viewWeight" label="浏览权重" :min="0" :max="10" :step="0.01" />
                <NumberField v-model="form.sourceAwareRanking.engagementNormalization" label="互动归一化" :min="1" :max="100000" />
                <NumberField v-model="form.sourceAwareRanking.recencyDecayDays" label="新鲜度衰减（天）" :min="1" :max="3650" />
              </div>
            </div>
          </section>
        </el-form>
      </div>

      <div class="action-bar">
        <div class="draft-state">{{ draftRevision ? `草稿版本 v${draftRevision}` : '尚未保存草稿' }}</div>
        <div>
          <el-button :disabled="submitting" @click="resetToActive">恢复当前生效配置</el-button>
          <el-button type="primary" plain :loading="submitting" @click="saveDraft">保存草稿</el-button>
          <el-button type="warning" :loading="submitting" @click="validateDraft">校验草稿</el-button>
          <el-button type="primary" :loading="submitting" @click="publishDraft">发布并应用</el-button>
        </div>
      </div>
    </div>

    <div class="admin-card history-card">
      <div class="card-header card-header--split">
        <div><p class="section-kicker">AUDIT TRAIL</p><h3 class="card-title">版本历史与回滚</h3></div>
        <el-button :icon="Refresh" circle @click="loadPage" />
      </div>
      <div class="card-body table-wrap">
        <el-table :data="revisions" size="small" stripe>
          <el-table-column prop="revision" label="版本" width="90"><template #default="{ row }">v{{ row.revision }}</template></el-table-column>
          <el-table-column prop="status" label="状态" width="120"><template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template></el-table-column>
          <el-table-column prop="description" label="调整说明" min-width="220" show-overflow-tooltip />
          <el-table-column prop="createdBy" label="创建人" width="130" />
          <el-table-column prop="createdAt" label="创建时间" width="180"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
          <el-table-column label="操作" width="110" fixed="right"><template #default="{ row }"><el-button link type="danger" :disabled="row.status === 'ACTIVE' || row.status === 'DRAFT' || row.status === 'FAILED' || submitting" @click="rollback(row)">回滚至此</el-button></template></el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import { ElInputNumber, ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { createRagConfigDraft, getRagConfigCurrent, getRagConfigRevisions, publishRagConfigDraft, rollbackRagConfig, validateRagConfigDraft } from '@/api/rag-config'

const NumberField = defineComponent({
  props: { modelValue: { type: Number, required: true }, label: { type: String, required: true }, min: Number, max: Number, step: Number },
  emits: ['update:modelValue'],
  setup (props, { emit }) {
    return () => h('label', { class: 'number-field' }, [
      h('span', props.label),
      h(ElInputNumber, { modelValue: props.modelValue, min: props.min, max: props.max, step: props.step || 1, precision: props.step && props.step < 1 ? 2 : 0, controlsPosition: 'right', onUpdateModelValue: value => emit('update:modelValue', value) })
    ])
  }
})

const defaultConfig = () => ({
  denseTopK: 30, sparseTopK: 30, finalTopK: 8, similarityThreshold: 0.25, retrievalMode: 'HYBRID_RRF', rrfK: 60,
  rerankerEnabled: false, evidenceSelectorEnabled: true, mixedSourceCommunityLimit: 3,
  sourceAwareRanking: {
    enabled: true, rrfNormalizationFactor: 31, denseWeight: 0.55, rrfWeight: 0.45,
    plantSemanticWeight: 0.7, plantTrustWeight: 0.2, plantMatchWeight: 0.1,
    communitySemanticWeight: 0.62, communityTrustWeight: 0.15, communityQualityWeight: 0.13, communityRecencyWeight: 0.05, communityPlantMatchWeight: 0.05,
    communityEssenceWeight: 0.2, communityEngagementWeight: 0.8, collectWeight: 2, commentWeight: 1.5, viewWeight: 0.05, engagementNormalization: 1000, recencyDecayDays: 365
  }
})

const loading = ref(false)
const submitting = ref(false)
const activeRevision = ref(null)
const revisions = ref([])
const draftRevision = ref(null)
const description = ref('')
const form = reactive(defaultConfig())
const hasActive = computed(() => activeRevision.value?.config)
const unwrap = (response) => {
  const payload = response?.data ?? response
  if (payload?.code != null && payload.code !== 200) throw new Error(payload.message || '请求失败')
  return payload?.code != null ? payload.data : payload
}
const clone = value => JSON.parse(JSON.stringify(value))
const applyConfig = config => Object.assign(form, clone(config || defaultConfig()))

const loadHistory = async () => { revisions.value = unwrap(await getRagConfigRevisions()) || [] }
const loadPage = async () => {
  loading.value = true
  try {
    const [currentResponse, historyResponse] = await Promise.all([getRagConfigCurrent(), getRagConfigRevisions()])
    activeRevision.value = unwrap(currentResponse)
    revisions.value = unwrap(historyResponse) || []
    if (!draftRevision.value) applyConfig(activeRevision.value.config)
  } catch (error) {
    ElMessage.error(error.message || '加载 RAG 配置失败')
  } finally { loading.value = false }
}

const saveDraft = async () => {
  submitting.value = true
  try {
    const view = unwrap(await createRagConfigDraft({ config: clone(form), description: description.value }))
    draftRevision.value = view.revision
    ElMessage.success(`草稿 v${view.revision} 已保存`)
    await loadHistory()
    return view.revision
  } catch (error) {
    ElMessage.error(error.message || '保存草稿失败')
    return null
  } finally { submitting.value = false }
}
const ensureDraft = async () => draftRevision.value || await saveDraft()
const validateDraft = async () => {
  const revision = await ensureDraft()
  if (!revision) return false
  submitting.value = true
  try {
    const result = unwrap(await validateRagConfigDraft(revision))
    if (!result.valid) { ElMessage.error((result.errors || ['服务端校验未通过']).join('；')); return false }
    ElMessage.success(`草稿 v${revision} 校验通过`)
    await loadHistory()
    return true
  } catch (error) {
    ElMessage.error(error.message || '校验草稿失败')
    return false
  } finally { submitting.value = false }
}
const publishDraft = async () => {
  const revision = await ensureDraft()
  if (!revision || !(await validateDraft())) return
  try {
    await ElMessageBox.confirm(`确认发布草稿 v${revision}？发布后新 RAG 请求会立即使用该版本。`, '发布 RAG 配置', { type: 'warning', confirmButtonText: '发布', cancelButtonText: '取消' })
  } catch { return }
  submitting.value = true
  try {
    const view = unwrap(await publishRagConfigDraft(revision))
    activeRevision.value = view
    applyConfig(view.config)
    draftRevision.value = null
    description.value = ''
    ElMessage.success(`v${view.revision} 已发布并应用`)
    await loadHistory()
  } catch (error) { ElMessage.error(error.message || '发布失败') } finally { submitting.value = false }
}
const rollback = async row => {
  try {
    await ElMessageBox.confirm(`确认回滚到 v${row.revision}？当前生效版本会保留在历史中。`, '回滚 RAG 配置', { type: 'warning', confirmButtonText: '确认回滚', cancelButtonText: '取消' })
  } catch { return }
  submitting.value = true
  try {
    const view = unwrap(await rollbackRagConfig(row.revision))
    activeRevision.value = view
    applyConfig(view.config)
    draftRevision.value = null
    ElMessage.success(`已回滚并应用 v${view.revision}`)
    await loadHistory()
  } catch (error) { ElMessage.error(error.message || '回滚失败') } finally { submitting.value = false }
}
const resetToActive = () => {
  if (!hasActive.value) return
  applyConfig(activeRevision.value.config)
  draftRevision.value = null
  description.value = ''
  ElMessage.info('已恢复为当前生效配置')
}
const formatTime = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
const statusText = status => ({ DRAFT: '草稿', VALIDATED: '已校验', ACTIVE: '生效中', SUPERSEDED: '已替换', FAILED: '失败' })[status] || status
const statusType = status => ({ DRAFT: 'info', VALIDATED: 'warning', ACTIVE: 'success', SUPERSEDED: '', FAILED: 'danger' })[status] || 'info'
onMounted(loadPage)
</script>

<style lang="scss" scoped>
.rag-settings { --ink: #24372f; --moss: #2f6a4f; --leaf: #80aa72; --paper: #f8faf5; --line: #dce8da; max-width: 1480px; }
.hero { display: flex; align-items: end; justify-content: space-between; padding: 6px 2px 22px; border-bottom: 1px solid var(--line); margin-bottom: 20px; }
.eyebrow, .section-kicker { margin: 0 0 5px; color: var(--moss); font-size: 11px; letter-spacing: .16em; font-weight: 800; }.page-title { margin: 0; color: var(--ink); letter-spacing: -.03em; }.hero-copy { margin: 8px 0 0; color: #697b70; }
.revision-chip { display: grid; grid-template-columns: auto auto; gap: 2px 12px; align-items: center; padding: 12px 16px; background: var(--ink); color: #fff; border-radius: 3px 18px 3px 18px; box-shadow: 6px 6px 0 #d5e4d0; }.revision-chip span { font-size: 12px; color: #b7d3bd; }.revision-chip strong { font-size: 24px; line-height: 1; }.revision-chip small { grid-column: 1 / -1; color: #9ecfa8; }
.scope-alert { margin-bottom: 20px; }.config-card, .history-card { border: 1px solid var(--line); box-shadow: none; margin-bottom: 22px; overflow: hidden; }.card-header--split { display: flex; align-items: center; justify-content: space-between; gap: 24px; background: linear-gradient(100deg, #f2f7ef, #fbfcf8); border-bottom: 1px solid var(--line); }.card-title { margin: 0; color: var(--ink); }.description-input { max-width: 440px; }
.form-section { padding: 25px 0; border-bottom: 1px solid #e8efe6; }.form-section:last-child { border-bottom: 0; padding-bottom: 4px; }.form-section__title { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }.form-section__title > span { width: 29px; height: 29px; display: grid; place-items: center; color: #fff; background: var(--moss); border-radius: 50% 50% 50% 5%; font-size: 12px; font-weight: 800; }.form-section__title > div { flex: 1; }.form-section__title h4 { margin: 0; color: var(--ink); }.form-section__title p { margin: 3px 0 0; font-size: 12px; color: #7a8a7d; }
.form-grid { display: grid; gap: 2px 18px; }.form-grid--four { grid-template-columns: repeat(4, minmax(150px, 1fr)); }.rag-form :deep(.el-input-number), .rag-form :deep(.el-select) { width: 100%; }.switch-line { height: 32px; display: flex; align-items: center; }.weight-groups { display: grid; grid-template-columns: repeat(4, minmax(210px, 1fr)); gap: 14px; }.weight-group { padding: 16px; background: var(--paper); border-left: 3px solid var(--leaf); }.weight-group h5 { margin: 0 0 14px; color: var(--ink); font-size: 13px; }.number-field { display: flex; align-items: center; justify-content: space-between; gap: 8px; min-height: 36px; font-size: 12px; color: #607269; }.number-field :deep(.el-input-number) { width: 102px; }
.action-bar { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 17px 20px; background: #f5f8f2; border-top: 1px solid var(--line); }.draft-state { color: #667c6a; font-size: 13px; font-weight: 600; }.table-wrap { padding-top: 4px; }.history-card :deep(.el-table__header-wrapper th) { color: #536c59; background: #f5f8f2; }
@media (max-width: 1200px) { .form-grid--four { grid-template-columns: repeat(3, 1fr); }.weight-groups { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 768px) { .hero, .card-header--split, .action-bar { align-items: stretch; flex-direction: column; }.revision-chip { align-self: flex-start; }.description-input { max-width: none; }.form-grid--four, .weight-groups { grid-template-columns: 1fr; }.action-bar > div:last-child { display: grid; gap: 8px; }.action-bar :deep(.el-button) { margin-left: 0; } }
</style>
