package com.fidiacorp.credicasa.communication.interfaces.rest;

import com.fidiacorp.credicasa.communication.application.dto.response.*;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.*;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;
import java.util.*;

/** Documentation only: records the wire contract without changing controllers, serializers or authorization. */
@Component
public class ApiContractDocumentation implements OpenApiCustomizer {
    private static final String PREFIX="/api/v1";
    private static final String PLAIN_DECIMAL="^-?[0-9]+(\\.[0-9]+)?$";
    private static final String XLSX="binary:application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String TEXT="binary:text/plain;charset=UTF-8";
    private static final String PROBLEM="application/problem+json";
    private static final String SECURITY_FAILURE="JWT ausente, inválido, expirado o revocado. El filtro de seguridad no garantiza cuerpo JSON.";
    private record Endpoint(String path,PathItem.HttpMethod method,String tag,String summary,String description,
                            int status,String schema,boolean anonymous,boolean array) {}
    private Endpoint endpoint(String path,PathItem.HttpMethod method,String tag,String summary,String description,
                              int status,String schema,boolean anonymous) {
        return new Endpoint(path,method,tag,summary,description,status,schema,anonymous,false);
    }
    /** Prefijos de ruta que pertenecen a este microservicio (el resto lo documenta su propio servicio). */
    private static final List<String> OWNED=List.of("/notifications","/messages");
    private List<Endpoint> endpoints() {
        return allEndpoints().stream().filter(e -> OWNED.stream().anyMatch(e.path()::startsWith)).toList();
    }
    private List<Endpoint> allEndpoints() {
        return List.of(
            endpoint("/auth/register",PathItem.HttpMethod.POST,"Acceso","Registrar una cuenta CLIENT",
                "Público. Nombres obligatorios, correo único normalizado y contraseña de 12–72 caracteres/máximo 72 bytes UTF-8. "
                    +"Solo se asigna ROLE_CLIENT; no hay cuentas precargadas ni asignación pública de REALTOR.",201,"AccountProfileResponse",true),
            endpoint("/auth/login",PathItem.HttpMethod.POST,"Acceso","Ingresar con contraseña y, si corresponde, TOTP",
                "Público. Mantiene {token,type,email,roles,expiresIn}; expiresIn está en milisegundos. "
                    +"Con 2FA activo, un OTP ausente, expirado o reutilizado impide emitir JWT. "
                    +"El límite de cinco fallos/15 minutos cubre segundo factor y gestión, no los errores de contraseña primaria del login.",200,"AuthResponseDto",true),
            endpoint("/auth/logout",PathItem.HttpMethod.POST,"Acceso","Revocar únicamente el token actual",
                "Requiere Bearer, sin cuerpo. Persiste SHA-256 y expiración. Otras sesiones siguen válidas; "
                    +"un reintento con el token revocado recibe 401. No acepta un token alternativo del cuerpo.",204,null,false),
            endpoint("/users/me",PathItem.HttpMethod.GET,"Perfil","Consultar el perfil propio","La identidad procede del UUID del JWT; no existe consulta de perfiles ajenos.",200,"AccountProfileResponse",false),
            endpoint("/users/me",PathItem.HttpMethod.PUT,"Perfil","Actualizar el perfil propio","firstName, lastName y email son obligatorios. El UUID del JWT permanece estable al cambiar el correo; roles no se modifican.",200,"AccountProfileResponse",false),
            endpoint("/users/me/two-factor",PathItem.HttpMethod.GET,"Segundo factor","Consultar el estado TOTP propio","Solo devuelve enabled y pending; nunca devuelve el secreto activo.",200,"Status",false),
            endpoint("/users/me/two-factor/setup",PathItem.HttpMethod.POST,"Segundo factor","Generar una configuración TOTP pendiente",
                "Exige contraseña. Devuelve secret, otpauthUri y expiresIn=600 segundos con Cache-Control:no-store. "
                    +"Regenerar reemplaza un secreto pendiente, no uno activo, y no reinicia el bloqueo. El secreto se cifra en reposo con clave de entorno.",200,"Setup",false),
            endpoint("/users/me/two-factor/confirm",PathItem.HttpMethod.POST,"Segundo factor","Confirmar el TOTP pendiente",
                "OTP de seis dígitos del paso actual de 30 segundos, configuración con vigencia 10 minutos. Activa y consume el código; "
                    +"espere al siguiente para otro ingreso u operación. No revoca sesiones previas.",200,"Status",false),
            endpoint("/users/me/two-factor/disable",PathItem.HttpMethod.POST,"Segundo factor","Desactivar TOTP propio",
                "Exige contraseña y OTP nuevo; un código ya consumido no se admite. Elimina el secreto y mantiene el primer factor.",200,"Status",false),
            endpoint("/properties",PathItem.HttpMethod.POST,"Inmuebles","Registrar un inmueble propio",
                "CLIENT o REALTOR. OwnerId se toma del principal. Título 120, dirección 250 y descripción 2000 caracteres; "
                    +"precio positivo 16+2, PEN/USD, hasta 20 etiquetas de 40 caracteres normalizadas NFKC/minúsculas/espacios y sin duplicados.",201,"PropertyResponse",false),
            endpoint("/properties",PathItem.HttpMethod.GET,"Inmuebles","Buscar inmuebles activos",
                "CLIENT o REALTOR. q literal sin distinguir mayúsculas en título/dirección/descripción (máximo 120); "
                    +"tags repetidos normalizados se combinan con AND; mine=true limita al principal. "
                    +"page desde 0, size por defecto 20 y rango 1–100; orden createdAt DESC,id DESC. Excluye eliminados.",200,"PropertyPageResponse",false),
            endpoint("/properties/{id}",PathItem.HttpMethod.GET,"Inmuebles","Consultar un inmueble activo","CLIENT o REALTOR. El catálogo autenticado es compartido; recurso inexistente o eliminado devuelve 404.",200,"PropertyResponse",false),
            endpoint("/properties/{id}",PathItem.HttpMethod.DELETE,"Inmuebles","Eliminar un inmueble propio",
                "Solo el propietario, incluso si otro usuario es REALTOR. Borrado lógico; conserva instantáneas históricas. "
                    +"Eliminar de nuevo devuelve 404. No existe edición PUT/PATCH.",204,null,false),
            endpoint("/offers",PathItem.HttpMethod.POST,"Ofertas DEMO","Emitir una oferta DEMO y su aviso de forma atómica",
                "Solo REALTOR para una propiedad propia activa y destinatario ROLE_CLIENT resuelto por correo. "
                    +"BCP/INTERBANK; banco y título derivados. Moneda igual a la propiedad, préstamo≤precio, TEA decimal(0,1] "
                    +"hasta 8 decimales, plazo 1–360 y fecha segura. No realiza una aprobación bancaria real.",201,"CreditOfferResponse",false),
            endpoint("/offers",PathItem.HttpMethod.GET,"Ofertas DEMO","Listar ofertas recibidas propias","CLIENT o REALTOR. Solo destinatario; page desde 0,size 20/rango 1–100, orden createdAt DESC,id DESC.",200,"CreditOfferPageResponse",false),
            endpoint("/offers/{offerId}",PathItem.HttpMethod.GET,"Ofertas DEMO","Consultar una oferta recibida","Solo destinatario; el emisor y terceros reciben 404. La instantánea del inmueble se conserva tras eliminarlo del catálogo.",200,"CreditOfferResponse",false),
            endpoint("/offers/{offerId}/schedule",PathItem.HttpMethod.POST,"Cronogramas","Generar u obtener el cronograma persistido de una oferta",
                "Solo destinatario, sin cuerpo. Siempre 200. Bloqueo transaccional e idempotencia también con concurrencia. "
                    +"Términos exclusivamente del snapshot; sin gracia, balón, seguros ni gastos y COK=TEA. "
                    +"Cronograma DEMO, no una TCEA regulatoria de un producto bancario real.",200,"SimulationResponseDto",false),
            endpoint("/offers/{offerId}/schedule",PathItem.HttpMethod.GET,"Cronogramas","Leer el cronograma ya generado de una oferta","Solo destinatario; si aún no existe, la oferta es ajena o no existe, devuelve 404. No recalcula.",200,"SimulationResponseDto",false),
            endpoint("/notifications",PathItem.HttpMethod.GET,"Avisos","Consultar avisos propios y contador sin leer",
                "Solo destinatario. unreadOnly=false por defecto; unreadCount cuenta todos los propios pendientes, independiente de página/filtro. "
                    +"page 0,size 20/rango 1–100, createdAt DESC,id DESC. La UI consulta cada 5 segundos mientras esté visible y autenticada; no hay SSE.",200,"NotificationPageResponse",false),
            endpoint("/notifications/{notificationId}/read",PathItem.HttpMethod.PATCH,"Avisos","Marcar un aviso propio como leído","Sin cuerpo. Idempotente y conserva el primer readAt; ajeno o inexistente devuelve 404.",200,"OfferNotificationResponse",false),
            endpoint("/simulations/calculate",PathItem.HttpMethod.POST,"Cronogramas","Calcular y guardar una simulación propia",
                "CLIENT o REALTOR; owner y perfil se derivan de la cuenta. TEA decimal(0,1] hasta 8 decimales, importes positivos 16+2, "
                    +"loan≤property, plazo 1–360 (termYears consistente si se envía). NONE exige 0 meses; TOTAL/PARTIAL 1–36 y menor al plazo. "
                    +"Balón positivo solo al vencimiento y≤loan; seguros/COK[0,1], gastos no negativos. Fecha inicial 1900–9990 y última cuota≤9999. "
                    +"COK 0 es válido; el VAN es loan−pagos descontados. Con gracia total el capital amortizado incluye interés capitalizado.",201,"SimulationResponseDto",false),
            endpoint("/simulations/{id}",PathItem.HttpMethod.GET,"Cronogramas","Recuperar una simulación propia","Solo propietario. Ajena o heredada sin owner devuelve 404; no se infiere identidad ni COK histórico.",200,"SimulationResponseDto",false),
            new Endpoint("/banking/benchmarks",PathItem.HttpMethod.GET,"Referencias DEMO","Consultar referencias de tasas simuladas",
                "Público. Datos simulados en caché; no consulta oficial SBS ni garantía de latencia. El DTO heredado utiliza números JSON, no cadenas.",200,"BankRateResponseDto",true,true),
            endpoint("/banking/banks/{bankCode}/offer",PathItem.HttpMethod.GET,"Referencias DEMO","Consultar una cotización bancaria simulada",
                "CLIENT o REALTOR; adaptadores BCP/INTERBANK simulados, sin aprobación bancaria. No crea una oferta persistida ni aviso. "
                    +"El DTO heredado conserva tasas e importes como números JSON.",200,"BankRateResponseDto",false),
            // Sprint 3 / Etapa 1 (US05, US06, US09, US12, US15, US16, US17, US18)
            endpoint("/subscriptions/status",PathItem.HttpMethod.GET,"Suscripciones (US06)","Consultar estado de suscripción del usuario",
                "Solo la propia cuenta. Devuelve planName, status, active, startDate, endDate, statusDescription y paymentCount (pagos COMPLETED).",200,"SubscriptionStatusResponse",false),
            new Endpoint("/payments/gateways",PathItem.HttpMethod.GET,"Pagos (US05)","Listar pasarelas de pago sandbox disponibles",
                "CULQI, IZIPAY y STRIPE en modo sandbox con monedas, comisión porcentual, cargo fijo y tarifas oficiales por plan "
                    +"(BASIC 0.00, PREMIUM 49.90, PRO 99.90 PEN). No mueve dinero real.",200,"PaymentGatewayResponse",false,true),
            endpoint("/payments/checkout",PathItem.HttpMethod.POST,"Pagos (US05)","Iniciar un cobro sandbox con la pasarela elegida",
                "El importe se deriva de la tarifa oficial del plan (no del cliente). Aprobado: 200 con transactionReference firmada HMAC-SHA256 "
                    +"(GATEWAY-nonce-firma) ligada a cuenta, plan, importe y moneda. Token con DECLINED_INSUFFICIENT_FUNDS o DECLINED: 402 Payment Required "
                    +"con status=REJECTED, declineCode y retryAllowed=true para reintentar con otro procesador.",200,"CheckoutResponse",false),
            endpoint("/payments/verify",PathItem.HttpMethod.POST,"Pagos (US06)","Verificar la referencia de pago y activar la suscripción",
                "Exige transactionReference emitida por /payments/checkout, paymentMethod igual a la pasarela, planName y amount igual a la tarifa "
                    +"oficial en PEN; si no, 422. Idempotente para el mismo titular; una referencia de otra cuenta devuelve 409 sin filtrar el comprobante.",
                200,"PaymentVerificationResponse",false),
            endpoint("/properties/{id}/verify-registry",PathItem.HttpMethod.POST,"Inmuebles (US09)","Verificar partida registral del inmueble propio (SUNARP DEMO)",
                "Solo el propietario (otro usuario recibe 403). Partida de 8 dígitos; persiste estado, notas, titular y certificateHash SHA-256 "
                    +"sobre propertyId|registryNumber|claimedOwner|status|verifiedAt. La consulta registral es simulada.",200,"PropertyLegitimacyResponse",false),
            endpoint("/properties/{id}/legitimacy",PathItem.HttpMethod.GET,"Inmuebles (US09)","Consultar certificado registral de legitimidad",
                "Catálogo compartido. Devuelve status/legitimacyStatus (VERIFIED_GENUINE, FLAGGED_ANOMALY, PENDING_VERIFICATION), fechas, notas y certificateHash.",200,"PropertyLegitimacyResponse",false),
            endpoint("/simulations/{id}/export/xlsx",PathItem.HttpMethod.GET,"Cronogramas (US12)","Descargar cronograma de una simulación propia en .xlsx",
                "Solo propietario. Libro OpenXML (ZIP con firma PK\\x03\\x04) con métricas y tabla cuota a cuota.",200,XLSX,false),
            endpoint("/offers/{offerId}/schedule/export/xlsx",PathItem.HttpMethod.GET,"Cronogramas (US12)","Descargar cronograma de una oferta recibida en .xlsx",
                "Solo destinatario y con cronograma ya generado; terceros reciben 404.",200,XLSX,false),
            endpoint("/ai/recommendations",PathItem.HttpMethod.POST,"Asistente IA (US15)","Recomendar inmuebles según capacidad de pago y preferencias",
                "Motor determinista en BigDecimal (DECIMAL128, HALF_EVEN): cuota admisible 35% SBS, TEA de referencia 9.50%, solo inmuebles en la moneda "
                    +"solicitada; downPaymentAvailable y preferredDistrict opcionales.",200,"AiRecommendationResponse",false),
            endpoint("/messages/blocks/{targetUserId}",PathItem.HttpMethod.POST,"Mensajería (US17)","Bloquear a otro usuario en la mensajería",
                "Impide crear conversaciones y enviar mensajes entre ambas cuentas (409). Idempotente.",200,"UserBlockResponse",false),
            endpoint("/messages/blocks/{targetUserId}",PathItem.HttpMethod.DELETE,"Mensajería (US17)","Desbloquear a un usuario previamente bloqueado",
                "Restaura la posibilidad de interacción y comunicación mutua.",204,null,false),
            new Endpoint("/messages/blocks",PathItem.HttpMethod.GET,"Mensajería (US17)","Listar usuarios bloqueados por la cuenta actual",
                "Cada elemento expone blockId y blockedId.",200,"UserBlockResponse",false,true),
            endpoint("/messages/conversations",PathItem.HttpMethod.POST,"Mensajería (US16)","Crear u obtener una conversación con otra cuenta",
                "201 al crearla y 200 si ya existía entre ambas cuentas para el mismo propertyId (opcional, inmueble activo). "
                    +"recipientId debe existir; no admite conversaciones consigo mismo; con bloqueo activo devuelve 409.",201,"ConversationResponse",false),
            new Endpoint("/messages/conversations",PathItem.HttpMethod.GET,"Mensajería (US16)","Listar las conversaciones propias",
                "Solo conversaciones donde el principal es participante, con contraparte, contador y último mensaje.",200,"ConversationResponse",false,true),
            new Endpoint("/messages/conversations/{conversationId}/messages",PathItem.HttpMethod.GET,"Mensajería (US16)","Leer los mensajes de una conversación",
                "Orden cronológico. Inexistente: 404; el principal no participa: 403.",200,"ChatMessageResponse",false,true),
            endpoint("/messages",PathItem.HttpMethod.POST,"Mensajería (US16)","Enviar un mensaje privado en una conversación propia",
                "conversationId obligatorio; el destinatario es la contraparte (recipientId opcional debe coincidir). Inexistente 404, ajena 403, "
                    +"bloqueo activo 409.",200,"ChatMessageResponse",false),
            endpoint("/messages/conversations/{conversationId}/export",PathItem.HttpMethod.GET,"Mensajería (US18)","Descargar historial de conversación en archivo .txt",
                "Solo participantes (403 ajena, 404 inexistente). Transcripción completa en text/plain;charset=UTF-8.",200,TEXT,false),
            endpoint("/messages/conversations/{conversationId}/export/json",PathItem.HttpMethod.GET,"Mensajería (US18)","Obtener historial de conversación estructurado en JSON",
                "Solo participantes. Mensajes cronológicos y texto formateado.",200,"ConversationExportResponse",false));
    }
    @Override public void customise(OpenAPI api) {
        ModelConverters.getInstance().readAll(RestExceptionHandler.ErrorResponse.class).forEach(api.getComponents()::addSchemas);
        ModelConverters.getInstance().readAll(UserBlockResponse.class).forEach(api.getComponents()::addSchemas);
        ModelConverters.getInstance().readAll(ChatMessageResponse.class).forEach(api.getComponents()::addSchemas);
        ModelConverters.getInstance().readAll(ConversationExportResponse.class).forEach(api.getComponents()::addSchemas);
        ModelConverters.getInstance().readAll(ConversationResponse.class).forEach(api.getComponents()::addSchemas);
        api.setSecurity(null);
        Map<String,Tag> tags=new LinkedHashMap<>();
        for(Endpoint d:endpoints()) {
            PathItem path=api.getPaths().get(PREFIX+d.path());
            if(path==null || path.readOperationsMap().get(d.method())==null)throw new IllegalStateException("Missing documented operation: "+d.path());
            Operation op=path.readOperationsMap().get(d.method());
            op.setSummary(d.summary());op.setDescription(d.description());op.setTags(List.of(d.tag()));
            op.setSecurity(d.anonymous()?List.of():List.of(new SecurityRequirement().addList("BearerAuth")));
            if(op.getParameters()!=null)op.setParameters(op.getParameters().stream().filter(p -> !"Authorization".equalsIgnoreCase(p.getName())).toList());
            ApiResponses responses=new ApiResponses();
            if(d.schema()!=null && d.schema().startsWith("binary:")) {
                responses.addApiResponse(Integer.toString(d.status()),new ApiResponse().description("Archivo adjunto (Content-Disposition: attachment).")
                    .content(new Content().addMediaType(d.schema().substring(7),new MediaType().schema(new StringSchema().format("binary")))));
            } else {
                Schema schema=d.schema()==null?null:ref(d.schema());if(d.array())schema=new ArraySchema().items(schema);
                responses.addApiResponse(Integer.toString(d.status()),response(d.status()==204?"Operación completada, sin cuerpo.":"Operación completada.",schema));
            }
            if(d.status()==201 && Set.of("/properties","/offers").contains(d.path()))responses.get("201").addHeaderObject("Location",new Header().schema(new StringSchema()).description("URI del recurso creado."));
            if((d.method()!=PathItem.HttpMethod.GET && !d.path().equals("/auth/logout") && !d.path().startsWith("/messages/blocks")) || d.path().contains("{") || d.path().startsWith("/banking") || d.path().equals("/properties") || d.path().equals("/offers") || d.path().equals("/notifications"))
                responses.addApiResponse("400",response("Datos o parámetros inválidos; validationErrors puede identificar campos.",ref("ErrorResponse")));
            if(!d.anonymous()) {
                responses.addApiResponse("401",response(SECURITY_FAILURE,null));
                if(!d.path().startsWith("/users/me") && !d.path().equals("/auth/logout"))responses.addApiResponse("403",response("Rol o permiso por recurso insuficiente.",null));
            }
            if((d.path().contains("{") && !d.path().startsWith("/banking")) || (d.method()==PathItem.HttpMethod.POST && Set.of("/offers","/messages","/messages/conversations").contains(d.path())))
                responses.addApiResponse("404",response("Recurso inexistente, eliminado o no disponible para el principal.",ref("ErrorResponse")));
            if(d.path().equals("/auth/register") || (d.path().equals("/users/me") && d.method()==PathItem.HttpMethod.PUT))
                responses.addApiResponse("409",response("Correo ya registrado o actualización concurrente.",ref("ErrorResponse")));
            if(d.method()==PathItem.HttpMethod.POST && Set.of("/messages","/messages/conversations").contains(d.path()))
                responses.addApiResponse("409",response("Comunicación restringida: existe un bloqueo activo entre las cuentas.",ref("ErrorResponse")));
            if(d.path().equals("/payments/verify")) {
                responses.addApiResponse("409",response("La referencia ya fue utilizada por otra cuenta (no se revela el comprobante).",ref("ErrorResponse")));
                responses.addApiResponse("422",response("Monto distinto a la tarifa oficial, moneda distinta de PEN o referencia no emitida por la pasarela.",ref("ErrorResponse")));
            }
            if(d.path().equals("/payments/checkout"))responses.addApiResponse("402",new ApiResponse().description(
                "Payment Required: la pasarela rechazó el cobro (status=REJECTED, declineCode, retryAllowed=true).")
                .content(new Content().addMediaType("application/json",new MediaType().schema(ref("CheckoutResponse")))));
            if(d.path().startsWith("/users/me/two-factor") && d.method()==PathItem.HttpMethod.POST) {
                responses.get("401").setDescription(SECURITY_FAILURE+" Si falla contraseña/OTP con JWT válido, el cuerpo TwoFactorErrorResponse incluye INVALID_CREDENTIALS o TWO_FACTOR_INVALID; no implica sesión expirada.");
                responses.get("401").addExtension("x-application-error-schema",ref("TwoFactorErrorResponse"));
                responses.addApiResponse("409",response("Estado incompatible: TWO_FACTOR_ALREADY_ENABLED o TWO_FACTOR_NOT_ENABLED.",ref("TwoFactorErrorResponse")));
                responses.addApiResponse("503",response("TWO_FACTOR_UNAVAILABLE: clave ausente/incorrecta o secreto no descifrable.",ref("TwoFactorErrorResponse")));
                throttle(responses);
            }
            if(d.path().equals("/auth/login")) {
                responses.addApiResponse("401",response("Credenciales inválidas (ErrorResponse) o TWO_FACTOR_REQUIRED/TWO_FACTOR_INVALID (TwoFactorErrorResponse). No se emite JWT.",
                    new ComposedSchema().anyOf(List.of(ref("ErrorResponse"),ref("TwoFactorErrorResponse")))));
                responses.addApiResponse("503",response("TWO_FACTOR_UNAVAILABLE con cuenta 2FA activa; nunca se omite el segundo factor.",ref("TwoFactorErrorResponse")));throttle(responses);
            }
            if(d.path().equals("/users/me/two-factor/setup"))responses.get("200").addHeaderObject("Cache-Control",new Header().schema(new StringSchema().example("no-store")));
            if(d.path().equals("/users/me/two-factor/confirm"))responses.addApiResponse("400",response(
                "Validación (ErrorResponse) o TWO_FACTOR_SETUP_EXPIRED (TwoFactorErrorResponse): configuración pendiente vencida a los 600 segundos.",
                new ComposedSchema().anyOf(List.of(ref("ErrorResponse"),ref("TwoFactorErrorResponse")))));
            if(d.path().equals("/banking/benchmarks"))responses.get("200").addHeaderObject("X-Response-Time-Ms",new Header().schema(new StringSchema()).description("Duración observada de esa llamada; no un SLA."));
            responses.addApiResponse("500",response("Error interno; no se garantiza un formato de cuerpo si ocurre antes del controlador.",null));
            op.setResponses(responses);tags.putIfAbsent(d.tag(),new Tag().name(d.tag()));
        }
        api.setTags(new ArrayList<>(tags.values()));
        outputDecimals(api,"PropertyResponse","price");outputDecimals(api,"OfferPropertySnapshotResponse","price");
        outputDecimals(api,"CreditOfferResponse","loanAmount","annualInterestRate");
        outputDecimals(api,"SimulationResponseDto","loanAmount","propertyValue","estimatedMonthlyInstallment");
        outputDecimals(api,"InstallmentDto","initialBalance","interest","principalAmortization","installmentAmount","lifeInsurance","propertyInsurance","adminFee","totalPayment","finalBalance");
        outputDecimals(api,"FinancialMetricsDto","tea","tem","monthlyTir","tcea","van","totalInterest","totalPrincipal","totalLifeInsurance","totalPropertyInsurance","totalAdminFee","totalCost","discountRateUsed");
        inputDecimals(api,"CreatePropertyRequest","price");inputDecimals(api,"CreateCreditOfferRequest","loanAmount","annualInterestRate");
        inputDecimals(api,"SimulationRequestDto","propertyValue","loanAmount","annualInterestRate","balloonPaymentAmount","lifeInsuranceRate","propertyInsuranceRate","monthlyAdminFee","discountRateAnnual");
    }
    private Schema ref(String name) {return new Schema<>().$ref("#/components/schemas/"+name);}
    private ApiResponse response(String description,Schema schema) {
        ApiResponse result=new ApiResponse().description(description);
        if(schema==null)return result;
        String ref=schema.get$ref()==null?"":schema.get$ref();
        boolean problem=ref.endsWith("/ErrorResponse") || ref.endsWith("/TwoFactorErrorResponse") || schema instanceof ComposedSchema;
        return result.content(new Content().addMediaType(problem?PROBLEM:"application/json",new MediaType().schema(schema)));
    }
    private void throttle(ApiResponses responses) {
        responses.addApiResponse("429",response("TWO_FACTOR_LOCKED: cinco fallos bloquean 15 minutos; contador persistente.",ref("TwoFactorErrorResponse"))
            .addHeaderObject("Retry-After",new Header().description("Segundos restantes, legibles mediante CORS.").schema(new IntegerSchema().minimum(java.math.BigDecimal.ONE).maximum(java.math.BigDecimal.valueOf(900)))));
    }
    private void outputDecimals(OpenAPI api,String model,String... names) {
        Schema object=api.getComponents().getSchemas().get(model);
        if(object==null)return;
        for(String name:names) {
            Schema original=(Schema)object.getProperties().get(name);
            String description=(original.getDescription()==null?"":original.getDescription()+" ")+"Cadena decimal exacta en JSON, sin exponente.";
            Object example=original.getExample();String plain=example==null?"0.00":new java.math.BigDecimal(example.toString()).toPlainString();
            object.addProperty(name,new StringSchema().pattern(PLAIN_DECIMAL).description(description).example(plain).nullable(original.getNullable()));
        }
    }
    private void inputDecimals(OpenAPI api,String model,String... names) {
        Schema object=api.getComponents().getSchemas().get(model);
        if(object==null)return;
        for(String name:names) {
            Schema original=(Schema)object.getProperties().get(name);
            NumberSchema numeric=new NumberSchema();numeric.setMinimum(original.getMinimum());numeric.setMaximum(original.getMaximum());
            numeric.setExclusiveMinimum(original.getExclusiveMinimum());numeric.setExclusiveMaximum(original.getExclusiveMaximum());
            Schema text=new StringSchema().description("Cadena interpretable como BigDecimal; se aplican los mismos límites semánticos y de precisión.");
            object.addProperty(name,new ComposedSchema().oneOf(List.of(numeric,text)).description(
                (original.getDescription()==null?"":original.getDescription()+" ")+"Número JSON o cadena decimal. Los límites se validan también en representación textual.").example(original.getExample()));
        }
    }
}
