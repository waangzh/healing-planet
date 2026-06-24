# Healing Planet

> A smart plant care and community platform — IoT hardware control, AI plant disease detection, plant encyclopedia, and social community.

## Overview

Healing Planet is a full-stack system consisting of:

- **2 Spring Boot backends** — community services (port 8000) + IoT device control (port 8070)
- **3 Vue 3 frontends** — community portal, plant dashboard, admin panel
- **MCP protocol support** — AI agent integration for smart plant pot control

## Backend Services

### healing-planet-sys (Port 8000)

Community-focused backend providing:

- User registration, login, profile management
- Post publishing, comments, likes, bookmarks, follows
- Private messaging via WebSocket (`/chat/{userId}`)
- Plant encyclopedia with Redis caching
- "Xiao Lv" AI chat assistant (Baidu AI + SSE streaming)
- File upload (Alibaba Cloud OSS)
- Admin panel (user/content/tag/plant/billboard management)
- JWT authentication, captcha, global exception handling

### smart_green_plant (Port 8070)

IoT-focused backend for smart plant pots:

- Device management: add/delete/update/query devices, online/offline monitoring
- Environmental monitoring: soil moisture, temperature, humidity, CO2, light intensity (via Alibaba Cloud IoT AMQP)
- Threshold-based auto control: auto-switch irrigation pump, grow light, fan based on configurable thresholds
- Email alerts: QQ email SMTP notification when environmental data exceeds thresholds
- AI plant disease detection: upload plant images, identify 38 disease classes via custom model, generate care suggestions (Baidu AI multimodal)
- Weather integration: real-time weather and 3-day forecast (HeFeng API)
- Historical data: paginated query, trend analysis, Excel export (Apache POI)
- MCP protocol: WebSocket endpoint `/mcp`, JSON-RPC 2.0, provides `getPotData`, `getSwitchStates`, `setSwitchState` tools for AI agents
- XiaoZhi MCP bridge: transparent proxy to XiaoZhi MCP platform for remote AI agent control
- Community data sharing: REST API integration with healing-planet-sys for sensor-driven blog posts

## Frontends

| Application | Description | Tech |
|---|---|---|
| green-oasis-community | Community portal for plant lovers | Vue 3 + Vite + Element Plus + Pinia |
| smart-green-plant-website | Smart plant device dashboard | Vue 3 + Vite + Element Plus + Pinia + ECharts |
| Sprout-Admin | Admin management panel | Vue 3 + Vite + Element Plus + Pinia |

## Tech Stack Highlights

| Category | Technologies |
|---|---|
| Backend Framework | Spring Boot 2.6/2.7, Spring MVC, Spring WebFlux, Spring Mail |
| ORM | MyBatis-Plus (pagination, logical delete) |
| Database | MySQL 5.7+/8.x |
| Cache | Redis |
| Auth | JWT (admin + user dual secret) |
| Real-time | WebSocket (STOMP), SSE |
| AI | Baidu AI (ERNIE Bot, multimodal), custom disease detection model |
| IoT | Alibaba Cloud IoT SDK, Apache Qpid JMS (AMQP), MCP protocol |
| Storage | Alibaba Cloud OSS |
| Build | Maven (multi-module for healing-planet-sys) |

## Quick Start

### Prerequisites

- JDK 8, Maven 3.6+, MySQL, Redis
- Node.js 20.19+ or 22.12+

### Setup

```bash
# Clone
git clone https://github.com/waangzh/healing-planet.git
cd healing-planet

# Create databases
mysql -e "CREATE DATABASE green_community DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -e "CREATE DATABASE smart_agriculture DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

# Start Redis
redis-server
```

### Run Backends

```bash
# Community backend (port 8000)
cd SpringBoot后端/healing-planet-sys
mvn clean install && mvn -pl service -am spring-boot:run

# Smart plant backend (port 8070)
cd SpringBoot后端/smart_green_plant
mvn clean install && mvn spring-boot:run
```

### Run Frontends

```bash
# Community portal
cd VUE前端/green-oasis-community && npm install && npm run dev

# Plant dashboard
cd VUE前端/smart-green-plant-website && npm install && npm run dev

# Admin panel
cd VUE前端/Sprout-Admin && npm install && npm run dev
```

For detailed configuration (API keys, OSS credentials, IoT setup), see the [Chinese README](README.md).

---

**Healing Planet** — technology meets nature, plants warm the heart.
