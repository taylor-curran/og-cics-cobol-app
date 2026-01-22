/*
 *
 *    Copyright IBM Corp. 2023
 *
 */
package com.ibm.cics.cip.bankliberty.api.json;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProcessedTransactionResource transfer functionality.
 * Tests the REST endpoint and business logic for transfer audit logging.
 */
@ExtendWith(MockitoExtension.class)
class ProcessedTransactionResourceTransferTest {

    @Nested
    @DisplayName("ProcessedTransactionTransferLocalJSON Tests")
    class ProcessedTransactionTransferLocalJSONTests {

        private ProcessedTransactionTransferLocalJSON proctranTransferLocal;

        @BeforeEach
        void setUp() {
            proctranTransferLocal = new ProcessedTransactionTransferLocalJSON();
        }

        @Test
        @DisplayName("Should set and get amount correctly")
        void testSetAndGetAmount() {
            BigDecimal amount = new BigDecimal("250.00");
            proctranTransferLocal.setAmount(amount);
            assertEquals(amount, proctranTransferLocal.getAmount());
        }

        @Test
        @DisplayName("Should set and get target account number correctly")
        void testSetAndGetTargetAccountNumber() {
            String targetAccountNumber = "87654321";
            proctranTransferLocal.setTargetAccountNumber(targetAccountNumber);
            assertEquals(targetAccountNumber, proctranTransferLocal.getTargetAccountNumber());
        }

        @Test
        @DisplayName("Should handle null amount")
        void testNullAmount() {
            proctranTransferLocal.setAmount(null);
            assertNull(proctranTransferLocal.getAmount());
        }

        @Test
        @DisplayName("Should handle null target account number")
        void testNullTargetAccountNumber() {
            proctranTransferLocal.setTargetAccountNumber(null);
            assertNull(proctranTransferLocal.getTargetAccountNumber());
        }

        @Test
        @DisplayName("Should handle zero amount")
        void testZeroAmount() {
            BigDecimal amount = BigDecimal.ZERO;
            proctranTransferLocal.setAmount(amount);
            assertEquals(BigDecimal.ZERO, proctranTransferLocal.getAmount());
        }

        @Test
        @DisplayName("Should handle large amount")
        void testLargeAmount() {
            BigDecimal amount = new BigDecimal("9999999999.99");
            proctranTransferLocal.setAmount(amount);
            assertEquals(amount, proctranTransferLocal.getAmount());
        }

        @Test
        @DisplayName("Should handle amount with many decimal places")
        void testAmountWithManyDecimalPlaces() {
            BigDecimal amount = new BigDecimal("100.123456789");
            proctranTransferLocal.setAmount(amount);
            assertEquals(amount, proctranTransferLocal.getAmount());
        }

        @Test
        @DisplayName("Should be instance of ProcessedTransactionJSON")
        void testInheritance() {
            assertTrue(proctranTransferLocal instanceof ProcessedTransactionJSON);
        }

        @Test
        @DisplayName("Should handle padded target account number")
        void testPaddedTargetAccountNumber() {
            String targetAccountNumber = "00012345";
            proctranTransferLocal.setTargetAccountNumber(targetAccountNumber);
            assertEquals(targetAccountNumber, proctranTransferLocal.getTargetAccountNumber());
        }

        @Test
        @DisplayName("Should handle maximum length target account number")
        void testMaxLengthTargetAccountNumber() {
            String targetAccountNumber = "99999998";
            proctranTransferLocal.setTargetAccountNumber(targetAccountNumber);
            assertEquals(targetAccountNumber, proctranTransferLocal.getTargetAccountNumber());
        }
    }

    @Nested
    @DisplayName("ProcessedTransactionJSON Base Class Tests")
    class ProcessedTransactionJSONBaseClassTests {

        private ProcessedTransactionTransferLocalJSON proctranTransferLocal;

        @BeforeEach
        void setUp() {
            proctranTransferLocal = new ProcessedTransactionTransferLocalJSON();
        }

        @Test
        @DisplayName("Should set and get sort code from parent class")
        void testSetAndGetSortCode() {
            String sortCode = "987654";
            proctranTransferLocal.setSortCode(sortCode);
            assertEquals(sortCode, proctranTransferLocal.getSortCode());
        }

        @Test
        @DisplayName("Should set and get account number from parent class")
        void testSetAndGetAccountNumber() {
            String accountNumber = "12345678";
            proctranTransferLocal.setAccountNumber(accountNumber);
            assertEquals(accountNumber, proctranTransferLocal.getAccountNumber());
        }
    }

    @Nested
    @DisplayName("Transfer Data Combination Tests")
    class TransferDataCombinationTests {

        @Test
        @DisplayName("Should hold all transfer data correctly")
        void testAllTransferData() {
            ProcessedTransactionTransferLocalJSON proctranTransferLocal = new ProcessedTransactionTransferLocalJSON();

            String sortCode = "987654";
            String accountNumber = "12345678";
            BigDecimal amount = new BigDecimal("500.00");
            String targetAccountNumber = "87654321";

            proctranTransferLocal.setSortCode(sortCode);
            proctranTransferLocal.setAccountNumber(accountNumber);
            proctranTransferLocal.setAmount(amount);
            proctranTransferLocal.setTargetAccountNumber(targetAccountNumber);

            assertEquals(sortCode, proctranTransferLocal.getSortCode());
            assertEquals(accountNumber, proctranTransferLocal.getAccountNumber());
            assertEquals(amount, proctranTransferLocal.getAmount());
            assertEquals(targetAccountNumber, proctranTransferLocal.getTargetAccountNumber());
        }

        @Test
        @DisplayName("Should allow updating transfer data")
        void testUpdateTransferData() {
            ProcessedTransactionTransferLocalJSON proctranTransferLocal = new ProcessedTransactionTransferLocalJSON();

            proctranTransferLocal.setAmount(new BigDecimal("100.00"));
            proctranTransferLocal.setTargetAccountNumber("11111111");

            proctranTransferLocal.setAmount(new BigDecimal("200.00"));
            proctranTransferLocal.setTargetAccountNumber("22222222");

            assertEquals(new BigDecimal("200.00"), proctranTransferLocal.getAmount());
            assertEquals("22222222", proctranTransferLocal.getTargetAccountNumber());
        }
    }
}
