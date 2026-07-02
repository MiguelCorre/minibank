Feature: Money transfers
  Transfers move money between two accounts exactly once: the Idempotency-Key
  header makes retries safe, and every movement is recorded twice in the
  ledger (double entry).

  Background:
    Given an authenticated session
    And an account for "Alice" in EUR with balance 500.00
    And an account for "Bob" in EUR with balance 100.00

  Scenario: A transfer moves money between two accounts
    When 150.00 is transferred from "Alice" to "Bob"
    Then the account of "Alice" has balance 350.00
    And the account of "Bob" has balance 250.00
    And the ledger of "Alice" shows a DEBIT of 150.00
    And the ledger of "Bob" shows a CREDIT of 150.00

  Scenario: Replaying the same idempotency key does not debit twice
    When 150.00 is transferred from "Alice" to "Bob"
    And the same transfer is submitted again
    Then the account of "Alice" has balance 350.00
    And the account of "Bob" has balance 250.00

  Scenario: A transfer without enough funds is rejected
    When a transfer of 999.99 from "Bob" to "Alice" is attempted
    Then the request fails with status 422
    And the account of "Bob" has balance 100.00

  Scenario: A transfer to the same account is rejected
    When a transfer of 10.00 from "Alice" to "Alice" is attempted
    Then the request fails with status 400

  Scenario: A cross-currency transfer is rejected
    Given an account for "Chuck" in USD with balance 300.00
    When a transfer of 50.00 from "Chuck" to "Alice" is attempted
    Then the request fails with status 422
    And the account of "Chuck" has balance 300.00
