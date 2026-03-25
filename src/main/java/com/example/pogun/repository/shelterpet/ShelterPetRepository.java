package com.example.pogun.repository.shelterpet;

import com.example.pogun.entity.shelterpet.ShelterPet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
/**
 * 영속성 조회와 저장을 담당하는 ShelterPetRepository이다.
 */

@Repository
public interface ShelterPetRepository extends JpaRepository<ShelterPet, String> {

    @Query("""
            SELECT s FROM ShelterPet s
            WHERE (:region IS NULL OR s.region = :region)
              AND (:breed IS NULL OR s.breed = :breed)
              AND (:status IS NULL OR s.status = :status)
            """)
    List<ShelterPet> findShelterPets(
            @Param("region") String region,
            @Param("breed") String breed,
            @Param("status") String status
    );
}