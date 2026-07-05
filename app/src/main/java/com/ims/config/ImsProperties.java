package com.ims.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ims")
public class ImsProperties {

    private String role = "API";
    private Cors cors = new Cors();
    private Aws aws = new Aws();
    private Reports reports = new Reports();
    private Forecast forecast = new Forecast();
    private Reorder reorder = new Reorder();

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Cors getCors() { return cors; }
    public void setCors(Cors cors) { this.cors = cors; }
    public Aws getAws() { return aws; }
    public void setAws(Aws aws) { this.aws = aws; }
    public Reports getReports() { return reports; }
    public void setReports(Reports reports) { this.reports = reports; }
    public Forecast getForecast() { return forecast; }
    public void setForecast(Forecast forecast) { this.forecast = forecast; }
    public Reorder getReorder() { return reorder; }
    public void setReorder(Reorder reorder) { this.reorder = reorder; }

    public static class Cors {
        private String allowedOrigins = "http://localhost:5173";
        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }

    public static class Aws {
        private String region = "us-east-1";
        private boolean enabled = true;
        private Dynamodb dynamodb = new Dynamodb();
        private Sqs sqs = new Sqs();
        private Sns sns = new Sns();
        private S3 s3 = new S3();

        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Dynamodb getDynamodb() { return dynamodb; }
        public void setDynamodb(Dynamodb dynamodb) { this.dynamodb = dynamodb; }
        public Sqs getSqs() { return sqs; }
        public void setSqs(Sqs sqs) { this.sqs = sqs; }
        public Sns getSns() { return sns; }
        public void setSns(Sns sns) { this.sns = sns; }
        public S3 getS3() { return s3; }
        public void setS3(S3 s3) { this.s3 = s3; }
    }

    public static class Dynamodb {
        private String table = "ims-stock-movements";
        private String endpoint = "";
        public String getTable() { return table; }
        public void setTable(String table) { this.table = table; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    }

    public static class Sqs {
        private boolean enabled = true;
        private String queueUrl = "";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getQueueUrl() { return queueUrl; }
        public void setQueueUrl(String queueUrl) { this.queueUrl = queueUrl; }
    }

    public static class Sns {
        private boolean enabled = true;
        private String topicArn = "";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getTopicArn() { return topicArn; }
        public void setTopicArn(String topicArn) { this.topicArn = topicArn; }
    }

    public static class S3 {
        private String bucket = "ims-reports";
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
    }

    public static class Reports {
        private String localDir = "./reports";
        public String getLocalDir() { return localDir; }
        public void setLocalDir(String localDir) { this.localDir = localDir; }
    }

    public static class Forecast {
        private String provider = "ewma";
        private String bedrockModelId = "anthropic.claude-3-haiku-20240307-v1:0";
        private String claudeModel = "claude-opus-4-8";
        private String geminiModel = "gemini-2.5-flash";
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getBedrockModelId() { return bedrockModelId; }
        public void setBedrockModelId(String bedrockModelId) { this.bedrockModelId = bedrockModelId; }
        public String getClaudeModel() { return claudeModel; }
        public void setClaudeModel(String claudeModel) { this.claudeModel = claudeModel; }
        public String getGeminiModel() { return geminiModel; }
        public void setGeminiModel(String geminiModel) { this.geminiModel = geminiModel; }
    }

    public static class Reorder {
        private String cron = "0 0 * * * *";
        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
    }
}
