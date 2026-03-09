package com.demo.notification.grpc;

import com.demo.grpc.profile.GetProfileRequest;
import com.demo.grpc.profile.GetProfileResponse;
import com.demo.grpc.profile.ProfileServiceGrpc;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * gRPC client stub for the Profile Service.
 *
 * <p>The {@code @GrpcClient("profile-service")} annotation tells
 * {@code grpc-spring-boot-starter} to inject a channel configured under
 * {@code grpc.client.profile-service.*} in {@code application.yml}.
 *
 * <p>Flow:
 * <pre>
 * Notification Service  ──gRPC──►  Profile Service (port 9090)
 *                                         │
 *                               returns profile details
 * </pre>
 */
@Slf4j
@Component
public class ProfileServiceClient {

    /**
     * Blocking stub – appropriate for request/response calls inside a Kafka
     * consumer handler where we already run on a worker thread.
     */
    @GrpcClient("profile-service")
    private ProfileServiceGrpc.ProfileServiceBlockingStub profileStub;

    /**
     * Fetches a user profile from the Profile Service via gRPC.
     *
     * @param userId UUID string
     * @return {@link GetProfileResponse} from the Profile Service
     */
    public GetProfileResponse getProfile(String userId) {
        log.debug("gRPC call → ProfileService.GetProfile(userId={})", userId);

        GetProfileRequest request = GetProfileRequest.newBuilder()
                .setUserId(userId)
                .build();

        try {
            GetProfileResponse response = profileStub.getProfile(request);
            log.debug("gRPC response: found={} name={}", response.getFound(), response.getName());
            return response;
        } catch (Exception e) {
            log.error("gRPC call to ProfileService failed for userId={}: {}", userId, e.getMessage());
            // Return an empty response so the caller can decide how to handle it
            return GetProfileResponse.newBuilder().setFound(false).build();
        }
    }
}
