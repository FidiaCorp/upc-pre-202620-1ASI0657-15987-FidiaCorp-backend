# language: en
Feature: identity-subscription-service — acceso, perfil, segundo factor, sesiones, pasarelas y suscripciones (US01–US06)
  Las credenciales se validan sin estado, cada recurso pertenece al principal autenticado y los pagos son sandbox.

  Background:
    Given a client, a second client and an advisor registered in the identity service

  @US01
  Scenario: El usuario ingresa con su correo y contraseña correctos
    When the client logs in with the correct email in upper case and password
    Then a bearer token carrying the account identity is issued and the profile can be read with it

  @US01
  Scenario: El usuario no posee los datos correctos
    When the client logs in with a wrong password
    Then the login is rejected with status 401 and no token is issued

  @US02
  Scenario: Los cambios de perfil persisten tras un nuevo ingreso y los inválidos no se guardan
    When the client submits an invalid profile then saves a valid profile
    Then a new login reads the saved profile and the other client remains unchanged

  @US03
  Scenario: Solo un código TOTP válido activa el segundo factor
    When the client configures TOTP and tries an invalid code followed by a valid code
    Then login without the second factor issues no token
    And login with a fresh valid code issues a token

  @US04
  Scenario: El cierre de sesión revoca solo el token presentado
    When the client closes one of two active sessions
    Then the closed session is rejected and the other session can read its profile
    And the token introspection used by the API Gateway reports the closed session as inactive

  @US05
  Scenario: El usuario elige una pasarela, la transacción es rechazada por fondos y reintenta con otro procesador
    When the client lists the available payment gateways
    And the client pays plan "PREMIUM" through gateway "IZIPAY" with card token "tok_DECLINED_INSUFFICIENT_FUNDS"
    Then the payment is rejected with status 402 and decline code "INSUFFICIENT_FUNDS"
    When the client pays plan "PREMIUM" through gateway "CULQI" with card token "tok_visa_approved"
    Then the gateway approves the charge with a signed transaction reference

  @US06
  Scenario: Un cliente consulta el estado de su suscripción y verifica un pago aprobado para activarla
    When the client pays plan "PRO" through gateway "STRIPE" with card token "tok_mastercard_ok"
    And the client verifies the approved payment for plan "PRO" with amount "99.90"
    Then the subscription is active with confirmed payment status and 1 payment

  @US06
  Scenario: Un pago con monto o plan inválido no activa la suscripción
    When the client attempts to verify a payment with zero amount
    Then the payment verification is rejected with status 400
    When the client pays plan "PRO" through gateway "CULQI" with card token "tok_visa_approved"
    And the client verifies the approved payment for plan "PRO" with amount "0.01"
    Then the payment verification is rejected with status 422
    And the client subscription remains inactive
    When the client verifies the approved payment for plan "PRO" with amount "99.90"
    And the second client tries to verify the client's approved payment
    Then the payment verification is rejected with status 409
