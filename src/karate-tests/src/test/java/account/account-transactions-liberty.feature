Feature: Account Transactions - Happy Path (Liberty REST API)

  Background:
    * url libertyBaseUrl

  Scenario: Credit an account and verify balance increases
    # Setup: Create customer and account
    Given path 'customer'
    And request
      """
      {
        "customerName": "Mr Karate T Credit",
        "customerAddress": "10 Credit Road, Glasgow, G1 1AA",
        "dateOfBirth": "1990-03-15",
        "sortCode": "#(sortCode)"
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 201
    * def customerId = response.id

    Given path 'account'
    And request
      """
      {
        "customerNumber": "#(customerId)",
        "sortCode": "#(sortCode)",
        "accountType": "CURRENT",
        "interestRate": 0.10,
        "overdraftLimit": 0
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 201
    * def accountId = response.id

    # Get initial balance
    Given path 'account', accountId
    When method get
    Then status 200
    * def initialBalance = parseFloat(response.actualBalance)

    # Credit the account with 500.00
    Given path 'account', 'credit', accountId
    And request { "amount": 500.00 }
    And header Content-Type = 'application/json'
    When method put
    Then status 200

    # Verify balance increased
    Given path 'account', accountId
    When method get
    Then status 200
    * def newBalance = parseFloat(response.actualBalance)
    * assert newBalance == initialBalance + 500.00

    # Cleanup
    Given path 'account', accountId
    When method delete
    Then status 200

    Given path 'customer', customerId
    When method delete
    Then status 200

  Scenario: Debit an account and verify balance decreases
    # Setup: Create customer and account
    Given path 'customer'
    And request
      """
      {
        "customerName": "Mrs Karate T Debit",
        "customerAddress": "20 Debit Lane, Glasgow, G2 2AA",
        "dateOfBirth": "1988-07-22",
        "sortCode": "#(sortCode)"
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 201
    * def customerId = response.id

    Given path 'account'
    And request
      """
      {
        "customerNumber": "#(customerId)",
        "sortCode": "#(sortCode)",
        "accountType": "CURRENT",
        "interestRate": 0.10,
        "overdraftLimit": 1000
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 201
    * def accountId = response.id

    # Credit the account first so there's money to debit
    Given path 'account', 'credit', accountId
    And request { "amount": 1000.00 }
    And header Content-Type = 'application/json'
    When method put
    Then status 200

    # Get balance after credit
    Given path 'account', accountId
    When method get
    Then status 200
    * def balanceAfterCredit = parseFloat(response.actualBalance)

    # Debit the account with 250.00
    Given path 'account', 'debit', accountId
    And request { "amount": 250.00 }
    And header Content-Type = 'application/json'
    When method put
    Then status 200

    # Verify balance decreased
    Given path 'account', accountId
    When method get
    Then status 200
    * def newBalance = parseFloat(response.actualBalance)
    * assert newBalance == balanceAfterCredit - 250.00

    # Cleanup
    Given path 'account', accountId
    When method delete
    Then status 200

    Given path 'customer', customerId
    When method delete
    Then status 200

  Scenario: Transfer funds between two accounts
    # Setup: Create a customer with two accounts
    Given path 'customer'
    And request
      """
      {
        "customerName": "Dr Karate T Transfer",
        "customerAddress": "30 Transfer Way, Belfast, BT1 1AA",
        "dateOfBirth": "1975-11-08",
        "sortCode": "#(sortCode)"
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 201
    * def customerId = response.id

    # Create source account
    Given path 'account'
    And request
      """
      {
        "customerNumber": "#(customerId)",
        "sortCode": "#(sortCode)",
        "accountType": "CURRENT",
        "interestRate": 0.10,
        "overdraftLimit": 0
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 201
    * def sourceAccountId = response.id

    # Create target account
    Given path 'account'
    And request
      """
      {
        "customerNumber": "#(customerId)",
        "sortCode": "#(sortCode)",
        "accountType": "SAVING",
        "interestRate": 1.50,
        "overdraftLimit": 0
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 201
    * def targetAccountId = response.id

    # Fund the source account
    Given path 'account', 'credit', sourceAccountId
    And request { "amount": 2000.00 }
    And header Content-Type = 'application/json'
    When method put
    Then status 200

    # Get initial balances
    Given path 'account', sourceAccountId
    When method get
    Then status 200
    * def sourceInitial = parseFloat(response.actualBalance)

    Given path 'account', targetAccountId
    When method get
    Then status 200
    * def targetInitial = parseFloat(response.actualBalance)

    # Transfer 750.00 from source to target
    Given path 'account', 'transfer', sourceAccountId
    And request { "targetAccountNumber": "#(targetAccountId)", "amount": 750.00 }
    And header Content-Type = 'application/json'
    When method put
    Then status 200

    # Verify source balance decreased
    Given path 'account', sourceAccountId
    When method get
    Then status 200
    * def sourceAfter = parseFloat(response.actualBalance)
    * assert sourceAfter == sourceInitial - 750.00

    # Verify target balance increased
    Given path 'account', targetAccountId
    When method get
    Then status 200
    * def targetAfter = parseFloat(response.actualBalance)
    * assert targetAfter == targetInitial + 750.00

    # Cleanup
    Given path 'account', sourceAccountId
    When method delete
    Then status 200

    Given path 'account', targetAccountId
    When method delete
    Then status 200

    Given path 'customer', customerId
    When method delete
    Then status 200
