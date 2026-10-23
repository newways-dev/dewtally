package com.kate.dewtally.extraction;

import com.kate.dewtally.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real call to the language model to check key, network and configuration.
 * Costs a fraction of a cent, so it only runs on demand, never in CI:
 * <p>
 * .\mvnw.cmd test -Dtest=OpenAiConnectionManualTest -Dai=true
 */
@SpringBootTest(properties = "dewtally.validation.kosit.enabled=false")
@Import(TestcontainersConfiguration.class)
@EnabledIfSystemProperty(named = "ai", matches = "true")
class OpenAiConnectionManualTest {

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Test
    void modelAnswers() {
        ChatClient chatClient = chatClientBuilder.build();

        String answer = chatClient.prompt()
                .user("Antworte nur mit dem Wort OK, ohne Satzzeichen.")
                .call()
                .content();

        System.out.println("Model answered: " + answer);
        assertThat(answer).containsIgnoringCase("OK");
    }
}