package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.manpower.ManpowerAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ManpowerAllocationRepository extends JpaRepository<ManpowerAllocation, UUID> {}
