package com.careq.queue.exception;

public class DoctorServiceUnavailableException extends RuntimeException {

    public DoctorServiceUnavailableException(String message) {
        super(message);
    }

    public DoctorServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
