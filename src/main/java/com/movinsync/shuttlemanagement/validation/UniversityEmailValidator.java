package com.movinsync.shuttlemanagement.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UniversityEmailValidator implements ConstraintValidator<UniversityEmail, String> {

    @Value("${university.email.domain}")
    private String allowedDomain;

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return email.toLowerCase().endsWith(allowedDomain.toLowerCase());
    }
}
