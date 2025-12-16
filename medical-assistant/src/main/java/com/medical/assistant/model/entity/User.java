package com.medical.assistant.model.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 100)
    private String userId;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(unique = true, length = 150)
    private String email;

    @Column(unique = true, length = 20)
    private String phone;

    @Column(nullable = false)
    private String password;

    // 基本信息
    @Column(name = "real_name", length = 100)
    private String realName;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('MALE', 'FEMALE', 'OTHER', 'UNKNOWN')")
    private Gender gender = Gender.UNKNOWN;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    // 角色和状态
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('DOCTOR', 'PATIENT')")
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", columnDefinition = "ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED')")
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    // 医生特有字段
    @Column(name = "doctor_id", length = 50)
    private String doctorId;

    @Column(length = 50)
    private String title;

    @Column(length = 100)
    private String department;

    @Column(length = 200)
    private String hospital;

    // 患者特有字段
    @Column(name = "blood_type", columnDefinition = "ENUM('A', 'B', 'AB', 'O', 'UNKNOWN')")
    @Enumerated(EnumType.STRING)
    private BloodType bloodType = BloodType.UNKNOWN;

    @Column(name = "allergy_history", columnDefinition = "TEXT")
    private String allergyHistory;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Enums
    public enum Gender {
        MALE, FEMALE, OTHER, UNKNOWN
    }

    public enum UserRole {
        DOCTOR, PATIENT
    }

    public enum AccountStatus {
        ACTIVE, INACTIVE, SUSPENDED
    }

    public enum BloodType {
        A, B, AB, O, UNKNOWN
    }
}
