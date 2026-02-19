/*
 *
 *    Copyright IBM Corp. 2023
 *
 */

package com.ibm.cics.cip.bankliberty.datainterfaces;

import com.ibm.jzos.fields.*;
import java.math.*;

public class XFRFUNCommarea
{

	protected static CobolDatatypeFactory factory = new CobolDatatypeFactory();
	static
	{
		factory.setStringTrimDefault(false);
	}

	public static final int DFHCOMMAREA_LEN = 90;

	protected static final ExternalDecimalAsIntField COMM_FACCNO = factory
			.getExternalDecimalAsIntField(8, false, false, false, false);

	protected static final ExternalDecimalAsIntField COMM_FSCODE = factory
			.getExternalDecimalAsIntField(6, false, false, false, false);

	protected static final ExternalDecimalAsIntField COMM_TACCNO = factory
			.getExternalDecimalAsIntField(8, false, false, false, false);

	protected static final ExternalDecimalAsIntField COMM_TSCODE = factory
			.getExternalDecimalAsIntField(6, false, false, false, false);

	protected static final ExternalDecimalAsBigDecimalField COMM_AMT = factory
			.getExternalDecimalAsBigDecimalField(12, 2, true, false, false,
					false);

	protected static final ExternalDecimalAsBigDecimalField COMM_FAVBAL = factory
			.getExternalDecimalAsBigDecimalField(12, 2, true, false, false,
					false);

	protected static final ExternalDecimalAsBigDecimalField COMM_FACTBAL = factory
			.getExternalDecimalAsBigDecimalField(12, 2, true, false, false,
					false);

	protected static final ExternalDecimalAsBigDecimalField COMM_TAVBAL = factory
			.getExternalDecimalAsBigDecimalField(12, 2, true, false, false,
					false);

	protected static final ExternalDecimalAsBigDecimalField COMM_TACTBAL = factory
			.getExternalDecimalAsBigDecimalField(12, 2, true, false, false,
					false);

	protected static final StringField COMM_FAIL_CODE = factory
			.getStringField(1);

	protected static final StringField COMM_SUCCESS = factory
			.getStringField(1);

	protected byte[] byteBuffer;


	public XFRFUNCommarea(byte[] buffer)
	{
		this.byteBuffer = buffer;
	}


	public XFRFUNCommarea()
	{
		this.byteBuffer = new byte[DFHCOMMAREA_LEN];
	}


	public byte[] getByteBuffer()
	{
		return byteBuffer;
	}


	public int getCommFaccno()
	{
		return COMM_FACCNO.getInt(byteBuffer);
	}


	public void setCommFaccno(int value)
	{
		COMM_FACCNO.putInt(value, byteBuffer);
	}


	public int getCommFscode()
	{
		return COMM_FSCODE.getInt(byteBuffer);
	}


	public void setCommFscode(int value)
	{
		COMM_FSCODE.putInt(value, byteBuffer);
	}


	public int getCommTaccno()
	{
		return COMM_TACCNO.getInt(byteBuffer);
	}


	public void setCommTaccno(int value)
	{
		COMM_TACCNO.putInt(value, byteBuffer);
	}


	public int getCommTscode()
	{
		return COMM_TSCODE.getInt(byteBuffer);
	}


	public void setCommTscode(int value)
	{
		COMM_TSCODE.putInt(value, byteBuffer);
	}


	public BigDecimal getCommAmt()
	{
		return COMM_AMT.getBigDecimal(byteBuffer);
	}


	public void setCommAmt(BigDecimal value)
	{
		COMM_AMT.putBigDecimal(value, byteBuffer);
	}


	public BigDecimal getCommFavbal()
	{
		return COMM_FAVBAL.getBigDecimal(byteBuffer);
	}


	public void setCommFavbal(BigDecimal value)
	{
		COMM_FAVBAL.putBigDecimal(value, byteBuffer);
	}


	public BigDecimal getCommFactbal()
	{
		return COMM_FACTBAL.getBigDecimal(byteBuffer);
	}


	public void setCommFactbal(BigDecimal value)
	{
		COMM_FACTBAL.putBigDecimal(value, byteBuffer);
	}


	public BigDecimal getCommTavbal()
	{
		return COMM_TAVBAL.getBigDecimal(byteBuffer);
	}


	public void setCommTavbal(BigDecimal value)
	{
		COMM_TAVBAL.putBigDecimal(value, byteBuffer);
	}


	public BigDecimal getCommTactbal()
	{
		return COMM_TACTBAL.getBigDecimal(byteBuffer);
	}


	public void setCommTactbal(BigDecimal value)
	{
		COMM_TACTBAL.putBigDecimal(value, byteBuffer);
	}


	public String getCommFailCode()
	{
		return COMM_FAIL_CODE.getString(byteBuffer);
	}


	public void setCommFailCode(String value)
	{
		COMM_FAIL_CODE.putString(value, byteBuffer);
	}


	public String getCommSuccess()
	{
		return COMM_SUCCESS.getString(byteBuffer);
	}


	public void setCommSuccess(String value)
	{
		COMM_SUCCESS.putString(value, byteBuffer);
	}
}
