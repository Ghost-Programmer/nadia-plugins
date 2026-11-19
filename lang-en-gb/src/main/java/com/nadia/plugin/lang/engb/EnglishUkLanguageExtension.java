package com.nadia.plugin.lang.engb;

import com.nadia.app.plugin.api.LanguagePackDescriptor;
import com.nadia.app.plugin.api.NadiaLanguageExtension;
import org.pf4j.Extension;

/**
 * PF4J {@link NadiaLanguageExtension} providing British English ({@code en-GB}) UI and AI localization.
 */
@Extension
public class EnglishUkLanguageExtension implements NadiaLanguageExtension {

    private static final String RESOURCE_PATH = "i18n/messages_en_GB.properties";
    private volatile LanguagePackDescriptor cached;

    @Override
    public LanguagePackDescriptor getLanguagePack() {
        LanguagePackDescriptor result = cached;
        if (result == null) {
            synchronized (this) {
                result = cached;
                if (result == null) {
                    result = new LanguagePackDescriptor(
                            "en-GB",
                            "English (UK)",
                            "English (UK)",
                            "🇬🇧",
                            NadiaLanguageExtension.loadUtf8Properties(getClass().getClassLoader(), RESOURCE_PATH)
                    );
                    cached = result;
                }
            }
        }
        return result;
    }
}

