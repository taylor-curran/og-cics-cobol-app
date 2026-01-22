/*
 *
 *    Copyright IBM Corp. 2023
 *
 */
package com.ibm.cics.cip.bankliberty.api.json;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransferLocalJSON class.
 * Tests the JSON model used for local transfer requests.
 */
class TransferLocalJSONTest {

    private TransferLocalJSON transferLocalJSON;

    @BeforeEach
    void setUp() {
        transferLocalJSON = new TransferLocalJSON();
    }

    @Test
    @DisplayName("Should set and get target account correctly")
    void testSetAndGetTargetAccount() {
        Integer targetAccount = 12345678;
        transferLocalJSON.setTargetAccount(targetAccount);
        assertEquals(targetAccount, transferLocalJSON.getTargetAccount());
    }

    @Test
    @DisplayName("Should handle null target account")
    void testNullTargetAccount() {
        transferLocalJSON.setTargetAccount(null);
        assertNull(transferLocalJSON.getTargetAccount());
    }

    @Test
    @DisplayName("Should inherit amount from DebitCreditAccountJSON")
    void testInheritedAmountField() {
        BigDecimal amount = new BigDecimal("100.50");
        transferLocalJSON.setAmount(amount);
        assertEquals(amount, transferLocalJSON.getAmount());
    }

    @Test
    @DisplayName("Should handle zero amount")
    void testZeroAmount() {
        BigDecimal amount = BigDecimal.ZERO;
        transferLocalJSON.setAmount(amount);
        assertEquals(BigDecimal.ZERO, transferLocalJSON.getAmount());
    }

    @Test
    @DisplayName("Should handle negative amount")
    void testNegativeAmount() {
        BigDecimal amount = new BigDecimal("-50.00");
        transferLocalJSON.setAmount(amount);
        assertEquals(amount, transferLocalJSON.getAmount());
    }

    @Test
    @DisplayName("Should handle large amount")
    void testLargeAmount() {
        BigDecimal amount = new BigDecimal("9999999999.99");
        transferLocalJSON.setAmount(amount);
        assertEquals(amount, transferLocalJSON.getAmount());
    }

    @Test
    @DisplayName("Should handle amount with many decimal places")
    void testAmountWithManyDecimalPlaces() {
        BigDecimal amount = new BigDecimal("100.123456789");
        transferLocalJSON.setAmount(amount);
        assertEquals(amount, transferLocalJSON.getAmount());
    }

    @Test
    @DisplayName("Should be instance of DebitCreditAccountJSON")
    void testInheritance() {
        assertTrue(transferLocalJSON instanceof DebitCreditAccountJSON);
    }

    @Test
    @DisplayName("Should handle minimum valid account number")
    void testMinimumAccountNumber() {
        Integer targetAccount = 1;
        transferLocalJSON.setTargetAccount(targetAccount);
        assertEquals(targetAccount, transferLocalJSON.getTargetAccount());
    }

    @Test
    @DisplayName("Should handle maximum valid account number")
    void testMaximumAccountNumber() {
        Integer targetAccount = 99999998;
        transferLocalJSON.setTargetAccount(targetAccount);
        assertEquals(targetAccount, transferLocalJSON.getTargetAccount());
    }
}
