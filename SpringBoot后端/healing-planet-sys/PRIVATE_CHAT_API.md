# 私聊功能接口文档

## 1. 功能说明

本次在现有项目上补齐了用户私聊能力，包含：

- WebSocket 实时收发
- 消息持久化到数据库
- 会话列表查询
- 历史消息分页查询
- 会话已读
- 全量未读数查询

## 2. 鉴权说明

- HTTP 接口需要携带 `Authorization` 请求头（登录后 token）。
- 通过现有 JWT 过滤器校验后，服务端会自动注入 `userName` 请求头。
- 私聊 HTTP 接口路径统一前缀：`/chat/private/**`。

## 3. WebSocket 协议

### 3.1 连接地址

`ws://{host}:{port}/chat/{userId}`

示例：

`ws://localhost:8000/chat/1912345678901234567`

### 3.2 连接成功回包

```json
{"type":"system","message":"connected"}
```

### 3.3 客户端发送消息格式

```json
{
  "toUserId": "1912345678901234568",
  "content": "你好，今天浇水了吗？"
}
```

### 3.4 服务端推送消息格式

```json
{
  "type": "private_message",
  "data": {
    "id": "1912400000000000001",
    "fromUserId": "1912345678901234567",
    "fromUsername": "alice",
    "fromAlias": "Alice",
    "fromAvatar": "https://xxx/a.png",
    "toUserId": "1912345678901234568",
    "toUsername": "bob",
    "toAlias": "Bob",
    "toAvatar": "https://xxx/b.png",
    "content": "你好，今天浇水了吗？",
    "isRead": false,
    "createdAt": "2026-03-26 21:30:00",
    "readAt": null
  }
}
```

说明：

- 消息会同时推送给发送方和接收方在线连接（用于双方前端实时刷新）。
- 若接收方离线，消息已落库，可通过历史接口补拉。

## 4. HTTP 接口

统一返回结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

---

### 4.1 发送私聊消息

- Method: `POST`
- Path: `/chat/private/send`
- Header: `Authorization: {token}`
- Header(服务端注入): `userName`

请求体：

```json
{
  "toUserId": "1912345678901234568",
  "content": "你好"
}
```

响应 `data`：`PrivateMessageVO`

---

### 4.2 获取会话列表

- Method: `GET`
- Path: `/chat/private/sessions`
- Header: `Authorization: {token}`

响应 `data`：`List<PrivateChatSessionVO>`

字段说明：

- `peerUserId`：会话对端用户 ID
- `peerUsername`：会话对端用户名
- `peerAlias`：会话对端昵称
- `peerAvatar`：会话对端头像
- `lastMessage`：最近一条消息内容
- `lastMessageTime`：最近一条消息时间
- `unreadCount`：该会话未读数

---

### 4.3 获取会话消息历史（分页）

- Method: `GET`
- Path: `/chat/private/messages`
- Header: `Authorization: {token}`

Query 参数：

- `peerUserId`：对端用户 ID（必填）
- `pageNo`：页码，默认 `1`
- `size`：每页条数，默认 `20`

响应 `data`：`Page<PrivateMessageVO>`

分页结构示例：

```json
{
  "records": [],
  "total": 125,
  "size": 20,
  "current": 1
}
```

排序规则：按 `createdAt` 倒序（最新消息在前）。

---

### 4.4 标记会话已读

- Method: `PUT`
- Path: `/chat/private/read`
- Header: `Authorization: {token}`

Query 参数：

- `peerUserId`：对端用户 ID（必填）

响应 `data`：`Integer`（本次更新为已读的消息条数）

---

### 4.5 获取当前用户私聊总未读数

- Method: `GET`
- Path: `/chat/private/unread/count`
- Header: `Authorization: {token}`

响应 `data`：`Integer`

## 5. 数据库表结构

新增表：`private_message`

```sql
CREATE TABLE `private_message` (
  `id` varchar(64) NOT NULL,
  `from_user_id` varchar(64) NOT NULL COMMENT '发送方用户ID',
  `to_user_id` varchar(64) NOT NULL COMMENT '接收方用户ID',
  `content` varchar(2000) NOT NULL COMMENT '消息内容',
  `is_read` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已读 0-未读 1-已读',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `read_at` datetime DEFAULT NULL COMMENT '已读时间',
  PRIMARY KEY (`id`),
  KEY `idx_pm_from_to_time` (`from_user_id`,`to_user_id`,`created_at`),
  KEY `idx_pm_to_read` (`to_user_id`,`is_read`),
  KEY `idx_pm_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户私聊消息表';
```

## 6. 联调建议

1. 用户 A、B 登录，分别拿到 token 和用户 ID。
2. A、B 分别建立 WebSocket 连接：`/chat/{userId}`。
3. A 调用 HTTP 发送接口或直接发 WebSocket 消息。
4. B 在线时应实时收到 `type=private_message` 推送。
5. B 离线时，重新上线后调用历史接口补拉。
6. B 查看后调用已读接口，再刷新会话列表确认 `unreadCount` 变化。
