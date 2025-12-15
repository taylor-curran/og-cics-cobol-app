# Create Account Migration

This module contains a standalone Java implementation of the COBOL CREACC program, migrated from the original mainframe application.

## Overview

The CREACC program creates new bank accounts for existing customers. This Java implementation replicates the exact business logic from the COBOL version, including:

- Customer validation
- Account count validation (max 10 accounts per customer)
- Account type validation (ISA, MORTGAGE, SAVING, CURRENT, LOAN)
- Account number generation from CONTROL table
- Account record insertion
- Audit trail writing to PROCTRAN table

## Failure Codes

The program uses the same failure codes as the COBOL version:

| Code | Description |
|------|-------------|
| '1'  | Customer not found |
| '3'  | Failed to enqueue named counter |
| '5'  | Failed to dequeue named counter |
| '7'  | Failed to insert account record |
| '8'  | Customer has too many accounts (>= 10) |
| '9'  | Error counting accounts |
| 'A'  | Invalid account type |

## Building

```bash
cd src/creacc-migration
mvn compile
```

## Running

The main class includes mock data for testing:

```bash
cd src/creacc-migration
mvn exec:java
```

This will:
1. Initialize a local SQLite database with test data
2. Run through various test scenarios
3. Display the results for each account creation attempt

## Project Structure

- `CreateAccountRequest.java` - Input request object (maps to COBOL CREACC copybook)
- `CreateAccountResponse.java` - Output response object
- `CreateAccountService.java` - Main business logic implementation
- `CreateAccountMain.java` - Entry point with mock data for testing

## Original COBOL Program

The original COBOL program is located at:
- `src/base/cobol_src/CREACC.cbl` - Main program
- `src/base/cobol_copy/CREACC.cpy` - Copybook defining the COMMAREA structure
