package com.gstagent.user;

/**
 * ADMIN      - business owner: everything, including user management.
 * ACCOUNTANT - uploads, reviews, edits and approves bills; generates reports.
 * VIEWER     - read-only access to bills and reports.
 */
public enum Role {
    ADMIN, ACCOUNTANT, VIEWER
}
