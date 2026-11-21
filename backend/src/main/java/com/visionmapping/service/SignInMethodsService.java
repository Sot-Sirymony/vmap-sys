package com.visionmapping.service;

import com.visionmapping.dto.response.SignInMethodsResponse;
import com.visionmapping.dto.response.SignInMethodsResponse.LinkedAccount;
import com.visionmapping.entity.AppUser;
import com.visionmapping.repository.ExternalIdentityRepository;
import com.visionmapping.util.UserScope;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** V14 (GA-FR-26, GA-FR-27): the signed-in person's own sign-in methods. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SignInMethodsService {

    private final UserScope userScope;
    private final ExternalIdentityRepository identityRepository;

    public SignInMethodsResponse current() {
        AppUser user = userScope.currentUser();
        return new SignInMethodsResponse(user.isPasswordSet(), linkedAccounts(user.getId()));
    }

    /** Also part of the account data export (GA-FR-32): never the provider's user ID. */
    public List<LinkedAccount> linkedAccounts(Long userId) {
        return identityRepository.findByUser_IdOrderByLinkedAtAsc(userId).stream()
                .map(identity -> new LinkedAccount(identity.getProvider().name(), identity.getLastEmail(), identity.getLinkedAt()))
                .toList();
    }
}
