# Non-Functional Requirements & Constraints

## 1. Introduction

### 1.1 Document Purpose
This document specifies the non-functional requirements (NFRs) and constraints for the CICS Bank Sample Application (CBSA) modernization effort. It covers performance, security, reliability, maintainability, compliance, and technology stack requirements based on analysis of the existing system and industry best practices.

**Source**: Analysis of COBOL programs, CICS configuration in `BANK.csd`, and architecture documentation

### 1.2 NFR Categories
This document addresses:
- Performance and scalability requirements
- Security and access control
- Transaction integrity and data consistency
- Audit and compliance logging
- Error handling and recovery
- Concurrency and locking
- Availability and reliability
- Maintainability and supportability
- Technology stack requirements
- Modernization considerations

---

## 2. Performance Requirements

### 2.1 Response Time Requirements

#### 2.1.1 Current System Baseline
⚠️ **Unknown**: Exact current performance metrics (need to measure)

**Typical expectations for online banking systems**:
- Inquiry operations (read): < 1 second
- Update operations (write): < 2 seconds
- Transfer operations: < 3 seconds
- Create operations: < 3 seconds (excluding credit checks)

#### 2.1.2 Target Requirements for Modern System

| Operation Type | Target Response Time | Percentile | Notes |
|----------------|---------------------|------------|-------|
| Customer inquiry | < 200ms | p95 | Simple database read |
| Account inquiry | < 200ms | p95 | Simple database read |
| Account lookup by customer | < 300ms | p95 | Query with small result set (< 10 accounts) |
| Create customer | < 2000ms | p95 | Includes async credit checks (3s timeout) |
| Create account | < 500ms | p95 | Includes counter, validation, inserts |
| Update customer | < 300ms | p95 | Database update with lock |
| Update account | < 300ms | p95 | Database update with lock |
| Debit/credit transaction | < 500ms | p95 | Update with validation, logging |
| Transfer funds | < 800ms | p95 | Two account updates, logging |
| Delete operations | < 500ms | p95 | Validation, delete, logging |

**Source**: Industry standards for online banking systems

#### 2.1.3 Performance Acceptance Criteria
- **AC1**: 95% of inquiry requests complete in < 200ms under normal load
- **AC2**: 95% of transaction requests complete in < 500ms under normal load
- **AC3**: 99% of all requests complete in < 2000ms under normal load
- **AC4**: No request times out before 30 seconds
- **AC5**: System maintains performance targets under load (see 2.2)

### 2.2 Throughput Requirements

#### 2.2.1 Current System Capacity
**CICS Configuration Constraints**:
- VSAM STRINGS(20): Maximum 20 concurrent readers per file
- Fixed number of CICS tasks based on region configuration
- Single-threaded COBOL programs

**Source**: `BANK.csd` lines 10-34

#### 2.2.2 Target Throughput

| Metric | Target | Notes |
|--------|--------|-------|
| Peak transactions per second (TPS) | 1,000 TPS | Across all operation types |
| Sustained TPS | 500 TPS | Normal business hours |
| Concurrent users | 10,000 | Simultaneous sessions |
| Database connections | 100-200 | Connection pool size |
| API requests per minute | 60,000 | 1,000 TPS × 60 seconds |

#### 2.2.3 Scalability Requirements
- **NFR-PERF-001**: System must horizontally scale to handle increased load
  - Add application instances without code changes
  - Stateless architecture to support load balancing
  - Database read replicas for read-heavy workloads

- **NFR-PERF-002**: System must handle 2x peak load during promotional periods
  - Auto-scaling policies based on CPU, memory, request rate
  - Circuit breakers to protect downstream dependencies

- **NFR-PERF-003**: Database must support growth to 10M customers, 50M accounts, 1B transactions
  - Partitioning strategy for large tables
  - Archive strategy for historical data

### 2.3 Resource Utilization

#### 2.3.1 Database Performance
- **Connection Pooling**: HikariCP with min=10, max=100 connections
- **Query Performance**: All queries < 100ms, use EXPLAIN ANALYZE to verify
- **Indexes**: Proper indexes on foreign keys, frequently queried columns
- **Caching**: Redis cache for frequently accessed data (customers, account metadata)

**Cache Strategy**:
```
Customer records: TTL 15 minutes (moderate change frequency)
Account metadata: TTL 5 minutes (rates/limits change occasionally)
Account balances: NO CACHE (constantly changing)
Transaction history: NO CACHE (append-only, large volume)
```

#### 2.3.2 Application Performance
- **JVM Tuning**: -Xms2g -Xmx4g, G1GC for low-latency
- **Thread Pools**: Dedicated pools for I/O vs CPU-bound operations
- **Async Processing**: Use @Async for non-critical paths (logging, notifications)

---

## 3. Security Requirements

### 3.1 Authentication

#### 3.1.1 Current System
**CICS Security**:
- User IDs authenticated by CICS/RACF
- Transaction-level security (which users can run which transactions)
- Resource-level security (file access, program execution)

**Source**: `BANK.csd` RESSEC and CMDSEC attributes

#### 3.1.2 Modern System Requirements

**NFR-SEC-001**: Multi-Factor Authentication (MFA)
- All user access requires MFA (TOTP, SMS, or push notification)
- Admin/privileged accounts require stronger MFA (hardware token)
- Session timeout after 15 minutes of inactivity

**NFR-SEC-002**: JWT-Based Authentication
```java
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .authorizeRequests()
                .antMatchers("/api/public/**").permitAll()
                .antMatchers("/api/admin/**").hasRole("ADMIN")
                .antMatchers("/api/**").authenticated()
            .and()
            .oauth2ResourceServer()
                .jwt();
        
        return http.build();
    }
}
```

**Token Requirements**:
- Algorithm: RS256 (RSA signature)
- Token expiry: 1 hour
- Refresh token expiry: 7 days
- Include user roles and permissions in claims

**NFR-SEC-003**: Password Requirements
- Minimum 12 characters
- Must include uppercase, lowercase, digit, special character
- Cannot reuse last 10 passwords
- Must change every 90 days
- Account lockout after 5 failed attempts

### 3.2 Authorization

**NFR-SEC-004**: Role-Based Access Control (RBAC)

| Role | Permissions |
|------|-------------|
| TELLER | Create/update customers, Create/update accounts, Debit/credit/transfer |
| MANAGER | All TELLER permissions + Delete customers/accounts, View audit logs |
| AUDITOR | Read-only access to all data, View audit logs, Generate reports |
| ADMIN | All permissions + User management, System configuration |

**Implementation**:
```java
@PreAuthorize("hasRole('TELLER')")
@PostMapping("/api/accounts")
public ResponseEntity<AccountDTO> createAccount(...) { }

@PreAuthorize("hasRole('MANAGER')")
@DeleteMapping("/api/customers/{id}")
public ResponseEntity<Void> deleteCustomer(...) { }

@PreAuthorize("hasAnyRole('AUDITOR', 'MANAGER', 'ADMIN')")
@GetMapping("/api/audit/transactions")
public ResponseEntity<List<TransactionDTO>> getAuditLog(...) { }
```

**NFR-SEC-005**: Fine-Grained Authorization
- Field-level security: Hide sensitive fields (SSN, account numbers) based on role
- Row-level security: Users can only access customers/accounts they created or are assigned to
- Operation-level security: Separate permissions for read vs write operations

### 3.3 Data Protection

**NFR-SEC-006**: Encryption at Rest
- All sensitive PII encrypted in database
- Encryption algorithm: AES-256
- Key management via KMS (AWS KMS, HashiCorp Vault)
- Rotate encryption keys annually

**Fields to Encrypt**:
- Customer name, address, DOB
- Account numbers
- Transaction descriptions (may contain PII)

**NFR-SEC-007**: Encryption in Transit
- All communication uses TLS 1.3
- Certificate pinning for mobile apps
- HTTPS only (no HTTP endpoints)
- Internal service-to-service communication also encrypted

**NFR-SEC-008**: Data Masking
- Logs never contain PII in plaintext
- API responses mask sensitive data (e.g., account number shows last 4 digits only)
- Audit logs use hashed identifiers

**Example**:
```java
@JsonSerialize(using = AccountNumberSerializer.class)
private String accountNumber; // Serializes as "****5678"

// In logs
logger.info("Customer inquiry", 
    kv("customerId", hashCustomerId(customerId)), // Hashed
    kv("accountNumber", "****" + accountNumber.substring(4)) // Masked
);
```

**NFR-SEC-009**: Secure Secrets Management
- No secrets in code or environment variables
- Use secrets manager (AWS Secrets Manager, Vault)
- Rotate secrets regularly
- Audit secret access

### 3.4 API Security

**NFR-SEC-010**: Rate Limiting
- 100 requests per minute per user
- 1,000 requests per minute per organization
- 10,000 requests per minute globally
- Return HTTP 429 Too Many Requests when exceeded

**NFR-SEC-011**: Input Validation
- Validate all inputs at API boundary
- Use Bean Validation annotations
- Sanitize inputs to prevent injection attacks
- Reject requests with unexpected fields (fail closed)

**NFR-SEC-012**: CORS Policy
- Restrict origins to known domains only
- No wildcard (*) allowed in production
- Include credentials only for authenticated requests

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("https://bank.example.com", "https://admin.bank.example.com")
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

**NFR-SEC-013**: Security Headers
- Content-Security-Policy
- X-Content-Type-Options: nosniff
- X-Frame-Options: DENY
- Strict-Transport-Security: max-age=31536000; includeSubDomains

---

## 4. Transaction Integrity

### 4.1 ACID Properties

**NFR-ACID-001**: Atomicity
- All database operations within a transaction succeed or fail together
- Use @Transactional with proper propagation
- Rollback on any exception

**Example** (Transfer funds):
```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public TransferReceipt transferFunds(TransferRequest request) {
    // If any operation fails, all changes are rolled back
    Account fromAccount = lockAccount(request.getFromAccountId());
    Account toAccount = lockAccount(request.getToAccountId());
    
    debitAccount(fromAccount, request.getAmount()); // May throw exception
    creditAccount(toAccount, request.getAmount());  // May throw exception
    logTransaction(fromAccount, toAccount, request.getAmount()); // May throw exception
    
    // All succeed or all rolled back
}
```

**Source**: XFRFUN uses SYNCPOINT ROLLBACK for rollback in COBOL (`XFRFUN.cbl` lines 1240-1335)

**NFR-ACID-002**: Consistency
- Database constraints enforced (foreign keys, check constraints, unique indexes)
- Application-level validation before database operations
- Invariants maintained (e.g., total debits = total credits for transfers)

**NFR-ACID-003**: Isolation
- Use appropriate isolation level based on operation
  - READ_COMMITTED: Default for most operations
  - REPEATABLE_READ: For financial calculations requiring consistency
  - SERIALIZABLE: For critical operations (rare, impacts performance)
- Pessimistic locking for balance updates (SELECT FOR UPDATE)
- Optimistic locking for metadata updates (@Version)

**NFR-ACID-004**: Durability
- All committed transactions persisted to disk
- Database write-ahead logging (WAL) enabled
- Regular backups (daily full, hourly incremental)
- Point-in-time recovery capability

### 4.2 Concurrency Control

#### 4.2.1 Current System
**CICS/COBOL Patterns**:
- Named Counter ENQ/DEQ for serialized ID generation
- VSAM record locking (UPDATE intent)
- Db2 row-level locking (SELECT FOR UPDATE)
- Ordered locking in XFRFUN to prevent deadlocks

**Source**: `CREACC.cbl` lines 447-683 (ENQ/DEQ), `XFRFUN.cbl` lines 486-898 (ordered locking)

#### 4.2.2 Modern System Requirements

**NFR-CONC-001**: Pessimistic Locking for Balance Updates
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM Account a WHERE a.id = :id")
Optional<Account> findByIdForUpdate(@Param("id") Long id);
```

**Use Cases**:
- Debit/credit operations
- Fund transfers
- Any operation that modifies balances

**NFR-CONC-002**: Optimistic Locking for Metadata Updates
```java
@Entity
public class Account {
    @Version
    private Long version;
    
    // If another transaction updates this entity first,
    // OptimisticLockException is thrown on commit
}
```

**Use Cases**:
- Update account type, interest rate, overdraft
- Update customer name, address
- Low-contention updates

**NFR-CONC-003**: Deadlock Prevention
- Always acquire locks in consistent order (by entity ID)
- Use database deadlock detection and retry
- Timeout locks after 10 seconds

```java
@Retryable(
    value = {DeadlockLoserDataAccessException.class},
    maxAttempts = 10,
    backoff = @Backoff(delay = 100)
)
public void transferFunds(TransferRequest request) {
    // Lock accounts in ID order to prevent deadlock
    List<Long> ids = Arrays.asList(fromAccountId, toAccountId);
    Collections.sort(ids);
    
    for (Long id : ids) {
        accountRepository.findByIdForUpdate(id);
    }
    // ... proceed with transfer
}
```

**Source**: XFRFUN implements ordered locking to prevent Db2 deadlocks

**NFR-CONC-004**: Idempotency
- All write operations must be idempotent (can be retried safely)
- Use idempotency keys for critical operations (transfers, creates)
- Store idempotency key with transaction record

```java
@PostMapping("/api/transfers")
public ResponseEntity<TransferReceipt> transfer(
    @RequestBody TransferRequest request,
    @RequestHeader("X-Idempotency-Key") String key
) {
    Optional<Transfer> existing = transferRepository.findByIdempotencyKey(key);
    if (existing.isPresent()) {
        return ResponseEntity.ok(existing.get());
    }
    
    // Process new transfer
}
```

---

## 5. Audit and Compliance

### 5.1 Transaction Logging

#### 5.1.1 Current System
**PROCTRAN Table**:
- Logs all successful write operations
- Does NOT log inquiries or updates (UPDCUST, UPDACC)
- Includes transaction type, date/time, amount, description
- 17 transaction type codes (OCC, ODC, OCA, ODA, DEB, CRE, TFR, etc.)

**Source**: `PROCTRAN.cpy`, multiple program references

#### 5.1.2 Modern System Requirements

**NFR-AUDIT-001**: Comprehensive Audit Trail
All operations must be logged with:
- Operation type (CREATE, READ, UPDATE, DELETE)
- Entity type (CUSTOMER, ACCOUNT, TRANSACTION)
- Entity ID
- User identity (who performed the operation)
- Timestamp (ISO 8601 format)
- Source IP address
- Request correlation ID
- Before/after values for updates
- Success or failure status
- Error details if failed

**Example Schema**:
```sql
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    correlation_id UUID NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    user_role VARCHAR(50) NOT NULL,
    source_ip VARCHAR(45) NOT NULL,
    before_value JSONB,
    after_value JSONB,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    INDEX idx_correlation_id (correlation_id),
    INDEX idx_timestamp (timestamp),
    INDEX idx_entity (entity_type, entity_id),
    INDEX idx_user (user_id)
);
```

**NFR-AUDIT-002**: Immutable Audit Logs
- Audit records cannot be modified or deleted
- Stored in append-only table
- Partition by date for performance
- Archive to cold storage after 7 years

**NFR-AUDIT-003**: Real-Time Audit Events
- Publish audit events to message queue (Kafka, SQS)
- Enable real-time monitoring and alerting
- Support downstream consumers (SIEM, analytics, compliance)

```java
@Aspect
@Component
public class AuditAspect {
    
    @AfterReturning(pointcut = "@annotation(Audited)", returning = "result")
    public void auditSuccess(JoinPoint joinPoint, Object result) {
        AuditEvent event = AuditEvent.builder()
            .correlationId(RequestContext.getCorrelationId())
            .timestamp(Instant.now())
            .operationType(getOperationType(joinPoint))
            .entityType(getEntityType(joinPoint))
            .entityId(getEntityId(result))
            .userId(SecurityContext.getCurrentUser())
            .sourceIp(RequestContext.getIpAddress())
            .status("SUCCESS")
            .build();
        
        auditRepository.save(event);
        eventPublisher.publish("audit.events", event);
    }
}
```

### 5.2 Compliance Requirements

**NFR-COMP-001**: Data Retention
- Transaction logs: 7 years (regulatory requirement)
- Customer data: Duration of relationship + 7 years
- Account data: 7 years after closure
- Audit logs: 7 years
- Archive to cold storage after 1 year

**NFR-COMP-002**: Right to be Forgotten (GDPR)
- Implement customer data deletion process
- Pseudonymize instead of delete where legally required
- Maintain audit trail of deletion requests
- Complete deletion within 30 days of request

**NFR-COMP-003**: Data Portability (GDPR)
- Export customer data in machine-readable format (JSON, CSV)
- Include all personal data and transaction history
- Complete export within 30 days of request

**NFR-COMP-004**: PCI-DSS Compliance
⚠️ **Decision required**: Does application handle payment card data?
If yes:
- Encrypt card data at rest and in transit
- Tokenize card numbers in logs and displays
- Implement network segmentation
- Regular security audits and penetration testing

**NFR-COMP-005**: SOX Compliance
⚠️ **Decision required**: Is company subject to SOX?
If yes:
- Separation of duties (developers cannot deploy to production)
- Change management process with approvals
- Audit trail for all system changes
- Regular access reviews

---

## 6. Error Handling and Recovery

### 6.1 Current System

#### 6.1.1 ABNDPROC Pattern
- All programs use ABNDPROC for unrecoverable errors
- Write error details to ABNDFILE VSAM
- Includes program name, transaction ID, SQL codes, CICS response codes

**Source**: `ABNDPROC.cbl`, `ABNDFILE` VSAM definition

#### 6.1.2 Retry Logic
- INQCUST: Retry SYSIDERR up to 100 times with 3-second delays
- XFRFUN: Retry deadlocks up to 10 times
- Named Counter: Retry ENQ/read failures up to 3 times

**Source**: `INQCUST.cbl` lines 321-400, `XFRFUN.cbl`, `CREACC.cbl` lines 447-683

### 6.2 Modern System Requirements

**NFR-ERROR-001**: Structured Exception Hierarchy
```java
public class BankingException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;
}

public class BusinessRuleException extends BankingException { }
public class InsufficientFundsException extends BusinessRuleException { }
public class AccountNotFoundException extends BusinessRuleException { }
public class CustomerNotFoundException extends BusinessRuleException { }
public class ValidationException extends BusinessRuleException { }

public class SystemException extends BankingException { }
public class DatabaseException extends SystemException { }
public class ExternalServiceException extends SystemException { }
```

**NFR-ERROR-002**: Global Exception Handler
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex) {
        logger.warn("Business rule violation", ex);
        return ResponseEntity
            .status(ex.getHttpStatus())
            .body(ErrorResponse.from(ex));
    }
    
    @ExceptionHandler(SystemException.class)
    public ResponseEntity<ErrorResponse> handleSystemError(SystemException ex) {
        logger.error("System error", ex);
        alertingService.sendAlert(ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.generic());
    }
}
```

**NFR-ERROR-003**: Retry Strategies
```java
// Database deadlock retry
@Retryable(
    value = {DeadlockLoserDataAccessException.class},
    maxAttempts = 10,
    backoff = @Backoff(delay = 100, multiplier = 1.5)
)

// External service retry with exponential backoff
@Retryable(
    value = {RestClientException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2.0)
)

// Circuit breaker for external services
@CircuitBreaker(
    name = "creditAgency",
    fallbackMethod = "getDefaultCreditScore"
)
```

**NFR-ERROR-004**: Graceful Degradation
- If credit agencies timeout, use default score (500)
- If audit logging fails, log error but continue transaction
- If notification service fails, queue for retry

**NFR-ERROR-005**: Error Tracking
- Integrate with error tracking service (Sentry, Rollbar, Bugsnag)
- Capture stack traces, request context, user info
- Group similar errors for easier triage
- Alert on error rate thresholds

---

## 7. Availability and Reliability

### 7.1 Service Level Objectives (SLOs)

**NFR-AVAIL-001**: Uptime SLO
- Target: 99.9% uptime (8.76 hours downtime per year)
- Measured over rolling 30-day window
- Excludes planned maintenance windows

**NFR-AVAIL-002**: Disaster Recovery
- Recovery Time Objective (RTO): 4 hours
- Recovery Point Objective (RPO): 15 minutes
- Multi-region deployment for geographic redundancy
- Automated failover to backup region

**NFR-AVAIL-003**: Backup Strategy
- Full database backup: Daily at 2 AM
- Incremental backup: Every 15 minutes
- Backup retention: 30 days hot, 7 years cold
- Test restore procedure monthly

### 7.2 Monitoring and Alerting

**NFR-MON-001**: Application Monitoring
Monitor:
- Request rate, error rate, latency (RED metrics)
- CPU, memory, disk, network utilization
- Database connection pool stats
- JVM metrics (heap, GC, threads)
- Cache hit/miss rates

**Tools**: Prometheus, Grafana, Datadog, New Relic

**NFR-MON-002**: Health Checks
```java
@RestController
public class HealthController {
    
    @GetMapping("/health/liveness")
    public ResponseEntity<String> liveness() {
        // Is the app running?
        return ResponseEntity.ok("UP");
    }
    
    @GetMapping("/health/readiness")
    public ResponseEntity<HealthStatus> readiness() {
        // Is the app ready to serve traffic?
        boolean dbOk = checkDatabase();
        boolean cacheOk = checkCache();
        
        if (dbOk && cacheOk) {
            return ResponseEntity.ok(HealthStatus.UP);
        } else {
            return ResponseEntity.status(503).body(HealthStatus.DOWN);
        }
    }
}
```

**NFR-MON-003**: Alerting Rules
Alert when:
- Error rate > 1% for 5 minutes
- p95 latency > 1 second for 5 minutes
- CPU > 80% for 10 minutes
- Memory > 90% for 5 minutes
- Database connection pool exhausted
- External service circuit breaker open

**Severity Levels**:
- **P1 (Critical)**: Service down, customer impact, page on-call
- **P2 (High)**: Degraded performance, partial outage
- **P3 (Medium)**: Minor issue, can wait for business hours
- **P4 (Low)**: Informational, no action required

**NFR-MON-004**: Distributed Tracing
- Implement distributed tracing with Jaeger, Zipkin, or AWS X-Ray
- Trace request flow across microservices
- Correlate logs and traces with correlation IDs
- Identify bottlenecks and performance issues

```java
@Component
public class TracingFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setHeader("X-Correlation-ID", correlationId);
        
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

---

## 8. Maintainability and Supportability

### 8.1 Code Quality

**NFR-MAINT-001**: Code Standards
- Follow Spring Boot best practices
- Consistent naming conventions (camelCase for variables, PascalCase for classes)
- Maximum method length: 50 lines
- Maximum class length: 500 lines
- Cyclomatic complexity: < 10 per method

**NFR-MAINT-002**: Test Coverage
- Unit test coverage: > 80%
- Integration test coverage: > 70%
- End-to-end test coverage: Critical paths only
- Run tests on every commit (CI/CD)

**NFR-MAINT-003**: Documentation
- API documentation: OpenAPI/Swagger
- Code comments: Javadoc for public methods
- Architecture Decision Records (ADRs) for major decisions
- Runbooks for operational procedures

**Example OpenAPI**:
```java
@Operation(
    summary = "Create a new account",
    description = "Creates a new bank account for an existing customer",
    responses = {
        @ApiResponse(responseCode = "201", description = "Account created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Customer not found"),
        @ApiResponse(responseCode = "409", description = "Customer has maximum accounts")
    }
)
@PostMapping("/api/accounts")
public ResponseEntity<AccountDTO> createAccount(@Valid @RequestBody CreateAccountRequest request) {
    // ...
}
```

### 8.2 Observability

**NFR-OBS-001**: Structured Logging
```java
// BAD: String concatenation
logger.info("Customer " + customerId + " created account " + accountId);

// GOOD: Structured logging
logger.info("Customer created account",
    kv("customerId", customerId),
    kv("accountId", accountId),
    kv("accountType", accountType)
);
```

**Benefits**:
- Machine-parseable logs
- Easy to query and aggregate
- Works with log aggregation tools (ELK, Splunk)

**NFR-OBS-002**: Log Levels
- **ERROR**: Unhandled exceptions, system errors
- **WARN**: Business rule violations, degraded performance
- **INFO**: Significant events (create, update, delete operations)
- **DEBUG**: Detailed flow (disabled in production)
- **TRACE**: Very detailed (disabled in production)

**NFR-OBS-003**: Metrics and Dashboards
Create dashboards for:
- Business metrics (customers created, transactions processed, transfer volume)
- Technical metrics (latency, error rates, throughput)
- Infrastructure metrics (CPU, memory, disk, network)

---

## 9. Technology Stack Requirements

### 9.1 Backend Stack

**NFR-TECH-001**: Core Framework
- **Language**: Java 17 or later (LTS version)
- **Framework**: Spring Boot 3.x
- **Build Tool**: Maven or Gradle
- **Dependency Management**: Maven Central

**Rationale**:
- Mature ecosystem
- Strong community support
- Enterprise-ready
- Widely adopted for financial services

**NFR-TECH-002**: Database
⚠️ **Decision required by architect**: PostgreSQL vs Db2

**Option 1: PostgreSQL** (Recommended for cloud-native)
- Open-source, no licensing costs
- Excellent performance and scalability
- Strong JSON support (JSONB)
- Wide cloud provider support

**Option 2: Db2** (If staying on IBM stack)
- Continuity with existing system
- Enterprise support from IBM
- Familiar to existing team
- Higher licensing costs

**NFR-TECH-003**: Caching
- **In-Memory Cache**: Redis or Memcached
- **Application Cache**: Caffeine (embedded)
- **Use Cases**: Customer metadata, account types, reference data

**NFR-TECH-004**: Message Queue
⚠️ **Decision required**: Async processing needs?

If yes:
- **Cloud**: Amazon SQS, Azure Service Bus, Google Pub/Sub
- **Self-Hosted**: RabbitMQ, Apache Kafka

**Use Cases**:
- Async credit checks
- Notification delivery
- Audit event streaming

### 9.2 Frontend Stack

**NFR-TECH-005**: Web Application
- **Framework**: React 18 or Angular 15+
- **State Management**: Redux, Zustand, or Context API
- **Build Tool**: Vite or Webpack
- **UI Components**: Carbon Design System (IBM), Material-UI, or Ant Design

**NFR-TECH-006**: Mobile Application
⚠️ **Decision required**: Is mobile app needed?

If yes:
- **Native**: Swift (iOS), Kotlin (Android)
- **Cross-Platform**: React Native, Flutter

### 9.3 DevOps Stack

**NFR-TECH-007**: Containerization
- **Container Runtime**: Docker
- **Orchestration**: Kubernetes
- **Service Mesh**: Istio or Linkerd (optional)

**NFR-TECH-008**: CI/CD Pipeline
- **Version Control**: Git (GitHub, GitLab, Bitbucket)
- **CI/CD**: GitHub Actions, GitLab CI, Jenkins, CircleCI
- **Artifact Repository**: Artifactory, Nexus
- **Container Registry**: Docker Hub, Amazon ECR, Google GCR

**Pipeline Stages**:
1. Compile
2. Unit tests
3. Integration tests
4. Security scan (Snyk, SonarQube)
5. Build Docker image
6. Push to registry
7. Deploy to staging
8. Run E2E tests
9. Deploy to production (manual approval)

**NFR-TECH-009**: Infrastructure as Code
- **Tool**: Terraform, CloudFormation, or Pulumi
- **Benefits**: Version-controlled infrastructure, reproducible environments, disaster recovery

### 9.4 Observability Stack

**NFR-TECH-010**: Logging
- **Application Logs**: Logback or Log4j2
- **Log Aggregation**: ELK Stack (Elasticsearch, Logstash, Kibana), Splunk, CloudWatch Logs

**NFR-TECH-011**: Metrics
- **Metrics Library**: Micrometer (Spring Boot Actuator)
- **Time-Series Database**: Prometheus, InfluxDB
- **Visualization**: Grafana, Datadog

**NFR-TECH-012**: Tracing
- **Tool**: Jaeger, Zipkin, AWS X-Ray
- **Instrumentation**: OpenTelemetry

**NFR-TECH-013**: Error Tracking
- **Tool**: Sentry, Rollbar, Bugsnag
- **Features**: Stack traces, release tracking, user context

---

## 10. Modernization Strategy

### 10.1 Migration Approach

**NFR-MOD-001**: Strangler Fig Pattern
Gradually replace legacy system:

**Phase 1: API Gateway**
```
Client → API Gateway → z/OS Connect → CICS → COBOL
```
- Add API gateway for routing, auth, rate limiting
- No code changes yet

**Phase 2: Parallel Implementation**
```
Client → API Gateway → ┌→ z/OS Connect → CICS → COBOL (legacy)
                       └→ Spring Boot → PostgreSQL (new)
```
- Implement new services alongside legacy
- Route traffic based on feature flags
- Dual-write to both systems for critical data

**Phase 3: Gradual Cutover**
- Migrate one endpoint at a time
- Validate with canary deployments
- Monitor metrics closely

**Phase 4: Decommission Legacy**
```
Client → API Gateway → Spring Boot → PostgreSQL
```
- All traffic to new system
- Decommission CICS, z/OS Connect, COBOL

**NFR-MOD-002**: Data Migration Strategy
1. **Schema Migration**: Create new PostgreSQL schema based on requirements docs
2. **Data Extract**: Export data from VSAM and Db2
3. **Data Transform**: Convert dates (DDMMYYYY → YYYY-MM-DD), normalize fields
4. **Data Load**: Import into PostgreSQL with validation
5. **Data Verification**: Compare records, reconcile discrepancies
6. **Cutover**: Switch to new database (maintenance window)

**NFR-MOD-003**: Microservices Architecture
⚠️ **Decision required by architect**: Monolith vs Microservices?

**Recommended Microservices**:
- **Customer Service**: Customer CRUD, credit checks
- **Account Service**: Account CRUD, account-customer relationships
- **Transaction Service**: Debit, credit, transfer, transaction history
- **Audit Service**: Transaction logging, audit queries
- **Notification Service**: Email, SMS, push notifications
- **API Gateway**: Authentication, routing, rate limiting

**Communication**:
- Synchronous: REST (HTTP) for request-response
- Asynchronous: Message queue (Kafka, RabbitMQ) for events

**NFR-MOD-004**: Backward Compatibility
During migration, maintain compatibility:
- Support both old and new date formats
- Accept both customer number (10 digits) and UUID
- Provide data translation layer

### 10.2 Testing Strategy

**NFR-TEST-001**: Test Types
- **Unit Tests**: Individual methods and classes (JUnit, Mockito)
- **Integration Tests**: API endpoints with real database (TestContainers)
- **Contract Tests**: API contracts (Pact, Spring Cloud Contract)
- **End-to-End Tests**: Full user flows (Selenium, Cypress)
- **Performance Tests**: Load and stress testing (JMeter, Gatling)
- **Security Tests**: Penetration testing, vulnerability scanning

**NFR-TEST-002**: Test Environments
- **Local**: Developer workstation
- **Dev**: Continuous integration, latest code
- **Test/QA**: Manual testing, stable builds
- **Staging**: Production-like, final validation
- **Production**: Live system

**NFR-TEST-003**: Test Data Management
- Use anonymized production data in test environments
- Generate synthetic data for load testing
- Reset test data between test runs
- Never use production data in development

---

## 11. Constraints and Assumptions

### 11.1 Technical Constraints

**NFR-CONST-001**: Browser Support
- Chrome: Latest 2 versions
- Firefox: Latest 2 versions
- Safari: Latest 2 versions
- Edge: Latest 2 versions
- No IE11 support

**NFR-CONST-002**: Mobile Support
- iOS: 14.0 and above
- Android: 10.0 and above

**NFR-CONST-003**: Network Requirements
- Minimum bandwidth: 1 Mbps
- Maximum latency: 200ms
- Support intermittent connectivity (offline mode?)

### 11.2 Assumptions

⚠️ **Assumptions to Validate**:
1. Current system handles < 100 TPS peak load
2. 9-account limit per customer remains in modern system
3. Account types (CURRENT, SAVINGS, LOAN, MORTGAGE, ISA) are fixed
4. Credit check agencies provide REST APIs
5. Users access system during business hours (9 AM - 6 PM)
6. No international transactions (single currency, single country)
7. Regulatory requirements limited to domestic jurisdiction
8. Existing CICS programs have no undocumented business rules

### 11.3 Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Data migration failures | High | Thorough testing, rollback plan, maintain dual-write |
| Performance degradation | High | Load testing, gradual rollout, rollback capability |
| Security vulnerabilities | Critical | Security audits, penetration testing, code reviews |
| Team learning curve (Java/Spring) | Medium | Training, pair programming, mentorship |
| Scope creep | Medium | Clear requirements, change control process |
| Integration with external agencies | Medium | Mock services, contract testing, fallback logic |

---

## 12. Compliance and Regulatory

### 12.1 Financial Regulations

**NFR-REG-001**: Know Your Customer (KYC)
⚠️ **Decision required**: Which KYC regulations apply?
- Verify customer identity at onboarding
- Maintain customer due diligence records
- Monitor for suspicious activity

**NFR-REG-002**: Anti-Money Laundering (AML)
- Monitor transactions for suspicious patterns
- Report large transactions (> threshold)
- Maintain transaction records for 7 years

**NFR-REG-003**: Consumer Data Protection
- **GDPR** (EU): Data protection and privacy
- **CCPA** (California): Consumer privacy rights
- **PIPEDA** (Canada): Personal information protection

**NFR-REG-004**: Financial Reporting
- Accurate financial records
- Audit trail for all transactions
- Regular reconciliation

### 12.2 Security Standards

**NFR-STD-001**: PCI-DSS
⚠️ **If handling payment cards**:
- Encrypt cardholder data
- Maintain secure network
- Regular security testing
- Access control measures

**NFR-STD-002**: ISO 27001
- Information security management system
- Risk assessment and treatment
- Security policies and procedures

**NFR-STD-003**: SOC 2
- Security, availability, processing integrity
- Confidentiality, privacy
- Annual audit and report

---

## 13. Open Questions and Decisions

⚠️ **Critical Decisions Required**:

1. **Database Choice**: PostgreSQL vs Db2 for modernized system?
2. **Architecture**: Monolith vs Microservices?
3. **Deployment**: Cloud (AWS, Azure, GCP) vs On-Premise?
4. **Mobile App**: Is native mobile app required?
5. **Message Queue**: Is async processing needed? Which tool?
6. **Soft Delete**: Should customer/account deletes be logical instead of physical?
7. **Audit Updates**: Should UPDCUST and UPDACC write to transaction log?
8. **Credit Agencies**: What are the integration protocols and SLAs?
9. **Compliance**: Which specific regulations apply (PCI-DSS, SOX, GDPR)?
10. **Performance SLAs**: What are the target response times and throughput?

⚠️ **Unknown Requirements**:

1. Current system performance baselines (TPS, latency, concurrent users)
2. Exact authentication/authorization model for existing interfaces
3. Business rules for different account types (ISA, LOAN, MORTGAGE)
4. International banking requirements (multi-currency, cross-border)
5. Statement generation logic and frequency
6. Interest calculation and posting schedule
7. Overdraft fee calculation rules
8. Customer notification preferences and channels
9. Reporting requirements (management reports, regulatory reports)
10. Disaster recovery and business continuity requirements

---

*Document Version: 1.0*  
*Last Updated: 2025-10-27*  
*Source Repository: taylor-curran/og-cics-cobol-app*
