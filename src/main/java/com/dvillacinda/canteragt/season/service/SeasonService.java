package com.dvillacinda.canteragt.season.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.season.dto.SeasonCreateRequest;
import com.dvillacinda.canteragt.season.dto.SeasonResponse;
import com.dvillacinda.canteragt.season.mapper.SeasonMapper;
import com.dvillacinda.canteragt.season.repository.SeasonRepository;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class SeasonService {
    private final SeasonRepository seasonRepository;
    private final SeasonMapper seasonMapper;

    public SeasonResponse getSeasonById(UUID seasonId) {
        var season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new NotFoundException("Season with id " + seasonId + " not founded"));

        return seasonMapper.toResponse(season);
    }

    public SeasonResponse createSeason(SeasonCreateRequest request) {
        return seasonMapper.toResponse(seasonRepository.save(seasonMapper.toEntity(request)));
    }
}
