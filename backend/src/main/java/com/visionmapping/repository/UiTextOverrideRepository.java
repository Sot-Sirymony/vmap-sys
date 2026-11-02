package com.visionmapping.repository;

import com.visionmapping.entity.UiTextOverride;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UiTextOverrideRepository extends JpaRepository<UiTextOverride, Long> {

    List<UiTextOverride> findByLanguageOrderBySourceTextAsc(String language);

    Optional<UiTextOverride> findByLanguageAndSourceText(String language, String sourceText);
}
