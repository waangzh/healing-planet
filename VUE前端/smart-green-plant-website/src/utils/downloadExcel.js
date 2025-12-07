export const downloadExcel = (base64Data, fileName) => {
  // 如果 data 字段包含 MIME 前缀，移除它
  const base64Content = base64Data.includes('base64,') ? base64Data.split('base64,')[1] : base64Data

  // 解码 base64 数据
  const byteCharacters = atob(base64Content)

  // 转换字节字符为二进制数据
  const byteArrays = []
  for (let offset = 0; offset < byteCharacters.length; offset += 1024) {
    const slice = byteCharacters.slice(offset, offset + 1024)
    const byteNumbers = new Array(slice.length)
    for (let i = 0; i < slice.length; i++) {
      byteNumbers[i] = slice.charCodeAt(i)
    }
    byteArrays.push(new Uint8Array(byteNumbers))
  }

  // 创建 Blob 对象
  const blob = new Blob(byteArrays, {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  })

  // 创建下载链接
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = fileName

  // 自动触发下载
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}