# Healing Planet

> A smart plant care platform that brings together a plant community, IoT monitoring, and a traceable evidence-based AI assistant.

[中文](README.md) | [English](README.en.md)

## Overview

Healing Planet is a full-stack system consisting of:

- **3 backend service directories** — community services (port 8000), IoT device control sources (port 8070), and the JDK 17 AI/RAG service (port 8010)
- **3 Vue 3 web frontends** — community portal, plant dashboard, admin panel
- **1 WeChat Mini Program** — environmental monitoring, device control, AI assistant, plant identification
- **MCP protocol support** — AI agent integration for smart plant pot control

## Backend Services

### healing-planet-sys (Port 8000)

Community-focused backend providing:

- User registration, login (with captcha), profile management
- Post publishing, editing, deletion; comments, likes, bookmarks, follows
- Tag-based search, browse history
- Private messaging via WebSocket (`/chat/{userId}`) with read/unread status
- Plant encyclopedia with Redis caching
- "Xiao Lv" AI chat assistant (Baidu AI + SSE streaming + Markdown rendering)
- File upload (Alibaba Cloud OSS)
- Admin panel (user/content/tag/plant/billboard management; extensible)
- JWT authentication, global exception handling, CORS

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

The repository currently contains the IoT service sources and example application configuration, but no verifiable root `pom.xml` or `build.gradle`. Use the original deployment environment for this module rather than assuming the Maven commands used by the other services.

### healing-planet-ai (Port 8010)

The standalone AI service provides an Evidence-first RAG pipeline:

```text
Query Analysis -> Entity Resolution -> Retrieval Planning -> Broad Retrieval
-> Evidence Selection -> Answerability -> Generation
```

It combines dense retrieval, Lucene BM25, RRF, an optional reranker, source-aware ranking, plant entity resolution, community evidence, and on-demand current/history sensor state. Explicit source bans and security constraints remain hard; semantic domain/topic predictions are only planning and ranking signals. Every `REQUIRED` source must contribute relevant evidence, and retrieval, reranking, answerability, and generation share one immutable runtime configuration snapshot per request. The service is read-only with respect to IoT devices.

## Frontends

| Application | Description | Tech |
|---|---|---|
| green-oasis-community | Community portal for plant lovers | Vue 3 + Vite + Element Plus + Pinia |
| smart-green-plant-website | Smart plant device dashboard | Vue 3 + Vite + Element Plus + Pinia + ECharts |
| Sprout-Admin | Admin management panel | Vue 3 + Vite + Element Plus + Pinia |
| smart-green-plant-mini-program | WeChat Mini Program for plant monitoring & control | WeChat native + ECharts (ec-canvas) + custom Markdown parser |

## Mini Program

| Tab | Features |
|---|---|
| Dashboard | Real-time environmental data (temperature, humidity, soil moisture, CO2, light), 7-day ECharts trend charts, HeFeng weather (current + 3-day forecast) |
| Device Control | ESP8266 status monitoring, manual fan/pump/light switches, configurable threshold ranges (temperature, soil moisture, CO2, light) with auto-control toggle |
| Tools | AI chat assistant with Markdown rendering + local message cache; plant identification via photo upload |
| User Center | WeChat one-tap login, account/password login, profile editing (avatar, nickname, email, phone), password change |

The API base URL is configured via `utils/config.js` (copy from `utils/config.example.js` and fill in your server address). The `project.config.json` and `utils/config.js` are gitignored to avoid committing credentials.

## System Architecture

![System Architecture](docs/arc.png)

## Tech Stack Highlights

| Category | Technologies |
|---|---|
| Backend Framework | Spring Boot 2.6.x for the community service; Spring Boot 3.5.x / Spring AI 1.1.x for the AI service |
| ORM | MyBatis-Plus (pagination, logical delete) |
| Database | MySQL 5.7+/8.x |
| Cache | Redis |
| Auth | JWT (admin + user dual secret) |
| Real-time | WebSocket (STOMP), SSE |
| AI | Baidu AI (ERNIE Bot, multimodal), custom disease detection model |
| IoT | Alibaba Cloud IoT SDK, Apache Qpid JMS (AMQP), MCP protocol |
| Storage | Alibaba Cloud OSS |
| Mini Program | WeChat Native Framework (glass-easel), ECharts (ec-canvas), custom Markdown parser |
| Build | Maven (multi-module for healing-planet-sys) |

## Quick Start

### Prerequisites

- JDK 8 and Maven for the community service; JDK 17 and Maven 3.9+ recommended for the AI service
- Node.js; `Sprout-Admin` requires `^20.19.0 || >=22.12.0`
- npm for the community/admin frontends; pnpm for the smart plant website
- MySQL and Redis; Docker/Qdrant plus OpenAI-compatible chat and embedding APIs for the AI service

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

# AI/RAG backend (port 8010; inject environment variables from .env.example first)
cd ../healing-planet-ai
docker compose up -d qdrant
mvn spring-boot:run
```

No startup command is documented for `smart_green_plant` because this checkout does not contain a verifiable build descriptor for that service.

### Run Frontends

```bash
# Community portal
cd VUE前端/green-oasis-community && npm ci && npm run dev

# Plant dashboard
cd ../smart-green-plant-website && pnpm install --frozen-lockfile && pnpm run dev

# Admin panel
cd ../Sprout-Admin && npm ci && npm run dev
```

### Setup Mini Program

```bash
cd smart-green-plant-mini-program
# Copy and edit project config
cp project.config.example.json project.config.json
# Fill in your WeChat appid

# Copy and edit API config
cp smart-plant/utils/config.example.js smart-plant/utils/config.js
# Fill in your backend URLs
```
Then open with WeChat DevTools: **File -> Open Project -> select `smart-green-plant-mini-program/`**.

For detailed configuration (API keys, OSS credentials, IoT setup), see the [Chinese README](README.md).

---

**Healing Planet** — technology meets nature, plants warm the heart.
