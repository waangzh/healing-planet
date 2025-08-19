import request from '@/utils/request'

// 获取公告
export const getBillboard = () => {
    return request.get('/billboard/show')
}