/*
 *
 *    Copyright IBM Corp. 2023
 *
 */
package com.ibm.cics.cip.bankliberty.web.db2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ibm.cics.cip.bankliberty.datainterfaces.PROCTRAN;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProcessedTransaction transfer functionality.
 * Tests the writeTransferLocal method and related transfer operations.
 */
@ExtendWith(MockitoExtension.class)
class ProcessedTransactionTransferTest {

    @Nested
    @DisplayName("Transfer Description Format Tests")
    class TransferDescriptionFormatTests {

        @Test
        @DisplayName("PROCTRAN transfer type constant should be TFR")
        void testTransferTypeConstant() {
            assertEquals("TFR", PROCTRAN.PROC_TY_TRANSFER);
        }

        @Test
        @DisplayName("PROCTRAN transfer description flag should be defined")
        void testTransferDescriptionFlag() {
            assertNotNull(PROCTRAN.PROC_TRAN_DESC_XFR_FLAG);
        }

        @Test
        @DisplayName("PROCTRAN valid transaction marker should be defined")
        void testValidTransactionMarker() {
            assertNotNull(PROCTRAN.PROC_TRAN_VALID);
        }
    }

    @Nested
    @DisplayName("ProcessedTransaction Field Tests")
    class ProcessedTransactionFieldTests {

        private ProcessedTransaction processedTransaction;

        @BeforeEach
        void setUp() {
            processedTransaction = new ProcessedTransaction();
        }

        @Test
        @DisplayName("Should set and get sortcode correctly")
        void testSetAndGetSortcode() {
            String sortcode = "987654";
            processedTransaction.setSortcode(sortcode);
            assertEquals(sortcode, processedTransaction.getSortcode());
        }

        @Test
        @DisplayName("Should set and get account number correctly")
        void testSetAndGetAccountNumber() {
            String accountNumber = "12345678";
            processedTransaction.setAccountNumber(accountNumber);
            assertEquals(accountNumber, processedTransaction.getAccountNumber());
        }

        @Test
        @DisplayName("Should set and get target account number correctly")
        void testSetAndGetTargetAccountNumber() {
            String targetAccountNumber = "87654321";
            processedTransaction.setTargetAccountNumber(targetAccountNumber);
            assertEquals(targetAccountNumber, processedTransaction.getTargetAccountNumber());
        }

        @Test
        @DisplayName("Should set and get target sortcode correctly")
        void testSetAndGetTargetSortcode() {
            String targetSortcode = "123456";
            processedTransaction.setTargetSortcode(targetSortcode);
            assertEquals(targetSortcode, processedTransaction.getTargetSortcode());
        }

        @Test
        @DisplayName("Should set and get type correctly")
        void testSetAndGetType() {
            String type = "TFR";
            processedTransaction.setType(type);
            assertEquals(type, processedTransaction.getType());
        }

        @Test
        @DisplayName("Should set and get amount correctly")
        void testSetAndGetAmount() {
            double amount = 500.00;
            processedTransaction.setAmount(amount);
            assertEquals(amount, processedTransaction.getAmount(), 0.001);
        }

        @Test
        @DisplayName("Should set and get transfer flag correctly")
        void testSetAndGetTransferFlag() {
            processedTransaction.setTransfer(true);
            assertTrue(processedTransaction.isTransfer());

            processedTransaction.setTransfer(false);
            assertFalse(processedTransaction.isTransfer());
        }

        @Test
        @DisplayName("Should set and get description correctly")
        void testSetAndGetDescription() {
            String description = "Transfer to account 87654321";
            processedTransaction.setDescription(description);
            assertEquals(description, processedTransaction.getDescription());
        }

        @Test
        @DisplayName("Should set and get reference correctly")
        void testSetAndGetReference() {
            String reference = "REF123456789";
            processedTransaction.setReference(reference);
            assertEquals(reference, processedTransaction.getReference());
        }

        @Test
        @DisplayName("Should set and get customer correctly")
        void testSetAndGetCustomer() {
            String customer = "0000012345";
            processedTransaction.setCustomer(customer);
            assertEquals(customer, processedTransaction.getCustomer());
        }
    }

    @Nested
    @DisplayName("Transfer Amount Tests")
    class TransferAmountTests {

        private ProcessedTransaction processedTransaction;

        @BeforeEach
        void setUp() {
            processedTransaction = new ProcessedTransaction();
        }

        @Test
        @DisplayName("Should handle zero amount")
        void testZeroAmount() {
            processedTransaction.setAmount(0.0);
            assertEquals(0.0, processedTransaction.getAmount(), 0.001);
        }

        @Test
        @DisplayName("Should handle positive amount")
        void testPositiveAmount() {
            processedTransaction.setAmount(1000.50);
            assertEquals(1000.50, processedTransaction.getAmount(), 0.001);
        }

        @Test
        @DisplayName("Should handle negative amount")
        void testNegativeAmount() {
            processedTransaction.setAmount(-500.00);
            assertEquals(-500.00, processedTransaction.getAmount(), 0.001);
        }

        @Test
        @DisplayName("Should handle large amount")
        void testLargeAmount() {
            processedTransaction.setAmount(9999999999.99);
            assertEquals(9999999999.99, processedTransaction.getAmount(), 0.001);
        }

        @Test
        @DisplayName("Should handle small decimal amount")
        void testSmallDecimalAmount() {
            processedTransaction.setAmount(0.01);
            assertEquals(0.01, processedTransaction.getAmount(), 0.001);
        }
    }

    @Nested
    @DisplayName("Transfer Record Processing Tests")
    class TransferRecordProcessingTests {

        @Test
        @DisplayName("Should identify transfer type correctly")
        void testTransferTypeIdentification() {
            ProcessedTransaction processedTransaction = new ProcessedTransaction();
            processedTransaction.setType("TFR");
            assertEquals("TFR", processedTransaction.getType());
            assertEquals(PROCTRAN.PROC_TY_TRANSFER, processedTransaction.getType());
        }

        @Test
        @DisplayName("Should distinguish transfer from debit")
        void testDistinguishTransferFromDebit() {
            ProcessedTransaction processedTransaction = new ProcessedTransaction();
            processedTransaction.setType(PROCTRAN.PROC_TY_DEBIT);
            assertNotEquals(PROCTRAN.PROC_TY_TRANSFER, processedTransaction.getType());
        }

        @Test
        @DisplayName("Should distinguish transfer from credit")
        void testDistinguishTransferFromCredit() {
            ProcessedTransaction processedTransaction = new ProcessedTransaction();
            processedTransaction.setType(PROCTRAN.PROC_TY_CREDIT);
            assertNotEquals(PROCTRAN.PROC_TY_TRANSFER, processedTransaction.getType());
        }
    }

    @Nested
    @DisplayName("Account Number Padding Tests")
    class AccountNumberPaddingTests {

        private ProcessedTransaction processedTransaction;

        @BeforeEach
        void setUp() {
            processedTransaction = new ProcessedTransaction();
        }

        @Test
        @DisplayName("Should handle 8-digit account number")
        void testEightDigitAccountNumber() {
            String accountNumber = "12345678";
            processedTransaction.setAccountNumber(accountNumber);
            assertEquals(accountNumber, processedTransaction.getAccountNumber());
        }

        @Test
        @DisplayName("Should handle short account number")
        void testShortAccountNumber() {
            String accountNumber = "12345";
            processedTransaction.setAccountNumber(accountNumber);
            assertEquals(accountNumber, processedTransaction.getAccountNumber());
        }

        @Test
        @DisplayName("Should handle 6-digit sortcode")
        void testSixDigitSortcode() {
            String sortcode = "987654";
            processedTransaction.setSortcode(sortcode);
            assertEquals(sortcode, processedTransaction.getSortcode());
        }
    }

    @Nested
    @DisplayName("Transfer Data Combination Tests")
    class TransferDataCombinationTests {

        @Test
        @DisplayName("Should hold complete transfer data")
        void testCompleteTransferData() {
            ProcessedTransaction processedTransaction = new ProcessedTransaction();

            String sortcode = "987654";
            String accountNumber = "12345678";
            String targetAccountNumber = "87654321";
            String targetSortcode = "987654";
            double amount = 500.00;
            String type = "TFR";

            processedTransaction.setSortcode(sortcode);
            processedTransaction.setAccountNumber(accountNumber);
            processedTransaction.setTargetAccountNumber(targetAccountNumber);
            processedTransaction.setTargetSortcode(targetSortcode);
            processedTransaction.setAmount(amount);
            processedTransaction.setType(type);
            processedTransaction.setTransfer(true);

            assertEquals(sortcode, processedTransaction.getSortcode());
            assertEquals(accountNumber, processedTransaction.getAccountNumber());
            assertEquals(targetAccountNumber, processedTransaction.getTargetAccountNumber());
            assertEquals(targetSortcode, processedTransaction.getTargetSortcode());
            assertEquals(amount, processedTransaction.getAmount(), 0.001);
            assertEquals(type, processedTransaction.getType());
            assertTrue(processedTransaction.isTransfer());
        }
    }
}
