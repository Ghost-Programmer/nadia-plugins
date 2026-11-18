package com.nadia.plugin.lang.es;

import com.nadia.app.plugin.api.LanguagePackDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpanishLanguageExtensionTest {

    @Test
    void loadsSpanishUtf8BundleWithCompleteKeys() {
        SpanishLanguageExtension ext = new SpanishLanguageExtension();
        LanguagePackDescriptor pack = ext.getLanguagePack();

        assertEquals("es", pack.localeCode());
        assertEquals("Spanish", pack.displayName());
        assertEquals("Español", pack.nativeName());
        assertEquals("🇪🇸", pack.flagEmoji());
        assertTrue(pack.translations().size() >= 150);
        assertEquals("Archivo", pack.translations().get("menu.file"));
        assertEquals("Configuración del sistema", pack.translations().get("settings.tab.system"));
        pack.translations().forEach((k, v) -> assertFalse(v.isBlank(), "Blank value for key: " + k));
    }
}

