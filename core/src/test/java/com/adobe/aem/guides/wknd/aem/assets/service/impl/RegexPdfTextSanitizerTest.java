package com.adobe.aem.guides.wknd.aem.assets.service.impl;

import com.adobe.aem.guides.wknd.aem.assets.config.PdfTextSanitizerConfig;
import com.adobe.aem.guides.wknd.aem.assets.service.PdfTextSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.*;

class RegexPdfTextSanitizerTest {

    private RegexPdfTextSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new RegexPdfTextSanitizer();
    }

    @Test
    void shouldSkipWhenTextIsEmpty() {
        sanitizer.activate(config(
            new String[]{"^Header$"},
            new String[]{"^Footer$"},
            new String[]{"^Page\\s+\\d+$"},
            new String[]{"^\\d+\\.\\s+.*$"},
            new String[0],
            0.20d
        ));

        PdfTextSanitizer.Result result = sanitizer.sanitize(
            "/content/dam/test/empty.pdf",
            ""
        );

        assertFalse(result.shouldWrite());
        assertEquals("empty-text", result.reason());
        assertEquals("", result.sanitizedText());
    }

    @Test
    void shouldStripHeaderFooterAndPageNumbers() {
        sanitizer.activate(config(
            new String[]{"^ACME Quarterly Report$"},
            new String[]{"^Confidential$"},
            new String[]{"^Page\\s+\\d+\\s+of\\s+\\d+$"},
            new String[0],
            new String[0],
            0.20d
        ));

        String original = String.join("\n",
            "ACME Quarterly Report",
            "Introduction to the body text",
            "More useful searchable content",
            "Page 1 of 12",
            "Confidential",
            "",
            "ACME Quarterly Report",
            "Second page body content",
            "Additional paragraph text",
            "Page 2 of 12",
            "Confidential"
        );

        PdfTextSanitizer.Result result = sanitizer.sanitize(
            "/content/dam/test/report.pdf",
            original
        );

        assertTrue(result.shouldWrite());
        assertEquals("sanitized", result.reason());

        String sanitized = result.sanitizedText();
        assertFalse(sanitized.contains("ACME Quarterly Report"));
        assertFalse(sanitized.contains("Confidential"));
        assertFalse(sanitized.contains("Page 1 of 12"));
        assertFalse(sanitized.contains("Page 2 of 12"));

        assertTrue(sanitized.contains("Introduction to the body text"));
        assertTrue(sanitized.contains("More useful searchable content"));
        assertTrue(sanitized.contains("Second page body content"));
        assertTrue(sanitized.contains("Additional paragraph text"));
    }

    @Test
    void shouldStripSimpleFootnoteLines() {
        sanitizer.activate(config(
            new String[0],
            new String[0],
            new String[0],
            new String[]{
                "^\\d+\\.\\s+.*$",
                "^\\*\\s+.*$"
            },
            new String[0],
            0.20d
        ));

        String original = String.join("\n",
            "Main paragraph content that should remain searchable.",
            "More business text in the document body.",
            "1. This is a footnote that should be removed.",
            "* Another footnote-style line that should be removed."
        );

        PdfTextSanitizer.Result result = sanitizer.sanitize(
            "/content/dam/test/footnotes.pdf",
            original
        );

        assertTrue(result.shouldWrite());

        String sanitized = result.sanitizedText();
        assertTrue(sanitized.contains("Main paragraph content that should remain searchable."));
        assertTrue(sanitized.contains("More business text in the document body."));
        assertFalse(sanitized.contains("1. This is a footnote"));
        assertFalse(sanitized.contains("* Another footnote-style line"));
    }

    @Test
    void shouldSkipWhenNoChangeOccurs() {
        sanitizer.activate(config(
            new String[]{"^HeaderThatDoesNotExist$"},
            new String[]{"^FooterThatDoesNotExist$"},
            new String[]{"^Page\\s+999$"},
            new String[]{"^NeverMatches$"},
            new String[0],
            0.20d
        ));

        String original = String.join("\n",
            "Only body text remains here.",
            "No configured regex matches these lines."
        );

        PdfTextSanitizer.Result result = sanitizer.sanitize(
            "/content/dam/test/no-change.pdf",
            original
        );

        assertFalse(result.shouldWrite());
        assertEquals("no-change", result.reason());
        assertEquals(original, result.sanitizedText());
    }

    @Test
    void shouldSkipWhenSanitizedContentFallsBelowMinimumRetainedRatio() {
        sanitizer.activate(config(
            new String[]{
                "^Header$",
                "^Keep nothing useful$",
                "^Body line 1$",
                "^Body line 2$",
                "^Body line 3$"
            },
            new String[]{"^Footer$"},
            new String[]{"^Page\\s+\\d+$"},
            new String[0],
            new String[0],
            0.90d
        ));

        String original = String.join("\n",
            "Header",
            "Body line 1",
            "Body line 2",
            "Body line 3",
            "Page 1",
            "Footer",
            "Tiny"
        );

        PdfTextSanitizer.Result result = sanitizer.sanitize(
            "/content/dam/test/ratio.pdf",
            original
        );

        assertFalse(result.shouldWrite());
        assertEquals("below-min-retained-ratio", result.reason());
        assertEquals(original, result.sanitizedText());
    }

    @Test
    void shouldSkipWhenSanitizedTextWouldBecomeEmpty() {
        sanitizer.activate(config(
            new String[]{"^Header$"},
            new String[]{"^Footer$"},
            new String[]{"^Page\\s+\\d+$"},
            new String[]{"^\\d+\\.\\s+.*$"},
            new String[0],
            0.01d
        ));

        String original = String.join("\n",
            "Header",
            "Page 1",
            "Footer",
            "1. Footnote"
        );

        PdfTextSanitizer.Result result = sanitizer.sanitize(
            "/content/dam/test/empty-after-sanitize.pdf",
            original
        );

        assertFalse(result.shouldWrite());
        assertEquals("sanitized-empty", result.reason());
        assertEquals(original, result.sanitizedText());
    }

    @Test
    void shouldCollapseExcessBlankLinesAfterRemoval() {
        sanitizer.activate(config(
            new String[]{"^Header$"},
            new String[]{"^Footer$"},
            new String[0],
            new String[0],
            new String[0],
            0.20d
        ));

        String original = String.join("\n",
            "Header",
            "",
            "Body line 1",
            "",
            "",
            "Footer",
            "",
            "Body line 2"
        );

        PdfTextSanitizer.Result result = sanitizer.sanitize(
            "/content/dam/test/blank-lines.pdf",
            original
        );

        assertTrue(result.shouldWrite());

        String sanitized = result.sanitizedText();
        assertFalse(sanitized.contains("Header"));
        assertFalse(sanitized.contains("Footer"));
        assertFalse(sanitized.contains("\n\n\n"));
        assertTrue(sanitized.contains("Body line 1"));
        assertTrue(sanitized.contains("Body line 2"));
    }

    @Test
    void shouldRemoveSandiskLegalBlock() {
        sanitizer.activate(config(
            new String[0],
            new String[0],
            new String[0],
            new String[0],
            new String[] {
                "SANDISK,\\s+the\\s+SANDISK\\s+logo,\\s+SANDISK\\s+Optimus,\\s+and\\s+nCache\\s+are\\s+registered\\s+trademarks\\s+or\\s+trademarks\\s+of\\s+Sandisk\\s+Corporation\\s+or\\s+its\\s+affiliates\\s+in\\s+the\\s+U\\.S\\.\\s+and/or\\s+other\\s+countries\\.\\s+Acronis\\s+and\\s+True\\s+Image\\s+are\\s+registered\\s+trademarks\\s+of\\s+Acronis\\s+International\\s+GmbH\\s+in\\s+the\\s+United\\s+States\\s+and\\s+other\\s+countries\\.\\s+Windows,\\s+DirectStorage\\s+and\\s+Microsoft\\s+are\\s+trademarks\\s+of\\s+the\\s+Microsoft\\s+group\\s+of\\s+companies\\.\\s+The\\s+NVMe\\s+word\\s+mark\\s+is\\s+a\\s+trademark\\s+of\\s+NVM\\s+Express,\\s+Inc\\.\\s+PCIe®\\s+is\\s+a\\s+registered\\s+trademark\\s+of\\s+PCI-SIG\\.\\s+All\\s+other\\s+marks\\s+are\\s+the\\s+property\\s+of\\s+their\\s+respective\\s+owners\\.\\s+Product\\s+specifications\\s+subject\\s+to\\s+change\\s+without\\s+notice\\.\\s+Pictures\\s+shown\\s+may\\s+vary\\s+from\\s+actual\\s+products\\.\\s+©\\s+\\d{4}\\s+Sandisk\\s+Corporation\\s+or\\s+its\\s+affiliates\\.\\s+All\\s+rights\\s+reserved\\."
            },
            0.05d
        ));

        String original = String.join("\n",
            "Main body content that should remain searchable.",
            "",
            "SANDISK, the SANDISK logo, SANDISK Optimus, and nCache are registered trademarks or trademarks of Sandisk Corporation or its affiliates in the U.S. and/or other countries.",
            "Acronis and True Image are registered trademarks of Acronis International GmbH in the United States and other countries.",
            "Windows, DirectStorage and Microsoft are trademarks of the Microsoft group of companies.",
            "The NVMe word mark is a trademark of NVM Express, Inc.",
            "PCIe® is a registered trademark of PCI-SIG.",
            "All other marks are the property of their respective owners.",
            "Product specifications subject to change without notice.",
            "Pictures shown may vary from actual products.",
            "© 2025 Sandisk Corporation or its affiliates. All rights reserved."
        );

        PdfTextSanitizer.Result result = sanitizer.sanitize(
            "/content/dam/test/sandisk.pdf",
            original
        );

        assertTrue(result.shouldWrite());
        assertTrue(result.sanitizedText().contains("Main body content that should remain searchable."));
        assertFalse(result.sanitizedText().contains("SANDISK, the SANDISK logo"));
        assertFalse(result.sanitizedText().contains("All rights reserved."));
    }

    @Test
    void shouldRemoveNumberedSandiskFootnoteBlock() {
        sanitizer.activate(config(
            new String[0],
            new String[0],
            new String[0],
            new String[0],
            new String[] {
                "(?:^|\\n)\\s*1\\s+1GB\\s*=\\s*1\\s+billion\\s+bytes\\s+and\\s+1TB\\s*=\\s*1\\s+trillion\\s+bytes\\..*?\\n\\s*11\\s+Backwards\\s+compatible\\s+with\\s+PCIe®\\s*3\\.0\\s*x4,\\s*3\\.0\\s*x2,\\s*3\\.0\\s*x1,\\s*2\\.0\\s*x4,\\s*2\\.0\\s*x2\\s+and\\s+2\\.0\\s*x1\\.?\\s*(?:\\n|$)"
            },
            0.05d
        ));

        String original = String.join("\n",
            "Main product description that should remain searchable.",
            "",
            "1 1GB = 1 billion bytes and 1TB = 1 trillion bytes. Actual user capacity may be less depending on operating environment.",
            "2 1MB/s = 1 million bytes per second. IOPS = input/output operations per second.",
            "3 Average Power – Read and Average Power – Write are measured using IOMeter 1.1.0 during a burst sequential read and write operation.",
            "4 TBW (terabytes written) values calculated using JEDEC client workload (JESD219).",
            "5 Requires motherboard BIOS or third-party software to enable.",
            "6 Available for download at sandisk.com/support",
            "7 Download, installation and administrative privileges required.",
            "8 Physical product dimensions for length and width may vary by ± 0.15mm and product weight may vary by ± 1g.",
            "9 5 years or Max Endurance (TBW) limit, whichever occurs first.",
            "10 Operational temperature is defined as temperature reported by the drive.",
            "11 Backwards compatible with PCIe® 3.0 x4, 3.0 x2, 3.0 x1, 2.0 x4, 2.0 x2 and 2.0 x1."
        );

        PdfTextSanitizer.Result result = sanitizer.sanitize(
            "/content/dam/test/numbered-footnotes.pdf",
            original
        );

        assertTrue(result.shouldWrite());
        assertTrue(result.sanitizedText().contains("Main product description that should remain searchable."));
        assertFalse(result.sanitizedText().contains("1GB = 1 billion bytes"));
        assertFalse(result.sanitizedText().contains("Backwards compatible with PCIe®"));
    }

    private PdfTextSanitizerConfig config(
        String[] headerRegexes,
        String[] footerRegexes,
        String[] pageNumberRegexes,
        String[] footnoteRegexes,
        String[] blockRegexes,
        double minRetainedRatio
    ) {
        return new PdfTextSanitizerConfig() {
            @Override
            public String[] headerRegexes() {
                return headerRegexes;
            }

            @Override
            public String[] footerRegexes() {
                return footerRegexes;
            }

            @Override
            public String[] pageNumberRegexes() {
                return pageNumberRegexes;
            }

            @Override
            public String[] footnoteRegexes() {
                return footnoteRegexes;
            }

            @Override
            public double minRetainedRatio() {
                return minRetainedRatio;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return PdfTextSanitizerConfig.class;
            }

            @Override
            public String[] blockRegexes() {
                return blockRegexes;
            }
        };
    }
}