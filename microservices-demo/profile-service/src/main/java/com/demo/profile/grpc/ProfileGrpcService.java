package com.demo.profile.grpc;

import com.demo.grpc.profile.GetProfileRequest;
import com.demo.grpc.profile.GetProfileResponse;
import com.demo.grpc.profile.ProfileServiceGrpc;
import com.demo.grpc.profile.UpdateProfileRequest;
import com.demo.grpc.profile.UpdateProfileResponse;
import com.demo.profile.model.UserProfile;
import com.demo.profile.repository.ProfileRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * gRPC server implementation for {@code ProfileService}.
 *
 * <p>Exposes two methods consumed by the Notification Service:
 * <ul>
 *   <li>{@link #getProfile(GetProfileRequest, StreamObserver)}</li>
 *   <li>{@link #updateProfile(UpdateProfileRequest, StreamObserver)}</li>
 * </ul>
 *
 * <p>Annotated with {@code @GrpcService} so that
 * {@code grpc-spring-boot-starter} registers it automatically on the gRPC port.
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ProfileGrpcService extends ProfileServiceGrpc.ProfileServiceImplBase {

    private final ProfileRepository profileRepository;

    // ── GetProfile ─────────────────────────────────────────────────────────

    @Override
    public void getProfile(GetProfileRequest request,
                           StreamObserver<GetProfileResponse> responseObserver) {

        log.debug("gRPC GetProfile request for userId={}", request.getUserId());

        try {
            UUID userId = UUID.fromString(request.getUserId());
            Optional<UserProfile> opt = profileRepository.findByUserId(userId);

            GetProfileResponse.Builder builder = GetProfileResponse.newBuilder();

            if (opt.isPresent()) {
                UserProfile p = opt.get();
                builder
                    .setUserId(p.getUserId().toString())
                    .setName(p.getName() == null ? "" : p.getName())
                    .setBio(p.getBio() == null ? "" : p.getBio())
                    .setAvatarUrl(p.getAvatarUrl() == null ? "" : p.getAvatarUrl())
                    .setCreatedAt(p.getCreatedAt().toString())
                    .setFound(true);
            } else {
                builder.setFound(false);
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid userId in gRPC request: {}", request.getUserId());
            responseObserver.onError(
                io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("Invalid userId format")
                    .asRuntimeException());
        }
    }

    // ── UpdateProfile ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public void updateProfile(UpdateProfileRequest request,
                              StreamObserver<UpdateProfileResponse> responseObserver) {

        log.debug("gRPC UpdateProfile request for userId={}", request.getUserId());

        try {
            UUID userId = UUID.fromString(request.getUserId());
            Optional<UserProfile> opt = profileRepository.findByUserId(userId);

            if (opt.isEmpty()) {
                responseObserver.onNext(UpdateProfileResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Profile not found for userId=" + request.getUserId())
                        .build());
                responseObserver.onCompleted();
                return;
            }

            UserProfile profile = opt.get();
            if (!request.getName().isBlank())      profile.setName(request.getName());
            if (!request.getBio().isBlank())        profile.setBio(request.getBio());
            if (!request.getAvatarUrl().isBlank())  profile.setAvatarUrl(request.getAvatarUrl());

            profileRepository.save(profile);

            responseObserver.onNext(UpdateProfileResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Profile updated successfully")
                    .build());
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            responseObserver.onError(
                io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("Invalid userId format")
                    .asRuntimeException());
        }
    }
}
