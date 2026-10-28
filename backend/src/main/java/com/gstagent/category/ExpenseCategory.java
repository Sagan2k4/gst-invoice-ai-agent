package com.gstagent.category;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExpenseCategory {
    RAW_MATERIALS("Raw materials", true),
    INVENTORY("Stock for resale", true),
    OFFICE_SUPPLIES("Office supplies & stationery", true),
    IT_AND_SOFTWARE("IT services & software", true),
    EQUIPMENT_AND_ASSETS("Equipment, furniture & electronics", true),
    FUEL_AND_TRAVEL("Fuel & travel", true),
    LOGISTICS("Freight & logistics", true),
    RENT_AND_UTILITIES("Rent & utilities", true),
    TELECOM_AND_INTERNET("Telecom & internet", true),
    PROFESSIONAL_SERVICES("Professional services", true),
    MARKETING("Marketing & advertising", true),
    REPAIRS_AND_MAINTENANCE("Repairs & maintenance", true),
    // Section 17(5) CGST Act: input tax credit is generally blocked for these.
    FOOD_AND_BEVERAGES("Food & beverages", false),
    MOTOR_VEHICLES("Motor vehicles", false),
    OTHER("Other", true);

    private final String label;
    /** Whether input tax credit is ordinarily claimable for this kind of expense. */
    private final boolean itcEligible;
}
