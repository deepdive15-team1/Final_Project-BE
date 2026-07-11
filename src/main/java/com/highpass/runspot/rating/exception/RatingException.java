package com.highpass.runspot.rating.exception;

import com.highpass.runspot.common.exception.BaseException;
import com.highpass.runspot.common.exception.BaseExceptionType;

public class RatingException extends BaseException {

    public RatingException(final BaseExceptionType exceptionType) {
        super(exceptionType);
    }
}
