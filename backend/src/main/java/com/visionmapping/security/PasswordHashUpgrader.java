package com.visionmapping.security;

import com.visionmapping.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V10 (SEC-04-02): upgrades an old password hash at the next successful sign-in.
 *
 * <p>Spring's {@code DaoAuthenticationProvider} calls this after the password
 * matched, when {@code BCryptPasswordEncoder.upgradeEncoding} says the stored
 * hash uses a lower cost than {@code app.security.bcrypt-cost}. The new hash is
 * made from the password just typed, so nobody has to change their password.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordHashUpgrader implements UserDetailsPasswordService {

    private final AppUserRepository appUserRepository;

    @Override
    @Transactional
    public UserDetails updatePassword(UserDetails user, String newPassword) {
        return appUserRepository.findByEmail(user.getUsername())
                .map(account -> {
                    account.setPasswordHash(newPassword);
                    log.info("Upgraded the password hash of user {}", account.getId());
                    return (UserDetails) new AppUserPrincipal(appUserRepository.save(account));
                })
                .orElse(user);
    }
}
