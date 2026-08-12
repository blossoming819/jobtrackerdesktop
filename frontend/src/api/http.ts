import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/api', timeout: 15000 })

http.interceptors.response.use(
  response => {
    const data = response.data
    if (data && typeof data.code !== 'undefined') {
      if (data.code !== 200) {
        ElMessage.error(data.message || '请求失败')
        return Promise.reject(new Error(data.message))
      }
      return data.data
    }
    return response
  },
  error => {
    const message = error.code === 'ECONNABORTED'
      ? '请求等待时间过长，已停止等待。模型服务可能繁忙，请稍后重试。'
      : error.message || '网络异常'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default http
