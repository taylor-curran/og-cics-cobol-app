/*
 *
 *    Copyright IBM Corp. 2023
 *
 */

package com.ibm.cics.cip.bankliberty.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.cics.server.CicsConditionException;
import com.ibm.cics.server.CommAreaHolder;
import com.ibm.cics.server.Program;

public class BNK1TFN
{

	private static Logger logger = Logger
			.getLogger("com.ibm.cics.cip.bankliberty.transaction");

	private static final String CLASS_NAME = "BNK1TFN";

	private static final int COMMAREA_FACCNO_OFFSET = 0;

	private static final int COMMAREA_FACCNO_LENGTH = 8;

	private static final int COMMAREA_TACCNO_OFFSET = 8;

	private static final int COMMAREA_TACCNO_LENGTH = 8;

	private static final int COMMAREA_AMT_OFFSET = 16;

	private static final int COMMAREA_AMT_LENGTH = 12;

	private static final int COMMAREA_LENGTH = 28;

	private static final String ZERO_ACCOUNT = "00000000";

	private static final String MSG_ENTER_FROM = "Please enter a FROM account no  ";

	private static final String MSG_ENTER_TO = "Please enter a TO account no    ";

	private static final String MSG_SAME_ACCOUNTS = "The FROM & TO account should be different ";

	private static final String MSG_ZERO_ACCOUNT = "Account no 00000000 is not valid          ";

	private static final String MSG_AMOUNT_NUMERIC = "The Amount entered must be numeric.";

	private static final String MSG_POSITIVE_AMOUNT = "Please supply a positive amount.";

	private static final String MSG_NONZERO_AMOUNT = "Please supply a non-zero amount.";

	private static final String MSG_EMBEDDED_SPACES = "Please supply a numeric amount without embedded  spaces.";

	private static final String MSG_NUMERIC_AMOUNT = "Please supply a numeric amount.";

	private static final String MSG_ONE_DECIMAL = "Use one decimal point for amount only.";

	private static final String MSG_TWO_DECIMALS = "Only up to two decimal places are supported.";

	private static final String MSG_FROM_NOT_FOUND = "Sorry the FROM ACCOUNT no was not found. Transfer not applied. ";

	private static final String MSG_TO_NOT_FOUND = "Sorry the TO ACCOUNT no was not found. Transfer not applied. ";

	private static final String MSG_UNEXPECTED_ERROR = "Sorry but the transfer could not be applied due to an unexpected error.";

	private static final String MSG_ZERO_AMOUNT_ERROR = "Please supply an amount greater than zero.";

	private static final String MSG_UNKNOWN_ERROR = "Sorry but the transfer could not be applied due to an error.";

	private static final String MSG_UNDETERMINED = "Sorry but the transfer could not be applied unable to determine success.";

	private static final String MSG_SUCCESS = "Transfer successfully applied.             ";


	public static void main(CommAreaHolder cah)
	{
		logger.entering(CLASS_NAME, "main");

		byte[] commArea = cah.getValue();
		if (commArea == null || commArea.length == 0)
		{
			logger.log(Level.INFO, () -> "First entry - sending empty map");
			logger.exiting(CLASS_NAME, "main");
			return;
		}

		String fromAccount = XFRFUN.extractField(commArea,
				COMMAREA_FACCNO_OFFSET, COMMAREA_FACCNO_LENGTH);
		String toAccount = XFRFUN.extractField(commArea,
				COMMAREA_TACCNO_OFFSET, COMMAREA_TACCNO_LENGTH);
		String amountStr = XFRFUN.extractField(commArea, COMMAREA_AMT_OFFSET,
				COMMAREA_AMT_LENGTH);

		String validationError = validateInputs(fromAccount, toAccount,
				amountStr);
		if (validationError != null)
		{
			logger.log(Level.WARNING, () -> validationError);
			logger.exiting(CLASS_NAME, "main");
			return;
		}

		BigDecimal amount = parseAmount(amountStr);
		if (amount == null)
		{
			logger.log(Level.WARNING, () -> MSG_AMOUNT_NUMERIC);
			logger.exiting(CLASS_NAME, "main");
			return;
		}

		byte[] subpgmParms = new byte[XFRFUN.COMMAREA_LENGTH];
		Arrays.fill(subpgmParms, (byte) '0');

		XFRFUN.writeField(subpgmParms, 0, 8, fromAccount);
		XFRFUN.writeField(subpgmParms, 14, 8, toAccount);
		XFRFUN.writeAmount(subpgmParms, 28, 12, amount);
		subpgmParms[89] = (byte) 'N';

		try
		{
			Program xfrfun = new Program();
			xfrfun.setName("XFRFUN");
			xfrfun.setSyncOnReturn(true);
			xfrfun.link(subpgmParms);
		}
		catch (CicsConditionException e)
		{
			logger.log(Level.SEVERE,
					() -> "LINK to XFRFUN failed: " + e.toString());
			logger.exiting(CLASS_NAME, "main");
			return;
		}

		char success = (char) subpgmParms[89];
		if (success == 'N')
		{
			char failCode = (char) subpgmParms[88];
			String errorMsg = getErrorMessage(failCode);
			logger.log(Level.WARNING, () -> errorMsg);
		}
		else if (success == 'Y')
		{
			String fromSortCode = XFRFUN.extractField(subpgmParms, 8, 6);
			String toSortCode = XFRFUN.extractField(subpgmParms, 22, 6);
			BigDecimal fromAvailBal = XFRFUN.extractAmount(subpgmParms, 40,
					12);
			BigDecimal fromActualBal = XFRFUN.extractAmount(subpgmParms, 52,
					12);
			BigDecimal toAvailBal = XFRFUN.extractAmount(subpgmParms, 64, 12);
			BigDecimal toActualBal = XFRFUN.extractAmount(subpgmParms, 76, 12);

			logger.log(Level.INFO, () -> MSG_SUCCESS);
			logger.log(Level.INFO,
					() -> "FROM: " + fromAccount + " SortCode: "
							+ fromSortCode + " ActualBal: " + fromActualBal
							+ " AvailBal: " + fromAvailBal);
			logger.log(Level.INFO,
					() -> "TO: " + toAccount + " SortCode: " + toSortCode
							+ " ActualBal: " + toActualBal + " AvailBal: "
							+ toAvailBal);
		}
		else
		{
			logger.log(Level.WARNING, () -> MSG_UNDETERMINED);
		}

		logger.exiting(CLASS_NAME, "main");
	}


	static String validateInputs(String fromAccount, String toAccount,
			String amountStr)
	{
		if (!isNumeric(fromAccount))
		{
			return MSG_ENTER_FROM;
		}

		if (!isNumeric(toAccount))
		{
			return MSG_ENTER_TO;
		}

		if (fromAccount.equals(toAccount))
		{
			return MSG_SAME_ACCOUNTS;
		}

		if (ZERO_ACCOUNT.equals(fromAccount)
				|| ZERO_ACCOUNT.equals(toAccount))
		{
			return MSG_ZERO_ACCOUNT;
		}

		return validateAmount(amountStr);
	}


	static String validateAmount(String amountStr)
	{
		if (amountStr == null || amountStr.isEmpty())
		{
			return MSG_AMOUNT_NUMERIC;
		}

		String trimmed = amountStr.trim();
		if (trimmed.isEmpty())
		{
			return MSG_AMOUNT_NUMERIC;
		}

		if (isNumeric(trimmed))
		{
			BigDecimal val = new BigDecimal(trimmed);
			if (val.compareTo(BigDecimal.ZERO) <= 0)
			{
				return MSG_POSITIVE_AMOUNT;
			}
			return null;
		}

		if (trimmed.contains("-"))
		{
			return MSG_POSITIVE_AMOUNT;
		}

		if (trimmed.contains(" "))
		{
			return MSG_EMBEDDED_SPACES;
		}

		for (int i = 0; i < trimmed.length(); i++)
		{
			char c = trimmed.charAt(i);
			if (!Character.isDigit(c) && c != '.')
			{
				return MSG_NUMERIC_AMOUNT;
			}
		}

		long dotCount = trimmed.chars().filter(c -> c == '.').count();
		if (dotCount > 1)
		{
			return MSG_ONE_DECIMAL;
		}

		if (dotCount == 1)
		{
			int dotIndex = trimmed.indexOf('.');
			String afterDot = trimmed.substring(dotIndex + 1);
			if (afterDot.length() > 2)
			{
				String significantDecimals = afterDot.replaceAll("0+$", "");
				if (significantDecimals.length() > 2)
				{
					return MSG_TWO_DECIMALS;
				}
			}
		}

		BigDecimal parsed;
		try
		{
			parsed = new BigDecimal(trimmed);
		}
		catch (NumberFormatException e)
		{
			return MSG_NUMERIC_AMOUNT;
		}

		if (parsed.compareTo(BigDecimal.ZERO) == 0)
		{
			return MSG_NONZERO_AMOUNT;
		}

		return null;
	}


	static BigDecimal parseAmount(String amountStr)
	{
		if (amountStr == null || amountStr.trim().isEmpty())
		{
			return null;
		}
		try
		{
			return new BigDecimal(amountStr.trim())
					.setScale(2, RoundingMode.HALF_UP);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}


	private static String getErrorMessage(char failCode)
	{
		switch (failCode)
		{
		case '1':
			return MSG_FROM_NOT_FOUND;
		case '2':
			return MSG_TO_NOT_FOUND;
		case '3':
			return MSG_UNEXPECTED_ERROR;
		case '4':
			return MSG_ZERO_AMOUNT_ERROR;
		default:
			return MSG_UNKNOWN_ERROR;
		}
	}


	private static boolean isNumeric(String str)
	{
		if (str == null || str.isEmpty())
		{
			return false;
		}
		for (int i = 0; i < str.length(); i++)
		{
			if (!Character.isDigit(str.charAt(i)))
			{
				return false;
			}
		}
		return true;
	}
}
