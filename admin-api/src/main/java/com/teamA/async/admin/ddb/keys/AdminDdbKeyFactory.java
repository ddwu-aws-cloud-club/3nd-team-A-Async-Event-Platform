package com.teamA.async.admin.ddb.keys;

public class AdminDdbKeyFactory {
    public static String eventPk(String eventId) {
        return "EVENT#" + eventId;
    }

    public static String metaSk() {
        return "META";
    }
}
