import axios from 'axios'

const API_BASE_URL = '/api'

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000,
  withCredentials: true
})

export const connectSSE = (url, params, onMessage, onError) => {
  const queryString = Object.keys(params)
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&')

  const fullUrl = `${API_BASE_URL}${url}?${queryString}`
  const eventSource = new EventSource(fullUrl, { withCredentials: true })

  eventSource.onmessage = event => {
    const data = event.data
    if (onMessage) onMessage(data)
  }

  eventSource.onerror = error => {
    if (onError) onError(error)
    eventSource.close()
  }

  return eventSource
}

export const registerAccount = (payload) => request.post('/auth/register', payload)
export const loginAccount = (payload) => request.post('/auth/login', payload)
export const logoutAccount = () => request.post('/auth/logout')
export const getCurrentUser = () => request.get('/auth/me')

export const suggestConversationReply = (payload) => {
  return request.post('/ai/coach/suggest', payload)
}

export const getModelStatus = () => request.get('/ai/system/models')
export const getSystemStatus = () => request.get('/ai/system/status')
export const searchGlobal = (q = '', limit = 12, scope = 'all') => request.get('/ai/search', { params: { q, limit, scope } })
export const getChatGptAuthStatus = () => request.get('/ai/auth/chatgpt/status')

export const startChatGptLogin = (forceNew = false) => {
  return request.post('/ai/auth/chatgpt/start', null, {
    params: { forceNew }
  })
}

export const getChatGptModels = () => request.get('/ai/auth/chatgpt/models')
export const logoutChatGpt = () => request.post('/ai/auth/chatgpt/logout')

export const selectModelRoute = (route, candidate) => {
  return request.post(`/ai/system/models/routes/${route}`, { candidate })
}

export const clearModelRoute = (route) => {
  return request.delete(`/ai/system/models/routes/${route}`)
}

export const chatWithManus = (message) => {
  return connectSSE('/ai/manus/chat', { message })
}

export default {
  registerAccount,
  loginAccount,
  logoutAccount,
  getCurrentUser,
  suggestConversationReply,
  getModelStatus,
  getSystemStatus,
  searchGlobal,
  getChatGptAuthStatus,
  startChatGptLogin,
  getChatGptModels,
  logoutChatGpt,
  selectModelRoute,
  clearModelRoute,
  chatWithManus
}
