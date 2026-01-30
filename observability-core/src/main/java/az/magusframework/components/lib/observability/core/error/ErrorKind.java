package az.magusframework.components.lib.observability.core.error;

/**
 * Categorizes the nature of a failure for observability and alerting purposes.
 */
public enum ErrorKind {
    /**
     * Represents expected failures caused by invalid user input or business rule violations.
     * These typically do not trigger operational alerts.
     */
    BUSINESS,

    /**
     * Represents unexpected failures caused by system instability, infrastructure issues,
     * or unhandled runtime exceptions. These typically trigger operational alerts.
     */
    TECHNICAL
}