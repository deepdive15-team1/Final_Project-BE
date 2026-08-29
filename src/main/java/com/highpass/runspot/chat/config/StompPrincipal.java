package com.highpass.runspot.chat.config;import java.security.Principal;public record StompPrincipal(Long userId) implements Principal{public String getName(){return userId.toString();}}
