# Portfolio Backend

Spring Boot API serving dynamic stats and rickroll counter for portfolio frontend.

## Tech Stack
- Java 17
- Spring Boot 4.0.2
- Maven
- Redis (optional - for persistent counter storage)
- Spring Boot Actuator (health checks)

## API Endpoints

### GET `/api/stats`
Returns current portfolio stats with dynamic song rotation.

```json
{
  "location": "Hyderabad",
  "game": "Counter Strike 2",
  "currentTime": "5:30",
  "timeStamp": 1234567890,
  "songName": "a banger",
  "songURL": "/audio/Funkadelic.mp3"
}
```

### GET `/api/rickroll`
Increments and returns the rickroll counter.

```json
{
  "count": 42
}
```

### GET `/api/rickroll/count`
Returns current rickroll count without incrementing.

```json
{
  "count": 42
}
```

### GET `/api/redis/test`
Tests Redis connectivity (useful for debugging).

```json
{
  "status": "success",
  "message": "Redis is connected",
  "test": "test:value"
}
```

### GET `/actuator/health`
Health check endpoint for monitoring and container orchestration.

```json
{
  "status": "UP"
}
```

## Features
- **Audio rotation**: 6 different songs rotating every 4 hours
- **Rickroll counter**: Tracks total rickroll clicks
- **Redis fallback**: Automatically falls back to in-memory storage if Redis unavailable
- **Static audio files**: Served from `/audio/`
- **CORS configured**: Ready for frontend integration
- **Health checks**: Spring Boot Actuator for monitoring

## Run Locally

### Without Redis (in-memory counter)
```bash
mvn spring-boot:run
```

### With Redis (persistent counter)
```bash
# Set Redis environment variables
export REDIS_HOST=your-host.upstash.io
export REDIS_PORT=33079
export REDIS_PASSWORD=your-password
export REDIS_SSL=true

mvn spring-boot:run
```

Server runs on `http://localhost:8081`

## Docker

### Build
```bash
docker build -t portfolio-backend .
```

### Run without Redis
```bash
docker run -p 8081:8081 portfolio-backend
```

### Run with Redis (Upstash)
```bash
docker run -p 8081:8081 \
  -e REDIS_HOST=your-host.upstash.io \
  -e REDIS_PORT=33079 \
  -e REDIS_PASSWORD=your-password \
  -e REDIS_SSL=true \
  portfolio-backend
```

## Configuration

### application.properties
```properties
# Server
server.port=8081

# Portfolio Stats
portfolio.location=Hyderabad
portfolio.game=Counter Strike 2
portfolio.songName=a banger

# Redis (optional - uses environment variables)
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.password=${REDIS_PASSWORD:}
spring.data.redis.ssl.enabled=${REDIS_SSL:false}

# Actuator
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=always
```

## Redis Setup (Optional)

The rickroll counter works in two modes:

### 1. In-Memory Mode (Default)
- No Redis required
- Counter resets on application restart
- Perfect for development and testing

### 2. Redis Mode (Persistent)
- Counter persists across restarts
- Supports horizontal scaling
- Recommended for production

**To enable Redis:**
1. Create free Redis database at [Upstash](https://upstash.com/) or [Redis Cloud](https://redis.com/try-free/)
2. Set environment variables (see above)
3. Application automatically uses Redis when available

See `UPSTASH_REDIS_SETUP.md` for detailed Redis setup instructions.

## Deployment

### AWS EC2
See `AWS_DEPLOYMENT_GUIDE.md` for complete deployment instructions.

### Docker Compose
```bash
cd /path/to/STS-Workspace
docker-compose up -d
```

See `docker-compose.yml` in parent directory.

## Audio Files

Static audio files rotate every 4 hours:
- **12am - 4am**: Funkadelic.mp3
- **4am - 8am**: Lawrie.mp3
- **8am - 12pm**: Aladeeeeen.mp3
- **12pm - 4pm**: ManMazeUmalunGele.mp3
- **4pm - 8pm**: EverybodyWantsToRuleTheWorldJoshGad.mp3
- **8pm - 12am**: miMorchaNelaNahi.mp3

Files are served from `/audio/` path.

## Development

### Requirements
- Java 17 or higher
- Maven 3.6+
- (Optional) Redis 6.0+ or Upstash account

### Build
```bash
mvn clean package
```

### Run tests
```bash
mvn test
```

### Run with dev tools (hot reload)
```bash
mvn spring-boot:run
```

## Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `SERVER_PORT` | Application port | 8081 | No |
| `REDIS_HOST` | Redis hostname | localhost | No |
| `REDIS_PORT` | Redis port | 6379 | No |
| `REDIS_PASSWORD` | Redis password | (empty) | No |
| `REDIS_SSL` | Enable Redis SSL | false | No |

## Troubleshooting

### Redis Connection Issues
- Check logs for "using in-memory" message - app will work with fallback
- Verify Redis credentials are correct
- Test connectivity: `curl http://localhost:8081/api/redis/test`

### Port Already in Use
```bash
# Change port
export SERVER_PORT=8082
mvn spring-boot:run
```

### Build Issues
```bash
# Clean and rebuild
mvn clean install -U
```

## License
Portfolio project - Free to use

## Author
Sushrut
