package com.nadia.plugin.lang.fr;

import com.nadia.app.plugin.api.LanguagePackDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FrenchLanguageExtensionTest {

    @Test
    void loadsFrenchUtf8BundleWithCompleteKeys() {
        FrenchLanguageExtension ext = new FrenchLanguageExtension();
        LanguagePackDescriptor pack = ext.getLanguagePack();

        assertEquals("fr", pack.localeCode());
        assertEquals("French", pack.displayName());
        assertEquals("Français", pack.nativeName());
        assertEquals("🇫🇷", pack.flagEmoji());
        assertTrue(pack.translations().size() >= 150);
        assertEquals("Fichier", pack.translations().get("menu.file"));
        assertEquals("À propos de Nadia", pack.translations().get("app.about.title"));
        pack.translations().forEach((k, v) -> assertFalse(v.isBlank(), "Blank value for key: " + k));
    }
}

