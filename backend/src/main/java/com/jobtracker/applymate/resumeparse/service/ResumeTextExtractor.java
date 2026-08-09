package com.jobtracker.applymate.resumeparse.service;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

@Component
public class ResumeTextExtractor {
    private final Tika tika = new Tika();

    public String extract(Path path) {
        try {
            return tika.parseToString(path);
        } catch (Exception e) {
            throw new IllegalArgumentException("简历文本提取失败：" + safeMessage(e));
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
