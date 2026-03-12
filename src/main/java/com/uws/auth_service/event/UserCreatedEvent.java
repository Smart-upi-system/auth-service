package com.uws.auth_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreatedEvent {

    private String eventId;

    @Builder.Default
    private String eventType = "UserCreated";

    private String userId;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    private UserData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserData {
        private String username;
        private String email;
        private String name;
        private String role;
    }
}
