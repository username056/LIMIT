package com.c203.limit.domain.admin.entity;
import java.time.OffsetDateTime; import jakarta.persistence.*;
@Entity @Table(name="admin_account") public class AdminAccount{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="admin_id") private Long id; @Column(nullable=false,unique=true) private String email; @Column(nullable=false) private String password; @Column(nullable=false) private String name; @Column(nullable=false) private String role; @Column(nullable=false) private String status; @Column(name="created_at",nullable=false) private OffsetDateTime createdAt;
 protected AdminAccount(){} public Long getId(){return id;} public String getEmail(){return email;} public String getPassword(){return password;} public String getName(){return name;} public String getRole(){return role;} public String getStatus(){return status;}
}
