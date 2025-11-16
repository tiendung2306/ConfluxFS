<template>
  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
      <!-- Main File Explorer -->
      <div class="lg:col-span-2">
        <div class="rounded-lg border border-gray-200 bg-white text-gray-900 shadow-sm">
          <div class="flex flex-col space-y-1.5 p-6">
            <div class="flex items-center justify-between">
              <div class="flex items-center">
                <svg class="w-6 h-6 text-blue-600 mr-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2H5a2 2 0 00-2-2z"></path>
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 5a2 2 0 012-2h4a2 2 0 012 2v2H8V5z"></path>
                </svg>
                <h2 class="text-xl font-semibold text-gray-900">File Explorer</h2>
              </div>
              <div class="flex space-x-3">
                <button
                  @click="showCreateFolderDialog = true"
                  class="inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:opacity-50 disabled:pointer-events-none bg-blue-600 text-white hover:bg-blue-700 active:bg-blue-800 h-8 px-3 text-xs"
                >
                  <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6"></path>
                  </svg>
                  Tạo thư mục
                </button>
                <button
                  @click="triggerFileUpload"
                  class="inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:opacity-50 disabled:pointer-events-none bg-gray-100 text-gray-900 hover:bg-gray-200 active:bg-gray-300 h-8 px-3 text-xs"
                >
                  <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path>
                  </svg>
                  Tải lên
                </button>
              </div>
            </div>
          </div>
          
          <div class="p-6 pt-0">
            <!-- Breadcrumb -->
            <div v-if="breadcrumb.length" class="text-sm text-gray-600 mb-4 flex flex-wrap items-center">
              <button class="hover:underline" @click="selectFolder(null)">Root</button>
              <span v-for="(crumb, idx) in breadcrumb" :key="crumb.id" class="flex items-center">
                <svg class="w-4 h-4 mx-2 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path></svg>
                <button class="hover:underline" @click="selectFolder(crumb.id)">{{ crumb.name }}</button>
              </span>
            </div>
            <!-- Loading State -->
            <div v-if="fileStore.loading" class="flex items-center justify-center py-12">
              <div class="flex items-center space-x-3">
                <svg class="animate-spin h-6 w-6 text-blue-600" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                <span class="text-gray-600">Đang tải...</span>
              </div>
            </div>
            
            <!-- File Tree -->
            <div v-else-if="fileTree.length > 0" class="space-y-1">
              <FileTreeNode
                v-for="file in fileTree"
                :key="file.id"
                :file="file"
                :selectedFolderId="selectedFolderId"
                @rename="renameFile"
                @move="moveFile"
                @delete="deleteFile"
                @select-folder="selectFolder"
                @context="showContextMenu"
              />
            </div>
            
            <!-- Empty State -->
            <div v-else class="text-center py-12">
              <svg class="mx-auto h-12 w-12 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2H5a2 2 0 00-2-2z"></path>
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 5a2 2 0 012-2h4a2 2 0 012 2v2H8V5z"></path>
              </svg>
              <h3 class="mt-2 text-sm font-medium text-gray-900">Không có file nào</h3>
              <p class="mt-1 text-sm text-gray-500">Bắt đầu bằng cách tạo thư mục hoặc tải file lên.</p>
              <div class="mt-6 flex justify-center space-x-3">
                <button
                  @click="showCreateFolderDialog = true"
                  class="inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:opacity-50 disabled:pointer-events-none bg-blue-600 text-white hover:bg-blue-700 active:bg-blue-800 h-8 px-3 text-xs"
                >
                  <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6"></path>
                  </svg>
                  Tạo thư mục
                </button>
                <button
                  @click="triggerFileUpload"
                  class="inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:opacity-50 disabled:pointer-events-none border border-blue-600 text-blue-600 hover:bg-blue-50 active:bg-blue-100 h-8 px-3 text-xs"
                >
                  <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path>
                  </svg>
                  Tải lên
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <!-- Sync Status Sidebar -->
      <div class="lg:col-span-1">
        <div class="rounded-lg border border-gray-200 bg-white text-gray-900 shadow-sm">
          <div class="flex flex-col space-y-1.5 p-6">
            <div class="flex items-center">
              <svg class="w-5 h-5 text-green-600 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path>
              </svg>
              <h3 class="text-lg font-semibold text-gray-900">Trạng thái đồng bộ</h3>
            </div>
          </div>
          
          <div class="p-6 pt-0">
            <!-- Connection Status -->
            <div class="flex items-center mb-4">
              <div :class="getSyncStatusColorClass(syncStore.connectionStatus)" class="w-3 h-3 rounded-full mr-3"></div>
              <span class="text-sm font-medium text-gray-700">{{ syncStore.connectionStatus }}</span>
            </div>
            
            <!-- Last Sync Time -->
            <div v-if="syncStore.lastSyncTime" class="text-xs text-gray-500 mb-4">
              Lần đồng bộ cuối: {{ formatTime(syncStore.lastSyncTime) }}
            </div>
            
            <div class="border-t border-gray-200 pt-4">
              <h4 class="text-sm font-medium text-gray-900 mb-3">Hoạt động gần đây</h4>
              
              <!-- Operations List -->
              <div v-if="syncStore.operations.length > 0" class="space-y-2">
                <div
                  v-for="operation in syncStore.operations.slice(0, 5)"
                  :key="operation.id"
                  class="flex items-start space-x-3 p-2 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  <div class="flex-shrink-0 mt-0.5">
                    <svg class="w-4 h-4" :class="getOperationIconClass(operation.type)" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path v-if="operation.type === 'File Created'" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6"></path>
                      <path v-else-if="operation.type === 'File Updated'" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path>
                      <path v-else-if="operation.type === 'File Deleted'" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                      <path v-else-if="operation.type === 'File Moved'" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4"></path>
                      <path v-else stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                    </svg>
                  </div>
                  <div class="flex-1 min-w-0">
                    <p class="text-xs font-medium text-gray-900">{{ operation.type }}</p>
                    <p class="text-xs text-gray-500">{{ formatTime(operation.timestamp) }}</p>
                  </div>
                </div>
              </div>
              
              <!-- Empty Operations -->
              <div v-else class="text-center py-4">
                <svg class="mx-auto h-8 w-8 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v10a2 2 0 002 2h8a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"></path>
                </svg>
                <p class="mt-2 text-xs text-gray-500">Chưa có hoạt động nào</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Folder Content View (like Google Drive) -->
    <div class="mt-8">
      <div class="rounded-lg border border-gray-200 bg-white text-gray-900 shadow-sm">
        <div class="flex items-center justify-between p-4 border-b border-gray-100">
          <h3 class="text-sm font-medium text-gray-900">Nội dung thư mục</h3>
          <span class="text-xs text-gray-500">{{ currentItems.length }} mục</span>
        </div>
        <div class="p-4" @contextmenu.prevent="showEmptyAreaContextMenu($event)">
          <div v-if="currentItems.length === 0" class="text-center text-sm text-gray-500 py-8">
            Thư mục trống
          </div>
          <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            <div v-for="item in currentItems" 
                 :key="item.id" 
                 class="flex items-center justify-between p-3 rounded-lg border border-gray-100 hover:bg-gray-50 group"
                 @contextmenu.prevent.stop="showContextMenu(item, $event)">
              <div class="flex items-center flex-1 min-w-0">
                <svg class="w-5 h-5 mr-3 flex-shrink-0" :class="item.type === 'FOLDER' ? 'text-yellow-500' : 'text-gray-500'" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path v-if="item.type === 'FOLDER'" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2H5a2 2 0 00-2-2z"/>
                  <path v-else stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
                </svg>
                <button class="truncate text-sm font-medium text-gray-900 hover:underline" @click="item.type === 'FOLDER' && selectFolder(item.id)">{{ item.name }}</button>
              </div>
              <button
                @click.stop="showContextMenu(item, $event)"
                class="p-1 hover:bg-gray-200 rounded opacity-0 group-hover:opacity-100 transition-opacity ml-2"
              >
                <svg class="w-4 h-4 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z"></path>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Create Folder Dialog -->
    <div v-if="showCreateFolderDialog" class="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50" @click.self="showCreateFolderDialog = false">
      <div class="relative top-20 mx-auto p-5 border w-96 shadow-lg rounded-md bg-white">
        <div class="mt-3">
          <h3 class="text-lg font-medium text-gray-900 mb-4">Tạo thư mục mới</h3>
          <div class="mb-4">
            <label for="folderName" class="block text-sm font-medium text-gray-700 mb-2">Tên thư mục</label>
            <input
              id="folderName"
              v-model="newFolderName"
              type="text"
              class="flex h-10 w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm placeholder:text-gray-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
              placeholder="Nhập tên thư mục"
              @keyup.enter="createFolder"
            />
          </div>
          <div class="flex justify-end space-x-3">
            <button
              @click="showCreateFolderDialog = false"
              class="inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:opacity-50 disabled:pointer-events-none text-gray-600 hover:bg-gray-100 hover:text-gray-900 active:bg-gray-200 h-8 px-3 text-xs"
            >
              Hủy
            </button>
            <button
              @click="createFolder"
              :disabled="!newFolderName.trim()"
              class="inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:opacity-50 disabled:pointer-events-none bg-blue-600 text-white hover:bg-blue-700 active:bg-blue-800 h-8 px-3 text-xs"
            >
              Tạo
            </button>
          </div>
        </div>
      </div>
    </div>
    
    <!-- File Upload Input -->
    <input
      ref="fileInput"
      type="file"
      style="display: none"
      @change="handleFileUpload"
      multiple
    />

    <!-- Context Menu -->
    <div v-if="contextMenu.visible" 
         ref="contextMenuRef"
         class="fixed z-50 bg-white border border-gray-200 rounded-md shadow-lg py-1 text-sm"
         :style="{ top: contextMenu.y + 'px', left: contextMenu.x + 'px', minWidth: '180px' }">
      <!-- Menu cho item cụ thể -->
      <template v-if="contextMenu.target">
        <button class="w-full text-left px-4 py-2 hover:bg-gray-100" @click="openItem(contextMenu.target)">
          {{ contextMenu.target?.type === 'FOLDER' ? 'Mở thư mục' : 'Xem file' }}
        </button>
        <div class="border-t my-1"></div>
        <button class="w-full text-left px-4 py-2 hover:bg-gray-100" @click="copyItem">Sao chép</button>
        <button class="w-full text-left px-4 py-2 hover:bg-gray-100" @click="cutItem">Cắt</button>
        <button class="w-full text-left px-4 py-2 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed disabled:text-gray-400" 
                @click="pasteItem" 
                :disabled="!clipboard.item">
          Dán vào thư mục đang chọn
        </button>
        <div class="border-t my-1"></div>
        <button class="w-full text-left px-4 py-2 hover:bg-gray-100" @click="renameFile(contextMenu.target)">Đổi tên</button>
        <button class="w-full text-left px-4 py-2 text-red-600 hover:bg-red-50" @click="deleteFile(contextMenu.target)">Xóa</button>
      </template>
      <!-- Menu cho vùng trống -->
      <template v-else>
        <button class="w-full text-left px-4 py-2 hover:bg-gray-100" @click="showCreateFolderDialog = true; hideContextMenu()">
          Tạo thư mục
        </button>
        <button class="w-full text-left px-4 py-2 hover:bg-gray-100" @click="triggerFileUpload(); hideContextMenu()">
          Tải file lên
        </button>
        <button class="w-full text-left px-4 py-2 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed disabled:text-gray-400" 
                @click="pasteItem" 
                :disabled="!clipboard.item">
          Dán vào thư mục đang chọn
        </button>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue';
import { useFileStore } from '../stores/files';
import { useSyncStore } from '../stores/sync';
import FileTreeNode from '../components/FileTreeNode.vue';

// Store instances
const fileStore = useFileStore();
const syncStore = useSyncStore();

// Component state
const showCreateFolderDialog = ref(false);
const newFolderName = ref('');
const selectedFolderId = ref(null);
const fileInput = ref(null); // ref for the file input element
const contextMenuRef = ref(null); // ref for the context menu element

const contextMenu = ref({
  visible: false,
  x: 0,
  y: 0,
  target: null
});

const clipboard = ref({
  mode: null, // 'copy' | 'cut'
  item: null
});

// Computed properties
const fileTree = computed(() => fileStore.files);

const breadcrumb = computed(() => {
  if (!selectedFolderId.value) return [];
  const path = [];
  const parentMap = new Map();
  const fillParentMap = (nodes, parentId = null) => {
    nodes.forEach(n => {
      parentMap.set(n.id, parentId);
      if (n.children) fillParentMap(n.children, n.id);
    });
  };
  fillParentMap(fileStore.files, null);

  const nodeMap = new Map();
  const indexNodes = (nodes) => {
    nodes.forEach(n => {
      nodeMap.set(n.id, n);
      if (n.children) indexNodes(n.children);
    });
  };
  indexNodes(fileStore.files);

  let currentId = selectedFolderId.value;
  while (currentId) {
    const node = nodeMap.get(currentId);
    if (!node) break;
    path.unshift({ id: node.id, name: node.name });
    currentId = parentMap.get(currentId);
  }
  return path;
});

const currentItems = computed(() => {
  if (!selectedFolderId.value) return fileStore.files;
  const stack = [...fileStore.files];
  while (stack.length) {
    const n = stack.shift();
    if (n.id === selectedFolderId.value) {
      return n.children || [];
    }
    if (n.children) stack.push(...n.children);
  }
  return [];
});

// Lifecycle hooks
onMounted(async () => {
  await fileStore.loadFiles();
  syncStore.connect();
});

onBeforeUnmount(() => {
  syncStore.disconnect();
});

// Methods
const hideContextMenu = () => {
  contextMenu.value.visible = false;
  document.removeEventListener('click', hideContextMenu);
};

const _showContextMenuWithPosition = (item, evt) => {
  const estimatedMenuWidth = 180;
  const estimatedMenuHeight = item ? 220 : 150;
  const { innerWidth: viewportWidth, innerHeight: viewportHeight } = window;
  
  let x = evt.clientX;
  let y = evt.clientY;

  if (x + estimatedMenuWidth > viewportWidth) x = viewportWidth - estimatedMenuWidth - 10;
  if (x < 10) x = 10;
  if (y + estimatedMenuHeight > viewportHeight) y = evt.clientY - estimatedMenuHeight;
  if (y < 10) y = 10;

  contextMenu.value = { visible: true, x, y, target: item };
  document.addEventListener('click', hideContextMenu, { once: true });

  nextTick(() => {
    if (!contextMenuRef.value) return;
    const menu = contextMenuRef.value;
    const { offsetWidth: menuWidth, offsetHeight: menuHeight } = menu;
    let finalX = contextMenu.value.x;
    let finalY = contextMenu.value.y;

    if (finalX + menuWidth > viewportWidth) finalX = viewportWidth - menuWidth - 10;
    if (finalY + menuHeight > viewportHeight) finalY = evt.clientY - menuHeight;

    contextMenu.value.x = finalX;
    contextMenu.value.y = finalY;
  });
};

const showContextMenu = (item, evt) => {
  _showContextMenuWithPosition(item, evt);
};

const showEmptyAreaContextMenu = (evt) => {
  const target = evt.target;
  const itemElement = target.closest('.group, [class*="item"]');
  if (itemElement && itemElement !== evt.currentTarget) return;
  _showContextMenuWithPosition(null, evt);
};

const selectFolder = (id) => {
  selectedFolderId.value = id || null;
};

const openItem = (item) => {
  if (item.type === 'FOLDER') {
    selectFolder(item.id);
  } else {
    viewFile(item);
  }
  hideContextMenu();
};

const viewFile = async (item) => {
  try {
    const token = localStorage.getItem('token');
    const url = `/api/files/${item.id}/download`;
    const res = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
    const blob = await res.blob();
    const objUrl = URL.createObjectURL(blob);
    window.open(objUrl, '_blank');
    setTimeout(() => URL.revokeObjectURL(objUrl), 60000);
  } catch (e) {
    console.error('View failed', e);
  }
};

const copyItem = () => {
  clipboard.value = { mode: 'copy', item: contextMenu.value.target };
  hideContextMenu();
};

const cutItem = () => {
  clipboard.value = { mode: 'cut', item: contextMenu.value.target };
  hideContextMenu();
};

const pasteItem = async () => {
  if (!clipboard.value.item) return;
  const targetParent = selectedFolderId.value || null;
  if (clipboard.value.mode === 'cut') {
    await fileStore.moveFile(clipboard.value.item.id, targetParent);
  } else if (clipboard.value.mode === 'copy') {
    await fileStore.copyFile(clipboard.value.item.id, targetParent);
  }
  clipboard.value = { mode: null, item: null };
  hideContextMenu();
};

const createFolder = async () => {
  if (!newFolderName.value.trim()) return;
  const result = await fileStore.createFolder(newFolderName.value, selectedFolderId.value);
  if (result.success) {
    showCreateFolderDialog.value = false;
    newFolderName.value = '';
  }
};

const triggerFileUpload = () => {
  fileInput.value.click();
};

const handleFileUpload = async (event) => {
  const files = Array.from(event.target.files);
  for (const file of files) {
    await fileStore.uploadFile(file, selectedFolderId.value);
  }
  event.target.value = '';
};

const renameFile = async (file) => {
  const newName = prompt('Nhập tên mới:', file.name);
  if (newName && newName !== file.name) {
    await fileStore.updateFileName(file.id, newName);
  }
};

const moveFile = async (file) => {
  const newParentId = prompt('Nhập ID thư mục cha mới (để trống cho thư mục gốc):');
  await fileStore.moveFile(file.id, newParentId || null);
};

const deleteFile = async (file) => {
  if (confirm(`Bạn có chắc chắn muốn xóa "${file.name}"?`)) {
    await fileStore.deleteFile(file.id);
  }
};

const getSyncStatusColorClass = (status) => {
  switch (status) {
    case 'Connected': return 'bg-green-500';
    case 'Syncing': return 'bg-yellow-500';
    case 'Disconnected': return 'bg-red-500';
    default: return 'bg-gray-500';
  }
};

const getOperationIconClass = (type) => {
  switch (type) {
    case 'File Created': return 'text-green-600';
    case 'File Updated': return 'text-blue-600';
    case 'File Deleted': return 'text-red-600';
    case 'File Moved': return 'text-purple-600';
    case 'Sync Conflict': return 'text-orange-600';
    default: return 'text-gray-600';
  }
};

const formatTime = (date) => new Date(date).toLocaleTimeString('vi-VN');

</script>
