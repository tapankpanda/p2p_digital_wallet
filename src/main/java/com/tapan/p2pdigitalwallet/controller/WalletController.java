package com.tapan.p2pdigitalwallet.controller;

import com.tapan.p2pdigitalwallet.dto.*;
import com.tapan.p2pdigitalwallet.entity.WalletStatus;
import com.tapan.p2pdigitalwallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/wallet")
@Tag(
        name = "Wallet",
        description = "Endpoints for wallet management, balance retrieval, "
                + "transaction history, P2P transfers, and wallet lifecycle management"
)
public class WalletController {

    private  final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    private void validateAccountNumber(String accountNumber) {
        if (accountNumber == null || !accountNumber.matches("^[1-9][0-9]{11}$")) {
            throw new IllegalArgumentException("Account number must be exactly 12 digits and cannot start with 0.");
        }
    }


    @Operation(
            summary = "Create a new wallet",
            description = "Creates a wallet with a unique 12-digit account number. "
                    + "Returns the wallet details, including its initial balance and status."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Wallet created successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid wallet creation request",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Wallet creation conflict",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(
            @Valid @RequestBody CreateWalletRequest request){
        return  ResponseEntity
                .status(HttpStatus.CREATED)
                .body(walletService.createWallet(request));
    }


    @Operation(
            summary = "Get wallet details",
            description = "Retrieves wallet details using its 12-digit account number."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Wallet details retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid account number",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Wallet not found",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping("/{accountNumber}")
    public WalletResponse getWallet(@PathVariable String accountNumber){
        validateAccountNumber(accountNumber);
        return walletService.getWallet(accountNumber);
    }


    @Operation(
            summary = "Get wallet balance",
            description = "Retrieves the current balance for a wallet using its 12-digit account number."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Wallet balance retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid account number",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Wallet not found",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping("/{accountNumber}/balance")
    public BalanceResponse getBalance(@PathVariable String accountNumber){
        validateAccountNumber(accountNumber);
        return walletService.getBalance(accountNumber);
    }


    @Operation(
            summary = "Get wallet transaction history",
            description = "Retrieves a wallet's transaction history with pagination. "
                    + "Page numbering starts at 0, the default page size is 20, "
                    + "and the maximum page size is 100."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transaction history retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid account number or pagination parameters",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Wallet not found",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping("/{accountNumber}/transactions")
    public Page<TransactionResponse> getTransactions(
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validateAccountNumber(accountNumber);

        if (page < 0) {
            throw new IllegalArgumentException("Page number cannot be negative");
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }

        Pageable pageable = PageRequest.of(page, size);

        return walletService.getTransactions(accountNumber, pageable);
    }


    @Operation(
            summary = "Transfer money between wallets",
            description = "Transfers money from one wallet to another. "
                    + "The operation uses an idempotency key to help prevent "
                    + "duplicate transfers when a request is retried."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transfer completed successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid transfer request",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Source or destination wallet not found",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Transfer conflict, such as insufficient funds, "
                            + "a frozen or closed wallet, or an idempotency-key conflict",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> transfer(
            @Parameter(
                    description = "Unique key used to identify a transfer request "
                            + "and safely handle retries",
                    required = true
            )
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request
    ){
        return ResponseEntity.ok(
                walletService.transfer(idempotencyKey, request)
        );
    }


    @Operation(
            summary = "Update wallet status",
            description = "Changes a wallet's status, for example to ACTIVE or FROZEN, "
                    + "subject to the wallet lifecycle rules."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Wallet status updated successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid account number or status",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Wallet not found",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Wallet lifecycle conflict",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PutMapping("/{accountNumber}/status")
    public WalletResponse updateWalletStatus(
            @PathVariable String accountNumber,
            @Parameter(
                    description = "New wallet status. Use ACTIVE or FROZEN. "
                            + "Use the dedicated close-wallet endpoint to close a wallet.",
                    schema = @Schema(allowableValues = {"ACTIVE", "FROZEN"})
            )
            @RequestParam WalletStatus status
    ){
        validateAccountNumber(accountNumber);
        return walletService.updateWalletStatus(accountNumber, status);
    }


    @Operation(
            summary = "Close a wallet",
            description = "Closes a wallet subject to the wallet lifecycle rules. "
                    + "The request may be rejected if the wallet cannot be closed "
                    + "under its current state."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Wallet closed successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid account number",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Wallet not found",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Wallet cannot be closed under the current lifecycle rules",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PatchMapping("/{accountNumber}/close")
    public WalletResponse closeWallet(
            @PathVariable String accountNumber
    ){
        validateAccountNumber(accountNumber);
        return walletService.closeWallet(accountNumber);
    }
}

