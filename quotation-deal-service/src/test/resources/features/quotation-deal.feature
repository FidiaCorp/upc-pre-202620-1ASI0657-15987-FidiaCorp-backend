Feature: quotation-deal-service — ofertas, cronograma, versiones inmutables y exportación (US10, US11, US12, US21, US22)
  Destinatario e inmueble se validan por API interna (sin FKs); el cálculo financiero lo hace mortgage-pricing-service.

  Background:
    Given an advisor owning a property of "250000.00" PEN and a registered client

  @US10
  Scenario: El asesor emite una oferta DEMO y el aviso queda en el outbox
    When the advisor issues a BCP offer of "200000.00" to the client
    Then the client sees the offer in the received list
    And the outbox holds a "credit.offer.issued.v1" event addressed to the client

  @US10
  Scenario: Una oferta a un correo que no es cliente no se emite
    When the advisor issues a BCP offer of "200000.00" to an unknown email
    Then the offer request is rejected with status 404 and nothing is stored

  @US11
  Scenario: El destinatario genera una sola vez el cronograma de su oferta
    When the advisor issues a BCP offer of "200000.00" to the client
    And the client generates the offer schedule twice
    Then both responses are the same 12 installment schedule ending at balance "0.00"

  @US12
  Scenario: El destinatario descarga la cotización en Excel
    When the advisor issues a BCP offer of "200000.00" to the client
    Then the client downloads the "xlsx" export of version 1

  @US21
  Scenario: El asesor emite una nueva versión inmutable con mejor tasa
    When the advisor issues a BCP offer of "200000.00" to the client
    And the advisor issues version 2 with rate "0.0825" valid for 7 days
    Then the client sees 2 versions with distinct SHA-256 hashes and version 2 first
    And version 1 still keeps its original rate "0.08512345"
    And the outbox holds a "quotation.version.issued.v1" event addressed to the client

  @US22
  Scenario: PDF y XLSX se generan desde el mismo snapshot y quedan registrados
    When the advisor issues a BCP offer of "200000.00" to the client
    Then the client downloads the "pdf" export of version 1
    And the client downloads the "xlsx" export of version 1
    And the export history lists 2 completed jobs sharing the same snapshot hash
