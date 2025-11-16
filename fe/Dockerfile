FROM node:18-alpine

WORKDIR /app

COPY package*.json ./
RUN npm install --legacy-peer-deps --timeout=600000

COPY . .

EXPOSE 3000

CMD ["sh", "-c", "echo '🚀 Starting Vue.js development server on port 3000...' && npm run serve"]
