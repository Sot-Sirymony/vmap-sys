package com.visionmapping.service;

import java.util.ArrayList;
import java.util.List;

/** Lets the flow test in another package read the delete list. */
public final class AccountServiceTables {

    private AccountServiceTables() {
    }

    public static List<String> names() {
        List<String> names = new ArrayList<>(AccountService.USER_TABLES_IN_DELETE_ORDER);
        names.addAll(AccountService.USER_TABLES_ANONYMISED);
        return names;
    }
}
