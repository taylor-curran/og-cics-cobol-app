Feature: Customer Operations - Happy Path (z/OS Connect API)

  Background:
    * url zosConnectBaseUrl

  Scenario: Inquire about a customer via z/OS Connect
    Given path '/inqcustz/enquiry/0000000001'
    And request
      """
      {
        "InqCustZ": {
          "InqCustEye": " ",
          "InqCustScode": "987654",
          "InqCustName": " ",
          "InqCustAddr": " ",
          "InqCustDob": {
            "InqCustDobDd": 0,
            "InqCustDobMm": 0,
            "InqCustDobYyyy": 0
          },
          "InqCustCreditScore": 0,
          "InqCustCsReviewDt": {
            "InqCustCsReviewDd": 0,
            "InqCustCsReviewMm": 0,
            "InqCustCsReviewYyyy": 0
          },
          "InqCustInqSuccess": " ",
          "InqCustInqFailCd": " ",
          "InqCustPcbPointer": " "
        }
      }
      """
    And header Content-Type = 'application/json'
    When method get
    Then status 200
    And match response.InqCustZ.InqCustInqSuccess == 'Y'
    And match response.InqCustZ.InqCustCustno == '#number'
    And match response.InqCustZ.InqCustName == '#present'
    And match response.InqCustZ.InqCustAddr == '#present'
    And match response.InqCustZ.InqCustCreditScore == '#number'

  Scenario: Update a customer via z/OS Connect
    # First create a customer to update
    Given path '/crecust/insert'
    And request
      """
      {
        "CreCust": {
          "CommEyecatcher": " ",
          "CommKey": {
            "CommSortcode": 0,
            "CommNumber": 0
          },
          "CommName": "Mrs Karate Z Updatable",
          "CommAddress": "1 Update Street, London. W1 1AA",
          "CommDateOfBirth": 10051990,
          "CommCreditScore": 0,
          "CommCsReviewDate": 0,
          "CommSuccess": " ",
          "CommFailCode": " "
        }
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 200
    And match response.CreCust.CommSuccess == 'Y'
    * def custNo = response.CreCust.CommKey.CommNumber
    * def scode = response.CreCust.CommKey.CommSortcode

    # Update the customer's address
    Given path '/updcust/update'
    And request
      """
      {
        "UpdCust": {
          "CommEye": " ",
          "CommScode": "#('' + scode)",
          "CommCustno": "#('' + custNo)",
          "CommName": "Mrs Karate Z Updated",
          "CommAddress": "99 Updated Road, London. W2 2BB",
          "CommDob": 10051990,
          "CommCreditScore": 0,
          "CommCsReviewDate": 0,
          "CommUpdSuccess": " ",
          "CommUpdFailCd": " "
        }
      }
      """
    And header Content-Type = 'application/json'
    When method put
    Then status 200
    And match response.UpdCust.CommUpdSuccess == 'Y'
    And match response.UpdCust.CommName == '#present'

  Scenario: Delete a customer via z/OS Connect
    # Create a customer to delete
    Given path '/crecust/insert'
    And request
      """
      {
        "CreCust": {
          "CommEyecatcher": " ",
          "CommKey": {
            "CommSortcode": 0,
            "CommNumber": 0
          },
          "CommName": "Mr Karate Z Deletable",
          "CommAddress": "1 Delete Road, Edinburgh. EH1 1AA",
          "CommDateOfBirth": 25121985,
          "CommCreditScore": 0,
          "CommCsReviewDate": 0,
          "CommSuccess": " ",
          "CommFailCode": " "
        }
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 200
    And match response.CreCust.CommSuccess == 'Y'
    * def custNo = response.CreCust.CommKey.CommNumber
    * def scode = response.CreCust.CommKey.CommSortcode

    # Delete the customer
    Given path '/delcus/remove/' + custNo
    And request
      """
      {
        "DelCus": {
          "CommEye": " ",
          "CommScode": "#('' + scode)",
          "CommName": " ",
          "CommAddr": " ",
          "CommDob": 0,
          "CommCreditScore": 0,
          "CommCsReviewDate": 0,
          "CommDelSuccess": " ",
          "CommDelFailCd": " "
        }
      }
      """
    And header Content-Type = 'application/json'
    When method delete
    Then status 200
    And match response.DelCus.CommDelSuccess == 'Y'
