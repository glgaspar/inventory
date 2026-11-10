package com.pessimaideia.inventory.model;

public class LoginResult {
    public static class User {
        public final long id;
        public final String username;
        public final boolean admin;

        public User(long id, String username, boolean admin) {
            this.id = id;
            this.username = username;
            this.admin = admin;
        }
    }

    public final String token;
    public final User user;

    public LoginResult(String token, User user) {
        this.token = token;
        this.user = user;
    }
}
