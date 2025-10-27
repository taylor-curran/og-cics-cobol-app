# Data & Interface Requirements

## 1. Introduction

### 1.1 Document Purpose
This document specifies the data structures, data stores, and interface layers of the CICS Bank Sample Application (CBSA). It provides the foundation for understanding data flows, storage requirements, and integration points needed for modernization to a Java-based technology stack.

**Source**: Analysis of copybooks in `src/base/cobol_copy/`, CICS resource definitions in `etc/install/base/installjcl/BANK.csd`, and architecture documentation in `doc/CBSA_Architecture_guide.md`

### 1.2 Data Architecture Overview
CBSA uses a multi-tiered data architecture:
- **Db2 Relational Tables**: ACCOUNT (account records), PROCTRAN (transaction log), CONTROL (named counter state)
- **VSAM Files**: CUSTOMER (customer records), ABNDFILE (error logs)
- **In-Memory**: CICS Named Counters for ID generation
- **Interface Layers**: BMS 3270 maps, Carbon React components, Spring Boot REST APIs

---

## 2. Db2 Table Structures

### 2.1 ACCOUNT Table

#### 2.1.1 Purpose
Stores all bank account information including balances, account type, overdraft limits, and statement dates. Primary transactional data store for account operations.

**Source**: `src/base/cobol_copy/ACCOUNT.cpy`, `src/base/cobol_copy/ACCDB2.cpy`

#### 2.1.2 Field Definitions

| Field Name | COBOL Type | Size | Db2 Type | Description | Constraints |
|------------|------------|------|----------|-------------|-------------|
| ACCOUNT_EYECATCHER | PIC X(4) | 4 | CHAR(4) | Record type identifier | Value='ACCT' |
| ACCOUNT_CUSTOMER_NUMBER | PIC 9(10) | 10 | DECIMAL(10,0) | Customer ID (foreign key) | References CUSTOMER |
| ACCOUNT_SORTCODE | PIC 9(6) | 6 | DECIMAL(6,0) | Bank sort code (part of key) | System-defined |
| ACCOUNT_NUMBER | PIC 9(8) | 8 | DECIMAL(8,0) | Account number (part of key) | Unique per sort code |
| ACCOUNT_TYPE | PIC X(8) | 8 | CHAR(8) | Account category | CURRENT, SAVINGS, LOAN, MORTGAGE, ISA |
| ACCOUNT_INTEREST_RATE | PIC 9(4)V99 | 6 | DECIMAL(6,2) | Annual interest rate percentage | 0.00 to 9999.99 |
| ACCOUNT_OPENED | PIC 9(8) | 8 | DECIMAL(8,0) | Date account opened | Format: DDMMYYYY |
| ACCOUNT_OVERDRAFT_LIMIT | PIC 9(8) | 8 | DECIMAL(8,0) | Overdraft limit amount | 0 to 99999999 |
| ACCOUNT_LAST_STATEMENT | PIC 9(8) | 8 | DECIMAL(8,0) | Last statement date | Format: DDMMYYYY |
| ACCOUNT_NEXT_STATEMENT | PIC 9(8) | 8 | DECIMAL(8,0) | Next statement date | Format: DDMMYYYY |
| ACCOUNT_AVAILABLE_BALANCE | PIC S9(10)V99 COMP-3 | 6 | DECIMAL(12,2) | Available balance (includes overdraft) | Signed, can be negative |
| ACCOUNT_ACTUAL_BALANCE | PIC S9(10)V99 COMP-3 | 6 | DECIMAL(12,2) | Actual balance | Signed, can be negative |

**Source**: Lines 8-35 of `ACCOUNT.cpy`

#### 2.1.3 Primary Key
- **Composite Key**: (ACCOUNT_SORTCODE, ACCOUNT_NUMBER)
- **Uniqueness**: Enforced at Db2 level
- **Index**: Primary index on composite key

#### 2.1.4 Indexes
⚠️ **Unknown**: Additional indexes beyond primary key (needs investigation of Db2 DDL)
- Likely index on ACCOUNT_CUSTOMER_NUMBER for customer account lookups

#### 2.1.5 Business Rules
- **Date Format**: All dates stored as DDMMYYYY (8 digits)
- **Balance Precision**: Two decimal places (represents cents/pence)
- **Negative Balances**: Permitted up to overdraft limit
- **Account Types**: Limited to 5 predefined values (validated at application layer)

#### 2.1.6 Modernization Mapping
```sql
CREATE TABLE accounts (
    account_id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(customer_id),
    sort_code VARCHAR(6) NOT NULL,
    account_number VARCHAR(8) NOT NULL,
    account_type VARCHAR(20) NOT NULL CHECK (account_type IN ('CURRENT', 'SAVINGS', 'LOAN', 'MORTGAGE', 'ISA')),
    interest_rate DECIMAL(6,2) NOT NULL DEFAULT 0.00,
    opened_date DATE NOT NULL,
    overdraft_limit DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    last_statement_date DATE,
    next_statement_date DATE,
    available_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    actual_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (sort_code, account_number)
);

CREATE INDEX idx_accounts_customer ON accounts(customer_id);
```

**Changes from Legacy**:
- Add surrogate key `account_id` for simpler references
- Change date format from DDMMYYYY to DATE type
- Add audit timestamps (created_at, updated_at)
- Add CHECK constraint for account_type
- Increase precision for monetary fields

### 2.2 PROCTRAN Table

#### 2.2.1 Purpose
Transaction audit log capturing all successful write operations (create, delete, debit, credit, transfer). Primary compliance and reconciliation data store.

**Source**: `src/base/cobol_copy/PROCTRAN.cpy`, `src/base/cobol_copy/PROCDB2.cpy`

#### 2.2.2 Field Definitions

| Field Name | COBOL Type | Size | Db2 Type | Description | Constraints |
|------------|------------|------|----------|-------------|-------------|
| PROC_TRAN_EYECATCHER | PIC X(4) | 4 | CHAR(4) | Record type identifier | Value='PRTR' |
| PROC_TRAN_SORTCODE | PIC 9(6) | 6 | DECIMAL(6,0) | Sort code of account | Part of composite ID |
| PROC_TRAN_NUMBER | PIC 9(8) | 8 | DECIMAL(8,0) | Transaction number | Part of composite ID |
| PROC_TRAN_DATE | PIC 9(8) | 8 | DECIMAL(8,0) | Transaction date | Format: YYYYMMDD |
| PROC_TRAN_TIME | PIC 9(6) | 6 | DECIMAL(6,0) | Transaction time | Format: HHMMSS |
| PROC_TRAN_REF | PIC 9(12) | 12 | DECIMAL(12,0) | Transaction reference | Unique reference number |
| PROC_TRAN_TYPE | PIC X(3) | 3 | CHAR(3) | Transaction type code | See type codes below |
| PROC_TRAN_DESC | PIC X(40) | 40 | CHAR(40) | Transaction description | Context-specific format |
| PROC_TRAN_AMOUNT | PIC S9(10)V99 COMP-3 | 6 | DECIMAL(12,2) | Transaction amount | Signed, can be negative |

**Source**: Lines 8-103 of `PROCTRAN.cpy`

#### 2.2.3 Transaction Type Codes

| Code | Description | Logged By | Amount Significance |
|------|-------------|-----------|---------------------|
| OCC | Branch Create Customer | CRECUST | 0.00 |
| ODC | Branch Delete Customer | DELCUS | 0.00 |
| OCA | Branch Create Account | CREACC | 0.00 (initial balance) |
| ODA | Branch Delete Account | DELACC | Final balance |
| DEB | Debit Transaction | DBCRFUN | Negative amount |
| CRE | Credit Transaction | DBCRFUN | Positive amount |
| TFR | Transfer Between Accounts | XFRFUN | Transfer amount |
| ICC | Web Create Customer | CRECUST (API) | 0.00 |
| IDC | Web Delete Customer | DELCUS (API) | 0.00 |
| ICA | Web Create Account | CREACC (API) | 0.00 |
| IDA | Web Delete Account | DELACC (API) | Final balance |
| PCR | Payment Credit | DBCRFUN (Payment API) | Positive amount |
| PDR | Payment Debit | DBCRFUN (Payment API) | Negative amount |

**Source**: Lines 30-47 of `PROCTRAN.cpy`

#### 2.2.4 Description Field Format

The PROC_TRAN_DESC field uses different formats depending on transaction type:

**Transfer (TFR)**:
- Format: "TRANSFER" + destination_sortcode(6) + destination_account(8)
- Example: "TRANSFER000001012345678"

**Create Account (OCA/ICA)**:
- Format: customer_no(10) + account_type(8) + last_stmt_date(8) + next_stmt_date(8) + "CREATE"(6)

**Delete Account (ODA/IDA)**:
- Format: customer_no(10) + account_type(8) + last_stmt_date(8) + next_stmt_date(8) + "DELETE"(6)

**Create Customer (OCC/ICC)**:
- Format: sortcode(6) + customer_no(10) + name(14) + DOB_YYYY(4) + "-" + DOB_MM(2) + "-" + DOB_DD(2)

**Delete Customer (ODC/IDC)**:
- Format: sortcode(6) + customer_no(10) + name(14) + DOB_YYYY(4) + "-" + DOB_MM(2) + "-" + DOB_DD(2)

**Debit/Credit (DEB/CRE/PCR/PDR)**:
- Format: Free-form description (up to 40 characters)

**Source**: Lines 49-102 of `PROCTRAN.cpy`

#### 2.2.5 Primary Key
- **Composite Key**: (PROC_TRAN_SORTCODE, PROC_TRAN_NUMBER)
- **Generation**: Transaction number likely from Named Counter or timestamp-based

#### 2.2.6 Indexes
⚠️ **Assumption**: Likely additional indexes on:
- PROC_TRAN_DATE for date-range queries
- PROC_TRAN_REF for lookup by reference
- (PROC_TRAN_SORTCODE, PROC_TRAN_DATE) for account transaction history

#### 2.2.7 Modernization Mapping
```sql
CREATE TABLE transactions (
    transaction_id BIGSERIAL PRIMARY KEY,
    account_id BIGINT REFERENCES accounts(account_id),
    sort_code VARCHAR(6) NOT NULL,
    account_number VARCHAR(8) NOT NULL,
    transaction_date DATE NOT NULL,
    transaction_time TIME NOT NULL,
    transaction_timestamp TIMESTAMP NOT NULL,
    reference_number VARCHAR(20) UNIQUE NOT NULL,
    transaction_type VARCHAR(10) NOT NULL,
    description TEXT,
    amount DECIMAL(12,2) NOT NULL,
    related_account_id BIGINT REFERENCES accounts(account_id), -- For transfers
    created_by VARCHAR(50), -- User identity
    created_from VARCHAR(50), -- IP address or system
    correlation_id UUID, -- For request tracing
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transactions_account ON transactions(account_id);
CREATE INDEX idx_transactions_date ON transactions(transaction_date);
CREATE INDEX idx_transactions_reference ON transactions(reference_number);
CREATE INDEX idx_transactions_type ON transactions(transaction_type);
```

**Enhancements for Modern System**:
- Add surrogate key and account_id foreign key
- Separate date and time into proper DATE/TIME/TIMESTAMP types
- Add user identity and IP address for enhanced audit
- Add correlation_id for distributed tracing
- Add related_account_id for transfers
- Flexible TEXT description field
- More descriptive transaction_type values

### 2.3 CONTROL Table

#### 2.3.1 Purpose
Stores state for Named Counters used to generate unique customer and account numbers. Acts as persistent store for counter values.

**Source**: Referenced in `CREACC.cbl` and `CRECUST.cbl`, copybook `CONTDB2.cpy`

#### 2.3.2 Field Definitions
⚠️ **Assumption**: Based on typical counter table patterns, likely structure:

| Field Name | Type | Description |
|------------|------|-------------|
| COUNTER_NAME | CHAR(20) | Counter identifier (e.g., 'CBSAACCT', 'CBSACUST') |
| COUNTER_VALUE | DECIMAL(10,0) | Current counter value |
| LAST_UPDATED | TIMESTAMP | Last update timestamp |

#### 2.3.3 Known Counter Names
- **CBSAACCT**: Account number generator (8-digit numbers)
- **CBSACUST**: Customer number generator (10-digit numbers)

**Source**: Referenced in `CREACC.cbl` lines 447-683 and `CRECUST.cbl` lines 108-142

#### 2.3.4 Concurrency Control
- Programs use CICS ENQ/DEQ for serialized access
- Lock resource name matches counter name
- Retry logic if ENQ fails

#### 2.3.5 Modernization Mapping
```sql
-- Option 1: Use database sequences (preferred)
CREATE SEQUENCE customer_number_seq START WITH 1000000001;
CREATE SEQUENCE account_number_seq START WITH 10000001;

-- Option 2: Counter table for compatibility
CREATE TABLE counters (
    counter_name VARCHAR(50) PRIMARY KEY,
    counter_value BIGINT NOT NULL,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_counter_value CHECK (counter_value >= 0)
);

-- Option 3: Use UUIDs (recommended for cloud-native)
-- No counter table needed, generate UUID.randomUUID() in application
```

**Recommendation**: Use database sequences or UUIDs to eliminate need for application-level locking.

---

## 3. VSAM File Structures

### 3.1 CUSTOMER File

#### 3.1.1 Purpose
Stores customer master data including personal information, address, date of birth, and credit score. Primary data store for customer records.

**Source**: `src/base/cobol_copy/CUSTOMER.cpy`, CICS FILE definition in `BANK.csd` lines 10-21

#### 3.1.2 VSAM Configuration
- **Type**: KSDS (Key Sequenced Data Set)
- **Key**: Composite (CUSTOMER_SORTCODE + CUSTOMER_NUMBER), length 16 bytes
- **Record Size**: Variable (259 bytes maximum)
- **Attributes**: 
  - STRINGS(20) - maximum 20 concurrent read strings
  - DATABUFFERS(21)
  - INDEXBUFFERS(20)
  - UPDATEMODEL(LOCKING) - record-level locking
  - RECOVERY(BACKOUTONLY) - supports rollback but not forward recovery

**Source**: Lines 10-21 of `BANK.csd`

#### 3.1.3 Field Definitions

| Field Name | COBOL Type | Size | Description | Constraints |
|------------|------------|------|-------------|-------------|
| CUSTOMER_EYECATCHER | PIC X(4) | 4 | Record type identifier | Value='CUST' |
| CUSTOMER_SORTCODE | PIC 9(6) DISPLAY | 6 | Bank sort code (key part 1) | System-defined |
| CUSTOMER_NUMBER | PIC 9(10) DISPLAY | 10 | Customer number (key part 2) | Unique, from Named Counter |
| CUSTOMER_NAME | PIC X(60) | 60 | Full customer name | Includes title |
| CUSTOMER_ADDRESS | PIC X(160) | 160 | Full address | Multi-line |
| CUSTOMER_DATE_OF_BIRTH | PIC 9(8) | 8 | Date of birth | Format: DDMMYYYY |
| CUSTOMER_CREDIT_SCORE | PIC 999 | 3 | Credit score | 0-999 |
| CUSTOMER_CS_REVIEW_DATE | PIC 9(8) | 8 | Credit score review date | Format: DDMMYYYY |

**Source**: Lines 8-34 of `CUSTOMER.cpy`

#### 3.1.4 Name and Address Structure
The copybook shows commented-out subfields suggesting original intention:
- **Name**: Title(8) + Given_Name(20) + Initials(10) + Family_Name(20) = 58 chars (but stored as single 60-char field)
- **Address**: Street(50) + District(50) + Town(50) + Postcode(10) = 160 chars (but stored as single 160-char field)

**Current Implementation**: Stores as free-form text fields without enforced structure.

**Source**: Lines 14-22 of `CUSTOMER.cpy` (commented sections)

#### 3.1.5 Business Rules
- **Title Validation**: First word of name must be valid title (Professor, Mr, Mrs, Miss, Ms, Dr, Drs, Lord, Sir, Lady)
- **DOB Validation**: Year >= 1601, age <= 150 years, not in future
- **Credit Score**: Updated asynchronously by credit agency checks
- **Review Date**: Set when credit score updated

#### 3.1.6 Modernization Mapping
```sql
CREATE TABLE customers (
    customer_id BIGSERIAL PRIMARY KEY,
    sort_code VARCHAR(6) NOT NULL,
    customer_number VARCHAR(10) NOT NULL,
    title VARCHAR(20),
    full_name VARCHAR(100) NOT NULL,
    address_line1 VARCHAR(100),
    address_line2 VARCHAR(100),
    city VARCHAR(50),
    postal_code VARCHAR(20),
    country VARCHAR(50) DEFAULT 'United Kingdom',
    date_of_birth DATE NOT NULL,
    credit_score INT CHECK (credit_score BETWEEN 0 AND 999),
    credit_score_review_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (sort_code, customer_number),
    CONSTRAINT check_dob CHECK (date_of_birth <= CURRENT_DATE),
    CONSTRAINT check_age CHECK (date_of_birth >= DATE '1601-01-01')
);

CREATE INDEX idx_customers_sortcode_number ON customers(sort_code, customer_number);
CREATE INDEX idx_customers_name ON customers(full_name);
```

**Changes from Legacy**:
- Add surrogate key for simpler references
- Migrate from VSAM to relational database
- Split address into structured fields (line1, line2, city, postal_code)
- Separate title from name for better querying
- Change date format from DDMMYYYY to DATE type
- Add audit timestamps
- Add database constraints for validation

### 3.2 ABNDFILE File

#### 3.2.1 Purpose
Stores error and abend (abnormal end) information for troubleshooting and debugging. Written by ABNDPROC when programs encounter unrecoverable errors.

**Source**: CICS FILE definition in `BANK.csd` lines 23-34, referenced in multiple programs

#### 3.2.2 VSAM Configuration
- **Type**: KSDS (Key Sequenced Data Set)
- **Key**: 12 bytes (likely timestamp-based)
- **Record Size**: Variable (681 bytes maximum)
- **Attributes**:
  - STRINGS(20)
  - DATABUFFERS(21)
  - INDEXBUFFERS(20)
  - OPENTIME(STARTUP) - opened when CICS region starts
  - RECOVERY(NONE) - no transaction rollback support

**Source**: Lines 23-34 of `BANK.csd`

#### 3.2.3 Field Definitions
⚠️ **Unknown**: Exact field structure (would need to examine ABNDINFO copybook or ABNDPROC program)

**Source**: Referenced in `ABNDINFO.cpy` copybook

Likely fields based on typical abend logging:
- Abend code
- Program name
- Transaction ID
- User ID
- Timestamp
- CICS response codes
- SQL codes (if applicable)
- Error message/description

#### 3.2.4 Modernization Mapping
**Recommendation**: Replace VSAM error file with modern logging infrastructure:
- Application logging framework (Logback/Log4j2)
- Centralized log aggregation (ELK Stack, Splunk, CloudWatch)
- Distributed tracing (Jaeger, Zipkin)
- Error tracking service (Sentry, Rollbar)

```java
// Modern equivalent using structured logging
logger.error("Account creation failed", 
    kv("customerNumber", customerNumber),
    kv("accountType", accountType),
    kv("errorCode", errorCode),
    kv("sqlCode", sqlCode),
    kv("transactionId", transactionId));
```

---

## 4. Interface Layer Definitions

### 4.1 BMS 3270 Maps

#### 4.1.1 Purpose
Define screen layouts and field mappings for 3270 terminal interface. Handle input/output between CICS programs and terminal screens.

**Source**: `src/base/bms_src/*.bms`, MAPSET definitions in `BANK.csd` lines 36-74

#### 4.1.2 Defined Mapsets

| Mapset Name | Description | Associated Program | Source |
|-------------|-------------|-------------------|--------|
| BNK1MAI | Main Menu | BNKMENU | BNK1MAI.bms |
| BNK1ACC | Account Inquiry by Customer | BNK1CCA | BNK1ACC.bms |
| BNK1CAM | Create Account | BNK1CAC | BNK1CAM.bms |
| BNK1CCM | Create Customer | BNK1CCS | BNK1CCM.bms |
| BNK1CDM | Credit/Debit Account | BNK1CRA | BNK1CDM.bms |
| BNK1DAM | Display Account | BNK1DAC | BNK1DAM.bms |
| BNK1DCM | Display Customer | BNK1DCS | BNK1DCM.bms |
| BNK1TFM | Transfer Funds | BNK1TFN | BNK1TFM.bms |
| BNK1UAM | Update Account | BNK1UAC | BNK1UAM.bms |
| BNK1B2M | Bank to Bank Transfer | BNK1B2B | BNK1B2M.bms |

**Source**: Lines 36-74 of `BANK.csd`

#### 4.1.3 BNK1MAI Main Menu Structure
- **Screen Size**: 24 rows x 80 columns (standard 3270)
- **Input Fields**: 
  - ACTION (position 5,16): 1 character for menu selection
- **Display Fields**:
  - Title bar (row 1)
  - Menu options 1-7 and A (rows 5-11,13)
  - Message area (row 23)
  - Function key help (row 24)
- **Function Keys**: F3=Exit, F12=Cancel
- **Color Attributes**: Blue titles, Turquoise prompts, Yellow messages, Green input fields

**Source**: `BNK1MAI.bms` lines 24-62

#### 4.1.4 Common Field Attributes
- **PROT**: Protected (display-only)
- **UNPROT**: Unprotected (input-allowed)
- **ASKIP**: Auto-skip (tab moves to next field)
- **IC**: Initial cursor position
- **BRT/NORM/DRK**: Brightness levels
- **HILIGHT**: Underlining
- **Colors**: BLUE, TURQUOISE, GREEN, YELLOW, RED, NEUTRAL

#### 4.1.5 Modernization Mapping
**Replace with React Components**:

```jsx
// BNK1MAI equivalent
function MainMenu() {
  return (
    <Container>
      <Header title="CICS Banking Sample Application - Main Menu" />
      <MenuOptions>
        <MenuItem value="1" label="Display/Delete/Update CUSTOMER information" />
        <MenuItem value="2" label="Display/Delete ACCOUNT information" />
        <MenuItem value="3" label="Create CUSTOMER" />
        <MenuItem value="4" label="Create ACCOUNT" />
        <MenuItem value="5" label="Update ACCOUNT" />
        <MenuItem value="6" label="Credit/Debit funds to an ACCOUNT" />
        <MenuItem value="7" label="Transfer funds" />
        <MenuItem value="A" label="Look up Accounts with Customer Number" />
      </MenuOptions>
      <MessageArea>{message}</MessageArea>
    </Container>
  );
}
```

**Each BMS map** becomes a React form component with:
- State management (React hooks or Redux)
- Form validation (Yup, React Hook Form)
- API calls (Axios, fetch)
- Error handling and display
- Responsive design for mobile

### 4.2 Carbon React UI

#### 4.2.1 Architecture
- **Platform**: Liberty JVM Server (CBSAWLP) running in CICS region
- **Technology**: 
  - Carbon Design System (IBM's React component library)
  - React frontend
  - CICS Java APIs for backend communication
- **Communication**: React → Liberty → CICS Java APIs → COBOL programs

**Source**: `doc/CBSA_Architecture_guide.md` lines 76-90

#### 4.2.2 Component Structure
⚠️ **Unknown**: Exact React component structure (would need to examine `src/webui/` directory)

Likely structure:
- Main routing component
- Menu/navigation component
- Customer management components
- Account management components
- Transaction components
- Shared UI components (forms, tables, dialogs)

#### 4.2.3 API Communication
Uses CICS Java APIs:
- Program.link() - synchronous program calls
- Channel/Container - data passing mechanism
- Transaction.current() - transaction context

#### 4.2.4 Modernization Mapping
**Migrate to Standalone React App**:
- Remove Liberty/CICS dependency
- Replace CICS Java API calls with REST API calls
- Deploy as static SPA (S3, CDN, Netlify)
- Add authentication (JWT, OAuth2)
- Implement state management (Redux, MobX, Zustand)

### 4.3 Spring Boot REST APIs

#### 4.3.1 Customer Services Interface
Provides REST APIs for customer operations via z/OS Connect Server.

**Source**: Referenced in `doc/CBSA_Architecture_guide.md` and Spring Boot guide

**Endpoints** (inferred from COBOL programs):
- `GET /customers/{customerId}` - INQCUST
- `POST /customers` - CRECUST
- `PATCH /customers/{customerId}` - UPDCUST
- `DELETE /customers/{customerId}` - DELCUS

#### 4.3.2 Payment Services Interface
Provides REST APIs for account and transaction operations via z/OS Connect Server.

**Endpoints** (inferred):
- `GET /accounts/{accountId}` - INQACC
- `POST /accounts` - CREACC
- `PATCH /accounts/{accountId}` - UPDACC
- `DELETE /accounts/{accountId}` - DELACC
- `GET /customers/{customerId}/accounts` - INQACCCU
- `POST /accounts/{accountId}/transactions` - DBCRFUN
- `POST /transfers` - XFRFUN

#### 4.3.3 z/OS Connect Integration
- **Protocol**: HTTP/HTTPS
- **Format**: JSON request/response
- **Authentication**: ⚠️ **Unknown** (needs investigation)
- **Error Handling**: Maps COBOL fail codes to HTTP status codes

**Architecture Flow**:
```
Client → Spring Boot → z/OS Connect Server → CICS → COBOL Program → Data Store
```

#### 4.3.4 Request/Response Examples

**POST /customers (Create Customer)**:
```json
// Request
{
  "name": "Mr John Smith",
  "address": "123 Main Street, London, SW1A 1AA",
  "dateOfBirth": "1980-05-15"
}

// Response (201 Created)
{
  "customerId": "0001234567",
  "sortCode": "000001",
  "name": "Mr John Smith",
  "address": "123 Main Street, London, SW1A 1AA",
  "dateOfBirth": "1980-05-15",
  "creditScore": 725,
  "creditScoreReviewDate": "2025-10-27"
}
```

**POST /accounts (Create Account)**:
```json
// Request
{
  "customerId": "0001234567",
  "accountType": "CURRENT",
  "interestRate": 0.50,
  "overdraftLimit": 1000.00
}

// Response (201 Created)
{
  "accountId": "12345678",
  "sortCode": "000001",
  "customerId": "0001234567",
  "accountType": "CURRENT",
  "interestRate": 0.50,
  "openedDate": "2025-10-27",
  "overdraftLimit": 1000.00,
  "availableBalance": 0.00,
  "actualBalance": 0.00
}
```

**POST /transfers (Transfer Funds)**:
```json
// Request
{
  "fromAccount": {
    "sortCode": "000001",
    "accountNumber": "12345678"
  },
  "toAccount": {
    "sortCode": "000001",
    "accountNumber": "87654321"
  },
  "amount": 150.00,
  "description": "Payment"
}

// Response (201 Created)
{
  "transactionId": "TXN20251027001",
  "reference": "123456789012",
  "timestamp": "2025-10-27T13:45:30Z",
  "fromAccount": {
    "sortCode": "000001",
    "accountNumber": "12345678",
    "newBalance": 850.00
  },
  "toAccount": {
    "sortCode": "000001",
    "accountNumber": "87654321",
    "newBalance": 650.00
  },
  "amount": 150.00,
  "status": "COMPLETED"
}
```

#### 4.3.5 Error Response Format
```json
{
  "error": {
    "code": "INSUFFICIENT_FUNDS",
    "message": "Account does not have sufficient funds for this transaction",
    "failCode": "3",
    "timestamp": "2025-10-27T13:45:30Z",
    "path": "/api/accounts/12345678/transactions"
  }
}
```

#### 4.3.6 Modernization Mapping
**Replace z/OS Connect with Native Spring Boot**:
- Remove z/OS Connect intermediary
- Implement business logic directly in Spring Boot services
- Port COBOL logic to Java
- Use Spring Data JPA for database access
- Implement proper REST standards (HATEOAS, pagination, filtering)

---

## 5. Data Flow Diagrams

### 5.1 Customer Creation Flow
```
User Input → BMS/React/API → BNK1CCS/Controller → CRECUST Program
                                                        ↓
                                    ┌───────────────────┴───────────────────┐
                                    ↓                                       ↓
                            Named Counter                          Credit Agencies
                            (CBSACUST)                             (CRDTAGY1-5)
                                    ↓                                       ↓
                            Customer Number                        Credit Scores
                                    ↓                                       ↓
                                    └───────────────────┬───────────────────┘
                                                        ↓
                                            CUSTOMER VSAM (Write)
                                                        ↓
                                            PROCTRAN Db2 (Log)
                                                        ↓
                                            Response to User
```

### 5.2 Transfer Funds Flow
```
User Input → BMS/React/API → BNK1TFN/Controller → XFRFUN Program
                                                        ↓
                                    ┌───────────────────┴───────────────────┐
                                    ↓                                       ↓
                            Source Account Query                  Dest Account Query
                            ACCOUNT Db2                           ACCOUNT Db2
                                    ↓                                       ↓
                            Validate & Lock                       Validate & Lock
                            (Lower ID first)                      (Lower ID first)
                                    ↓                                       ↓
                            Debit Source                          Credit Destination
                            ACCOUNT Db2                           ACCOUNT Db2
                                    ↓                                       ↓
                                    └───────────────────┬───────────────────┘
                                                        ↓
                                            Log Transaction
                                            PROCTRAN Db2
                                                        ↓
                                    (If any step fails: SYNCPOINT ROLLBACK)
                                                        ↓
                                            Response to User
```

### 5.3 Account Inquiry Flow
```
User Input → BMS/React/API → BNK1DAC/Controller → INQACC Program
                                                        ↓
                                            ACCOUNT Db2 (Query with Cursor)
                                                        ↓
                                            Response to User
```

---

## 6. Interface Integration Points

### 6.1 CICS Transaction Interface
All programs are invoked via CICS transactions defined in BANK.csd.

**Key Transactions**:
| Transaction ID | Program | Description |
|---------------|---------|-------------|
| OMEN | BNKMENU | Main menu |
| OCR1-OCR5 | CRDTAGY1-5 | Credit agency checks |

**Source**: `BANK.csd` lines 265-403

**Modernization**: Replace CICS transactions with REST endpoints and message queues.

### 6.2 Named Counter Interface
Programs use CICS Named Counter Server to generate unique IDs.

**Implementation**:
- EXEC CICS ENQ RESOURCE(counter-name)
- EXEC CICS READ COUNTER(counter-name) VALUE(counter-value)
- counter-value++
- EXEC CICS REWRITE COUNTER(counter-name) VALUE(counter-value)
- EXEC CICS DEQ RESOURCE(counter-name)

**Modernization**: Replace with database sequences or UUID generation.

### 6.3 Db2 Interface
Programs use embedded SQL via COBOL SQL preprocessor.

**Connection**: Implicit via CICS-Db2 attachment facility
**Transaction Coordination**: CICS SYNCPOINT handles commit/rollback

**Modernization**: Use Spring Data JPA with declarative @Transactional.

### 6.4 VSAM Interface
Programs use CICS file control commands for VSAM access.

**Operations**:
- EXEC CICS READ FILE('CUSTOMER') RIDFLD(key) INTO(record)
- EXEC CICS WRITE FILE('CUSTOMER') FROM(record) RIDFLD(key)
- EXEC CICS REWRITE FILE('CUSTOMER') FROM(record)
- EXEC CICS DELETE FILE('CUSTOMER') RIDFLD(key)

**Modernization**: Migrate VSAM data to relational database, use JPA repositories.

---

## 7. Data Migration Considerations

### 7.1 VSAM to Relational Migration

**CUSTOMER File Migration**:
```sql
-- Extract from VSAM (via COBOL utility or IDCAMS REPRO)
-- Transform dates from DDMMYYYY to YYYY-MM-DD
-- Split name field (extract title, rest of name)
-- Structure address into multiple fields
-- Load into PostgreSQL customers table

INSERT INTO customers (
    sort_code, customer_number, title, full_name,
    address_line1, city, postal_code,
    date_of_birth, credit_score, credit_score_review_date
)
SELECT 
    CUSTOMER_SORTCODE,
    CUSTOMER_NUMBER,
    SUBSTRING(CUSTOMER_NAME FROM 1 FOR POSITION(' ' IN CUSTOMER_NAME)-1) as title,
    TRIM(SUBSTRING(CUSTOMER_NAME FROM POSITION(' ' IN CUSTOMER_NAME))) as full_name,
    -- Address parsing logic here
    TO_DATE(CUSTOMER_DATE_OF_BIRTH, 'DDMMYYYY'),
    CUSTOMER_CREDIT_SCORE,
    TO_DATE(CUSTOMER_CS_REVIEW_DATE, 'DDMMYYYY')
FROM vsam_customer_export;
```

### 7.2 Date Format Conversion
**Legacy Format**: DDMMYYYY (e.g., 27101980 = 27th October 1980)
**Modern Format**: YYYY-MM-DD (e.g., 1980-10-27)

**Conversion Logic**:
```java
public LocalDate convertFromDDMMYYYY(String legacyDate) {
    if (legacyDate == null || legacyDate.length() != 8) {
        return null;
    }
    int day = Integer.parseInt(legacyDate.substring(0, 2));
    int month = Integer.parseInt(legacyDate.substring(2, 4));
    int year = Integer.parseInt(legacyDate.substring(4, 8));
    return LocalDate.of(year, month, day);
}
```

### 7.3 Data Validation During Migration
- Check all dates are valid and within constraints (year >= 1601, not future)
- Validate account types match enum values
- Ensure customer-account relationships are intact
- Verify transaction log completeness
- Check balance consistency across accounts

### 7.4 Phased Migration Strategy
⚠️ **Decision required by architect**: Consider phased approach:

**Phase 1**: Dual-write (write to both legacy and new database)
**Phase 2**: Dual-read-verify (read from new, compare with legacy)
**Phase 3**: Read from new, write to both
**Phase 4**: Cut over to new system only
**Phase 5**: Decommission legacy system

---

## 8. Performance and Scalability Considerations

### 8.1 Legacy System Capacity
- **VSAM STRINGS(20)**: Maximum 20 concurrent readers per file
- **Db2 Connection Pooling**: Managed by CICS
- **Transaction Throughput**: Limited by CICS region configuration

### 8.2 Modern System Requirements
⚠️ **Performance SLAs Unknown** - Need to establish:
- Target transactions per second (TPS)
- Maximum response time for each operation
- Concurrent user capacity
- Data volume growth projections

### 8.3 Database Optimization
**Recommended Indexes**:
- Customer lookups by customer_number
- Account lookups by account_number and customer_id
- Transaction history queries by account and date range
- Composite indexes for frequent join patterns

### 8.4 Caching Strategy
**Candidates for Caching**:
- Customer records (moderate frequency changes)
- Account metadata (type, rates) (high read, low write)
- System configuration data

**Not Suitable for Caching**:
- Account balances (constantly changing)
- Transaction history (append-only, large volume)

---

## 9. Security Considerations

### 9.1 Data Sensitivity Classification
| Data Type | Classification | Justification |
|-----------|----------------|---------------|
| Customer PII (name, address, DOB) | Highly Sensitive | GDPR, privacy laws |
| Account numbers | Sensitive | Financial account identifiers |
| Account balances | Highly Sensitive | Financial information |
| Transaction history | Highly Sensitive | Financial activity |
| Credit scores | Highly Sensitive | Credit reporting regulations |

### 9.2 Legacy Security Model
- **CICS Transaction Security**: Controls which users can invoke which transactions
- **File/Program Security**: CICS resource security
- **Db2 Security**: Database-level access control

**Source**: `BANK.csd` RESSEC and CMDSEC attributes

### 9.3 Modern Security Requirements
**Must Implement**:
- TLS/HTTPS for all API communications
- JWT or OAuth2 for authentication
- Role-based access control (RBAC)
- Encryption at rest for PII
- Encryption in transit
- Audit logging of all data access
- PCI-DSS compliance for payment data
- GDPR compliance for European customers

---

## 10. Open Questions and Decisions Required

⚠️ **Data Architecture Decisions**:
1. Should we use PostgreSQL, MySQL, or stick with Db2 for modernization?
2. What is the data retention policy for transaction logs?
3. Should customer/account deletes be soft (logical) or hard (physical)?
4. Do we need real-time data replication for disaster recovery?
5. What are the backup and recovery RTO/RPO requirements?

⚠️ **Unknown Details Requiring Investigation**:
1. Exact Db2 DDL (table definitions, indexes, constraints)
2. CONTROL table structure and usage patterns
3. ABNDFILE detailed structure
4. z/OS Connect authentication and authorization model
5. Current data volumes (number of customers, accounts, daily transactions)
6. Peak transaction rates and performance baselines

⚠️ **Migration Decisions**:
1. Big bang migration vs. phased/incremental?
2. Downtime window available for migration?
3. Rollback strategy if migration fails?
4. Data reconciliation process?

---

*Document Version: 1.0*  
*Last Updated: 2025-10-27*  
*Source Repository: taylor-curran/og-cics-cobol-app*
