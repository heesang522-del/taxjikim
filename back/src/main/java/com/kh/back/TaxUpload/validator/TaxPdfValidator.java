package com.kh.back.TaxUpload.validator;

import com.kh.back.common.exception.ApiException;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class TaxPdfValidator {
    private static final int MAX_PDF_BYTES = 6 * 1024 * 1024;
    private static final int MAX_PDF_PAGES = 100;

    public byte[] validateAndRead(MultipartFile file) throws IOException {
        String name = file == null ? null : file.getOriginalFilename();
        if (file == null || file.isEmpty() || name == null || !name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw securityError("PDF_IMPORT_ERROR", "유효한 PDF 파일을 선택해 주세요.");
        }
        if (file.getSize() > MAX_PDF_BYTES) {
            throw securityError("PDF_SIZE_LIMIT", "PDF는 파일당 6MB 이하만 처리할 수 있습니다.");
        }
        // 클라이언트가 보낸 크기와 무관하게 실제 읽는 바이트 수도 제한한다.
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_PDF_BYTES + 1);
            validatePdfBytes(bytes);
            return bytes;
        }
    }

    public void validateDocument(PDDocument document) {
        if (document.isEncrypted()) throw securityError("PDF_IMPORT_ERROR", "암호를 해제한 PDF를 선택해 주세요.");
        if (document.getNumberOfPages() > MAX_PDF_PAGES) {
            throw securityError("PDF_PAGE_LIMIT", "PDF는 최대 100페이지까지 처리할 수 있습니다.");
        }
        // 위험 기능 차단이며 백신 엔진의 악성코드 검사와는 구분한다.
        rejectActiveContent(document);
    }

    public void validatePdfBytes(byte[] bytes) {
        if (bytes.length > MAX_PDF_BYTES) throw securityError("PDF_SIZE_LIMIT", "PDF는 파일당 6MB 이하만 처리할 수 있습니다.");
        String header = new String(bytes, 0, Math.min(bytes.length, 8), StandardCharsets.US_ASCII);
        String tail = new String(bytes, Math.max(0, bytes.length - 1024), Math.min(bytes.length, 1024), StandardCharsets.ISO_8859_1);
        if (!(header.startsWith("%PDF-1.") || header.startsWith("%PDF-2.")) || !tail.contains("%%EOF")) {
            throw securityError("INVALID_PDF_SIGNATURE", "실제 PDF 형식이 아니거나 파일이 손상되었습니다.");
        }
    }

    public void rejectActiveContent(PDDocument document) {
        Set<COSBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<COSBase> pending = new ArrayDeque<>();
        pending.add(document.getDocument().getTrailer());
        Set<String> blockedKeys = Set.of("JS", "JavaScript", "OpenAction", "AA", "EmbeddedFiles", "EF", "XFA", "RichMediaContent");
        Set<String> blockedActions = Set.of("JavaScript", "Launch", "GoToR", "GoToE", "SubmitForm", "ImportData", "Rendition", "RichMediaExecute");
        while (!pending.isEmpty()) {
            COSBase value = pending.removeFirst();
            if (!visited.add(value)) continue;
            if (visited.size() > 50000) throw securityError("PDF_OBJECT_LIMIT", "PDF 문서 구조가 너무 복잡해 처리할 수 없습니다.");
            if (value instanceof COSObject object) {
                if (object.getObject() != null) pending.add(object.getObject());
            } else if (value instanceof COSDictionary dictionary) {
                for (COSName key : dictionary.keySet()) {
                    if (blockedKeys.contains(key.getName())) throw securityError("UNSAFE_PDF_FEATURE", "스크립트·자동 실행·첨부파일이 포함된 PDF는 지원하지 않습니다.");
                    COSBase child = dictionary.getItem(key);
                    if (child != null) pending.add(child);
                }
                COSName action = dictionary.getCOSName(COSName.S);
                COSName subtype = dictionary.getCOSName(COSName.SUBTYPE);
                if ((action != null && blockedActions.contains(action.getName()))
                        || (subtype != null && Set.of("RichMedia", "Movie", "Sound", "FileAttachment").contains(subtype.getName()))) {
                    throw securityError("UNSAFE_PDF_FEATURE", "외부 실행이나 미디어 첨부 기능이 포함된 PDF는 지원하지 않습니다.");
                }
            } else if (value instanceof COSArray array) {
                for (COSBase child : array) if (child != null) pending.add(child);
            }
        }
    }

    private ApiException securityError(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
