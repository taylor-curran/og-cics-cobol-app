/*
 *
 *    Copyright IBM Corp. 2023
 *
 */
package com.ibm.cics.cip.bank.springboot.createaccount;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service class implementing the Create Account business logic.
 * This is a migration of the COBOL CREACC program to Java.
 * 
 * The COBOL program performs the following steps:
 * 1. Validate that the customer exists (links to INQCUST)
 * 2. Count customer's existing accounts (links to INQACCCU)
 * 3. Validate account type (ISA, MORTGAGE, SAVING, CURRENT, LOAN)
 * 4. Get next account number from CONTROL table
 * 5. Insert into ACCOUNT table
 * 6. Update CONTROL table with new account number
 * 7. Write audit record to PROCTRAN table
 * 
 * Failure codes:
 * '1' = Customer not found
 * '3' = Failed to enqueue named counter
 * '5' = Failed to dequeue named counter
 * '7' = Failed to insert account record
 * '8' = Customer has too many accounts (>= 10)
 * '9' = Error counting accounts
 * 'A' = Invalid account type
 */
public class CreateAccountService {

    private static final Logger logger = Logger.getLogger(
            "com.ibm.cics.cip.bank.springboot.createaccount");

    private static final int MAXIMUM_ACCOUNTS_PER_CUSTOMER = 10;
    private static final int CUSTOMER_NUMBER_LENGTH = 10;
    private static final int ACCOUNT_NUMBER_LENGTH = 8;
    private static final int SORT_CODE_LENGTH = 6;

    private final Connection connection;

    public CreateAccountService(Connection connection) {
        this.connection = connection;
    }

    /**
     * Creates a new account based on the request parameters.
     * Implements the same business logic as the COBOL CREACC program.
     * 
     * @param request The account creation request
     * @return The response containing the created account or failure information
     */
    public CreateAccountResponse createAccount(CreateAccountRequest request) {
        logger.entering(this.getClass().getName(), "createAccount", request);

        CreateAccountResponse response = new CreateAccountResponse();
        response.setCustomerNumber(padCustomerNumber(request.getCustomerNumber()));
        response.setSortCode(padSortCode(request.getSortCode()));
        response.setAccountType(request.getAccountType());
        response.setInterestRate(request.getInterestRate());
        response.setOverdraftLimit(request.getOverdraftLimit());

        try {
            // Step 1: Validate that the customer exists
            if (!customerExists(request.getCustomerNumber())) {
                response.setSuccess(false);
                response.setFailCode('1');
                logger.log(Level.WARNING, "Customer not found: {0}", 
                        request.getCustomerNumber());
                return response;
            }

            // Step 2: Count customer's existing accounts
            int accountCount = countCustomerAccounts(request.getCustomerNumber(), 
                    request.getSortCode());
            if (accountCount < 0) {
                response.setSuccess(false);
                response.setFailCode('9');
                logger.log(Level.WARNING, "Error counting accounts for customer: {0}", 
                        request.getCustomerNumber());
                return response;
            }

            // Step 3: Check if customer has too many accounts
            if (accountCount >= MAXIMUM_ACCOUNTS_PER_CUSTOMER) {
                response.setSuccess(false);
                response.setFailCode('8');
                logger.log(Level.WARNING, 
                        "Customer {0} has too many accounts: {1}", 
                        new Object[]{request.getCustomerNumber(), accountCount});
                return response;
            }

            // Step 4: Validate account type
            if (!isValidAccountType(request.getAccountType())) {
                response.setSuccess(false);
                response.setFailCode('A');
                logger.log(Level.WARNING, "Invalid account type: {0}", 
                        request.getAccountType());
                return response;
            }

            // Step 5: Get next account number from CONTROL table
            int nextAccountNumber = getNextAccountNumber(request.getSortCode());
            if (nextAccountNumber < 0) {
                response.setSuccess(false);
                response.setFailCode('3');
                logger.log(Level.WARNING, "Failed to get next account number");
                return response;
            }

            // Step 6: Calculate dates
            LocalDate today = LocalDate.now();
            Date openedDate = Date.valueOf(today);
            Date lastStatementDate = openedDate;
            Date nextStatementDate = calculateNextStatementDate(today);

            response.setOpenedDate(openedDate);
            response.setLastStatementDate(lastStatementDate);
            response.setNextStatementDate(nextStatementDate);
            response.setAccountNumber(padAccountNumber(nextAccountNumber));
            response.setAvailableBalance(BigDecimal.ZERO);
            response.setActualBalance(BigDecimal.ZERO);

            // Step 7: Insert into ACCOUNT table
            boolean insertSuccess = insertAccount(response, request);
            if (!insertSuccess) {
                response.setSuccess(false);
                response.setFailCode('7');
                logger.log(Level.WARNING, "Failed to insert account record");
                return response;
            }

            // Step 8: Update CONTROL table with new account number
            boolean updateSuccess = updateControlTable(request.getSortCode(), 
                    nextAccountNumber);
            if (!updateSuccess) {
                response.setSuccess(false);
                response.setFailCode('5');
                logger.log(Level.WARNING, "Failed to update CONTROL table");
                return response;
            }

            // Step 9: Write audit record to PROCTRAN table
            boolean proctranSuccess = writeProctranRecord(response);
            if (!proctranSuccess) {
                logger.log(Level.WARNING, 
                        "Failed to write PROCTRAN record, rolling back");
                connection.rollback();
                response.setSuccess(false);
                response.setFailCode('7');
                return response;
            }

            // Commit the transaction
            connection.commit();

            response.setSuccess(true);
            response.setFailCode(' ');
            response.setEyecatcher("ACCT");

            logger.log(Level.INFO, "Account created successfully: {0}", 
                    response.getAccountNumber());

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error during account creation", e);
            response.setSuccess(false);
            response.setFailCode('7');
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                logger.log(Level.SEVERE, "Failed to rollback transaction", rollbackEx);
            }
        }

        logger.exiting(this.getClass().getName(), "createAccount", response);
        return response;
    }

    /**
     * Checks if a customer exists in the CUSTOMER table.
     * Equivalent to the COBOL LINK to INQCUST program.
     */
    private boolean customerExists(String customerNumber) throws SQLException {
        String sql = "SELECT COUNT(*) FROM CUSTOMER WHERE CUSTOMER_NUMBER = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, padCustomerNumber(customerNumber));
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    /**
     * Counts the number of accounts for a customer.
     * Equivalent to the COBOL LINK to INQACCCU program.
     */
    private int countCustomerAccounts(String customerNumber, String sortCode) 
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM ACCOUNT " +
                "WHERE ACCOUNT_CUSTOMER_NUMBER = ? AND ACCOUNT_SORTCODE = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, padCustomerNumber(customerNumber));
            stmt.setString(2, padSortCode(sortCode));
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return -1;
    }

    /**
     * Validates that the account type is one of the allowed types.
     * Valid types: ISA, MORTGAGE, SAVING, CURRENT, LOAN
     * Equivalent to the COBOL ACCOUNT-TYPE-CHECK section.
     */
    private boolean isValidAccountType(String accountType) {
        if (accountType == null) {
            return false;
        }
        String type = accountType.trim().toUpperCase();
        return type.equals("ISA") || 
               type.equals("MORTGAGE") || 
               type.equals("SAVING") || 
               type.equals("CURRENT") || 
               type.equals("LOAN");
    }

    /**
     * Gets the next account number from the CONTROL table.
     * Equivalent to the COBOL FIND-NEXT-ACCOUNT section.
     */
    private int getNextAccountNumber(String sortCode) throws SQLException {
        String controlName = padSortCode(sortCode) + "-ACCOUNT-LAST";
        String sql = "SELECT CONTROL_VALUE_NUM FROM CONTROL WHERE CONTROL_NAME = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, controlName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) + 1;
            }
        }
        return -1;
    }

    /**
     * Updates the CONTROL table with the new account number.
     */
    private boolean updateControlTable(String sortCode, int newAccountNumber) 
            throws SQLException {
        String controlName = padSortCode(sortCode) + "-ACCOUNT-LAST";
        String sql = "UPDATE CONTROL SET CONTROL_VALUE_NUM = ? WHERE CONTROL_NAME = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, newAccountNumber);
            stmt.setString(2, controlName);
            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        }
    }

    /**
     * Inserts a new account record into the ACCOUNT table.
     * Equivalent to the COBOL WRITE-ACCOUNT-DB2 section.
     */
    private boolean insertAccount(CreateAccountResponse response, 
            CreateAccountRequest request) throws SQLException {
        String sql = "INSERT INTO ACCOUNT (" +
                "ACCOUNT_EYECATCHER, " +
                "ACCOUNT_CUSTOMER_NUMBER, " +
                "ACCOUNT_SORTCODE, " +
                "ACCOUNT_NUMBER, " +
                "ACCOUNT_TYPE, " +
                "ACCOUNT_INTEREST_RATE, " +
                "ACCOUNT_OPENED, " +
                "ACCOUNT_OVERDRAFT_LIMIT, " +
                "ACCOUNT_LAST_STATEMENT, " +
                "ACCOUNT_NEXT_STATEMENT, " +
                "ACCOUNT_AVAILABLE_BALANCE, " +
                "ACCOUNT_ACTUAL_BALANCE" +
                ") VALUES ('ACCT', ?, ?, ?, ?, ?, ?, ?, ?, ?, 0.00, 0.00)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, response.getCustomerNumber());
            stmt.setString(2, response.getSortCode());
            stmt.setString(3, response.getAccountNumber());
            stmt.setString(4, request.getAccountType());
            stmt.setBigDecimal(5, request.getInterestRate());
            stmt.setDate(6, response.getOpenedDate());
            stmt.setInt(7, request.getOverdraftLimit());
            stmt.setDate(8, response.getLastStatementDate());
            stmt.setDate(9, response.getNextStatementDate());
            int rowsInserted = stmt.executeUpdate();
            return rowsInserted > 0;
        }
    }

    /**
     * Writes an audit record to the PROCTRAN table.
     * Equivalent to the COBOL WRITE-PROCTRAN-DB2 section.
     */
    private boolean writeProctranRecord(CreateAccountResponse response) 
            throws SQLException {
        String sql = "INSERT INTO PROCTRAN (" +
                "PROCTRAN_EYECATCHER, " +
                "PROCTRAN_SORTCODE, " +
                "PROCTRAN_NUMBER, " +
                "PROCTRAN_DATE, " +
                "PROCTRAN_TIME, " +
                "PROCTRAN_REF, " +
                "PROCTRAN_TYPE, " +
                "PROCTRAN_DESC, " +
                "PROCTRAN_AMOUNT" +
                ") VALUES ('PRTR', ?, ?, ?, ?, ?, 'OCA', ?, 0.00)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, response.getSortCode());
            stmt.setString(2, response.getAccountNumber());
            stmt.setString(3, response.getOpenedDate().toString());
            stmt.setString(4, getCurrentTime());
            stmt.setString(5, generateReference());
            
            // Description format: customer number (10) + account type (8) + 
            // last stmt (8) + next stmt (8) + spaces (6)
            String description = String.format("%-10s%-8s%-8s%-8s      ",
                    response.getCustomerNumber(),
                    response.getAccountType(),
                    formatDateForProctran(response.getLastStatementDate()),
                    formatDateForProctran(response.getNextStatementDate()));
            stmt.setString(6, description);
            
            int rowsInserted = stmt.executeUpdate();
            return rowsInserted > 0;
        }
    }

    /**
     * Calculates the next statement date (today + approximately 30 days).
     * Equivalent to the COBOL CALCULATE-DATES section.
     */
    private Date calculateNextStatementDate(LocalDate today) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(Date.valueOf(today));
        
        int month = cal.get(Calendar.MONTH);
        int daysToAdd;
        
        switch (month) {
            case Calendar.SEPTEMBER:
            case Calendar.APRIL:
            case Calendar.JUNE:
            case Calendar.NOVEMBER:
                daysToAdd = 30;
                break;
            case Calendar.FEBRUARY:
                int year = cal.get(Calendar.YEAR);
                if (isLeapYear(year)) {
                    daysToAdd = 29;
                } else {
                    daysToAdd = 28;
                }
                break;
            default:
                daysToAdd = 30;
                break;
        }
        
        cal.add(Calendar.DAY_OF_MONTH, daysToAdd);
        return new Date(cal.getTimeInMillis());
    }

    /**
     * Checks if a year is a leap year.
     */
    private boolean isLeapYear(int year) {
        if (year % 4 != 0) {
            return false;
        }
        if (year % 100 != 0) {
            return true;
        }
        return year % 400 == 0;
    }

    /**
     * Pads a customer number to 10 digits with leading zeros.
     */
    private String padCustomerNumber(String customerNumber) {
        if (customerNumber == null) {
            return "0000000000";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = customerNumber.length(); i < CUSTOMER_NUMBER_LENGTH; i++) {
            sb.append('0');
        }
        sb.append(customerNumber);
        return sb.toString();
    }

    /**
     * Pads an account number to 8 digits with leading zeros.
     */
    private String padAccountNumber(int accountNumber) {
        String accNum = Integer.toString(accountNumber);
        StringBuilder sb = new StringBuilder();
        for (int i = accNum.length(); i < ACCOUNT_NUMBER_LENGTH; i++) {
            sb.append('0');
        }
        sb.append(accNum);
        return sb.toString();
    }

    /**
     * Pads a sort code to 6 digits with leading zeros.
     */
    private String padSortCode(String sortCode) {
        if (sortCode == null) {
            return "000000";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = sortCode.length(); i < SORT_CODE_LENGTH; i++) {
            sb.append('0');
        }
        sb.append(sortCode);
        return sb.toString();
    }

    /**
     * Gets the current time in HHMMSS format.
     */
    private String getCurrentTime() {
        Calendar cal = Calendar.getInstance();
        return String.format("%02d%02d%02d",
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                cal.get(Calendar.SECOND));
    }

    /**
     * Generates a reference number for the PROCTRAN record.
     */
    private String generateReference() {
        return String.format("%012d", System.currentTimeMillis() % 1000000000000L);
    }

    /**
     * Formats a date for the PROCTRAN description (DDMMYYYY).
     */
    private String formatDateForProctran(Date date) {
        if (date == null) {
            return "        ";
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return String.format("%02d%02d%04d",
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.YEAR));
    }
}
