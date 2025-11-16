# Hướng dẫn cấu hình Frontend với Backend URL

## Thay đổi đã thực hiện

1. **Loại bỏ proxy configuration** từ `vue.config.js`
2. **Cập nhật axios configuration** trong `src/api/index.js` để sử dụng URL trực tiếp
3. **Tạo file cấu hình mẫu** `config.env.example`

## Cách sử dụng

### 1. Tạo file .env (nếu chưa có)
```bash
# Trong thư mục fe/
cp config.env.example .env
```

### 2. Cấu hình URL backend trong file .env
```env
# Backend API Configuration
VUE_APP_API_BASE_URL=http://localhost:8080

# Hoặc cho production
# VUE_APP_API_BASE_URL=https://your-domain.com/api
```

### 3. Khởi động lại frontend
```bash
npm run serve
```

## Lợi ích của cách cấu hình mới

- ✅ Không còn lỗi proxy ECONNREFUSED
- ✅ Axios gọi trực tiếp đến backend
- ✅ Dễ dàng thay đổi URL backend qua environment variables
- ✅ Hoạt động tốt trong cả development và production
- ✅ Không phụ thuộc vào proxy configuration

## Troubleshooting

Nếu vẫn gặp lỗi CORS:
1. Đảm bảo backend đã cấu hình CORS cho frontend domain
2. Kiểm tra backend có đang chạy trên port 8080 không
3. Kiểm tra firewall/antivirus có chặn kết nối không

## Cấu hình cho các môi trường khác nhau

### Development
```env
VUE_APP_API_BASE_URL=http://localhost:8080
```

### Production
```env
VUE_APP_API_BASE_URL=https://your-backend-domain.com/api
```

### Docker
```env
VUE_APP_API_BASE_URL=http://backend:8080
```
