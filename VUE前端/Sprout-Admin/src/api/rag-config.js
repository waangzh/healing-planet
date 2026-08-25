import request from '@/utils/request'

export const getRagConfigCurrent = () => request.get('/admin/rag-config/current')
export const getRagConfigRevisions = () => request.get('/admin/rag-config/revisions')
export const createRagConfigDraft = (data) => request.post('/admin/rag-config/drafts', data)
export const validateRagConfigDraft = (revision) => request.post(`/admin/rag-config/drafts/${revision}/validate`)
export const publishRagConfigDraft = (revision) => request.post(`/admin/rag-config/drafts/${revision}/publish`)
export const rollbackRagConfig = (revision) => request.post(`/admin/rag-config/revisions/${revision}/rollback`)
