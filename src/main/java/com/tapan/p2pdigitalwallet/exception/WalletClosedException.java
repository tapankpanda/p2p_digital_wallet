package com.tapan.p2pdigitalwallet.exception;

public class WalletClosedException extends ConflictException{

    public WalletClosedException(String message){
        super(message);
    }
}
