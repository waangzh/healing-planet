<template>
  <div class="plant-detail">
    <div class="page-title">
      <el-button :icon="ArrowLeft" @click="$router.go(-1)">返回</el-button>
      <span>植物详情</span>
    </div>
    
    <div v-if="plantInfo" class="plant-detail-content">
      <!-- 植物基本信息 -->
      <div class="admin-card">
        <div class="card-header">
          <h3 class="card-title">{{ plantInfo.name }}</h3>
          <div class="card-actions">
            <el-button type="primary" @click="editPlant">
              <el-icon><Edit /></el-icon>
              编辑信息
            </el-button>
            <el-button
              :type="plantInfo.isRecommended ? 'warning' : 'success'"
              @click="toggleRecommend"
            >
              {{ plantInfo.isRecommended ? '取消推荐' : '设为推荐' }}
            </el-button>
            <el-button type="danger" @click="deletePlant">删除植物</el-button>
          </div>
        </div>
        <div class="card-body">
          <div class="plant-overview">
            <div class="plant-image">
              <img :src="plantInfo.image" :alt="plantInfo.name" />
            </div>
            <div class="plant-info">
              <div class="info-row">
                <span class="label">学名：</span>
                <span class="value">{{ plantInfo.scientificName }}</span>
              </div>
              <div class="info-row">
                <span class="label">科属：</span>
                <span class="value">{{ plantInfo.family }}</span>
              </div>
              <div class="info-row">
                <span class="label">类型：</span>
                <span class="value">{{ plantInfo.type }}</span>
              </div>
              <div class="info-row">
                <span class="label">原产地：</span>
                <span class="value">{{ plantInfo.origin }}</span>
              </div>
              <div class="info-row">
                <span class="label">花期：</span>
                <span class="value">{{ plantInfo.floweringSeason }}</span>
              </div>
              <div class="info-row">
                <span class="label">状态：</span>
                <el-tag :type="plantInfo.isRecommended ? 'success' : 'info'">
                  {{ plantInfo.isRecommended ? '推荐植物' : '普通植物' }}
                </el-tag>
              </div>
            </div>
          </div>
          
          <div class="plant-description">
            <h4>植物描述</h4>
            <p>{{ plantInfo.description }}</p>
          </div>
        </div>
      </div>
      
      <!-- 养护指南 -->
      <div class="admin-card">
        <div class="card-header">
          <h3 class="card-title">养护指南</h3>
        </div>
        <div class="card-body">
          <el-row :gutter="24">
            <el-col :xs="24" :sm="12" :md="8">
              <div class="care-item">
                <div class="care-icon">
                  <el-icon size="24" color="#67c23a"><Sunny /></el-icon>
                </div>
                <div class="care-content">
                  <h5>光照需求</h5>
                  <p>{{ plantInfo.careGuide.light }}</p>
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <div class="care-item">
                <div class="care-icon">
                  <el-icon size="24" color="#409eff"><Coffee /></el-icon>
                </div>
                <div class="care-content">
                  <h5>浇水频率</h5>
                  <p>{{ plantInfo.careGuide.water }}</p>
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <div class="care-item">
                <div class="care-icon">
                  <el-icon size="24" color="#e6a23c"><Setting /></el-icon>
                </div>
                <div class="care-content">
                  <h5>温度要求</h5>
                  <p>{{ plantInfo.careGuide.temperature }}</p>
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <div class="care-item">
                <div class="care-icon">
                  <el-icon size="24" color="#909399"><Cloudy /></el-icon>
                </div>
                <div class="care-content">
                  <h5>湿度要求</h5>
                  <p>{{ plantInfo.careGuide.humidity }}</p>
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <div class="care-item">
                <div class="care-icon">
                  <el-icon size="24" color="#f56c6c"><Location /></el-icon>
                </div>
                <div class="care-content">
                  <h5>土壤要求</h5>
                  <p>{{ plantInfo.careGuide.soil }}</p>
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <div class="care-item">
                <div class="care-icon">
                  <el-icon size="24" color="#67c23a"><Orange /></el-icon>
                </div>
                <div class="care-content">
                  <h5>施肥建议</h5>
                  <p>{{ plantInfo.careGuide.fertilizer }}</p>
                </div>
              </div>
            </el-col>
          </el-row>
        </div>
      </div>
      
      <!-- 统计信息 -->
      <el-row :gutter="24" class="stats-row">
        <el-col :xs="12" :sm="6">
          <div class="stat-card primary">
            <el-icon class="stat-icon"><View /></el-icon>
            <div class="stat-number">{{ plantInfo.viewCount }}</div>
            <div class="stat-label">浏览次数</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="6">
          <div class="stat-card success">
            <el-icon class="stat-icon"><Star /></el-icon>
            <div class="stat-number">{{ plantInfo.favoriteCount }}</div>
            <div class="stat-label">收藏次数</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="6">
          <div class="stat-card warning">
            <el-icon class="stat-icon"><ChatLineRound /></el-icon>
            <div class="stat-number">{{ plantInfo.discussionCount }}</div>
            <div class="stat-label">讨论数</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="6">
          <div class="stat-card danger">
            <el-icon class="stat-icon"><Share /></el-icon>
            <div class="stat-number">{{ plantInfo.shareCount }}</div>
            <div class="stat-label">分享次数</div>
          </div>
        </el-col>
      </el-row>
      
      <!-- 注意事项 -->
      <div class="admin-card">
        <div class="card-header">
          <h3 class="card-title">注意事项</h3>
        </div>
        <div class="card-body">
          <div class="tips-list">
            <div v-for="(tip, index) in plantInfo.tips" :key="index" class="tip-item">
              <el-icon class="tip-icon" color="#e6a23c"><Warning /></el-icon>
              <span>{{ tip }}</span>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 编辑对话框 -->
      <el-dialog
        v-model="editDialogVisible"
        title="编辑植物信息"
        width="800px"
        destroy-on-close
      >
        <el-form v-if="editForm" :model="editForm" label-width="120px">
          <el-row :gutter="24">
            <el-col :span="12">
              <el-form-item label="植物名称">
                <el-input v-model="editForm.name" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="学名">
                <el-input v-model="editForm.scientificName" />
              </el-form-item>
            </el-col>
          </el-row>
          
          <el-row :gutter="24">
            <el-col :span="12">
              <el-form-item label="科属">
                <el-input v-model="editForm.family" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="类型">
                <el-input v-model="editForm.type" />
              </el-form-item>
            </el-col>
          </el-row>
          
          <el-row :gutter="24">
            <el-col :span="12">
              <el-form-item label="原产地">
                <el-input v-model="editForm.origin" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="花期">
                <el-input v-model="editForm.floweringSeason" />
              </el-form-item>
            </el-col>
          </el-row>
          
          <el-form-item label="植物描述">
            <el-input v-model="editForm.description" type="textarea" :rows="4" />
          </el-form-item>
          
          <el-form-item label="植物图片">
            <el-input v-model="editForm.image" placeholder="请输入图片URL" />
          </el-form-item>
          
          <el-form-item label="推荐状态">
            <el-switch v-model="editForm.isRecommended" />
          </el-form-item>
        </el-form>
        
        <template #footer>
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveEdit">保存</el-button>
        </template>
      </el-dialog>
    </div>
    
    <div v-else class="loading-container">
      <el-loading-spinner />
      <span>加载中...</span>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  ArrowLeft, 
  Edit,
  View, 
  Star, 
  ChatLineRound, 
  Share,
  Sunny,
  Coffee,
  Setting,
  Cloudy,
  Location,
  Orange,
  Warning
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

// 响应式数据
const plantInfo = ref(null)
const editDialogVisible = ref(false)
const editForm = ref(null)

// 方法
const editPlant = () => {
  editForm.value = { ...plantInfo.value }
  editDialogVisible.value = true
}

const saveEdit = () => {
  Object.assign(plantInfo.value, editForm.value)
  editDialogVisible.value = false
  ElMessage.success('植物信息已保存')
}

const toggleRecommend = async () => {
  const action = plantInfo.value.isRecommended ? '取消推荐' : '设为推荐'
  
  try {
    await ElMessageBox.confirm(
      `确定要${action}植物 "${plantInfo.value.name}" 吗？`,
      '确认操作',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }
    )
    
    plantInfo.value.isRecommended = !plantInfo.value.isRecommended
    ElMessage.success(`${action}成功`)
    
  } catch {
    // 用户取消操作
  }
}

const deletePlant = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要删除植物 "${plantInfo.value.name}" 吗？此操作不可恢复！`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'error'
      }
    )
    
    ElMessage.success('植物删除成功')
    router.push('/plants')
    
  } catch {
    // 用户取消操作
  }
}

// 获取植物详情
const fetchPlantDetail = async () => {
  try {
    const plantId = route.params.id
    
    // 模拟API调用
    plantInfo.value = {
      id: plantId,
      name: `植物名称${plantId}`,
      scientificName: `Scientific Name ${plantId}`,
      family: '景天科',
      type: '多肉植物',
      origin: '南非',
      floweringSeason: '春季',
      description: `这是植物${plantId}的详细描述，包含了植物的基本特征、生长习性等信息。这种植物易于养护，适合新手种植。`,
      image: `https://picsum.photos/400/300?random=${plantId}`,
      isRecommended: Math.random() > 0.5,
      careGuide: {
        light: '喜光，需要充足的阳光照射',
        water: '春秋季节每周浇水1-2次',
        temperature: '适宜温度15-25℃',
        humidity: '适中湿度，避免过于潮湿',
        soil: '疏松透气的沙质土壤',
        fertilizer: '生长期每月施肥一次'
      },
      tips: [
        '避免长期积水，容易导致根部腐烂',
        '夏季高温时需要适当遮阴',
        '冬季要控制浇水频率',
        '定期转动花盆，使植物受光均匀'
      ],
      viewCount: Math.floor(Math.random() * 5000),
      favoriteCount: Math.floor(Math.random() * 1000),
      discussionCount: Math.floor(Math.random() * 200),
      shareCount: Math.floor(Math.random() * 100),
      createdAt: new Date(Date.now() - Math.random() * 365 * 24 * 60 * 60 * 1000).toISOString()
    }
    
  } catch (error) {
    ElMessage.error('获取植物详情失败')
    router.go(-1)
  }
}

// 生命周期
onMounted(() => {
  fetchPlantDetail()
})
</script>

<style lang="scss" scoped>
.plant-detail {
  .page-title {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 20px;
    font-size: 24px;
    font-weight: 600;
    color: #303133;
  }
  
  .plant-detail-content {
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    
    .plant-overview {
      display: flex;
      gap: 24px;
      margin-bottom: 24px;
      
      .plant-image {
        flex-shrink: 0;
        
        img {
          width: 200px;
          height: 200px;
          object-fit: cover;
          border-radius: 12px;
          box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
        }
      }
      
      .plant-info {
        flex: 1;
        
        .info-row {
          display: flex;
          align-items: center;
          margin-bottom: 12px;
          
          .label {
            width: 80px;
            font-weight: 500;
            color: #303133;
          }
          
          .value {
            color: #606266;
          }
        }
      }
    }
    
    .plant-description {
      h4 {
        margin-bottom: 12px;
        color: #303133;
        font-weight: 600;
      }
      
      p {
        line-height: 1.8;
        color: #606266;
      }
    }
    
    .stats-row {
      margin: 24px 0;
    }
    
    .care-item {
      display: flex;
      align-items: flex-start;
      gap: 16px;
      padding: 20px;
      background: #f8f9fa;
      border-radius: 12px;
      margin-bottom: 16px;
      transition: all 0.3s ease;
      
      &:hover {
        background: #f0f2f5;
        transform: translateY(-2px);
      }
      
      .care-icon {
        flex-shrink: 0;
        padding: 8px;
        background: white;
        border-radius: 8px;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
      }
      
      .care-content {
        flex: 1;
        
        h5 {
          margin: 0 0 8px 0;
          font-weight: 600;
          color: #303133;
        }
        
        p {
          margin: 0;
          color: #606266;
          line-height: 1.6;
        }
      }
    }
    
    .tips-list {
      .tip-item {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 12px 0;
        border-bottom: 1px solid #f0f0f0;
        
        &:last-child {
          border-bottom: none;
        }
        
        .tip-icon {
          flex-shrink: 0;
        }
        
        span {
          color: #606266;
          line-height: 1.6;
        }
      }
    }
  }
  
  .loading-container {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 200px;
    gap: 16px;
    color: #909399;
  }
}

@media (max-width: 768px) {
  .plant-detail {
    .card-header {
      flex-direction: column;
      gap: 12px;
      align-items: flex-start;
    }
    
    .plant-overview {
      flex-direction: column;
      text-align: center;
      
      .plant-image img {
        width: 150px;
        height: 150px;
      }
    }
    
    .care-item {
      text-align: left;
    }
  }
}
</style>
