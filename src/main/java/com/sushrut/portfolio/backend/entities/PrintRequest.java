package com.sushrut.portfolio.backend.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "print_requests")
public class PrintRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String addressLine1;

    private String addressLine2;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false, length = 6)
    private String pincode;

    @Column(nullable = false, length = 15)
    private String phone;

    private UUID cardId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public UUID getId()               { return id; }
    public String getFullName()       { return fullName; }
    public String getAddressLine1()   { return addressLine1; }
    public String getAddressLine2()   { return addressLine2; }
    public String getCity()           { return city; }
    public String getPincode()        { return pincode; }
    public String getPhone()          { return phone; }
    public UUID getCardId()           { return cardId; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setFullName(String v)     { this.fullName = v; }
    public void setAddressLine1(String v) { this.addressLine1 = v; }
    public void setAddressLine2(String v) { this.addressLine2 = v; }
    public void setCity(String v)         { this.city = v; }
    public void setPincode(String v)      { this.pincode = v; }
    public void setPhone(String v)        { this.phone = v; }
    public void setCardId(UUID v)         { this.cardId = v; }
}
