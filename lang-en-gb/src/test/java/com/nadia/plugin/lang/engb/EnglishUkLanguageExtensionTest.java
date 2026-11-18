package com.nadia.plugin.lang.engb;

import com.nadia.app.plugin.api.LanguagePackDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnglishUkLanguageExtensionTest {

    @Test
    void loadsBritishEnglishBundleWithAuthenticUkSpelling() {
        EnglishUkLanguageExtension ext = new EnglishUkLanguageExtension();
        LanguagePackDescriptor pack = ext.getLanguagePack();

        assertEquals("en-GB", pack.localeCode());
        assertEquals("English (UK)", pack.displayName());
        assertEquals("🇬🇧", pack.flagEmoji());
        assertTrue(pack.translations().size() >= 150);
        assertEquals("0. Language & Localisation", pack.translations().get("settings.lang.section_title"));
        assertEquals("CONTROL CENTRE", pack.translations().get("panel.title"));
        assertTrue(pack.translations().get("menu.view.toggle_theme").contains("Colour"));
        pack.translations().forEach((k, v) -> assertFalse(v.isBlank(), "Blank value for key: " + k));
    }
}

