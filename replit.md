# Workspace

## Overview

pnpm workspace monorepo using TypeScript. Each package manages its own dependencies.
Also contains a Java Spring Boot backend project for real-time chat.

## Stack

- **Monorepo tool**: pnpm workspaces
- **Node.js version**: 24
- **Package manager**: pnpm
- **TypeScript version**: 5.9
- **API framework**: Express 5
- **Database**: PostgreSQL + Drizzle ORM
- **Validation**: Zod (`zod/v4`), `drizzle-zod`
- **API codegen**: Orval (from OpenAPI spec)
- **Build**: esbuild (CJS bundle)

## Key Commands

- `pnpm run typecheck` — full typecheck across all packages
- `pnpm run build` — typecheck + build all packages
- `pnpm --filter @workspace/api-spec run codegen` — regenerate API hooks and Zod schemas from OpenAPI spec
- `pnpm --filter @workspace/db run push` — push DB schema changes (dev only)
- `pnpm --filter @workspace/api-server run dev` — run API server locally

See the `pnpm-workspace` skill for workspace structure, TypeScript setup, and package details.

## Java Spring Boot Chat Backend

Located at: `artifacts/chat-backend/`

A complete Telegram-like real-time chat backend. See `artifacts/chat-backend/README.md` for full docs.

### Tech Stack
- **Java 17** + Spring Boot 3.2
- **Spring Security** + JWT (jjwt 0.11.5)
- **WebSocket** + STOMP protocol for real-time messaging
- **JPA/Hibernate** + PostgreSQL
- **Lombok** for boilerplate reduction
- **BCrypt** for password hashing

### Architecture: Controller → Service → Repository

| Layer | Classes |
|---|---|
| Controller | AuthController, ChatController, UserController |
| Service | AuthService, ChatService, MessageService, UserService |
| Repository | UserRepository, DirectChatRepository, GroupChatRepository, MessageRepository |
| Security | JwtTokenProvider, JwtAuthenticationFilter, UserDetailsServiceImpl |
| Model | User, DirectChat, GroupChat, Message, MessageStatus, MessageType |

### Running Locally
```bash
cd artifacts/chat-backend
export DB_URL=jdbc:postgresql://localhost:5432/chatdb
export DB_USERNAME=chatuser
export DB_PASSWORD=chatpassword
export JWT_SECRET=your-256-bit-secret
./mvnw spring-boot:run
```

### Infrastructure Files
- `Dockerfile` — Multi-stage build (JDK build stage, JRE runtime stage)
- `k8s/deployment.yaml` — K8s Deployment + HorizontalPodAutoscaler
- `k8s/service.yaml` — K8s Service + Secret template + Ingress
