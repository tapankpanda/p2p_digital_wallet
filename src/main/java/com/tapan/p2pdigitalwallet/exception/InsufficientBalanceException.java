package com.tapan.p2pdigitalwallet.exception;

public class InsufficientBalanceException extends RuntimeException{

    public InsufficientBalanceException (String message){
        super(message);
    }
}
