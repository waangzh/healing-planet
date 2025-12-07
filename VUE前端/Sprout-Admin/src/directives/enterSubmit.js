// 全局回车提交指令
export const enterSubmit = {
  mounted(el, binding) {
    // 查找输入框元素
    const findInput = (element) => {
      if (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA') {
        return element
      }
      return element.querySelector('input') || element.querySelector('textarea')
    }

    const input = findInput(el)
    if (!input) return

    // 添加回车事件监听
    const handleEnter = (event) => {
      if (event.key === 'Enter' && !event.shiftKey) {
        // 如果是 textarea 且按住 shift，则不触发提交（允许换行）
        if (input.tagName === 'TEXTAREA' && event.shiftKey) {
          return
        }
        
        event.preventDefault()
        
        // 执行绑定的函数
        if (typeof binding.value === 'function') {
          binding.value(event)
        } else if (binding.value && typeof binding.value.handler === 'function') {
          binding.value.handler(event)
        }
      }
    }

    input.addEventListener('keydown', handleEnter)
    
    // 保存事件处理器引用，用于卸载
    el._enterSubmitHandler = handleEnter
    el._enterSubmitInput = input
  },

  beforeUnmount(el) {
    // 清理事件监听器
    if (el._enterSubmitHandler && el._enterSubmitInput) {
      el._enterSubmitInput.removeEventListener('keydown', el._enterSubmitHandler)
      delete el._enterSubmitHandler
      delete el._enterSubmitInput
    }
  }
}

// 自动表单提交指令 - 针对表单内的输入框
export const autoSubmitForm = {
  mounted(el, binding) {
    // 查找所有输入框
    const inputs = el.querySelectorAll('input, textarea')
    const handlers = []

    inputs.forEach(input => {
      // 跳过特殊类型的输入框
      if (input.type === 'checkbox' || input.type === 'radio' || input.type === 'file') {
        return
      }

      const handleEnter = (event) => {
        if (event.key === 'Enter') {
          // textarea 按 shift+enter 允许换行
          if (input.tagName === 'TEXTAREA' && event.shiftKey) {
            return
          }
          
          event.preventDefault()
          
          // 执行绑定的提交函数
          if (typeof binding.value === 'function') {
            binding.value(event)
          }
        }
      }

      input.addEventListener('keydown', handleEnter)
      handlers.push({ input, handler: handleEnter })
    })

    // 保存处理器引用
    el._autoSubmitHandlers = handlers
  },

  beforeUnmount(el) {
    // 清理所有事件监听器
    if (el._autoSubmitHandlers) {
      el._autoSubmitHandlers.forEach(({ input, handler }) => {
        input.removeEventListener('keydown', handler)
      })
      delete el._autoSubmitHandlers
    }
  }
}

export default {
  enterSubmit,
  autoSubmitForm
}