package com.gstagent.business;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "businesses")
@Getter
@Setter
@NoArgsConstructor
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** The business's own GSTIN — used to decide intra-state (CGST+SGST) vs inter-state (IGST) supplies. */
    private String gstin;

    private String stateCode;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
