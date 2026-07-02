# Build & Run Guide

## Prerequisites

- **Java 21+**
  ```bash
  java -version
  # Expected: openjdk 21.x.x
  ```

- **Maven 3.8+**
  ```bash
  mvn -version
  # Expected: Apache Maven 3.8.x or higher
  ```

- **Docker & Docker Compose** (optional, for containerized deployment)
  ```bash
  docker --version
  docker-compose --version
  ```

## Build from Source

### Step 1: Clone or download the project

```bash
cd market-data-platform
```

### Step 2: Build with Maven

```bash
# Full build with tests
mvn clean package

# Build without tests (faster)
mvn clean package -DskipTests

# Build with specific profile
mvn clean package -P prod
```

**Build Output:**
```
[INFO] Building jar: target/market-data-platform-1.0.0.jar
[INFO] BUILD SUCCESS
Total time: 45s
```

### Step 3: Run Locally

```bash
# Option A: Direct from JAR
java -jar target/market-data-platform-1.0.0.jar

# Option B: With custom JVM options
java -Xms512m -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -jar target/market-data-platform-1.0.0.jar

# Option C: With Spring profile
java -Dspring.profiles.active=dev \
  -jar target/market-data-platform-1.0.0.jar
```

**Expected Output:**
```
2024-01-15 10:30:42.123 [main] INFO MarketDataPlatformApplication - Starting MarketDataPlatformApplication
2024-01-15 10:30:43.456 [main] INFO ApplicationStartup - Starting Market Data Platform
2024-01-15 10:30:43.789 [main] INFO LinkedEventBus - Starting EventBus with 4 worker threads
2024-01-15 10:30:44.000 [main] INFO BinanceWebSocketClient - Connecting to WebSocket: wss://stream.binance.com:9443/ws
2024-01-15 10:30:44.500 [main] INFO BinanceWebSocketClient - Connected to WebSocket successfully
2024-01-15 10:30:44.600 [main] INFO Tomcat - Tomcat started on port(s): 8080
```

## API Testing

### Get Prices

```bash
curl http://localhost:8080/api/market/prices | jq
```

**Response:**
```json
[
  {
    "symbol": "BTCUSDT",
    "price": 99500.50,
    "bid": 99490.00,
    "ask": 99510.00,
    "spread": 20.00,
    "midPrice": 99500.00,
    "timestamp": "2024-01-15T10:30:45.000Z",
    "latencyMs": 45
  }
]
```

### Get Order Book

```bash
# Top 10 levels
curl http://localhost:8080/api/market/orderbook/BTCUSDT?depth=10 | jq

# Top 50 levels
curl http://localhost:8080/api/market/orderbook/BTCUSDT?depth=50 | jq
```

### Get Recent Trades

```bash
# Last 100 trades
curl http://localhost:8080/api/market/trades/BTCUSDT | jq

# Last 50 trades
curl http://localhost:8080/api/market/trades/BTCUSDT?limit=50 | jq

# Latest trade
curl http://localhost:8080/api/market/trades/BTCUSDT/latest | jq
```

### Health Check

```bash
curl http://localhost:8080/api/health | jq
```

**Response:**
```json
{
  "status": "UP",
  "timestamp": "2024-01-15T10:30:45.000Z",
  "services": {
    "websocket": true,
    "eventbus": true
  },
  "details": {
    "ws_connection_duration_ms": "45000",
    "ws_last_message_ms_ago": "45",
    "eventbus_queue_size": "2",
    "eventbus_subscribers": "3"
  },
  "uptime": 45000,
  "messagesProcessed": 1234
}
```

### Prometheus Metrics

```bash
curl http://localhost:8080/actuator/prometheus | grep market
```

## Docker Deployment

### Build Docker Image

```bash
docker build -t market-data-platform:latest .

# With tag
docker build -t market-data-platform:1.0.0 .
```

### Run Docker Container

```bash
# Basic run
docker run -p 8080:8080 market-data-platform:latest

# With memory limits
docker run -p 8080:8080 \
  -m 2g \
  --cpus 2 \
  market-data-platform:latest

# With environment variables
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e WEBSOCKET_URL=wss://stream.binance.com:9443/ws \
  market-data-platform:latest

# With logging
docker run -p 8080:8080 \
  --log-driver json-file \
  --log-opt max-size=10m \
  --log-opt max-file=3 \
  market-data-platform:latest
```

### Docker Compose Stack

```bash
# Start all services
docker-compose up -d

# Monitor logs
docker-compose logs -f app

# Check status
docker-compose ps

# Stop all services
docker-compose down

# Clean up everything
docker-compose down -v
```

**Services:**
- `app`: Market Data Platform (port 8080)
- `prometheus`: Prometheus metrics (port 9090)
- `grafana`: Grafana dashboards (port 3000)
- `zookeeper`: Kafka Zookeeper (optional)
- `kafka`: Kafka broker (optional)
- `redis`: Redis cache (optional)
- `mongodb`: MongoDB database (optional)
- `mysql`: MySQL database (optional)

## IDE Setup

### IntelliJ IDEA

1. Open project folder
2. Mark as Maven project
3. Maven → Reimport
4. Run → Edit Configurations
5. Add "Application" configuration:
   - Main class: `com.market.data.MarketDataPlatformApplication`
   - JVM options: `-Xms512m -Xmx2g`
   - Environment: `SPRING_PROFILES_ACTIVE=dev`

### VS Code

1. Install extensions:
   - Extension Pack for Java
   - Maven for Java
   - REST Client

2. Debug launch configuration (`.vscode/launch.json`):
   ```json
   {
     "configurations": [
       {
         "type": "java",
         "name": "Launch Market Data Platform",
         "mainClass": "com.market.data.MarketDataPlatformApplication",
         "preLaunchTask": "mvn: clean compile"
       }
     ]
   }
   ```

## Testing

### Run All Tests

```bash
mvn test
```

### Run Specific Test Class

```bash
mvn test -Dtest=LinkedEventBusTest
```

### Run with Coverage

```bash
mvn clean test jacoco:report
# Report: target/site/jacoco/index.html
```

### Load Test

```bash
# Simulate 1M messages
# See src/test/java/.../LoadTest.java

mvn test -Dtest=LoadTest
```

## Troubleshooting

### Issue: Connection refused to WebSocket

**Cause**: WebSocket URL is unreachable

**Solution**:
```bash
# Check internet connection
ping stream.binance.com

# Update application.yml
websocket:
  url: wss://stream.binance.com:9443/ws
  connection-timeout-ms: 10000
```

### Issue: Memory exhaustion

**Cause**: Event queue filling up, orders book too large

**Solution**:
```bash
# Increase heap size
java -Xmx4g -jar target/market-data-platform-1.0.0.jar

# Check event bus queue size
curl http://localhost:8080/actuator/prometheus | grep eventbus_queue_size

# Increase queue capacity in LinkedEventBus
QUEUE_CAPACITY = 100000  // Adjust in LinkedEventBus.java
```

### Issue: High CPU usage

**Cause**: Busy waiting in event processing

**Solution**:
```bash
# Check which consumer is slow
curl http://localhost:8080/actuator/prometheus | grep eventbus_dispatch_latency

# Profile with JFR
java -XX:+UnlockCommercialFeatures -XX:+FlightRecorder \
  -XX:StartFlightRecording=delay=5s,duration=60s,filename=profile.jfr \
  -jar target/market-data-platform-1.0.0.jar
```

### Issue: Tests failing locally but passing on CI

**Cause**: Timing issues in async tests

**Solution**:
```bash
# Run with increased timeout
mvn test -DargLine="-Dtest.timeout=30000"

# Run single test multiple times
mvn test -Dtest=LinkedEventBusTest -Drepeat=10
```

## Performance Tuning

### JVM Tuning

```bash
# G1GC (recommended for latency-sensitive apps)
java -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:InitiatingHeapOccupancyPercent=35 \
  -XX:+PrintGCDetails \
  -jar target/market-data-platform-1.0.0.jar

# ZGC (ultra-low latency)
java -XX:+UseZGC \
  -XX:ZUncommitDelay=30 \
  -jar target/market-data-platform-1.0.0.jar

# Throughput tuning
java -XX:+UseParallelGC \
  -XX:ParallelGCThreads=8 \
  -jar target/market-data-platform-1.0.0.jar
```

### Application Tuning

```yaml
# application.yml
websocket:
  read-timeout-ms: 30000        # Increase if network is slow
  heartbeat-interval-ms: 30000  # Monitor connection health
  max-reconnect-attempts: 10    # Failover strategy

eventbus:
  queue-capacity: 100000        # Backpressure buffer
  worker-threads: 8             # Match CPU cores
```

## Production Deployment Checklist

- [ ] Java 21 installed on server
- [ ] 2+ CPU cores allocated
- [ ] 2-4GB RAM allocated
- [ ] Network connectivity to Binance WebSocket
- [ ] Prometheus configured for metrics scraping
- [ ] Grafana dashboards imported
- [ ] Log aggregation configured (ELK, Datadog, etc.)
- [ ] Alerting rules configured
- [ ] Health check endpoint monitored
- [ ] Docker images pushed to registry
- [ ] Database backups configured (if persisting data)
- [ ] SSL/TLS certificates installed (if using HTTPS API)

---

**Ready to run!** Your Market Data Platform is now ready for development or production deployment.
