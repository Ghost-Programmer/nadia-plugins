package com.nadia.plugin.lang.enus;

import com.nadia.app.plugin.api.LanguagePackDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnglishUsLanguageExtensionTest {

    @Test
    void loadsEnglishUsBundleWithCompleteKeys() {
        EnglishUsLanguageExtension ext = new EnglishUsLanguageExtension();
        LanguagePackDescriptor pack = ext.getLanguagePack();

        assertEquals("en-US", pack.localeCode());
        assertEquals("English (US)", pack.displayName());
        assertEquals("🇺🇸", pack.flagEmoji());
        assertTrue(pack.translations().size() >= 150);
        assertEquals("File", pack.translations().get("menu.file"));
        assertEquals("0. Language & Localization", pack.translations().get("settings.lang.section_title"));
        pack.translations().forEach((k, v) -> assertFalse(v.isBlank(), "Blank value for key: " + k));
    }
}

