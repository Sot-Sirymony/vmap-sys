package com.visionmapping.service;

import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityAudit;
import com.visionmapping.security.Roles;
import com.visionmapping.dto.request.UiTextOverrideRequest;
import com.visionmapping.dto.response.UiTextOverrideResponse;
import com.visionmapping.entity.UiTextOverride;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.UiTextOverrideRepository;
import com.visionmapping.service.support.EntityLookup;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-20: changing interface text without a code change. Like module content
 * (FR-78), the text is shared by every account on this server, so only an admin
 * edits it. Anyone may read it: the sign-in page needs it before sign-in.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class UiTextService {

    private static final Set<String> LANGUAGES = Set.of("en", "km");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\w+}");

    private final EntityLookup lookup;
    private final UiTextOverrideRepository repository;

    @Transactional(readOnly = true)
    public Map<String, String> overridesFor(String language) {
        requireLanguage(language);
        Map<String, String> texts = new LinkedHashMap<>();
        repository.findByLanguageOrderBySourceTextAsc(language)
                .forEach(override -> texts.put(override.getSourceText(), override.getText()));
        return texts;
    }

    @Transactional(readOnly = true)
    public List<UiTextOverrideResponse> list(String language) {
        requireAdmin();
        requireLanguage(language);
        return repository.findByLanguageOrderBySourceTextAsc(language).stream().map(UiTextService::toResponse).toList();
    }

    @SecurityAudit(value = SecurityEventType.INTERFACE_TEXT_CHANGED, target = "ui_text")
    public UiTextOverrideResponse save(UiTextOverrideRequest request) {
        requireAdmin();
        requireLanguage(request.language());
        String text = request.text().trim();
        // A value such as {count} is filled in by the page; losing one would leave a gap in the sentence.
        if (!placeholders(request.sourceText()).equals(placeholders(text))) {
            throw new BusinessRuleException("Keep every placeholder from the original, such as {count}: "
                    + String.join(", ", placeholders(request.sourceText())) + ".");
        }
        UiTextOverride override = repository.findByLanguageAndSourceText(request.language(), request.sourceText())
                .orElseGet(() -> UiTextOverride.builder().language(request.language()).sourceText(request.sourceText()).build());
        override.setText(text);
        return toResponse(repository.save(override));
    }

    /** Removing an override restores the app's own text, so it is deleted rather than archived. */
    @SecurityAudit(value = SecurityEventType.INTERFACE_TEXT_CHANGED, target = "ui_text")
    public void reset(Long id) {
        requireAdmin();
        UiTextOverride override = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interface text change not found: " + id));
        repository.delete(override);
    }

    private static Set<String> placeholders(String text) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        return matcher.results().map(MatchResult::group).collect(Collectors.toCollection(TreeSet::new));
    }

    private static UiTextOverrideResponse toResponse(UiTextOverride override) {
        return new UiTextOverrideResponse(override.getId(), override.getLanguage(), override.getSourceText(),
                override.getText(), override.getUpdatedAt());
    }

    private static void requireLanguage(String language) {
        if (!LANGUAGES.contains(language)) {
            throw new BusinessRuleException("That language is not supported.");
        }
    }

    private void requireAdmin() {
        Roles.requireAdmin(lookup.currentUser(), "Only an administrator can edit interface text.");
    }
}
