/*
 *
 *    Copyright IBM Corp. 2023
 *
 */
package com.ibm.cics.cip.bank.springboot.createaccount;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main class for running the Create Account migration program.
 * This demonstrates the migrated COBOL CREACC program functionality
 * using a local SQLite database for testing.
 */
public class CreateAccountMain {

    private static final Logger logger = Logger.getLogger(
            "com.ibm.cics.cip.bank.springboot.createaccount");

    private static final String DB_URL = "jdbc:sqlite:banking.db";

    public static void main(String[] args) {
        logger.info("=== Create Account Migration Program ===");
        logger.info("This program demonstrates the migrated COBOL CREACC functionality");

        try {
            // Initialize the database
            initializeDatabase();

            // Create mock data for testing
            List<CreateAccountRequest> mockRequests = createMockData();

            // Process each mock request
            try (Connection connection = DriverManager.getConnection(DB_URL)) {
                connection.setAutoCommit(false);
                CreateAccountService service = new CreateAccountService(connection);

                for (CreateAccountRequest request : mockRequests) {
                    logger.info("\n--- Processing Request ---");
                    logger.info("Request: " + request);

                    CreateAccountResponse response = service.createAccount(request);

                    logger.info("Response: " + response);
                    if (response.isSuccess()) {
                        logger.info("SUCCESS: Account " + response.getAccountNumber() + 
                                " created for customer " + response.getCustomerNumber());
                    } else {
                        logger.info("FAILURE: " + response.getFailCodeDescription());
                    }
                }
            }

            logger.info("\n=== All test cases completed ===");

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error", e);
        }
    }

    /**
     * Creates mock data for testing various scenarios.
     * Test cases:
     * 1. Successful account creation
     * 2. Customer not found (fail code '1')
     * 3. Too many accounts (fail code '8')
     * 4. Invalid account type (fail code 'A')
     */
    private static List<CreateAccountRequest> createMockData() {
        List<CreateAccountRequest> requests = new ArrayList<>();

        // Test Case 1: Successful account creation - ISA account
        requests.add(new CreateAccountRequest(
                "1",           // customerNumber
                "987654",      // sortCode
                "ISA",         // accountType
                new BigDecimal("2.50"),  // interestRate
                1000           // overdraftLimit
        ));

        // Test Case 2: Successful account creation - MORTGAGE account
        requests.add(new CreateAccountRequest(
                "1",           // customerNumber
                "987654",      // sortCode
                "MORTGAGE",    // accountType
                new BigDecimal("4.99"),  // interestRate
                0              // overdraftLimit
        ));

        // Test Case 3: Successful account creation - SAVING account
        requests.add(new CreateAccountRequest(
                "2",           // customerNumber
                "987654",      // sortCode
                "SAVING",      // accountType
                new BigDecimal("1.25"),  // interestRate
                500            // overdraftLimit
        ));

        // Test Case 4: Successful account creation - CURRENT account
        requests.add(new CreateAccountRequest(
                "2",           // customerNumber
                "987654",      // sortCode
                "CURRENT",     // accountType
                new BigDecimal("0.10"),  // interestRate
                2000           // overdraftLimit
        ));

        // Test Case 5: Successful account creation - LOAN account
        requests.add(new CreateAccountRequest(
                "3",           // customerNumber
                "987654",      // sortCode
                "LOAN",        // accountType
                new BigDecimal("7.50"),  // interestRate
                0              // overdraftLimit
        ));

        // Test Case 6: Customer not found (fail code '1')
        requests.add(new CreateAccountRequest(
                "9999999",     // customerNumber - does not exist
                "987654",      // sortCode
                "CURRENT",     // accountType
                new BigDecimal("1.00"),  // interestRate
                500            // overdraftLimit
        ));

        // Test Case 7: Invalid account type (fail code 'A')
        requests.add(new CreateAccountRequest(
                "1",           // customerNumber
                "987654",      // sortCode
                "INVALID",     // accountType - invalid
                new BigDecimal("1.00"),  // interestRate
                500            // overdraftLimit
        ));

        // Test Case 8: Another valid account type test
        requests.add(new CreateAccountRequest(
                "3",           // customerNumber
                "987654",      // sortCode
                "isa",         // accountType - lowercase should work
                new BigDecimal("3.00"),  // interestRate
                1500           // overdraftLimit
        ));

        return requests;
    }

    /**
     * Initializes the SQLite database with the required tables and test data.
     */
    private static void initializeDatabase() throws SQLException {
        try (Connection connection = DriverManager.getConnection(DB_URL);
             Statement stmt = connection.createStatement()) {

            // Create CUSTOMER table
            stmt.execute("DROP TABLE IF EXISTS CUSTOMER");
            stmt.execute("""
                CREATE TABLE CUSTOMER (
                    CUSTOMER_EYECATCHER TEXT,
                    CUSTOMER_SORTCODE TEXT,
                    CUSTOMER_NUMBER TEXT PRIMARY KEY,
                    CUSTOMER_NAME TEXT,
                    CUSTOMER_ADDRESS TEXT,
                    CUSTOMER_DATE_OF_BIRTH TEXT,
                    CUSTOMER_CREDIT_SCORE INTEGER,
                    CUSTOMER_CS_REVIEW_DATE TEXT
                )
            """);

            // Create ACCOUNT table
            stmt.execute("DROP TABLE IF EXISTS ACCOUNT");
            stmt.execute("""
                CREATE TABLE ACCOUNT (
                    ACCOUNT_EYECATCHER TEXT,
                    ACCOUNT_CUSTOMER_NUMBER TEXT,
                    ACCOUNT_SORTCODE TEXT,
                    ACCOUNT_NUMBER TEXT PRIMARY KEY,
                    ACCOUNT_TYPE TEXT,
                    ACCOUNT_INTEREST_RATE REAL,
                    ACCOUNT_OPENED TEXT,
                    ACCOUNT_OVERDRAFT_LIMIT INTEGER,
                    ACCOUNT_LAST_STATEMENT TEXT,
                    ACCOUNT_NEXT_STATEMENT TEXT,
                    ACCOUNT_AVAILABLE_BALANCE REAL,
                    ACCOUNT_ACTUAL_BALANCE REAL
                )
            """);

            // Create CONTROL table
            stmt.execute("DROP TABLE IF EXISTS CONTROL");
            stmt.execute("""
                CREATE TABLE CONTROL (
                    CONTROL_NAME TEXT PRIMARY KEY,
                    CONTROL_VALUE_NUM INTEGER,
                    CONTROL_VALUE_STR TEXT
                )
            """);

            // Create PROCTRAN table
            stmt.execute("DROP TABLE IF EXISTS PROCTRAN");
            stmt.execute("""
                CREATE TABLE PROCTRAN (
                    PROCTRAN_EYECATCHER TEXT,
                    PROCTRAN_SORTCODE TEXT,
                    PROCTRAN_NUMBER TEXT,
                    PROCTRAN_DATE TEXT,
                    PROCTRAN_TIME TEXT,
                    PROCTRAN_REF TEXT,
                    PROCTRAN_TYPE TEXT,
                    PROCTRAN_DESC TEXT,
                    PROCTRAN_AMOUNT REAL
                )
            """);

            // Insert test customers
            stmt.execute("""
                INSERT INTO CUSTOMER (CUSTOMER_EYECATCHER, CUSTOMER_SORTCODE, 
                    CUSTOMER_NUMBER, CUSTOMER_NAME, CUSTOMER_ADDRESS, 
                    CUSTOMER_DATE_OF_BIRTH, CUSTOMER_CREDIT_SCORE, CUSTOMER_CS_REVIEW_DATE)
                VALUES ('CUST', '987654', '0000000001', 'John Smith', 
                    '123 Main Street, London', '1980-01-15', 750, '2024-01-01')
            """);
            stmt.execute("""
                INSERT INTO CUSTOMER (CUSTOMER_EYECATCHER, CUSTOMER_SORTCODE, 
                    CUSTOMER_NUMBER, CUSTOMER_NAME, CUSTOMER_ADDRESS, 
                    CUSTOMER_DATE_OF_BIRTH, CUSTOMER_CREDIT_SCORE, CUSTOMER_CS_REVIEW_DATE)
                VALUES ('CUST', '987654', '0000000002', 'Jane Doe', 
                    '456 Oak Avenue, Manchester', '1990-05-20', 800, '2024-02-15')
            """);
            stmt.execute("""
                INSERT INTO CUSTOMER (CUSTOMER_EYECATCHER, CUSTOMER_SORTCODE, 
                    CUSTOMER_NUMBER, CUSTOMER_NAME, CUSTOMER_ADDRESS, 
                    CUSTOMER_DATE_OF_BIRTH, CUSTOMER_CREDIT_SCORE, CUSTOMER_CS_REVIEW_DATE)
                VALUES ('CUST', '987654', '0000000003', 'Bob Johnson', 
                    '789 Pine Road, Birmingham', '1975-11-30', 650, '2024-03-01')
            """);

            // Insert control record for account numbering
            stmt.execute("""
                INSERT INTO CONTROL (CONTROL_NAME, CONTROL_VALUE_NUM, CONTROL_VALUE_STR)
                VALUES ('987654-ACCOUNT-LAST', 10000000, '')
            """);

            // Insert control record for account count
            stmt.execute("""
                INSERT INTO CONTROL (CONTROL_NAME, CONTROL_VALUE_NUM, CONTROL_VALUE_STR)
                VALUES ('987654-ACCOUNT-COUNT', 0, '')
            """);

            logger.info("Database initialized successfully with test data");
        }
    }
}
