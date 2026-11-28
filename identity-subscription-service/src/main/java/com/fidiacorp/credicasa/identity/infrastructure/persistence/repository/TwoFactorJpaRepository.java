package com.fidiacorp.credicasa.identity.infrastructure.persistence.repository;
import com.fidiacorp.credicasa.identity.infrastructure.persistence.entity.TwoFactorJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface TwoFactorJpaRepository extends JpaRepository<TwoFactorJpaEntity,UUID> {}
