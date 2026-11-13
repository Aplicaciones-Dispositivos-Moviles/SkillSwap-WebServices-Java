package com.innovify.skillswap.shared.infrastructure.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.innovify.skillswap.shared.domain.events.DomainEvent;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SpringDomainEventPublisherTest {

    record SampleEvent(String name) implements DomainEvent { }

    record OtherEvent() implements DomainEvent { }

    static class RecordingHandler implements DomainEventHandler<SampleEvent> {
        final List<String> handled = new ArrayList<>();

        @Override
        public void handle(SampleEvent event) {
            handled.add(event.name());
        }
    }

    static class FailingHandler implements DomainEventHandler<SampleEvent> {
        @Override
        public void handle(SampleEvent event) {
            throw new IllegalStateException("boom");
        }
    }

    static class OtherHandler implements DomainEventHandler<OtherEvent> {
        int calls;

        @Override
        public void handle(OtherEvent event) {
            calls++;
        }
    }

    @Test
    void publish_runsEveryHandlerOfTheEvent() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("first", RecordingHandler.class);
            context.registerBean("second", RecordingHandler.class);
            context.refresh();

            new SpringDomainEventPublisher(context).publish(new SampleEvent("passed"));

            assertThat(context.getBean("first", RecordingHandler.class).handled).containsExactly("passed");
            assertThat(context.getBean("second", RecordingHandler.class).handled).containsExactly("passed");
        }
    }

    @Test
    void publish_whenAHandlerFails_doesNotThrowAndTheOthersStillRun() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("failing", FailingHandler.class);
            context.registerBean("recording", RecordingHandler.class);
            context.refresh();

            assertThatCode(() -> new SpringDomainEventPublisher(context).publish(new SampleEvent("passed")))
                    .doesNotThrowAnyException();
            assertThat(context.getBean(RecordingHandler.class).handled).containsExactly("passed");
        }
    }

    @Test
    void publish_withoutHandlers_completes() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.refresh();

            assertThatCode(() -> new SpringDomainEventPublisher(context).publish(new SampleEvent("passed")))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void publish_doesNotRunTheHandlersOfOtherEvents() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(OtherHandler.class);
            context.refresh();

            new SpringDomainEventPublisher(context).publish(new SampleEvent("passed"));

            assertThat(context.getBean(OtherHandler.class).calls).isZero();
        }
    }

    @Test
    void publish_insideATransaction_runsTheHandlersAfterTheCommit() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RecordingHandler.class);
            context.refresh();
            RecordingHandler handler = context.getBean(RecordingHandler.class);

            TransactionSynchronizationManager.initSynchronization();
            try {
                new SpringDomainEventPublisher(context).publish(new SampleEvent("passed"));
                assertThat(handler.handled).isEmpty();

                TransactionSynchronizationManager.getSynchronizations()
                        .forEach(TransactionSynchronization::afterCommit);
                assertThat(handler.handled).containsExactly("passed");
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }
}
