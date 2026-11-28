Feature: property-catalog-service — catálogo, legitimidad registral y asesor hipotecario IA (US07–US09, US13–US15)
  El catálogo autenticado es compartido; solo el propietario modifica o verifica su inmueble y el asesor usa BigDecimal.

  Background:
    Given a client, a second client and an advisor authenticated with JWTs issued by the identity service

  @US07
  Scenario: Registro exitoso de un inmueble
    When the client registers the property "Departamento Barranco" priced "385000.00" PEN with tags "vista-mar, terraza"
    Then the property is stored with its owner and can be read by its ID

  @US07
  Scenario: Campos obligatorios incompletos no registran el inmueble
    When the client submits a property without title and with a negative price
    Then the property registration is rejected with validation errors for "title" and "price"

  @US08
  Scenario: Solo el propietario puede eliminar un inmueble registrado
    When another client tries to delete the property before its owner deletes it
    Then the deleted property is no longer in the catalogue or available by ID

  @US09
  Scenario: El propietario valida la autenticidad registral del inmueble mediante SUNARP
    When the client registers a property and verifies registry with entry "14298172"
    Then the property legitimacy report is verified with a valid certificate hash
    And the catalogue card of the property shows the verified badge

  @US09
  Scenario: Una consulta registral sobre un inmueble inexistente o ajeno es rechazada
    When a registry verification is requested for a non-existent property ID
    Then the registry verification attempt returns status 404
    When the other client tries to verify the registry of the client's property
    Then the registry verification attempt returns status 403
    And the property remains pending verification

  @US13
  Scenario: Búsqueda con resultados coincidentes
    When the client registers the property "Casa Chorrillos Bajo" priced "410000.00" PEN with tags "jardin"
    And the client searches the catalogue for "chorrillos"
    Then the search returns the property "Casa Chorrillos Bajo"

  @US13
  Scenario: Búsqueda sin resultados
    When the client searches the catalogue for "termino-inexistente-xyz"
    Then the search returns no properties

  @US14
  Scenario: Las etiquetas normalizadas se combinan con texto y propiedad mediante AND
    When properties with overlapping tags are registered
    Then the search returns only properties matching every requested tag and owner

  @US15
  Scenario: El asistente hipotecario IA evalúa el ingreso mensual y recomienda inmuebles que no superen el 35% SBS
    When the client queries AI recommendations with monthly income "7500.00" and down payment "50000.00"
    Then the AI borrower evaluation limits installment to "2625.00" and explains the 35 percent SBS rule

  @US15
  Scenario: El asistente hipotecario IA filtra por distrito y etiquetas preferidas asignando un score
    When properties in Miraflores and Surco are registered
    And the client requests AI recommendations for district "MIRAFLORES" with tag "terraza"
    Then the Miraflores property ranks first with a high compatibility score
