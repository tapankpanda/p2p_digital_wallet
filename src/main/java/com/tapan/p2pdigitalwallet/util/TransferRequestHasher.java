package com.tapan.p2pdigitalwallet.util;

import com.tapan.p2pdigitalwallet.dto.TransferRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class TransferRequestHasher {

    private TransferRequestHasher(){

    }

    public static String hash(TransferRequest request){
        String canonicalRequest = String.join(
                "\n",
                request.fromAccountNumber(),
                request.toAccountNumber(),
                request.amount().stripTrailingZeros().toPlainString(),
                request.description() == null ? "" : request.description()
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(
                    canonicalRequest.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException exception){
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable", exception
            );
        }
    }
}
