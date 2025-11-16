# 🔧 Cấu hình API - Frontend với Backend

## ✅ **Đã sửa xong vấn đề API routing!**

### 🚨 **Vấn đề trước đây:**
- Frontend đang gọi API sang `http://localhost:3000/api/auth/login` (sai port)
- Thay vì gọi sang backend `http://localhost:8080/api/auth/login`

### 🔧 **Giải pháp đã áp dụng:**

#### 1. **Proxy Configuration trong `vue.config.js`:**
```javascript
devServer: {
    port: 3000,
    proxy: {
        '/api': {
            target: 'http://localhost:8080',  // Backend server
            changeOrigin: true,
            secure: false
        }
    }
}
```

#### 2. **API Instance trong `src/api/index.js`:**
```javascript
import axios from 'axios'

const api = axios.create({
  baseURL: process.env.NODE_ENV === 'production' 
    ? 'http://localhost:8080' 
    : '', // Development: sử dụng proxy
  timeout: 10000
})

// Auto-inject JWT token vào headers
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})
```

#### 3. **Cập nhật Stores:**
- ✅ `auth.js` - Sử dụng API instance mới
- ✅ `files.js` - Sử dụng API instance mới  
- ✅ `sync.js` - WebSocket vẫn kết nối trực tiếp đến port 8080

### 🌐 **Cách hoạt động:**

#### **Development Mode:**
1. Frontend chạy trên `http://localhost:3000`
2. Khi gọi `/api/auth/login` → Proxy forward sang `http://localhost:8080/api/auth/login`
3. Backend xử lý và trả về response
4. Frontend nhận được response từ backend

#### **Production Mode:**
1. Frontend build và deploy
2. API calls sẽ gọi trực tiếp đến `http://localhost:8080`
3. Không cần proxy

### 🚀 **Cách chạy:**

1. **Start Backend:**
   ```bash
   cd be
   mvn spring-boot:run
   # Backend chạy trên http://localhost:8080
   ```

2. **Start Frontend:**
   ```bash
   cd fe
   npm run serve
   # Frontend chạy trên http://localhost:3000
   ```

3. **Test API:**
   - Mở `http://localhost:3000`
   - Thử đăng nhập/đăng ký
   - Kiểm tra Network tab trong DevTools
   - API calls sẽ được proxy sang `localhost:8080`

### 🔍 **Kiểm tra hoạt động:**

#### **Trong Browser DevTools:**
- **Network tab**: Sẽ thấy requests đến `/api/auth/login`
- **Request URL**: `http://localhost:3000/api/auth/login`
- **Actual target**: `http://localhost:8080/api/auth/login` (qua proxy)

#### **Console logs:**
- ✅ "Connected to WebSocket" - WebSocket kết nối thành công
- ✅ API responses từ backend
- ❌ Không còn lỗi CORS hoặc 404

### 📋 **API Endpoints được proxy:**

- `POST /api/auth/login` → Backend authentication
- `POST /api/auth/register` → Backend registration  
- `GET /api/files/tree` → Backend file tree
- `POST /api/files/folder` → Backend create folder
- `POST /api/files/upload` → Backend file upload
- `PUT /api/files/{id}` → Backend update file
- `DELETE /api/files/{id}` → Backend delete file

### 🎯 **Lợi ích:**

1. **✅ Đúng routing**: API calls đến đúng backend port
2. **✅ CORS handling**: Proxy xử lý CORS issues
3. **✅ Development friendly**: Không cần cấu hình CORS phức tạp
4. **✅ Production ready**: Tự động switch sang direct API calls
5. **✅ Error handling**: Centralized error handling với interceptors
6. **✅ Auto token**: JWT token tự động được inject vào requests

### 🐛 **Troubleshooting:**

#### **Nếu vẫn gặp lỗi:**
1. **Kiểm tra backend có chạy không:**
   ```bash
   curl http://localhost:8080/api/auth/login
   ```

2. **Kiểm tra proxy config:**
   - Restart frontend server sau khi thay đổi `vue.config.js`
   - Clear browser cache

3. **Kiểm tra Network tab:**
   - Requests phải đến `localhost:3000/api/...`
   - Response phải từ backend

### 📝 **Notes:**

- **WebSocket**: Vẫn kết nối trực tiếp đến `localhost:8080` (không qua proxy)
- **File uploads**: Được proxy bình thường với multipart/form-data
- **Authentication**: JWT token được tự động inject vào mọi requests
- **Error handling**: 401 errors sẽ tự động logout và redirect về login

Bây giờ frontend sẽ gọi đúng API sang backend! 🎉
