package pet.project.streamEra.playbackDomain.service;


import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Service;
import pet.project.streamEra.playbackDomain.entity.WatchHistory;
import pet.project.streamEra.playbackDomain.repository.WatchHistoryRepository;
import pet.project.streamEra.userDomain.entity.User;
import pet.project.streamEra.userDomain.repository.UserRepository;
import pet.project.streamEra.videoDomain.entity.Video;

import java.security.Principal;
import java.util.List;

@Service
@AllArgsConstructor
public class WatchHistoryService {
    private final WatchHistoryRepository watchHistoryRepository;
    private final UserRepository userRepository;

    public List<WatchHistory> getMe(Principal principal){
        User userToFind = userRepository.findByUsername(principal.getName()).orElseThrow(EntityNotFoundException::new);

        return watchHistoryRepository.findByUserId(userToFind.getUserId());
    }

    public WatchHistory getWhByVideo(@Argument Long id, Principal principal){
        User tokenOwner = userRepository.findByUsername(principal.getName()).orElseThrow(EntityNotFoundException::new);

        return watchHistoryRepository.findByUserIdAndVideoId(tokenOwner.getUserId(), id).orElse(null);
    }
}
