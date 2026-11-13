package com.innovify.skillswap.learningpathengine.application.fakes;

import com.innovify.skillswap.recognitionincentives.application.acl.RecognitionContextFacade;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The advanced path unlocks each student redeemed, set by each test. */
public class FakeRecognitionContextFacade implements RecognitionContextFacade {

    private final Map<Integer, List<Integer>> redemptions = new HashMap<>();

    public FakeRecognitionContextFacade redeemed(int userId, int redemptionId) {
        redemptions.computeIfAbsent(userId, id -> new ArrayList<>()).add(redemptionId);
        return this;
    }

    @Override
    public List<Integer> getAdvancedPathUnlockRedemptionIds(int userId) {
        return List.copyOf(redemptions.getOrDefault(userId, List.of()));
    }
}
