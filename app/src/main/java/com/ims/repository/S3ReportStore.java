package com.ims.repository;

import com.ims.config.ImsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Uploads reports to S3 for prod. Downloads go through short-lived presigned
 * GET URLs so the bucket stays fully private (public access block on) and no
 * AWS credentials are ever needed by the browser.
 */
@Component
@ConditionalOnProperty(name = "ims.aws.enabled", havingValue = "true", matchIfMissing = true)
public class S3ReportStore implements ReportStore {

    private static final String PREFIX = "reports/";

    /**
     * Security vs convenience trade-off: long enough to click a link from the
     * UI, short enough that a leaked URL goes stale quickly. Note the URL also
     * dies when the signing session credentials expire, whichever comes first.
     */
    private static final Duration URL_TTL = Duration.ofMinutes(15);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public S3ReportStore(S3Client s3, S3Presigner presigner, ImsProperties props) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = props.getAws().getS3().getBucket();
    }

    @Override
    public ReportDescriptor store(String reportId, String filename, byte[] content) {
        String key = PREFIX + reportId + "-" + filename;
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType("text/csv")
                        .build(),
                RequestBody.fromBytes(content));
        return new ReportDescriptor(reportId, filename, "s3://" + bucket + "/" + key, content.length, presign(key));
    }

    @Override
    public List<ReportDescriptor> list() {
        List<ReportDescriptor> result = new ArrayList<>();
        var res = s3.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(PREFIX)
                .build());
        res.contents().forEach(obj -> {
            String[] parts = ReportStore.splitStoredName(obj.key().substring(PREFIX.length()));
            result.add(new ReportDescriptor(
                    parts[0], parts[1], "s3://" + bucket + "/" + obj.key(), obj.size(), presign(obj.key())));
        });
        return result;
    }

    /** Presigning is a local signature computation — no network call, cheap per object. */
    private String presign(String key) {
        var request = GetObjectPresignRequest.builder()
                .signatureDuration(URL_TTL)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build();
        return presigner.presignGetObject(request).url().toString();
    }
}
