/*
 *
 *    Copyright IBM Corp. 2023
 *
 */
package com.ibm.cics.cip.bank.springboot.createaccount;

import java.math.BigDecimal;

/**
 * Request object for creating an account.
 * Maps to the COBOL CREACC copybook structure.
 */
public class CreateAccountRequest {

    private String customerNumber;
    private String sortCode;
    private String accountType;
    private BigDecimal interestRate;
    private int overdraftLimit;

    public CreateAccountRequest() {
    }

    public CreateAccountRequest(String customerNumber, String sortCode, 
            String accountType, BigDecimal interestRate, int overdraftLimit) {
        this.customerNumber = customerNumber;
        this.sortCode = sortCode;
        this.accountType = accountType;
        this.interestRate = interestRate;
        this.overdraftLimit = overdraftLimit;
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

    public int getOverdraftLimit() {
        return overdraftLimit;
    }

    public void setOverdraftLimit(int overdraftLimit) {
        this.overdraftLimit = overdraftLimit;
    }

    @Override
    public String toString() {
        return "CreateAccountRequest{" +
                "customerNumber='" + customerNumber + '\'' +
                ", sortCode='" + sortCode + '\'' +
                ", accountType='" + accountType + '\'' +
                ", interestRate=" + interestRate +
                ", overdraftLimit=" + overdraftLimit +
                '}';
    }
}
