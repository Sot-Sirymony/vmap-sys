package com.visionmapping.service;

import com.visionmapping.dto.request.AppearancePreferencesRequest;
import com.visionmapping.dto.response.AppearancePreferencesResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.AccentColor;
import com.visionmapping.entity.enums.BackgroundTone;
import com.visionmapping.entity.enums.FontFamily;
import com.visionmapping.entity.enums.FontSize;
import com.visionmapping.entity.enums.InterfaceStyle;
import com.visionmapping.entity.enums.ThemeMode;
import com.visionmapping.entity.enums.ThemePreset;
import com.visionmapping.entity.enums.UiDensity;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.util.UserScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-39: reads and writes the authenticated user's appearance preferences.
 *
 * <p>BR-33 shapes this class in two ways. Scoping is absolute: every operation
 * goes through {@link UserScope#currentUser()}, so there is no id parameter a
 * caller could point at somebody else's row. And reads are lenient: a missing
 * value resolves to the documented default instead of surfacing a null, so a
 * row written before this feature existed still renders.
 *
 * <p>Writes are partial by design — the Appearance UI changes one control at a
 * time, and an omitted field keeps its stored value.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AppearancePreferenceService {

    private final UserScope userScope;
    private final AppUserRepository appUserRepository;

    @Transactional(readOnly = true)
    public AppearancePreferencesResponse getMyPreferences() {
        return toResponse(userScope.currentUser());
    }

    /**
     * Applies the supplied changes to the current user. Any field left null in
     * the request is untouched.
     */
    public AppearancePreferencesResponse updateMyPreferences(AppearancePreferencesRequest request) {
        AppUser user = userScope.currentUser();

        if (request.themePreset() != null) {
            user.setThemePreset(request.themePreset());
        }
        if (request.themeMode() != null) {
            user.setThemeMode(request.themeMode());
        }
        if (request.themeAccent() != null) {
            user.setThemeAccent(request.themeAccent());
        }
        if (request.uiDensity() != null) {
            user.setUiDensity(request.uiDensity());
        }
        if (request.fontSize() != null) {
            user.setFontSize(request.fontSize());
        }
        if (request.fontFamily() != null) {
            user.setFontFamily(request.fontFamily());
        }
        if (request.backgroundTone() != null) {
            user.setBackgroundTone(request.backgroundTone());
        }
        if (request.interfaceStyle() != null) {
            user.setInterfaceStyle(request.interfaceStyle());
        }
        if (request.highContrast() != null) {
            user.setHighContrast(request.highContrast());
        }
        if (request.reduceMotion() != null) {
            user.setReduceMotion(request.reduceMotion());
        }

        return toResponse(appUserRepository.save(user));
    }

    /**
     * FR-39.6: also used to embed the preferences in the login/register
     * response, so the first paint after signing in is already correct.
     *
     * <p>The defaults applied here are the same ones the migration wrote as
     * column defaults; they exist as a second line of defence for a user object
     * built in code (a test fixture, say) rather than loaded from the database.
     */
    public static AppearancePreferencesResponse toResponse(AppUser user) {
        return new AppearancePreferencesResponse(
                user.getThemePreset() == null ? ThemePreset.FLUENT_SYSTEM : user.getThemePreset(),
                user.getThemeMode() == null ? ThemeMode.SYSTEM : user.getThemeMode(),
                user.getThemeAccent() == null ? AccentColor.BLUE : user.getThemeAccent(),
                user.getUiDensity() == null ? UiDensity.COMFORTABLE : user.getUiDensity(),
                user.getFontSize() == null ? FontSize.MEDIUM : user.getFontSize(),
                user.getFontFamily() == null ? FontFamily.SYSTEM : user.getFontFamily(),
                user.getBackgroundTone() == null ? BackgroundTone.NEUTRAL : user.getBackgroundTone(),
                user.getInterfaceStyle() == null ? InterfaceStyle.CLASSIC : user.getInterfaceStyle(),
                user.isHighContrast(),
                user.isReduceMotion()
        );
    }
}
