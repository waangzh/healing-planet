// 简单的markdown解析函数
function parseMarkdown(markdown) {
  if (!markdown) return '';
  
  let html = markdown
    // 处理代码块
    .replace(/```([\s\S]*?)```/g, '<pre><code>$1</code></pre>')
    // 处理行内代码
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    // 处理标题
    .replace(/#{1,6}\s+([^\n]+)/g, (match, content) => {
      const level = match.trim().split(' ')[0].length;
      return `<h${level}>${content}</h${level}>`;
    })
    // 处理粗体
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    // 处理斜体
    .replace(/\*([^*]+)\*/g, '<em>$1</em>')
    // 处理链接
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>')
    // 处理列表
    .replace(/^\s*-\s+([^\n]+)/gm, '<li>$1</li>')
    // 处理段落
    .replace(/([^\n]+)\n/g, '<p>$1</p>');

  return html;
}

module.exports = {
  parseMarkdown
}; 