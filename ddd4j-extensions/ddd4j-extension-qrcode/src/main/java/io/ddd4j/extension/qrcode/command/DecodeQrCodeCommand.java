package io.ddd4j.extension.qrcode.command;

import io.github.hiwepy.zxing.model.QrCodeDecodeRequest;
import lombok.Builder;
import lombok.Getter;

/** Application command for QR code decoding. */
@Getter
@Builder
public final class DecodeQrCodeCommand {

    private final String correlationId;
    private final QrCodeDecodeRequest request;
}
