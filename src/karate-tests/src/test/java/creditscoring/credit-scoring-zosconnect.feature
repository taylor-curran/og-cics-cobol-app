Feature: Credit Score Generation - Happy Path (z/OS Connect API)

  Background:
    * url zosConnectBaseUrl

  Scenario: Create customer via z/OS Connect and verify credit score in response
    Given path '/crecust/insert'
    And request
      """
      {
        "CRECUST": {
          "COMM_EYECATCHER": " ",
          "COMM_KEY": {
            "COMM_SORTCODE": 0,
            "COMM_NUMBER": 0
          },
          "COMM_NAME": "Mrs Karate Z Tester",
          "COMM_ADDRESS": "42 Test Lane, London. EC1A 1BB",
          "COMM_DATE_OF_BIRTH": 15031985,
          "COMM_CREDIT_SCORE": 0,
          "COMM_CS_REVIEW_DATE": 0,
          "COMM_SUCCESS": " ",
          "COMM_FAIL_CODE": " "
        }
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 200
    And match response.CRECUST.COMM_SUCCESS == 'Y'
    And match response.CRECUST.COMM_FAIL_CODE == ''
    # Credit score should be between 1 and 999
    And match response.CRECUST.COMM_CREDIT_SCORE == '#number'
    * def creditScore = response.CRECUST.COMM_CREDIT_SCORE
    * assert creditScore >= 1 && creditScore <= 999
    # Review date should be non-zero (DDMMYYYY format integer)
    And match response.CRECUST.COMM_CS_REVIEW_DATE == '#number'
    * assert response.CRECUST.COMM_CS_REVIEW_DATE > 0
    # Customer number should have been assigned
    And match response.CRECUST.COMM_KEY.COMM_NUMBER != 0
    # Sort code should be populated
    And match response.CRECUST.COMM_KEY.COMM_SORTCODE != 0

  Scenario: All 5 credit agencies respond and average is within valid range
    # This scenario validates that the average of 5 agency scores (each 1-999) produces
    # a final score also in range 1-999
    Given path '/crecust/insert'
    And request
      """
      {
        "CRECUST": {
          "COMM_EYECATCHER": " ",
          "COMM_KEY": {
            "COMM_SORTCODE": 0,
            "COMM_NUMBER": 0
          },
          "COMM_NAME": "Dr Agency A Validator",
          "COMM_ADDRESS": "5 Agency Road, Edinburgh. EH1 1AA",
          "COMM_DATE_OF_BIRTH": 20061980,
          "COMM_CREDIT_SCORE": 0,
          "COMM_CS_REVIEW_DATE": 0,
          "COMM_SUCCESS": " ",
          "COMM_FAIL_CODE": " "
        }
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 200
    And match response.CRECUST.COMM_SUCCESS == 'Y'
    * def creditScore = response.CRECUST.COMM_CREDIT_SCORE
    # Average of scores each 1-999 must itself be 1-999
    * assert creditScore >= 1 && creditScore <= 999
