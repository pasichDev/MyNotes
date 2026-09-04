package com.pasich.mynotes.data.sync;

import java.io.IOException;

/**
 * Indicates that bytes did not satisfy the immutable attachment contract.
 *
 * <p>This is deliberately distinct from a transport failure. An object discovered after a lost
 * HTTP response can only confirm an ambiguous request; it can never turn a hash or size mismatch
 * into success.
 */
public final class AttachmentIntegrityException extends IOException {

    public AttachmentIntegrityException(String message) {
        super(message);
    }

    public AttachmentIntegrityException(String message, Throwable cause) {
        super(message, cause);
    }
}
