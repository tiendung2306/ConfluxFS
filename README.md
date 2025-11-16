# CRDT File System

Hệ thống File Phân tán Cộng tác thời gian thực sử dụng CRDT Tree cho Replicated Servers.

## Tổng quan

Đây là một hệ thống file phân tán được xây dựng dựa trên thuật toán CRDT Tree của Martin Kleppmann, cho phép nhiều replica servers hoạt động độc lập và đồng bộ hóa metadata thông qua Redis Pub/Sub mà không cần khóa trung tâm.

## Tính năng chính

- **CRDT Tree Implementation**: Triển khai thuật toán "A highly-available move operation for replicated trees"
- **Real-time Synchronization**: Đồng bộ hóa thời gian thực qua Redis Pub/Sub
- **Conflict Resolution**: Tự động giải quyết xung đột và đảm bảo eventual consistency
- **Move Operation Safety**: Ngăn chặn việc tạo chu trình trong cây thư mục
- **Web Interface**: Giao diện web thân thiện với Vue.js
- **Authentication**: Hệ thống xác thực JWT
- **File Upload/Download**: Tải lên và tải xuống file
- **Docker Support**: Triển khai dễ dàng với Docker Compose

## Kiến trúc hệ thống

### Backend (Spring Boot)
- **CRDT Tree Core**: Thuật toán CRDT Tree với các operations create, update, delete, move
- **RESTful APIs**: APIs cho file operations và user management
- **Redis Integration**: Pub/Sub messaging cho real-time sync
- **PostgreSQL**: Database cho persistence
- **Spring Security**: Authentication và authorization
- **WebSocket**: Real-time communication

### Frontend (Vue.js)
- **Vue 3**: Framework frontend với Composition API
- **Pinia**: State management
- **Vuetify**: UI components
- **WebSocket Client**: Real-time sync indicators
- **Axios**: HTTP client

## Cài đặt và chạy

### Yêu cầu hệ thống
- **JDK 24** (Java Development Kit)
- Docker và Docker Compose
- Node.js 18+ (nếu chạy frontend local)
- Maven 3.9+ (nếu chạy backend local)

### Chạy với Docker Compose

1. Clone repository:
```bash
git clone <repository-url>
cd project-3
```

2. Chạy hệ thống:
```bash
docker-compose up -d
```

3. Truy cập ứng dụng:
- Frontend: http://localhost:3000
- Backend API: http://localhost:8080/api
- PostgreSQL: localhost:5432
- Redis: localhost:6379

### Chạy local development

#### Backend
```bash
cd be
mvn clean install
mvn spring-boot:run
```

#### Frontend
```bash
cd fe
npm install
npm run serve
```

## Cấu hình

### Environment Variables

#### Backend (.env hoặc application.yml)
```yaml
DB_USERNAME: crdt_user
DB_PASSWORD: crdt_password
REDIS_HOST: localhost
REDIS_PORT: 6379
JWT_SECRET: your-secret-key
REPLICA_ID: replica-1
FILE_STORAGE_PATH: ./uploads
```

### Database Schema

Hệ thống sử dụng PostgreSQL với các bảng chính:
- `users`: Thông tin người dùng
- `files`: Metadata file/folder
- `crdt_operations`: Log các CRDT operations
- `replica_states`: Trạng thái các replica

## API Documentation

### Authentication APIs
- `POST /api/auth/login` - Đăng nhập
- `POST /api/auth/register` - Đăng ký
- `POST /api/auth/logout` - Đăng xuất

### File System APIs
- `GET /api/files/tree` - Lấy cấu trúc cây thư mục
- `POST /api/files/upload` - Tải lên file
- `GET /api/files/{id}/download` - Tải xuống file
- `POST /api/files/folder` - Tạo thư mục
- `PUT /api/files/{id}` - Cập nhật file/folder
- `DELETE /api/files/{id}` - Xóa file/folder
- `PUT /api/files/{id}/move` - Di chuyển file/folder

### CRDT APIs
- `POST /api/crdt/operations` - Gửi CRDT operation
- `GET /api/crdt/state` - Lấy trạng thái CRDT
- `POST /api/crdt/sync` - Đồng bộ với replicas khác

## CRDT Tree Algorithm

### Core Operations

1. **CREATE**: Tạo node mới trong cây
2. **UPDATE**: Cập nhật thuộc tính node
3. **DELETE**: Đánh dấu node là deleted (tombstone)
4. **MOVE**: Di chuyển node đến parent mới

### Conflict Resolution

- **Timestamp-based**: Operations với timestamp cao hơn sẽ thắng
- **Cycle Prevention**: Kiểm tra và ngăn chặn việc tạo chu trình
- **Eventual Consistency**: Tất cả replicas sẽ hội tụ về cùng trạng thái

### Vector Clock

Mỗi replica duy trì vector clock để theo dõi thứ tự operations và đảm bảo consistency.

## Testing

### Manual Testing
1. Tạo nhiều replica servers
2. Thực hiện concurrent operations
3. Kiểm tra eventual consistency
4. Test network partition scenarios

### Load Testing
```bash
# Sử dụng JMeter hoặc tools tương tự
# Test concurrent move operations
# Measure latency và throughput
```

## Monitoring

### Health Checks
- `GET /api/actuator/health` - Health status
- `GET /api/actuator/metrics` - System metrics

### Logs
- CRDT operations
- Sync events
- Error handling

## Troubleshooting

### Common Issues

1. **Sync Problems**: Kiểm tra Redis connection
2. **Database Issues**: Verify PostgreSQL connection
3. **File Upload Errors**: Check file storage permissions
4. **Authentication Issues**: Verify JWT configuration

### Debug Mode
```yaml
logging:
  level:
    com.crdt: DEBUG
```

## Contributing

1. Fork repository
2. Create feature branch
3. Commit changes
4. Push to branch
5. Create Pull Request

## License

MIT License - xem file LICENSE để biết thêm chi tiết.

## References

- [CRDT Wikipedia](https://en.wikipedia.org/wiki/Conflict-free_replicated_data_type)
- [Kleppmann Move Operation Paper](https://martin.kleppmann.com/papers/move-op.pdf)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Vue.js Documentation](https://vuejs.org/)
