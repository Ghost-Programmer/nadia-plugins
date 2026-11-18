package com.nadia.plugin.lang.de;

import com.nadia.app.plugin.api.LanguagePackDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GermanLanguageExtensionTest {

    @Test
    void loadsGermanUtf8BundleWithCompleteKeys() {
        GermanLanguageExtension ext = new GermanLanguageExtension();
        LanguagePackDescriptor pack = ext.getLanguagePack();

        assertEquals("de", pack.localeCode());
        assertEquals("German", pack.displayName());
        assertEquals("Deutsch", pack.nativeName());
        assertEquals("🇩🇪", pack.flagEmoji());
        assertTrue(pack.translations().size() >= 150);
        assertEquals("Datei", pack.translations().get("menu.file"));
        assertEquals("Über Nadia", pack.translations().get("app.about.title"));
        pack.translations().forEach((k, v) -> assertFalse(v.isBlank(), "Blank value for key: " + k));
    }
}

