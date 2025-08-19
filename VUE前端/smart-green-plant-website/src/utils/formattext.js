// 格式化文本
export const formatText = (result) => {
  if (!result) return ''
  
  // 预处理：统一换行符
  let html = result.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  
  // 处理标题（先处理，避免被列表处理干扰）
  html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>')
  html = html.replace(/^#### (.+)$/gm, '<h4>$1</h4>')
  html = html.replace(/^##### (.+)$/gm, '<h5>$1</h5>')
  html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>')
  html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>')
  
  // 处理加粗文本
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/__(.+?)__/g, '<strong>$1</strong>')
  
  // 处理斜体文本
  html = html.replace(/\*([^*]+?)\*/g, '<em>$1</em>')
  html = html.replace(/_([^_]+?)_/g, '<em>$1</em>')
  
  // 处理代码块
  html = html.replace(/```[\s\S]*?```/g, (match) => {
    const code = match.replace(/```/g, '').trim()
    return `<pre><code>${code}</code></pre>`
  })
  
  // 处理行内代码
  html = html.replace(/`([^`]+?)`/g, '<code>$1</code>')
  
  // 处理引用
  html = html.replace(/^>\s+(.+)$/gm, '<blockquote>$1</blockquote>')
  
  // 处理分隔线
  html = html.replace(/^---+$/gm, '<hr>')
  html = html.replace(/^\*\*\*+$/gm, '<hr>')
  
  // 处理链接
  html = html.replace(/\[(.+?)\]\((.+?)\)/g, '<a href="$2" target="_blank">$1</a>')
  
  // 按段落分割处理
  const paragraphs = html.split(/\n\s*\n/)
  
  html = paragraphs.map(paragraph => {
    paragraph = paragraph.trim()
    if (!paragraph) return ''
    
    // 如果已经是HTML标签，不需要处理
    if (paragraph.match(/^<(h[1-6]|blockquote|hr|pre)/)) {
      return paragraph
    }
    
    // 分析当前段落的行
    const lines = paragraph.split('\n').map(line => line.trim()).filter(line => line)
    
    if (lines.length === 0) return ''
    
    // 检查是否为列表段落
    const listPattern = /^(\d+\.\s+|\-\s+|\*\s+|\+\s+)/
    const isListParagraph = lines.some(line => listPattern.test(line))
    
    if (isListParagraph) {
      let result = ''
      let currentList = null
      let currentListType = null
      let listCounter = 1 // 用于跟踪有序列表的序号
      
      for (const line of lines) {
        const orderedMatch = line.match(/^(\d+)\.\s+(.+)$/)
        const unorderedMatch = line.match(/^[-*+]\s+(.+)$/)
        
        if (orderedMatch) {
          // 有序列表项
          const number = parseInt(orderedMatch[1])
          
          if (currentListType !== 'ol') {
            if (currentList) result += `</${currentListType}>`
            result += `<ol start="${number}">`
            currentList = 'ol'
            currentListType = 'ol'
            listCounter = number
          } else if (number !== listCounter) {
            // 如果序号不连续，重新开始一个列表
            result += `</ol><ol start="${number}">`
            listCounter = number
          }
          
          result += `<li value="${number}">${orderedMatch[2]}</li>`
          listCounter = number + 1
        } else if (unorderedMatch) {
          // 无序列表项
          if (currentListType !== 'ul') {
            if (currentList) result += `</${currentListType}>`
            result += '<ul>'
            currentList = 'ul'
            currentListType = 'ul'
          }
          result += `<li>${unorderedMatch[1]}</li>`
        } else {
          // 非列表行
          if (currentList) {
            result += `</${currentListType}>`
            currentList = null
            currentListType = null
          }
          result += `<p>${line}</p>`
        }
      }
      
      // 关闭最后的列表
      if (currentList) {
        result += `</${currentListType}>`
      }
      
      return result
    } else {
      // 普通段落
      return `<p>${paragraph.replace(/\n/g, '<br>')}</p>`
    }
  }).join('')
  
  // 清理多余的p标签嵌套
  html = html.replace(/<p>(<[hH][1-6]>.*?<\/[hH][1-6]>)<\/p>/g, '$1')
  html = html.replace(/<p>(<blockquote>.*?<\/blockquote>)<\/p>/g, '$1')
  html = html.replace(/<p>(<hr>)<\/p>/g, '$1')
  html = html.replace(/<p>(<pre>[\s\S]*?<\/pre>)<\/p>/g, '$1')
  
  // 处理连续的相同类型列表
  html = html.replace(/<\/ul>\s*<ul>/g, '')
  html = html.replace(/<\/ol>\s*<ol>/g, '')
  
  // 最终清理多余空格
  html = html.replace(/>\s+</g, '><')
  html = html.trim()
  
  return html
}