package com.visionmapping.util;

import com.visionmapping.entity.AppUser;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The signed-in user, loaded fresh from the database for each request, so all
 * data access can be scoped to that user (CLAUDE.md Core Business Rule 13).
 */
@Component
@RequiredArgsConstructor
public class UserScope {

    private final AppUserRepository appUserRepository;

    @Transactional(readOnly = true)
    public AppUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new ResourceNotFoundException("Authenticated user not found.");
        }

        return appUserRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
