package com.ims.repository;

import com.ims.config.ImsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Writes reports to ./reports for the local profile. */
@Component
@ConditionalOnProperty(name = "ims.aws.enabled", havingValue = "false")
public class LocalReportStore implements ReportStore {

    private final Path dir;

    public LocalReportStore(ImsProperties props) {
        this.dir = Paths.get(props.getReports().getLocalDir());
    }

    @Override
    public String store(String reportId, String filename, byte[] content) {
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(reportId + "-" + filename);
            Files.write(file, content);
            return file.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write report", e);
        }
    }

    @Override
    public List<ReportDescriptor> list() {
        List<ReportDescriptor> result = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return result;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile).forEach(p -> {
                try {
                    String name = p.getFileName().toString();
                    String reportId = name.contains("-") ? name.substring(0, name.indexOf('-')) : name;
                    result.add(new ReportDescriptor(reportId, p.toAbsolutePath().toString(), Files.size(p)));
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list reports", e);
        }
        return result;
    }
}
