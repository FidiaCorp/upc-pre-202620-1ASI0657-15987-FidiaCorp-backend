package com.fidiacorp.credicasa.communication.infrastructure.persistence;

import com.fidiacorp.credicasa.communication.domain.model.UserBlock;
import com.fidiacorp.credicasa.communication.domain.ports.out.UserBlockRepositoryPort;
import com.fidiacorp.credicasa.communication.infrastructure.persistence.entity.UserBlockJpaEntity;
import com.fidiacorp.credicasa.communication.infrastructure.persistence.repository.UserBlockJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class UserBlockRepositoryAdapter implements UserBlockRepositoryPort {

    private final UserBlockJpaRepository repository;

    public UserBlockRepositoryAdapter(UserBlockJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserBlock save(UserBlock userBlock) {
        UserBlockJpaEntity e = new UserBlockJpaEntity();
        e.id = userBlock.getId();
        e.blockerId = userBlock.getBlockerId();
        e.blockedId = userBlock.getBlockedId();
        e.reason = userBlock.getReason();
        e.createdAt = userBlock.getCreatedAt();
        UserBlockJpaEntity saved = repository.save(e);
        return new UserBlock(saved.id, saved.blockerId, saved.blockedId, saved.reason, saved.createdAt);
    }

    @Override
    public boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId) {
        return repository.existsByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    @Override
    public boolean isBlockedBetween(UUID userA, UUID userB) {
        return repository.isBlockedBetween(userA, userB);
    }

    @Override
    public void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId) {
        repository.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    @Override
    public List<UserBlock> findByBlockerId(UUID blockerId) {
        return repository.findByBlockerIdOrderByCreatedAtDesc(blockerId).stream()
                .map(e -> new UserBlock(e.id, e.blockerId, e.blockedId, e.reason, e.createdAt))
                .toList();
    }
}
