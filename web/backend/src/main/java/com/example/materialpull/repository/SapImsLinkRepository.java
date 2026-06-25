package com.example.materialpull.repository;

import com.example.materialpull.entity.SapImsLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface SapImsLinkRepository extends JpaRepository<SapImsLinkEntity, Long> {
    Optional<SapImsLinkEntity> findByLinkNo(String linkNo);
    List<SapImsLinkEntity> findBySystemCode(String systemCode);
    Optional<SapImsLinkEntity> findBySystemCodeAndExternalKey(String systemCode, String externalKey);
}
