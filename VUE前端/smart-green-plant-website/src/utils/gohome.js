import { useRouter } from 'vue-router'

export function useGoHome() {
  const router = useRouter()

  const goHome = () => {
    router.push('/')
  }

  return { goHome }
}
