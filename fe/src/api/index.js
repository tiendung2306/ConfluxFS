import axios from 'axios'

// Cấu hình axios base URL
const api = axios.create({
    baseURL: process.env.VUE_APP_API_BASE_URL || 'http://localhost:8080',
    timeout: 10000,
    headers: {
        'Content-Type': 'application/json'
    }
})

// Request interceptor để thêm token
api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('token')
        if (token) {
            config.headers.Authorization = `Bearer ${token}`
        }
        return config
    },
    (error) => {
        return Promise.reject(error)
    }
)

// Response interceptor để xử lý lỗi
api.interceptors.response.use(
    (response) => {
        return response
    },
    (error) => {
        // Chỉ đăng xuất khi 401 (Unauthorized) - không phải lỗi khác như 413 (Payload Too Large), 415, 500
        if (error.response?.status === 401) {
            // Kiểm tra xem có phải lỗi do file upload không
            const isUploadError = error.config?.url?.includes('/upload') || 
                                 error.response?.status === 413 ||
                                 error.message?.includes('timeout') ||
                                 error.message?.includes('size')
            
            if (!isUploadError) {
                // Token hết hạn, xóa token và redirect về login
                localStorage.removeItem('token')
                window.location.href = '/login'
            }
        }
        return Promise.reject(error)
    }
)

export default api
