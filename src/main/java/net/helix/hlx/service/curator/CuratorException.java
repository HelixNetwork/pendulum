package net.helix.hlx.service.curator;

/**
 * This class is used to wrap exceptions that are specific to the curator logic.
 *
 * It allows us to distinct between the different kinds of errors that can happen during the execution of the code.
 */
public class CuratorException extends Exception {
    /**
     * Constructor of the exception which allows us to provide a specific error message and the cause of the error.
     *
     * @param message reason why this error occurred
     * @param cause wrapped exception that caused this error
     */
    public CuratorException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructor of the exception which allows us to provide a specific error message without having an underlying
     * cause.
     *
     * @param message reason why this error occurred
     */
    public CuratorException(String message) {
        super(message);
    }

    /**
     * Constructor of the exception which allows us to wrap the underlying cause of the error without providing a
     * specific reason.
     *
     * @param cause wrapped exception that caused this error
     */
    public CuratorException(Throwable cause) {
        super(cause);
    }
}
