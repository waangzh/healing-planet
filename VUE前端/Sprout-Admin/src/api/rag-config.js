import request from '@/utils/request'

export const getRagConfigCurrent = () => request.get('/admin/rag-config/current')
export const getRagConfigRevisions = () => request.get('/admin/rag-config/revisions')
export const getRagConnectionProfiles = () => request.get('/admin/rag-config/connection-profiles')
export const createRagConfigDraft = (data) => request.post('/admin/rag-config/drafts', data)
export const validateRagConfigDraft = (revision) => request.post(`/admin/rag-config/drafts/${revision}/validate`)
export const testRagConfigConnections = (revision) => request.post(`/admin/rag-config/drafts/${revision}/connections/test`)
export const publishRagConfigDraft = (revision) => request.post(`/admin/rag-config/drafts/${revision}/publish`)
export const rollbackRagConfig = (revision) => request.post(`/admin/rag-config/revisions/${revision}/rollback`)
