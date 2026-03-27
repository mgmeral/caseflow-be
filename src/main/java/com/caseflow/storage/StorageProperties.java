package com.caseflow.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "caseflow.storage")
public class StorageProperties {

    /** Root directory for local file storage. */
    private String rootPath = "./storage-data";

    /** Storage provider: 'local' (default) or 'minio' */
    private String provider = "local";

    private MinioProperties minio = new MinioProperties();

    public String getRootPath() { return rootPath; }
    public void setRootPath(String rootPath) { this.rootPath = rootPath; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public MinioProperties getMinio() { return minio; }
    public void setMinio(MinioProperties minio) { this.minio = minio; }

    public static class MinioProperties {
        private String endpoint = "http://localhost:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucket = "caseflow";

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
    }
}
