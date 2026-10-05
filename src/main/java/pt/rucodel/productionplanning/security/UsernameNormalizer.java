package pt.rucodel.productionplanning.security;

import java.util.Locale;

public final class UsernameNormalizer {
    private UsernameNormalizer() {
    }

    public static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
