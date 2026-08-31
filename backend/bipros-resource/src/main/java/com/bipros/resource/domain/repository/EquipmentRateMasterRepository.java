package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.rate.EquipmentRateMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EquipmentRateMasterRepository extends JpaRepository<EquipmentRateMaster, UUID> {

  Optional<EquipmentRateMaster> findByEquipmentNameAndMakeAndModel(
      String equipmentName, String make, String model);

  List<EquipmentRateMaster> findByActiveTrue();
}
