package com.example.demo.domain.model;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Domain enumeration describing which extraction features the caller wants.
 * Enables the application layer to toggle expensive parsing work.
 */
public enum PdfFeature {
    STATEMENT_METADATA,
    TABLE_ROWS,
    RAW_TEXT,
    DOCUMENT_METADATA;

	/**
	 * Builds an {@link EnumSet} containing every feature value.
	 *
	 * @return enum set with all defined features
	 */
    public static EnumSet<PdfFeature> allFeatures() {
        return EnumSet.allOf(PdfFeature.class);
    }

	/**
	 * Converts a list of request parameters into an {@link EnumSet} of features.
	 * Invalid or unknown values are ignored and fall back to all features.
	 *
	 * @param rawValues feature names supplied by the caller
	 * @return parsed feature set or {@link #allFeatures()} when empty/invalid
	 */
    public static EnumSet<PdfFeature> fromStrings(List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return allFeatures();
        }
        EnumSet<PdfFeature> features = EnumSet.noneOf(PdfFeature.class);
        for (String value : rawValues) {
            PdfFeature feature = fromString(value);
            if (feature != null) {
                features.add(feature);
            }
        }
        if (features.isEmpty()) {
            return allFeatures();
        }
        return features;
    }

	/**
	 * Parses a single raw value into a {@link PdfFeature}.
	 *
	 * @param rawValue string coming from the HTTP layer
	 * @return feature or {@code null} when the input cannot be parsed
	 */
    private static PdfFeature fromString(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        try {
            return PdfFeature.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

	/**
	 * Resolves a human friendly label for UI drop downs.
	 *
	 * @param feature feature to translate
	 * @return localized display name
	 */
    public static String toDisplayName(PdfFeature feature) {
        return switch (feature) {
            case STATEMENT_METADATA -> "Statement metadata";
            case TABLE_ROWS -> "Statement table rows";
            case RAW_TEXT -> "Raw document text";
            case DOCUMENT_METADATA -> "PDF info/XMP metadata";
        };
    }

	/**
	 * Converts the feature set to the wire format expected by checkboxes.
	 *
	 * @param features feature set to serialize
	 * @return unique set of backing enum names
	 */
    public static Set<String> toStrings(Set<PdfFeature> features) {
        if (features == null) {
            return Set.of();
        }
        return features.stream().map(Enum::name).collect(Collectors.toSet());
    }
}
