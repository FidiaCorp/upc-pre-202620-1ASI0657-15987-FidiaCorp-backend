Feature: communication-notification-service — bandeja de avisos y mensajería privada (US10, US16, US17, US18)
  Los avisos llegan por RabbitMQ de forma idempotente y los mensajes exigen membresía estricta en la conversación.

  Background:
    Given two clients registered in the identity service

  @US10
  Scenario: El cliente recibe el aviso de una oferta emitida una sola vez aunque el evento se reentregue
    When the event "credit.offer.issued.v1" for the first client is delivered twice
    Then the first client has 1 unread notification titled "Oferta demostrativa de Banco de Crédito del Perú"
    And the second client has 0 notifications

  @US10
  Scenario: El cliente marca como leído su aviso
    When the event "credit.offer.issued.v1" for the first client is delivered twice
    And the first client marks the notification as read
    Then the first client has 0 unread notifications

  @US16
  Scenario: Contactar al propietario abre una conversación privada sobre un inmueble activo
    When the first client contacts the second client about an active property
    And the first client sends the message "¿Sigue disponible el departamento?"
    Then the second client reads 1 message from "Ana Perez"
    And a third user cannot read that conversation

  @US17
  Scenario: Un usuario bloqueado no puede enviar mensajes
    When the first client contacts the second client about an active property
    And the second client blocks the first client
    Then the first client cannot send messages in that conversation

  @US18
  Scenario: El participante exporta la conversación en texto y JSON
    When the first client contacts the second client about an active property
    And the first client sends the message "Hola, quisiera visitar el inmueble"
    Then the first client exports the conversation as text and JSON with 1 message
