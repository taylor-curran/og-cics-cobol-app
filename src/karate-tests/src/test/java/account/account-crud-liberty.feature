Feature: Account CRUD Operations - Happy Path (Liberty REST API)

  Background:
    * url libertyBaseUrl

  Scenario: Create account, retrieve it, update it, and delete it
    # Prerequisite: Create a customer first
    Given path 'customer'
    And request
      """
      {
        "customerName": "Mrs Karate A Account",
        "customerAddress": "1 Account Lane, Liverpool, L1 1AA",
        "dateOfBirth": "1978-05-20",
        "sortCode": "#(sortCode)"
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 201
    * def customerId = response.id

    # Step 1: Create an account for the customer
    Given path 'account'
    And request
      """
      {
        "customerNumber": "#(customerId)",
        "sortCode": "#(sortCode)",
        "accountType": "ISA",
        "interestRate": 1.50,
        "overdraftLimit": 0
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 201
    And match response.id == '#notnull'
    And match response.customerNumber == customerId
    And match response.accountType == 'ISA'
    And match response.sortCode == sortCode
    * def accountId = response.id

    # Step 2: Retrieve the account
    Given path 'account', accountId
    When method get
    Then status 200
    And match response.id == accountId
    And match response.customerNumber == customerId
    And match response.accountType == 'ISA'
    And match response.availableBalance == '#notnull'
    And match response.actualBalance == '#notnull'

    # Step 3: Update the account (change type and interest rate)
    Given path 'account', accountId
    And request
      """
      {
        "customerNumber": "#(customerId)",
        "sortCode": "#(sortCode)",
        "accountType": "SAVING",
        "interestRate": 2.75,
        "overdraftLimit": 100
      }
      """
    And header Content-Type = 'application/json'
    When method put
    Then status 200
    And match response.id == accountId
    And match response.accountType == 'SAVING'

    # Step 4: Verify update persisted
    Given path 'account', accountId
    When method get
    Then status 200
    And match response.accountType == 'SAVING'

    # Step 5: Delete the account
    Given path 'account', accountId
    When method delete
    Then status 200

    # Cleanup: Delete the customer
    Given path 'customer', customerId
    When method delete
    Then status 200

  Scenario: List all accounts with pagination
    Given path 'account'
    And param limit = 5
    And param offset = 0
    When method get
    Then status 200
    And match response.numberOfAccounts == '#notnull'
    And match response.accounts == '#present'

  Scenario: Retrieve accounts by customer number
    # Create a customer
    Given path 'customer'
    And request
      """
      {
        "customerName": "Mr Karate B ByCustomer",
        "customerAddress": "2 ByCustomer Road, Cardiff, CF1 1AA",
        "dateOfBirth": "1985-09-10",
        "sortCode": "#(sortCode)"
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 201
    * def customerId = response.id

    # Create an account
    Given path 'account'
    And request
      """
      {
        "customerNumber": "#(customerId)",
        "sortCode": "#(sortCode)",
        "accountType": "CURRENT",
        "interestRate": 0.50,
        "overdraftLimit": 500
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 201
    * def accountId = response.id

    # Retrieve accounts by customer number
    Given path 'account', 'retrieveByCustomerNumber', customerId
    When method get
    Then status 200
    And match response.numberOfAccounts == '#notnull'

    # Cleanup
    Given path 'account', accountId
    When method delete
    Then status 200

    Given path 'customer', customerId
    When method delete
    Then status 200

  Scenario: Query accounts by balance
    Given path 'account', 'balance'
    And param balance = 0
    And param operator = '>'
    And param offset = 0
    And param limit = 5
    When method get
    Then status 200
