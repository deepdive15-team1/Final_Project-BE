package com.highpass.runspot.auth.exception;

import com.highpass.runspot.common.exception.BaseException;

public class UserException extends BaseException {
    public UserException(UserErrorCode code) {
        super(code);
    }
}
