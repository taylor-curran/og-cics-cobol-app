Feature: Credit Score Generation - Happy Path (z/OS Connect API)

  Background:
    * url zosConnectBaseUrl

  Scenario: Create customer via z/OS Connect and verify credit score in response
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
          "CommName": "Mrs Karate Z Tester",
          "CommAddress": "42 Test Lane, London. EC1A 1BB",
          "CommDateOfBirth": 15031985,
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
    And match response.CreCust.CommFailCode == ''
    # Credit score should be between 1 and 999
    And match response.CreCust.CommCreditScore == '#number'
    * def creditScore = response.CreCust.CommCreditScore
    * assert creditScore >= 1 && creditScore <= 999
    # Review date should be non-zero (DDMMYYYY format integer)
    And match response.CreCust.CommCsReviewDate == '#number'
    * assert response.CreCust.CommCsReviewDate > 0
    # Customer number should have been assigned
    And match response.CreCust.CommKey.CommNumber != 0
    # Sort code should be populated
    And match response.CreCust.CommKey.CommSortcode != 0

  Scenario: All 5 credit agencies respond and average is within valid range
    # This scenario validates that the average of 5 agency scores (each 1-999) produces
    # a final score also in range 1-999
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
          "CommName": "Dr Agency A Validator",
          "CommAddress": "5 Agency Road, Edinburgh. EH1 1AA",
          "CommDateOfBirth": 20061980,
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
    * def creditScore = response.CreCust.CommCreditScore
    # Average of scores each 1-999 must itself be 1-999
    * assert creditScore >= 1 && creditScore <= 999
