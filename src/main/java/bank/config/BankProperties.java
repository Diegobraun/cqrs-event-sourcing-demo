package bank.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "bank")
public record BankProperties(@DefaultValue("3") int snapshotEvery,
                             @DefaultValue("50ms") Duration projectionPollInterval,
                             @DefaultValue("10s") Duration transferTimeout,
                             @DefaultValue("1s") Duration transferTimeoutCheckInterval) {
}
