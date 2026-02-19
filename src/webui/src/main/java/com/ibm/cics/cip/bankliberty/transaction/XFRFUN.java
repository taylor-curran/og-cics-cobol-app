/*
 *
 *    Copyright IBM Corp. 2023
 *
 */

package com.ibm.cics.cip.bankliberty.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.cics.server.CicsConditionException;
import com.ibm.cics.server.CommAreaHolder;
import com.ibm.cics.server.InvalidRequestException;
import com.ibm.cics.server.Task;
import com.ibm.cics.cip.bankliberty.api.json.HBankDataAccess;
import com.ibm.cics.cip.bankliberty.web.db2.Account;
import com.ibm.cics.cip.bankliberty.web.db2.ProcessedTransaction;

public class XFRFUN
{

	private static Logger logger = Logger
			.getLogger("com.ibm.cics.cip.bankliberty.transaction");

	private static final String CLASS_NAME = "XFRFUN";

	private static final int FACCNO_OFFSET = 0;

	private static final int FACCNO_LENGTH = 8;

	private static final int FSCODE_OFFSET = 8;

	private static final int FSCODE_LENGTH = 6;

	private static final int TACCNO_OFFSET = 14;

	private static final int TACCNO_LENGTH = 8;

	private static final int TSCODE_OFFSET = 22;

	private static final int TSCODE_LENGTH = 6;

	private static final int AMT_OFFSET = 28;

	private static final int AMT_LENGTH = 12;

	private static final int FAVBAL_OFFSET = 40;

	private static final int FAVBAL_LENGTH = 12;

	private static final int FACTBAL_OFFSET = 52;

	private static final int FACTBAL_LENGTH = 12;

	private static final int TAVBAL_OFFSET = 64;

	private static final int TAVBAL_LENGTH = 12;

	private static final int TACTBAL_OFFSET = 76;

	private static final int TACTBAL_LENGTH = 12;

	private static final int FAIL_CODE_OFFSET = 88;

	private static final int SUCCESS_OFFSET = 89;

	static final int COMMAREA_LENGTH = 90;

	private static final char FAIL_FROM_NOT_FOUND = '1';

	private static final char FAIL_TO_NOT_FOUND = '2';

	private static final char FAIL_UNEXPECTED = '3';

	private static final char FAIL_ZERO_AMOUNT = '4';


	public static void main(CommAreaHolder cah)
	{
		logger.entering(CLASS_NAME, "main");

		byte[] commArea = cah.getValue();
		if (commArea == null || commArea.length < COMMAREA_LENGTH)
		{
			logger.severe("Invalid commarea");
			return;
		}

		String fromAccNo = extractField(commArea, FACCNO_OFFSET,
				FACCNO_LENGTH);
		String toAccNo = extractField(commArea, TACCNO_OFFSET, TACCNO_LENGTH);
		BigDecimal amount = extractAmount(commArea, AMT_OFFSET, AMT_LENGTH);

		String sortCode = getSortCode();

		writeField(commArea, FSCODE_OFFSET, FSCODE_LENGTH, sortCode);
		writeField(commArea, TSCODE_OFFSET, TSCODE_LENGTH, sortCode);

		if (amount.compareTo(BigDecimal.ZERO) <= 0)
		{
			commArea[FAIL_CODE_OFFSET] = (byte) FAIL_ZERO_AMOUNT;
			commArea[SUCCESS_OFFSET] = (byte) 'N';
			logger.exiting(CLASS_NAME, "main");
			return;
		}

		amount = amount.setScale(2, RoundingMode.HALF_UP);
		BigDecimal negativeAmount = amount.negate()
				.setScale(2, RoundingMode.HALF_UP);

		try
		{
			Account fromAccount = new Account();
			fromAccount.setAccountNumber(fromAccNo);
			fromAccount.setSortcode(sortCode);

			if (!fromAccount.debitCredit(negativeAmount))
			{
				commArea[FAIL_CODE_OFFSET] = (byte) FAIL_FROM_NOT_FOUND;
				commArea[SUCCESS_OFFSET] = (byte) 'N';
				rollback();
				logger.exiting(CLASS_NAME, "main");
				return;
			}

			double fromAvailBal = fromAccount.getAvailableBalance();
			double fromActualBal = fromAccount.getActualBalance();

			Account toAccount = new Account();
			toAccount.setAccountNumber(toAccNo);
			toAccount.setSortcode(sortCode);

			if (!toAccount.debitCredit(amount))
			{
				commArea[FAIL_CODE_OFFSET] = (byte) FAIL_TO_NOT_FOUND;
				commArea[SUCCESS_OFFSET] = (byte) 'N';
				rollback();
				logger.exiting(CLASS_NAME, "main");
				return;
			}

			double toAvailBal = toAccount.getAvailableBalance();
			double toActualBal = toAccount.getActualBalance();

			ProcessedTransaction proctran = new ProcessedTransaction();
			if (!proctran.writeTransferLocal(sortCode, fromAccNo, amount,
					toAccNo))
			{
				commArea[FAIL_CODE_OFFSET] = (byte) FAIL_UNEXPECTED;
				commArea[SUCCESS_OFFSET] = (byte) 'N';
				rollback();
				logger.exiting(CLASS_NAME, "main");
				return;
			}

			writeAmount(commArea, FAVBAL_OFFSET, FAVBAL_LENGTH,
					BigDecimal.valueOf(fromAvailBal));
			writeAmount(commArea, FACTBAL_OFFSET, FACTBAL_LENGTH,
					BigDecimal.valueOf(fromActualBal));
			writeAmount(commArea, TAVBAL_OFFSET, TAVBAL_LENGTH,
					BigDecimal.valueOf(toAvailBal));
			writeAmount(commArea, TACTBAL_OFFSET, TACTBAL_LENGTH,
					BigDecimal.valueOf(toActualBal));

			commArea[SUCCESS_OFFSET] = (byte) 'Y';
		}
		catch (Exception e)
		{
			logger.log(Level.SEVERE, () -> "Transfer failed: " + e.toString());
			commArea[FAIL_CODE_OFFSET] = (byte) FAIL_UNEXPECTED;
			commArea[SUCCESS_OFFSET] = (byte) 'N';
			rollback();
		}
		finally
		{
			HBankDataAccess hBankDataAccess = new HBankDataAccess();
			hBankDataAccess.terminate();
		}

		logger.exiting(CLASS_NAME, "main");
	}


	private static void rollback()
	{
		try
		{
			Task.getTask().rollback();
		}
		catch (InvalidRequestException e)
		{
			logger.log(Level.SEVERE,
					() -> "Rollback failed: " + e.toString());
		}
	}


	private static String getSortCode()
	{
		com.ibm.cics.cip.bankliberty.api.json.SortCodeResource sortCodeResource = new com.ibm.cics.cip.bankliberty.api.json.SortCodeResource();
		jakarta.ws.rs.core.Response sortCodeResponse = sortCodeResource
				.getSortCode();
		String responseEntity = (String) sortCodeResponse.getEntity();
		return responseEntity.substring(13, 19);
	}


	static String extractField(byte[] data, int offset, int length)
	{
		return new String(data, offset, length,
				java.nio.charset.StandardCharsets.UTF_8).trim();
	}


	static BigDecimal extractAmount(byte[] data, int offset, int length)
	{
		String raw = extractField(data, offset, length);
		if (raw.isEmpty())
		{
			return BigDecimal.ZERO;
		}
		try
		{
			return new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP);
		}
		catch (NumberFormatException e)
		{
			return BigDecimal.ZERO;
		}
	}


	static void writeField(byte[] data, int offset, int length, String value)
	{
		byte[] padded = new byte[length];
		java.util.Arrays.fill(padded, (byte) '0');
		byte[] valueBytes = value
				.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		int start = length - valueBytes.length;
		if (start < 0)
		{
			start = 0;
		}
		int copyLen = Math.min(valueBytes.length, length);
		System.arraycopy(valueBytes, 0, padded, start, copyLen);
		System.arraycopy(padded, 0, data, offset, length);
	}


	static void writeAmount(byte[] data, int offset, int length,
			BigDecimal value)
	{
		String formatted = value.setScale(2, RoundingMode.HALF_UP)
				.toPlainString();
		byte[] padded = new byte[length];
		java.util.Arrays.fill(padded, (byte) ' ');
		byte[] valueBytes = formatted
				.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		int start = length - valueBytes.length;
		if (start < 0)
		{
			start = 0;
		}
		int copyLen = Math.min(valueBytes.length, length);
		System.arraycopy(valueBytes, 0, padded, start, copyLen);
		System.arraycopy(padded, 0, data, offset, length);
	}
}
