package com.bipros.security.application.service;

import com.bipros.security.application.dto.UserAccessResponse;
import com.bipros.security.domain.model.IcpmsModule;
import com.bipros.security.domain.model.ModuleAccessLevel;
import com.bipros.security.domain.model.UserCorridorScope;
import com.bipros.security.domain.repository.UserCorridorScopeRepository;
import com.bipros.security.domain.repository.UserModuleAccessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserAccessService {

    private final UserModuleAccessRepository moduleAccessRepository;
    private final UserCorridorScopeRepository corridorScopeRepository;

    public UserAccessResponse getAccess(UUID userId) {
        Map<IcpmsModule, ModuleAccessLevel> moduleMap = new EnumMap<>(IcpmsModule.class);
        moduleAccessRepository.findByUserId(userId)
                .forEach(uma -> moduleMap.put(uma.getModule(), uma.getAccessLevel()));

        List<UserCorridorScope> scopes = corridorScopeRepository.findByUserId(userId);
        boolean allCorridors = scopes.stream().anyMatch(s -> s.getWbsNodeId() == null);
        List<UUID> corridorIds = scopes.stream()
                .map(UserCorridorScope::getWbsNodeId)
                .filter(id -> id != null)
                .collect(Collectors.toList());

        return new UserAccessResponse(userId, moduleMap, corridorIds, allCorridors);
    }
}
