# Hướng dẫn chạy hệ thống CRDT File System

## 🚀 Cách chạy hệ thống

### 1. Chạy với Docker Compose (Khuyến nghị)

```bash
# Chạy tất cả services
docker-compose up -d

# Kiểm tra logs
docker-compose logs -f

# Dừng hệ thống
docker-compose down
```

### 2. Chạy local development

#### Prerequisites
- **JDK 21** (Java Development Kit)
- Maven 3.9+
- Node.js 18+

#### Backend (Spring Boot)
```bash
cd be
mvn clean install
mvn spring-boot:run
```

#### Frontend (Vue.js)
```bash
cd fe
npm install
npm run serve
```

## 📋 Services và Ports

- **Frontend**: http://localhost:3000
- **Backend API**: http://localhost:8080/api
- **PostgreSQL**: localhost:5432
- **Redis**: localhost:6379

## 🔧 Cấu hình Database

### Tạo database PostgreSQL
```sql
CREATE DATABASE crdt_filesystem;
CREATE USER crdt_user WITH PASSWORD 'crdt_password';
GRANT ALL PRIVILEGES ON DATABASE crdt_filesystem TO crdt_user;
```

## 🧪 Test hệ thống

### 1. Test Authentication
```bash
# Register user
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123",
    "firstName": "Test",
    "lastName": "User"
  }'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }'
```

### 2. Test File Operations
```bash
# Tạo folder (thay YOUR_JWT_TOKEN bằng token từ login)
curl -X POST http://localhost:8080/api/files/folder \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "name": "Test Folder",
    "parentId": null
  }'

# Upload file
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -F "file=@test.txt" \
  -F "parentId="

# Get file tree
curl -X GET http://localhost:8080/api/files/tree \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 3. Test CRDT Operations
```bash
# Get CRDT state
curl -X GET http://localhost:8080/api/crdt/state \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# Sync with replicas
curl -X POST http://localhost:8080/api/crdt/sync \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## 🐛 Troubleshooting

### Common Issues

1. **Database Connection Error**
   - Kiểm tra PostgreSQL đang chạy
   - Verify connection string trong application.yml

2. **Redis Connection Error**
   - Kiểm tra Redis server đang chạy
   - Check Redis configuration

3. **Port Already in Use**
   - Thay đổi port trong application.yml
   - Hoặc kill process đang sử dụng port

4. **Frontend Build Error**
   - Chạy `npm install` lại
   - Check Node.js version (cần 18+)

### Debug Mode
```yaml
# Trong application.yml
logging:
  level:
    com.crdt: DEBUG
    org.springframework.security: DEBUG
```

## 📊 Monitoring

### Health Checks
- Backend: http://localhost:8080/api/actuator/health
- Metrics: http://localhost:8080/api/actuator/metrics

### Logs
```bash
# Docker logs
docker-compose logs -f app

# Local logs
tail -f logs/application.log
```

## 🔄 Multi-Replica Testing

Để test với nhiều replicas:

1. **Tạo multiple instances**:
```bash
# Terminal 1 - Replica 1
REPLICA_ID=replica-1 mvn spring-boot:run

# Terminal 2 - Replica 2  
REPLICA_ID=replica-2 mvn spring-boot:run

# Terminal 3 - Replica 3
REPLICA_ID=replica-3 mvn spring-boot:run
```

2. **Test concurrent operations**:
   - Tạo files/folders từ different replicas
   - Test move operations
   - Verify eventual consistency

## 📝 API Documentation

### Authentication Endpoints
- `POST /api/auth/login` - User login
- `POST /api/auth/register` - User registration
- `POST /api/auth/logout` - User logout

### File System Endpoints
- `GET /api/files/tree` - Get file tree structure
- `POST /api/files/upload` - Upload file
- `GET /api/files/{id}/download` - Download file
- `POST /api/files/folder` - Create folder
- `PUT /api/files/{id}` - Update file/folder
- `DELETE /api/files/{id}` - Delete file/folder
- `PUT /api/files/{id}/move` - Move file/folder

### CRDT Endpoints
- `POST /api/crdt/operations` - Submit CRDT operation
- `GET /api/crdt/state` - Get current CRDT state
- `POST /api/crdt/sync` - Sync with other replicas
- `GET /api/crdt/operations` - Get operations since timestamp

## 🎯 Next Steps

1. **Performance Testing**: Sử dụng JMeter để test concurrent operations
2. **Load Testing**: Test với nhiều users và files
3. **Network Partition Testing**: Simulate network failures
4. **Benchmark**: So sánh với traditional locking approach
