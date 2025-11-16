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
                // UI will be updated via WebSocket event
                const toastStore = useToastStore()
                toastStore.success('Tạo thư mục thành công')
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
                        // Optional: can emit progress event if needed
                        const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total)
                        console.log(`Upload progress: ${percentCompleted}%`)
                    }
                })
                // UI will be updated via WebSocket event
                const toastStore = useToastStore()
                toastStore.success('Tải file lên thành công')
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
                toastStore.success('Di chuyển file thành công')
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
                toastStore.success('Sao chép thành công')
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
                toastStore.success('Xóa file thành công')
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
                toastStore.success('Đổi tên thành công')
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
        },

        // Internal actions for state manipulation
        _addNode(node) {
            const findAndAdd = (nodes, parentId, newNode) => {
                if (parentId === null) {
                    nodes.push(newNode);
                    return true;
                }
                for (let i = 0; i < nodes.length; i++) {
                    if (nodes[i].id === parentId) {
                        if (!nodes[i].children) {
                            nodes[i].children = [];
                        }
                        nodes[i].children.push(newNode);
                        return true;
                    }
                    if (nodes[i].children && findAndAdd(nodes[i].children, parentId, newNode)) {
                        return true;
                    }
                }
                return false;
            };
            findAndAdd(this.files, node.parentId, { ...node, children: [] });
        },

        _removeNode(nodeId) {
            const findAndRemove = (nodes, targetId) => {
                for (let i = 0; i < nodes.length; i++) {
                    if (nodes[i].id === targetId) {
                        nodes.splice(i, 1);
                        return true;
                    }
                    if (nodes[i].children && findAndRemove(nodes[i].children, targetId)) {
                        return true;
                    }
                }
                return false;
            };
            findAndRemove(this.files, nodeId);
        },

        _updateNode(nodeData) {
            const findAndUpdate = (nodes, data) => {
                for (let i = 0; i < nodes.length; i++) {
                    if (nodes[i].id === data.id) {
                        // Only update properties that are expected to change, like name.
                        // Moves are handled separately by the 'file.moved' event.
                        if (data.name !== undefined) {
                            nodes[i].name = data.name;
                        }
                        // Add other updatable properties here if necessary
                        return true;
                    }
                    if (nodes[i].children && findAndUpdate(nodes[i].children, data)) {
                        return true;
                    }
                }
                return false;
            };
            findAndUpdate(this.files, nodeData);
        }
    }
})
