<template>
  <div class="min-h-screen bg-gradient-to-br from-blue-50 via-indigo-50 to-purple-50 flex items-center justify-center p-4">
    <div class="w-full max-w-md">
      <!-- Logo và Title -->
      <div class="text-center mb-8 animate-fade-in">
        <div class="inline-flex items-center justify-center w-20 h-20 bg-gradient-to-r from-blue-600 to-purple-600 rounded-full mb-4 shadow-lg">
          <svg class="w-10 h-10 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2H5a2 2 0 00-2-2z"></path>
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 5a2 2 0 012-2h4a2 2 0 012 2v2H8V5z"></path>
          </svg>
        </div>
        <h1 class="text-3xl font-bold text-gray-900 mb-2">CRDT File System</h1>
        <p class="text-gray-600">Đăng nhập vào tài khoản của bạn</p>
      </div>

      <!-- Login Form -->
      <div class="rounded-lg border border-gray-200 bg-white text-gray-900 shadow-sm shadow-xl animate-slide-up">
        <div class="p-6 pt-0">
          <form @submit.prevent="login" class="space-y-6">
            <!-- Username Field -->
            <div class="space-y-2">
              <label for="username" class="text-sm font-medium text-gray-700">
                Tên đăng nhập
              </label>
              <div class="relative">
                <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <svg class="h-5 w-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"></path>
                  </svg>
                </div>
                <input
                  id="username"
                  v-model="form.username"
                  type="text"
                  class="flex h-10 w-full rounded-md border border-gray-300 bg-white px-3 py-2 pl-10 text-sm placeholder:text-gray-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                  placeholder="Nhập tên đăng nhập"
                  required
                />
              </div>
              <p v-if="errors.username" class="text-sm text-red-600">{{ errors.username }}</p>
            </div>

            <!-- Password Field -->
            <div class="space-y-2">
              <label for="password" class="text-sm font-medium text-gray-700">
                Mật khẩu
              </label>
              <div class="relative">
                <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <svg class="h-5 w-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path>
                  </svg>
                </div>
                <input
                  id="password"
                  v-model="form.password"
                  type="password"
                  class="flex h-10 w-full rounded-md border border-gray-300 bg-white px-3 py-2 pl-10 text-sm placeholder:text-gray-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                  placeholder="Nhập mật khẩu"
                  required
                />
              </div>
              <p v-if="errors.password" class="text-sm text-red-600">{{ errors.password }}</p>
            </div>

            <!-- Error Alert -->
            <div v-if="errors.general" class="relative w-full rounded-lg border p-4 border-red-200 bg-red-50 text-red-800 animate-bounce-in">
              <div class="flex items-center">
                <svg class="w-5 h-5 mr-2" fill="currentColor" viewBox="0 0 20 20">
                  <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd"></path>
                </svg>
                {{ errors.general }}
              </div>
            </div>

            <!-- Login Button -->
            <button
              type="submit"
              :disabled="loading"
              class="inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:opacity-50 disabled:pointer-events-none bg-blue-600 text-white hover:bg-blue-700 active:bg-blue-800 h-12 px-8 text-base w-full"
            >
              <svg v-if="loading" class="animate-spin -ml-1 mr-3 h-5 w-5 text-white" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              <svg v-else class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 16l-4-4m0 0l4-4m-4 4h14m-5 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h7a3 3 0 013 3v1"></path>
              </svg>
              {{ loading ? 'Đang đăng nhập...' : 'Đăng nhập' }}
            </button>
          </form>

          <!-- Divider -->
          <div class="mt-6">
            <div class="relative">
              <div class="absolute inset-0 flex items-center">
                <div class="w-full border-t border-gray-300"></div>
              </div>
              <div class="relative flex justify-center text-sm">
                <span class="px-2 bg-white text-gray-500">hoặc</span>
              </div>
            </div>
          </div>

          <!-- Register Link -->
          <div class="mt-6 text-center">
            <p class="text-sm text-gray-600 mb-4">Chưa có tài khoản?</p>
            <button
              @click="$router.push('/register')"
              class="inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:opacity-50 disabled:pointer-events-none border border-blue-600 text-blue-600 hover:bg-blue-50 active:bg-blue-100 h-10 px-4 py-2 w-full"
            >
              <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z"></path>
              </svg>
              Tạo tài khoản mới
            </button>
          </div>

          <!-- API Documentation Link -->
          <div class="mt-6 text-center">
            <button
              @click="openSwagger"
              class="text-sm text-blue-600 hover:text-blue-800 hover:underline transition-colors"
            >
              <svg class="w-4 h-4 inline mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4"></path>
              </svg>
              API Documentation (Swagger)
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { useAuthStore } from '../stores/auth';

export default {
  name: 'Login',
  setup() {
    const authStore = useAuthStore()
    
    return {
      authStore
    }
  },
  data() {
    return {
      form: {
        username: '',
        password: ''
      },
      errors: {},
      loading: false
    }
  },
  methods: {
    async login() {
      this.errors = {}
      this.loading = true
      
      try {
        const result = await this.authStore.login(this.form)
        
        if (result.success) {
          this.$router.push('/files')
        } else {
          this.errors = { general: result.error }
        }
      } catch (error) {
        this.errors = { general: 'Đã xảy ra lỗi không mong muốn' }
      } finally {
        this.loading = false
      }
    },
    
    openSwagger() {
      const swaggerUrl = 'http://localhost:8080/api/swagger-ui.html'
      window.open(swaggerUrl, '_blank')
    }
  }
}
</script>
