package com.highpass.runspot.community.exception;

import com.highpass.runspot.common.exception.BaseException;

public class CommunityException extends BaseException {
    public CommunityException(CommunityErrorCode exceptionType) {
        super(exceptionType);
    }
}
