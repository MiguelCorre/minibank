Feature: Account ownership
  Accounts belong to the customer who opened them. Another customer's
  account is indistinguishable from a missing one — but anyone may be the
  destination of a transfer, like a regular bank payment.

  Background:
    Given an authenticated session
    And an account for "Mine" in EUR with balance 100.00
    And another customer has an account for "Theirs" in EUR with balance 50.00

  Scenario: Another customer's account is invisible
    When the account of "Theirs" is requested
    Then the request fails with status 404

  Scenario: The accounts list only shows own accounts
    Then the accounts list contains "Mine"
    And the accounts list does not contain "Theirs"

  Scenario: Money cannot be moved out of another customer's account
    When a transfer of 10.00 from "Theirs" to "Mine" is attempted
    Then the request fails with status 404
    And the account of "Theirs" has balance 50.00

  Scenario: Transfers to another customer's account are allowed
    When 25.00 is transferred from "Mine" to "Theirs"
    Then the account of "Mine" has balance 75.00
    And the account of "Theirs" has balance 75.00
