package com.highpass.runspot.file.domain;

public enum FileDomain {
    POST("posts"),
    CHAT("chat"),
    PROFILE("profile");
    private final String directory;

    FileDomain(String directory) {
        this.directory = directory;
    }

    public String directory() {
        return directory;
    }
}
