# Functional Requirements

## 1. Introduction

### 1.1 Document Purpose
This document specifies the detailed functional requirements for the CICS Bank Sample Application (CBSA) based on analysis of the existing COBOL programs. Each requirement includes source references, business rules, validation logic, error handling, and acceptance criteria to guide modernization to a Java-based stack.

**Source**: Analysis of COBOL programs in `src/base/cobol_src/` and copybooks in `src/base/cobol_copy/`

### 1.2 Requirement Organization
Requirements are organized by COBOL program, with each program's functionality broken down into:
- Inputs and outputs
- Business rules and validation
- Error conditions and handling
- Data access patterns
- Acceptance criteria
- Modernization considerations

---

## 2. Customer Management Requirements

### 2.1 FR-CUST-001: Customer Inquiry (INQCUST)

#### 2.1.1 Functional Description
Retrieve customer information from the CUSTOMER VSAM file based on customer number. Supports special customer numbers for random and last customer retrieval.

**Source**: `INQCUST.cbl` (lines 9-19), invoked by `BNK1DCS.cbl`

#### 2.1.2 Input Parameters
| Parameter | Type | Description | Constraints |
|-----------|------|-------------|-------------|
| CUSTOMER_NUMBER | PIC 9(10) | Customer identifier | Required, 10 digits |
| CUSTOMER_SORTCODE | PIC 9(6) | Bank sort code | Optional, defaults to system sortcode |

#### 2.1.3 Output Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| CUSTOMER_EYECATCHER | PIC X(4) | 'CUST' if found, low-values if not |
| CUSTOMER_NUMBER | PIC 9(10) | Customer number |
| CUSTOMER_SORTCODE | PIC 9(6) | Sort code |
| CUSTOMER_NAME | PIC X(60) | Full name with title |
| CUSTOMER_ADDRESS | PIC X(160) | Complete address |
| CUSTOMER_DOB | PIC 9(8) | Date of birth (DDMMYYYY) |
| CUSTOMER_CREDIT_SCORE | PIC 999 | Credit score (0-999) |
| CUSTOMER_CS_REVIEW_DATE | PIC 9(8) | Credit review date (DDMMYYYY) |
| INQCUST_FAIL | PIC X | '1' if not found, space if success |

#### 2.1.4 Business Rules
1. **Standard Lookup**: Read CUSTOMER file with provided customer number as key
2. **Random Customer (0000000000)**: 
   - Query Named Counter CBSACUST for current value
   - Generate random number between 1 and counter value
   - Read customer with that number
   - If not found, retry with different random number (up to 10 attempts)
3. **Last Customer (9999999999)**:
   - Query Named Counter CBSACUST for current value
   - Read customer with that number
4. **SYSIDERR Retry Logic**:
   - If VSAM read returns SYSIDERR (file unavailable), retry up to 100 times
   - Wait 3 seconds between retries
   - After 100 failures, return fail code '1'
5. **Not Found**: Return low-values in all customer fields with fail code '1'

**Source**: Lines 154-275 for special number handling, lines 321-400 for retry logic in `INQCUST.cbl`

#### 2.1.5 Error Conditions
| Condition | Response | Fail Code |
|-----------|----------|-----------|
| Customer not found | Low-values in output | '1' |
| VSAM SYSIDERR after 100 retries | Low-values in output | '1' |
| Random customer - all 10 attempts failed | Low-values in output | '1' |
| Invalid VSAM operation | Call ABNDPROC | N/A |

#### 2.1.6 Data Access
- **VSAM File**: CUSTOMER (KSDS, key = sortcode + customer_number)
- **Operation**: READ
- **Locking**: None (inquiry only)

#### 2.1.7 Acceptance Criteria
- **AC1**: Given existing customer number, When INQCUST is invoked, Then return complete customer record with fail code space
- **AC2**: Given non-existent customer number, When INQCUST is invoked, Then return low-values with fail code '1'
- **AC3**: Given customer number 0000000000, When INQCUST is invoked, Then return random customer record
- **AC4**: Given customer number 9999999999, When INQCUST is invoked, Then return most recently created customer
- **AC5**: Given VSAM SYSIDERR condition, When INQCUST is invoked, Then retry up to 100 times with 3-second delays
- **AC6**: Given SYSIDERR persists after 100 retries, When INQCUST completes, Then return fail code '1'

#### 2.1.8 Modernization Considerations
```java
// REST Endpoint
@GetMapping("/api/customers/{customerId}")
public ResponseEntity<CustomerDTO> getCustomer(@PathVariable String customerId) {
    // Handle special IDs
    if ("0000000000".equals(customerId)) {
        return ResponseEntity.ok(customerService.getRandomCustomer());
    } else if ("9999999999".equals(customerId)) {
        return ResponseEntity.ok(customerService.getLastCustomer());
    }
    
    return customerService.findById(customerId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
}

// Retry logic with Spring Retry
@Retryable(
    value = {DataAccessException.class},
    maxAttempts = 100,
    backoff = @Backoff(delay = 3000)
)
public Customer findById(String customerId) {
    return customerRepository.findByCustomerNumber(customerId)
        .orElseThrow(() -> new CustomerNotFoundException(customerId));
}
```

**Recommendations**:
- Consider removing special customer IDs (0000000000, 9999999999) or implement as separate endpoints
- Replace VSAM with PostgreSQL customers table
- Use Spring Retry or Resilience4j for retry logic
- Return HTTP 404 for not found instead of fail code

---

### 2.2 FR-CUST-002: Create Customer (CRECUST)

#### 2.2.1 Functional Description
Create a new customer record with asynchronous credit checks from multiple agencies. Validates date of birth, generates unique customer number, aggregates credit scores, and logs transaction.

**Source**: `CRECUST.cbl` (lines 9-34), invoked by `BNK1CCS.cbl`

#### 2.2.2 Input Parameters
| Parameter | Type | Description | Constraints |
|-----------|------|-------------|-------------|
| CUSTOMER_NAME | PIC X(60) | Full name with title | Required, must start with valid title |
| CUSTOMER_ADDRESS | PIC X(160) | Complete address | Required |
| CUSTOMER_DOB | PIC 9(8) | Date of birth | Required, DDMMYYYY format |

#### 2.2.3 Output Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| CUSTOMER_NUMBER | PIC 9(10) | Generated customer number | Unique, from Named Counter |
| CUSTOMER_SORTCODE | PIC 9(6) | System sort code | System-defined |
| CUSTOMER_CREDIT_SCORE | PIC 999 | Aggregated credit score | 0-999 |
| CRECUST_FAIL | PIC X | Fail code or space | See error conditions |

#### 2.2.4 Business Rules
1. **Customer Number Generation**:
   - Use Named Counter CBSACUST
   - ENQ resource 'CBSACUST' for exclusive access
   - Read current value from CONTROL table
   - Increment by 1
   - Update CONTROL table
   - DEQ resource
   - Retry up to 3 times if ENQ or read fails

2. **Date of Birth Validation**:
   - Parse DDMMYYYY format
   - Use CEEDAYS to validate date
   - Year must be >= 1601 (CEEDAYS minimum)
   - Date cannot be in future
   - Age must be <= 150 years (business rule)
   - Fail codes: 'O' for year/age violation, 'Y' for future date

3. **Title Validation**:
   - Extract first word from CUSTOMER_NAME
   - Must be one of: Professor, Mr, Mrs, Miss, Ms, Dr, Drs, Lord, Sir, Lady
   - Case-insensitive comparison
   - Fail code 'T' if invalid

4. **Asynchronous Credit Checks**:
   - Start 5 child transactions (OCR1-OCR5) via CICS CHANNELS
   - Pass customer details (name, DOB, address) to each agency
   - Wait up to 3 seconds for responses
   - Aggregate scores: sum all returned scores, divide by count of responses
   - If all agencies fail, use default score of 500
   - Store aggregated score in customer record

5. **VSAM Write**:
   - Write customer record to CUSTOMER file
   - Key = sortcode + customer_number
   - Set credit score review date = current date

6. **Transaction Logging**:
   - Write to PROCTRAN Db2 table
   - Type = 'OCC' (Branch Create Customer) or 'ICC' (Web Create Customer)
   - Description = sortcode + customer_number + name(first 14 chars) + DOB(YYYY-MM-DD)
   - Amount = 0.00

**Source**: Lines 91-203 for DOB validation, lines 205-387 for credit check, lines 390-450 for VSAM/PROCTRAN in `CRECUST.cbl`

#### 2.2.5 Error Conditions
| Condition | Response | Fail Code |
|-----------|----------|-----------|
| ENQ on CBSACUST fails after retries | Abort | '3' |
| Counter read fails after retries | Abort | '1' |
| Counter update fails | Abort | '1' |
| DOB year < 1601 or age > 150 | Abort | 'O' |
| DOB in future | Abort | 'Y' |
| Invalid title | Abort | 'T' |
| All credit agencies fail | Continue with default score 500 | Space |
| Credit agency communication error | Log warning, continue | Space |
| VSAM write fails | Abort | '2' |
| PROCTRAN insert fails | Log error, continue | '3' |

#### 2.2.6 Data Access
- **Named Counter**: CBSACUST (read and increment)
- **VSAM File**: CUSTOMER (write)
- **Db2 Table**: PROCTRAN (insert)
- **CICS Channels**: Communication with CRDTAGY1-5 programs

#### 2.2.7 Acceptance Criteria
- **AC1**: Given valid customer details with title, name, address, and DOB, When CRECUST is invoked, Then customer is created with unique 10-digit number
- **AC2**: Given DOB with year < 1601, When CRECUST is invoked, Then fail with code 'O'
- **AC3**: Given DOB with age > 150 years, When CRECUST is invoked, Then fail with code 'O'
- **AC4**: Given DOB in future, When CRECUST is invoked, Then fail with code 'Y'
- **AC5**: Given name with invalid title, When CRECUST is invoked, Then fail with code 'T'
- **AC6**: Given valid customer and credit agencies respond, When CRECUST completes, Then credit score is average of agency scores
- **AC7**: Given valid customer and all agencies timeout, When CRECUST completes, Then credit score defaults to 500
- **AC8**: Given successful customer creation, When transaction completes, Then PROCTRAN contains entry with type 'OCC' or 'ICC'

#### 2.2.8 Modernization Considerations
```java
// REST Endpoint
@PostMapping("/api/customers")
public ResponseEntity<CustomerDTO> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
    CustomerDTO customer = customerService.createCustomer(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(customer);
}

// Service with async credit checks
@Service
public class CustomerService {
    
    @Transactional
    public CustomerDTO createCustomer(CreateCustomerRequest request) {
        // Validate DOB
        validateDateOfBirth(request.getDateOfBirth());
        
        // Validate title
        validateTitle(request.getName());
        
        // Generate customer number (use sequence)
        String customerNumber = generateCustomerNumber();
        
        // Async credit checks
        int creditScore = creditCheckService.aggregateCreditScores(request);
        
        // Create customer
        Customer customer = new Customer();
        customer.setCustomerNumber(customerNumber);
        customer.setName(request.getName());
        customer.setAddress(request.getAddress());
        customer.setDateOfBirth(request.getDateOfBirth());
        customer.setCreditScore(creditScore);
        customer.setCreditScoreReviewDate(LocalDate.now());
        
        customer = customerRepository.save(customer);
        
        // Log transaction
        transactionLogService.logCustomerCreation(customer);
        
        return mapToDTO(customer);
    }
    
    private void validateDateOfBirth(LocalDate dob) {
        if (dob.getYear() < 1601) {
            throw new ValidationException("DOB year must be >= 1601", "O");
        }
        if (dob.isAfter(LocalDate.now())) {
            throw new ValidationException("DOB cannot be in future", "Y");
        }
        if (Period.between(dob, LocalDate.now()).getYears() > 150) {
            throw new ValidationException("Age cannot exceed 150 years", "O");
        }
    }
    
    private void validateTitle(String name) {
        String title = name.split(" ")[0];
        List<String> validTitles = Arrays.asList(
            "Professor", "Mr", "Mrs", "Miss", "Ms", "Dr", "Drs", "Lord", "Sir", "Lady"
        );
        if (!validTitles.contains(title)) {
            throw new ValidationException("Invalid title: " + title, "T");
        }
    }
}

// Async credit check service
@Service
public class CreditCheckService {
    
    @Async
    public CompletableFuture<Integer> checkAgency(String agencyName, CustomerRequest request) {
        // Call external credit agency API
        // Timeout after 3 seconds
    }
    
    public int aggregateCreditScores(CreateCustomerRequest request) {
        List<CompletableFuture<Integer>> futures = Arrays.asList(
            checkAgency("Agency1", request),
            checkAgency("Agency2", request),
            checkAgency("Agency3", request),
            checkAgency("Agency4", request),
            checkAgency("Agency5", request)
        );
        
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

**Recommendations**:
- Use database sequence or UUID for customer number generation
- Implement DOB validation with Java LocalDate and Bean Validation
- Use @Async with CompletableFuture for credit checks
- Consider event-driven architecture for credit checks (publish event, consume responses)
- Add idempotency key to prevent duplicate customer creation

---

### 2.3 FR-CUST-003: Update Customer (UPDCUST)

#### 2.3.1 Functional Description
Update existing customer's name and/or address. Re-validates title if name changes. Does not log to PROCTRAN.

**Source**: `UPDCUST.cbl` (lines 9-23), invoked by `BNK1DCS.cbl` via F10 key

#### 2.3.2 Input Parameters
| Parameter | Type | Description | Constraints |
|-----------|------|-------------|-------------|
| CUSTOMER_NUMBER | PIC 9(10) | Customer identifier | Required |
| CUSTOMER_SORTCODE | PIC 9(6) | Sort code | Required |
| CUSTOMER_NAME | PIC X(60) | Updated name | Optional, must have valid title if provided |
| CUSTOMER_ADDRESS | PIC X(160) | Updated address | Optional |

#### 2.3.3 Output Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| UPDCUST_FAIL | PIC X | Fail code or space | See error conditions |

#### 2.3.4 Business Rules
1. **Read with UPDATE Intent**:
   - Read CUSTOMER record with UPDATE option (locks record)
   - Fail if customer not found

2. **Field Validation**:
   - At least one of name or address must be provided (not both empty/spaces)
   - If name provided, must start with valid title
   - Valid titles: Professor, Mr, Mrs, Miss, Ms, Dr, Drs, Lord, Sir, Lady

3. **Update Logic**:
   - Update name if provided and valid
   - Update address if provided
   - Keep existing values for other fields (DOB, credit score, etc.)
   - REWRITE record to VSAM

4. **No Transaction Logging**:
   - Updates do NOT write to PROCTRAN
   - ⚠️ **Decision required**: Should modernized system log updates for audit?

**Source**: Lines 144-195 for title validation, lines 220-310 for update logic in `UPDCUST.cbl`

#### 2.3.5 Error Conditions
| Condition | Response | Fail Code |
|-----------|----------|-----------|
| Customer not found | Abort | '1' |
| VSAM read failure | Abort | '2' |
| Both name and address empty/spaces | Abort | '4' |
| Invalid title in name | Abort | 'T' |
| VSAM rewrite failure | Abort | '3' |

#### 2.3.6 Data Access
- **VSAM File**: CUSTOMER (read with UPDATE, rewrite)
- **Locking**: Record-level lock acquired on read, released after rewrite

#### 2.3.7 Acceptance Criteria
- **AC1**: Given valid customer and updated name with valid title, When UPDCUST is invoked, Then customer name is updated
- **AC2**: Given valid customer and updated address, When UPDCUST is invoked, Then customer address is updated
- **AC3**: Given updated name with invalid title, When UPDCUST is invoked, Then fail with code 'T'
- **AC4**: Given both name and address empty, When UPDCUST is invoked, Then fail with code '4'
- **AC5**: Given non-existent customer, When UPDCUST is invoked, Then fail with code '1'
- **AC6**: Given successful update, When transaction completes, Then PROCTRAN has no new entry

#### 2.3.8 Modernization Considerations
```java
// REST Endpoint
@PatchMapping("/api/customers/{customerId}")
public ResponseEntity<CustomerDTO> updateCustomer(
    @PathVariable String customerId,
    @Valid @RequestBody UpdateCustomerRequest request
) {
    CustomerDTO updated = customerService.updateCustomer(customerId, request);
    return ResponseEntity.ok(updated);
}

// Service
@Transactional
public CustomerDTO updateCustomer(String customerId, UpdateCustomerRequest request) {
    Customer customer = customerRepository.findByCustomerNumber(customerId)
        .orElseThrow(() -> new CustomerNotFoundException(customerId));
    
    // Validate at least one field provided
    if (StringUtils.isBlank(request.getName()) && StringUtils.isBlank(request.getAddress())) {
        throw new ValidationException("Must provide name or address", "4");
    }
    
    // Update name if provided
    if (StringUtils.isNotBlank(request.getName())) {
        validateTitle(request.getName());
        customer.setName(request.getName());
    }
    
    // Update address if provided
    if (StringUtils.isNotBlank(request.getAddress())) {
        customer.setAddress(request.getAddress());
    }
    
    customer.setUpdatedAt(Instant.now());
    customer = customerRepository.save(customer);
    
    // Decision: Should we log updates for audit?
    // transactionLogService.logCustomerUpdate(customer);
    
    return mapToDTO(customer);
}
```

**Recommendations**:
- Use optimistic locking (@Version) instead of pessimistic locking
- Consider logging updates for compliance (even though legacy doesn't)
- Return HTTP 400 for validation errors
- Add updated_at timestamp for tracking

---

### 2.4 FR-CUST-004: Delete Customer (DELCUS)

#### 2.4.1 Functional Description
Delete customer record after verifying no associated accounts exist. Logs deletion to PROCTRAN.

**Source**: `DELCUS.cbl`, invoked by `BNK1DCS.cbl` via F5 key

#### 2.4.2 Input Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| CUSTOMER_NUMBER | PIC 9(10) | Customer identifier | Required |
| CUSTOMER_SORTCODE | PIC 9(6) | Sort code | Required |

#### 2.4.3 Output Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| DELCUS_FAIL | PIC X | Fail code or space |

#### 2.4.4 Business Rules
1. **Account Check**:
   - Query ACCOUNT table for records with matching customer number
   - If any accounts found, reject deletion with appropriate error
   - ⚠️ **Assumption**: Based on typical banking logic

2. **Delete Customer**:
   - DELETE record from CUSTOMER VSAM file
   - Physical deletion (not soft delete)

3. **Transaction Logging**:
   - Write to PROCTRAN with type='ODC' (Branch Delete) or 'IDC' (Web Delete)
   - Description includes sortcode, customer number, name, DOB

**Source**: `DELCUS.cbl` referenced in `BANK.csd` lines 195-200

#### 2.4.5 Error Conditions
| Condition | Response | Fail Code |
|-----------|----------|-----------|
| Customer has active accounts | Abort | TBD |
| Customer not found | Abort | '1' |
| VSAM delete failure | Abort | '2' |
| PROCTRAN insert failure | Log error, continue | '3' |

#### 2.4.6 Data Access
- **Db2 Table**: ACCOUNT (query for existence check)
- **VSAM File**: CUSTOMER (delete)
- **Db2 Table**: PROCTRAN (insert)

#### 2.4.7 Acceptance Criteria
- **AC1**: Given customer with no accounts, When DELCUS is invoked, Then customer is deleted and PROCTRAN logged
- **AC2**: Given customer with active accounts, When DELCUS is invoked, Then deletion is rejected with error
- **AC3**: Given non-existent customer, When DELCUS is invoked, Then fail with code '1'
- **AC4**: Given successful deletion, When transaction completes, Then PROCTRAN contains entry with type 'ODC' or 'IDC'

#### 2.4.8 Modernization Considerations
```java
// REST Endpoint
@DeleteMapping("/api/customers/{customerId}")
public ResponseEntity<Void> deleteCustomer(@PathVariable String customerId) {
    customerService.deleteCustomer(customerId);
    return ResponseEntity.noContent().build();
}

// Service
@Transactional
public void deleteCustomer(String customerId) {
    Customer customer = customerRepository.findByCustomerNumber(customerId)
        .orElseThrow(() -> new CustomerNotFoundException(customerId));
    
    // Check for active accounts
    long accountCount = accountRepository.countByCustomerId(customer.getId());
    if (accountCount > 0) {
        throw new BusinessRuleException(
            "Cannot delete customer with active accounts",
            HttpStatus.CONFLICT
        );
    }
    
    // Log before deleting (need customer data for log)
    transactionLogService.logCustomerDeletion(customer);
    
    // Delete customer
    customerRepository.delete(customer);
}
```

**Recommendations**:
- Consider soft delete (deleted_at timestamp) instead of physical deletion
- Return HTTP 409 Conflict if customer has accounts
- Ensure cascade rules are properly defined

---

## 3. Account Management Requirements

### 3.1 FR-ACCT-001: Account Inquiry (INQACC)

#### 3.1.1 Functional Description
Retrieve account information from ACCOUNT Db2 table using sort code and account number. Uses cursor for query execution.

**Source**: `INQACC.cbl` (lines 10-16), invoked by `BNK1DAC.cbl`

#### 3.1.2 Input Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| ACCOUNT_SORTCODE | PIC 9(6) | Bank sort code |
| ACCOUNT_NUMBER | PIC 9(8) | Account number |

#### 3.1.3 Output Parameters
All fields from ACCOUNT copybook, or low-values if not found.

#### 3.1.4 Business Rules
1. **Db2 Cursor Query**:
   - DECLARE cursor with SELECT statement
   - WHERE clause: sortcode = ? AND account_number = ?
   - OPEN cursor with input parameters
   - FETCH one row
   - CLOSE cursor

2. **Not Found Handling**:
   - SQLCODE +100 indicates no rows found
   - Return low-values in all account fields
   - No fail code (caller checks eyecatcher for 'ACCT')

3. **Error Handling**:
   - Negative SQLCODE indicates error
   - Call ABNDPROC with SQLCODE
   - Storm drain processing for Db2 errors

**Source**: Lines 195-273 for cursor logic, lines 416-503 for error handling in `INQACC.cbl`

#### 3.1.5 Error Conditions
| Condition | Response | Fail Code |
|-----------|----------|-----------|
| Account not found (SQLCODE +100) | Return low-values | None |
| Db2 error (SQLCODE < 0) | Call ABNDPROC | N/A |

#### 3.1.6 Data Access
- **Db2 Table**: ACCOUNT (query)
- **Operation**: SELECT with cursor
- **Locking**: None (inquiry only)

#### 3.1.7 Acceptance Criteria
- **AC1**: Given existing account, When INQACC is invoked, Then return complete account record with eyecatcher 'ACCT'
- **AC2**: Given non-existent account, When INQACC is invoked, Then return low-values with empty eyecatcher
- **AC3**: Given Db2 error, When INQACC executes, Then call ABNDPROC and log to ABNDFILE

#### 3.1.8 Modernization Considerations
```java
// REST Endpoint
@GetMapping("/api/accounts/{sortCode}/{accountNumber}")
public ResponseEntity<AccountDTO> getAccount(
    @PathVariable String sortCode,
    @PathVariable String accountNumber
) {
    return accountService.findBySortCodeAndAccountNumber(sortCode, accountNumber)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
}

// Alternative with single ID
@GetMapping("/api/accounts/{accountId}")
public ResponseEntity<AccountDTO> getAccount(@PathVariable Long accountId) {
    return accountService.findById(accountId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
}

// Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findBySortCodeAndAccountNumber(String sortCode, String accountNumber);
}
```

**Recommendations**:
- Use simple JPA query instead of cursor
- Return HTTP 404 for not found
- Consider using surrogate key (accountId) for simpler API

---

### 3.2 FR-ACCT-002: Create Account (CREACC)

#### 3.2.1 Functional Description
Create new account for existing customer. Validates customer exists, checks account limit, generates unique account number, initializes balances, and logs transaction.

**Source**: `CREACC.cbl` (lines 9-22), invoked by `BNK1CAC.cbl`

#### 3.2.2 Input Parameters
| Parameter | Type | Description | Constraints |
|-----------|------|-------------|-------------|
| CUSTOMER_NUMBER | PIC 9(10) | Customer ID | Required, must exist |
| ACCOUNT_TYPE | PIC X(8) | Account category | Required, valid type |
| INTEREST_RATE | PIC 9(4)V99 | Interest rate % | Required, 0.00-9999.99 |
| OVERDRAFT_LIMIT | PIC 9(8) | Overdraft limit | Required, 0-99999999 |

#### 3.2.3 Output Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| ACCOUNT_NUMBER | PIC 9(8) | Generated account number |
| ACCOUNT_SORTCODE | PIC 9(6) | System sort code |
| CREACC_FAIL | PIC X | Fail code or space |

#### 3.2.4 Business Rules
1. **Customer Validation**:
   - Call INQCUST to verify customer exists
   - If customer not found or call fails, abort with fail code '1' or '2'

2. **Account Limit Check**:
   - Call INQACCCU to count existing accounts for customer
   - Maximum 9 accounts per customer
   - If limit exceeded, abort with fail code '8'
   - If count query fails, abort with fail code '9'

3. **Account Number Generation**:
   - Use Named Counter CBSAACCT
   - ENQ resource 'CBSAACCT' for exclusive access
   - Read counter from CONTROL table
   - Increment by 1
   - Update CONTROL table
   - DEQ resource
   - Retry up to 3 times on failure
   - Fail codes: '3' (ENQ failed), '4' (read failed), '5' (update failed)

4. **Account Type Validation**:
   - Valid types: CURRENT, SAVINGS, LOAN, MORTGAGE, ISA
   - ⚠️ **Assumption**: Validation likely in calling program or at UI

5. **Initial Values**:
   - ACCOUNT_EYECATCHER = 'ACCT'
   - ACCOUNT_OPENED = current date (DDMMYYYY)
   - ACCOUNT_AVAILABLE_BALANCE = 0.00
   - ACCOUNT_ACTUAL_BALANCE = 0.00
   - ACCOUNT_LAST_STATEMENT = current date
   - ACCOUNT_NEXT_STATEMENT = current date + 1 month

6. **Db2 Insert**:
   - INSERT INTO ACCOUNT table
   - If insert fails, abort with fail code '6'

7. **Transaction Logging**:
   - INSERT INTO PROCTRAN table
   - Type = 'OCA' (Branch Create) or 'ICA' (Web Create)
   - Description = customer_no + account_type + stmt dates + 'CREATE'
   - Amount = 0.00
   - If PROCTRAN fails, log error with fail code '7' but continue

**Source**: Lines 178-305 for customer validation, lines 447-683 for counter, lines 788-893 for limit check, lines 895-1070 for insert in `CREACC.cbl`

#### 3.2.5 Error Conditions
| Condition | Response | Fail Code |
|-----------|----------|-----------|
| Customer not found | Abort | '1' |
| Customer inquiry failed | Abort | '2' |
| ENQ on CBSAACCT failed | Abort | '3' |
| Counter read failed (after retries) | Abort | '4' |
| Counter update failed | Abort | '5' |
| Account insert failed | Abort | '6' |
| PROCTRAN insert failed | Log error, continue | '7' |
| Customer has 9 accounts already | Abort | '8' |
| Account count query failed | Abort | '9' |

#### 3.2.6 Data Access
- **Program Calls**: INQCUST, INQACCCU
- **Named Counter**: CBSAACCT
- **Db2 Tables**: ACCOUNT (insert), PROCTRAN (insert)

#### 3.2.7 Acceptance Criteria
- **AC1**: Given valid customer and account details, When CREACC is invoked, Then account is created with unique 8-digit number and balances of 0.00
- **AC2**: Given non-existent customer, When CREACC is invoked, Then fail with code '1'
- **AC3**: Given customer with 9 existing accounts, When CREACC is invoked, Then fail with code '8'
- **AC4**: Given valid inputs, When account created, Then ACCOUNT_OPENED = current date
- **AC5**: Given valid inputs, When account created, Then ACCOUNT_NEXT_STATEMENT = ACCOUNT_OPENED + 1 month
- **AC6**: Given successful creation, When transaction completes, Then PROCTRAN contains entry with type 'OCA' or 'ICA'

#### 3.2.8 Modernization Considerations
```java
// REST Endpoint
@PostMapping("/api/accounts")
public ResponseEntity<AccountDTO> createAccount(@Valid @RequestBody CreateAccountRequest request) {
    AccountDTO account = accountService.createAccount(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(account);
}

// Service
@Transactional
public AccountDTO createAccount(CreateAccountRequest request) {
    // Validate customer exists
    Customer customer = customerRepository.findByCustomerNumber(request.getCustomerId())
        .orElseThrow(() -> new CustomerNotFoundException(request.getCustomerId()));
    
    // Check account limit
    long accountCount = accountRepository.countByCustomerId(customer.getId());
    if (accountCount >= 9) {
        throw new BusinessRuleException("Customer has maximum 9 accounts", "8");
    }
    
    // Validate account type
    if (!AccountType.isValid(request.getAccountType())) {
        throw new ValidationException("Invalid account type");
    }
    
    // Create account (sequence auto-generates account number)
    Account account = new Account();
    account.setCustomer(customer);
    account.setSortCode(systemConfig.getSortCode());
    account.setAccountType(AccountType.valueOf(request.getAccountType()));
    account.setInterestRate(request.getInterestRate());
    account.setOverdraftLimit(request.getOverdraftLimit());
    account.setOpenedDate(LocalDate.now());
    account.setLastStatementDate(LocalDate.now());
    account.setNextStatementDate(LocalDate.now().plusMonths(1));
    account.setAvailableBalance(BigDecimal.ZERO);
    account.setActualBalance(BigDecimal.ZERO());
    
    account = accountRepository.save(account);
    
    // Log transaction
    transactionLogService.logAccountCreation(account);
    
    return mapToDTO(account);
}

// Enum for account types
public enum AccountType {
    CURRENT, SAVINGS, LOAN, MORTGAGE, ISA;
    
    public static boolean isValid(String type) {
        try {
            valueOf(type);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
```

**Recommendations**:
- Use database sequence for account number generation
- Use enum for account types with validation
- Replace Named Counter ENQ/DEQ with optimistic locking
- Add account number to URL after creation (RESTful)

---

### 3.3 FR-ACCT-003: Update Account (UPDACC)

#### 3.3.1 Functional Description
Update account type, interest rate, and/or overdraft limit. Cannot update balances (use DBCRFUN for that). Does not log to PROCTRAN.

**Source**: `UPDACC.cbl` (lines 10-32), invoked by `BNK1UAC.cbl`

#### 3.3.2 Input Parameters
| Parameter | Type | Description | Constraints |
|-----------|------|-------------|-------------|
| ACCOUNT_SORTCODE | PIC 9(6) | Sort code | Required |
| ACCOUNT_NUMBER | PIC 9(8) | Account number | Required |
| ACCOUNT_TYPE | PIC X(8) | Account category | Optional, must be valid if provided |
| INTEREST_RATE | PIC 9(4)V99 | Interest rate % | Optional |
| OVERDRAFT_LIMIT | PIC 9(8) | Overdraft limit | Optional |

#### 3.3.3 Output Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| UPDACC_STATUS | PIC X | 'N' if failed, space if success |

#### 3.3.4 Business Rules
1. **Query Account**:
   - SELECT from ACCOUNT table with WHERE sortcode = ? AND account_number = ?
   - If not found, fail with status 'N'

2. **Validation**:
   - Account type cannot be empty or start with space
   - If validation fails, status = 'N'

3. **Update Statement**:
   - UPDATE ACCOUNT SET type = ?, interest_rate = ?, overdraft_limit = ?
   - WHERE sortcode = ? AND account_number = ?
   - If update fails (SQLCODE <> 0), status = 'N'

4. **Read-Only Fields**:
   - Customer number, balances, dates CANNOT be updated via this function
   - Balances can only change via DBCRFUN or XFRFUN

5. **No Transaction Logging**:
   - Updates do NOT write to PROCTRAN
   - ⚠️ **Decision required**: Should modernized system log updates?

**Source**: Lines 155-285 for query/update logic in `UPDACC.cbl`

#### 3.3.5 Error Conditions
| Condition | Response | Fail Code |
|-----------|----------|-----------|
| Account not found | Abort | 'N' |
| Empty account type | Abort | 'N' |
| Db2 update failure | Abort | 'N' |

#### 3.3.6 Data Access
- **Db2 Table**: ACCOUNT (query, update)
- **Locking**: Implicit Db2 row lock during update

#### 3.3.7 Acceptance Criteria
- **AC1**: Given valid account and updated type, When UPDACC is invoked, Then account type is updated
- **AC2**: Given valid account and updated interest rate, When UPDACC is invoked, Then interest rate is updated
- **AC3**: Given valid account and updated overdraft, When UPDACC is invoked, Then overdraft limit is updated
- **AC4**: Given attempt to update balance, When UPDACC is invoked, Then update is ignored (balance unchanged)
- **AC5**: Given empty account type, When UPDACC is invoked, Then fail with status 'N'
- **AC6**: Given non-existent account, When UPDACC is invoked, Then fail with status 'N'
- **AC7**: Given successful update, When transaction completes, Then PROCTRAN has no new entry

#### 3.3.8 Modernization Considerations
```java
// REST Endpoint
@PatchMapping("/api/accounts/{accountId}")
public ResponseEntity<AccountDTO> updateAccount(
    @PathVariable Long accountId,
    @Valid @RequestBody UpdateAccountRequest request
) {
    AccountDTO updated = accountService.updateAccount(accountId, request);
    return ResponseEntity.ok(updated);
}

// Request DTO with validation
public class UpdateAccountRequest {
    @NotBlank(message = "Account type cannot be blank")
    private String accountType;
    
    @DecimalMin(value = "0.0", message = "Interest rate must be non-negative")
    @DecimalMax(value = "9999.99", message = "Interest rate cannot exceed 9999.99")
    private BigDecimal interestRate;
    
    @Min(value = 0, message = "Overdraft limit must be non-negative")
    @Max(value = 99999999, message = "Overdraft limit cannot exceed 99999999")
    private Integer overdraftLimit;
    
    // Balance fields intentionally omitted - cannot be updated here
}

// Service
@Transactional
public AccountDTO updateAccount(Long accountId, UpdateAccountRequest request) {
    Account account = accountRepository.findById(accountId)
        .orElseThrow(() -> new AccountNotFoundException(accountId));
    
    // Update allowed fields only
    if (request.getAccountType() != null) {
        account.setAccountType(AccountType.valueOf(request.getAccountType()));
    }
    if (request.getInterestRate() != null) {
        account.setInterestRate(request.getInterestRate());
    }
    if (request.getOverdraftLimit() != null) {
        account.setOverdraftLimit(request.getOverdraftLimit());
    }
    
    account.setUpdatedAt(Instant.now());
    account = accountRepository.save(account);
    
    // Decision: Should we log for audit?
    // transactionLogService.logAccountUpdate(account);
    
    return mapToDTO(account);
}
```

**Recommendations**:
- Use PATCH method for partial updates
- Enforce balance immutability at DTO level (don't include balance fields)
- Consider logging updates for audit trail
- Use Bean Validation for constraints

---

### 3.4 FR-ACCT-004: Delete Account (DELACC)

#### 3.4.1 Functional Description
Delete account after verifying balance is zero. Logs deletion to PROCTRAN.

**Source**: `DELACC.cbl`, invoked by `BNK1DAC.cbl` via F5 key

#### 3.4.2 Input Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| ACCOUNT_SORTCODE | PIC 9(6) | Sort code | Required |
| ACCOUNT_NUMBER | PIC 9(8) | Account number | Required |

#### 3.4.3 Output Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| DELACC_FAIL | PIC X | Fail code or space |

#### 3.4.4 Business Rules
1. **Balance Check**:
   - Query account actual_balance
   - Must be exactly 0.00
   - Reject deletion if balance non-zero

2. **Delete Account**:
   - DELETE FROM ACCOUNT WHERE sortcode = ? AND account_number = ?
   - Physical deletion from Db2

3. **Transaction Logging**:
   - INSERT INTO PROCTRAN
   - Type = 'ODA' (Branch Delete) or 'IDA' (Web Delete)
   - Description includes customer number, account type, dates, 'DELETE'
   - Amount = final balance (should be 0.00)

**Source**: `DELACC.cbl` referenced in `BANK.csd` lines 188-193

#### 3.4.5 Error Conditions
| Condition | Response | Fail Code |
|-----------|----------|-----------|
| Account balance not zero | Abort | TBD |
| Account not found | Abort | '1' |
| Db2 delete failure | Abort | '2' |
| PROCTRAN insert failure | Log error, continue | '3' |

#### 3.4.6 Data Access
- **Db2 Tables**: ACCOUNT (query, delete), PROCTRAN (insert)

#### 3.4.7 Acceptance Criteria
- **AC1**: Given account with zero balance, When DELACC is invoked, Then account is deleted and PROCTRAN logged
- **AC2**: Given account with non-zero balance, When DELACC is invoked, Then deletion is rejected
- **AC3**: Given non-existent account, When DELACC is invoked, Then fail with code '1'
- **AC4**: Given successful deletion, When transaction completes, Then PROCTRAN contains entry with type 'ODA' or 'IDA'

#### 3.4.8 Modernization Considerations
```java
// REST Endpoint
@DeleteMapping("/api/accounts/{accountId}")
public ResponseEntity<Void> deleteAccount(@PathVariable Long accountId) {
    accountService.deleteAccount(accountId);
    return ResponseEntity.noContent().build();
}

// Service
@Transactional
public void deleteAccount(Long accountId) {
    Account account = accountRepository.findById(accountId)
        .orElseThrow(() -> new AccountNotFoundException(accountId));
    
    // Check balance is zero
    if (account.getActualBalance().compareTo(BigDecimal.ZERO) != 0) {
        throw new BusinessRuleException(
            "Cannot delete account with non-zero balance",
            HttpStatus.CONFLICT
        );
    }
    
    // Log before deleting
    transactionLogService.logAccountDeletion(account);
    
    // Delete account
    accountRepository.delete(account);
}
```

**Recommendations**:
- Consider soft delete (closed_at timestamp)
- Return HTTP 409 Conflict if balance non-zero
- Maintain full account history even after deletion

---

### 3.5 FR-ACCT-005: Account Lookup by Customer (INQACCCU)

#### 3.5.1 Functional Description
Retrieve all accounts (up to 9) associated with a customer number.

**Source**: `INQACCCU.cbl`, invoked by `BNK1CCA.cbl`

#### 3.5.2 Input Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| CUSTOMER_NUMBER | PIC 9(10) | Customer ID | Required |

#### 3.5.3 Output Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| ACCOUNT_COUNT | PIC 9 | Number of accounts found | 0-9 |
| ACCOUNT_ARRAY | Array | Up to 9 account records |

#### 3.5.4 Business Rules
1. **Db2 Query**:
   - SELECT * FROM ACCOUNT WHERE customer_number = ?
   - ORDER BY account_number
   - FETCH up to 9 rows

2. **Return Data**:
   - Array of account numbers, types, balances
   - Empty array if customer has no accounts

**Source**: `INQACCCU.cbl` referenced in `BANK.csd` lines 237-242

#### 3.5.5 Acceptance Criteria
- **AC1**: Given customer with accounts, When INQACCCU is invoked, Then return list of all accounts (up to 9)
- **AC2**: Given customer with no accounts, When INQACCCU is invoked, Then return empty array
- **AC3**: Given customer with 9+ accounts, When INQACCCU is invoked, Then return first 9 accounts

#### 3.5.6 Modernization Considerations
```java
// REST Endpoint
@GetMapping("/api/customers/{customerId}/accounts")
public ResponseEntity<List<AccountSummaryDTO>> getCustomerAccounts(
    @PathVariable String customerId,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
) {
    Page<AccountSummaryDTO> accounts = accountService.findByCustomerId(customerId, page, size);
    return ResponseEntity.ok(accounts.getContent());
}

// Remove 9-account limit in modern system
// Add pagination support
```

**Recommendations**:
- Remove arbitrary 9-account limit
- Implement pagination for scalability
- Return account summaries (not full details)

---

## 4. Transaction Requirements

### 4.1 FR-TXN-001: Debit/Credit Funds (DBCRFUN)

#### 4.1.1 Functional Description
Debit or credit funds to an account. Validates sufficient funds for debits, updates balances, and logs transaction.

**Source**: `DBCRFUN.cbl` (lines 10-29), invoked by `BNK1CRA.cbl`

#### 4.1.2 Input Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| ACCOUNT_SORTCODE | PIC 9(6) | Sort code | Required |
| ACCOUNT_NUMBER | PIC 9(8) | Account number | Required |
| AMOUNT | PIC S9(10)V99 | Transaction amount | Required, > 0 |
| TRANSACTION_TYPE | PIC X | 'D' or 'C' | Required |
| DESCRIPTION | PIC X(40) | Optional description |

#### 4.1.3 Output Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| AVAILABLE_BALANCE | PIC S9(10)V99 | Updated available balance |
| ACTUAL_BALANCE | PIC S9(10)V99 | Updated actual balance |
| DBCRFUN_FAIL | PIC X | Fail code or space |

#### 4.1.4 Business Rules
1. **Account Query**:
   - SELECT for UPDATE from ACCOUNT table
   - Acquires row lock
   - Fail if account not found (code '1')

2. **Debit Validation** (Type = 'D'):
   - Check: actual_balance + overdraft_limit >= amount
   - If insufficient, fail with code '3'
   - Update: actual_balance -= amount, available_balance -= amount

3. **Credit Processing** (Type = 'C'):
   - No validation needed (can always credit)
   - Update: actual_balance += amount, available_balance += amount

4. **Interface-Specific Account Type Restrictions**:
   - Payment Services interface prevents debit/credit on MORTGAGE and LOAN accounts
   - Restriction enforced at interface layer, not in DBCRFUN itself
   - Fail code '4' from interface

5. **Balance Update**:
   - UPDATE ACCOUNT SET actual_balance = ?, available_balance = ?
   - WHERE sortcode = ? AND account_number = ?
   - Fail if update returns SQLCODE <> 0 (code '2')

6. **Transaction Logging**:
   - INSERT INTO PROCTRAN
   - Type = 'DEB' or 'CRE' (branch), 'PDR' or 'PCR' (payment interface)
   - Description = provided description or default
   - Amount = signed amount (negative for debit, positive for credit)

**Source**: Lines 246-344 for debit, lines 390-458 for credit, lines 531-624 for logging in `DBCRFUN.cbl`

#### 4.1.5 Error Conditions
| Condition | Response | Fail Code |
|-----------|----------|-----------|
| Account not found | Abort | '1' |
| Balance update failed | Abort | '2' |
| Insufficient funds (debit) | Abort | '3' |
| Invalid account type (interface) | Abort | '4' |

#### 4.1.6 Data Access
- **Db2 Tables**: ACCOUNT (query with lock, update), PROCTRAN (insert)
- **Locking**: Row-level lock on account during transaction

#### 4.1.7 Acceptance Criteria
- **AC1**: Given valid account and credit amount, When DBCRFUN is invoked, Then both balances increase by amount
- **AC2**: Given valid account with sufficient funds and debit amount, When DBCRFUN is invoked, Then both balances decrease by amount
- **AC3**: Given debit exceeding available funds (actual + overdraft), When DBCRFUN is invoked, Then fail with code '3'
- **AC4**: Given MORTGAGE account and Payment interface, When debit is attempted, Then fail with code '4'
- **AC5**: Given successful transaction, When complete, Then PROCTRAN contains entry with appropriate type and signed amount

#### 4.1.8 Modernization Considerations
```java
// REST Endpoint
@PostMapping("/api/accounts/{accountId}/transactions")
public ResponseEntity<TransactionReceiptDTO> createTransaction(
    @PathVariable Long accountId,
    @Valid @RequestBody CreateTransactionRequest request
) {
    TransactionReceiptDTO receipt = transactionService.processTransaction(accountId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(receipt);
}

// Request DTO
public class CreateTransactionRequest {
    @NotNull
    private TransactionType type; // DEBIT or CREDIT
    
    @NotNull
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;
    
    @Size(max = 100)
    private String description;
}

// Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public TransactionReceiptDTO processTransaction(Long accountId, CreateTransactionRequest request) {
    // Lock account row
    Account account = accountRepository.findByIdForUpdate(accountId)
        .orElseThrow(() -> new AccountNotFoundException(accountId));
    
    // Check account type restrictions
    if (request.getType() == TransactionType.DEBIT || request.getType() == TransactionType.CREDIT) {
        if (account.getAccountType() == AccountType.MORTGAGE || 
            account.getAccountType() == AccountType.LOAN) {
            throw new BusinessRuleException("Cannot debit/credit MORTGAGE or LOAN accounts", "4");
        }
    }
    
    // Process based on type
    if (request.getType() == TransactionType.DEBIT) {
        // Check sufficient funds
        BigDecimal availableFunds = account.getActualBalance().add(account.getOverdraftLimit());
        if (availableFunds.compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException("Insufficient funds for debit", "3");
        }
        
        // Debit account
        account.setActualBalance(account.getActualBalance().subtract(request.getAmount()));
        account.setAvailableBalance(account.getAvailableBalance().subtract(request.getAmount()));
    } else {
        // Credit account
        account.setActualBalance(account.getActualBalance().add(request.getAmount()));
        account.setAvailableBalance(account.getAvailableBalance().add(request.getAmount()));
    }
    
    account = accountRepository.save(account);
    
    // Log transaction
    Transaction transaction = transactionLogService.logTransaction(
        account,
        request.getType(),
        request.getAmount(),
        request.getDescription()
    );
    
    return buildReceipt(account, transaction);
}

// Repository with pessimistic locking
public interface AccountRepository extends JpaRepository<Account, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);
}
```

**Recommendations**:
- Use pessimistic locking (@Lock) for account updates
- Return transaction receipt with updated balance
- Consider idempotency key for duplicate prevention
- Implement account type rules as configurable business rules

---

### 4.2 FR-TXN-002: Transfer Funds (XFRFUN)

#### 4.2.1 Functional Description
Transfer funds between two accounts atomically. Validates accounts, checks funds, uses ordered locking to prevent deadlocks, and rolls back on any failure.

**Source**: `XFRFUN.cbl` (lines 11-38), invoked by `BNK1TFN.cbl`

#### 4.2.2 Input Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| FROM_SORTCODE | PIC 9(6) | Source sort code | Required |
| FROM_ACCOUNT_NUMBER | PIC 9(8) | Source account number | Required |
| TO_SORTCODE | PIC 9(6) | Destination sort code | Required |
| TO_ACCOUNT_NUMBER | PIC 9(8) | Destination account number | Required |
| AMOUNT | PIC S9(10)V99 | Transfer amount | Required, > 0 |
| DESCRIPTION | PIC X(40) | Optional description |

#### 4.2.3 Output Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| FROM_BALANCE | PIC S9(10)V99 | Updated source balance |
| TO_BALANCE | PIC S9(10)V99 | Updated destination balance |
| XFRFUN_FAIL | PIC X | Fail code or space |

#### 4.2.4 Business Rules
1. **Same Account Check**:
   - Source and destination cannot be identical
   - Fail with code '3' if same

2. **Amount Validation**:
   - Amount must be > 0
   - Fail with code '4' if <= 0

3. **Account Queries**:
   - Query both accounts first (no lock yet)
   - Validate both exist
   - Fail with code '1' (source not found) or '2' (destination not found)

4. **Ordered Locking** (Deadlock Prevention):
   - Compare account numbers
   - Lock accounts in ascending order (lower number first)
   - Example: If from=87654321 and to=12345678, lock to first, then from
   - This ensures consistent lock ordering across all transfers

5. **Source Debit**:
   - SELECT for UPDATE on source account (acquires lock)
   - Check: actual_balance + overdraft_limit >= amount
   - Fail with code '5' if insufficient
   - UPDATE: actual_balance -= amount, available_balance -= amount
   - Fail with code '6' if update fails

6. **Destination Credit**:
   - SELECT for UPDATE on destination account (acquires lock)
   - UPDATE: actual_balance += amount, available_balance += amount
   - Fail with code '7' if update fails

7. **Transaction Logging**:
   - INSERT INTO PROCTRAN for source account
   - Type = 'TFR'
   - Description = 'TRANSFER' + destination_sortcode + destination_account_number
   - Amount = transfer amount (positive)
   - Fail with code '8' if insert fails

8. **Rollback on Failure**:
   - If any step fails after source debit, issue SYNCPOINT ROLLBACK
   - All Db2 changes are undone
   - Account balances restored to original values

9. **Deadlock Retry**:
   - If Db2 returns deadlock SQLCODE, retry entire transfer up to 10 times
   - Wait between retries

**Source**: Lines 270-470 for validation, lines 486-676 for source debit, lines 726-898 for destination credit, lines 945-1127 for PROCTRAN, lines 1240-1335 for rollback in `XFRFUN.cbl`

#### 4.2.5 Error Conditions
| Condition | Response | Fail Code |
|-----------|----------|-----------|
| Source and destination same | Abort | '3' |
| Amount <= 0 | Abort | '4' |
| Source account not found | Abort | '1' |
| Destination account not found | Abort | '2' |
| Insufficient funds in source | Rollback, Abort | '5' |
| Source debit failed | Rollback, Abort | '6' |
| Destination credit failed | Rollback, Abort | '7' |
| PROCTRAN insert failed | Rollback, Abort | '8' |

#### 4.2.6 Data Access
- **Db2 Tables**: ACCOUNT (query, update with locks), PROCTRAN (insert)
- **Transaction**: Uses Db2 transaction with explicit rollback
- **Locking**: Pessimistic row locks in ordered sequence

#### 4.2.7 Acceptance Criteria
- **AC1**: Given valid accounts with sufficient funds, When XFRFUN is invoked, Then source debited, destination credited, PROCTRAN logged
- **AC2**: Given source = destination, When XFRFUN is invoked, Then fail with code '3' without changes
- **AC3**: Given amount = 0 or negative, When XFRFUN is invoked, Then fail with code '4' without changes
- **AC4**: Given insufficient funds, When XFRFUN is invoked, Then fail with code '5' without changes
- **AC5**: Given destination credit fails after source debit, When failure occurs, Then SYNCPOINT ROLLBACK restores source balance
- **AC6**: Given Db2 deadlock, When XFRFUN executes, Then retry up to 10 times
- **AC7**: Given successful transfer, When complete, Then PROCTRAN contains 'TFR' entry with destination account in description

#### 4.2.8 Modernization Considerations
```java
// REST Endpoint
@PostMapping("/api/transfers")
public ResponseEntity<TransferReceiptDTO> transferFunds(@Valid @RequestBody TransferRequest request) {
    TransferReceiptDTO receipt = transactionService.transferFunds(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(receipt);
}

// Request DTO
public class TransferRequest {
    @NotBlank
    private String fromAccountId;
    
    @NotBlank
    private String toAccountId;
    
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;
    
    @Size(max = 100)
    private String description;
    
    private String idempotencyKey; // For duplicate prevention
}

// Service
@Transactional(isolation = Isolation.READ_COMMITTED)
@Retryable(
    value = {DeadlockLoserDataAccessException.class},
    maxAttempts = 10,
    backoff = @Backoff(delay = 100)
)
public TransferReceiptDTO transferFunds(TransferRequest request) {
    // Validate not same account
    if (request.getFromAccountId().equals(request.getToAccountId())) {
        throw new ValidationException("Cannot transfer to same account", "3");
    }
    
    // Validate amount
    if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
        throw new ValidationException("Amount must be positive", "4");
    }
    
    // Check idempotency
    if (request.getIdempotencyKey() != null) {
        Optional<Transfer> existing = transferRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            return buildReceipt(existing.get()); // Return existing transfer
        }
    }
    
    // Query both accounts first (validation without locks)
    Account fromAccount = accountRepository.findById(Long.parseLong(request.getFromAccountId()))
        .orElseThrow(() -> new AccountNotFoundException("Source account not found", "1"));
    Account toAccount = accountRepository.findById(Long.parseLong(request.getToAccountId()))
        .orElseThrow(() -> new AccountNotFoundException("Destination account not found", "2"));
    
    // Acquire locks in consistent order (by ID to prevent deadlock)
    List<Account> accounts = Arrays.asList(fromAccount, toAccount);
    accounts.sort(Comparator.comparing(Account::getId));
    
    Account lockedFrom = accountRepository.findByIdForUpdate(fromAccount.getId()).get();
    Account lockedTo = accountRepository.findByIdForUpdate(toAccount.getId()).get();
    
    // Map back to correct roles
    if (lockedFrom.getId().equals(fromAccount.getId())) {
        fromAccount = lockedFrom;
        toAccount = lockedTo;
    } else {
        fromAccount = lockedTo;
        toAccount = lockedFrom;
    }
    
    // Check sufficient funds
    BigDecimal availableFunds = fromAccount.getActualBalance().add(fromAccount.getOverdraftLimit());
    if (availableFunds.compareTo(request.getAmount()) < 0) {
        throw new InsufficientFundsException("Insufficient funds in source account", "5");
    }
    
    // Debit source
    fromAccount.setActualBalance(fromAccount.getActualBalance().subtract(request.getAmount()));
    fromAccount.setAvailableBalance(fromAccount.getAvailableBalance().subtract(request.getAmount()));
    fromAccount = accountRepository.save(fromAccount);
    
    // Credit destination
    toAccount.setActualBalance(toAccount.getActualBalance().add(request.getAmount()));
    toAccount.setAvailableBalance(toAccount.getAvailableBalance().add(request.getAmount()));
    toAccount = accountRepository.save(toAccount);
    
    // Create transfer record
    Transfer transfer = new Transfer();
    transfer.setFromAccount(fromAccount);
    transfer.setToAccount(toAccount);
    transfer.setAmount(request.getAmount());
    transfer.setDescription(request.getDescription());
    transfer.setIdempotencyKey(request.getIdempotencyKey());
    transfer.setStatus(TransferStatus.COMPLETED);
    transfer = transferRepository.save(transfer);
    
    // Log to transaction log
    transactionLogService.logTransfer(transfer);
    
    return buildReceipt(transfer, fromAccount, toAccount);
}
```

**Recommendations**:
- Use @Transactional for automatic rollback
- Implement ordered locking by entity ID
- Use @Retryable for deadlock retries
- Add idempotency key support
- Consider separate Transfer entity for better tracking
- Publish event for downstream notifications

---

## 5. Cross-Cutting Functional Requirements

### 5.1 FR-CROSS-001: Transaction Audit Logging

#### 5.1.1 Description
All successful write operations must be logged to PROCTRAN table for audit trail, compliance, and reconciliation.

**Operations Logged**:
- Customer create/delete (not update)
- Account create/delete (not update)
- All financial transactions (debit, credit, transfer)

**Operations NOT Logged**:
- Inquiries (read-only)
- Updates (UPDCUST, UPDACC)

**Source**: `PROCTRAN.cpy`, multiple program references

#### 5.1.2 Modernization Considerations
```java
@Service
public class TransactionLogService {
    
    @Async
    public void logTransaction(Account account, TransactionType type, BigDecimal amount, String description) {
        TransactionLog log = new TransactionLog();
        log.setAccountId(account.getId());
        log.setSortCode(account.getSortCode());
        log.setAccountNumber(account.getAccountNumber());
        log.setTransactionDate(LocalDate.now());
        log.setTransactionTime(LocalTime.now());
        log.setTransactionType(type);
        log.setAmount(amount);
        log.setDescription(description);
        log.setReferenceNumber(generateReference());
        log.setCreatedBy(SecurityContext.getCurrentUser());
        log.setCreatedFrom(RequestContext.getIpAddress());
        log.setCorrelationId(RequestContext.getCorrelationId());
        
        transactionLogRepository.save(log);
        
        // Also publish event for downstream consumers
        eventPublisher.publishTransactionEvent(log);
    }
}
```

**Enhancements**:
- Add user identity (who made the change)
- Add source IP address
- Add correlation ID for distributed tracing
- Make logging asynchronous to not block main transaction
- Publish events for downstream systems

---

### 5.2 FR-CROSS-002: Error Handling and Abend Processing

#### 5.2.1 Description
All programs must handle errors consistently using ABNDPROC for unrecoverable errors and ABNDFILE for error logging.

**Error Categories**:
1. **Recoverable**: Return fail code to caller (insufficient funds, not found, validation errors)
2. **Unrecoverable**: Call ABNDPROC and write to ABNDFILE (Db2 errors, VSAM errors, system errors)

**Source**: `ABNDPROC.cbl`, `ABNDFILE` VSAM, CICS HANDLE ABEND commands in programs

#### 5.2.2 Modernization Considerations
```java
// Exception hierarchy
public class BusinessRuleException extends RuntimeException {
    private final String failCode;
    private final HttpStatus httpStatus;
}

public class InsufficientFundsException extends BusinessRuleException {
    public InsufficientFundsException() {
        super("Insufficient funds", "3", HttpStatus.BAD_REQUEST);
    }
}

// Global exception handler
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRuleException(BusinessRuleException ex) {
        ErrorResponse error = new ErrorResponse();
        error.setCode(ex.getFailCode());
        error.setMessage(ex.getMessage());
        error.setTimestamp(Instant.now());
        error.setPath(RequestContext.getPath());
        
        logger.warn("Business rule violation: {}", ex.getMessage(), ex);
        
        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex) {
        logger.error("Unexpected error", ex);
        
        // Send to error tracking service (Sentry, Rollbar)
        errorTrackingService.captureException(ex);
        
        ErrorResponse error = new ErrorResponse();
        error.setCode("INTERNAL_ERROR");
        error.setMessage("An unexpected error occurred");
        error.setTimestamp(Instant.now());
        error.setPath(RequestContext.getPath());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```

**Recommendations**:
- Use structured exception hierarchy
- Map fail codes to HTTP status codes
- Implement global exception handler
- Use error tracking service (Sentry, Rollbar)
- Log all errors with correlation IDs

---

## 6. Open Questions

⚠️ **Functional Ambiguities**:
1. Should updates (UPDCUST, UPDACC) log to transaction log in modern system?
2. What is exact overdraft calculation formula (complexity not visible in code)?
3. Are there additional business rules for different account types (MORTGAGE, LOAN, ISA)?
4. Should account/customer deletes be soft (logical) or hard (physical)?
5. What are the exact credit agency integration protocols?
6. Should modern system support special customer IDs (0000000000, 9999999999)?
7. What are the exact performance SLAs for each operation?

⚠️ **Missing Details**:
1. DELACC and DELCUS implementation details (programs not fully analyzed)
2. Credit agency program (CRDTAGY1-5) implementation
3. ABNDFILE and ABNDPROC detailed logic
4. Business rules for ISA accounts (tax implications?)
5. Statement generation logic (not visible in analyzed programs)

---

*Document Version: 1.0*  
*Last Updated: 2025-10-27*  
*Source Repository: taylor-curran/og-cics-cobol-app*
