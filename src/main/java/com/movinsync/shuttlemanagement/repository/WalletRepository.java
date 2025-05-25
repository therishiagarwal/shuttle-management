package com.movinsync.shuttlemanagement.repository;

import com.movinsync.shuttlemanagement.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    @Query("SELECT SUM(w.balance) FROM Wallet w")
    Long totalDistributedThisMonth(); // Optional: Rename later if tracking monthly allocations
}
