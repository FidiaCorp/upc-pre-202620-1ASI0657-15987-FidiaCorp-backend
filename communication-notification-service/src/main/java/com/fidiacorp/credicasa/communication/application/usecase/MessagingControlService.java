package com.fidiacorp.credicasa.communication.application.usecase;

import com.fidiacorp.credicasa.communication.application.dto.request.BlockUserRequest;
import com.fidiacorp.credicasa.communication.application.dto.request.CreateConversationRequest;
import com.fidiacorp.credicasa.communication.application.dto.request.SendMessageRequest;
import com.fidiacorp.credicasa.communication.application.dto.response.ChatMessageResponse;
import com.fidiacorp.credicasa.communication.application.dto.response.ConversationExportResponse;
import com.fidiacorp.credicasa.communication.application.dto.response.ConversationResponse;
import com.fidiacorp.credicasa.communication.application.dto.response.UserBlockResponse;
import com.fidiacorp.credicasa.communication.domain.model.ChatMessage;
import com.fidiacorp.credicasa.communication.domain.model.Conversation;
import com.fidiacorp.credicasa.communication.domain.model.ConversationNotFoundException;
import com.fidiacorp.credicasa.communication.domain.model.ConversationTranscript;
import com.fidiacorp.credicasa.communication.domain.model.UserBlock;
import com.fidiacorp.credicasa.communication.application.ports.AccountDirectoryPort;
import com.fidiacorp.credicasa.communication.domain.ports.out.ChatMessageRepositoryPort;
import com.fidiacorp.credicasa.communication.domain.ports.out.ConversationRepositoryPort;
import com.fidiacorp.credicasa.communication.application.ports.PropertyDirectoryPort;
import com.fidiacorp.credicasa.communication.domain.ports.out.UserBlockRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * US16/US17/US18. Toda operación sobre mensajes exige membresía estricta en la conversación:
 * inexistente → 404, ajena → 403 (cierra el hijacking/exfiltración de chats privados).
 */
@Service
@Transactional
public class MessagingControlService {
    private static final Logger log = LoggerFactory.getLogger(MessagingControlService.class);

    private final UserBlockRepositoryPort blockRepository;
    private final ChatMessageRepositoryPort chatRepository;
    private final AccountDirectoryPort accountRepository;
    private final ConversationRepositoryPort conversations;
    private final PropertyDirectoryPort properties;
    private final Clock clock;

    public MessagingControlService(UserBlockRepositoryPort blockRepository, ChatMessageRepositoryPort chatRepository,
                                   AccountDirectoryPort accountRepository, ConversationRepositoryPort conversations,
                                   PropertyDirectoryPort properties, Clock clock) {
        this.blockRepository = blockRepository;
        this.chatRepository = chatRepository;
        this.accountRepository = accountRepository;
        this.conversations = conversations;
        this.properties = properties;
        this.clock = clock;
    }

    public UserBlockResponse blockUser(UUID blockerId, UUID targetUserId, BlockUserRequest request) {
        if (blockerId.equals(targetUserId)) throw new IllegalArgumentException("No puedes bloquear tu propia cuenta de usuario.");
        accountRepository.find(targetUserId).orElseThrow(() -> new IllegalArgumentException("El usuario a bloquear no existe."));
        if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, targetUserId)) {
            UserBlock existing = blockRepository.findByBlockerId(blockerId).stream()
                    .filter(b -> b.getBlockedId().equals(targetUserId)).findFirst().orElseThrow();
            return toResponse(existing, "El usuario ya se encuentra bloqueado.");
        }
        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason() : "Usuario bloqueado por privacidad.";
        UserBlock saved = blockRepository.save(new UserBlock(UUID.randomUUID(), blockerId, targetUserId, reason, LocalDateTime.now(clock)));
        return toResponse(saved, "Usuario bloqueado exitosamente. Ya no podrá enviarte mensajes ni visualizar tu actividad directa.");
    }

    public void unblockUser(UUID blockerId, UUID targetUserId) {
        blockRepository.deleteByBlockerIdAndBlockedId(blockerId, targetUserId);
    }

    @Transactional(readOnly = true)
    public List<UserBlockResponse> getBlockedUsers(UUID blockerId) {
        return blockRepository.findByBlockerId(blockerId).stream().map(b -> toResponse(b, "Bloqueo activo.")).toList();
    }

    public ConversationResponse startConversation(UUID requesterId, CreateConversationRequest request) {
        UUID recipientId = request.recipientId();
        if (requesterId.equals(recipientId)) throw new IllegalArgumentException("No puedes iniciar una conversación contigo mismo.");
        accountRepository.find(recipientId).orElseThrow(() -> new IllegalArgumentException("El destinatario no existe."));
        if (request.propertyId() != null) {
            if (!properties.isActive(request.propertyId())) throw new com.fidiacorp.credicasa.shared.error.ResourceNotFoundException("El inmueble no existe o fue eliminado.");
        }
        if (blockRepository.isBlockedBetween(requesterId, recipientId)) {
            throw new IllegalStateException("La comunicación está restringida: existe un bloqueo activo entre los usuarios.");
        }
        Conversation conversation = conversations.findBetween(requesterId, recipientId, request.propertyId())
                .orElseGet(() -> conversations.save(new Conversation(UUID.randomUUID(), request.propertyId(), requesterId,
                        recipientId, clock.instant())));
        return toResponse(conversation, requesterId);
    }

    public boolean conversationExists(UUID requesterId, CreateConversationRequest request) {
        return conversations.findBetween(requesterId, request.recipientId(), request.propertyId()).isPresent();
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(UUID requesterId) {
        return conversations.findByParticipant(requesterId).stream()
                .map(c -> toResponse(c, requesterId))
                .sorted(Comparator.comparing((ConversationResponse c) -> c.lastMessageAt() == null ? LocalDateTime.MIN : c.lastMessageAt())
                        .reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(UUID requesterId, UUID conversationId) {
        requireMembership(requesterId, conversationId);
        return chatRepository.findByConversationId(conversationId).stream().map(this::toResponse).toList();
    }

    public ChatMessageResponse sendMessage(UUID senderId, String senderName, SendMessageRequest request) {
        Conversation conversation = requireMembership(senderId, request.conversationId());
        UUID recipientId = conversation.counterpartOf(senderId);
        if (request.recipientId() != null && !request.recipientId().equals(recipientId)) {
            throw new IllegalArgumentException("recipientId no corresponde a la contraparte de la conversación.");
        }
        if (blockRepository.isBlockedBetween(senderId, recipientId)) {
            throw new IllegalStateException("La comunicación está restringida: existe un bloqueo activo entre los usuarios.");
        }
        // El nombre visible del remitente proviene del JWT validado (sin consultar identity_db).
        ChatMessage saved = chatRepository.save(new ChatMessage(UUID.randomUUID(), conversation.id(), senderId, recipientId,
                senderName, request.content(), LocalDateTime.now(clock)));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ConversationExportResponse exportConversation(UUID requesterId, UUID conversationId) {
        requireMembership(requesterId, conversationId);
        List<ChatMessage> messages = chatRepository.findByConversationId(conversationId);
        ConversationTranscript transcript = new ConversationTranscript(conversationId, messages, LocalDateTime.now());
        return new ConversationExportResponse(conversationId, messages.size(), transcript.getGeneratedAt(),
                messages.stream().map(this::toResponse).toList(), transcript.toFormattedText());
    }

    private Conversation requireMembership(UUID requesterId, UUID conversationId) {
        Conversation conversation = conversations.findById(conversationId).orElseThrow(ConversationNotFoundException::new);
        if (!conversation.isParticipant(requesterId)) {
            log.warn("[SECURITY-AUDIT] Acceso denegado: cuenta {} no participa en la conversación {}", requesterId, conversationId);
            throw new AccessDeniedException("No participas en esta conversación.");
        }
        return conversation;
    }

    private ConversationResponse toResponse(Conversation c, UUID requesterId) {
        UUID counterpart = c.counterpartOf(requesterId);
        String name;
        try {
            name = accountRepository.find(counterpart).map(AccountDirectoryPort.Account::displayName).orElse("Cuenta no disponible");
        } catch (RuntimeException unavailable) {
            name = "Cuenta no disponible"; // degradación: el listado de conversaciones no depende de identity.
        }
        List<ChatMessage> messages = chatRepository.findByConversationId(c.id());
        ChatMessage last = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        String preview = last == null ? null : (last.getContent().length() > 80 ? last.getContent().substring(0, 80) + "…" : last.getContent());
        return new ConversationResponse(c.id(), c.propertyId(), c.participantAId(), c.participantBId(), counterpart, name,
                c.createdAt(), messages.size(), last == null ? null : last.getSentAt(), preview);
    }

    private ChatMessageResponse toResponse(ChatMessage m) {
        return new ChatMessageResponse(m.getId(), m.getConversationId(), m.getSenderId(), m.getRecipientId(), m.getSenderName(),
                m.getContent(), m.getSentAt());
    }

    private UserBlockResponse toResponse(UserBlock b, String message) {
        return new UserBlockResponse(b.getId(), b.getBlockerId(), b.getBlockedId(), b.getReason(), b.getCreatedAt(), message);
    }
}
