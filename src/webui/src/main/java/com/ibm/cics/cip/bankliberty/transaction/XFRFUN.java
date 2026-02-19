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

import com.ibm.cics.server.CommAreaHolder;
import com.ibm.cics.server.InvalidRequestException;
import com.ibm.cics.server.Task;
import com.ibm.cics.cip.bankliberty.api.json.HBankDataAccess;
import com.ibm.cics.cip.bankliberty.datainterfaces.XFRFUNCommarea;
import com.ibm.cics.cip.bankliberty.web.db2.Account;
import com.ibm.cics.cip.bankliberty.web.db2.ProcessedTransaction;

public class XFRFUN
{

	private static Logger logger = Logger
			.getLogger("com.ibm.cics.cip.bankliberty.transaction");

	private static final String CLASS_NAME = "XFRFUN";

	private static final String FAIL_FROM_NOT_FOUND = "1";

	private static final String FAIL_TO_NOT_FOUND = "2";

	private static final String FAIL_UNEXPECTED = "3";

	private static final String FAIL_ZERO_AMOUNT = "4";


	public static void main(CommAreaHolder cah)
	{
		logger.entering(CLASS_NAME, "main");

		byte[] commArea = cah.getValue();
		if (commArea == null
				|| commArea.length < XFRFUNCommarea.DFHCOMMAREA_LEN)
		{
			logger.severe("Invalid commarea");
			return;
		}

		XFRFUNCommarea commData = new XFRFUNCommarea(commArea);

		String fromAccNo = String.format("%08d", commData.getCommFaccno());
		String toAccNo = String.format("%08d", commData.getCommTaccno());
		BigDecimal amount = commData.getCommAmt();

		String sortCode = getSortCode();
		int sortCodeInt = Integer.parseInt(sortCode);

		commData.setCommFscode(sortCodeInt);
		commData.setCommTscode(sortCodeInt);

		if (amount.compareTo(BigDecimal.ZERO) <= 0)
		{
			commData.setCommFailCode(FAIL_ZERO_AMOUNT);
			commData.setCommSuccess("N");
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
				commData.setCommFailCode(FAIL_FROM_NOT_FOUND);
				commData.setCommSuccess("N");
				rollback();
				logger.exiting(CLASS_NAME, "main");
				return;
			}

			BigDecimal fromAvailBal = BigDecimal
					.valueOf(fromAccount.getAvailableBalance())
					.setScale(2, RoundingMode.HALF_UP);
			BigDecimal fromActualBal = BigDecimal
					.valueOf(fromAccount.getActualBalance())
					.setScale(2, RoundingMode.HALF_UP);

			Account toAccount = new Account();
			toAccount.setAccountNumber(toAccNo);
			toAccount.setSortcode(sortCode);

			if (!toAccount.debitCredit(amount))
			{
				commData.setCommFailCode(FAIL_TO_NOT_FOUND);
				commData.setCommSuccess("N");
				rollback();
				logger.exiting(CLASS_NAME, "main");
				return;
			}

			BigDecimal toAvailBal = BigDecimal
					.valueOf(toAccount.getAvailableBalance())
					.setScale(2, RoundingMode.HALF_UP);
			BigDecimal toActualBal = BigDecimal
					.valueOf(toAccount.getActualBalance())
					.setScale(2, RoundingMode.HALF_UP);

			ProcessedTransaction proctran = new ProcessedTransaction();
			if (!proctran.writeTransferLocal(sortCode, fromAccNo, amount,
					toAccNo))
			{
				commData.setCommFailCode(FAIL_UNEXPECTED);
				commData.setCommSuccess("N");
				rollback();
				logger.exiting(CLASS_NAME, "main");
				return;
			}

			commData.setCommFavbal(fromAvailBal);
			commData.setCommFactbal(fromActualBal);
			commData.setCommTavbal(toAvailBal);
			commData.setCommTactbal(toActualBal);
			commData.setCommSuccess("Y");
		}
		catch (Exception e)
		{
			logger.log(Level.SEVERE, () -> "Transfer failed: " + e.toString());
			commData.setCommFailCode(FAIL_UNEXPECTED);
			commData.setCommSuccess("N");
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
}
