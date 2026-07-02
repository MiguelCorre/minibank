Feature: Account management
  Accounts are opened with a holder and a currency; deposits credit the
  balance and every movement lands in the ledger.

  Background:
    Given an authenticated session

  Scenario: Open an account
    When an account is opened for "Alice Martins" in EUR
    Then the account of "Alice Martins" has balance 0.00
    And the accounts list contains "Alice Martins"

  Scenario: Deposit funds into an account
    Given an account for "Bruno Costa" in EUR
    When 250.00 is deposited into the account of "Bruno Costa"
    Then the account of "Bruno Costa" has balance 250.00
    And the ledger of "Bruno Costa" shows a CREDIT of 250.00

  Scenario: A CSV statement lists the movements of the period
    Given an account for "Carla Nunes" in EUR with balance 300.00
    When the CSV statement of "Carla Nunes" for today is requested
    Then the statement has 1 movement line
