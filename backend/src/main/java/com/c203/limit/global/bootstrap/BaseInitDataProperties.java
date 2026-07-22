package com.c203.limit.global.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "limit.bootstrap.base-init")
public class BaseInitDataProperties {
    private final MemberAccount member = new MemberAccount();
    private final AdminAccount admin = new AdminAccount();

    public MemberAccount getMember() {
        return member;
    }

    public AdminAccount getAdmin() {
        return admin;
    }

    public static class MemberAccount {
        private boolean enabled = true;
        private String email;
        private String password;
        private String nickname;
        private String phone;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
    }

    public static class AdminAccount {
        private boolean enabled = true;
        private String email;
        private String password;
        private String name;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
