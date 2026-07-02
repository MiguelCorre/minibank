Feature: Authentication and session management
  Customers exchange credentials for a short-lived access token and a
  rotating refresh token. Failed logins are rate limited.

  Scenario: A new customer registers and signs in
    Given a registered user "carla@example.dev" named "Carla" with password "secret-123"
    When "carla@example.dev" logs in with password "secret-123"
    Then the response contains an access token and a refresh token
    And the authenticated profile shows "Carla"

  Scenario: A wrong password is rejected
    Given a registered user "dora@example.dev" named "Dora" with password "secret-123"
    When "dora@example.dev" logs in with password "not-the-password"
    Then the request fails with status 401

  Scenario: The API requires authentication
    When the accounts list is requested without a token
    Then the request fails with status 401

  Scenario: A rotated refresh token cannot be reused
    Given a registered user "eva@example.dev" named "Eva" with password "secret-123"
    And "eva@example.dev" is logged in
    When the session is refreshed
    And the previous refresh token is used again
    Then the request fails with status 401

  Scenario: Repeated failed logins are rate limited
    Given a registered user "fabio@example.dev" named "Fabio" with password "secret-123"
    When "fabio@example.dev" fails to log in 5 times
    And "fabio@example.dev" logs in with password "secret-123"
    Then the request fails with status 429
