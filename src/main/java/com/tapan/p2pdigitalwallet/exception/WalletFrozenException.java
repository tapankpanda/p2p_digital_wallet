package com.tapan.p2pdigitalwallet.exception;

public class WalletFrozenException extends ConflictException{

    public WalletFrozenException(String message){
        super(message);
    }
}
