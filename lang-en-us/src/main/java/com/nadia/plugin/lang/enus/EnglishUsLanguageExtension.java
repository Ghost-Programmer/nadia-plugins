package com.nadia.plugin.lang.enus;

import com.nadia.app.plugin.api.LanguagePackDescriptor;
import com.nadia.app.plugin.api.NadiaLanguageExtension;
import org.pf4j.Extension;

/**
 * PF4J {@link NadiaLanguageExtension} providing American English ({@code en-US}) UI and AI localization.
 */
@Extension
public class EnglishUsLanguageExtension implements NadiaLanguageExtension {

    private static final String RESOURCE_PATH = "i18n/messages_en_US.properties";

    @Override
    public LanguagePackDescriptor getLanguagePack() {
        return new LanguagePackDescriptor(
                "en-US",
                "English (US)",
                "English (US)",
                "🇺🇸",
                NadiaLanguageExtension.loadUtf8Properties(getClass().getClassLoader(), RESOURCE_PATH)
        );
    }
}

