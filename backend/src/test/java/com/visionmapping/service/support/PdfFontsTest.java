package com.visionmapping.service.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lowagie.text.pdf.BaseFont;
import org.junit.jupiter.api.Test;

/** BRD 13 EJ-FR-24: a font that cannot load fails with its file name and the cause, not a bare Exception. */
class PdfFontsTest {

    @Test
    void aMissingFontNamesTheFileAndKeepsTheCause() {
        assertThatThrownBy(() -> PdfFonts.font("fonts/Missing-Font.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot load the PDF font fonts/Missing-Font.ttf")
                .hasCauseInstanceOf(Exception.class);
    }
}
