package com.highpass.runspot.notification.push.service;

@FunctionalInterface
public interface PushJitterSource {

    double nextDouble();
}
