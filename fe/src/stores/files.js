import { defineStore } from 'pinia'
import api from '../api'
import { useToastStore } from './toast'

export const useFileStore = defineStore('files', {
    state: () => ({
        files: [],
        currentPath: [],
        selectedFile: null,
        loading: false
    }),

    actions: {
        async loadFiles() {
            this.loading = true
            try {
                const response = await api.get('/api/files/tree')
                this.files = response.data.nodes || []
                return { success: true }
            } catch (error) {
                console.error('Error loading files:', error)
                const toastStore = useToastStore()
                const errorMsg = error.response?.data?.message || error.response?.data || 'Không thể tải danh sách file'
                toastStore.error(typeof errorMsg === 'string' ? errorMsg : 'Không thể tải danh sách file')
                return {
                    success: false,
                    error: errorMsg
                }
            } finally {
                this.loading = false
            }
        },

        async createFolder(name, parentId = null) {
            try {
                const response = await api.post('/api/files/folder', {
                    name,
                    parentId
                })
                // UI will be updated via WebSocket event, which triggers loadFiles()
                const toastStore = useToastStore()
                toastStore.success('Yêu cầu tạo thư mục đã được gửi')
                return { success: true, data: response.data }
            } catch (error) {
                const toastStore = useToastStore()
                const errorMsg = error.response?.data?.message || error.response?.data || 'Không thể tạo thư mục'
                toastStore.error(typeof errorMsg === 'string' ? errorMsg : 'Không thể tạo thư mục')
                return {
                    success: false,
                    error: errorMsg
                }
            }
        },

        async uploadFile(file, parentId = null) {
            try {
                const formData = new FormData()
                formData.append('file', file)
                if (parentId) {
                    formData.append('parentId', parentId)
                }

                const response = await api.post('/api/files/upload', formData, {
                    headers: {
                        'Content-Type': 'multipart/form-data'
                    },
                    timeout: 300000, // 5 minutes for large file uploads
                    onUploadProgress: (progressEvent) => {
                        const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total)
                        console.log(`Upload progress: ${percentCompleted}%`)
                    }
                })
                // UI will be updated via WebSocket event
                const toastStore = useToastStore()
                toastStore.success('Yêu cầu tải file lên đã được gửi')
                return { success: true, data: response.data }
            } catch (error) {
                const toastStore = useToastStore()
                const errorMsg = error.response?.data?.message || error.response?.data || 'Không thể tải file lên'
                toastStore.error(typeof errorMsg === 'string' ? errorMsg : 'Không thể tải file lên')
                return {
                    success: false,
                    error: errorMsg
                }
            }
        },

        async moveFile(fileId, newParentId) {
            try {
                const response = await api.put(`/api/files/${fileId}/move`, {
                    newParentId
                })
                // UI will be updated via WebSocket event
                const toastStore = useToastStore()
                toastStore.success('Yêu cầu di chuyển file đã được gửi')
                return { success: true, data: response.data }
            } catch (error) {
                const toastStore = useToastStore()
                const errorMsg = error.response?.data?.message || error.response?.data || 'Không thể di chuyển file'
                toastStore.error(typeof errorMsg === 'string' ? errorMsg : 'Không thể di chuyển file')
                return {
                    success: false,
                    error: errorMsg
                }
            }
        },

        async copyFile(fileId, targetParentId) {
            try {
                const response = await api.post(`/api/files/${fileId}/copy`, {
                    targetParentId
                })
                // UI will be updated via WebSocket event
                const toastStore = useToastStore()
                toastStore.success('Yêu cầu sao chép đã được gửi')
                return { success: true, data: response.data }
            } catch (error) {
                const toastStore = useToastStore()
                const errorMsg = error.response?.data?.message || error.response?.data || 'Không thể sao chép'
                toastStore.error(typeof errorMsg === 'string' ? errorMsg : 'Không thể sao chép')
                return {
                    success: false,
                    error: errorMsg
                }
            }
        },

        async deleteFile(fileId) {
            try {
                await api.delete(`/api/files/${fileId}`)
                // UI will be updated via WebSocket event
                const toastStore = useToastStore()
                toastStore.success('Yêu cầu xóa file đã được gửi')
                return { success: true }
            } catch (error) {
                const toastStore = useToastStore()
                const errorMsg = error.response?.data?.message || error.response?.data || 'Không thể xóa file'
                toastStore.error(typeof errorMsg === 'string' ? errorMsg : 'Không thể xóa file')
                return {
                    success: false,
                    error: errorMsg
                }
            }
        },

        async updateFileName(fileId, newName) {
            try {
                const response = await api.put(`/api/files/${fileId}`, {
                    name: newName
                })
                // UI will be updated via WebSocket event
                const toastStore = useToastStore()
                toastStore.success('Yêu cầu đổi tên đã được gửi')
                return { success: true, data: response.data }
            } catch (error) {
                const toastStore = useToastStore()
                const errorMsg = error.response?.data?.message || error.response?.data || 'Không thể đổi tên file'
                toastStore.error(typeof errorMsg === 'string' ? errorMsg : 'Không thể đổi tên file')
                return {
                    success: false,
                    error: errorMsg
                }
            }
        },

        setSelectedFile(file) {
            this.selectedFile = file
        },

        clearSelectedFile() {
            this.selectedFile = null
        }
    }
})
