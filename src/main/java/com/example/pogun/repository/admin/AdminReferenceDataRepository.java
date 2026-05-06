package com.example.pogun.repository.admin;

import com.example.pogun.entity.admin.AdminReferenceData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminReferenceDataRepository extends JpaRepository<AdminReferenceData, UUID> {
    List<AdminReferenceData> findByDataKindOrderByDataLabelAsc(String dataKind);
    Optional<AdminReferenceData> findByIdAndDataKind(UUID id, String dataKind);
}

