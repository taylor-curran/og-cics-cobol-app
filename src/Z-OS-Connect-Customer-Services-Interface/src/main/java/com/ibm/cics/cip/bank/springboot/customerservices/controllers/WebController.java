/*
 *
 *    Copyright IBM Corp. 2023
 *
 *
 */
package com.ibm.cics.cip.bank.springboot.customerservices.controllers;

import java.util.HashMap;
import java.util.Map;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.MapperFeature;
import com.ibm.cics.cip.bank.springboot.customerservices.ConnectionInfo;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.accountenquiry.AccountEnquiryForm;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.accountenquiry.AccountEnquiryJson;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.createaccount.AccountType;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.createaccount.CreateAccountForm;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.createaccount.CreateAccountJson;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.createcustomer.CreateCustomerForm;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.createcustomer.CreateCustomerJson;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.customerenquiry.CustomerEnquiryForm;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.customerenquiry.CustomerEnquiryJson;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.deleteaccount.DeleteAccountJson;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.deletecustomer.DeleteCustomerJson;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.listaccounts.ListAccJson;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.updateaccount.UpdateAccountForm;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.updateaccount.UpdateAccountJson;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.updatecustomer.UpdateCustomerForm;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.updatecustomer.UpdateCustomerJson;

@RestController
@CrossOrigin
public class WebController
{

	static final String COPYRIGHT = "Copyright IBM Corp. 2022";

	private static final String REQUEST_ERROR = "Request Error";

	private static final String CONNECTION_ERROR_MSG = "Connection refused or failed to resolve; Are you using the right address and port? Is the server running?";

	private static final String CONNECTION_ERROR = "Connection Error";

	private static final String ERROR_MSG = "There was an error processing the request; Please try again later or check logs for more info.";

	private static final String ACCOUNT = "account";

	private static final String CUSTOMER = "customer";

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


	// Customer and account services screen
	@GetMapping(value =
	{ "", "/services", "/" })
	public ResponseEntity<Map<String, Object>> showCustServices()
	{
		Map<String, Object> response = new HashMap<>();
		response.put("contextPath", "");
		return ResponseEntity.ok(response);
	}


	// 1. Enquire account
	@PostMapping("/enqacct")
	public ResponseEntity<Map<String, Object>> returnAcct(
			@Valid AccountEnquiryForm accountEnquiryForm,
			BindingResult bindingResult) throws JsonProcessingException
	{
		if (bindingResult.hasErrors())
		{
			return ResponseEntity.badRequest()
					.body(buildResponse(false, "Validation Error",
							"Please check the form fields."));
		}

		WebClient client = WebClient
				.create(ConnectionInfo.getAddressAndPort() + "/inqaccz/enquiry/"
						+ accountEnquiryForm.getAcctNumber());

		try
		{
			ResponseSpec response = client.get().retrieve();
			String responseBody = response.bodyToMono(String.class).block();
			log.info(responseBody);

			ObjectMapper myObjectMapper = new ObjectMapper();
			myObjectMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);

			AccountEnquiryJson responseObj = myObjectMapper
					.readValue(responseBody, AccountEnquiryJson.class);
			log.info("{}", responseObj);

			checkIfResponseValidListAcc(responseObj);

			return ResponseEntity.ok(buildResponse(true, "Account Details:",
					responseObj.toPrettyString()));
		}
		catch (ItemNotFoundException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, REQUEST_ERROR, e.getMessage()));
		}
		catch (WebClientRequestException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, REQUEST_ERROR, CONNECTION_ERROR_MSG));
		}
		catch (Exception e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, REQUEST_ERROR, ERROR_MSG));
		}
	}


	public static void checkIfResponseValidListAcc(AccountEnquiryJson response)
			throws ItemNotFoundException
	{
		if (response.getInqaccCommarea().getInaccSuccess().equals("N")
				&& response.getInqaccCommarea().getInqaccCustno() == 0)
		{
			throw new ItemNotFoundException(ACCOUNT);
		}
	}


	// 2. Enquire Customer
	@PostMapping("/enqcust")
	public ResponseEntity<Map<String, Object>> returnCust(
			@Valid CustomerEnquiryForm customerEnquiryForm,
			BindingResult bindingResult) throws JsonProcessingException
	{
		if (bindingResult.hasErrors())
		{
			return ResponseEntity.badRequest()
					.body(buildResponse(false, "Validation Error",
							"Please check the form fields."));
		}

		WebClient client = WebClient
				.create(ConnectionInfo.getAddressAndPort()
						+ "/inqcustz/enquiry/"
						+ customerEnquiryForm.getCustNumber());

		try
		{
			ResponseSpec response = client.get().retrieve();
			String responseBody = response.bodyToMono(String.class).block();

			CustomerEnquiryJson responseObj = new ObjectMapper()
					.readValue(responseBody, CustomerEnquiryJson.class);
			checkIfResponseValidEnqCust(responseObj);
			return ResponseEntity.ok(buildResponse(true, "Customer Details",
					responseObj.toPrettyString()));
		}
		catch (ItemNotFoundException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, REQUEST_ERROR, e.getMessage()));
		}
		catch (WebClientRequestException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, REQUEST_ERROR, CONNECTION_ERROR_MSG));
		}
		catch (Exception e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, REQUEST_ERROR, ERROR_MSG));
		}
	}


	public static void checkIfResponseValidEnqCust(CustomerEnquiryJson response)
			throws ItemNotFoundException
	{
		if (response.getInqCustZ().getInqcustInqSuccess().equals("N"))
		{
			throw new ItemNotFoundException(CUSTOMER);
		}
	}


	// 3. List all accounts belonging to a customer
	@PostMapping("/listacc")
	public ResponseEntity<Map<String, Object>> returnListAcc(
			@Valid CustomerEnquiryForm customerEnquiryForm,
			BindingResult bindingResult) throws JsonProcessingException
	{
		if (bindingResult.hasErrors())
		{
			return ResponseEntity.badRequest()
					.body(buildResponse(false, "Validation Error",
							"Please check the form fields."));
		}

		WebClient client = WebClient.create(
				ConnectionInfo.getAddressAndPort() + "/inqacccz/list/"
						+ customerEnquiryForm.getCustNumber());

		try
		{
			ResponseSpec response = client.get().retrieve();
			String responseBody = response.bodyToMono(String.class).block();
			log.info(responseBody);
			ListAccJson responseObj = new ObjectMapper()
					.readValue(responseBody, ListAccJson.class);
			log.info("{}", responseObj);
			checkIfResponseValidListAcc(responseObj);

			Map<String, Object> result = buildResponse(true,
					"Accounts belonging to customer "
							+ responseObj.getInqacccz().getCustomerNumber()
							+ ":",
					null);
			result.put("accounts",
					responseObj.getInqacccz().getAccountDetails());
			return ResponseEntity.ok(result);
		}
		catch (ItemNotFoundException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, REQUEST_ERROR, e.getMessage()));
		}
		catch (WebClientRequestException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, REQUEST_ERROR, CONNECTION_ERROR_MSG));
		}
		catch (Exception e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, REQUEST_ERROR, ERROR_MSG));
		}
	}


	public static void checkIfResponseValidListAcc(ListAccJson response)
			throws ItemNotFoundException
	{
		if (response.getInqacccz().getCustomerFound().equals("N"))
		{
			throw new ItemNotFoundException(CUSTOMER);
		}
	}


	// 4. Create an account
	@PostMapping("/createacc")
	public ResponseEntity<Map<String, Object>> processCreateAcc(
			@Valid CreateAccountForm createAccForm,
			BindingResult bindingResult) throws JsonProcessingException
	{
		if (bindingResult.hasErrors())
		{
			return ResponseEntity.badRequest()
					.body(buildResponse(false, "Validation Error",
							"Please check the form fields."));
		}

		CreateAccountJson transferjson = new CreateAccountJson(createAccForm);
		log.info("{}", transferjson);
		String jsonString = new ObjectMapper().writeValueAsString(transferjson);
		log.info(jsonString);

		WebClient client = WebClient
				.create(ConnectionInfo.getAddressAndPort() + "/creacc/insert");

		try
		{
			ResponseSpec response = client.post()
					.header(CONTENT_TYPE, APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.body(BodyInserters.fromValue(jsonString)).retrieve();
			String responseBody = response.bodyToMono(String.class).block();
			log.info(responseBody);

			CreateAccountJson responseObj = new ObjectMapper()
					.readValue(responseBody, CreateAccountJson.class);
			log.info("{}", responseObj);
			checkIfResponseValidCreateAcc(responseObj);

			return ResponseEntity.ok(buildResponse(true,
					"Account creation successful",
					"Details: " + responseObj.toPrettyString()));
		}
		catch (TooManyAccountsException | ItemNotFoundException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, "Account Error", e.getMessage()));
		}
		catch (WebClientRequestException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(buildResponse(false, CONNECTION_ERROR,
					CONNECTION_ERROR_MSG));
		}
		catch (Exception e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, REQUEST_ERROR, ERROR_MSG));
		}
	}


	public static void checkIfResponseValidCreateAcc(
			CreateAccountJson responseObj)
			throws TooManyAccountsException, ItemNotFoundException
	{
		if (responseObj.getCreAcc().getCommSuccess().equals("N"))
		{
			if (responseObj.getCreAcc().getCommFailCode().equals("1"))
			{
				throw new ItemNotFoundException(CUSTOMER);
			}
			if (responseObj.getCreAcc().getCommFailCode().equals("8"))
			{
				throw new TooManyAccountsException(Integer
						.parseInt(responseObj.getCreAcc().getCommCustno()));
			}
			if (responseObj.getCreAcc().getCommFailCode().equals("A"))
			{
				throw new IllegalArgumentException(
						"Invalid account type supplied.");
			}
		}
	}


	// 5. Create a customer
	@PostMapping("/createcust")
	public ResponseEntity<Map<String, Object>> processCreateCust(
			@Valid CreateCustomerForm createCustForm,
			BindingResult bindingResult) throws JsonProcessingException
	{
		if (bindingResult.hasErrors())
		{
			return ResponseEntity.badRequest()
					.body(buildResponse(false, "Validation Error",
							"Please check the form fields."));
		}

		CreateCustomerJson transferjson = new CreateCustomerJson(createCustForm);
		log.info("{}", transferjson);
		String jsonString = new ObjectMapper().writeValueAsString(transferjson);
		log.info("Json to be sent:\n{}", jsonString);

		WebClient client = WebClient
				.create(ConnectionInfo.getAddressAndPort() + "/crecust/insert");

		try
		{
			ResponseSpec response = client.post()
					.header(CONTENT_TYPE, APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.body(BodyInserters.fromValue(jsonString)).retrieve();
			String responseBody = response.bodyToMono(String.class).block();
			log.info("Response Body: \n{}", responseBody);

			CreateCustomerJson responseObj = new ObjectMapper()
					.readValue(responseBody, CreateCustomerJson.class);
			log.info("Response Json:\n{}", responseObj);
			checkIfResponseValidCreateCust(responseObj);

			return ResponseEntity.ok(buildResponse(true,
					"Customer creation successful",
					responseObj.toPrettyString()));
		}
		catch (WebClientRequestException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(buildResponse(false, CONNECTION_ERROR,
					CONNECTION_ERROR_MSG));
		}
		catch (Exception e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, REQUEST_ERROR, ERROR_MSG));
		}
	}


	public static void checkIfResponseValidCreateCust(
			CreateCustomerJson responseObj) throws InvalidCustomerException,
			NumberFormatException, TooManyAccountsException
	{
		if (!responseObj.getCreCust().getCommFailCode().equals(""))
		{
			if (responseObj.getCreCust().getCommFailCode().equals("8"))
			{
				throw new TooManyAccountsException(Integer
						.parseInt(responseObj.getCreCust().getCommFailCode()));
			}
			throw new InvalidCustomerException("An unexpected error occured");
		}
	}


	// 6. Update an account
	@PostMapping("/updateacc")
	public ResponseEntity<Map<String, Object>> processUpdateAcc(
			@Valid UpdateAccountForm updateAccountForm,
			BindingResult bindingResult) throws JsonProcessingException
	{
		if (bindingResult.hasErrors())
		{
			return ResponseEntity.badRequest()
					.body(buildResponse(false, "Validation Error",
							"Please check the form fields."));
		}

		UpdateAccountJson transferjson = new UpdateAccountJson(updateAccountForm);
		log.info("{}", transferjson);
		String jsonString = new ObjectMapper().writeValueAsString(transferjson);
		log.info("{}", jsonString);

		WebClient client = WebClient
				.create(ConnectionInfo.getAddressAndPort() + "/updacc/update");

		try
		{
			ResponseSpec response = client.put()
					.header(CONTENT_TYPE, APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.body(BodyInserters.fromValue(jsonString)).retrieve();
			String responseBody = response.bodyToMono(String.class).block();
			log.info(responseBody);

			UpdateAccountJson responseObj = new ObjectMapper()
					.readValue(responseBody, UpdateAccountJson.class);
			log.info("{}", responseObj);
			checkIfResponseValidUpdateAcc(responseObj);

			return ResponseEntity.ok(buildResponse(true,
					"Account updated", responseObj.toPrettyString()));
		}
		catch (ItemNotFoundException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, "Update Error", e.getMessage()));
		}
		catch (WebClientRequestException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(buildResponse(false, CONNECTION_ERROR,
					CONNECTION_ERROR_MSG));
		}
		catch (Exception e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, REQUEST_ERROR, ERROR_MSG));
		}
	}


	public static void checkIfResponseValidUpdateAcc(
			UpdateAccountJson responseObj) throws ItemNotFoundException
	{
		if (responseObj.getUpdacc().getCommSuccess().equals("N"))
		{
			throw new ItemNotFoundException(ACCOUNT);
		}
	}


	// 7. Update a customer
	@PostMapping("/updatecust")
	public ResponseEntity<Map<String, Object>> processUpdateCust(
			@Valid UpdateCustomerForm updateCustomerForm,
			BindingResult bindingResult) throws JsonProcessingException
	{
		if (bindingResult.hasErrors())
		{
			return ResponseEntity.badRequest()
					.body(buildResponse(false, "Validation Error",
							"Please check the form fields."));
		}

		UpdateCustomerJson transferjson = new UpdateCustomerJson(updateCustomerForm);
		log.info("{}", transferjson);
		String jsonString = new ObjectMapper().writeValueAsString(transferjson);
		log.info(jsonString);

		WebClient client = WebClient
				.create(ConnectionInfo.getAddressAndPort() + "/updcust/update");

		try
		{
			ResponseSpec response = client.put()
					.header(CONTENT_TYPE, APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.body(BodyInserters.fromValue(jsonString)).retrieve();
			String responseBody = response.bodyToMono(String.class).block();
			log.info(responseBody);

			UpdateCustomerJson responseObj = new ObjectMapper()
					.readValue(responseBody, UpdateCustomerJson.class);
			log.info("{}", responseObj);
			checkIfResponseValidUpdateCust(responseObj);

			return ResponseEntity.ok(buildResponse(true, "Customer updated",
					responseObj.toPrettyString()));
		}
		catch (ItemNotFoundException | IllegalArgumentException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, "Update Error", e.getMessage()));
		}
		catch (WebClientRequestException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(buildResponse(false, CONNECTION_ERROR,
					CONNECTION_ERROR_MSG));
		}
		catch (Exception e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, REQUEST_ERROR, ERROR_MSG));
		}
	}


	public static void checkIfResponseValidUpdateCust(
			UpdateCustomerJson responseObj)
			throws ItemNotFoundException, IllegalArgumentException
	{
		if (responseObj.getUpdcust().getCommUpdateSuccess().equals("N"))
		{
			if (responseObj.getUpdcust().getCommUpdateFailCode().equals("4"))
			{
				throw new IllegalArgumentException(
						"No name and no address supplied. (Are there spaces before both the name and the address?)");
			}
			if (responseObj.getUpdcust().getCommUpdateFailCode().equals("T"))
			{
				throw new IllegalArgumentException(
						"Invalid title; Valid titles are: Professor, Mr, Mrs, Miss, Ms, Dr, Drs, Lord, Sir or Lady.");
			}
			throw new ItemNotFoundException(CUSTOMER);
		}
	}


	// 8. Delete an account
	@PostMapping("/delacct")
	public ResponseEntity<Map<String, Object>> deleteAcct(
			@Valid AccountEnquiryForm accountEnquiryForm,
			BindingResult bindingResult) throws JsonProcessingException
	{
		if (bindingResult.hasErrors())
		{
			return ResponseEntity.badRequest()
					.body(buildResponse(false, "Validation Error",
							"Please check the form fields."));
		}

		WebClient client = WebClient
				.create(ConnectionInfo.getAddressAndPort()
						+ "/delacc/remove/"
						+ accountEnquiryForm.getAcctNumber());

		try
		{
			ResponseSpec response = client.delete().retrieve();
			String responseBody = response.bodyToMono(String.class).block();
			log.info(responseBody);
			DeleteAccountJson responseObj = new ObjectMapper()
					.readValue(responseBody, DeleteAccountJson.class);
			log.info("{}", responseObj);
			checkIfResponseValidDeleteAcc(responseObj);
			return ResponseEntity.ok(buildResponse(true, "Account Deleted",
					responseObj.toPrettyString()));
		}
		catch (ItemNotFoundException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, REQUEST_ERROR, e.getMessage()));
		}
		catch (WebClientRequestException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, REQUEST_ERROR, CONNECTION_ERROR_MSG));
		}
		catch (Exception e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, REQUEST_ERROR, ERROR_MSG));
		}
	}


	public static void checkIfResponseValidDeleteAcc(
			DeleteAccountJson responseObj) throws ItemNotFoundException
	{
		if (responseObj.getDelaccCommarea().getDelaccDelFailCode() == 1)
		{
			throw new ItemNotFoundException(ACCOUNT);
		}
	}


	// 9. Delete a customer
	@PostMapping("/delcust")
	public ResponseEntity<Map<String, Object>> deleteCust(
			@Valid CustomerEnquiryForm customerEnquiryForm,
			BindingResult bindingResult) throws JsonProcessingException
	{
		if (bindingResult.hasErrors())
		{
			return ResponseEntity.badRequest()
					.body(buildResponse(false, "Validation Error",
							"Please check the form fields."));
		}

		WebClient client = WebClient
				.create(ConnectionInfo.getAddressAndPort()
						+ "/delcus/remove/" + String
								.format(String
										.format("%10s",
												customerEnquiryForm
														.getCustNumber())
										.replace(" ", "0")));

		try
		{
			ResponseSpec response = client.delete().retrieve();
			String responseBody = response.bodyToMono(String.class).block();
			log.info(responseBody);
			DeleteCustomerJson responseObj = new ObjectMapper()
					.readValue(responseBody, DeleteCustomerJson.class);
			log.info("{}", responseObj);
			checkIfResponseValidDeleteCust(responseObj);
			return ResponseEntity.ok(buildResponse(true,
					"Customer and associated accounts Deleted",
					responseObj.toPrettyString()));
		}
		catch (ItemNotFoundException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, REQUEST_ERROR, e.getMessage()));
		}
		catch (WebClientRequestException e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, REQUEST_ERROR, CONNECTION_ERROR_MSG));
		}
		catch (Exception e)
		{
			log.info(e.toString());
			return ResponseEntity.ok(
					buildResponse(false, REQUEST_ERROR, ERROR_MSG));
		}
	}


	public static void checkIfResponseValidDeleteCust(
			DeleteCustomerJson responseObj) throws ItemNotFoundException
	{
		if (responseObj.getDelcus().getCommDelFailCode() == 1)
		{
			throw new ItemNotFoundException(CUSTOMER);
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

class TooManyAccountsException extends Exception
{
	private static final long serialVersionUID = -3421012321723845378L;
	static final String COPYRIGHT = "Copyright IBM Corp. 2022";

	public TooManyAccountsException(int customerNumber)
	{
		super("Too many accounts for customer number " + customerNumber
				+ "; Try deleting an account first.");
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

class InvalidCustomerException extends Exception
{
	private static final long serialVersionUID = 1L;
	static final String COPYRIGHT = "Copyright IBM Corp. 2022";

	public InvalidCustomerException(String message)
	{
		super(message);
	}
}
