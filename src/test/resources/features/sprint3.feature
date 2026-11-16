Feature: Sprint 3 financial verification, registry legitimacy, AI advisor, and messaging security
  All operations enforce authenticated access, SBS compliance, and data privacy.

  @US06
  Scenario: Un cliente consulta el estado de su suscripción y verifica un pago para activarla
    Given registered clients and an advisor with independent sessions for sprint 3
    When the client checks subscription status and submits payment verification with reference "OP-102938"
    Then the subscription is active with confirmed payment status

  @US06
  Scenario: Un pago con monto o plan inválido no activa la suscripción
    Given registered clients and an advisor with independent sessions for sprint 3
    When the client attempts to verify a payment with zero amount
    Then the payment verification is rejected with status 400

  @US09
  Scenario: El propietario valida la autenticidad registral del inmueble mediante SUNARP
    Given registered clients and an advisor with independent sessions for sprint 3
    When the client registers a property and verifies registry with entry "14298172" and sheet "P03192831"
    Then the property legitimacy report is verified with a valid certificate hash

  @US09
  Scenario: Una consulta registral sobre un inmueble inexistente o datos fraudulentos es observada
    Given registered clients and an advisor with independent sessions for sprint 3
    When a registry verification is requested for a non-existent property ID
    Then the registry verification attempt returns not found status 404

  @US12
  Scenario: El usuario descarga el cronograma de pagos de una simulación en formato XLSX
    Given registered clients and an advisor with independent sessions for sprint 3
    When the client calculates a mortgage simulation and requests schedule export in xlsx
    Then the system returns a valid OpenXML spreadsheet binary stream

  @US12
  Scenario: Un usuario no puede descargar el cronograma en XLSX de una oferta que no le pertenece
    Given registered clients and an advisor with independent sessions for sprint 3
    When the advisor issues a housing credit offer to the client
    Then the other client is forbidden from exporting the offer schedule in xlsx

  @US15
  Scenario: El asistente hipotecario IA evalúa el ingreso mensual y recomienda inmuebles que no superen el 35% SBS
    Given registered clients and an advisor with independent sessions for sprint 3
    When the client queries AI recommendations with monthly income "7500.00" and down payment "50000.00"
    Then the AI borrower evaluation limits installment to 35 percent and suggests solvent properties

  @US15
  Scenario: El asistente hipotecario IA filtra por distrito y etiquetas preferidas asignando un score
    Given registered clients and an advisor with independent sessions for sprint 3
    When properties in Miraflores and Surco are registered
    And the client requests AI recommendations for district "MIRAFLORES" with tag "terraza"
    Then recommended properties match district criteria with high compatibility scores

  @US17
  Scenario: Un usuario bloquea a otro usuario indeseado para evitar mensajes directos
    Given registered clients and an advisor with independent sessions for sprint 3
    When the client blocks the other client with reason "Contacto comercial no deseado"
    Then the other client appears in the blocked list

  @US17
  Scenario: Un usuario bloqueado no puede enviar mensajes al usuario que lo bloqueó
    Given registered clients and an advisor with independent sessions for sprint 3
    When the client blocks the other client
    And the other client attempts to send a direct message to the client
    Then the message dispatch is blocked with status 409

  @US18
  Scenario: Un usuario descarga el historial completo de una conversación en formato texto
    Given registered clients and an advisor with independent sessions for sprint 3
    When messages are exchanged in a conversation
    Then the client can export the conversation transcript in plain text

  @US18
  Scenario: Un usuario descarga el historial de conversación en formato estructurado JSON
    Given registered clients and an advisor with independent sessions for sprint 3
    When messages are exchanged in a conversation
    Then the client can export the conversation transcript in JSON format
