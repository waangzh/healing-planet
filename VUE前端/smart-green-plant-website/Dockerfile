# 构建阶段
FROM node:latest AS builder

# 设置工作目录
WORKDIR /app

# 首先只复制包管理文件
COPY package*.json ./

# 安装依赖 (添加 --legacy-peer-deps 标志)
RUN npm install --legacy-peer-deps

# 复制其他源代码文件
COPY . .

# 构建应用
RUN npm run build

# 生产阶段
FROM nginx:latest

# 从构建阶段复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 暴露端口
EXPOSE 80

# 启动 Nginx
CMD ["nginx", "-g", "daemon off;"]