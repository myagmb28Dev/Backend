package com.example.pogun.repository.shelterpet;

import com.example.pogun.entity.shelterpet.ShelterPet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
/**
 * 영속성 조회와 저장을 담당하는 ShelterPetRepository이다.
 */

@Repository
public interface ShelterPetRepository extends JpaRepository<ShelterPet, String> {

    default List<ShelterPet> findShelterPets(String region, String breed, String status) {
        String requestedRegion = normalize(region);
        String requestedBreed = normalize(breed);
        String requestedStatus = normalize(status);
        return findAll().stream()
                .filter(shelterPet -> contains(shelterPet.getRegion(), requestedRegion))
                .filter(shelterPet -> contains(shelterPet.getBreed(), requestedBreed))
                .filter(shelterPet -> contains(shelterPet.getStatus(), requestedStatus))
                .toList();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean contains(String value, String keyword) {
        if (keyword == null) {
            return true;
        }
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }
}
