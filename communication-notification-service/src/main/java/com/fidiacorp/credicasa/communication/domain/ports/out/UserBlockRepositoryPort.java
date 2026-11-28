package com.fidiacorp.credicasa.communication.domain.ports.out;

import com.fidiacorp.credicasa.communication.domain.model.UserBlock;

import java.util.List;
import java.util.UUID;

public interface UserBlockRepositoryPort {
    UserBlock save(UserBlock userBlock);
    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
    boolean isBlockedBetween(UUID userA, UUID userB);
    void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
    List<UserBlock> findByBlockerId(UUID blockerId);
}
