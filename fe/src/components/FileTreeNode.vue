<template>
  <div class="file-node">
    <div class="flex items-center justify-between p-2 hover:bg-gray-50 rounded-lg group"
         :class="{ 'bg-blue-50 border border-blue-200': selectedFolderId === file.id }"
         @contextmenu.prevent="handleContextClick($event)">
      <div class="flex items-center flex-1 min-w-0">
        <button
          @click="isExpanded = !isExpanded"
          class="p-1 hover:bg-gray-200 rounded mr-2"
          v-if="file.children && file.children.length > 0"
        >
          <svg class="w-4 h-4 transition-transform" :class="{ 'rotate-90': isExpanded }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path>
          </svg>
        </button>
        <div class="w-6 mr-2" v-else></div>
        
        <svg class="w-5 h-5 text-gray-500 mr-3 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path v-if="file.type === 'FOLDER'" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2H5a2 2 0 00-2-2z"></path>
          <path v-else stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
        </svg>
        
        <button class="text-left text-sm font-medium text-gray-900 truncate hover:underline"
                @click="$emit('select-folder', file.id)"
                :disabled="file.type !== 'FOLDER'">
          {{ file.name }}
        </button>
      </div>
      
      <div class="relative">
        <button
          @click.stop="handleContextClick"
          class="p-1 hover:bg-gray-200 rounded opacity-0 group-hover:opacity-100 transition-opacity"
        >
          <svg class="w-4 h-4 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z"></path>
          </svg>
        </button>
      </div>
    </div>
    
    <div v-if="isExpanded && file.children && file.children.length > 0" class="ml-6 mt-1">
      <FileTreeNode
        v-for="child in file.children"
        :key="child.id"
        :file="child"
        :selectedFolderId="selectedFolderId"
        @rename="$emit('rename', $event)"
        @move="$emit('move', $event)"
        @delete="$emit('delete', $event)"
        @select-folder="$emit('select-folder', $event)"
        @context="(file, evt) => $emit('context', file, evt)"
      />
    </div>
  </div>
</template>

<script>
export default {
  name: 'FileTreeNode', // Important for recursive components
  props: ['file', 'selectedFolderId'],
  emits: ['rename', 'move', 'delete', 'select-folder', 'context'],
  data() {
    return {
      isExpanded: false,
    }
  },
  methods: {
    handleContextClick(evt) {
      this.$emit('context', this.file, evt)
    }
  }
}
</script>
