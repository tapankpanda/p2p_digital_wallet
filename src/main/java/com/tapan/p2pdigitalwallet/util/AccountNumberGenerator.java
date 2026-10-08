package com.tapan.p2pdigitalwallet.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class AccountNumberGenerator {

    private static final long MIN = 100_000_000_000L;
    private static final long RANGE = 900_000_000_000L;

    private final SecureRandom random = new SecureRandom();

    public String generate(){
        return String.valueOf(MIN + random.nextLong(RANGE));
    }
}
