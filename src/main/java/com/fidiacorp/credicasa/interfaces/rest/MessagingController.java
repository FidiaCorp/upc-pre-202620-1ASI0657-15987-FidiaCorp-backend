package com.fidiacorp.credicasa.interfaces.rest;

import com.fidiacorp.credicasa.application.dto.request.BlockUserRequest;
import com.fidiacorp.credicasa.application.dto.request.SendMessageRequest;
import com.fidiacorp.credicasa.application.dto.response.ChatMessageResponse;
import com.fidiacorp.credicasa.application.dto.response.ConversationExportResponse;
import com.fidiacorp.credicasa.application.dto.response.UserBlockResponse;
import com.fidiacorp.credicasa.application.usecase.MessagingControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/messages")
@Tag(name = "Messaging Controls (US17, US18)", description = "Control de privacidad, bloqueo de usuarios y exportación de conversaciones")
@PreAuthorize("hasAnyRole('CLIENT','REALTOR')")
public class MessagingController {

    private final MessagingControlService messagingService;

    public MessagingController(MessagingControlService messagingService) {
        this.messagingService = messagingService;
    }

    private UUID id(Principal p) {
        return UUID.fromString(p.getName());
    }

    @PostMapping("/blocks/{targetUserId}")
    @Operation(summary = "Bloquear a otro usuario en la mensajería (US17)",
            description = "Impide la recepción y envío de mensajes privados entre ambas cuentas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario bloqueado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Operación inválida (e.g. autobloqueo)")
    })
    public ResponseEntity<UserBlockResponse> blockUser(
            Principal principal,
            @PathVariable UUID targetUserId,
            @RequestBody(required = false) BlockUserRequest request) {
        UserBlockResponse response = messagingService.blockUser(id(principal), targetUserId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/blocks/{targetUserId}")
    @Operation(summary = "Desbloquear a un usuario previamente bloqueado")
    public ResponseEntity<Void> unblockUser(Principal principal, @PathVariable UUID targetUserId) {
        messagingService.unblockUser(id(principal), targetUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/blocks")
    @Operation(summary = "Listar usuarios bloqueados por la cuenta actual")
    public ResponseEntity<List<UserBlockResponse>> getBlockedUsers(Principal principal) {
        return ResponseEntity.ok(messagingService.getBlockedUsers(id(principal)));
    }

    @PostMapping
    @Operation(summary = "Enviar mensaje privado en una conversación")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            Principal principal,
            @Valid @RequestBody SendMessageRequest request) {
        ChatMessageResponse response = messagingService.sendMessage(id(principal), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversations/{conversationId}/export")
    @Operation(summary = "Descargar el historial de una conversación en archivo de texto .txt (US18)",
            description = "Genera un archivo con el registro cronológico completo de la charla.")
    public ResponseEntity<byte[]> exportConversationText(Principal principal, @PathVariable UUID conversationId) {
        ConversationExportResponse export = messagingService.exportConversation(id(principal), conversationId);
        byte[] bytes = export.formattedTranscript().getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"conversacion_" + conversationId + ".txt\"")
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping("/conversations/{conversationId}/export/json")
    @Operation(summary = "Obtener el historial de una conversación en formato estructurado JSON")
    public ResponseEntity<ConversationExportResponse> exportConversationJson(Principal principal, @PathVariable UUID conversationId) {
        return ResponseEntity.ok(messagingService.exportConversation(id(principal), conversationId));
    }
}
