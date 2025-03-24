package de.rptu.cs.exclaim.i18n;

import java.util.List;
import java.util.Locale;

public class NoSuchMessageInAnyLocaleException extends RuntimeException {
    public NoSuchMessageInAnyLocaleException(String code, List<Locale> locales) {
        super("No message found under code '" + code + "' for any of the locales " + locales + ".");
    }
}
