Feature: Sprint 2 authenticated housing credit journeys
  All bank proposals are DEMO; users can only read their own private resources.

  @US02
  Scenario: Profile changes survive a new login and invalid changes do not persist
    Given registered clients and an advisor with independent sessions
    When the client submits an invalid profile then saves a valid profile
    Then a new login reads the saved profile and the other client remains unchanged

  @US03
  Scenario: Only a valid authenticator code enables the second factor
    Given registered clients and an advisor with independent sessions
    When the client configures TOTP and tries an invalid code followed by a valid code
    Then login without the second factor issues no token

  @US04
  Scenario: Logout revokes the presented token without revoking a second session
    Given registered clients and an advisor with independent sessions
    When the client closes one of two active sessions
    Then the closed session is rejected and the other session can read its profile

  @US08 @US07-dependency
  Scenario: Only the property owner can remove a registered property
    Given registered clients and an advisor with independent sessions
    When another client tries to delete the property before its owner deletes it
    Then the deleted property is no longer in the catalogue or available by ID

  @US10
  Scenario: An issued DEMO offer creates a private notification that can be read twice
    Given registered clients and an advisor with independent sessions
    When the advisor issues an offer to the client
    Then only the recipient sees the offer and its notification and marking it read is idempotent

  @US11
  Scenario: A received offer keeps one persisted schedule after catalogue deletion
    Given registered clients and an advisor with independent sessions
    When the advisor issues an offer to the client
    And the source property is removed and the recipient generates its schedule twice
    Then the same persisted schedule is returned only to its recipient with zero final balance

  @US14
  Scenario: Normalized tags combine with text and ownership using AND
    Given registered clients and an advisor with independent sessions
    When properties with overlapping tags are registered
    Then the search returns only properties matching every requested tag and owner
