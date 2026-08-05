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
import com.visionmapping.entity.enums.BackgroundTone;
import com.visionmapping.entity.enums.FontFamily;
import com.visionmapping.entity.enums.FontSize;
import com.visionmapping.entity.enums.InterfaceStyle;
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

    /**
     * Builds a partial request by naming only the fields a test cares about.
     *
     * <p>The alternative — constructing the record positionally — means a row of
     * eight nulls at every call site, which says nothing about intent and has to
     * be re-counted by hand each time a preference is added. This keeps the tests
     * about behaviour rather than about argument order.
     */
    private static final class Changes {
        private ThemePreset preset;
        private ThemeMode mode;
        private AccentColor accent;
        private UiDensity density;
        private FontSize fontSize;
        private FontFamily fontFamily;
        private BackgroundTone tone;
        private InterfaceStyle style;
        private Boolean highContrast;
        private Boolean reduceMotion;

        static Changes none() {
            return new Changes();
        }

        Changes preset(ThemePreset value) { this.preset = value; return this; }
        Changes mode(ThemeMode value) { this.mode = value; return this; }
        Changes accent(AccentColor value) { this.accent = value; return this; }
        Changes density(UiDensity value) { this.density = value; return this; }
        Changes fontSize(FontSize value) { this.fontSize = value; return this; }
        Changes fontFamily(FontFamily value) { this.fontFamily = value; return this; }
        Changes tone(BackgroundTone value) { this.tone = value; return this; }
        Changes style(InterfaceStyle value) { this.style = value; return this; }
        Changes highContrast(Boolean value) { this.highContrast = value; return this; }
        Changes reduceMotion(Boolean value) { this.reduceMotion = value; return this; }

        AppearancePreferencesRequest build() {
            return new AppearancePreferencesRequest(
                    preset, mode, accent, density, fontSize, fontFamily, tone, style, highContrast, reduceMotion);
        }
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
        // FR-40 AC-3: NEUTRAL is defined as the surfaces that shipped before
        // FR-40, so defaulting to it is a no-op for anyone who never opens the
        // control.
        assertThat(response.backgroundTone()).isEqualTo(BackgroundTone.NEUTRAL);
        // FR-42: the platform's own UI face, so the default downloads nothing.
        assertThat(response.fontFamily()).isEqualTo(FontFamily.SYSTEM);
        // FR-48: CLASSIC is the Fluent treatment that shipped before the style
        // control existed, so defaulting to it changes nothing for anyone.
        assertThat(response.interfaceStyle()).isEqualTo(InterfaceStyle.CLASSIC);
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
        stored.setBackgroundTone(null);
        stored.setFontFamily(null);
        stored.setInterfaceStyle(null);
        when(userScope.currentUser()).thenReturn(stored);

        AppearancePreferencesResponse response = service.getMyPreferences();

        assertThat(response.themePreset()).isEqualTo(ThemePreset.FLUENT_SYSTEM);
        assertThat(response.themeMode()).isEqualTo(ThemeMode.SYSTEM);
        assertThat(response.themeAccent()).isEqualTo(AccentColor.BLUE);
        assertThat(response.uiDensity()).isEqualTo(UiDensity.COMFORTABLE);
        assertThat(response.fontSize()).isEqualTo(FontSize.MEDIUM);
        assertThat(response.backgroundTone()).isEqualTo(BackgroundTone.NEUTRAL);
        assertThat(response.fontFamily()).isEqualTo(FontFamily.SYSTEM);
        assertThat(response.interfaceStyle()).isEqualTo(InterfaceStyle.CLASSIC);
    }

    @Test
    void updateAppliesEveryProvidedField() {
        AppUser stored = user();
        when(userScope.currentUser()).thenReturn(stored);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        AppearancePreferencesResponse response = service.updateMyPreferences(Changes.none()
                .preset(ThemePreset.MIDNIGHT)
                .mode(ThemeMode.DARK)
                .accent(AccentColor.PURPLE)
                .density(UiDensity.COMPACT)
                .fontSize(FontSize.LARGE)
                .tone(BackgroundTone.WARM)
                .fontFamily(FontFamily.INTER)
                .style(InterfaceStyle.MODERN)
                .highContrast(true)
                .reduceMotion(true)
                .build());

        assertThat(response.themePreset()).isEqualTo(ThemePreset.MIDNIGHT);
        assertThat(response.themeMode()).isEqualTo(ThemeMode.DARK);
        assertThat(response.themeAccent()).isEqualTo(AccentColor.PURPLE);
        assertThat(response.uiDensity()).isEqualTo(UiDensity.COMPACT);
        assertThat(response.fontSize()).isEqualTo(FontSize.LARGE);
        assertThat(response.backgroundTone()).isEqualTo(BackgroundTone.WARM);
        assertThat(response.fontFamily()).isEqualTo(FontFamily.INTER);
        assertThat(response.interfaceStyle()).isEqualTo(InterfaceStyle.MODERN);
        assertThat(response.highContrast()).isTrue();
        assertThat(response.reduceMotion()).isTrue();
        assertThat(stored.getThemeAccent()).isEqualTo(AccentColor.PURPLE);
    }

    /**
     * FR-40.4: tone is an independent axis, so changing it must not disturb the
     * preset, mode, or accent the user already chose.
     */
    @Test
    void changingToneLeavesTheRestOfTheThemeAlone() {
        AppUser stored = user();
        stored.setThemePreset(ThemePreset.OCEAN);
        stored.setThemeMode(ThemeMode.LIGHT);
        stored.setThemeAccent(AccentColor.TEAL);
        when(userScope.currentUser()).thenReturn(stored);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        AppearancePreferencesResponse response =
                service.updateMyPreferences(Changes.none().tone(BackgroundTone.SOFT).build());

        assertThat(response.backgroundTone()).isEqualTo(BackgroundTone.SOFT);
        assertThat(response.themePreset()).isEqualTo(ThemePreset.OCEAN);
        assertThat(response.themeMode()).isEqualTo(ThemeMode.LIGHT);
        assertThat(response.themeAccent()).isEqualTo(AccentColor.TEAL);
    }

    /**
     * FR-42: the typeface is its own axis — picking one must not disturb the
     * size ramp, the tone, or anything else the user already chose.
     */
    @Test
    void changingFontFamilyLeavesTheRestAlone() {
        AppUser stored = user();
        stored.setFontSize(FontSize.LARGE);
        stored.setBackgroundTone(BackgroundTone.COOL);
        when(userScope.currentUser()).thenReturn(stored);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        AppearancePreferencesResponse response =
                service.updateMyPreferences(Changes.none().fontFamily(FontFamily.DM_SANS).build());

        assertThat(response.fontFamily()).isEqualTo(FontFamily.DM_SANS);
        assertThat(response.fontSize()).isEqualTo(FontSize.LARGE);
        assertThat(response.backgroundTone()).isEqualTo(BackgroundTone.COOL);
    }

    /**
     * FR-43: Vermilion and Violet were offered by the picker for two releases
     * without existing here, so choosing either applied locally and then failed
     * to save. This asserts every accent the theme offers is storable; the
     * frontend's `accent-wire.test.ts` is what compares the two lists.
     */
    @Test
    void storesEveryAccentTheThemeOffers() {
        for (AccentColor accent : AccentColor.values()) {
            AppUser stored = user();
            when(userScope.currentUser()).thenReturn(stored);
            when(appUserRepository.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

            AppearancePreferencesResponse response =
                    service.updateMyPreferences(Changes.none().accent(accent).build());

            assertThat(response.themeAccent()).isEqualTo(accent);
        }
    }

    /**
     * FR-48.2: the style is its own axis too. It decides shape and chrome, never
     * colour, so picking one must leave the mode, accent, and tone exactly as
     * they were — the same guarantee tone and font each get above.
     */
    @Test
    void changingInterfaceStyleLeavesTheColoursAlone() {
        AppUser stored = user();
        stored.setThemeMode(ThemeMode.DARK);
        stored.setThemeAccent(AccentColor.TEAL);
        stored.setBackgroundTone(BackgroundTone.WARM);
        when(userScope.currentUser()).thenReturn(stored);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        AppearancePreferencesResponse response =
                service.updateMyPreferences(Changes.none().style(InterfaceStyle.MODERN).build());

        assertThat(response.interfaceStyle()).isEqualTo(InterfaceStyle.MODERN);
        assertThat(response.themeMode()).isEqualTo(ThemeMode.DARK);
        assertThat(response.themeAccent()).isEqualTo(AccentColor.TEAL);
        assertThat(response.backgroundTone()).isEqualTo(BackgroundTone.WARM);
    }

    /**
     * FR-40.5: high contrast overrides the tone when rendering, but must not
     * erase it — turning high contrast off has to restore the chosen tone
     * exactly, which only works if the stored value survived untouched.
     */
    @Test
    void highContrastDoesNotOverwriteTheStoredTone() {
        AppUser stored = user();
        stored.setBackgroundTone(BackgroundTone.WARM);
        when(userScope.currentUser()).thenReturn(stored);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        AppearancePreferencesResponse on =
                service.updateMyPreferences(Changes.none().highContrast(true).build());
        assertThat(on.backgroundTone()).isEqualTo(BackgroundTone.WARM);

        AppearancePreferencesResponse off =
                service.updateMyPreferences(Changes.none().highContrast(false).build());
        assertThat(off.backgroundTone()).isEqualTo(BackgroundTone.WARM);
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

        AppearancePreferencesResponse response = service.updateMyPreferences(Changes.none().mode(ThemeMode.LIGHT).build());

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
                Changes.none().build());

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
                Changes.none().reduceMotion(false).build());

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

        service.updateMyPreferences(Changes.none().mode(ThemeMode.DARK).build());

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
                Changes.none().mode(ThemeMode.LIGHT).highContrast(true).build());
        assertThat(light.themeMode()).isEqualTo(ThemeMode.LIGHT);
        assertThat(light.highContrast()).isTrue();

        AppearancePreferencesResponse dark = service.updateMyPreferences(
                Changes.none().mode(ThemeMode.DARK).build());
        assertThat(dark.themeMode()).isEqualTo(ThemeMode.DARK);
        assertThat(dark.highContrast()).isTrue();
    }
}
