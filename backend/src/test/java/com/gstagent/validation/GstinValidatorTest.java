package com.gstagent.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GstinValidatorTest {

    @Test
    void acceptsRealWorldGstin() {
        assertThat(GstinValidator.isValid("27AAPFU0939F1ZV")).isTrue();
    }

    @Test
    void detectsSingleCharacterMisread() {
        // 'O' read instead of '0' keeps the format valid but breaks the checksum
        assertThat(GstinValidator.isValidFormat("27AAPFU0939F1ZV")).isTrue();
        assertThat(GstinValidator.isValid("27AAPFU0939F1ZW")).isFalse();
        assertThat(GstinValidator.isValidChecksum("27AAPFU0939F1ZW")).isFalse();
    }

    @Test
    void rejectsBadFormatAndUnknownState() {
        assertThat(GstinValidator.isValidFormat("27AAPFU0939F1Z")).isFalse();
        assertThat(GstinValidator.isValidFormat("27aapfu0939f1zv")).isFalse();
        String first14 = "45AAPFU0939F1Z";
        assertThat(GstinValidator.isValid(first14 + GstinValidator.checksumChar(first14))).isFalse();
    }

    @Test
    void normalizesOcrNoise() {
        assertThat(GstinValidator.normalize(" 27 aapfu-0939f1zv ")).isEqualTo("27AAPFU0939F1ZV");
        assertThat(GstinValidator.normalize("  ")).isNull();
    }
}
