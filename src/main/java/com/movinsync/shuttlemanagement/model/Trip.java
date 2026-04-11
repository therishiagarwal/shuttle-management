package com.movinsync.shuttlemanagement.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Student student;

    @ManyToOne
    private Stop fromStop;

    @ManyToOne
    private Stop toStop;

    @Column(name = "fare_used")
    private Integer fare;

    @Column(name = "timestamp")
    private LocalDateTime tripTime;

    @Enumerated(EnumType.STRING)
    private TripStatus status = TripStatus.BOOKED;
}
