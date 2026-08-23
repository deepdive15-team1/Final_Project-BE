package com.highpass.runspot.notification.exception;

import com.highpass.runspot.common.exception.BaseException;
import com.highpass.runspot.common.exception.BaseExceptionType;

public class NotificationException extends BaseException {

    public NotificationException(final BaseExceptionType exceptionType) {
        super(exceptionType);
    }
}
