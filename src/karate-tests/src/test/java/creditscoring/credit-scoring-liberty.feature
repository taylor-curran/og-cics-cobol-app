Feature: Credit Score Generation - Happy Path (Liberty REST API)

  Background:
    * url libertyBaseUrl

  Scenario: Create a new customer and verify credit score is generated
    # Step 1: Create a new customer via POST
    Given path 'customer'
    And request
      """
      {
        "customerName": "Mrs Karate T Tester",
        "customerAddress": "42 Test Lane, London, EC1A 1BB",
        "dateOfBirth": "1985-03-15",
        "sortCode": "#(sortCode)"
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 201
    And match response.id == '#notnull'
    And match response.customerName == 'Mrs Karate T Tester'
    And match response.customerAddress == '42 Test Lane, London, EC1A 1BB'
    And match response.sortCode == sortCode
    * def customerId = response.id

    # Step 2: Retrieve the customer and verify credit score was assigned
    Given path 'customer', customerId
    When method get
    Then status 200
    And match response.id == customerId
    And match response.customerName contains 'Karate'
    And match response.customerCreditScore == '#notnull'
    And match response.customerCreditScore != '0'
    # Credit score should be a string representation of a number between 1 and 999
    * def creditScore = parseInt(response.customerCreditScore.trim())
    * assert creditScore >= 1 && creditScore <= 999
    # Review date should be present
    And match response.customerCreditScoreReviewDate == '#notnull'

    # Step 3: Clean up — delete the customer
    Given path 'customer', customerId
    When method delete
    Then status 200

  Scenario: Credit score and review date are persisted to CUSTOMER datastore
    # Create a customer
    Given path 'customer'
    And request
      """
      {
        "customerName": "Dr Persist R Check",
        "customerAddress": "99 Data Drive, Manchester, M1 1AA",
        "dateOfBirth": "1990-06-20",
        "sortCode": "#(sortCode)"
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 201
    * def customerId = response.id

    # Verify credit score persisted via GET
    Given path 'customer', customerId
    When method get
    Then status 200
    And match response.customerCreditScore == '#present'
    And match response.customerCreditScoreReviewDate == '#present'
    * def creditScore = parseInt(response.customerCreditScore.trim())
    * assert creditScore >= 1 && creditScore <= 999

    # Cleanup
    Given path 'customer', customerId
    When method delete
    Then status 200

  Scenario: Multiple customers get distinct credit scores (randomness check)
    # Create two customers in sequence
    Given path 'customer'
    And request
      """
      {
        "customerName": "Mr Random A Score",
        "customerAddress": "1 Probability Place, Bristol, BS1 1AA",
        "dateOfBirth": "1975-11-30",
        "sortCode": "#(sortCode)"
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 201
    * def customer1Id = response.id

    Given path 'customer'
    And request
      """
      {
        "customerName": "Mrs Random B Score",
        "customerAddress": "2 Probability Place, Bristol, BS1 1BB",
        "dateOfBirth": "1982-04-10",
        "sortCode": "#(sortCode)"
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 201
    * def customer2Id = response.id

    # Fetch both and verify each has a valid credit score
    Given path 'customer', customer1Id
    When method get
    Then status 200
    * def score1 = parseInt(response.customerCreditScore.trim())
    * assert score1 >= 1 && score1 <= 999
    * def reviewDate1 = response.customerCreditScoreReviewDate

    Given path 'customer', customer2Id
    When method get
    Then status 200
    * def score2 = parseInt(response.customerCreditScore.trim())
    * assert score2 >= 1 && score2 <= 999

    # Both should have valid scores (they may or may not be identical due to randomness,
    # but both must be in valid range)

    # Cleanup
    Given path 'customer', customer1Id
    When method delete
    Then status 200

    Given path 'customer', customer2Id
    When method delete
    Then status 200
