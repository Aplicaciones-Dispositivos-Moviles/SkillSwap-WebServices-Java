package com.innovify.skillswap.learningpathengine.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AdvancedPathUnlock;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.api.Test;

class AdvancedPathUnlockTest {

    @Test
    void newUnlock_isAvailableUntilItStartsAPath() {
        AdvancedPathUnlock unlock = new AdvancedPathUnlock(3, 40);
        assertThat(unlock.isAvailable()).isTrue();
        assertThat(unlock.getGrantedAt()).isNotNull();

        unlock.useFor(9);

        assertThat(unlock.isAvailable()).isFalse();
        assertThat(unlock.getLearningPathId()).isEqualTo(9);
        assertThat(unlock.getUsedAt()).isNotNull();
        assertThatThrownBy(() -> unlock.useFor(10)).isInstanceOf(DomainException.class);
    }

    @Test
    void invalidData_isRejected() {
        assertThatThrownBy(() -> new AdvancedPathUnlock(0, 40)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new AdvancedPathUnlock(3, 0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new AdvancedPathUnlock(3, 40).useFor(0)).isInstanceOf(DomainException.class);
    }
}
