Feature: mortgage-pricing-service — cronograma francés, exportación, TNA/TEA y comparador (US11, US12, US19, US20)
  Los cálculos usan BigDecimal DECIMAL128/HALF_EVEN, cada simulación pertenece al JWT y nada depende del proveedor externo.

  Background:
    Given a client and a second client authenticated with JWTs issued by the identity service

  @US11
  Scenario: Cálculo de cronograma con método francés y liquidación exacta
    When the client simulates a loan of "200000.00" PEN at TEA "0.09" for 240 months
    Then the schedule has 240 installments and the final balance is "0.00"
    And the response exposes TCEA, VAN and TIR as exact decimal strings

  @US11
  Scenario: Datos de crédito inválidos no generan cronograma
    When the client simulates a loan of "300000.00" PEN at TEA "8.5" for 240 months
    Then the simulation is rejected with status 400

  @US12
  Scenario: El propietario exporta su cronograma a Excel y otro usuario no puede
    When the client simulates a loan of "150000.00" PEN at TEA "0.095" for 120 months
    Then the client downloads the schedule as a valid XLSX workbook
    And the second client cannot download that schedule

  @US19
  Scenario: Simulación con TNA y capitalización diaria
    When the client simulates a loan of "200000.00" PEN at TNA "0.12" compounded 360 times per year for 240 months
    Then the simulation reports rate type "TNA" and effective annual rate "0.12747431"
    And retrieving that simulation exposes exactly the same rate terms and TEA

  @US19
  Scenario: Simulación con TNA sin capitalización es rechazada
    When the client simulates a TNA loan without compounding periods
    Then the simulation is rejected with status 400

  @US20
  Scenario: Listado de simulaciones propias
    When the client simulates a loan of "200000.00" PEN at TEA "0.09" for 240 months
    And the client simulates a loan of "200000.00" PEN at TNA "0.12" compounded 12 times per year for 240 months
    Then the client lists 2 own simulations with the most recent first
    And the second client lists 0 own simulations

  @US20
  Scenario: Comparación de escenarios con pago extraordinario
    When the client compares a TEA 9.5% scenario, a TNA 9% monthly scenario and a TEA 9.5% scenario with a 30000.00 prepayment after installment 24
    Then the comparison returns 3 scenarios with the TNA scenario best by TCEA
    And the prepayment scenario shortens the term and saves interest
