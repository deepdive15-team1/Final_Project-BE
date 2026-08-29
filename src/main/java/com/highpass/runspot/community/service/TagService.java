package com.highpass.runspot.community.service;

import com.highpass.runspot.community.domain.Tag;
import com.highpass.runspot.community.repository.TagRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TagService {
    private final TagRepository tags;
    private final TagCreator creator;

    public List<Tag> resolve(List<String> names) {
        return names.stream()
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .map(String::toLowerCase)
                .distinct()
                .map(this::resolve)
                .toList();
    }

    private Tag resolve(String name) {
        return tags.findByName(name).orElseGet(() -> create(name));
    }

    private Tag create(String name) {
        try {
            return creator.create(name);
        } catch (DataIntegrityViolationException e) {
            return tags.findByName(name).orElseThrow(() -> e);
        }
    }
}
