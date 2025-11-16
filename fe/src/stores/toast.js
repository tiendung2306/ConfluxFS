import { defineStore } from 'pinia'

export const useToastStore = defineStore('toast', {
  state: () => ({
    toasts: []
  }),

  actions: {
    show(message, type = 'info') {
      const id = Date.now() + Math.random()
      const toast = { id, message, type }
      
      this.toasts.push(toast)
      
      // Auto remove after 5 seconds
      setTimeout(() => {
        this.remove(id)
      }, 5000)
      
      return id
    },
    
    error(message) {
      return this.show(message, 'error')
    },
    
    success(message) {
      return this.show(message, 'success')
    },
    
    info(message) {
      return this.show(message, 'info')
    },
    
    remove(id) {
      const index = this.toasts.findIndex(t => t.id === id)
      if (index > -1) {
        this.toasts.splice(index, 1)
      }
    }
  }
})

