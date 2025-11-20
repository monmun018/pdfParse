package com.example.demo.domain.model;

/**
 * Source format of a Suica PDF. Full history exports contain both balance and amount columns,
 * while partial-selection exports omit the balance column entirely.
 */
public enum SuicaPdfType {
    FULL_HISTORY,
    PARTIAL_SELECTION;

    public boolean hasBalanceColumn() {
        return this == FULL_HISTORY;
    }
}
