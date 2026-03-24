package io.mosip.registration.util.common;

import java.util.Arrays;
import org.springframework.stereotype.Component;
import io.mosip.registration.config.AppConfig;
import io.mosip.kernel.core.logger.spi.Logger;

/**
 * UID Generator Helper implementing Luhn Algorithm for Registration Client
 */
@Component
public class UidGeneratorHelper {

    private static final Logger LOGGER = AppConfig.getLogger(UidGeneratorHelper.class);

    /**
     * Validate if a given UID follows Luhn Algorithm rules
     *
     * @param uid the UID to validate
     * @return true if valid, false otherwise
     */
    public boolean validateUidWithLuhn(String uid) {
        if (uid == null || uid.length() != 10) {
            return false;
        }

        try {
            // Extract the 9-digit base number (first 9 digits)
            String baseNumber = uid.substring(0, 9);
            int providedChecksum = Integer.parseInt(uid.substring(9));

            // Calculate checksum for the base number
            String[] digits = baseNumber.split("");
            int[] processedDigits = new int[9];

            for (int i = 0; i < 9; i++) {
                int digit = Integer.parseInt(digits[i]);

                // If position is EVEN (0-based index), multiply digit by 2
                if (i % 2 == 0) {
                    int multiplied = digit * 2;
                    // If result > 9, sum the digits (e.g., 14 -> 1+4 = 5)
                    if (multiplied > 9) {
                        processedDigits[i] = (multiplied / 10) + (multiplied % 10);
                    } else {
                        processedDigits[i] = multiplied;
                    }
                } else {
                    // For ODD positions, keep digit unchanged
                    processedDigits[i] = digit;
                }
            }

            int sum = Arrays.stream(processedDigits).sum();
            int calculatedChecksum = (10 - (sum % 10)) % 10;

            return calculatedChecksum == providedChecksum;

        } catch (NumberFormatException e) {
            LOGGER.error("UidGeneratorHelper", "validateUidWithLuhn", "Invalid characters in UID", e.getMessage());
            return false;
        }
    }
}