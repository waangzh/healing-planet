<script setup>
import { ref, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getPostDetail, getPostComment, addPostComment, getRecommendPost, likePost, collectPost, checkLike, checkCollect, recordPostView } from '@/api/post';
import { getArticleRecommend } from '@/api/recommend';
import { followUser, unfollowUser, checkFollow } from '@/api/relationship';
import { useUserStore } from '@/stores/modules/user';
import { ElMessage } from 'element-plus';
import MarkdownIt from 'markdown-it';

// 创建MarkdownIt实例并配置
const md = new MarkdownIt({
  html: true,
  breaks: true,
  linkify: true,
  typographer: true
});

// 添加自定义渲染规则
md.renderer.rules.paragraph_open = () => '<p class="markdown-paragraph">';

// 添加简单的emoji支持 - 手动添加一些常用的emoji
const emojiMap = {
  ':date:': '📅',
  ':calendar:': '📅',
  ':cactus:': '🌵',
  ':sunflower:': '🌻',
  ':herb:': '🌿',
  ':leaves:': '🍃',
  ':seedling:': '🌱',
  ':evergreen_tree:': '🌲',
  ':deciduous_tree:': '🌳',
  ':palm_tree:': '🌴',
  ':ear_of_rice:': '🌾',
  ':hibiscus:': '🌺',
  ':tulip:': '🌷',
  ':blossom:': '🌼',
  ':cherry_blossom:': '🌸',
  ':rose:': '🌹',
  ':fallen_leaf:': '🍂',
  ':maple_leaf:': '🍁',
  ':mushroom:': '🍄',
  ':droplet:': '💧',
  ':sun:': '☀️',
  ':cloud:': '☁️',
  ':umbrella:': '☔'
};

// 替换文本中的emoji代码
const replaceEmojis = (text) => {
  let result = text;
  for (const [code, emoji] of Object.entries(emojiMap)) {
    result = result.replace(new RegExp(code, 'g'), emoji);
  }
  return result;
};

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const postDetail = ref(null);
const loading = ref(true);
const comments = ref([]);
const showComments = ref(false);
const replyContent = ref('');
const replyTo = ref(null);
const submitting = ref(false);
const recommendPosts = ref([]);
const recommendPage = ref(1);
const recommendLoading = ref(false);
const isFollowing = ref(false);
const followLoading = ref(false);
const isLiked = ref(false);
const isCollected = ref(false);
const likeLoading = ref(false);
const collectLoading = ref(false);

// 文章目录相关
const tocItems = ref([]);
const showToc = ref(true);

// 从内容中提取目录
const extractToc = (content) => {
  const headings = [];
  const existingIds = new Set(); // 用于跟踪已添加的ID，避免重复
  
  // 提取HTML标题标签 <h1>、<h2>等
  const htmlHeadingRegex = /<h([1-6])[^>]*>(.*?)<\/h\1>/g;
  let htmlHeadingMatch;
  
  while ((htmlHeadingMatch = htmlHeadingRegex.exec(content)) !== null) {
    const level = parseInt(htmlHeadingMatch[1]);
    // 处理可能包含的HTML和Markdown混合内容
    let text = htmlHeadingMatch[2].replace(/#\s+/, '').trim();
    // 移除Markdown中的**加粗标记和其他常见标记
    text = text.replace(/\*\*(.*?)\*\*/g, '$1')  // 移除加粗标记 **text**
               .replace(/\*(.*?)\*/g, '$1')      // 移除斜体标记 *text*
               .replace(/__(.*?)__/g, '$1')      // 移除加粗标记 __text__
               .replace(/_(.*?)_/g, '$1')        // 移除斜体标记 _text_
               .trim();
    const id = text.toLowerCase().replace(/\s+/g, '-').replace(/[^\w\u4e00-\u9fa5-]/g, '');
    
    // 检查ID是否已存在，避免重复添加
    if (!existingIds.has(id)) {
      existingIds.add(id);
      headings.push({
        level,
        text,
        id
      });
    }
  }
  
  // 如果内容包含HTML标签（如<p>标签）
  if (content && /<p>/.test(content) && headings.length === 0) {
    // 处理从富文本编辑器生成的内容
    // 查找所有可能的标题格式
    const regex = /<p>(?:#{1,6}|🌱|🌞|💧|☀️|🌡️|🌸|🆘)\s*([^<]+)<\/p>/g;
    let match;
    
    while ((match = regex.exec(content)) !== null) {
      // 确保找到的文本确实是标题内容而非普通文本
      let headerText = match[1].trim();
      // 移除Markdown中的**加粗标记和其他常见标记
      headerText = headerText.replace(/\*\*(.*?)\*\*/g, '$1')  // 移除加粗标记 **text**
                            .replace(/\*(.*?)\*/g, '$1')      // 移除斜体标记 *text*
                            .replace(/__(.*?)__/g, '$1')      // 移除加粗标记 __text__
                            .replace(/_(.*?)_/g, '$1')        // 移除斜体标记 _text_
                            .trim();
      if (headerText) {
        // 为emoji标题和#标题创建不同的层级
        let level = 1; // 默认为h1级别
        
        // 如果标题以#开头，计算#的数量作为级别
        if (match[0].includes('#')) {
          const hashMatch = match[0].match(/#{1,6}/);
          if (hashMatch) {
            level = hashMatch[0].length;
          }
        }
        
        const id = headerText.toLowerCase().replace(/\s+/g, '-').replace(/[^\w\u4e00-\u9fa5-]/g, '');
        
        // 检查ID是否已存在，避免重复添加
        if (!existingIds.has(id)) {
          existingIds.add(id);
          headings.push({
            level,
            text: headerText,
            id
          });
        }
      }
    }
  } else if (content && headings.length === 0) {
    // 处理纯Markdown文本
    const lines = content.split('\n');
    const headingRegex = /^(#{1,6})\s+(.+)$/;
    
    lines.forEach(line => {
      const match = line.match(headingRegex);
      if (match) {
        const level = match[1].length;
        let text = match[2].trim();
        // 移除Markdown中的**加粗标记和其他常见标记
        text = text.replace(/\*\*(.*?)\*\*/g, '$1')  // 移除加粗标记 **text**
                   .replace(/\*(.*?)\*/g, '$1')      // 移除斜体标记 *text*
                   .replace(/__(.*?)__/g, '$1')      // 移除加粗标记 __text__
                   .replace(/_(.*?)_/g, '$1')        // 移除斜体标记 _text_
                   .trim();
        const id = text.toLowerCase().replace(/\s+/g, '-').replace(/[^\w\u4e00-\u9fa5-]/g, '');
        
        // 检查ID是否已存在，避免重复添加
        if (!existingIds.has(id)) {
          existingIds.add(id);
          headings.push({
            level,
            text,
            id
          });
        }
      }
    });
  }
  
  return headings;
};

const validateLikeAndCollect = async () => {
  if (!postDetail.value) return;
  try {
    // 验证点赞
    const likeRes = await checkLike(postDetail.value.topic.id);
    if (likeRes.data.code === 200) {
      isLiked.value = !!likeRes.data.data;
    }
    // 验证收藏
    const collectRes = await checkCollect(postDetail.value.topic.id);
    if (collectRes.data.code === 200) {
      isCollected.value = !!collectRes.data.data;
    }
  } catch (error) {
    // 可选：错误处理
  }
};

const recordView = async () => {
  try {
    await recordPostView(route.params.id);
    console.log('已记录文章浏览');
  } catch (error) {
    console.error('记录文章浏览失败:', error);
  }
};

const fetchPostDetail = async () => {
  try {
    loading.value = true;
    const res = await getPostDetail(route.params.id);
    if (res.data.code === 200) {
      const data = res.data.data;
      
      // 先提取目录，再设置文章数据
      if (data && data.topic && data.topic.content) {
        tocItems.value = extractToc(data.topic.content);
      }
      
      // 设置文章数据
      postDetail.value = data;
      
      // 获取关注状态
      await checkFollowStatus();
      // 校验点赞和收藏
      await validateLikeAndCollect();
      // 记录文章浏览
      await recordView();
    }
  } catch (error) {
    console.error('获取帖子详情失败:', error);
  } finally {
    loading.value = false;
  }
};

const checkFollowStatus = async () => {
  if (!postDetail.value) return;
  
  try {
    const res = await checkFollow(postDetail.value.user.id);
    if (res.data.code === 200) {
      isFollowing.value = res.data.data.hasFollow;
    }
  } catch (error) {
    console.error('获取关注状态失败:', error);
  }
};

const handleFollow = async () => {
  if (!postDetail.value || followLoading.value) return;
  
  try {
    followLoading.value = true;
    const followAction = isFollowing.value ? unfollowUser : followUser;
    const res = await followAction(postDetail.value.user.id);
    
    if (res.data.code === 200) {
      isFollowing.value = !isFollowing.value;
      ElMessage.success(res.data.message || (isFollowing.value ? '关注成功' : '已取消关注'));
      
      // 更新关注数
      if (postDetail.value) {
        if (isFollowing.value) {
          postDetail.value.user.followerCount++;
        } else {
          postDetail.value.user.followerCount--;
        }
      }
    } else {
      ElMessage.error(res.data.message || '操作失败');
    }
  } catch (error) {
    console.error('关注操作失败:', error);
    ElMessage.error(error.response?.data?.message || '操作失败，请重试');
  } finally {
    followLoading.value = false;
  }
};

const fetchComments = async () => {
  try {
    const res = await getPostComment(route.params.id);
    if (res.data.code === 200) {
      comments.value = res.data.data;
    }
  } catch (error) {
    console.error('获取评论失败:', error);
  }
};

const fetchRecommendPosts = async () => {
  try {
    recommendLoading.value = true;
    const res = await getArticleRecommend(recommendPage.value, 8);
    if (res.data && res.data.code === 200) {
      recommendPosts.value = res.data.data;
    }
  } catch (error) {
    console.error('获取推荐文章失败:', error);
  } finally {
    setTimeout(() => {
      recommendLoading.value = false;
    }, 1000);
  }
};

const loadNextRecommend = () => {
  recommendPage.value++;
  fetchRecommendPosts();
};

const toggleComments = async () => {
  showComments.value = !showComments.value;
  if (showComments.value && comments.value.length === 0) {
    await fetchComments();
  }
};

const formatDate = (dateString) => {
  const date = new Date(dateString);
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

const handleReply = (comment) => {
  replyTo.value = comment;
  replyContent.value = '';
};

const submitComment = async () => {
  if (!replyContent.value.trim()) {
    ElMessage.warning('评论内容不能为空');
    return;
  }

  try {
    submitting.value = true;
    const params = {
      userName: userStore.user.username,
      content: replyContent.value.trim(),
      topic_id: route.params.id,
      parentId: replyTo.value ? replyTo.value.id : null,
      replyToUserId: replyTo.value ? replyTo.value.userId : userStore.user.id
    };

    const res = await addPostComment(params);
    if (res.data.code === 200) {
      ElMessage.success('评论成功');
      replyContent.value = '';
      replyTo.value = null;
      // 重新获取评论列表
      await fetchComments();
      // 更新帖子评论数
      if (postDetail.value) {
        postDetail.value.topic.comments += 1;
      }
    }
  } catch (error) {
    console.error('发表评论失败:', error);
    ElMessage.error('发表评论失败，请重试');
  } finally {
    submitting.value = false;
  }
};

const goToPostDetail = (postId) => {
  // 如果是当前文章，不进行跳转
  if (postId === route.params.id) return;
  
  // 跳转到新文章
  router.push(`/post/${postId}`);
  // 不需要强制刷新页面，让路由钩子自己处理
};

// 修改渲染内容的函数，为标题添加ID并处理图片
const renderContentWithIds = (content) => {
  if (!content) return '';
  try {
    // 先替换emoji代码
    let processedContent = content;
    
    // 处理HTML标题中的Markdown标记
    processedContent = processedContent.replace(/<h(\d)>#\s+([^<]+)<\/h\d>/g, (match, level, title) => {
      const id = title.toLowerCase().replace(/\s+/g, '-').replace(/[^\w\u4e00-\u9fa5-]/g, '');
      return `<h${level} id="${id}">${title}</h${level}>`;
    });
    
    // 检查是否匹配一键成文的特征（标题格式和emoji符号）
    const looksLikeAutoGenerated = /(<p>#{1,6} [^<]+<\/p>|<p>[:🌱🌞💧☀️🌡️🌸🆘][\w]+:? [^<]+<\/p>)/i.test(content);
    
    // 检查内容是否看起来像含有Markdown标记但被<p>标签包裹
    const containsMarkdownInP = /<p>[\s\S]*?[#*`\[\]()-_][\s\S]*?<\/p>/i.test(content);
    
    // 如果匹配一键成文特征或内容被<p>标签包裹且包含Markdown标记，先提取出纯文本再渲染
    if (looksLikeAutoGenerated || containsMarkdownInP) {
      try {
        // 提取和处理图片
        const imgRegex = /<p[^>]*>\s*<img[^>]*src="([^"]+)"[^>]*\/>\s*<\/p>/g;
        let imgMatches = [];
        let imgMatch;
        while ((imgMatch = imgRegex.exec(content)) !== null) {
          imgMatches.push({
            full: imgMatch[0],
            src: imgMatch[1],
            style: imgMatch[0].match(/style="([^"]+)"/) ? imgMatch[0].match(/style="([^"]+)"/)[1] : ''
          });
        }
        
        // 处理HTML标题中的Markdown标记，先提取出来
        let htmlHeadings = [];
        const htmlHeadingRegex = /<h(\d)>#\s+([^<]+)<\/h\d>/g;
        let htmlHeadingMatch;
        
        while ((htmlHeadingMatch = htmlHeadingRegex.exec(content)) !== null) {
          htmlHeadings.push({
            level: htmlHeadingMatch[1],
            text: htmlHeadingMatch[2],
            id: htmlHeadingMatch[2].toLowerCase().replace(/\s+/g, '-').replace(/[^\w\u4e00-\u9fa5-]/g, '')
          });
        }
        
        // 移除所有HTML标签，保留文本内容，并替换<br>为换行符
        let textContent = content
          .replace(/<br\s*\/?>/gi, '\n') // 先处理<br>标签为换行符
          .replace(/<p[^>]*>/gi, '') // 移除开始<p>标签，支持带样式
          .replace(/<\/p>/gi, '\n\n') // 将结束</p>标签替换为两个换行符
          .replace(/<h\d>#\s+([^<]+)<\/h\d>/gi, '## $1\n\n') // 将HTML标题转换为Markdown
          .replace(/<\/?[^>]+(>|$)/g, '') // 移除其他所有HTML标签
          .trim();
        
        // 替换emoji代码
        textContent = replaceEmojis(textContent);
        
        // 创建临时目录变量，不直接修改响应式状态
        // 注意：移除这里对tocItems.value的直接赋值，避免无限递归
        // tocItems.value = extractToc(content);
        const tempTocItems = extractToc(content);
        
        // 自定义渲染规则，为标题添加ID
        const customMd = new MarkdownIt({
          html: true,
          breaks: true,
          linkify: true,
          typographer: true
        });
        
        // 添加自定义渲染规则 - 为标题添加ID属性
        customMd.renderer.rules.heading_open = (tokens, idx) => {
          const token = tokens[idx];
          const nextToken = tokens[idx + 1];
          if (nextToken && nextToken.type === 'inline' && nextToken.content) {
            const id = nextToken.content.toLowerCase()
                                         .replace(/\s+/g, '-')
                                         .replace(/[^\w\u4e00-\u9fa5-]/g, '');
            return `<${token.tag} id="${id}">`;
          }
          return `<${token.tag}>`;
        };
        
        // 为emoji标题添加ID
        textContent = textContent.replace(/(^|\n)(🌱|🌞|💧|☀️|🌡️|🌸|🆘)\s+([^\n]+)/g, (match, p1, emoji, title) => {
          const id = title.toLowerCase().replace(/\s+/g, '-').replace(/[^\w\u4e00-\u9fa5-]/g, '');
          return `${p1}## <span id="${id}">${emoji} ${title}</span>`;
        });
        
        customMd.renderer.rules.paragraph_open = () => '<p class="markdown-paragraph">';
        
        // 自定义图片渲染
        customMd.renderer.rules.image = (tokens, idx) => {
          const token = tokens[idx];
          const src = token.attrs.find(attr => attr[0] === 'src')[1];
          const alt = token.content || '';
          return `<div class="article-image"><img src="${src}" alt="${alt}" class="full-width-image" /></div>`;
        };
        
        // 渲染Markdown
        let renderedContent = customMd.render(textContent);
        
        // 将提取的图片插回渲染后的内容
        imgMatches.forEach(img => {
          if (img.style) {
            renderedContent = `<div class="article-image"><img src="${img.src}" alt="" class="full-width-image" style="${img.style}" /></div>` + renderedContent;
          } else {
            renderedContent = `<div class="article-image"><img src="${img.src}" alt="" class="full-width-image" /></div>` + renderedContent;
          }
        });
        
        // 将HTML标题插回渲染后的内容（如果有）
        if (htmlHeadings.length > 0) {
          // 避免合并时出现重复
          const existingIds = new Set(tempTocItems.map(item => item.id));
          const uniqueHeadings = htmlHeadings.filter(heading => !existingIds.has(heading.id));
          
          if (uniqueHeadings.length > 0) {
            // 不直接修改tocItems.value
            // tocItems.value = uniqueHeadings.concat(tocItems.value);
          }
        }
        
        return renderedContent;
      } catch (error) {
        // 如果特殊处理出错，回退到普通HTML渲染
        console.error('Markdown特殊处理失败，回退到普通渲染', error);
        return content;
      }
    }
    
    // 检查内容是否包含HTML标签
    const containsHTML = /<[a-z][\s\S]*>/i.test(content);
    
    // 如果包含HTML标签，进行一些处理后返回
    if (containsHTML) {
      // 处理HTML标题中的Markdown标记
      let processedHtml = processedContent.replace(/<h(\d)>#\s+([^<]+)<\/h\d>/g, (match, level, title) => {
        const id = title.toLowerCase().replace(/\s+/g, '-').replace(/[^\w\u4e00-\u9fa5-]/g, '');
        return `<h${level} id="${id}">${title}</h${level}>`;
      });
      
      // 不直接修改tocItems.value
      // tocItems.value = extractToc(processedHtml);
      
      // 处理图片标签，给图片添加合适的样式
      processedHtml = processedHtml.replace(/<p[^>]*>\s*<img([^>]*)\/>\s*<\/p>/g, (match, imgAttrs) => {
        const style = match.match(/style="([^"]+)"/) ? match.match(/style="([^"]+)"/)[1] : '';
        if (style) {
          return `<div class="article-image"><img${imgAttrs} class="full-width-image" style="${style}" /></div>`;
        }
        return `<div class="article-image"><img${imgAttrs} class="full-width-image" /></div>`;
      });
      
      // 为emoji标题添加ID并转换为标题格式
      processedHtml = processedHtml.replace(/<p>(#{1,6}|🌱|🌞|💧|☀️|🌡️|🌸|🆘)\s+([^<]+)<\/p>/g, (match, prefix, title) => {
        const id = title.toLowerCase().replace(/\s+/g, '-').replace(/[^\w\u4e00-\u9fa5-]/g, '');
        return `<h2 id="${id}" class="emoji-heading">${prefix} ${title}</h2>`;
      });
      
      return processedHtml;
    }
    
    // 否则作为Markdown渲染
    processedContent = replaceEmojis(content);
    
    // 不直接修改tocItems.value
    // tocItems.value = extractToc(processedContent);
    
    // 自定义渲染规则，为标题添加ID
    const customMd = new MarkdownIt({
      html: true,
      breaks: true,
      linkify: true,
      typographer: true
    });
    
    // 添加自定义渲染规则 - 为标题添加ID属性
    customMd.renderer.rules.heading_open = (tokens, idx) => {
      const token = tokens[idx];
      const nextToken = tokens[idx + 1];
      if (nextToken && nextToken.type === 'inline' && nextToken.content) {
        const id = nextToken.content.toLowerCase()
                                   .replace(/\s+/g, '-')
                                   .replace(/[^\w\u4e00-\u9fa5-]/g, '');
        return `<${token.tag} id="${id}">`;
      }
      return `<${token.tag}>`;
    };
    
    customMd.renderer.rules.paragraph_open = () => '<p class="markdown-paragraph">';
    
    // 自定义图片渲染
    customMd.renderer.rules.image = (tokens, idx) => {
      const token = tokens[idx];
      const src = token.attrs.find(attr => attr[0] === 'src')[1];
      const alt = token.content || '';
      return `<div class="article-image"><img src="${src}" alt="${alt}" class="full-width-image" /></div>`;
    };
    
    return customMd.render(processedContent);
  } catch (error) {
    console.error('内容渲染错误:', error);
    return content;
  }
};

// 添加标签点击处理函数
const handleTagClick = (tag) => {
  router.push({
    path: '/',
    query: { tab: tag.name }
  });
};

const handleLike = async () => {
  if (!postDetail.value || likeLoading.value) return;
  
  try {
    likeLoading.value = true;
    const res = await likePost(postDetail.value.topic.id);
    if (res.data.code === 200) {
      isLiked.value = res.data.data.isLiked;
      postDetail.value.topic.likes = res.data.data.likes;
      ElMessage.success(res.data.message);
    }
  } catch (error) {
    console.error('点赞操作失败:', error);
    ElMessage.error('操作失败，请重试');
  } finally {
    likeLoading.value = false;
  }
};

const handleCollect = async () => {
  if (!postDetail.value || collectLoading.value) return;
  
  try {
    collectLoading.value = true;
    const params = {
      userName: userStore.user.username,
      topicId: postDetail.value.topic.id
    };
    const res = await collectPost(params);
    if (res.data.code === 200) {
      isCollected.value = res.data.data.isCollected;
      postDetail.value.topic.collects = res.data.data.collect;
      ElMessage.success(res.data.message);
    }
  } catch (error) {
    console.error('收藏操作失败:', error);
    ElMessage.error('操作失败，请重试');
  } finally {
    collectLoading.value = false;
  }
};

// 滚动到目录项
const scrollToHeading = (id) => {
  const element = document.getElementById(id);
  if (element) {
    // 添加一点偏移量，确保标题不会被导航栏遮挡
    const offset = 100; // 增加顶部预留的像素，确保标题完全可见
    const elementPosition = element.getBoundingClientRect().top;
    const offsetPosition = elementPosition + window.scrollY - offset;
    
    // 平滑滚动到位置
    window.scrollTo({
      top: offsetPosition,
      behavior: 'smooth'
    });
    
    // 为被点击的标题添加高亮效果
    element.classList.add('highlight-heading');
    setTimeout(() => {
      element.classList.remove('highlight-heading');
    }, 2000); // 2秒后移除高亮效果
  }
};

// 切换目录显示
const toggleToc = () => {
  showToc.value = !showToc.value;
};

onMounted(() => {
  fetchPostDetail();
  fetchRecommendPosts();
});

// 监听路由参数变化，当文章id变化时重新获取数据
watch(() => route.params.id, (newId, oldId) => {
  if (newId !== oldId) {
    fetchPostDetail();
    fetchRecommendPosts();
    // 重置评论状态
    comments.value = [];
    showComments.value = false;
    replyTo.value = null;
    replyContent.value = '';
  }
});
</script>

<template>
  <div v-if="loading" class="loading-container">
    <el-skeleton :rows="10" animated />
  </div>
  
  <div v-else-if="postDetail" class="detail-container">
    <!-- 左侧目录 -->
    <div class="toc-container" v-if="tocItems.length > 0">
      <div class="toc-header">
        <span class="toc-title">目录</span>
        <el-button type="text" @click="toggleToc" class="toc-toggle">
          <i :class="['fas', showToc ? 'fa-chevron-up' : 'fa-chevron-down']"></i>
        </el-button>
      </div>
      <div class="toc-content" v-show="showToc">
        <ul class="toc-list">
          <li 
            v-for="(item, index) in tocItems" 
            :key="index" 
            class="toc-item"
            :style="{ 'padding-left': (item.level - 1) * 12 + 'px' }"
            @click="scrollToHeading(item.id)"
          >
            <span class="toc-item-text">{{ item.text }}</span>
          </li>
        </ul>
      </div>
    </div>
    <div class="toc-spacer" v-else></div>
    
    <!-- 左侧主要内容 -->
    <div class="post-detail">
      <!-- 文章内容 -->
      <div class="post-content">
        <h1 class="post-title">{{ postDetail.topic.title }}</h1>
        <div class="post-header">
          <div class="post-meta">
            <div class="meta-left">
              <span class="post-time">修改于 {{ formatDate(postDetail.topic.createTime) }}</span>
              <span class="meta-divider">·</span>
              <span>发布者：{{ postDetail.user.username }}</span>
              <span class="meta-divider">·</span>
              <span>查看：{{ postDetail.topic.view }}</span>
            </div>

            <div class="tags">
              <el-tag
                v-for="tag in postDetail.tags"
                :key="tag.id"
                size="small"
                effect="plain"
                class="tag"
                @click="handleTagClick(tag)"
                style="margin-right: 5px"
              >
                #{{ tag.name }}
              </el-tag>
            </div>

            <div class="post-actions">
              <el-button 
                :type="isLiked ? 'primary' : 'default'"
                :loading="likeLoading"
                @click="handleLike"
              >
                <i :class="['fas', isLiked ? 'fa-thumbs-up' : 'fa-thumbs-up']" style="margin-right: 5px;"></i>
                {{ isLiked ? '已点赞' : '点赞' }} ({{ postDetail.topic.likes || 0 }})
              </el-button>
              <el-button 
                :type="isCollected ? 'primary' : 'default'"
                :loading="collectLoading"
                @click="handleCollect"
              >
                <i :class="['fas', isCollected ? 'fa-star' : 'fa-star']" style="margin-right: 5px;"></i>
                {{ isCollected ? '已收藏' : '收藏' }} ({{ postDetail.topic.collects || 0 }})
              </el-button>
            </div>
          </div>
        </div>
        <div class="content-text markdown-body" v-html="renderContentWithIds(postDetail.topic.content)"></div>
      </div>

      <!-- 评论区 -->
      <div class="comments-section">
        <div class="comments-header" @click="toggleComments">
          <h3>评论 ({{ postDetail.topic.comments }})</h3>
          <i :class="['fas', showComments ? 'fa-chevron-down' : 'fa-chevron-up']"></i>
        </div>

        <div v-show="showComments" class="comments-container">
          <!-- 主评论输入框，只在不是回复状态时显示 -->
          <div v-if="!replyTo" class="comment-input">
            <el-input
              v-model="replyContent"
              type="textarea"
              :rows="3"
              placeholder="写下你的评论..."
            />
            <div class="comment-actions">
              <el-button 
                type="primary" 
                class="submit-comment"
                :loading="submitting"
                :disabled="!replyContent.trim()"
                @click="submitComment"
              >
                发表评论
              </el-button>
            </div>
          </div>

          <div class="comments-list">
            <div v-for="comment in comments" :key="comment.id" class="comment-item">
              <div class="comment-main">
                <div class="comment-user">
                  <div class="comment-user-avatar">
                    <img :src="comment.userAvatar" :alt="comment.authorName" class="avatar-img" />
                  </div>
                  <div class="comment-user-info">
                    <div class="comment-username">{{ comment.authorName }}</div>
                    <div class="comment-time">{{ formatDate(comment.createTime) }}</div>
                  </div>
                </div>
                <div class="comment-content">{{ comment.content }}</div>
                <div class="comment-footer">
                  <span class="comment-reply" @click="handleReply(comment)">
                    <i class="far fa-comment"></i> 回复
                  </span>
                </div>
              </div>

              <!-- 回复输入框，只在当前评论被选中回复时显示 -->
              <div v-if="replyTo && replyTo.id === comment.id" class="reply-input">
                <el-input
                  v-model="replyContent"
                  type="textarea"
                  :rows="3"
                  :placeholder="`回复 @${replyTo.authorName}`"
                />
                <div class="comment-actions">
                  <span class="reply-cancel" @click="replyTo = null">
                    取消回复
                  </span>
                  <el-button 
                    type="primary" 
                    class="submit-comment"
                    :loading="submitting"
                    :disabled="!replyContent.trim()"
                    @click="submitComment"
                  >
                    发表回复
                  </el-button>
                </div>
              </div>

              <!-- 子评论列表 -->
              <div v-if="comment.children && comment.children.length > 0" class="comment-replies">
                <div v-for="reply in comment.children" :key="reply.id" class="reply-item">
                  <div class="reply-user">
                    <div class="reply-user-avatar">
                      <img :src="reply.userAvatar" :alt="reply.authorName" class="avatar-img" />
                    </div>
                    <div class="reply-user-info">
                      <span class="reply-username">{{ reply.authorName }}</span>
                      <span class="reply-to">回复 @{{ reply.replyToUsername }}</span>
                    </div>
                  </div>
                  <div class="reply-content">{{ reply.content }}</div>
                  <div class="reply-footer">
                    <span class="reply-time">{{ formatDate(reply.createTime) }}</span>
                    <span class="reply-action" @click="handleReply(reply)">
                      <i class="far fa-comment"></i> 回复
                    </span>
                  </div>
                </div>

                <!-- 回复子评论的输入框 -->
                <div v-if="replyTo && comment.children.some(reply => reply.id === replyTo.id)" class="reply-input nested">
                  <el-input
                    v-model="replyContent"
                    type="textarea"
                    :rows="3"
                    :placeholder="`回复 @${replyTo.authorName}`"
                  />
                  <div class="comment-actions">
                    <span class="reply-cancel" @click="replyTo = null">
                      取消回复
                    </span>
                    <el-button 
                      type="primary" 
                      class="submit-comment"
                      :loading="submitting"
                      :disabled="!replyContent.trim()"
                      @click="submitComment"
                    >
                      发表回复
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧作者信息 -->
    <div class="author-sidebar">
      <div class="author-card">
        <div class="author-header">
          <img :src="postDetail.user.avatar" :alt="postDetail.user.username" class="author-avatar">
          <div class="author-name">{{ postDetail.user.username }}</div>
          <div class="author-alias">@{{ postDetail.user.alias }}</div>
        </div>
        
        <div class="author-stats">
          <div class="stat-group">
            <div class="stat-value">{{ postDetail.user.topicCount }}</div>
            <div class="stat-label">文章</div>
          </div>
          <div class="stat-group">
            <div class="stat-value">{{ postDetail.user.followerCount }}</div>
            <div class="stat-label">粉丝</div>
          </div>
          <div class="stat-group">
            <div class="stat-value">{{ postDetail.user.followCount || 0 }}</div>
            <div class="stat-label">关注</div>
          </div>
        </div>

        <div class="author-actions">
          <el-button 
            type="primary" 
            class="follow-btn" 
            :plain="!isFollowing"
            :loading="followLoading"
            @click="handleFollow"
          >
            <i :class="['fas', isFollowing ? 'fa-check' : 'fa-plus']"></i>
            {{ isFollowing ? '已关注' : '关注作者' }}
          </el-button>
          <el-button type="primary" class="message-btn" plain 
          @click="()=>{
              ElMessage({
                message: '功能开发中，敬请期待！',
                type: 'info',
                duration: 2000
              });
            }">
            <i class="far fa-envelope"></i> 发送消息
          </el-button>
        </div>
      </div>

      <!-- 推荐文章列表 -->
      <div class="recommend-posts">
        <div class="recommend-header">
          <h3 class="recommend-title">
            <span class="emoji">🧐</span> 猜你想看
          </h3>
          <span class="refresh-btn" @click="loadNextRecommend" :class="{ 'loading': recommendLoading }">
            <i class="fas fa-sync-alt"></i> 换一批
          </span>
        </div>

        <div v-if="recommendLoading" class="recommend-loading">
          <div class="loading-spinner"></div>
          <span>加载中...</span>
        </div>
        
        <div v-else-if="recommendPosts.length === 0" class="no-recommend">
          暂无推荐文章
        </div>
        
        <div v-else class="recommend-list">
          <div 
            v-for="(post, index) in recommendPosts" 
            :key="post.id" 
            class="recommend-item"
            @click="goToPostDetail(post.id)"
          >
            <span class="post-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="recommend-post-title" :title="post.title">
              {{ post.title }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.markdown-body {
  overflow-wrap: break-word;
}

.markdown-body .markdown-paragraph {
  margin: 1em 0;
  line-height: 1.8;
}

.detail-container {
  display: grid;
  grid-template-columns: 220px 1fr 280px;
  gap: 20px;
  max-width: 1500px;
  margin: 0 auto;
}

.post-detail {
  background-color: var(--el-bg-color-overlay);
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px var(--el-box-shadow);
  height: fit-content;
  min-height: min-content;
}

.loading-container {
  padding: 20px;
}

.post-header {
  margin-bottom: 24px;
}

.post-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-light);
}

.meta-left {
  display: flex;
  align-items: center;
  gap: 4px;
}

.meta-divider {
  margin: 0 4px;
}

.post-title {
  font-size: 40px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 16px;
  text-align: center;
}

.post-content {
  margin-bottom: 20px;
}

.content-text {
  font-size: 16px;
  line-height: 1.8;
  color: var(--el-text-color-primary);
  overflow-wrap: break-word;
}

.content-text p {
  margin: 1em 0;
}

.content-text span[style] {
  display: inline !important;
}

/* 保持颜色样式 */
.content-text [style*="color"] {
  color: inherit;
}

[data-theme="dark"] .content-text [style*="color: rgb"] {
  opacity: 0.9;
}

/* 添加 Markdown 样式 */
.markdown-body :deep(h1) {
  font-size: 2em;
  margin: 0.67em 0;
  padding-bottom: 0.3em;
  border-bottom: 1px solid var(--el-border-color-light);
}

.markdown-body :deep(h2) {
  font-size: 1.5em;
  margin: 0.83em 0;
  padding-bottom: 0.3em;
  border-bottom: 1px solid var(--el-border-color-light);
}

.markdown-body :deep(h3) {
  font-size: 1.17em;
  margin: 1em 0;
}

.markdown-body :deep(h4) {
  font-size: 1em;
  margin: 1.33em 0;
}

.markdown-body :deep(h5) {
  font-size: 0.83em;
  margin: 1.67em 0;
}

.markdown-body :deep(h6) {
  font-size: 0.67em;
  margin: 2.33em 0;
}

.markdown-body :deep(p) {
  margin: 1em 0;
  line-height: 1.8;
}

.markdown-body :deep(blockquote) {
  margin: 1em 0;
  padding: 0 1em;
  color: var(--el-text-color-secondary);
  border-left: 0.25em solid var(--el-border-color);
}

.markdown-body :deep(ul), .markdown-body :deep(ol) {
  margin: 1em 0;
  padding-left: 2em;
}

.markdown-body :deep(li) {
  margin: 0.5em 0;
}

.markdown-body :deep(code) {
  padding: 0.2em 0.4em;
  margin: 0;
  font-size: 85%;
  background-color: var(--el-fill-color-light);
  border-radius: 3px;
  font-family: Menlo, Monaco, Consolas, "Courier New", monospace;
}

.markdown-body :deep(pre) {
  margin: 1em 0;
  padding: 16px;
  overflow: auto;
  background-color: var(--el-fill-color-light);
  border-radius: 6px;
  
  code {
    padding: 0;
    background-color: transparent;
  }
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 1em 0;
}

.markdown-body :deep(th), .markdown-body :deep(td) {
  padding: 6px 13px;
  border: 1px solid var(--el-border-color);
}

.markdown-body :deep(th) {
  background-color: var(--el-fill-color-light);
}

.markdown-body :deep(img) {
  max-width: 100%;
  height: auto;
  margin: 1em 0;
  border-radius: 4px;
}

.markdown-body :deep(hr) {
  height: 1px;
  margin: 16px 0;
  background-color: var(--el-border-color);
  border: none;
}

.markdown-body :deep(a) {
  color: var(--el-color-primary);
  text-decoration: none;
  
  &:hover {
    text-decoration: underline;
  }
}
.tag {
  color: var(--el-color-primary);
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
}

.tag:hover {
  opacity: 0.8;
}

.post-stats {
  display: flex;
  gap: 20px;
  padding: 15px 0;
  border-top: 1px solid var(--el-border-color);
  border-bottom: 1px solid var(--el-border-color);
  margin-bottom: 20px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
}

.comments-section {
  margin-top: 30px;
  background: var(--comment-bg);
  border-radius: 8px;
}

.comments-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--comment-divider);
}

.comments-header h3 {
  font-size: 16px;
  font-weight: 500;
  color: var(--comment-text-primary);
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.comments-container {
  padding: 0 20px;
}

.comment-input {
  padding: 16px 0;
  border-bottom: 1px solid var(--comment-divider);

  :deep(.el-textarea__inner) {
    background-color: var(--comment-input-bg) !important;
    border: none;
    border-radius: 8px;
    padding: 12px 16px;
    font-size: 14px;
    resize: none;
    min-height: 80px !important;
    color: var(--comment-text-primary);

    &:focus {
      background-color: var(--comment-input-hover) !important;
    }

    &::placeholder {
      color: var(--comment-text-secondary);
    }
  }
}

.comment-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
  gap: 12px;
}

.submit-comment {
  padding: 8px 16px;
  font-size: 14px;
  border-radius: 4px;
}

.comment-item {
  padding: 16px 16px;
  border-bottom: 1px solid var(--comment-divider);
  transition: background-color 0.3s ease;
}

.comment-item:hover {
  background-color: var(--comment-hover-bg);
}

.comment-user {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.comment-user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background-color: var(--comment-avatar-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--comment-text-regular);
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

.comment-user-info {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.comment-username {
  font-size: 14px;
  color: var(--comment-text-primary);
  font-weight: 500;
}

.comment-time {
  font-size: 12px;
  color: var(--comment-text-secondary);
}

.comment-content {
  color: var(--comment-text-primary);
  line-height: 1.6;
  font-size: 14px;
  padding-left: 48px;
  margin: 4px 0 8px;
}

.comment-footer {
  padding-left: 48px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.comment-actions-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.comment-reply,
.comment-like {
  font-size: 13px;
  color: var(--comment-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.comment-reply:hover,
.comment-like:hover,
.comment-like.active {
  color: var(--el-color-primary);
  background-color: var(--comment-action-hover);
}

.comment-replies {
  margin: 12px 0 0 48px;
  padding: 12px 16px;
  background: var(--comment-reply-bg);
  border-radius: 4px;
}

.reply-item {
  padding: 12px 0;
  border-bottom: 1px solid var(--comment-divider);
}

.reply-item:last-child {
  border-bottom: none;
}

.reply-user {
  display: flex;
  align-items: center;
  gap: 8px;
}

.reply-user-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background-color: var(--comment-avatar-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--comment-text-regular);
  font-size: 12px;
}

.reply-user-info {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.reply-username {
  font-size: 13px;
  color: var(--comment-text-primary);
}

.reply-to {
  color: var(--el-color-primary);
  font-size: 13px;
  margin-left: 4px;
}

.reply-content {
  color: var(--comment-text-primary);
  line-height: 1.6;
  font-size: 13px;
  margin: 4px 0 4px 32px;
}

.reply-footer {
  margin-left: 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.reply-time {
  font-size: 12px;
  color: var(--comment-text-secondary);
}

.reply-action {
  font-size: 12px;
  color: var(--comment-text-secondary);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.reply-action:hover {
  color: var(--el-color-primary);
  background-color: var(--comment-action-hover);
}

.reply-input {
  margin-top: 12px;
  padding: 12px;
  background-color: var(--comment-input-bg);
  border-radius: 4px;

  :deep(.el-textarea__inner) {
    background-color: var(--comment-bg) !important;
    border: none;
    border-radius: 4px;
    padding: 8px 12px;
    font-size: 13px;
    resize: none;
    color: var(--comment-text-primary);

    &:focus {
      background-color: var(--comment-input-hover) !important;
    }

    &::placeholder {
      color: var(--comment-text-secondary);
    }
  }

  .comment-actions {
    margin-top: 8px;
  }
}

.reply-input.nested {
  margin: 16px 0 0 48px;
}

.reply-cancel {
  font-size: 13px;
  color: var(--comment-text-secondary);
  cursor: pointer;
  margin-right: 12px;
  transition: color 0.2s ease;

  &:hover {
    color: var(--el-color-danger);
  }
}

.author-sidebar {
  position: sticky;
  top: 80px;
  height: fit-content;
}

.author-card {
  background-color: var(--el-bg-color-overlay);
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px var(--el-box-shadow);
}

.author-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 10px;
}

.author-avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  object-fit: cover;
  margin-bottom: 12px;
  border: 3px solid var(--el-color-primary-light-8);
}

.author-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
}

.author-alias {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.author-stats {
  display: flex;
  justify-content: space-around;
  padding: 10px 0;
  border-top: 1px solid var(--el-border-color-light);
  border-bottom: 1px solid var(--el-border-color-light);
  margin-bottom: 20px;
}

.stat-group {
  text-align: center;
}

.stat-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.author-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.author-actions .el-button {
  width: 100%;
  border: 1px solid var(--el-border-color-light);
  transition: all 0.3s ease;
}

.author-actions .el-button:hover {
  color: var(--el-color-primary) !important;
  border-color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-9) !important;
}

.follow-btn {
  background-color: var(--el-bg-color) !important;
}

.follow-btn:not(.is-plain) {
  color: #fff !important;
  background-color: var(--el-color-primary) !important;
}

.follow-btn:hover {
  opacity: 0.9;
}

.message-btn {
  margin-left: 0;
  background-color: var(--el-bg-color) !important;
}

.follow-btn i, .message-btn i {
  margin-right: 4px;
}

@media (max-width: 1200px) {
  .detail-container {
    grid-template-columns: 1fr;
  }

  .toc-container,
  .author-sidebar {
    display: none;
  }
}

.recommend-posts {
  background-color: var(--el-bg-color-overlay);
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px var(--el-box-shadow);
  margin-top: 10px;
}

.recommend-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.recommend-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 10px;
  margin-top: 0px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.emoji {
  font-size: 20px;
}

.recommend-list {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.recommend-item {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.recommend-item:hover {
  background-color: var(--el-color-primary-light-9);
  transform: translateX(4px);
}

.post-index {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  font-family: Monaco, monospace;
}

.recommend-post-title {
  font-size: 14px;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
 
  white-space: nowrap;
  flex: 1;
}

.post-actions {
  display: flex;
  /* gap: 10px; */
  margin: 10px 0;
}

.post-actions .el-button {
  background: transparent !important;
  border: none;
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: 5px;
  box-shadow: none;
  transition: color 0.2s;
}

.post-actions .el-button i {
  font-size: 16px;
  margin-right: 5px;
  color: var(--el-text-color-secondary);
  transition: color 0.2s;
}

/* 激活（已点赞/已收藏）状态，仅改变颜色 */
.post-actions .el-button--primary,
.post-actions .el-button.primary {
  background: transparent !important;
  color: var(--el-color-primary);
}

.post-actions .el-button--primary i,
.post-actions .el-button.primary i {
  color: var(--el-color-primary);
}

/* 悬浮时主色，但背景依然透明 */
.post-actions .el-button:hover,
.post-actions .el-button:focus {
  color: var(--el-color-primary);
  background: transparent !important;
}

.post-actions .el-button:hover i,
.post-actions .el-button:focus i {
  color: var(--el-color-primary);
}

.refresh-btn {
  font-size: 12px;
  color: var(--el-color-primary);
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  gap: 3px;
}

.refresh-btn:hover {
  color: var(--el-color-primary-light-3);
}

.refresh-btn.loading i {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.recommend-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--el-border-color-lighter);
  border-top-color: var(--el-color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 8px;
}

.no-recommend {
  text-align: center;
  padding: 20px 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

/* 目录样式 */
.toc-container {
  position: sticky;
  top: 80px;
  height: fit-content;
  background-color: var(--el-bg-color-page);
  border-radius: 10px;
  box-shadow: 0 2px 8px var(--el-box-shadow);
  padding: 15px;
  max-height: calc(100vh - 100px);
  display: flex;
  flex-direction: column;
}

.toc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--el-border-color-light);
  padding-bottom: 10px;
  margin-bottom: 10px;
}

.toc-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.toc-toggle {
  padding: 0;
  font-size: 16px;
}

.toc-content {
  overflow-y: auto;
  flex: 1;
}

.toc-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.toc-item {
  padding: 6px 0;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-text-color-secondary);
}

.toc-item:hover {
  color: var(--el-color-primary);
}

.toc-item-text {
  position: relative;
}

.toc-item-text::before {
  content: '';
  position: absolute;
  left: -10px;
  top: 50%;
  height: 5px;
  width: 5px;
  margin-top: -2.5px;
  background-color: var(--el-color-primary);
  border-radius: 50%;
  opacity: 0;
  transition: opacity 0.2s;
}

.toc-item:hover .toc-item-text::before {
  opacity: 1;
}

/* 标题高亮效果 */
:deep(.highlight-heading) {
  position: relative;
  background-color: rgba(var(--el-color-primary-rgb), 0.1);
  transition: background-color 0.5s;
}

:deep(.highlight-heading::before) {
  content: '';
  position: absolute;
  left: -15px;
  top: 0;
  height: 100%;
  width: 4px;
  background-color: var(--el-color-primary);
}

/* 图片样式 */
.article-image {
  margin: 20px 0;
  text-align: center;
}

.full-width-image {
  max-width: 100%;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  height: auto !important; /* 覆盖可能存在的固定高度 */
}

/* emoji标题样式 */
:deep(.emoji-heading) {
  font-size: 1.5em;
  margin: 0.83em 0;
  padding-bottom: 0.3em;
  border-bottom: 1px solid var(--el-border-color-light);
}
</style>
