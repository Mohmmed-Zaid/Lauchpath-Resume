package com.launchpath.resume_service.exception;

public class ResumeLimitExceededException extends RuntimeException {
    public ResumeLimitExceededException(String message) { super(message); }
}
