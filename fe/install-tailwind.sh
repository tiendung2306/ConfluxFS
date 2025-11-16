#!/bin/bash

echo "🚀 Installing Tailwind CSS dependencies..."

# Navigate to frontend directory
cd fe

# Install new dependencies
echo "📦 Installing Tailwind CSS and related packages..."
npm install tailwindcss@^3.4.0 autoprefixer@^10.4.0 postcss@^8.4.0 @heroicons/vue@^2.0.0 lucide-vue-next@^0.300.0

# Remove Vuetify dependencies
echo "🗑️ Removing Vuetify dependencies..."
npm uninstall vuetify @mdi/font

echo "✅ Dependencies updated successfully!"
echo ""
echo "📋 Next steps:"
echo "1. Run 'npm run serve' to start the development server"
echo "2. The new Tailwind CSS design should be visible"
echo "3. All pages have been redesigned with modern UI components"
echo ""
echo "🎨 Features of the new design:"
echo "- Modern gradient backgrounds"
echo "- Responsive design"
echo "- Smooth animations"
echo "- Vietnamese language support"
echo "- Clean, minimal interface"
echo "- Better accessibility"
