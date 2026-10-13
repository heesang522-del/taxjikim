package com.kh.back.TaxUpload.controller;
import com.kh.back.TaxUpload.service.YearEndTaxService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/tax")
public class YearEndTaxController {

    private final YearEndTaxService yearEndTaxService;

    public YearEndTaxController(YearEndTaxService yearEndTaxService) {
        this.yearEndTaxService = yearEndTaxService;
    }

    @PostMapping("/upload-pdf")
    public ResponseEntity<String> uploadTaxPdf(
            @RequestParam("file") MultipartFile file, // 단일 파일로 수신
            @RequestParam("userId") Long userId,
            @RequestParam("taxInputId") Long taxInputId,
            @RequestParam("taxYear") int taxYear) {

        if (file.isEmpty() || !file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.badRequest().body("유효한 PDF 파일을 첨부해주세요.");
        }

        try {
            yearEndTaxService.processTaxPdf(file.getInputStream(), userId, taxInputId, taxYear);
            return ResponseEntity.ok("PDF 데이터 적재가 완료되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("처리 실패: " + e.getMessage());
        }
    }
}