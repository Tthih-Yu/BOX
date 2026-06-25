package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.dto.LabelDtos;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CodeRenderService {
    public LabelDtos.CodeRenderResponse render(LabelDtos.CodeRenderRequest req) {
        if (req == null) req = new LabelDtos.CodeRenderRequest();
        String text = req.text == null ? "" : req.text.trim();
        if (text.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "生成内容不能为空");
        if (text.length() > 1024) throw new BusinessException(ErrorCode.PARAM_ERROR, "生成内容过长，最多 1024 个字符");

        BarcodeFormat format = parseFormat(req.format);
        if (format == BarcodeFormat.CODE_128 && !isCode128Safe(text)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "CODE_128 条形码仅支持英文、数字和常用符号；中文、物料名称、工位描述请生成二维码");
        }
        int width = clamp(req.width, format == BarcodeFormat.QR_CODE ? 240 : 320, 80, 1200);
        int height = clamp(req.height, format == BarcodeFormat.QR_CODE ? width : 120, 60, 800);
        if (format == BarcodeFormat.QR_CODE) height = width;

        try {
            String svg;
            if (format == BarcodeFormat.CODE_128) {
                BitMatrix matrix = new Code128Writer().encode(text, BarcodeFormat.CODE_128, width, height);
                svg = toSvg(matrix, text, format, Boolean.TRUE.equals(req.includeText));
            } else {
                Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
                hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
                hints.put(EncodeHintType.MARGIN, 1);
                BitMatrix matrix = new MultiFormatWriter().encode(text, format, width, height, hints);
                svg = toSvg(matrix, text, format, Boolean.TRUE.equals(req.includeText));
            }
            LabelDtos.CodeRenderResponse r = new LabelDtos.CodeRenderResponse();
            r.text = text;
            r.format = format.name();
            r.svg = svg;
            r.dataUri = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
            return r;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "生成码失败：" + e.getMessage());
        }
    }

    private BarcodeFormat parseFormat(String value) {
        String v = value == null ? "QR_CODE" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (v) {
            case "QR", "QRCODE", "QR_CODE" -> BarcodeFormat.QR_CODE;
            case "CODE128", "CODE_128", "BARCODE", "BARCODE_1D" -> BarcodeFormat.CODE_128;
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的码类型：" + value + "，仅支持 QR_CODE、CODE_128");
        };
    }

    private int clamp(Integer value, int fallback, int min, int max) {
        int v = value == null ? fallback : value;
        return Math.max(min, Math.min(max, v));
    }

    private boolean isCode128Safe(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 32 || c > 126) return false;
        }
        return true;
    }

    private String toSvg(BitMatrix matrix, String text, BarcodeFormat format, boolean includeText) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int textHeight = includeText ? 32 : 0;
        StringBuilder sb = new StringBuilder(width * height / 2);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width).append("\" height=\"").append(height + textHeight).append("\" viewBox=\"0 0 ").append(width).append(' ').append(height + textHeight).append("\" role=\"img\" aria-label=\"").append(esc(text)).append("\">");
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>");
        for (int y = 0; y < height; y++) {
            int start = -1;
            for (int x = 0; x <= width; x++) {
                boolean black = x < width && matrix.get(x, y);
                if (black && start < 0) start = x;
                if ((!black || x == width) && start >= 0) {
                    sb.append("<rect x=\"").append(start).append("\" y=\"").append(y).append("\" width=\"").append(x - start).append("\" height=\"1\" fill=\"#111\"/>");
                    start = -1;
                }
            }
        }
        if (includeText) {
            String label = format == BarcodeFormat.QR_CODE && text.length() > 48 ? text.substring(0, 48) + "..." : text;
            sb.append("<text x=\"50%\" y=\"").append(height + 22).append("\" text-anchor=\"middle\" font-family=\"Arial, Microsoft YaHei, sans-serif\" font-size=\"18\" fill=\"#111\">").append(esc(label)).append("</text>");
        }
        sb.append("</svg>");
        return sb.toString();
    }

    private String esc(String v) {
        return v == null ? "" : v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
