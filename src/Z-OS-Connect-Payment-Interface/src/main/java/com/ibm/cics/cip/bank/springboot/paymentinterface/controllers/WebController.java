/*
 *
 *    Copyright IBM Corp. 2023
 *
 *
 */
package com.ibm.cics.cip.bank.springboot.paymentinterface.controllers;

import java.util.HashMap;
import java.util.Map;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cics.cip.bank.springboot.paymentinterface.ConnectionInfo;
import com.ibm.cics.cip.bank.springboot.paymentinterface.jsonclasses.paymentinterface.PaymentInterfaceJson;
import com.ibm.cics.cip.bank.springboot.paymentinterface.jsonclasses.paymentinterface.TransferForm;

@RestController
@CrossOrigin
public class WebController
{

	static final String COPYRIGHT = "Copyright IBM Corp. 2022";

	private static final String CONTENT_TYPE = "content-type";

	private static final String APPLICATION_JSON = "application/json";

	private static final Logger log = LoggerFactory
			.getLogger(WebController.class);


	private Map<String, Object> buildResponse(boolean success,
			String largeText, String smallText)
	{
		Map<String, Object> response = new HashMap<>();
		response.put("success", success);
		response.put("largeText", largeText);
		response.put("smallText", smallText);
		return response;
	}


	@PostMapping("/paydbcr")
	public ResponseEntity<Map<String, Object>> checkPersonInfo(
			@Valid TransferForm transferForm,
			BindingResult bindingResult) throws JsonProcessingException
	{
		if (bindingResult.hasErrors())
		{
			return ResponseEntity.badRequest()
					.body(buildResponse(false, "Validation Error",
							"Please check the form fields."));
		}

		PaymentInterfaceJson transferjson = new PaymentInterfaceJson(
				transferForm);

		log.info("{}", transferjson);
		String jsonString = new ObjectMapper().writeValueAsString(transferjson);
		log.info(jsonString);

		WebClient client = WebClient
				.create(ConnectionInfo.getAddressAndPort()
						+ "/makepayment/dbcr");

		try
		{
			ResponseSpec response = client.put()
					.header(CONTENT_TYPE, APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.body(BodyInserters.fromValue(jsonString)).retrieve();
			String responseBody = response.bodyToMono(String.class).block();
			log.info(responseBody);

			PaymentInterfaceJson responseObj = new ObjectMapper()
					.readValue(responseBody, PaymentInterfaceJson.class);
			log.info("{}", responseObj);

			checkIfResponseValid(responseObj);

			return ResponseEntity.ok(buildResponse(true,
					"Payment Successful",
					responseObj.toPrettyString()));
		}
		catch (InsufficientFundsException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, "Payment Rejected", e.getMessage()));
		}
		catch (InvalidAccountTypeException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, "Payment Rejected", e.getMessage()));
		}
		catch (ItemNotFoundException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, "Request Error", e.getMessage()));
		}
		catch (WebClientRequestException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, "Connection Error",
							"Connection refused or failed to resolve; Are you using the right address and port? Is the server running?"));
		}
		catch (Exception e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, "Request Error",
							"There was an error processing the request; Please try again later or check logs for more info."));
		}
	}


	public static void checkIfResponseValid(PaymentInterfaceJson responseObj)
			throws InsufficientFundsException, InvalidAccountTypeException,
			ItemNotFoundException
	{
		switch (responseObj.getDbcr().getCommSuccessCode())
		{
		case "N":
			switch (responseObj.getDbcr().getCommFailCode())
			{
			case "1":
				throw new ItemNotFoundException("account");
			case "2":
				throw new InsufficientFundsException();
			case "3":
				throw new InvalidAccountTypeException();
			default:
				break;
			}
			break;
		default:
			break;
		}
	}
}

class InsufficientFundsException extends Exception
{
	private static final long serialVersionUID = 2916294528612553278L;
	static final String COPYRIGHT = "Copyright IBM Corp. 2022";

	public InsufficientFundsException()
	{
		super("Payment rejected: Insufficient funds.");
	}
}

class InvalidAccountTypeException extends Exception
{
	private static final long serialVersionUID = -3342099995389507130L;
	static final String COPYRIGHT = "Copyright IBM Corp. 2022";

	public InvalidAccountTypeException()
	{
		super("Payment rejected: Invalid account type.");
	}
}

class ItemNotFoundException extends Exception
{
	private static final long serialVersionUID = -3570840021629249034L;
	static final String COPYRIGHT = "Copyright IBM Corp. 2022";

	public ItemNotFoundException(String item)
	{
		super("The " + item
				+ " you searched for could not be found; Try a different "
				+ item + " number.");
	}
}
