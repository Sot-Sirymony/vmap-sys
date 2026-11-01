package com.visionmapping.service;

import java.util.List;

/** Lets the flow test in another package read the delete list. */
public final class AccountServiceTables {

    private AccountServiceTables() {
    }

    public static List<String> names() {
        return AccountService.USER_TABLES_IN_DELETE_ORDER;
    }
}
