import { defineStore } from 'pinia'
import api from '../api'

export const useAuthStore = defineStore('auth', {
    state: () => ({
        user: null,
        token: localStorage.getItem('token'),
        isAuthenticated: !!localStorage.getItem('token')
    }),

    actions: {
        async login(credentials) {
            try {
                const response = await api.post('/api/auth/login', credentials)
                const { token, userId, username, email } = response.data

                this.token = token
                this.user = { userId, username, email }
                this.isAuthenticated = true

                localStorage.setItem('token', token)

                return { success: true }
            } catch (error) {
                return {
                    success: false,
                    error: error.response?.data || 'Đăng nhập thất bại'
                }
            }
        },

        async register(userData) {
            try {
                const response = await api.post('/api/auth/register', userData)
                const { token, userId, username, email } = response.data

                this.token = token
                this.user = { userId, username, email }
                this.isAuthenticated = true

                localStorage.setItem('token', token)

                return { success: true }
            } catch (error) {
                return {
                    success: false,
                    error: error.response?.data || 'Đăng ký thất bại'
                }
            }
        },

        logout() {
            this.token = null
            this.user = null
            this.isAuthenticated = false

            localStorage.removeItem('token')
        },

        initializeAuth() {
            if (this.token) {
                // Token đã được set trong localStorage, không cần làm gì thêm
                // vì interceptor sẽ tự động thêm vào request
            }
        }
    }
})
