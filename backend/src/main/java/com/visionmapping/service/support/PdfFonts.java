package com.visionmapping.service.support;

import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.FontSelector;
import com.lowagie.text.pdf.LayoutProcessor;
import lombok.AccessLevel;
import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * NFR-03: lets the plan PDF print Khmer. Khmer vowels and subscripts must be
 * reordered and stacked, which a PDF font alone cannot do, so LayoutProcessor
 * hands that shaping to Java's font engine. Each run of text uses Helvetica
 * where it can and Noto Sans Khmer for the rest.
 */
@Getter
@Component
public class PdfFonts {

    private static final String KHMER_REGULAR = "fonts/NotoSansKhmer-Regular.ttf";
    private static final String KHMER_BOLD = "fonts/NotoSansKhmer-Bold.ttf";

    private final Font title;
    private final Font heading;
    private final Font body;
    private final Font small;
    private final Font smallBold;
    @Getter(AccessLevel.NONE)
    private final BaseFont khmerRegular;
    @Getter(AccessLevel.NONE)
    private final BaseFont khmerBold;

    public PdfFonts() throws Exception {
        // Built with layout off: once enabled, LayoutProcessor tries to load every
        // new font from a file, and Helvetica is built in rather than a file. The
        // switch is global, so a second application context in one JVM finds it on.
        LayoutProcessor.disable();
        // They are real BaseFonts because FontFactory would defer creating them to
        // the first PDF drawn, by which time layout is on.
        BaseFont helvetica = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        BaseFont helveticaBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        title = new Font(helveticaBold, 18);
        heading = new Font(helveticaBold, 12);
        body = new Font(helvetica, 10);
        small = new Font(helvetica, 9);
        smallBold = new Font(helveticaBold, 9);
        LayoutProcessor.enable();
        khmerRegular = BaseFont.createFont(KHMER_REGULAR, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        khmerBold = BaseFont.createFont(KHMER_BOLD, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
    }

    public Phrase phrase(String text, Font latin) {
        boolean bold = latin.getBaseFont().getPostscriptFontName().contains("Bold");
        FontSelector selector = new FontSelector();
        selector.addFont(latin);
        selector.addFont(new Font(bold ? khmerBold : khmerRegular, latin.getSize()));
        return selector.process(text);
    }
}
