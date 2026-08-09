package com.jobtracker.applymate.profile.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobtracker.applymate.profile.dto.ProfileDiffResponse;
import com.jobtracker.applymate.profile.service.ProfileDiffService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class ProfileDiffServiceImpl implements ProfileDiffService {
    public ProfileDiffResponse compare(JsonNode current, JsonNode draft) {
        List<ProfileDiffResponse.Change> additions = new ArrayList<>(), updates = new ArrayList<>(), conflicts = new ArrayList<>();
        walk("", current, draft, additions, updates, conflicts);
        return new ProfileDiffResponse(additions, updates, conflicts);
    }
    private void walk(String path, JsonNode current, JsonNode draft, List<ProfileDiffResponse.Change> additions, List<ProfileDiffResponse.Change> updates, List<ProfileDiffResponse.Change> conflicts) {
        if (draft == null || draft.isMissingNode() || draft.isNull()) return;
        if (current == null || current.isMissingNode() || current.isNull()) { additions.add(change(path, null, draft)); return; }
        if (current.isObject() && draft.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = draft.fields();
            while (fields.hasNext()) { Map.Entry<String, JsonNode> field = fields.next(); walk(path.isEmpty() ? field.getKey() : path + "." + field.getKey(), current.get(field.getKey()), field.getValue(), additions, updates, conflicts); }
            return;
        }
        if (!current.equals(draft)) {
            // A non-empty confirmed Profile value requires user choice rather than automatic replacement.
            (path.startsWith("fieldMeta") ? updates : conflicts).add(change(path, current, draft));
        }
    }
    private ProfileDiffResponse.Change change(String path, JsonNode current, JsonNode draft) { return new ProfileDiffResponse.Change(path, current == null ? null : current, draft); }
}
