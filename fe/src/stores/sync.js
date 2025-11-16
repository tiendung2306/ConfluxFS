import { defineStore } from 'pinia'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'
import { useFileStore } from './files'
import { useToastStore } from './toast'

function getWebSocketURL() {
    const baseUrl = process.env.VUE_APP_API_BASE_URL || 'http://localhost:8080';
    // SockJS expects an http/https URL, it handles the ws/wss upgrade internally.
    // The backend WebSocket endpoint is at /api/ws relative to the base URL.
    return `${baseUrl}/api/ws`;
}

export const useSyncStore = defineStore('sync', {
    state: () => ({
        client: null,
        connectionStatus: 'Disconnected',
        lastSyncTime: null,
        operations: []
    }),

    actions: {
        connect() {
            if (this.client && this.client.active) {
                console.log("STOMP client already active. Deactivating before reconnecting.");
                this.client.deactivate();
            }

            const url = getWebSocketURL();
            console.log(`Connecting WebSocket to: ${url}`);

            this.client = new Client({
                webSocketFactory: () => new SockJS(url),
                reconnectDelay: 5000,
                onConnect: () => {
                    this.connectionStatus = 'Connected'
                    const toastStore = useToastStore();
                    toastStore.success('Đã kết nối với máy chủ.');
                    this.client.subscribe('/topic/events', (message) => {
                        try {
                            const payload = JSON.parse(message.body)
                            const { type, data } = payload || {}
                            this.routeEvent(type, data)
                        } catch (e) {
                            console.error('Invalid STOMP message', e)
                        }
                    })
                },
                onDisconnect: () => {
                    this.connectionStatus = 'Disconnected'
                    const toastStore = useToastStore();
                    toastStore.error('Mất kết nối với máy chủ.');
                },
                onStompError: (frame) => {
                    this.connectionStatus = 'Disconnected'
                    const toastStore = useToastStore();
                    toastStore.error(`Lỗi kết nối: ${frame.headers['message']}`);
                    console.error('Broker reported error: ' + frame.headers['message']);
                    console.error('Additional details: ' + frame.body);
                }
            })

            this.client.activate()
        },

        disconnect() {
            if (this.client) {
                this.client.deactivate()
                this.client = null
                this.connectionStatus = 'Disconnected'
            }
        },

        routeEvent(eventType, data) {
            const fileStore = useFileStore();
            const toastStore = useToastStore();
            this.addOperation(eventType, data);

            switch (eventType) {
                // Any event that modifies the tree structure
                case 'file.created':
                case 'file.updated':
                case 'file.deleted':
                case 'file.moved':
                case 'file.locally_modified':
                case 'file.externally_modified':
                    // The most robust way to sync the UI is to refetch the canonical state from the server.
                    console.log(`Received event '${eventType}', reloading file tree.`);
                    fileStore.loadFiles();
                    break;

                // Events that affect connection/sync status
                case 'sync.started':
                    this.connectionStatus = 'Syncing';
                    break;
                case 'sync.completed':
                    this.connectionStatus = 'Connected';
                    this.lastSyncTime = new Date();
                    // A sync might have merged operations, so reload the tree to be safe.
                    fileStore.loadFiles();
                    break;
                case 'sync.conflict':
                    toastStore.error('Xung đột đồng bộ. Đang tải lại trạng thái.');
                    this.addOperation('Sync Conflict', data);
                    fileStore.loadFiles();
                    break;
                
                default:
                    // Ignore unknown events
                    console.warn(`Received unknown event type: ${eventType}`);
                    break;
            }
        },

        addOperation(type, data) {
            this.operations.unshift({
                id: Date.now(),
                type,
                data,
                timestamp: new Date()
            })

            // Keep only last 50 operations
            if (this.operations.length > 50) {
                this.operations = this.operations.slice(0, 50)
            }
        },

        clearOperations() {
            this.operations = []
        }
    }
})
