Feature: Customer CRUD Operations - Happy Path (Liberty REST API)

  Background:
    * url libertyBaseUrl

  Scenario: Create, read, update, and delete a customer
    # Step 1: Create a new customer
    Given path 'customer'
    And request
      """
      {
        "customerName": "Mr Karate C Crud",
        "customerAddress": "10 CRUD Street, Birmingham, B1 1AA",
        "dateOfBirth": "1980-01-15",
        "sortCode": "#(sortCode)"
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 201
    And match response.id == '#notnull'
    And match response.customerName == 'Mr Karate C Crud'
    And match response.sortCode == sortCode
    * def customerId = response.id

    # Step 2: Retrieve the customer by ID
    Given path 'customer', customerId
    When method get
    Then status 200
    And match response.id == customerId
    And match response.customerName contains 'Karate'
    And match response.customerAddress contains 'CRUD Street'
    And match response.dateOfBirth == '#notnull'
    And match response.sortCode == sortCode

    # Step 3: Update the customer
    Given path 'customer', customerId
    And request
      """
      {
        "customerName": "Mr Karate C Updated",
        "customerAddress": "20 Updated Avenue, Birmingham, B2 2BB",
        "dateOfBirth": "1980-01-15",
        "sortCode": "#(sortCode)"
      }
      """
    And header Content-Type = 'application/json'
    When method put
    Then status 200
    And match response.id == customerId
    And match response.customerName == 'Mr Karate C Updated'
    And match response.customerAddress contains 'Updated Avenue'

    # Step 4: Verify update persisted
    Given path 'customer', customerId
    When method get
    Then status 200
    And match response.customerName == 'Mr Karate C Updated'
    And match response.customerAddress contains 'Updated Avenue'

    # Step 5: Delete the customer
    Given path 'customer', customerId
    When method delete
    Then status 200

  Scenario: List customers with pagination
    Given path 'customer'
    And param limit = 5
    And param offset = 0
    When method get
    Then status 200
    And match response.numberOfCustomers == '#notnull'
    And match response.customers == '#present'

  Scenario: Search customers by name
    # Create a uniquely named customer
    Given path 'customer'
    And request
      """
      {
        "customerName": "Mrs Karate S Searchable",
        "customerAddress": "55 Search Lane, Leeds, LS1 1AA",
        "dateOfBirth": "1992-07-04",
        "sortCode": "#(sortCode)"
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 201
    * def customerId = response.id

    # Search by name
    Given path 'customer', 'name'
    And param name = 'Searchable'
    And param limit = 10
    And param offset = 0
    When method get
    Then status 200
    And match response.numberOfCustomers == '#notnull'

    # Cleanup
    Given path 'customer', customerId
    When method delete
    Then status 200

  Scenario: Search customers by surname
    # Create a customer
    Given path 'customer'
    And request
      """
      {
        "customerName": "Dr Karate Surnamefind",
        "customerAddress": "77 Surname Road, Oxford, OX1 1AA",
        "dateOfBirth": "1988-12-25",
        "sortCode": "#(sortCode)"
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 201
    * def customerId = response.id

    # Search by surname
    Given path 'customer', 'all', 'surname', 'Surnamefind'
    When method get
    Then status 200

    # Cleanup
    Given path 'customer', customerId
    When method delete
    Then status 200

  Scenario: Search customers by age
    Given path 'customer', 'all', 'age', '30'
    When method get
    Then status 200
