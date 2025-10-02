package org.acme.utils;

public class GtinUtils {

    public static boolean isGtin(String input) {
        if (input == null) return false;

        // remove espaços em branco
        String digits = input.trim();

        // precisa ser só números
        if (!digits.matches("\\d+")) return false;

        // só pode ser GTIN-8, 12, 13 ou 14
        int len = digits.length();
        if (!(len == 8 || len == 12 || len == 13 || len == 14)) return false;

        // valida dígito verificador
        return validateCheckDigit(digits);
    }

    private static boolean validateCheckDigit(String gtin) {
        int len = gtin.length();
        int sum = 0;

        // cálculo do módulo 10 (GS1)
        for (int i = 0; i < len - 1; i++) {
            int digit = Character.getNumericValue(gtin.charAt(len - 2 - i));
            sum += digit * (i % 2 == 0 ? 3 : 1);
        }

        int checkDigit = (10 - (sum % 10)) % 10;
        int actualDigit = Character.getNumericValue(gtin.charAt(len - 1));

        return checkDigit == actualDigit;
    }
}
