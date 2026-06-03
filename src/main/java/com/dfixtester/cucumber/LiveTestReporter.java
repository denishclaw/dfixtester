package com.dfixtester.cucumber;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestStepFinished;

public class LiveTestReporter implements ConcurrentEventListener {

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseStarted.class, this::handleTestCaseStarted);
        publisher.registerHandlerFor(TestStepFinished.class, this::handleTestStepFinished);
    }

    private void handleTestCaseStarted(TestCaseStarted event) {
        System.out.println("@@TEST_EVENT@@{\"event\":\"TestCaseStarted\",\"name\":\"" + escapeJson(event.getTestCase().getName()) + "\"}");
    }

    private void handleTestStepFinished(TestStepFinished event) {
        if (event.getTestStep() instanceof PickleStepTestStep) {
            PickleStepTestStep step = (PickleStepTestStep) event.getTestStep();
            String status = event.getResult().getStatus().name();
            String stepText = step.getStep().getKeyword() + step.getStep().getText();
            String error = event.getResult().getError() != null ? event.getResult().getError().getMessage() : "";
            
            System.out.println("@@TEST_EVENT@@{\"event\":\"TestStepFinished\",\"name\":\"" + escapeJson(stepText) + "\",\"status\":\"" + status + "\",\"error\":\"" + escapeJson(error) + "\"}");
        }
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\b", "\\b")
                    .replace("\f", "\\f").replace("\n", "\\n").replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}