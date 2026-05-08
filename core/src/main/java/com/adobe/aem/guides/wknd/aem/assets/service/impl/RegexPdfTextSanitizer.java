package com.adobe.aem.guides.wknd.aem.assets.service.impl;

import com.adobe.aem.guides.wknd.aem.assets.config.PdfTextSanitizerConfig;
import com.adobe.aem.guides.wknd.aem.assets.service.PdfTextSanitizer;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component(service = PdfTextSanitizer.class)
public class RegexPdfTextSanitizer implements PdfTextSanitizer {

    private volatile ConfigState configState;

    @Activate
    @Modified
    protected void activate(PdfTextSanitizerConfig config) {
        this.configState = new ConfigState(config);
    }

    @Override
    public Result sanitize(String assetPath, String originalText) {
        if (originalText == null || originalText.isBlank()) {
            return Result.skip("empty-text", originalText);
        }

        String sanitized = normalize(originalText);

        // Remove exact / multi-line legal blocks first
        sanitized = removeMatchingBlocks(sanitized, configState.blockPatterns);

        // Then remove line-oriented patterns
        sanitized = removeMatchingLines(sanitized, configState.headerPatterns);
        sanitized = removeMatchingLines(sanitized, configState.footerPatterns);
        sanitized = removeMatchingLines(sanitized, configState.pageNumberPatterns);
        sanitized = removeMatchingLines(sanitized, configState.footnotePatterns);

        sanitized = collapseBlankLines(sanitized).trim();

        int originalLength = originalText.length();
        int sanitizedLength = sanitized.length();

        if (sanitizedLength == 0) {
            return Result.skip("sanitized-empty", originalText);
        }

        double retainedRatio = (double) sanitizedLength / (double) originalLength;
        if (retainedRatio < configState.minRetainedRatio) {
            return Result.skip("below-min-retained-ratio", originalText);
        }

        if (sanitized.equals(originalText)) {
            return Result.skip("no-change", originalText);
        }

        return Result.write(sanitized, "sanitized");
    }

    private String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }
    
    private String removeMatchingBlocks(String text, List<Pattern> patterns) {
        String result = text;
        for (Pattern pattern : patterns) {
            result = pattern.matcher(result).replaceAll("");
        }
        return result;
    }   

    private String removeMatchingLines(String text, List<Pattern> patterns) {
        if (patterns.isEmpty()) {
            return text;
        }

        List<String> kept = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            boolean matched = patterns.stream().anyMatch(p -> p.matcher(line).matches());
            if (!matched) {
                kept.add(line);
            }
        }
        return kept.stream().collect(Collectors.joining("\n"));
    }

    private String collapseBlankLines(String text) {
        return text.replaceAll("\\n{3,}", "\n\n");
    }

    private static final class ConfigState {
        private final List<Pattern> blockPatterns;
        private final List<Pattern> headerPatterns;
        private final List<Pattern> footerPatterns;
        private final List<Pattern> pageNumberPatterns;
        private final List<Pattern> footnotePatterns;
        private final double minRetainedRatio;

        private ConfigState(PdfTextSanitizerConfig config) {
            this.blockPatterns = compile(config.blockRegexes(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            this.headerPatterns = compile(config.headerRegexes(), 0);
            this.footerPatterns = compile(config.footerRegexes(), 0);
            this.pageNumberPatterns = compile(config.pageNumberRegexes(), 0);
            this.footnotePatterns = compile(config.footnoteRegexes(), 0);
            this.minRetainedRatio = config.minRetainedRatio();
        }

        private static List<Pattern> compile(String[] values, int flags) {
            List<Pattern> patterns = new ArrayList<>();
            if (values != null) {
                for (String value : values) {
                    if (value != null && !value.isBlank()) {
                        patterns.add(Pattern.compile(value, flags));
                    }
                }
            }
            return patterns;
        }
    }
}
