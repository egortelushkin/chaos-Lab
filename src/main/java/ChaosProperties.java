import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chaos")
public class ChaosProperties {

    private boolean enabled = true;

    private Http http = new Http();
    private Kafka kafka = new Kafka();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Http getHttp() { return http; }
    public void setHttp(Http http) { this.http = http; }

    public Kafka getKafka() { return kafka; }
    public void setKafka(Kafka kafka) { this.kafka = kafka; }

    public static class Http {
        private long maxDelayMs = 0;
        private double delayProbability = 0.0;
        // можно добавить errorProbability и т.д.

        public long getMaxDelayMs() { return maxDelayMs; }
        public void setMaxDelayMs(long maxDelayMs) { this.maxDelayMs = maxDelayMs; }

        public double getDelayProbability() { return delayProbability; }
        public void setDelayProbability(double delayProbability) { this.delayProbability = delayProbability; }
    }

    public static class Kafka {
        private double failureProbability = 0.0;
        public double getFailureProbability() { return failureProbability; }
        public void setFailureProbability(double failureProbability) { this.failureProbability = failureProbability; }
    }
}