package com.jobtracker.applymate.profile.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobtracker.applymate.profile.dto.ProfileDiffResponse;

public interface ProfileDiffService { ProfileDiffResponse compare(JsonNode current, JsonNode draft); }
