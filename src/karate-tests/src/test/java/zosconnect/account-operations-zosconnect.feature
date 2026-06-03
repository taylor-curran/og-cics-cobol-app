Feature: Account Operations - Happy Path (z/OS Connect API)

  Background:
    * url zosConnectBaseUrl

  Scenario: Create an account via z/OS Connect and verify response
    Given path '/creacc/insert'
    And request
      """
      {
        "CreAcc": {
          "CommEyecatcher": " ",
          "CommCustno": 1,
          "CommKey": {
            "CommSortcode": 0,
            "CommNumber": 0
          },
          "CommAccType": "ISA",
          "CommIntRt": 1.50,
          "CommOpened": 0,
          "CommOverdrLim": 0,
          "CommLastStmtDt": 0,
          "CommNextStmtDt": 0,
          "CommAvailBal": 0.00,
          "CommActBal": 0.00,
          "CommSuccess": " ",
          "CommFailCode": " "
        }
      }
      """
    And header Content-Type = 'application/json'
    When method post
    Then status 200
    And match response.CreAcc.CommSuccess == 'Y'
    And match response.CreAcc.CommKey.CommNumber != 0
    And match response.CreAcc.CommKey.CommSortcode != 0

  Scenario: Inquire about an account via z/OS Connect
    Given path '/inqaccz/enquiry/00000001'
    And request
      """
      {
        "InqAcc": {
          "InqAccEye": " ",
          "InqAccCustno": 0,
          "InqAccScode": 0,
          "InqAccAccType": " ",
          "InqAccIntRate": 0,
          "InqAccOpened": 0,
          "InqAccOverdraft": 0,
          "InqAccLastStmtDt": 0,
          "InqAccNextStmtDt": 0,
          "InqAccAvailBal": 0,
          "InqAccActualBal": 0,
          "InqAccSuccess": " ",
          "InqAccPcb1Pointer": " "
        }
      }
      """
    And header Content-Type = 'application/json'
    When method get
    Then status 200
    And match response.InqAcc.InqAccSuccess == 'Y'
    And match response.InqAcc.InqAccAccno == '#number'
    And match response.InqAcc.InqAccCustno == '#number'
    And match response.InqAcc.InqAccAccType == '#present'

  Scenario: Inquire about accounts by customer number via z/OS Connect
    Given path '/inqacccz/list/0000000001'
    And request
      """
      {
        "InqAccZ": {
          "CommSuccess": " ",
          "CommFailCode": " ",
          "CustomerFound": " ",
          "CommPcbPointer": " ",
          "AccountDetails": []
        }
      }
      """
    And header Content-Type = 'application/json'
    When method get
    Then status 200
    And match response.InqAccZ.CommSuccess == '#present'
    And match response.InqAccZ.CustomerNumber == '#number'

  Scenario: Update an account via z/OS Connect
    Given path '/updacc/update'
    And request
      """
      {
        "UpdAcc": {
          "CommEye": " ",
          "CommCustno": "0000000001",
          "CommScode": "987654",
          "CommAccno": 1,
          "CommAccType": "SAVING",
          "CommIntRate": 2.50,
          "CommOpened": 0,
          "CommOverdraft": 500,
          "CommLastStmtDt": 0,
          "CommNextStmtDt": 0,
          "CommAvailBal": 0,
          "CommActualBal": 0,
          "CommSuccess": " "
        }
      }
      """
    And header Content-Type = 'application/json'
    When method put
    Then status 200
    And match response.UpdAcc.CommSuccess == 'Y'
