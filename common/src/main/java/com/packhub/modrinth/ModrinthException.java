package com.packhub.modrinth;

import com.packhub.source.SourceException;

/** Modrinth 调用失败。 */
public class ModrinthException extends SourceException {
    public ModrinthException(String message, int statusCode, Throwable cause) {
        super(message, statusCode, cause);
    }

    public ModrinthException(String message, int statusCode) {
        this(message, statusCode, null);
    }
}
