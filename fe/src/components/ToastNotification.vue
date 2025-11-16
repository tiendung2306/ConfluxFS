<template>
  <TransitionGroup name="toast" tag="div" class="fixed top-4 right-4 z-50 space-y-2 pointer-events-none">
    <div
      v-for="toast in toasts"
      :key="toast.id"
      class="pointer-events-auto flex items-start p-4 rounded-lg shadow-lg border min-w-[320px] max-w-md animate-slide-in"
      :class="getToastClass(toast.type)"
    >
      <div class="flex-shrink-0">
        <svg v-if="toast.type === 'error'" class="w-5 h-5 text-red-600" fill="currentColor" viewBox="0 0 20 20">
          <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd"></path>
        </svg>
        <svg v-else-if="toast.type === 'success'" class="w-5 h-5 text-green-600" fill="currentColor" viewBox="0 0 20 20">
          <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"></path>
        </svg>
        <svg v-else class="w-5 h-5 text-blue-600" fill="currentColor" viewBox="0 0 20 20">
          <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd"></path>
        </svg>
      </div>
      <div class="ml-3 flex-1">
        <p class="text-sm font-medium" :class="getTextClass(toast.type)">
          {{ toast.message }}
        </p>
      </div>
      <button
        @click="removeToast(toast.id)"
        class="ml-4 flex-shrink-0 text-gray-400 hover:text-gray-600"
      >
        <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
          <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"></path>
        </svg>
      </button>
    </div>
  </TransitionGroup>
</template>

<script>
import { useToastStore } from '../stores/toast'

export default {
  name: 'ToastNotification',
  setup() {
    const toastStore = useToastStore()
    return { toastStore }
  },
  computed: {
    toasts() {
      return this.toastStore.toasts
    }
  },
  methods: {
    removeToast(id) {
      this.toastStore.remove(id)
    },
    getToastClass(type) {
      switch (type) {
        case 'error':
          return 'bg-red-50 border-red-200'
        case 'success':
          return 'bg-green-50 border-green-200'
        default:
          return 'bg-blue-50 border-blue-200'
      }
    },
    getTextClass(type) {
      switch (type) {
        case 'error':
          return 'text-red-800'
        case 'success':
          return 'text-green-800'
        default:
          return 'text-blue-800'
      }
    }
  }
}
</script>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s ease;
}

.toast-enter-from {
  opacity: 0;
  transform: translateX(100%);
}

.toast-leave-to {
  opacity: 0;
  transform: translateX(100%);
}

@keyframes slide-in {
  from {
    transform: translateX(100%);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

.animate-slide-in {
  animation: slide-in 0.3s ease-out;
}
</style>

