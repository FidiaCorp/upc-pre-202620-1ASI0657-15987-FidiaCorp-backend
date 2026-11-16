package com.fidiacorp.credicasa.application.usecase;

import com.fidiacorp.credicasa.application.dto.request.BlockUserRequest;
import com.fidiacorp.credicasa.application.dto.request.SendMessageRequest;
import com.fidiacorp.credicasa.application.dto.response.ChatMessageResponse;
import com.fidiacorp.credicasa.application.dto.response.ConversationExportResponse;
import com.fidiacorp.credicasa.application.dto.response.UserBlockResponse;
import com.fidiacorp.credicasa.domain.model.Account;
import com.fidiacorp.credicasa.domain.model.ChatMessage;
import com.fidiacorp.credicasa.domain.model.ConversationTranscript;
import com.fidiacorp.credicasa.domain.model.UserBlock;
import com.fidiacorp.credicasa.domain.ports.out.AccountRepositoryPort;
import com.fidiacorp.credicasa.domain.ports.out.ChatMessageRepositoryPort;
import com.fidiacorp.credicasa.domain.ports.out.UserBlockRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MessagingControlService {

    private final UserBlockRepositoryPort blockRepository;
    private final ChatMessageRepositoryPort chatRepository;
    private final AccountRepositoryPort accountRepository;

    public MessagingControlService(UserBlockRepositoryPort blockRepository,
                                  ChatMessageRepositoryPort chatRepository,
                                  AccountRepositoryPort accountRepository) {
        this.blockRepository = blockRepository;
        this.chatRepository = chatRepository;
        this.accountRepository = accountRepository;
    }

    public UserBlockResponse blockUser(UUID blockerId, UUID targetUserId, BlockUserRequest request) {
        if (blockerId.equals(targetUserId)) {
            throw new IllegalArgumentException("No puedes bloquear tu propia cuenta de usuario.");
        }

        accountRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("El usuario a bloquear no existe."));

        if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, targetUserId)) {
            return new UserBlockResponse(
                    null,
                    blockerId,
                    targetUserId,
                    request != null ? request.reason() : null,
                    LocalDateTime.now(),
                    "El usuario ya se encuentra bloqueado."
            );
        }

        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason()
                : "Usuario bloqueado por privacidad.";

        UserBlock block = new UserBlock(UUID.randomUUID(), blockerId, targetUserId, reason, LocalDateTime.now());
        UserBlock saved = blockRepository.save(block);

        return new UserBlockResponse(
                saved.getId(),
                saved.getBlockerId(),
                saved.getBlockedId(),
                saved.getReason(),
                saved.getCreatedAt(),
                "Usuario bloqueado exitosamente. Ya no podrá enviarte mensajes ni visualizar tu actividad directa."
        );
    }

    public void unblockUser(UUID blockerId, UUID targetUserId) {
        blockRepository.deleteByBlockerIdAndBlockedId(blockerId, targetUserId);
    }

    @Transactional(readOnly = true)
    public List<UserBlockResponse> getBlockedUsers(UUID blockerId) {
        return blockRepository.findByBlockerId(blockerId).stream()
                .map(b -> new UserBlockResponse(
                        b.getId(),
                        b.getBlockerId(),
                        b.getBlockedId(),
                        b.getReason(),
                        b.getCreatedAt(),
                        "Bloqueo activo."
                ))
                .toList();
    }

    public ChatMessageResponse sendMessage(UUID senderId, SendMessageRequest request) {
        if (blockRepository.isBlockedBetween(senderId, request.recipientId())) {
            throw new IllegalStateException("La comunicación está restringida: existe un bloqueo activo entre los usuarios.");
        }

        Account sender = accountRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta de remitente no encontrada."));

        String senderName = sender.firstName() + " " + sender.lastName();

        ChatMessage msg = new ChatMessage(
                UUID.randomUUID(),
                request.conversationId(),
                senderId,
                request.recipientId(),
                senderName,
                request.content(),
                LocalDateTime.now()
        );

        ChatMessage saved = chatRepository.save(msg);
        return new ChatMessageResponse(
                saved.getId(),
                saved.getConversationId(),
                saved.getSenderId(),
                saved.getRecipientId(),
                saved.getSenderName(),
                saved.getContent(),
                saved.getSentAt()
        );
    }

    @Transactional(readOnly = true)
    public ConversationExportResponse exportConversation(UUID requesterId, UUID conversationId) {
        List<ChatMessage> messages = chatRepository.findByConversationId(conversationId);
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("La conversación no contiene mensajes o no fue encontrada.");
        }

        boolean hasAccess = messages.stream()
                .anyMatch(m -> m.getSenderId().equals(requesterId) || m.getRecipientId().equals(requesterId));

        if (!hasAccess) {
            throw new SecurityException("No tienes autorización para acceder o exportar esta conversación.");
        }

        ConversationTranscript transcript = new ConversationTranscript(conversationId, messages, LocalDateTime.now());

        List<ChatMessageResponse> dtos = messages.stream()
                .map(m -> new ChatMessageResponse(
                        m.getId(),
                        m.getConversationId(),
                        m.getSenderId(),
                        m.getRecipientId(),
                        m.getSenderName(),
                        m.getContent(),
                        m.getSentAt()
                ))
                .toList();

        return new ConversationExportResponse(
                conversationId,
                messages.size(),
                transcript.getGeneratedAt(),
                dtos,
                transcript.toFormattedText()
        );
    }
}
