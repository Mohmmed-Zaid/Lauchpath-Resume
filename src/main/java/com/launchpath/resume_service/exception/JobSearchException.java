package com.launchpath.resume_service.exception;

public class JobSearchException extends RuntimeException {
  public JobSearchException(String message) {
    super(message);
  }

  public JobSearchException(String message, Throwable cause) {
    super(message, cause);
  }
}
