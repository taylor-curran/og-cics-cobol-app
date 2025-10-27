# Integration & External Interfaces

## 1. Introduction

### 1.1 Document Purpose
This document specifies the integration points and external interfaces of the CICS Bank Sample Application (CBSA). It describes CICS transaction mappings, BMS map programs, z/OS Connect integration, external credit agency interfaces, and named counter services needed to inform modernization architecture.

**Source**: Analysis of `etc/install/base/installjcl/BANK.csd`, architecture documentation, and COBOL program references

### 1.2 Integration Architecture Overview
CBSA integrates multiple systems and layers:
- **CICS Transaction Processing**: Programs invoked via 4-character transaction codes
- **BMS 3270 Interface**: Map programs handle terminal I/O
- **Liberty JVM Server**: Hosts Carbon React UI with CICS Java API integration
- **z/OS Connect**: Provides REST-to-CICS bridge for Spring Boot services
- **External Credit Agencies**: Async communication via CICS child transactions
- **Named Counter Service**: Centralized ID generation via CICS ENQ/DEQ
- **Abend Processing**: Centralized error handling via ABNDPROC

---

## 2. CICS Transaction Definitions

### 2.1 Transaction Catalog

#### 2.1.1 Main Menu Transaction
| Transaction | Program | Description | Source |
|-------------|---------|-------------|--------|
| OMEN | BNKMENU | Main menu - entry point for BMS interface | BANK.csd line 265 |

**Usage**: User types `OMEN` at 3270 terminal to start application

**Program Flow**:
1. Display BNK1MAI map (main menu screen)
2. Accept user selection (1-7, A)
3. Route to appropriate transaction based on selection

**Source**: `BNKMENU.cbl`, `BNK1MAI.bms`

#### 2.1.2 Credit Agency Transactions
| Transaction | Program | Description | Source |
|-------------|---------|-------------|--------|
| OCR1 | CRDTAGY1 | Credit agency 1 check | BANK.csd line 281 |
| OCR2 | CRDTAGY2 | Credit agency 2 check | BANK.csd line 285 |
| OCR3 | CRDTAGY3 | Credit agency 3 check | BANK.csd line 289 |
| OCR4 | CRDTAGY4 | Credit agency 4 check | BANK.csd line 293 |
| OCR5 | CRDTAGY5 | Credit agency 5 check | BANK.csd line 297 |

**Usage**: Called asynchronously by CRECUST program via CICS CHANNELS

**Communication Pattern**:
```
CRECUST (parent)
  ↓ EXEC CICS START CHANNEL
  ├─→ OCR1/CRDTAGY1 (child) → Return score via channel
  ├─→ OCR2/CRDTAGY2 (child) → Return score via channel
  ├─→ OCR3/CRDTAGY3 (child) → Return score via channel
  ├─→ OCR4/CRDTAGY4 (child) → Return score via channel
  └─→ OCR5/CRDTAGY5 (child) → Return score via channel
  ↓ Wait up to 3 seconds
  ↓ Aggregate returned scores
```

**Source**: `CRECUST.cbl` lines 205-387

#### 2.1.3 Complete Transaction List
⚠️ **Assumption**: Based on typical CICS Bank Sample Application patterns, likely additional transactions for each BMS operation:

| Transaction | Program | Function |
|-------------|---------|----------|
| OMEN | BNKMENU | Main menu |
| (TBD) | BNK1CCA | Account lookup by customer |
| (TBD) | BNK1CAC | Create account |
| (TBD) | BNK1CCS | Create customer |
| (TBD) | BNK1CRA | Credit/debit account |
| (TBD) | BNK1DAC | Display account |
| (TBD) | BNK1DCS | Display customer |
| (TBD) | BNK1TFN | Transfer funds |
| (TBD) | BNK1UAC | Update account |
| (TBD) | BNK1B2B | Bank-to-bank transfer |
| OCR1-5 | CRDTAGY1-5 | Credit agency checks |

**Modernization**: Replace CICS transactions with REST API endpoints

---

## 3. BMS Map Interface Programs

### 3.1 BMS Architecture

#### 3.1.1 Two-Program Pattern
Each BMS operation typically uses two programs:
1. **Wrapper Program** (BNK1xxx): Handles BMS map I/O, validation, user interaction
2. **Business Logic Program** (CREACCxxx): Implements core business logic, data access

**Example Flow**:
```
User → BNK1MAI (main menu)
  ↓ User selects option 4 (create account)
  ↓
BNK1CAC (wrapper)
  → Display BNK1CAM map (create account form)
  → Validate user inputs
  → Call CREACC (business logic)
  ← Return result
  → Display confirmation or error on map
```

### 3.2 BMS Map Programs

#### 3.2.1 Program-Map-Transaction Mapping
| Wrapper Program | Map Name | Business Logic | Function | Transaction |
|-----------------|----------|----------------|----------|-------------|
| BNKMENU | BNK1MAI | (none) | Main menu | OMEN |
| BNK1CCA | BNK1ACC | INQACCCU | Account lookup | TBD |
| BNK1CAC | BNK1CAM | CREACC | Create account | TBD |
| BNK1CCS | BNK1CCM | CRECUST | Create customer | TBD |
| BNK1CRA | BNK1CDM | DBCRFUN | Credit/debit | TBD |
| BNK1DAC | BNK1DAM | INQACC, DELACC | Display/delete account | TBD |
| BNK1DCS | BNK1DCM | INQCUST, DELCUS, UPDCUST | Display/delete/update customer | TBD |
| BNK1TFN | BNK1TFM | XFRFUN | Transfer funds | TBD |
| BNK1UAC | BNK1UAM | UPDACC | Update account | TBD |
| BNK1B2B | BNK1B2M | (TBD) | Bank-to-bank transfer | TBD |

**Source**: `BANK.csd` lines 36-74 (mapsets), lines 77-264 (programs)

#### 3.2.2 BMS Mapset Definitions
```
MAPSET: BNK1MAI (Main Menu)
  - MODE: INOUT (supports both input and output)
  - ENTRYPOINT: 0 (first map in set)
  - MAPS: BNK1MAI
  - Associated with program BNKMENU
  
MAPSET: BNK1CAM (Create Account)
  - Associated with program BNK1CAC
  - Collects: customer number, account type, interest rate, overdraft
  
MAPSET: BNK1CCM (Create Customer)
  - Associated with program BNK1CCS
  - Collects: name, address, date of birth
  
MAPSET: BNK1CDM (Credit/Debit)
  - Associated with program BNK1CRA
  - Collects: account number, amount, transaction type
  
... (similar for other mapsets)
```

**Source**: `BANK.csd` lines 36-74

#### 3.2.3 Function Key Navigation
Common function keys across all BMS maps:
- **F3**: Exit application (return to main menu)
- **F12**: Cancel current operation (return to previous screen)
- **F5**: Delete (on display customer/account screens)
- **F10**: Update (on display customer screen)

**Source**: `BNK1MAI.bms` line 62, similar patterns in other maps

#### 3.2.4 Modernization Mapping
Replace BMS maps with REST APIs + React components:

**BMS Map → REST + React Transformation**:
```
BNK1MAI (Main Menu)
  → React: <MainMenu /> component with navigation
  → No API call needed (pure UI)

BNK1CAM (Create Account Form)
  → React: <CreateAccountForm /> component
  → API: POST /api/accounts

BNK1CDM (Credit/Debit Form)
  → React: <TransactionForm /> component
  → API: POST /api/accounts/{id}/transactions

BNK1TFM (Transfer Form)
  → React: <TransferForm /> component
  → API: POST /api/transfers

... etc
```

---

## 4. z/OS Connect Integration

### 4.1 Architecture Overview

#### 4.1.1 z/OS Connect Role
z/OS Connect Server acts as a bridge between Spring Boot REST APIs and CICS programs:

```
External Client → Spring Boot App → z/OS Connect Server → CICS → COBOL Program → Data Store
                    (REST/JSON)      (REST/JSON)          (COMMAREA/Channel)
```

**Components**:
- **Service Archive (SAR)**: Packages service definitions for z/OS Connect
- **API Definitions**: Define REST endpoints that map to CICS programs
- **Data Transformation**: JSON ↔ COMMAREA/Channel conversion

**Source**: `doc/CBSA_Architecture_guide.md`

#### 4.1.2 Configuration
⚠️ **Unknown**: Exact z/OS Connect configuration details (would need to examine server.xml or similar)

Likely configuration includes:
- CICS region connection details
- Authentication/authorization settings
- Service mappings (REST path → CICS program)
- Timeout settings
- Error handling rules

#### 4.1.3 Service Interfaces
Based on Spring Boot documentation references, likely services:

**Customer Services**:
- `GET /customers/{id}` → INQCUST
- `POST /customers` → CRECUST
- `PATCH /customers/{id}` → UPDCUST
- `DELETE /customers/{id}` → DELCUS

**Payment Services**:
- `GET /accounts/{id}` → INQACC
- `POST /accounts` → CREACC
- `PATCH /accounts/{id}` → UPDACC
- `DELETE /accounts/{id}` → DELACC
- `GET /customers/{id}/accounts` → INQACCCU
- `POST /accounts/{id}/transactions` → DBCRFUN
- `POST /transfers` → XFRFUN

#### 4.1.4 Data Transformation Example
**REST Request (JSON)**:
```json
POST /api/customers
{
  "name": "Mr John Smith",
  "address": "123 Main St, London, SW1A 1AA",
  "dateOfBirth": "1980-05-15"
}
```

**z/OS Connect Transformation**:
```
JSON → COMMAREA fields
  CUSTOMER_NAME ← "Mr John Smith" (padded to 60 chars)
  CUSTOMER_ADDRESS ← "123 Main St..." (padded to 160 chars)
  CUSTOMER_DOB ← "15051980" (converted to DDMMYYYY)
```

**CICS Program Call**:
```
EXEC CICS LINK PROGRAM('CRECUST') COMMAREA(commarea-structure)
```

**Return Transformation**:
```
COMMAREA fields → JSON
  CUSTOMER_NUMBER → "customerId": "0001234567"
  CUSTOMER_SORTCODE → "sortCode": "000001"
  CUSTOMER_CREDIT_SCORE → "creditScore": 725
  (etc.)
```

**REST Response (JSON)**:
```json
HTTP 201 Created
{
  "customerId": "0001234567",
  "sortCode": "000001",
  "name": "Mr John Smith",
  "address": "123 Main St, London, SW1A 1AA",
  "dateOfBirth": "1980-05-15",
  "creditScore": 725,
  "creditScoreReviewDate": "2025-10-27"
}
```

#### 4.1.5 Error Mapping
z/OS Connect must map COBOL fail codes to HTTP status codes:

| Fail Code | Meaning | HTTP Status | HTTP Response |
|-----------|---------|-------------|---------------|
| ' ' | Success | 200/201 | Success response |
| '1' | Not found | 404 | Not Found |
| '2' | Server error | 500 | Internal Server Error |
| '3' | Insufficient funds | 400 | Bad Request |
| '4' | Validation error | 400 | Bad Request |
| 'O' | DOB validation | 400 | Bad Request |
| 'Y' | Future date | 400 | Bad Request |
| 'T' | Invalid title | 400 | Bad Request |

#### 4.1.6 Modernization Strategy
**Replace z/OS Connect**:
- Migrate COBOL business logic to Java/Spring Boot
- Eliminate intermediary layer
- Direct database access from Spring Boot services
- Reduce latency and complexity

```
Before: Client → Spring Boot → z/OS Connect → CICS → COBOL → Db2
After:  Client → Spring Boot → PostgreSQL
```

---

## 5. Liberty JVM Server Integration

### 5.1 CBSAWLP Server

#### 5.1.1 Architecture
Liberty JVM Server (CBSAWLP) runs within the CICS region and hosts:
- Carbon React UI application
- CICS Java APIs for calling COBOL programs
- HTTP/HTTPS endpoints for browser access

```
Browser → HTTPS → Liberty (CBSAWLP) → CICS Java API → COBOL Programs
                     ↓ React App
```

**Source**: `doc/CBSA_Architecture_guide.md` lines 76-90

#### 5.1.2 Communication Pattern
React components use CICS Java APIs:
```java
// Example: Create customer from React UI
Program program = new Program();
program.setName("CRECUST");

Channel channel = Task.getTask().createChannel("CUSTOMER-CHANNEL");
Container nameContainer = channel.createContainer("CUSTOMER-NAME");
nameContainer.putString(customerName);
// ... create other containers for address, DOB

program.link(channel);

// Retrieve results from output containers
String customerNumber = channel.getContainer("CUSTOMER-NUMBER").getString();
```

#### 5.1.3 Modernization Strategy
**Migrate to Standalone React App**:
1. Deploy React app as static site (S3, CDN, Netlify)
2. Replace CICS Java API calls with REST API calls
3. Add authentication layer (JWT, OAuth2)
4. Eliminate Liberty dependency

```javascript
// Before: CICS Java API call
const result = await cicsLink({
  program: 'CRECUST',
  data: customerData
});

// After: REST API call
const result = await fetch('/api/customers', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`
  },
  body: JSON.stringify(customerData)
});
```

---

## 6. External Credit Agency Integration

### 6.1 Credit Check Architecture

#### 6.1.1 Integration Pattern
CRECUST program performs async credit checks with multiple agencies:

```
CRECUST Program
  ↓
  ├─ EXEC CICS START TRANSID('OCR1') CHANNEL(channel1)
  ├─ EXEC CICS START TRANSID('OCR2') CHANNEL(channel2)
  ├─ EXEC CICS START TRANSID('OCR3') CHANNEL(channel3)
  ├─ EXEC CICS START TRANSID('OCR4') CHANNEL(channel4)
  └─ EXEC CICS START TRANSID('OCR5') CHANNEL(channel5)
  ↓
  Wait up to 3 seconds for responses
  ↓
  Read response containers from channels
  ↓
  Aggregate scores: (sum of scores) / (count of responses)
  ↓
  If all agencies timeout/fail, use default score 500
```

**Source**: `CRECUST.cbl` lines 205-387

#### 6.1.2 Channel/Container Communication
**Input Containers** (passed to credit agencies):
- `CUSTOMER-NAME`: Full customer name
- `CUSTOMER-DOB`: Date of birth (DDMMYYYY)
- `CUSTOMER-ADDRESS`: Full address
- `SORT-CODE`: Bank sort code
- `CUSTOMER-NUMBER`: Generated customer number

**Output Containers** (returned from agencies):
- `CREDIT-SCORE`: Integer score (0-999)
- `AGENCY-STATUS`: Success/failure indicator
- `AGENCY-MESSAGE`: Optional error message

#### 6.1.3 Credit Agency Programs
| Program | Transaction | Description | Implementation |
|---------|-------------|-------------|----------------|
| CRDTAGY1 | OCR1 | Credit Agency 1 | Likely dummy/stub |
| CRDTAGY2 | OCR2 | Credit Agency 2 | Likely dummy/stub |
| CRDTAGY3 | OCR3 | Credit Agency 3 | Likely dummy/stub |
| CRDTAGY4 | OCR4 | Credit Agency 4 | Likely dummy/stub |
| CRDTAGY5 | OCR5 | Credit Agency 5 | Likely dummy/stub |

⚠️ **Assumption**: These are likely dummy/stub implementations for demonstration purposes. Real implementations would call external credit bureau APIs (Experian, Equifax, TransUnion, etc.)

**Source**: `BANK.csd` lines 127-161

#### 6.1.4 Timeout and Retry Logic
- **Timeout**: 3 seconds per agency
- **No Retry**: If agency doesn't respond within 3 seconds, move on
- **Partial Success**: Aggregate scores from agencies that did respond
- **Total Failure**: If all agencies timeout/fail, use default score of 500

**Business Rule**: Better to create customer with default score than block on credit checks

#### 6.1.5 Score Aggregation
```
Example:
  Agency 1: 750
  Agency 2: timeout
  Agency 3: 700
  Agency 4: timeout
  Agency 5: 680

  Aggregated score = (750 + 700 + 680) / 3 = 710
```

#### 6.1.6 Modernization Considerations
**Replace CICS Child Transactions with Modern Async**:

**Option 1: Spring @Async**:
```java
@Service
public class CreditCheckService {
    
    @Async
    public CompletableFuture<Integer> checkAgency1(CustomerData customer) {
        try {
            // Call external API (HTTP REST, SOAP, etc.)
            int score = externalApiClient.getScore("Agency1", customer);
            return CompletableFuture.completedFuture(score);
        } catch (Exception e) {
            logger.warn("Agency1 check failed", e);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    public int aggregateScores(CustomerData customer) {
        List<CompletableFuture<Integer>> futures = Arrays.asList(
            checkAgency1(customer),
            checkAgency2(customer),
            checkAgency3(customer),
            checkAgency4(customer),
            checkAgency5(customer)
        );
        
        // Wait up to 3 seconds for all, timeout stragglers
        List<Integer> scores = futures.stream()
            .map(f -> f.completeOnTimeout(null, 3, TimeUnit.SECONDS))
            .map(CompletableFuture::join)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        if (scores.isEmpty()) {
            return 500; // Default score
        }
        
        return (int) scores.stream().mapToInt(Integer::intValue).average().orElse(500);
    }
}
```

**Option 2: Event-Driven with Message Queue**:
```java
// Publisher
public void requestCreditChecks(String customerId) {
    CreditCheckRequest request = new CreditCheckRequest(customerId);
    
    messagingTemplate.convertAndSend("credit.check.request", request);
    
    // Set up response listener with timeout
    CompletableFuture<CreditCheckResponse> future = 
        responseRegistry.registerRequest(customerId, 3000);
}

// Consumer (separate service or external agency)
@RabbitListener(queues = "credit.check.request")
public void processCreditCheck(CreditCheckRequest request) {
    int score = performCreditCheck(request);
    
    CreditCheckResponse response = new CreditCheckResponse(
        request.getCustomerId(), 
        "Agency1", 
        score
    );
    
    messagingTemplate.convertAndSend("credit.check.response", response);
}
```

**Option 3: GraphQL with DataLoader** (for batching):
```java
@DgsComponent
public class CreditCheckDataFetcher {
    
    @DgsData(parentType = "Customer", field = "creditScore")
    public CompletableFuture<Integer> getCreditScore(DgsDataFetchingEnvironment env) {
        Customer customer = env.getSource();
        
        // DataLoader batches and deduplicates requests
        DataLoader<String, Integer> loader = env.getDataLoader("creditScoreLoader");
        return loader.load(customer.getId());
    }
}
```

**Recommendation**: Use CompletableFuture for simplicity, or event-driven for loose coupling with external agencies.

---

## 7. Named Counter Service

### 7.1 Counter Architecture

#### 7.1.1 Purpose
Generate unique sequential identifiers for customers and accounts using CICS Named Counter facility.

**Counters**:
- **CBSACUST**: Customer number generator (10-digit)
- **CBSAACCT**: Account number generator (8-digit)

**Storage**: Counter state persisted in CONTROL Db2 table

#### 7.1.2 Access Pattern
Programs use ENQ/DEQ for exclusive access:

```cobol
* Acquire exclusive lock
EXEC CICS ENQ RESOURCE('CBSAACCT') LENGTH(8) END-EXEC.

* Read current counter value
EXEC CICS READ FILE('CONTROL') 
     RIDFLD(counter-name) 
     INTO(counter-record) 
END-EXEC.

* Increment counter
ADD 1 TO COUNTER-VALUE.

* Write back updated value
EXEC CICS REWRITE FILE('CONTROL') 
     FROM(counter-record) 
END-EXEC.

* Release lock
EXEC CICS DEQ RESOURCE('CBSAACCT') LENGTH(8) END-EXEC.
```

**Source**: `CREACC.cbl` lines 447-683, `CRECUST.cbl` lines 108-142

#### 7.1.3 Retry Logic
If ENQ or READ fails:
- Retry up to 3 times
- Wait between retries
- If all retries fail, abort with error code

#### 7.1.4 Concurrency Control
ENQ/DEQ ensures only one program can access a counter at a time, preventing duplicate IDs across concurrent transactions.

#### 7.1.5 Modernization Strategies

**Option 1: Database Sequences** (Recommended):
```sql
CREATE SEQUENCE customer_number_seq 
    START WITH 1000000001 
    INCREMENT BY 1
    CACHE 20;

CREATE SEQUENCE account_number_seq 
    START WITH 10000001 
    INCREMENT BY 1
    CACHE 20;
```

```java
@Entity
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, 
                    generator = "customer_seq")
    @SequenceGenerator(name = "customer_seq", 
                       sequenceName = "customer_number_seq",
                       allocationSize = 1)
    private Long id;
}
```

**Pros**:
- Database-managed, highly concurrent
- No application locking needed
- Cache reduces database calls
- Guaranteed uniqueness

**Option 2: UUID** (Cloud-native):
```java
@Entity
public class Customer {
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(columnDefinition = "VARCHAR(36)")
    private String id; // e.g., "550e8400-e29b-41d4-a716-446655440000"
}
```

**Pros**:
- No database dependency for generation
- Globally unique across distributed systems
- No locking or serialization needed
- Works with sharded databases

**Cons**:
- Longer IDs (36 chars vs 8-10 digits)
- Not human-friendly
- No ordering

**Option 3: Distributed ID Generator** (Snowflake algorithm):
```java
@Service
public class IdGeneratorService {
    private final SnowflakeIdGenerator generator;
    
    public long generateCustomerId() {
        return generator.nextId(); // 64-bit long, time-ordered
    }
}
```

**Pros**:
- Time-ordered IDs
- No database call per ID
- High throughput (millions/sec per node)
- Distributed-system friendly

**Recommendation**: Use database sequences for simplicity, or UUIDs for cloud-native / microservices architecture.

---

## 8. Abend Processing Integration

### 8.1 Centralized Error Handling

#### 8.1.1 ABNDPROC Program
All programs use ABNDPROC for unrecoverable error handling:

```cobol
* In every program
EXEC CICS HANDLE ABEND LABEL(ABEND-HANDLER) END-EXEC.

* When unrecoverable error occurs
ABEND-HANDLER.
    MOVE program-name TO ABEND-PROGRAM.
    MOVE transaction-id TO ABEND-TRANSID.
    MOVE SQLCODE TO ABEND-SQLCODE.
    
    EXEC CICS LINK PROGRAM('ABNDPROC') 
         COMMAREA(abend-info) 
    END-EXEC.
```

**Source**: Referenced throughout COBOL programs, `ABNDPROC.cbl`

#### 8.1.2 ABNDFILE VSAM
ABNDPROC writes error details to ABNDFILE:
- Abend code
- Program name
- Transaction ID
- CICS response codes
- SQL codes
- Timestamp
- Error message

**Source**: `BANK.csd` lines 23-34

#### 8.1.3 Error Recovery
After logging to ABNDFILE:
- ABNDPROC returns control to caller (if possible)
- Or triggers CICS abend (if unrecoverable)
- Operations team monitors ABNDFILE for errors

#### 8.1.4 Modernization Strategy
Replace with modern observability stack:

**Structured Logging**:
```java
@Component
public class ErrorHandler {
    
    @ExceptionHandler(Exception.class)
    public void handleError(Exception ex, HttpServletRequest request) {
        logger.error("Unhandled exception",
            kv("path", request.getRequestURI()),
            kv("method", request.getMethod()),
            kv("user", SecurityContext.getCurrentUser()),
            kv("correlationId", request.getHeader("X-Correlation-ID")),
            kv("exception", ex.getClass().getSimpleName()),
            kv("message", ex.getMessage()),
            ex
        );
        
        // Send to error tracking service
        Sentry.captureException(ex);
    }
}
```

**Monitoring Stack**:
- **Application Logs**: Logback/Log4j2 → ELK Stack / CloudWatch Logs
- **Distributed Tracing**: Jaeger / Zipkin / AWS X-Ray
- **Error Tracking**: Sentry / Rollbar / Bugsnag
- **Metrics**: Prometheus / Grafana / Datadog
- **Alerts**: PagerDuty / OpsGenie

**Benefits**:
- Real-time alerting
- Full request/response context
- Stack traces and debugging info
- Aggregated error analysis
- Integration with incident management

---

## 9. Data Flow Diagrams

### 9.1 BMS Interface Flow
```
User (3270 Terminal)
  ↓ Types "OMEN"
CICS Region
  ↓ Invokes transaction OMEN
BNKMENU Program
  ↓ Sends map BNK1MAI
User
  ↓ Selects option 4 (Create Account)
BNKMENU
  ↓ Transfers control
BNK1CAC Program
  ↓ Sends map BNK1CAM
User
  ↓ Enters account details
BNK1CAC
  ↓ Validates input
  ↓ EXEC CICS LINK PROGRAM('CREACC')
CREACC Program
  ↓ Calls INQCUST, INQACCCU
  ↓ Generates account number (Named Counter)
  ↓ Inserts to ACCOUNT, PROCTRAN
  ↓ Returns result
BNK1CAC
  ↓ Formats response on BNK1CAM
User
  ↓ Sees confirmation
```

### 9.2 REST API Flow (via z/OS Connect)
```
External Client
  ↓ POST /api/customers (JSON)
Spring Boot Application
  ↓ REST Controller
z/OS Connect Server
  ↓ JSON → COMMAREA transformation
  ↓ EXEC CICS LINK PROGRAM('CRECUST')
CICS Region
  ↓ Routes to CRECUST
CRECUST Program
  ↓ Validates input
  ↓ Generates customer number
  ↓ Starts credit check transactions (OCR1-5)
  ↓ Waits for responses
  ↓ Aggregates scores
  ↓ Writes to CUSTOMER VSAM
  ↓ Logs to PROCTRAN
  ↓ Returns result in COMMAREA
z/OS Connect
  ↓ COMMAREA → JSON transformation
Spring Boot
  ↓ Returns HTTP 201 Created
External Client
  ↓ Receives JSON response
```

### 9.3 Credit Check Flow
```
CRECUST Program
  ↓ Generates customer number
  ↓ Creates channels for credit checks
  ├─→ EXEC CICS START TRANSID('OCR1') CHANNEL(ch1)
  │   ↓ CRDTAGY1 Program
  │   ↓ (Calls external agency API)
  │   ↓ Writes score to output container
  │   └─→ Returns
  ├─→ EXEC CICS START TRANSID('OCR2') ... (similar)
  ├─→ EXEC CICS START TRANSID('OCR3') ...
  ├─→ EXEC CICS START TRANSID('OCR4') ...
  └─→ EXEC CICS START TRANSID('OCR5') ...
  ↓ Waits 3 seconds
  ↓ Reads scores from output containers
  ↓ Aggregates: (score1 + score2 + ...) / count
  ↓ Uses aggregated score for customer
```

---

## 10. Integration Security

### 10.1 Current Security Model

#### 10.1.1 CICS Security
- **Transaction Security**: Controls which users can invoke which transactions
- **Program Security**: Controls which programs can be called
- **Resource Security**: Controls access to files, databases
- **Command Security**: Controls which CICS commands can be used

**Source**: `BANK.csd` RESSEC and CMDSEC attributes

#### 10.1.2 z/OS Connect Security
⚠️ **Unknown**: Exact authentication/authorization model for z/OS Connect

Likely includes:
- HTTP Basic Authentication or OAuth2
- API key-based authentication
- Role-based access control
- TLS/SSL for encryption in transit

#### 10.1.3 Liberty Security
⚠️ **Unknown**: Security configuration for Liberty JVM server

### 10.2 Modern Security Requirements

#### 10.2.1 Authentication
**JWT-based Authentication**:
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .authorizeRequests()
                .antMatchers("/api/public/**").permitAll()
                .antMatchers("/api/**").authenticated()
            .and()
            .oauth2ResourceServer()
                .jwt();
    }
}
```

#### 10.2.2 Authorization
**Role-Based Access Control**:
```java
@PreAuthorize("hasRole('TELLER')")
@PostMapping("/api/accounts")
public ResponseEntity<AccountDTO> createAccount(@RequestBody CreateAccountRequest request) {
    // Only users with TELLER role can create accounts
}

@PreAuthorize("hasAnyRole('TELLER', 'MANAGER')")
@DeleteMapping("/api/customers/{id}")
public ResponseEntity<Void> deleteCustomer(@PathVariable String id) {
    // Only TELLER or MANAGER can delete customers
}

@PreAuthorize("hasRole('AUDITOR') or hasRole('MANAGER')")
@GetMapping("/api/transactions")
public ResponseEntity<List<TransactionDTO>> getTransactions() {
    // Only AUDITOR or MANAGER can view all transactions
}
```

#### 10.2.3 Data Protection
- **TLS/HTTPS**: All communication encrypted in transit
- **Encryption at Rest**: Sensitive PII encrypted in database
- **Field-Level Encryption**: Credit card numbers, SSN, etc.
- **Data Masking**: Logs don't expose sensitive data

```java
@Entity
public class Customer {
    @Id
    private Long id;
    
    @Convert(converter = EncryptedStringConverter.class)
    private String ssn; // Encrypted in database
    
    @JsonIgnore // Not exposed in JSON responses
    private String encryptedPassword;
    
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password; // Write-only, never read
}
```

#### 10.2.4 API Security
**Rate Limiting**:
```java
@Configuration
public class RateLimitConfig {
    
    @Bean
    public RateLimiter rateLimiter() {
        return RateLimiter.create(100.0); // 100 requests per second
    }
}
```

**Request Validation**:
```java
@PostMapping("/api/customers")
public ResponseEntity<CustomerDTO> createCustomer(
    @Valid @RequestBody CreateCustomerRequest request,
    @RequestHeader("X-Idempotency-Key") String idempotencyKey
) {
    // @Valid triggers Bean Validation
    // Idempotency key prevents duplicate requests
}
```

---

## 11. Performance and Scalability

### 11.1 Current System Constraints

#### 11.1.1 CICS Limitations
- Fixed number of concurrent transactions (based on CICS region config)
- VSAM STRINGS(20) limits concurrent file access
- Single CICS region = single point of failure

#### 11.1.2 z/OS Connect Overhead
- Additional network hop (Spring Boot → z/OS Connect → CICS)
- JSON ↔ COMMAREA transformation overhead
- Serialization bottleneck

### 11.2 Modern Architecture Benefits

#### 11.2.1 Horizontal Scaling
```
Load Balancer
  ├─→ Spring Boot Instance 1 (Pod)
  ├─→ Spring Boot Instance 2 (Pod)
  ├─→ Spring Boot Instance 3 (Pod)
  └─→ ... (auto-scale based on load)
       ↓
    PostgreSQL (Primary + Replicas)
```

#### 11.2.2 Caching Strategy
```java
@Cacheable(value = "customers", key = "#customerId")
public Customer findById(String customerId) {
    return customerRepository.findByCustomerNumber(customerId)
        .orElseThrow(() -> new CustomerNotFoundException(customerId));
}

@CacheEvict(value = "customers", key = "#customer.customerId")
public Customer updateCustomer(Customer customer) {
    return customerRepository.save(customer);
}
```

**Cache Backends**: Redis, Memcached, Hazelcast

#### 11.2.3 Database Optimization
- Read replicas for read-heavy queries
- Connection pooling (HikariCP)
- Query optimization and indexing
- Partitioning for large tables

---

## 12. Modernization Roadmap

### 12.1 Integration Migration Strategy

#### 12.1.1 Phase 1: API Gateway
Add API Gateway in front of z/OS Connect:
```
Client → API Gateway → z/OS Connect → CICS → COBOL
           ↓ (auth, rate limit, logging)
```

**Benefits**:
- Centralized auth/authorization
- Rate limiting
- API versioning
- Analytics

#### 12.1.2 Phase 2: Strangler Fig Pattern
Gradually migrate COBOL programs to Java services:
```
Client → API Gateway → ┌→ z/OS Connect → CICS → COBOL (legacy)
                       └→ Spring Boot Services → PostgreSQL (new)
```

Route by endpoint:
- `/api/v1/customers` → Legacy (COBOL)
- `/api/v2/customers` → New (Java)

#### 12.1.3 Phase 3: Complete Migration
All traffic to new services:
```
Client → API Gateway → Spring Boot Services → PostgreSQL
```

Decommission:
- z/OS Connect Server
- CICS region (or repurpose)
- COBOL programs
- VSAM files

### 12.2 Credit Agency Integration Migration

**Current**: CICS child transactions (OCR1-5)
**Target**: REST API calls or message queue

**Implementation**:
```java
@Service
public class CreditCheckService {
    
    @Value("${credit.agencies}")
    private List<CreditAgencyConfig> agencies;
    
    private final RestTemplate restTemplate;
    private final ExecutorService executor;
    
    public int aggregateCreditScores(Customer customer) {
        List<CompletableFuture<Integer>> futures = agencies.stream()
            .map(agency -> CompletableFuture.supplyAsync(
                () -> callAgency(agency, customer), 
                executor
            ))
            .collect(Collectors.toList());
        
        // Wait up to 3 seconds
        List<Integer> scores = futures.stream()
            .map(f -> f.completeOnTimeout(null, 3, TimeUnit.SECONDS))
            .map(CompletableFuture::join)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        return scores.isEmpty() ? 500 : 
            (int) scores.stream().mapToInt(Integer::intValue).average().orElse(500);
    }
    
    private Integer callAgency(CreditAgencyConfig agency, Customer customer) {
        try {
            CreditCheckRequest request = buildRequest(customer);
            CreditCheckResponse response = restTemplate.postForObject(
                agency.getUrl(), 
                request, 
                CreditCheckResponse.class
            );
            return response.getScore();
        } catch (Exception e) {
            logger.warn("Credit check failed for agency {}", agency.getName(), e);
            return null;
        }
    }
}
```

---

## 13. Open Questions

⚠️ **Integration Decisions Required**:
1. What is the exact authentication/authorization model for z/OS Connect?
2. What are the SLAs for credit agency integrations?
3. Should modernized system use sync or async credit checks?
4. What are the external agency API protocols (REST, SOAP, proprietary)?
5. Should we implement circuit breakers for external dependencies?
6. What is the disaster recovery strategy for external integrations?

⚠️ **Unknown Details**:
1. Exact z/OS Connect configuration and service definitions
2. Liberty JVM server configuration
3. CICS transaction definitions beyond OMEN and OCR1-5
4. Credit agency program (CRDTAGY1-5) implementation details
5. Network topology and firewall rules
6. Current monitoring and alerting setup

---

*Document Version: 1.0*  
*Last Updated: 2025-10-27*  
*Source Repository: taylor-curran/og-cics-cobol-app*
