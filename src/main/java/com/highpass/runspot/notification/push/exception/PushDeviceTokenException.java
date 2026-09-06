package com.highpass.runspot.notification.push.exception;

import com.highpass.runspot.common.exception.BaseException;
import com.highpass.runspot.common.exception.BaseExceptionType;

public class PushDeviceTokenException extends BaseException {

    public PushDeviceTokenException(BaseExceptionType exceptionType) {
        super(exceptionType);
    }
}
