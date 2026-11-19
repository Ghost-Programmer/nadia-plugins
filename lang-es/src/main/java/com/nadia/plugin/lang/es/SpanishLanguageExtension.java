package com.nadia.plugin.lang.es;

import com.nadia.app.plugin.api.LanguagePackDescriptor;
import com.nadia.app.plugin.api.NadiaLanguageExtension;
import org.pf4j.Extension;

/**
 * PF4J {@link NadiaLanguageExtension} providing Spanish ({@code es}) UI and AI localization.
 */
@Extension
public class SpanishLanguageExtension implements NadiaLanguageExtension {

    private static final String RESOURCE_PATH = "i18n/messages_es.properties";
    private volatile LanguagePackDescriptor cached;

    @Override
    public LanguagePackDescriptor getLanguagePack() {
        LanguagePackDescriptor result = cached;
        if (result == null) {
            synchronized (this) {
                result = cached;
                if (result == null) {
                    result = new LanguagePackDescriptor(
                            "es",
                            "Spanish",
                            "Español",
                            "🇪🇸",
                            NadiaLanguageExtension.loadUtf8Properties(getClass().getClassLoader(), RESOURCE_PATH)
                    );
                    cached = result;
                }
            }
        }
        return result;
    }
}

