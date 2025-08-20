import { ref, onMounted, onUnmounted } from 'vue';

/**
 * 响应式媒体查询钩子函数
 * @param {string} query 媒体查询字符串，例如'(max-width: 768px)'
 * @returns {object} 包含匹配状态的ref对象
 */
export function useMediaQuery(query) {
  const matches = ref(false);
  
  let mediaQuery;
  
  const updateMatches = (e) => {
    matches.value = e.matches;
  };
  
  onMounted(() => {
    mediaQuery = window.matchMedia(query);
    matches.value = mediaQuery.matches;
    mediaQuery.addEventListener('change', updateMatches);
  });
  
  onUnmounted(() => {
    if (mediaQuery) {
      mediaQuery.removeEventListener('change', updateMatches);
    }
  });
  
  return { matches };
}

/**
 * 移动设备检测钩子函数
 * @returns {object} 包含isMobile状态的ref对象
 */
export function useMobileDetection() {
  const { matches: isMobile } = useMediaQuery('(max-width: 768px)');
  return { isMobile };
}

/**
 * 平板设备检测钩子函数
 * @returns {object} 包含isTablet状态的ref对象
 */
export function useTabletDetection() {
  const { matches: isTablet } = useMediaQuery('(min-width: 769px) and (max-width: 1024px)');
  return { isTablet };
}

/**
 * 断点检测钩子函数
 * @returns {object} 包含各种断点状态的对象
 */
export function useBreakpoints() {
  const { matches: isMobile } = useMediaQuery('(max-width: 768px)');
  const { matches: isTablet } = useMediaQuery('(min-width: 769px) and (max-width: 1024px)');
  const { matches: isDesktop } = useMediaQuery('(min-width: 1025px) and (max-width: 1440px)');
  const { matches: isWidescreen } = useMediaQuery('(min-width: 1441px)');
  
  return {
    isMobile,
    isTablet,
    isDesktop,
    isWidescreen
  };
} 