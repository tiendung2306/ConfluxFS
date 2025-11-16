# CRDT File System - Frontend với Tailwind CSS

## 🎨 Thiết kế mới

Frontend đã được redesign hoàn toàn với Tailwind CSS, mang lại giao diện hiện đại và đẹp mắt hơn.

### ✨ Tính năng mới

- **Giao diện hiện đại**: Sử dụng Tailwind CSS với thiết kế clean và minimal
- **Responsive**: Tối ưu cho mọi thiết bị từ mobile đến desktop
- **Animations**: Hiệu ứng mượt mà với fade-in, slide-up, bounce-in
- **Tiếng Việt**: Giao diện được Việt hóa hoàn toàn
- **Icons**: Sử dụng Heroicons và Lucide icons thay vì Material Design Icons
- **Color Scheme**: Bảng màu nhất quán với primary blue và secondary gray

### 🚀 Cài đặt

1. **Cài đặt dependencies mới:**
   ```bash
   cd fe
   chmod +x install-tailwind.sh
   ./install-tailwind.sh
   ```

2. **Hoặc cài đặt thủ công:**
   ```bash
   cd fe
   npm install tailwindcss@^3.4.0 autoprefixer@^10.4.0 postcss@^8.4.0 @heroicons/vue@^2.0.0 lucide-vue-next@^0.300.0
   npm uninstall vuetify @mdi/font
   ```

3. **Chạy ứng dụng:**
   ```bash
   npm run serve
   ```

### 📱 Các trang đã được redesign

#### 1. **Trang Đăng nhập (Login)**
- Background gradient đẹp mắt
- Form validation với error messages
- Loading states với spinner animation
- Responsive design cho mobile

#### 2. **Trang Đăng ký (Register)**
- Layout 2 cột cho desktop, 1 cột cho mobile
- Form fields với icons
- Validation real-time
- Smooth transitions

#### 3. **File Explorer**
- Tree view với custom component
- Context menu cho mỗi file/folder
- Sync status sidebar
- Empty state với call-to-action buttons
- Modal dialog cho tạo folder

#### 4. **App Layout**
- Clean header với logo và user actions
- Connection status indicator
- Footer với copyright
- Responsive navigation

### 🎯 Cải thiện UX

- **Loading States**: Spinner animations khi đang tải
- **Empty States**: Hướng dẫn người dùng khi không có data
- **Error Handling**: Error messages rõ ràng và đẹp mắt
- **Hover Effects**: Interactive elements với hover states
- **Focus States**: Accessibility tốt hơn với focus indicators

### 🛠️ Cấu trúc file

```
fe/
├── src/
│   ├── assets/
│   │   └── css/
│   │       └── main.css          # Tailwind CSS imports và custom styles
│   ├── views/
│   │   ├── Login.vue             # Trang đăng nhập mới
│   │   ├── Register.vue          # Trang đăng ký mới
│   │   └── FileExplorer.vue      # File explorer mới
│   ├── App.vue                   # Layout chính
│   └── main.js                   # Entry point
├── tailwind.config.js            # Cấu hình Tailwind
├── postcss.config.js             # Cấu hình PostCSS
└── package.json                  # Dependencies mới
```

### 🎨 Custom CSS Classes

Đã tạo các utility classes tùy chỉnh trong `main.css`:

- `.btn` - Button base styles
- `.btn-primary`, `.btn-secondary`, `.btn-outline`, `.btn-ghost` - Button variants
- `.btn-sm`, `.btn-md`, `.btn-lg` - Button sizes
- `.input` - Input field styles
- `.card`, `.card-header`, `.card-content` - Card components
- `.alert`, `.alert-error`, `.alert-success` - Alert components

### 📱 Responsive Breakpoints

- **Mobile**: < 640px
- **Tablet**: 640px - 1024px  
- **Desktop**: > 1024px

### 🌈 Color Palette

- **Primary**: Blue gradient (#3b82f6 - #1d4ed8)
- **Secondary**: Gray scale (#64748b - #0f172a)
- **Success**: Green (#4CAF50)
- **Error**: Red (#FF5252)
- **Warning**: Yellow (#FFC107)

### 🔧 Development

Để phát triển thêm:

1. **Thêm components mới**: Sử dụng Tailwind classes
2. **Custom animations**: Thêm vào `tailwind.config.js`
3. **New colors**: Extend color palette trong config
4. **Responsive design**: Sử dụng Tailwind responsive prefixes

### 📝 Notes

- Đã loại bỏ hoàn toàn Vuetify và Material Design Icons
- Sử dụng SVG icons thay vì icon fonts
- Tất cả text đã được Việt hóa
- Performance tốt hơn với Tailwind CSS purging
- Bundle size nhỏ hơn so với Vuetify
