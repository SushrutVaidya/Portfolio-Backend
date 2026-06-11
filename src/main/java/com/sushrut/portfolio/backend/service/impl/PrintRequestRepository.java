package com.sushrut.portfolio.backend.service.impl;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sushrut.portfolio.backend.entities.PrintRequest;

public interface PrintRequestRepository extends JpaRepository<PrintRequest, UUID> {
    long countByCity(String city);
}
