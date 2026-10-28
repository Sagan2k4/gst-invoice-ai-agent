package com.gstagent.invoice;

public enum SupplyType {
    /** Supplier and place of supply in the same state: CGST + SGST. */
    INTRA,
    /** Different states: IGST. */
    INTER
}
