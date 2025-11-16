<script setup>
import { useAuthStore } from './stores/auth';
import { useSyncStore } from './stores/sync';
import ToastNotification from './components/ToastNotification.vue';
import { useRouter } from 'vue-router';

const authStore = useAuthStore();
const syncStore = useSyncStore();
const router = useRouter();

const logout = () => {
  authStore.logout();
  router.push('/login');
};

const getStatusColorClass = () => {
  switch (syncStore.connectionStatus) {
    case 'Connected': return 'bg-green-500';
    case 'Syncing': return 'bg-yellow-500';
    case 'Disconnected': return 'bg-red-500';
    default: return 'bg-gray-500';
  }
};
</script>

<template>
  <div class="min-h-screen bg-gray-50">
    <!-- Header -->
    <header class="bg-white shadow-sm border-b border-gray-200">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <!-- Logo -->
          <div class="flex items-center">
            <div class="flex-shrink-0 flex items-center">
              <div class="w-8 h-8 bg-gradient-to-r from-blue-600 to-purple-600 rounded-lg flex items-center justify-center mr-3">
                <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2H5a2 2 0 00-2-2z"></path>
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 5a2 2 0 012-2h4a2 2 0 012 2v2H8V5z"></path>
                </svg>
              </div>
              <h1 class="text-xl font-bold text-gray-900">CRDT File System</h1>
            </div>
          </div>

          <!-- User Actions -->
          <div v-if="authStore.isAuthenticated" class="flex items-center space-x-4">
            <!-- Connection Status -->
            <div class="flex items-center">
              <div :class="getStatusColorClass()" class="w-2 h-2 rounded-full mr-2"></div>
              <span class="text-sm font-medium text-gray-700">{{ syncStore.connectionStatus }}</span>
            </div>
            
            <!-- Logout Button -->
            <button
              @click="logout"
              class="inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:opacity-50 disabled:pointer-events-none text-gray-600 hover:bg-gray-100 hover:text-gray-900 active:bg-gray-200 h-8 px-3 text-xs"
            >
              <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"></path>
              </svg>
              Đăng xuất
            </button>
          </div>
        </div>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1">
      <router-view />
    </main>

    <!-- Footer -->
    <footer class="bg-white border-t border-gray-200">
      <div class="max-w-7xl mx-auto py-4 px-4 sm:px-6 lg:px-8">
        <div class="text-center text-sm text-gray-500">
          &copy; {{ new Date().getFullYear() }} CRDT File System. Tất cả quyền được bảo lưu.
        </div>
      </div>
    </footer>

    <!-- Toast Notifications -->
    <ToastNotification />
  </div>
</template>
