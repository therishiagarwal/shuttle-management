package com.movinsync.shuttlemanagement.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = UniversityEmailValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UniversityEmail {

    String message() default "Email must belong to the university domain";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
