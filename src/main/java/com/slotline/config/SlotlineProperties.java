package com.slotline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "slotline")
public class SlotlineProperties {

    private final Jwt jwt = new Jwt();
    private final Admin admin = new Admin();
    private final Notifications notifications = new Notifications();

    public Jwt getJwt() {
        return jwt;
    }

    public Admin getAdmin() {
        return admin;
    }

    public Notifications getNotifications() {
        return notifications;
    }

    public static class Jwt {
        private String secret;
        private Duration expiration = Duration.ofHours(12);
        private String issuer = "slotline";

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public Duration getExpiration() {
            return expiration;
        }

        public void setExpiration(Duration expiration) {
            this.expiration = expiration;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }
    }

    public static class Admin {
        private String username = "admin";
        private String password = "admin12345";

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Notifications {
        private final Webhook webhook = new Webhook();

        public Webhook getWebhook() {
            return webhook;
        }

        public static class Webhook {
            private String url;

            public String getUrl() {
                return url;
            }

            public void setUrl(String url) {
                this.url = url;
            }
        }
    }
}
