Feature: Daily transfer limits
  Outgoing transfers are capped per account per UTC day (1000.00 by
  default). Deposits and incoming transfers do not consume the allowance.

  Background:
    Given an authenticated session
    And an account for "Spender" in EUR with balance 5000.00
    And an account for "Sink" in EUR with balance 0.00

  Scenario: Transfers within the daily allowance succeed
    When 600.00 is transferred from "Spender" to "Sink"
    And 400.00 is transferred from "Spender" to "Sink"
    Then the account of "Spender" has balance 4000.00
    And the account of "Sink" has balance 1000.00

  Scenario: Exceeding the daily allowance is rejected
    When 600.00 is transferred from "Spender" to "Sink"
    And a transfer of 500.00 from "Spender" to "Sink" is attempted
    Then the request fails with status 422
    And the account of "Spender" has balance 4400.00

  Scenario: Incoming transfers do not consume the allowance
    Given an account for "Receiver" in EUR with balance 2000.00
    When 900.00 is transferred from "Receiver" to "Spender"
    And 1000.00 is transferred from "Spender" to "Sink"
    Then the account of "Spender" has balance 4900.00
