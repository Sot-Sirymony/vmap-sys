package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.visionmapping.dto.request.AppearancePreferencesRequest;
import com.visionmapping.dto.response.AppearancePreferencesResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.AccentColor;
import com.visionmapping.entity.enums.FontSize;
import com.visionmapping.entity.enums.ThemeMode;
import com.visionmapping.entity.enums.ThemePreset;
import com.visionmapping.entity.enums.UiDensity;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.util.UserScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * FR-39 / BR-33: appearance defaults resolve without nulls, updates are
 * partial, and every operation is bound to the authenticated user — there is no
 * parameter that could reach another user's row.
 */
@ExtendWith(MockitoExtension.class)
class AppearancePreferenceServiceTest {

    @Mock private UserScope userScope;
    @Mock private AppUserRepository appUserRepository;

    private AppearancePreferenceService service;

    @BeforeEach
    void setUp() {
        service = new AppearancePreferenceService(userScope, appUserRepository);
    }

    private AppUser user() {
        return AppUser.builder()
                .id(1L)
                .fullName("Test User")
                .email("test@example.com")
                .passwordHash("hash")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void newUserGetsTheDocumentedDefaults() {
        when(userScope.currentUser()).thenReturn(user());

        AppearancePreferencesResponse response = service.getMyPreferences();

        assertThat(response.themePreset()).isEqualTo(ThemePreset.FLUENT_SYSTEM);
        assertThat(response.themeMode()).isEqualTo(ThemeMode.SYSTEM);
        assertThat(response.themeAccent()).isEqualTo(AccentColor.BLUE);
        assertThat(response.uiDensity()).isEqualTo(UiDensity.COMFORTABLE);
        assertThat(response.fontSize()).isEqualTo(FontSize.MEDIUM);
        assertThat(response.highContrast()).isFalse();
        assertThat(response.reduceMotion()).isFalse();
    }

    /**
     * BR-33: a row that predates this feature, or a user object built in code,
     * still renders — a missing value reads as its default rather than a null.
     */
    @Test
    void missingStoredValuesResolveToDefaultsInsteadOfNulls() {
        AppUser stored = user();
        stored.setThemePreset(null);
        stored.setThemeMode(null);
        stored.setThemeAccent(null);
        stored.setUiDensity(null);
        stored.setFontSize(null);
        when(userScope.currentUser()).thenReturn(stored);

        AppearancePreferencesResponse response = service.getMyPreferences();

        assertThat(response.themePreset()).isEqualTo(ThemePreset.FLUENT_SYSTEM);
        assertThat(response.themeMode()).isEqualTo(ThemeMode.SYSTEM);
        assertThat(response.themeAccent()).isEqualTo(AccentColor.BLUE);
        assertThat(response.uiDensity()).isEqualTo(UiDensity.COMFORTABLE);
        assertThat(response.fontSize()).isEqualTo(FontSize.MEDIUM);
    }

    @Test
    void updateAppliesEveryProvidedField() {
        AppUser stored = user();
        when(userScope.currentUser()).thenReturn(stored);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        AppearancePreferencesResponse response = service.updateMyPreferences(new AppearancePreferencesRequest(
                ThemePreset.MIDNIGHT,
                ThemeMode.DARK,
                AccentColor.PURPLE,
                UiDensity.COMPACT,
                FontSize.LARGE,
                true,
                true));

        assertThat(response.themePreset()).isEqualTo(ThemePreset.MIDNIGHT);
        assertThat(response.themeMode()).isEqualTo(ThemeMode.DARK);
        assertThat(response.themeAccent()).isEqualTo(AccentColor.PURPLE);
        assertThat(response.uiDensity()).isEqualTo(UiDensity.COMPACT);
        assertThat(response.fontSize()).isEqualTo(FontSize.LARGE);
        assertThat(response.highContrast()).isTrue();
        assertThat(response.reduceMotion()).isTrue();
        assertThat(stored.getThemeAccent()).isEqualTo(AccentColor.PURPLE);
    }

    /**
     * The Appearance UI changes one control at a time, so a partial body is the
     * normal case — the untouched fields must survive it.
     */
    @Test
    void updateLeavesOmittedFieldsAlone() {
        AppUser stored = user();
        stored.setThemeAccent(AccentColor.TEAL);
        stored.setFontSize(FontSize.LARGE);
        stored.setHighContrast(true);
        when(userScope.currentUser()).thenReturn(stored);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        AppearancePreferencesResponse response = service.updateMyPreferences(new AppearancePreferencesRequest(
                null, ThemeMode.LIGHT, null, null, null, null, null));

        assertThat(response.themeMode()).isEqualTo(ThemeMode.LIGHT);
        assertThat(response.themeAccent()).isEqualTo(AccentColor.TEAL);
        assertThat(response.fontSize()).isEqualTo(FontSize.LARGE);
        assertThat(response.highContrast()).isTrue();
    }

    /** An all-null body is a no-op, not a reset to defaults. */
    @Test
    void emptyUpdateChangesNothing() {
        AppUser stored = user();
        stored.setThemeAccent(AccentColor.BRASS);
        stored.setUiDensity(UiDensity.COMPACT);
        when(userScope.currentUser()).thenReturn(stored);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        AppearancePreferencesResponse response = service.updateMyPreferences(
                new AppearancePreferencesRequest(null, null, null, null, null, null, null));

        assertThat(response.themeAccent()).isEqualTo(AccentColor.BRASS);
        assertThat(response.uiDensity()).isEqualTo(UiDensity.COMPACT);
    }

    /**
     * FR-39.4: reduce motion can be switched off in the app, which only means
     * "don't add motion suppression here" — the OS preference is honoured
     * separately in CSS (BR-19), so this can never re-enable motion for a user
     * whose OS asked for less.
     */
    @Test
    void reduceMotionCanBeTurnedBackOff() {
        AppUser stored = user();
        stored.setReduceMotion(true);
        when(userScope.currentUser()).thenReturn(stored);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        AppearancePreferencesResponse response = service.updateMyPreferences(
                new AppearancePreferencesRequest(null, null, null, null, null, null, false));

        assertThat(response.reduceMotion()).isFalse();
    }

    /**
     * BR-33: the write path resolves the target from the security context, so
     * another user's row is unreachable — there is no id to pass.
     */
    @Test
    void everyOperationTargetsOnlyTheAuthenticatedUser() {
        AppUser me = user();
        when(userScope.currentUser()).thenReturn(me);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        service.updateMyPreferences(new AppearancePreferencesRequest(
                null, ThemeMode.DARK, null, null, null, null, null));

        verify(appUserRepository).save(me);
        verify(appUserRepository, never()).findById(any());
    }

    /** High contrast is independent of mode (FR-39.3) — it composes, not replaces. */
    @Test
    void highContrastComposesWithLightAndDark() {
        AppUser stored = user();
        when(userScope.currentUser()).thenReturn(stored);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        AppearancePreferencesResponse light = service.updateMyPreferences(
                new AppearancePreferencesRequest(null, ThemeMode.LIGHT, null, null, null, true, null));
        assertThat(light.themeMode()).isEqualTo(ThemeMode.LIGHT);
        assertThat(light.highContrast()).isTrue();

        AppearancePreferencesResponse dark = service.updateMyPreferences(
                new AppearancePreferencesRequest(null, ThemeMode.DARK, null, null, null, null, null));
        assertThat(dark.themeMode()).isEqualTo(ThemeMode.DARK);
        assertThat(dark.highContrast()).isTrue();
    }
}
