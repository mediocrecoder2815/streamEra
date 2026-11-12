package pet.project.streamEra.userDomain.service;


import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.stereotype.Service;
import pet.project.streamEra.userDomain.dtos.WatchHistoryProgressDto;
import pet.project.streamEra.userDomain.entity.WatchHistory;
import pet.project.streamEra.userDomain.dtos.WatchHistoryDto;
import pet.project.streamEra.userDomain.repository.WatchHistoryRepository;
import pet.project.streamEra.userDomain.entity.User;
import pet.project.streamEra.userDomain.repository.UserRepository;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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


    public WatchHistory updateProgress(WatchHistoryProgressDto updateDto) {
        WatchHistory toUpdate = watchHistoryRepository.findById(updateDto.watchHistoryId())
                .orElseThrow(EntityNotFoundException::new);
        toUpdate.setTimestamp(updateDto.timestamp());
        return watchHistoryRepository.save(toUpdate);
    }
    @Transactional
    public WatchHistory createWH(WatchHistoryDto watchHistoryDto, Principal principal) {
        User watcher =  userRepository.findByUsername(principal.getName()).orElseThrow(EntityNotFoundException::new);
        Optional<WatchHistory> watchHistory = watchHistoryRepository.findByUserIdAndVideoId(watcher.getUserId(), watchHistoryDto.videoId());
        if(watchHistory.isPresent()){
            return watchHistory.get();
        }
        WatchHistory wh = new WatchHistory();
        wh.setDate(watchHistoryDto.date() != null ? watchHistoryDto.date() : Instant.now());
        wh.setTimestamp(watchHistoryDto.timestamp() != null ? watchHistoryDto.timestamp() : 0L);
        wh.setVideoId(watchHistoryDto.videoId());
        wh.setUserId(watcher.getUserId());
        return watchHistoryRepository.save(wh);
    }
}
