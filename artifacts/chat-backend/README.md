# Chat Backend — Telegram-like Real-Time Chat API

A production-ready Java Spring Boot backend for a real-time chat application, supporting one-to-one and group messaging, JWT authentication, WebSocket/STOMP real-time delivery, and PostgreSQL persistence. Designed for horizontal scalability with clear integration points for Kafka and Redis.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Client Applications                          │
│              (React, Vue, Mobile App, Postman)                      │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ HTTPS / WSS
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     API Gateway / Load Balancer                     │
│                  (NGINX, AWS ALB, Kubernetes Ingress)               │
└──────────────┬──────────────────────────────────────────────────────┘
               │ Routes /api/* and /ws/*
               ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                         Spring Boot Application                              │
│                                                                              │
│  ┌────────────────┐   ┌────────────────────┐   ┌──────────────────────────┐ │
│  │ AuthController │   │   ChatController   │   │    UserController        │ │
│  │  /api/v1/auth  │   │  /api/v1/chats     │   │   /api/v1/users          │ │
│  │  (REST)        │   │  + @MessageMapping │   │   (REST)                 │ │
│  └───────┬────────┘   └────────┬───────────┘   └───────────┬──────────────┘ │
│          │                    │                             │                │
│          ▼                    ▼                             ▼                │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                         Service Layer                                 │  │
│  │  AuthService │ ChatService │ MessageService │ UserService              │  │
│  └──────────────────────────────────┬────────────────────────────────────┘  │
│                                     │                                        │
│          ┌──────────────────────────┼──────────────────────────────┐        │
│          ▼                          ▼                              ▼         │
│  ┌──────────────┐          ┌─────────────────┐          ┌──────────────────┐│
│  │  JPA Repos   │          │ SimpMessaging   │          │  JwtTokenProvider││
│  │  (Hibernate) │          │ Template (WS)   │          │  BCrypt Encoder  ││
│  └──────┬───────┘          └────────┬────────┘          └──────────────────┘│
│         │                           │                                        │
└─────────┼───────────────────────────┼────────────────────────────────────────┘
          │                           │
          ▼                           ▼
  ┌──────────────┐           ┌─────────────────────────────────┐
  │  PostgreSQL  │           │   WebSocket Clients (STOMP)     │
  │  Database    │           │   /user/queue/messages (DM)     │
  │  (Persists   │           │   /topic/group/{id} (Group)     │
  │   messages)  │           └─────────────────────────────────┘
  └──────────────┘


Security Layer (runs before every request):
  JWT Filter → Spring Security → SecurityContext → Controller
```

---

## Package Structure

```
src/main/java/com/chatapp/
├── ChatBackendApplication.java       # Entry point
│
├── config/
│   ├── SecurityConfig.java           # Spring Security + JWT filter chain
│   ├── WebSocketConfig.java          # STOMP broker + JWT authentication for WS
│   └── WebMvcConfig.java             # CORS configuration
│
├── controller/
│   ├── AuthController.java           # POST /api/v1/auth/register|login
│   ├── ChatController.java           # REST + STOMP @MessageMapping handlers
│   └── UserController.java           # GET /api/v1/users/*
│
├── service/
│   ├── AuthService.java              # Registration, login, JWT issuance
│   ├── ChatService.java              # Direct chat + group chat management
│   ├── MessageService.java           # Message persistence + WS delivery
│   └── UserService.java              # User profile + presence management
│
├── repository/
│   ├── UserRepository.java
│   ├── DirectChatRepository.java
│   ├── GroupChatRepository.java
│   └── MessageRepository.java
│
├── model/
│   ├── User.java                     # User entity
│   ├── DirectChat.java               # One-to-one chat entity
│   ├── GroupChat.java                # Group chat entity (Many-to-Many members)
│   ├── Message.java                  # Message entity (direct or group)
│   ├── MessageStatus.java            # SENT, DELIVERED, READ, FAILED
│   └── MessageType.java              # TEXT, IMAGE, VIDEO, FILE, SYSTEM
│
├── security/
│   ├── JwtTokenProvider.java         # JWT generation + validation
│   ├── JwtAuthenticationFilter.java  # Per-request JWT extraction + auth
│   ├── UserPrincipal.java            # Spring Security UserDetails wrapper
│   └── UserDetailsServiceImpl.java   # Loads User from DB by username
│
├── dto/
│   ├── request/
│   │   ├── RegisterRequest.java
│   │   ├── LoginRequest.java
│   │   ├── SendMessageRequest.java   # Used for REST and WebSocket payloads
│   │   └── CreateGroupRequest.java
│   └── response/
│       ├── AuthResponse.java
│       ├── UserResponse.java
│       ├── MessageResponse.java
│       ├── DirectChatResponse.java
│       ├── GroupChatResponse.java
│       └── ApiErrorResponse.java     # Standard error format
│
└── exception/
    ├── GlobalExceptionHandler.java   # @RestControllerAdvice — maps exceptions → HTTP
    ├── ResourceNotFoundException.java # 404
    ├── DuplicateResourceException.java # 409
    └── UnauthorizedException.java    # 401

src/test/java/com/chatapp/
└── service/
    ├── AuthServiceTest.java
    ├── MessageServiceTest.java
    └── ChatServiceTest.java
```

---

## Database Schema

```sql
-- Users
CREATE TABLE users (
    id           BIGSERIAL PRIMARY KEY,
    username     VARCHAR(50)  UNIQUE NOT NULL,
    email        VARCHAR(100) UNIQUE NOT NULL,
    password     VARCHAR(255) NOT NULL,          -- BCrypt hash
    display_name VARCHAR(255),
    avatar_url   VARCHAR(500),
    online       BOOLEAN NOT NULL DEFAULT false,
    created_at   TIMESTAMP NOT NULL,
    updated_at   TIMESTAMP NOT NULL
);

-- One-to-One Chats
CREATE TABLE direct_chats (
    id         BIGSERIAL PRIMARY KEY,
    user1_id   BIGINT NOT NULL REFERENCES users(id),
    user2_id   BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_direct_chat_users UNIQUE(user1_id, user2_id)
    -- user1_id always < user2_id (enforced in ChatService)
);

-- Group Chats
CREATE TABLE group_chats (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    avatar_url  VARCHAR(500),
    admin_id    BIGINT NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL
);

-- Group Membership (Many-to-Many)
CREATE TABLE group_chat_members (
    group_chat_id BIGINT NOT NULL REFERENCES group_chats(id),
    user_id       BIGINT NOT NULL REFERENCES users(id),
    PRIMARY KEY (group_chat_id, user_id)
);

-- Messages (shared for direct + group)
CREATE TABLE messages (
    id             BIGSERIAL PRIMARY KEY,
    content        VARCHAR(4096) NOT NULL,
    sender_id      BIGINT NOT NULL REFERENCES users(id),
    direct_chat_id BIGINT REFERENCES direct_chats(id),
    group_chat_id  BIGINT REFERENCES group_chats(id),
    status         VARCHAR(20) NOT NULL DEFAULT 'SENT',  -- SENT|DELIVERED|READ|FAILED
    type           VARCHAR(20) NOT NULL DEFAULT 'TEXT',   -- TEXT|IMAGE|VIDEO|FILE|SYSTEM
    deleted        BOOLEAN NOT NULL DEFAULT false,
    created_at     TIMESTAMP NOT NULL
);
```

---

## API Endpoints

### Authentication (Public)

| Method | Endpoint                      | Description               | Request Body                                    | Response               |
|--------|-------------------------------|---------------------------|-------------------------------------------------|------------------------|
| POST   | `/api/v1/auth/register`       | Register a new user       | `{username, email, password, displayName?}`     | `AuthResponse` (JWT)   |
| POST   | `/api/v1/auth/login`          | Login, get JWT            | `{username, password}`                          | `AuthResponse` (JWT)   |

### Users (Requires JWT)

| Method | Endpoint                      | Description               |
|--------|-------------------------------|---------------------------|
| GET    | `/api/v1/users/me`            | Get current user profile  |
| GET    | `/api/v1/users/{id}`          | Get any user by ID        |
| GET    | `/api/v1/users/search?q=`     | Search users by username  |

### Chats — Direct (Requires JWT)

| Method | Endpoint                                    | Description                              |
|--------|---------------------------------------------|------------------------------------------|
| GET    | `/api/v1/chats/direct`                      | List all direct chats (inbox)            |
| POST   | `/api/v1/chats/direct/{userId}`             | Start or retrieve a direct chat          |
| GET    | `/api/v1/chats/direct/{chatId}/messages`    | Get message history (paginated)          |

### Chats — Groups (Requires JWT)

| Method | Endpoint                                           | Description                         |
|--------|----------------------------------------------------|-------------------------------------|
| POST   | `/api/v1/chats/groups`                             | Create a group chat                 |
| GET    | `/api/v1/chats/groups`                             | List my group chats                 |
| GET    | `/api/v1/chats/groups/{groupId}`                   | Get group details                   |
| POST   | `/api/v1/chats/groups/{groupId}/members/{userId}`  | Add a member (admin only)           |
| DELETE | `/api/v1/chats/groups/{groupId}/members/{userId}`  | Remove a member / leave group       |
| GET    | `/api/v1/chats/groups/{groupId}/messages`          | Get group message history           |

### WebSocket (STOMP)

**Connect:** `ws://localhost:8080/ws`
Send JWT in the STOMP CONNECT frame header:
```
CONNECT
Authorization:Bearer <your-jwt-token>
```

**Send a direct message:**
```
SEND /app/chat/direct
Content-Type:application/json

{"recipientId": 2, "content": "Hello Bob!", "type": "TEXT"}
```

**Send a group message:**
```
SEND /app/chat/group
Content-Type:application/json

{"groupChatId": 5, "content": "Hey team!", "type": "TEXT"}
```

**Subscribe to receive direct messages:**
```
SUBSCRIBE /user/queue/messages
```

**Subscribe to a group:**
```
SUBSCRIBE /topic/group/5
```

---

## Setup Instructions

### Prerequisites
- Java 17+
- Maven 3.8+
- PostgreSQL 14+
- (Optional) Docker + Docker Compose

### 1. Clone and Configure

```bash
git clone <repo-url>
cd chat-backend
```

Create your PostgreSQL database:
```sql
CREATE DATABASE chatdb;
CREATE USER chatuser WITH PASSWORD 'chatpassword';
GRANT ALL PRIVILEGES ON DATABASE chatdb TO chatuser;
```

Configure environment variables (or edit `application.properties`):
```bash
export DB_URL=jdbc:postgresql://localhost:5432/chatdb
export DB_USERNAME=chatuser
export DB_PASSWORD=chatpassword
export JWT_SECRET=your-very-long-random-256-bit-secret-key
```

### 2. Run Locally

```bash
./mvnw spring-boot:run
```

The server starts on **http://localhost:8080**

### 3. Build and Run with Docker

```bash
# Build the image
docker build -t chat-backend:1.0.0 .

# Run with environment variables
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/chatdb \
  -e DB_USERNAME=chatuser \
  -e DB_PASSWORD=chatpassword \
  -e JWT_SECRET=your-256-bit-secret \
  chat-backend:1.0.0
```

### 4. Run Tests

```bash
./mvnw test
```

### 5. Deploy to Kubernetes

```bash
# Create the Secrets (replace with real base64-encoded values)
kubectl apply -f k8s/service.yaml   # Creates Secret, Service, Ingress
kubectl apply -f k8s/deployment.yaml # Creates Deployment + HPA

# Verify
kubectl get pods -l app=chat-backend
kubectl get hpa chat-backend-hpa
```

---

## Quick API Test

```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","password":"password123"}'

# Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123"}' | jq -r .accessToken)

# Get profile
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/users/me

# Create a group
curl -X POST http://localhost:8080/api/v1/chats/groups \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Dev Team","description":"Our dev group","memberIds":[2,3]}'
```

---

## Scaling to Millions of Users

### Current Architecture (Single Instance)

```
Client → Spring Boot → PostgreSQL
               ↕
        In-Memory STOMP Broker
```
Works well up to ~10K concurrent WebSocket connections per instance.

---

### Phase 1: Redis for WebSocket Scaling (Multiple Instances)

**Problem:** With 3+ Spring Boot instances, a message sent to Instance A can't reach a client connected to Instance B.

**Solution:** Replace the in-memory STOMP broker with a Redis pub/sub relay.

```properties
# application.properties additions
spring.data.redis.host=redis-host
spring.data.redis.port=6379
spring.data.redis.password=${REDIS_PASSWORD}
```

```java
// WebSocketConfig.java — replace enableSimpleBroker with:
config.enableStompBrokerRelay("/topic", "/queue")
      .setRelayHost("rabbitmq-host")  // or use Redis via RabbitMQ-STOMP plugin
      .setRelayPort(61613);
```

Additional Redis uses:
- **User caching:** `@Cacheable("users")` on `UserDetailsService` — eliminates DB lookup per JWT validation
- **Online presence:** Store `userId → online` in Redis with TTL (heartbeat extends TTL)
- **Unread counts:** Redis `INCR/DECR` per `user:chat:unread` key instead of COUNT SQL queries
- **Rate limiting:** Redis `INCR + EXPIRE` per `ip:endpoint` to prevent brute-force

```
Client → NGINX → Spring Boot (×3) → PostgreSQL (primary)
                    ↕ Redis pub/sub        ↓
                 Redis Cluster         Read Replica
```

---

### Phase 2: Kafka for Message Fanout at Scale

**Problem:** At 1M+ messages/day, synchronous DB writes in the request path become a bottleneck. Large groups (100K members) create a massive fanout problem.

**Solution:** Decouple ingestion from persistence and delivery using Kafka.

```
Message flow with Kafka:
Client → Spring Boot → Kafka topic "chat-messages"
                              ↓
              ┌───────────────┼─────────────────────┐
              ▼               ▼                     ▼
     Persistence        Notification          Delivery
      Consumer          Consumer              Consumer
    (write to DB)   (push notification)   (Redis pub/sub
                                          → WebSocket)
```

**Kafka configuration:**
```properties
# application.properties
spring.kafka.bootstrap-servers=kafka-broker-1:9092,kafka-broker-2:9092
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.consumer.group-id=chat-delivery-service
spring.kafka.consumer.auto-offset-reset=earliest
```

**Service changes:**
```java
// MessageService — publish to Kafka instead of direct WS delivery
@Autowired KafkaTemplate<String, MessageEvent> kafkaTemplate;

// Replace: messagingTemplate.convertAndSend(...)
// With:
kafkaTemplate.send("chat-messages", message.getId().toString(), new MessageEvent(message));
```

Topics design:
- `chat-messages` — all messages (partition by chatId for ordering)
- `user-presence` — online/offline events
- `message-delivered` — delivery receipts (consumed by status update service)
- `notifications` — for push notification service

---

### Phase 3: Full Microservices Split

Each service in its own Kubernetes Deployment, communicating via Kafka:

```
┌──────────────────────────────────────────────────────────────┐
│                     API Gateway (Kong / NGINX)               │
└──────┬─────────────┬────────────────┬────────────────────────┘
       │             │                │
       ▼             ▼                ▼
  auth-service   chat-service    user-service
  (JWT issuance) (REST + WS)    (profiles, search)
       │             │
       │         Kafka Cluster
       │         ┌───┴────────────────────┐
       │         ▼                        ▼
       │   message-service         notification-service
       │   (persistence)           (push notifications)
       │
  PostgreSQL (sharded by user_id range or consistent hash)
  Redis Cluster (caching, pub/sub, rate limiting)
  Elasticsearch (user search, message search)
```

**Database sharding strategy:**
- Shard by `user_id` ranges (users 1–1M on shard 1, 1M–2M on shard 2, etc.)
- Messages partitioned by `created_at` (monthly) in PostgreSQL
- Old messages archived to S3 + Parquet (query with Athena or Spark)

**Load balancing:**
- API Gateway routes by path (stateless HTTP → any instance)
- WebSocket: consistent-hash load balancing by `userId` → same instance pool
  (or use Redis pub/sub to eliminate stickiness requirement entirely)
- Database: read replicas for all SELECT queries; primary only for writes

---

## Technology Choices & Rationale

| Technology        | Choice          | Rationale                                                    |
|-------------------|-----------------|--------------------------------------------------------------|
| Framework         | Spring Boot 3.2 | Mature, production-battle-tested, rich ecosystem             |
| Authentication    | JWT (HS256)     | Stateless — no session store needed, scales horizontally     |
| Real-time         | WebSocket/STOMP | Native browser support, rich client libraries                |
| Database          | PostgreSQL      | ACID transactions, JSON support, excellent for relational data|
| ORM               | JPA/Hibernate   | Reduces boilerplate, migration-friendly with proper setup     |
| Passwords         | BCrypt (cost 12)| Adaptive hashing — increase cost as hardware improves        |
| Logging           | SLF4J + Logback | Standard Java logging facade; swap backend without code change|
| Validation        | Bean Validation | Declarative, integrates with Spring MVC automatically        |

---

## Security Considerations

- Passwords stored as BCrypt hashes only — plaintext never persisted
- JWT secrets stored in environment variables / Kubernetes Secrets (never in code)
- CSRF disabled (JWT is not cookie-based; immune to CSRF attacks)
- Stateless sessions — no server-side session state to hijack
- WebSocket STOMP authenticated at CONNECT frame level
- Input validation on all request bodies (`@Valid` + Jakarta Validation)
- Global exception handler never leaks internal details to clients
- Non-root container user in Dockerfile + read-only root filesystem in K8s
- SQL injection prevented by JPA parameterized queries (never string concatenation)

---

## Extending the System

| Feature                  | Where to add                                                     |
|--------------------------|------------------------------------------------------------------|
| Read receipts            | Add `READ` status + endpoint to mark messages read               |
| Push notifications       | Kafka consumer → Firebase Cloud Messaging / APNs                 |
| Media uploads            | Add `/api/v1/media/upload` → store in S3 + return URL            |
| Message search           | Elasticsearch consumer of Kafka `chat-messages` topic            |
| OAuth2 login             | Spring Security OAuth2 Client + provider config in SecurityConfig|
| Rate limiting            | Redis `INCR/EXPIRE` per IP/user in a filter or interceptor       |
| Admin dashboard          | Add `ROLE_ADMIN` + `@PreAuthorize("hasRole('ADMIN')")` endpoints |
| Message reactions        | New `MessageReaction` entity with Many-to-Many (User, Message)   |
| Typing indicators        | WebSocket-only: publish to `/topic/typing/{chatId}` with TTL     |
