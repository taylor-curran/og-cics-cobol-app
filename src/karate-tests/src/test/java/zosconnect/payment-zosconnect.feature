Feature: Payment Operations - Happy Path (z/OS Connect API)

  Background:
    * url zosConnectBaseUrl

  Scenario: Make a credit payment via z/OS Connect
    Given path '/makepayment/dbcr'
    And request
      """
      {
        "PAYDBCR": {
          "CommAccno": "00000001",
          "CommAmt": 100.00,
          "mSortC": 987654,
          "CommAvBal": 0,
          "CommActBal": 0,
          "CommOrigin": {
            "CommApplid": "KARATE",
            "CommUserid": "TESTUSER",
            "CommFacilityName": "KARATE",
            "CommNetwrkId": "TEST",
            "CommFaciltype": 0,
            "Fill0": " "
          },
          "CommSuccess": " ",
          "CommFailCode": " "
        }
      }
      """
    And header Content-Type = 'application/json'
    When method put
    Then status 200
    And match response.PAYDBCR.CommSuccess == 'Y'
    And match response.PAYDBCR.CommAvBal == '#number'
    And match response.PAYDBCR.CommActBal == '#number'

  Scenario: Make a debit payment via z/OS Connect
    Given path '/makepayment/dbcr'
    And request
      """
      {
        "PAYDBCR": {
          "CommAccno": "00000001",
          "CommAmt": -50.00,
          "mSortC": 987654,
          "CommAvBal": 0,
          "CommActBal": 0,
          "CommOrigin": {
            "CommApplid": "KARATE",
            "CommUserid": "TESTUSER",
            "CommFacilityName": "KARATE",
            "CommNetwrkId": "TEST",
            "CommFaciltype": 0,
            "Fill0": " "
          },
          "CommSuccess": " ",
          "CommFailCode": " "
        }
      }
      """
    And header Content-Type = 'application/json'
    When method put
    Then status 200
    And match response.PAYDBCR.CommSuccess == 'Y'
    And match response.PAYDBCR.CommAvBal == '#number'
    And match response.PAYDBCR.CommActBal == '#number'
