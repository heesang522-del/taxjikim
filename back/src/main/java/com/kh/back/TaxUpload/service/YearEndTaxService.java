package com.kh.back.TaxUpload.service;

import com.kh.back.TaxUpload.dto.TaxTransactionDto;

import com.kh.back.TaxUpload.mapper.TaxMapper;
import com.kh.back.common.exception.ApiException;
import com.kh.back.post.dto.FileMetadataDto;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.time.LocalDate;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.text.TextPosition;

@Service
public class YearEndTaxService {
    private final TaxMapper taxMapper;
    private static final int MAX_PDF_BYTES = 6 * 1024 * 1024;
    private static final int MAX_PDF_PAGES = 100;
    private static final int MAX_TEXT_CHARACTERS = 2_000_000;


    // 세무 원본은 /uploads/** 공개 정적 리소스 경로와 분리한다.
    private Path storageDirectory = Path.of("storage", "tax-pdfs").toAbsolutePath();
    public YearEndTaxService(TaxMapper taxMapper) { this.taxMapper = taxMapper; }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> uploadPdf(MultipartFile file, Long userId, int taxYear) throws IOException {
        return processPdf(file, userId, taxYear);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> uploadLedgerPdf(MultipartFile file, Long userId, int taxYear) throws IOException {
        return processPdf(file, userId, taxYear);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> uploadEvidencePdf(MultipartFile file, Long userId, int taxYear) throws IOException {
        return processPdf(file, userId, taxYear);
    }

    private Map<String, Object> processPdf(MultipartFile file, Long userId, int taxYear) throws IOException {
        String originalName = file.getOriginalFilename();
        if (file.isEmpty() || originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw badRequest("유효한 PDF 파일을 선택해 주세요.");
        }
        if (file.getSize() > MAX_PDF_BYTES) throw securityError("PDF_SIZE_LIMIT", "PDF는 파일당 6MB 이하만 처리할 수 있습니다.");
        byte[] bytes;
        try (var input = file.getInputStream()) { bytes = input.readNBytes(MAX_PDF_BYTES + 1); }
        validatePdfBytes(bytes);
        final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        List<TaxTransactionDto> transactions;
        int skipped;
        Map<String, Object> evidence = null;
        // PDF 검증·파싱이 성공한 다음에만 파일 저장과 DB 변경을 시작한다.
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) throw badRequest("암호를 해제한 PDF를 선택해 주세요.");
            if (document.getNumberOfPages() > MAX_PDF_PAGES) throw securityError("PDF_PAGE_LIMIT", "PDF는 최대 100페이지까지 처리할 수 있습니다.");
            rejectActiveContent(document);
            PDFTextStripper stripper = new PDFTextStripper() {
                private int extractedCharacters;
                @Override protected void writeString(String value, List<TextPosition> positions) throws IOException {
                    extractedCharacters += value.length();
                    if (extractedCharacters > MAX_TEXT_CHARACTERS || System.nanoTime() > deadline) {
                        throw securityError("PDF_PARSE_LIMIT", "PDF 분석 범위를 초과했습니다. 파일을 나누어 다시 시도해 주세요.");
                    }
                    super.writeString(value, positions);
                }
            };
            stripper.setSortByPosition(true);
            List<PdfPageText> pages = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                if (System.nanoTime() > deadline) throw securityError("PDF_PARSE_LIMIT", "PDF 분석 대기 시간을 초과했습니다.");
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(new PdfPageText(page, stripper.getText(document)));
            }
            String text = String.join("\n", pages.stream().map(PdfPageText::text).toList());
            if (text.isBlank()) throw badRequest("추출 가능한 글자가 없습니다. 스캔 PDF의 OCR은 아직 지원하지 않습니다.");
            // 모든 추출 내역을 같은 편집 목록에 보내고 확정 거래 저장은 사용자 확인 뒤 수행한다.
            transactions = List.of();
            evidence = parseEvidence(pages, taxYear);
            skipped = 0;
        } catch (InvalidPasswordException error) {
            throw badRequest("암호를 해제한 PDF를 선택해 주세요.");
        } catch (IOException error) {
            throw badRequest("PDF를 읽지 못했습니다. 파일 손상 여부를 확인해 주세요.");
        }

        // 비회원은 추출만 체험하며 개인 원본과 DB 기록을 서버에 남기지 않는다.
        if (userId == null) return buildPdfResponse(null, originalName, transactions, skipped, false, evidence);
        Files.createDirectories(storageDirectory);
        Path savedPath = storageDirectory.resolve(UUID.randomUUID() + ".pdf");
        Files.write(savedPath, bytes, StandardOpenOption.CREATE_NEW);
        // DB 롤백 시 파일도 정리한다. 정상 커밋된 원본은 보존한다.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) cleanup(savedPath);
                }
            });
        }
        try {
            FileMetadataDto metadata = new FileMetadataDto();
            metadata.setUserId(userId);
            metadata.setStoredFileName(savedPath.getFileName().toString());
            metadata.setOriginalFileName(originalName);
            metadata.setFilePath(savedPath.toString());
            metadata.setFileSize((long) bytes.length);
            metadata.setFileType(transactions.isEmpty() ? "TAX_EVIDENCE" : "TAX_LEDGER");
            taxMapper.insertPdfFile(metadata);
            if (!transactions.isEmpty()) {
                Long inputId = findOrCreateInput(userId, taxYear);
                transactions.forEach(transaction -> transaction.setTaxInputId(inputId));
                taxMapper.insertTaxTransactions(transactions);
            }
            return buildPdfResponse(metadata.getFileId(), originalName, transactions, skipped, true, evidence);
        } catch (RuntimeException error) {
            cleanup(savedPath);
            throw error;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public TaxTransactionDto saveReviewedTransaction(TaxTransactionDto.ReviewedTransactionRequest request, Long userId) {
        return persistReviewedTransaction(request, userId, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public TaxTransactionDto updateReviewedTransaction(TaxTransactionDto.ReviewedTransactionRequest request, Long userId) {
        return persistReviewedTransaction(request, userId, true);
    }

    private TaxTransactionDto persistReviewedTransaction(TaxTransactionDto.ReviewedTransactionRequest request, Long userId, boolean update) {
        if (userId == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "로그인이 필요합니다.");
        if (request == null || request.fileId() == null || request.fileId() <= 0
                || request.itemIndex() == null || request.itemIndex() < 0 || request.itemIndex() > 100000
                || request.taxYear() == null || request.taxYear() < 1900 || request.taxYear() > 9999
                || request.transactionDate() == null || request.transactionDate().getYear() != request.taxYear()
                || !Set.of("INCOME", "EXPENSE").contains(Objects.toString(request.transactionType(), ""))
                || request.amount() == null || request.amount().signum() <= 0
                || request.amount().stripTrailingZeros().scale() > 0
                || request.amount().compareTo(BigDecimal.valueOf(9007199254740991L)) > 0
                || request.description() == null || request.description().isBlank() || request.description().length() > 200
                || request.category() == null
                || !("INCOME".equals(request.transactionType()) ? INCOME_CATEGORIES : EXPENSE_CATEGORIES).contains(request.category())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "검토한 거래의 날짜·구분·금액·내용·카테고리를 확인해 주세요.");
        }
        if (!request.fileId().equals(taxMapper.lockOwnedPdf(request.fileId(), userId))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PDF_NOT_FOUND", "본인이 업로드한 세무 PDF를 찾을 수 없습니다.");
        }
        // 응답 유실 후 재시도에도 최초 저장 결과를 그대로 반환한다.
        TaxTransactionDto existing = taxMapper.findReviewedTransaction(request.fileId(), request.itemIndex(), userId);
        if (!update && existing != null) return existing;
        if (update && existing == null) throw new ApiException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "수정할 검토 거래를 찾을 수 없습니다.");
        TaxTransactionDto transaction = new TaxTransactionDto();
        transaction.setTaxInputId(findOrCreateInput(userId, request.taxYear()));
        transaction.setUserId(userId);
        transaction.setTransactionType(request.transactionType());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setTaxYear(request.taxYear());
        transaction.setCategory(request.category());
        transaction.setDescription(request.description().trim());
        transaction.setAmount(request.amount());
        transaction.setEdited(true);
        transaction.setSourceFileId(request.fileId());
        transaction.setSourceItemIndex(request.itemIndex());
        // 수정은 기존 ID를 유지하고 최초 등록만 새 거래를 생성한다.
        if (update) {
            transaction.setTransactionId(existing.getTransactionId());
            taxMapper.updateReviewedTransaction(transaction);
        } else {
            taxMapper.insertReviewedTransaction(transaction);
        }
        return transaction;
    }

    private Long findOrCreateInput(Long userId, int year) {
        Long existing = taxMapper.findTaxInputIdByUserAndYear(userId, year);
        if (existing != null) return existing;
        Map<String, Object> values = new HashMap<>();
        values.put("userId", userId); values.put("taxYear", year);
        values.put("region", "미지정"); values.put("job", "미지정");
        values.put("incomeType", "FREELANCER"); values.put("autoWithholding", true);
        values.put("totalIncome", BigDecimal.ZERO); values.put("totalExpense", BigDecimal.ZERO); values.put("expectedTax", BigDecimal.ZERO);
        taxMapper.insertTaxInput(values);
        return ((Number) values.get("taxInputId")).longValue();
    }

    // 별도 응답 타입 없이 기존 화면이 사용하는 JSON 필드를 유지한다.
    private Map<String, Object> buildPdfResponse(Long fileId, String originalName,
            List<TaxTransactionDto> transactions, int skipped, boolean persisted, Map<String, Object> evidence) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileId", fileId);
        result.put("originalFileName", originalName);
        result.put("transactions", transactions);
        result.put("skippedCount", skipped);
        result.put("persisted", persisted);
        result.put("evidence", evidence);
        return result;
    }

    private Map<String, Object> buildReviewItem(String label, BigDecimal amount, int page,
            boolean confirmed, Object... details) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("reportedAmount", amount);
        item.put("page", page);
        item.put("confirmed", confirmed);
        item.put("transactionDate", details.length > 0 ? details[0] : null);
        item.put("valueKind", details.length > 1 ? details[1] : "SUMMARY");
        item.put("transactionMonth", details.length > 2 ? details[2] : null);
        item.put("transactionType", details.length > 3 ? details[3] : null);
        return item;
    }

    private ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "PDF_IMPORT_ERROR", message); }
    private void cleanup(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException error) {
            org.slf4j.LoggerFactory.getLogger(YearEndTaxService.class).warn("롤백된 PDF 임시 파일 정리에 실패했습니다.", error);
        }
    }

    // 장부와 증빙의 파싱 책임은 기존 서비스 안의 별도 메서드로 구분한다.
    record PdfPageText(int page, String text) {}

    private static final List<String> INCOME_CATEGORIES = List.of("프리랜서 용역비", "인세·저작권료", "광고·후원 수익", "기타 수입");
    private static final List<String> EXPENSE_CATEGORIES = List.of("장비·소프트웨어", "외주·인건비", "교통·출장", "통신비", "임차료·작업공간", "광고·마케팅", "교육·도서", "기타 경비");
    private static final Pattern DATE = Pattern.compile("(?<!\\d)(\\d{4})\\s*(?:[-/.]|년)\\s*(\\d{1,2})\\s*(?:[-/.]|월)\\s*(\\d{1,2})\\s*일?(?!\\d)");
    private static final Pattern KIND = Pattern.compile("(?<!\\S)(수입|입금|지출|출금)(?!\\S)");
    private static final Pattern AMOUNT = Pattern.compile("(?:^|\\s)(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원?\\s*$");

    public List<TaxTransactionDto> parseTransactions(String text, Long userId, int taxYear) {
        List<TaxTransactionDto> result = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            var dateMatch = DATE.matcher(line);
            var kindMatch = KIND.matcher(line);
            var amountMatch = AMOUNT.matcher(line);
            if (!dateMatch.find() || !kindMatch.find() || !amountMatch.find()) continue;
            try {
                LocalDate date = LocalDate.of(Integer.parseInt(dateMatch.group(1)), Integer.parseInt(dateMatch.group(2)), Integer.parseInt(dateMatch.group(3)));
                if (date.getYear() != taxYear) continue;
                boolean income = Set.of("수입", "입금").contains(kindMatch.group(1));
                String category = (income ? INCOME_CATEGORIES : EXPENSE_CATEGORIES).stream().filter(line::contains).findFirst().orElse(null);
                if (category == null) continue;
                String memo = line.substring(0, amountMatch.start()).replace(dateMatch.group(), "").replace(kindMatch.group(), "").replace(category, "").trim();
                BigDecimal amount = new BigDecimal(amountMatch.group(1).replace(",", ""));
                if (memo.isBlank() || amount.signum() <= 0 || amount.compareTo(BigDecimal.valueOf(9007199254740991L)) > 0) continue;
                TaxTransactionDto transaction = new TaxTransactionDto();
                transaction.setUserId(userId);
                transaction.setTransactionDate(date);
                transaction.setTaxYear(taxYear);
                transaction.setTransactionType(income ? "INCOME" : "EXPENSE");
                transaction.setCategory(category);
                transaction.setDescription(memo);
                transaction.setAmount(amount);
                result.add(transaction);
            } catch (IllegalArgumentException | java.time.DateTimeException ignored) {
                // 잘못된 날짜·금액은 추정하지 않고 제외한다.
            }
        }
        return result;
    }

    public int countSkipped(String text, int parsedCount) {
        long candidates = text.lines().filter(line -> DATE.matcher(line).find() || AMOUNT.matcher(line).find()).count();
        return (int) Math.max(0, candidates - parsedCount);
    }


    // 날짜나 주민번호의 숫자를 연도·금액으로 간주하지 않고 명시된 표제만 읽는다.
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})\\s*(?:년도|년)\\s*(?:귀속|연말정산)");
    private static final Pattern YEAR_LABEL = Pattern.compile("귀속\\s*연도\\s*[:：]?\\s*((?:19|20)\\d{2})(?!\\d)");
    private static final Pattern TOTAL = Pattern.compile("^(합계|총계|총\\s*사용금액|총\\s*의료비)(?:\\s*[:：]\\s*|\\s+)(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원?\\s*$");

    public String detectEvidenceType(String text) {
        String normalized = text.replaceAll("\\s+", "");
        // 종류 표시는 참고 정보이며 업로드 허용 여부에는 사용하지 않는다.
        // 안내문에 등장하는 다른 카드 종류보다 명시된 문서 구분을 우선한다.
        var heading = Pattern.compile("[\\[［]\\s*(신용카드|직불카드\\s*등|체크카드|의료비)\\s*[\\]］]").matcher(normalized);
        Set<String> markedTypes = new HashSet<>();
        while (heading.find()) {
            String name = heading.group(1).replaceAll("\\s+", "");
            markedTypes.add(name.equals("의료비") ? "MEDICAL" : name.equals("신용카드") ? "CREDIT_CARD" : "DEBIT_CARD");
        }
        if (markedTypes.size() == 1) return markedTypes.iterator().next();
        if (markedTypes.size() > 1) return "UNKNOWN";
        boolean medical = normalized.contains("의료비");
        boolean debit = normalized.contains("직불카드") || normalized.contains("체크카드");
        boolean credit = normalized.contains("신용카드");
        // 한 문서에 여러 종류가 섞이면 하나의 종류로 임의 확정하지 않는다.
        int count = (medical ? 1 : 0) + (debit ? 1 : 0) + (credit ? 1 : 0);
        if (count != 1) return "UNKNOWN";
        return medical ? "MEDICAL" : debit ? "DEBIT_CARD" : "CREDIT_CARD";
    }

    public boolean looksLikeEvidence(String text) {
        String normalized = text.replaceAll("\\s+", "");
        return normalized.contains("연말정산") || normalized.contains("소득·세액공제")
                || normalized.contains("소득ㆍ세액공제") || normalized.contains("소득공제증명")
                || normalized.contains("의료비납입") || normalized.contains("사용금액확인서")
                || normalized.contains("신용카드등사용") || normalized.contains("직불카드등사용");
    }

    public Map<String, Object> parseEvidence(List<PdfPageText> pages, int requestedYear) {
        String text = String.join("\n", pages.stream().map(PdfPageText::text).toList());
        String type = detectEvidenceType(text);
        Set<Integer> years = new HashSet<>();
        for (Pattern pattern : List.of(YEAR, YEAR_LABEL)) {
            var matcher = pattern.matcher(text);
            while (matcher.find()) years.add(Integer.parseInt(matcher.group(1)));
        }
        Integer documentYear = years.size() == 1 ? years.iterator().next() : null;
        List<Map<String, Object>> items = new ArrayList<>();
        if ("MEDICAL".equals(type)) items.addAll(parseMedicalEvidence(pages));
        // 제목이 없어도 명확한 집계 열 구조를 읽을 수 있다.
        if (items.isEmpty()) {
            items.addAll(parseCardSummary(pages));
            items.addAll(parseCardMonthly(pages, documentYear));
        }
        // 복잡한 표는 추측하지 않는다. 명시된 단일 합계 행만 확인 대기 후보로 반환한다.
        if (items.isEmpty()) {
            for (PdfPageText page : pages) {
                for (String line : page.text().split("\\R")) {
                    var match = TOTAL.matcher(line.trim());
                    if (!match.matches()) continue;
                    BigDecimal amount = new BigDecimal(match.group(2).replace(",", ""));
                    if (amount.compareTo(BigDecimal.valueOf(9007199254740991L)) <= 0) {
                        items.add(buildReviewItem("증빙 기재 합계(확인 필요)", amount, page.page(), false));
                    }
                }
            }
        }
        if (items.isEmpty()) items.addAll(parseReviewCandidates(pages, requestedYear));
        // 완전히 읽힌 거래도 편집 가능한 후보로 전달해 내용·카테고리 등을 자동으로 채운다.
        for (PdfPageText page : pages) {
            for (TaxTransactionDto row : parseTransactions(page.text(), null, requestedYear)) {
                var item = buildReviewItem(row.getDescription(), row.getAmount(), page.page(), false,
                        row.getTransactionDate().toString(), "DETAIL", null, row.getTransactionType());
                item.put("description", row.getDescription());
                item.put("category", row.getCategory());
                items.add(item);
            }
        }
        List<String> notices = new ArrayList<>();
        notices.add("증빙 기재금액은 사업 경비나 공제액으로 자동 반영하지 않습니다.");
        if (documentYear == null) notices.add("귀속연도를 확정하지 못했습니다. 원본에서 확인해 주세요.");
        if (items.stream().anyMatch(item -> "VALIDATION_TOTAL".equals(item.get("valueKind")))) {
            notices.add("카드 집계의 네 항목 합계와 전체 사용금액이 일치하는지 검증했습니다. 전체 합계는 같은 문서의 다른 금액과 중복 합산하지 마세요.");
            notices.add("카드 사용금액은 사업용 지출로 확정된 값이 아닙니다. 원본과 용도를 확인해 필요한 금액만 추가하세요.");
        }
        if (items.stream().anyMatch(item -> "MONTHLY_SUMMARY".equals(item.get("valueKind")))) {
            notices.add("월별 금액은 각 발급기관 행의 12개월 합계와 기재 합계가 일치한 경우만 표시합니다. 실제 거래일은 제공되지 않으며 연간 집계와 중복 합산하지 않습니다.");
        }
        if ("MEDICAL".equals(type) && !items.isEmpty()) {
            notices.add("일별 지출과 인별합계를 구분해 표시합니다. 상세 금액과 합계를 자동 합산하지 마세요.");
            notices.add("반복 표기된 전체 합계와 기관별 소계는 추가 금액으로 합산하지 않습니다.");
        } else if (items.size() > 1) notices.add("합계 후보가 여러 개입니다. 소계·중복 여부를 확인하고 자동 합산하지 마세요.");
        String status = items.isEmpty() ? "UNSUPPORTED_FORMAT" : "NEEDS_REVIEW";
        if (items.isEmpty()) notices.add("날짜·금액 또는 항목명·금액의 구조를 찾지 못했습니다. 원본을 확인하고 직접 입력해 주세요.");
        if (documentYear != null && documentYear != requestedYear) {
            status = "YEAR_MISMATCH";
            notices.add("선택한 귀속연도와 PDF에 명시된 연도가 다릅니다.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("evidenceType", type);
        result.put("documentYear", documentYear);
        result.put("requestedTaxYear", requestedYear);
        result.put("status", status);
        result.put("items", List.copyOf(items));
        result.put("notices", List.copyOf(notices));
        return result;
    }

    private List<Map<String, Object>> parseCardMonthly(List<PdfPageText> pages, Integer documentYear) {
        if (documentYear == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        String number = "(?:\\d{1,3}(?:,\\d{3})+|\\d+)";
        Pattern four = Pattern.compile("^" + number + "(?:\\s+" + number + "){3}$");
        Pattern middle = Pattern.compile("(?:일반|전통시장|대중교통|문화체육)\\s+(" + number + "(?:\\s+" + number + "){4})$");
        for (PdfPageText page : pages) {
            String compact = page.text().replaceAll("\\s+", "");
            // 12개월 헤더가 모두 명시된 표만 읽고 임의의 거래 날짜는 만들지 않는다.
            if (!compact.contains("월별공제대상금액")) continue;
            boolean complete = true;
            for (int month = 1; month <= 12; month++) {
                if (!Pattern.compile("(?<!\\d)0?" + month + "월").matcher(page.text()).find()) complete = false;
            }
            if (!complete) continue;
            String[] lines = page.text().split("\\R");
            for (int i = 1; i < lines.length - 1; i++) {
                var match = middle.matcher(lines[i].trim());
                if (!match.find()) continue;
                String before = null, after = null;
                // 발급기관 이름이 월별 숫자 행 사이에 인쇄될 수 있어 가까운 행만 확인한다.
                for (int j = i - 1; j >= Math.max(0, i - 3); j--) {
                    if (four.matcher(lines[j].trim()).matches()) { before = lines[j].trim(); break; }
                    if (middle.matcher(lines[j].trim()).find()) break;
                }
                for (int j = i + 1; j <= Math.min(lines.length - 1, i + 3); j++) {
                    if (four.matcher(lines[j].trim()).matches()) { after = lines[j].trim(); break; }
                    if (middle.matcher(lines[j].trim()).find()) break;
                }
                if (before == null || after == null) continue;
                String[] center = match.group(1).split("\\s+");
                List<String> values = new ArrayList<>(Arrays.asList(before.split("\\s+")));
                values.addAll(Arrays.asList(center).subList(0, 4));
                values.addAll(Arrays.asList(after.split("\\s+")));
                List<BigDecimal> amounts = values.stream().map(this::medicalAmount).toList();
                BigDecimal total = medicalAmount(center[4]);
                if (total == null || amounts.stream().anyMatch(Objects::isNull)
                        || amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(total) != 0) continue;
                String category = lines[i].substring(match.start(), match.start(1)).trim();
                for (int month = 1; month <= 12; month++) {
                    result.add(buildReviewItem(category + " 월별 사용금액", amounts.get(month - 1), page.page(), false,
                            null, "MONTHLY_SUMMARY", String.format(Locale.ROOT, "%04d-%02d", documentYear, month)));
                }
            }
        }
        return result;
    }

    private List<Map<String, Object>> parseCardSummary(List<PdfPageText> pages) {
        List<Map<String, Object>> result = new ArrayList<>();
        String[] labels = {"일반 사용금액", "전통시장 사용금액", "대중교통 사용금액", "문화체육 사용금액", "전체 사용금액 합계(검증용)"};
        Pattern values = Pattern.compile("^(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\s*원)?(?:\\s+(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\s*원)?){4}$");
        for (PdfPageText page : pages) {
            String[] lines = page.text().split("\\R");
            for (int i = 0; i < lines.length; i++) {
                String header = lines[i].replaceAll("\\s+", "");
                if (!header.equals("일반전통시장대중교통문화체육합계금액")) continue;
                // 헤더 바로 아래의 5개 금액만 읽는다. 다른 표·안내문의 숫자로 보충하지 않는다.
                int next = i + 1;
                while (next < lines.length && lines[next].isBlank()) next++;
                if (next >= lines.length || !values.matcher(lines[next].trim()).matches()) continue;
                String[] cells = lines[next].trim().replaceAll("\\s*원", "").split("\\s+");
                if (cells.length != 5) continue;
                List<BigDecimal> amounts = Arrays.stream(cells).map(this::medicalAmount).toList();
                if (amounts.stream().anyMatch(Objects::isNull)) continue;
                BigDecimal sum = amounts.subList(0, 4).stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                if (sum.compareTo(amounts.get(4)) != 0) continue;
                for (int col = 0; col < 5; col++) {
                    result.add(buildReviewItem(labels[col], amounts.get(col), page.page(), false, null,
                            col == 4 ? "VALIDATION_TOTAL" : "CATEGORY_SUMMARY"));
                }
            }
        }
        return result;
    }

    // 제목·업종과 무관하게 명시된 금액만 후보로 읽는다. 날짜/식별번호만 있는 줄은 제외한다.
    private List<Map<String, Object>> parseReviewCandidates(List<PdfPageText> pages, int requestedYear) {
        List<Map<String, Object>> result = new ArrayList<>();
        Pattern labeled = Pattern.compile("^(.{0,80}?(?:금액|대금|보증금|수수료|합계|총계))\\s*[:：]?\\s+(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원?\\s*$");
        for (PdfPageText page : pages) {
            for (String raw : page.text().split("\\R")) {
                String line = raw.trim();
                // 이미 자동 반영한 명확한 거래는 검토 후보로 중복 반환하지 않는다.
                if (!parseTransactions(line, null, requestedYear).isEmpty()) continue;
                var date = DATE.matcher(line);
                var amount = AMOUNT.matcher(line);
                var label = labeled.matcher(line);
                if (date.find() && amount.find() && amount.start(1) > date.end()) {
                    try {
                        LocalDate day = LocalDate.of(Integer.parseInt(date.group(1)), Integer.parseInt(date.group(2)), Integer.parseInt(date.group(3)));
                        BigDecimal value = medicalAmount(amount.group(1));
                        if (value != null) {
                            var item = buildReviewItem("거래 금액(용도 확인 필요)", value, page.page(), false, day.toString(), "DETAIL");
                            // 원문에서 읽은 내용·구분·카테고리만 전달하고 없는 값은 채우지 않는다.
                            var kind = KIND.matcher(line);
                            String category = java.util.stream.Stream.concat(INCOME_CATEGORIES.stream(), EXPENSE_CATEGORIES.stream())
                                    .filter(line::contains).findFirst().orElse("");
                            String description = line.substring(0, amount.start(1)).replace(date.group(), "").trim();
                            if (kind.find()) {
                                item.put("transactionType", Set.of("수입", "입금").contains(kind.group(1)) ? "INCOME" : "EXPENSE");
                                description = description.replace(kind.group(), "").trim();
                            }
                            if (!category.isEmpty()) description = description.replace(category, "").trim();
                            // 식별번호·연락처가 있는 행은 내용을 복사하지 않는다.
                            if (!description.matches(".*(?:주민|계좌|전화|연락처|등록번호).*")) item.put("description", description);
                            item.put("category", category);
                            result.add(item);
                        }
                    } catch (java.time.DateTimeException ignored) { }
                } else if (label.matches()) {
                    BigDecimal value = medicalAmount(label.group(2));
                    // 원문 개인정보는 라벨에 복사하지 않는다.
                    if (value != null) result.add(buildReviewItem("기재 금액(용도 확인 필요)", value, page.page(), false));
                }
            }
        }
        return result;
    }

    private List<Map<String, Object>> parseMedicalEvidence(List<PdfPageText> pages) {
        Pattern detail = Pattern.compile(DATE.pattern() + "\\s+(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*$");
        Pattern summary = Pattern.compile("^(의료비|안경구입비|산후조리원)\\s*인별합계금액\\s+(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*$");
        Pattern repeatedTotal = Pattern.compile("^인별합계금액\\s+(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*$");
        List<Map<String, Object>> details = new ArrayList<>();
        List<Map<String, Object>> summaries = new ArrayList<>();
        List<Map<String, Object>> fallbackTotals = new ArrayList<>();
        Set<String> seenSummaries = new HashSet<>();
        for (PdfPageText page : pages) {
            boolean inMedicalTable = false;
            for (String raw : page.text().split("\\R")) {
                String line = raw.trim();
                String compact = line.replaceAll("\\s+", "");
                if (compact.contains("의료비지출내역")) { inMedicalTable = true; continue; }
                // 표 아래 세법 안내·인적사항의 숫자는 금액 후보로 읽지 않는다.
                if (compact.matches("^[12]\\.공제.*") || compact.startsWith("일련번호")) inMedicalTable = false;
                if (!inMedicalTable) continue;
                var detailMatch = detail.matcher(line);
                var summaryMatch = summary.matcher(line);
                var totalMatch = repeatedTotal.matcher(line);
                if (detailMatch.find()) {
                    try {
                        LocalDate date = LocalDate.of(Integer.parseInt(detailMatch.group(1)), Integer.parseInt(detailMatch.group(2)), Integer.parseInt(detailMatch.group(3)));
                        BigDecimal amount = medicalAmount(detailMatch.group(4));
                        // 원본의 의료비 지출 내역임이 명확하므로 구분을 함께 반환한다. 경비 인정 여부는 별도 확인한다.
                        if (amount != null) details.add(buildReviewItem("의료비 일별 지출", amount, page.page(), false, date.toString(), "DETAIL", null, "EXPENSE"));
                    } catch (java.time.DateTimeException ignored) {
                        // 잘못 인식한 날짜는 추측해서 고치지 않는다.
                    }
                } else if (summaryMatch.matches()) {
                    BigDecimal amount = medicalAmount(summaryMatch.group(2));
                    String key = summaryMatch.group(1) + ":" + amount;
                    if (amount != null && seenSummaries.add(key)) {
                        summaries.add(buildReviewItem(summaryMatch.group(1) + " 인별합계금액", amount, page.page(), false));
                    }
                } else if (totalMatch.matches()) {
                    BigDecimal amount = medicalAmount(totalMatch.group(1));
                    if (amount != null && seenSummaries.add("전체:" + amount)) fallbackTotals.add(buildReviewItem("의료비 전체 인별합계금액", amount, page.page(), false));
                }
            }
        }
        // 항목별 합계가 있으면 반복된 전체 합계는 중복 후보로 추가하지 않는다.
        details.addAll(summaries.isEmpty() ? fallbackTotals : summaries);
        return details;
    }

    private BigDecimal medicalAmount(String value) {
        BigDecimal amount = new BigDecimal(value.replace(",", ""));
        return amount.signum() >= 0 && amount.compareTo(BigDecimal.valueOf(9007199254740991L)) <= 0 ? amount : null;
    }

    void validatePdfBytes(byte[] bytes) {
        if (bytes.length > MAX_PDF_BYTES) throw securityError("PDF_SIZE_LIMIT", "PDF는 파일당 6MB 이하만 처리할 수 있습니다.");
        String header = new String(bytes, 0, Math.min(bytes.length, 8), StandardCharsets.US_ASCII);
        String tail = new String(bytes, Math.max(0, bytes.length - 1024), Math.min(bytes.length, 1024), StandardCharsets.ISO_8859_1);
        if (!(header.startsWith("%PDF-1.") || header.startsWith("%PDF-2.")) || !tail.contains("%%EOF")) {
            throw securityError("INVALID_PDF_SIGNATURE", "실제 PDF 형식이 아니거나 파일이 손상되었습니다.");
        }
    }

    void rejectActiveContent(PDDocument document) {
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

    private ApiException securityError(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }


}
