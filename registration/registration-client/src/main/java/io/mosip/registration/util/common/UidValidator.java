package io.mosip.registration.util.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.mosip.kernel.core.idvalidator.exception.InvalidIDException;

/**
 * UID Validator for Registration Client
 */
@Component
public class UidValidator {

    @Autowired
    private UidGeneratorHelper uidGeneratorHelper;

    private static final String UID_REGEX_PATTERN = "^[0-9]{10}$";

    /**
     * Validate UID format and Luhn Algorithm checksum
     *
     * @param uid the UID to validate
     * @return true if valid
     * @throws InvalidIDException if invalid
     */
    public boolean validate(Object uid) {
        if (uid == null) {
            throw new InvalidIDException("UID", "UID cannot be null");
        }

        String uidStr = uid.toString();

        // 1. Check format (exactly 10 digits)
        Pattern pattern = Pattern.compile(UID_REGEX_PATTERN);
        Matcher matcher = pattern.matcher(uidStr);
        if (!matcher.matches()) {
            throw new InvalidIDException("UID", "UID must be exactly 10 digits");
        }

        // 2. Validate using Luhn Algorithm logic via helper
        if (!uidGeneratorHelper.validateUidWithLuhn(uidStr)) {
            throw new InvalidIDException("UID", "Invalid UID checksum");
        }

        return true;
    }
}