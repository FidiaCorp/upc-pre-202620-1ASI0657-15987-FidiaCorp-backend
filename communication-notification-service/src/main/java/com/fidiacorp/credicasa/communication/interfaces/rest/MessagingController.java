package com.fidiacorp.credicasa.communication.interfaces.rest;

import com.fidiacorp.credicasa.communication.application.dto.request.BlockUserRequest;
import com.fidiacorp.credicasa.communication.application.dto.request.CreateConversationRequest;
import com.fidiacorp.credicasa.communication.application.dto.request.SendMessageRequest;
import com.fidiacorp.credicasa.communication.application.dto.response.ChatMessageResponse;
import com.fidiacorp.credicasa.communication.application.dto.response.ConversationExportResponse;
import com.fidiacorp.credicasa.communication.application.dto.response.ConversationResponse;
import com.fidiacorp.credicasa.communication.application.dto.response.UserBlockResponse;
import com.fidiacorp.credicasa.communication.application.usecase.MessagingControlService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
@Tag(name = "Mensajería (US16, US17, US18)", description = "Conversaciones privadas, bloqueo de usuarios y exportación de historiales")
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
    public ResponseEntity<UserBlockResponse> blockUser(Principal principal, @PathVariable UUID targetUserId,
                                                       @Valid @RequestBody(required = false) BlockUserRequest request) {
        return ResponseEntity.ok(messagingService.blockUser(id(principal), targetUserId, request));
    }

    @DeleteMapping("/blocks/{targetUserId}")
    public ResponseEntity<Void> unblockUser(Principal principal, @PathVariable UUID targetUserId) {
        messagingService.unblockUser(id(principal), targetUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/blocks")
    public ResponseEntity<List<UserBlockResponse>> getBlockedUsers(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(messagingService.getBlockedUsers(id(principal)));
    }

    @PostMapping("/conversations")
    public ResponseEntity<ConversationResponse> startConversation(Principal principal, @Valid @RequestBody CreateConversationRequest request) {
        boolean existed = messagingService.conversationExists(id(principal), request);
        ConversationResponse response = messagingService.startConversation(id(principal), request);
        return ResponseEntity.status(existed ? HttpStatus.OK : HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationResponse>> listConversations(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(messagingService.listConversations(id(principal)));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> listMessages(Principal principal, @PathVariable UUID conversationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(messagingService.listMessages(id(principal), conversationId));
    }

    @PostMapping
    public ResponseEntity<ChatMessageResponse> sendMessage(Principal principal, @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(messagingService.sendMessage(id(principal), com.fidiacorp.credicasa.shared.security.AuthenticatedUser.displayName(principal), request));
    }

    @GetMapping("/conversations/{conversationId}/export")
    public ResponseEntity<byte[]> exportConversationText(Principal principal, @PathVariable UUID conversationId) {
        ConversationExportResponse export = messagingService.exportConversation(id(principal), conversationId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"conversacion_" + conversationId + ".txt\"")
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .body(export.formattedTranscript().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/conversations/{conversationId}/export/json")
    public ResponseEntity<ConversationExportResponse> exportConversationJson(Principal principal, @PathVariable UUID conversationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(messagingService.exportConversation(id(principal), conversationId));
    }
}
