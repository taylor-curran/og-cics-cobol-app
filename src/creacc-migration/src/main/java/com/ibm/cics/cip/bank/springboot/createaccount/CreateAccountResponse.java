/*
 *
 *    Copyright IBM Corp. 2023
 *
 */
package com.ibm.cics.cip.bank.springboot.createaccount;

import java.math.BigDecimal;
import java.sql.Date;

/**
 * Response object for account creation.
 * Maps to the COBOL CREACC copybook output structure.
 */
public class CreateAccountResponse {

    private String eyecatcher;
    private String customerNumber;
    private String sortCode;
    private String accountNumber;
    private String accountType;
    private BigDecimal interestRate;
    private Date openedDate;
    private int overdraftLimit;
    private Date lastStatementDate;
    private Date nextStatementDate;
    private BigDecimal availableBalance;
    private BigDecimal actualBalance;
    private boolean success;
    private char failCode;

    public CreateAccountResponse() {
        this.eyecatcher = "ACCT";
        this.availableBalance = BigDecimal.ZERO;
        this.actualBalance = BigDecimal.ZERO;
    }

    public String getEyecatcher() {
        return eyecatcher;
    }

    public void setEyecatcher(String eyecatcher) {
        this.eyecatcher = eyecatcher;
    }

    public String getCustomerNumber() {
        return customerNumber;
    }

    public void setCustomerNumber(String customerNumber) {
        this.customerNumber = customerNumber;
    }

    public String getSortCode() {
        return sortCode;
    }

    public void setSortCode(String sortCode) {
        this.sortCode = sortCode;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }

    public Date getOpenedDate() {
        return openedDate;
    }

    public void setOpenedDate(Date openedDate) {
        this.openedDate = openedDate;
    }

    public int getOverdraftLimit() {
        return overdraftLimit;
    }

    public void setOverdraftLimit(int overdraftLimit) {
        this.overdraftLimit = overdraftLimit;
    }

    public Date getLastStatementDate() {
        return lastStatementDate;
    }

    public void setLastStatementDate(Date lastStatementDate) {
        this.lastStatementDate = lastStatementDate;
    }

    public Date getNextStatementDate() {
        return nextStatementDate;
    }

    public void setNextStatementDate(Date nextStatementDate) {
        this.nextStatementDate = nextStatementDate;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
    }

    public BigDecimal getActualBalance() {
        return actualBalance;
    }

    public void setActualBalance(BigDecimal actualBalance) {
        this.actualBalance = actualBalance;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public char getFailCode() {
        return failCode;
    }

    public void setFailCode(char failCode) {
        this.failCode = failCode;
    }

    public String getFailCodeDescription() {
        return switch (failCode) {
            case '1' -> "Customer not found";
            case '3' -> "Failed to enqueue named counter";
            case '5' -> "Failed to dequeue named counter";
            case '7' -> "Failed to insert account record";
            case '8' -> "Customer has too many accounts (>= 10)";
            case '9' -> "Error counting accounts";
            case 'A' -> "Invalid account type";
            case ' ' -> "Success";
            default -> "Unknown error code: " + failCode;
        };
    }

    @Override
    public String toString() {
        return "CreateAccountResponse{" +
                "eyecatcher='" + eyecatcher + '\'' +
                ", customerNumber='" + customerNumber + '\'' +
                ", sortCode='" + sortCode + '\'' +
                ", accountNumber='" + accountNumber + '\'' +
                ", accountType='" + accountType + '\'' +
                ", interestRate=" + interestRate +
                ", openedDate=" + openedDate +
                ", overdraftLimit=" + overdraftLimit +
                ", lastStatementDate=" + lastStatementDate +
                ", nextStatementDate=" + nextStatementDate +
                ", availableBalance=" + availableBalance +
                ", actualBalance=" + actualBalance +
                ", success=" + success +
                ", failCode=" + failCode +
                " (" + getFailCodeDescription() + ")" +
                '}';
    }
}
