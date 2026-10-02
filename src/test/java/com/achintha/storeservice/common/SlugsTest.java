package com.achintha.storeservice.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlugsTest {

    @Test
    void derivesUrlSafeSlugs() {
        assertThat(Slugs.from("Lanka Crafts & Co.")).isEqualTo("lanka-crafts-co");
        assertThat(Slugs.from("  Café   Ceylon  ")).isEqualTo("cafe-ceylon");
        assertThat(Slugs.from("!!!")).isEqualTo("store");
        assertThat(Slugs.from("a".repeat(150))).hasSize(100);
    }

    @Test
    void sanitizerStripsMarkup() {
        assertThat(TextSanitizer.clean("<script>alert(1)</script>Nice & tidy")).isEqualTo("Nice & tidy");
        assertThat(TextSanitizer.cleanLine("two\nlines")).isEqualTo("two lines");
    }
}
