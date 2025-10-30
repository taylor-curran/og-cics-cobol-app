# CBSA Carbon React UI Integration Tests - Postman Collection

This directory contains comprehensive integration tests for the CICS Banking Sample Application (CBSA) Carbon React UI, covering three key user stories for modern tellers.

## Overview

The test collection validates the following user stories:

### User Story 1: Create Customer with Age Validation
- **Requirement**: Customers must be between 18-120 years old (per user guide)
- **Actual Implementation**: COBOL backend enforces maximum age of 150 years, no minimum age check
- **Tests**:
  - Create customer with valid age (25 years) ✓
  - Create customer with minimum age (18 years) ✓
  - Create customer with maximum documented age (120 years) ✓
  - Create customer with age > 150 years (should fail) ✗
  - Create customer with future date of birth (should fail) ✗

**Important Note**: There is a discrepancy between the user guide documentation and the actual COBOL implementation:
- User guide specifies: 18-120 years
- COBOL implementation (`CRECUST.cbl` line 1408): Only checks if age > 150
- No minimum age validation found in COBOL backend

### User Story 2: View/Update Customer Details
- **Requirement**: Retrieve and update customers by ID or name, with expandable account views
- **Tests**:
  - Get customer by ID ✓
  - Get customer by name ✓
  - Update customer details (address) ✓
  - Get customer with expandable accounts view ✓

### User Story 3: Create Accounts with 10-Account Limit
- **Requirement**: Maximum 10 accounts per customer
- **Tests**:
  - Create first account successfully ✓
  - Create 9 additional accounts (total 10) ✓
  - Verify customer has exactly 10 accounts ✓
  - Attempt to create 11th account (should fail with 400 error) ✗

## Prerequisites

1. **CBSA Server Running**: The CICS Banking Sample Application must be running and accessible
2. **Postman or Newman**: 
   - [Postman Desktop App](https://www.postman.com/downloads/) (for manual testing)
   - [Newman CLI](https://www.npmjs.com/package/newman) (for automated testing)

## Files

- `CBSA_Carbon_React_UI_Integration_Tests.postman_collection.json` - Main test collection
- `CBSA_Local.postman_environment.json` - Environment configuration for local testing
- `README.md` - This documentation file

## Setup Instructions

### Option 1: Using Postman Desktop App

1. **Import Collection**:
   - Open Postman
   - Click "Import" button
   - Select `CBSA_Carbon_React_UI_Integration_Tests.postman_collection.json`
   - Click "Import"

2. **Import Environment**:
   - Click "Import" button again
   - Select `CBSA_Local.postman_environment.json`
   - Click "Import"

3. **Configure Environment**:
   - Select "CBSA Local" from the environment dropdown (top right)
   - Click the eye icon to view environment variables
   - Update variables if needed:
     - `baseUrl`: Default is `http://localhost:9080/webui-1.0/banking`
     - `sortCode`: Default is `123456`

4. **Run Tests**:
   - Open the collection "CBSA Carbon React UI Integration Tests"
   - Click "Run" button to open Collection Runner
   - Select "CBSA Local" environment
   - Click "Run CBSA Carbon React UI Integration Tests"

### Option 2: Using Newman CLI

1. **Install Newman** (if not already installed):
   ```bash
   npm install -g newman
   ```

2. **Run Tests**:
   ```bash
   newman run CBSA_Carbon_React_UI_Integration_Tests.postman_collection.json \
     -e CBSA_Local.postman_environment.json \
     --reporters cli,json \
     --reporter-json-export results.json
   ```

3. **Run with Custom Base URL**:
   ```bash
   newman run CBSA_Carbon_React_UI_Integration_Tests.postman_collection.json \
     -e CBSA_Local.postman_environment.json \
     --env-var "baseUrl=http://your-server:port/webui-1.0/banking" \
     --env-var "sortCode=123456"
   ```

## Test Execution Flow

The tests are designed to run sequentially:

1. **User Story 1 Tests** (5 requests):
   - Creates test customers with various ages
   - Validates age boundary conditions
   - Stores customer ID and customer number in collection variables

2. **User Story 2 Tests** (4 requests):
   - Retrieves the created customer by ID
   - Searches for customer by name
   - Updates customer details
   - Views accounts associated with the customer

3. **User Story 3 Tests** (4 requests):
   - Creates first account for the customer
   - Creates 9 more accounts (total 10) via pre-request script
   - Verifies exactly 10 accounts exist
   - Attempts to create 11th account (should fail)

## API Endpoints Tested

| HTTP Method | Endpoint | Description |
|-------------|----------|-------------|
| POST | `/customer` | Create new customer |
| GET | `/customer/{id}` | Get customer by ID |
| GET | `/customer/name?name={name}` | Search customers by name |
| PUT | `/customer/{id}` | Update customer details |
| POST | `/account` | Create new account |
| GET | `/account/retrieveByCustomerNumber/{customerNumber}` | Get all accounts for a customer |

## Expected Results

### Successful Tests (✓)
- Customer creation with valid ages (18-150 years)
- Customer retrieval by ID and name
- Customer detail updates
- Account creation (up to 10 per customer)
- Expandable account views

### Expected Failures (✗)
- Customer creation with age > 150 years (HTTP 400/422/500)
- Customer creation with future date of birth (HTTP 400/422/500)
- Creating 11th account for customer (HTTP 400)

## PROCTRAN Audit Logging

All successful create operations (customers and accounts) are automatically logged to the `PROCTRAN` table for audit purposes. This is handled by the backend and does not require explicit testing via the REST API. To verify PROCTRAN entries:

1. Access the database directly
2. Query: `SELECT * FROM PROCTRAN WHERE ...`
3. Verify entries exist for created customers and accounts

Note: Inquiry operations (GET requests) are NOT logged to PROCTRAN.

## Troubleshooting

### Server Not Accessible
```
Error: connect ECONNREFUSED localhost:9080
```
**Solution**: Ensure CBSA server is running and the `baseUrl` in your environment matches your server configuration.

### Invalid Sort Code
```
Error: Invalid sort code
```
**Solution**: Update the `sortCode` environment variable to match a valid sort code in your CBSA instance.

### Test Failures
If tests fail unexpectedly:
1. Check server logs for detailed error messages
2. Verify the CBSA database is accessible and properly configured
3. Ensure COBOL programs (CRECUST, etc.) are compiled and deployed
4. Check for any VSAM or DB2 connection issues

### Age Validation Discrepancy
The tests document a known discrepancy:
- **Expected** (per user guide): Age validation 18-120 years
- **Actual** (per COBOL code): Only validates age ≤ 150 years, no minimum

This is reflected in the test assertions and console output.

## Test Data Management

**Important**: These tests create real customer and account records in your CBSA instance. Consider:

1. **Using Test Environment**: Run tests against a development/test environment, not production
2. **Unique Test Data**: Tests use random numbers to generate unique customer names
3. **Data Cleanup**: Implement cleanup procedures if needed (tests do not automatically delete created data)
4. **Test Sort Code**: Consider using a dedicated sort code (e.g., `999999`) for test data

## Newman CI/CD Integration

To integrate with CI/CD pipelines:

```bash
#!/bin/bash
# Run tests and check exit code
newman run CBSA_Carbon_React_UI_Integration_Tests.postman_collection.json \
  -e CBSA_Local.postman_environment.json \
  --reporters cli,junit \
  --reporter-junit-export newman-results.xml

if [ $? -eq 0 ]; then
  echo "All tests passed!"
  exit 0
else
  echo "Tests failed!"
  exit 1
fi
```

## References

- **User Guide**: `etc/usage/carbonReactUI/doc/CBSA_Carbon_React_UI_User_Guide.md`
- **REST API Reference**: `etc/usage/carbonReactUI/doc/CBSA_RESTful_Interface_Reference.md`
- **Source Code**:
  - Customer API: `src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/CustomerResource.java`
  - Account API: `src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/AccountsResource.java`
  - COBOL Customer Creation: `src/base/cobol_src/CRECUST.cbl` (lines 1405-1417 for age validation)

## Contributing

When updating these tests:
1. Maintain backward compatibility with the existing collection structure
2. Update test assertions to reflect actual API behavior
3. Document any new discrepancies found between documentation and implementation
4. Add new test scenarios to appropriate folders (User Story 1, 2, or 3)

## Support

For issues or questions about these integration tests:
- Review the CBSA user guide and REST API reference documentation
- Check the source code for API endpoint implementations
- Verify COBOL business logic in the `src/base/cobol_src/` directory
