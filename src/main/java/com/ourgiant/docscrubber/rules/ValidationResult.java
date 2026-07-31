package com.ourgiant.docscrubber.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Outcome of validating a {@link RuleSet}. Errors mean "refuse to scan"; warnings are surfaced but non-blocking (e.g. an unknown detector id on a disabled rule). */
public final class ValidationResult {

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    void addError(String message) {
        errors.add(message);
    }

    void addWarning(String message) {
        warnings.add(message);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }
}
