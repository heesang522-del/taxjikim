package com.kh.back.taxupload.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import com.kh.back.common.exception.ApiException;
import com.kh.back.taxupload.dto.PageOcrResultDto;
import com.kh.back.taxupload.dto.PdfParseResponseDto;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class PythonPdfClientImpl implements PythonPdfClient {

    private final RestClient client;
    private final ObjectMapper objectMapper;

    public PythonPdfClientImpl(
            @Value("${python.api-url:http://127.0.0.1:8000}")
            String baseUrl,

            @Value("${python.connect-timeout-ms:5000}")
            int connectTimeout,

            @Value("${python.read-timeout-ms:90000}")
            int readTimeout
    ) {
        if (connectTimeout <= 0 || readTimeout <= 0) {
            throw new IllegalArgumentException(
                    "Python 통신 제한 시간은 양수여야 합니다."
            );
        }

        SimpleClientHttpRequestFactory factory =  // 외부 서버에 요청을 보낼 http 클라이언트를 만들고 , 연결 응답 대기 시간을 설정하는 부분
                new SimpleClientHttpRequestFactory(); // http 연결을 실제로 처리할 객체 생성

        factory.setConnectTimeout(connectTimeout); // 외부 서버에 연결될 때까지 기다리는 최대시간 설정
        factory.setReadTimeout(readTimeout); // 연결후 응답 데이터를 읽으며 기다리는 최대 시간 설정, 전체 요청의 총 제한 시간과는 다름

        this.client = RestClient.builder()  // 설정을 조합하여 RestClient 를  생성하고, 현재 객체의 client 필드에 저장
                .baseUrl(baseUrl) // 요청에 사용할 기본 서버 주소 지정
                .requestFactory(factory) // 위에서 설정한 연결 방식과 대기 시간 적용
                .build(); // 설정을 적용한 클라이언트 생성

        this.objectMapper = createObjectMapper(); // createObjectMapper()가 반환하는 객체를 objectmapper 필드에 저장
    }

    private ObjectMapper createObjectMapper() { // python 서버가 보낸 json을 java 객체로 바꿀떄 적용할 규칙을 설정하는 함수
        SimpleModule dateModule = new SimpleModule(); // SimpleModule 은 ObjectMapper 에 추가할 사용자 정의 변환 규칙을 담는 객체

        /*
         * Python이 반환하는 YYYY-MM-DD 문자열을 LocalDate로 변환한다.
         * 날짜가 없는 월별 자료의 null은 그대로 유지한다.
         */
        dateModule.addDeserializer( // java 필드가 LOcalDate 타입이면 아래의 deserialize ()를 사용하도록 등록
                LocalDate.class,
                new JsonDeserializer<LocalDate>() {

                    @Override
                    public LocalDate deserialize(
                            JsonParser parser,
                            DeserializationContext context
                    ) throws IOException {

                        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                            throw JsonMappingException.from(
                                    parser,
                                    "거래 날짜는 YYYY-MM-DD 문자열 또는 null이어야 합니다."
                            );
                        } // 만약 날짜 값이 문자열인지 검사, 숫자나 배열등이 오면 오류 발생시킴

                        String value = parser.getText().trim(); // 문자열의 앞뒤 공백을 제거하고 날짜로 변환시킴

                        try {
                            return LocalDate.parse(value);

                        } catch (DateTimeParseException exception) { // 날짜 형식이 잘못됬거나 실제로 존재하지 않으면 json 변환 오류로 전달
                            throw JsonMappingException.from(
                                    parser,
                                    "거래 날짜의 형식이 올바르지 않습니다."
                            );
                        }
                    }
                }
        );

        ObjectMapper mapper = new ObjectMapper(); // json 변환기를 만들고 위에서 정의한 날짜 변환 규칙을 적용

        mapper.registerModule(dateModule); // python 응답에 java dto에는 없는 필드가 추가되어도 변환을 계속함
        //   ex : dto에 amount 만 있는데 다음 응답이 오면 extrainfo 는 무시

        /*
         * 분석 서버에 보조 필드가 추가되어도 기존 DTO 변환을 유지한다.
         * 필수 응답 구조는 parseResponse에서 별도로 확인한다.
         */
        mapper.disable(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
        ); // python 응답에 java dto에는 없는 필드가 추가되어도 변환을 계속함
        mapper.enable(
                DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS
        );   // // Map<String, Object> 처럼 숫자의 구체적인 java 타입이 정해지지 않은 경우 , 소수를 double 대신 BigDecimal로 읽음


        mapper.enable(
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS
        ); // 정상 json뒤에 다른 값이 이어지는 응답을 거부

        return mapper;
    }
    @Override
    public PdfParseResponseDto analyze(
            byte[] pdfBytes,
            String fileName,
            List<PageOcrResultDto> pages
    ) {
        if (pdfBytes == null
                || pdfBytes.length == 0
                || fileName == null
                || fileName.isBlank()) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "분석할 PDF 데이터와 파일명이 필요합니다."
            );
        }

        MultiValueMap<String, Object> parts =
                createParts(
                        pdfBytes,
                        fileName,
                        pages
                );

        String responseBody;

        try {
            responseBody = client.post()
                    .uri("/api/pdf/analyze")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(parts)
                    .retrieve()
                    .body(String.class);

        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();

            log.warn(
                    "Python PDF 서버 HTTP 오류. status={}",
                    status
            );

            if (status == 400 || status == 422) {
                String code = null;

                try {
                    JsonNode root = objectMapper.readTree(
                            exception.getResponseBodyAsString()
                    );

                    if (root != null) {
                        code = root.path("detail")
                                .path("code")
                                .asText(null);
                    }

                } catch (JsonProcessingException ignored) {
                    // 응답을 해석하지 못하면 아래 공통 오류로 처리한다.
                }

                if ("PDF_DAILY_DETAILS_REQUIRED".equals(code)) {
                    throw new ApiException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            code,
                            "날짜별 상세 내역을 업로드해주세요. "
                                    + "월별·지출처별 합계는 일별 거래로 저장하지 않습니다."
                    );
                }

                if ("PDF_OCR_REQUIRED".equals(code)) {
                    throw new ApiException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            code,
                            "이미지형 PDF의 OCR 결과가 전달되지 않았습니다. "
                                    + "서버의 OCR 처리와 전달 결과를 확인해주세요."
                    );
                }

                if ("PDF_TRANSACTIONS_NOT_FOUND".equals(code)) {
                    throw new ApiException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            code,
                            "날짜별 거래 내역을 찾지 못했습니다. "
                                    + "상세 내역 또는 OCR 인식 결과를 확인해주세요."
                    );
                }

                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "PDF_ANALYSIS_INPUT_INVALID",
                        "PDF 파일 또는 OCR 입력 형식을 확인해주세요."
                );
            }

            if (status == 413) {
                throw new ApiException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "PDF_TOO_LARGE",
                        "PDF 파일 크기가 허용 범위를 초과했습니다."
                );
            }

            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PDF_ANALYSIS_FAILED",
                    "PDF 분석 서버 처리에 실패했습니다."
            );

        } catch (ResourceAccessException exception) {
            boolean timeout = hasTimeoutCause(exception);

            log.warn(
                    "Python PDF 서버 통신 실패. timeout={}",
                    timeout
            );

            if (timeout) {
                throw new ApiException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "PDF_ANALYSIS_TIMEOUT",
                        "PDF 분석 서버의 응답 시간이 초과되었습니다."
                );
            }

            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PDF_ANALYSIS_UNAVAILABLE",
                    "PDF 분석 서버와 통신할 수 없습니다."
            );

        } catch (RestClientException exception) {
            log.error(
                    "Python PDF 응답 수신 실패. exceptionType={}",
                    exception.getClass().getName()
            );

            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PDF_RESPONSE_ERROR",
                    "PDF 분석 응답을 수신하지 못했습니다."
            );
        }

        return parseResponse(responseBody);
    }
    private PdfParseResponseDto parseResponse(
            String responseBody

    ) {
        if (responseBody == null
                || responseBody.isBlank()) {

            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PDF_EMPTY_RESPONSE",
                    "PDF 분석 서버가 빈 응답을 반환했습니다."
            );
        }

        try {
            JsonNode root =
                    objectMapper.readTree(responseBody);

            /*
             * 현재 Python API는 data로 감싸지 않은 객체를 반환한다.
             * 예상과 다른 구조를 빈 거래 목록으로 처리하지 않는다.
             */
            if (root == null || !root.isObject()) {
                throw invalidResponse(
                        "최상위 응답이 JSON 객체가 아닙니다."
                );
            }

            if (!root.path("transactions").isArray()) {
                throw invalidResponse(
                        "transactions 배열이 없습니다."
                );
            }

            for (String field : List.of(
                    "totalPages",
                    "extractedCount",
                    "parsedCount",
                    "failedCount"
            )) {
                JsonNode value = root.get(field);

                if (value == null
                        || !value.isIntegralNumber()
                        || !value.canConvertToInt()
                        || value.intValue() < 0) {

                    throw invalidResponse(
                            field + " 값이 올바른 정수가 아닙니다."
                    );
                }
            }

            PdfParseResponseDto response =
                    objectMapper.treeToValue(
                            root,
                            PdfParseResponseDto.class
                    );

            if (response.getTransactions().stream()
                    .anyMatch(Objects::isNull)) {

                throw invalidResponse(
                        "transactions 배열에 null 항목이 있습니다."
                );
            }

            log.info(
                    "Python PDF 응답 변환 완료. pages={}, transactions={}, parsed={}",
                    response.getTotalPages(),
                    response.getTransactions().size(),
                    response.getParsedCount()
            );

            /*
             * DB 저장 완료 여부는 이후 저장 서비스에서 설정한다.
             * Python 분석 응답만으로 저장 성공을 표시하지 않는다.
             */
            response.setPersisted(false);
            response.setSavedCount(0);
            response.setTaxInputIdsByYear(null);

            return response;

        } catch (JsonMappingException exception) {
            /*
             * 필드 경로와 예외 종류만 남긴다.
             * 예외 메시지에 포함될 수 있는 거래 값은 기록하지 않는다.
             */
            log.error(
                    "Python PDF DTO 변환 실패. exceptionType={}, fieldPath={}",
                    exception.getClass().getName(),
                    mappingPath(exception)
            );

            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PDF_RESPONSE_MAPPING_ERROR",
                    "PDF 분석 응답의 필드 형식을 확인해주세요."
            );

        } catch (JsonProcessingException exception) {
            log.error(
                    "Python PDF JSON 해석 실패. exceptionType={}",
                    exception.getClass().getName()
            );

            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PDF_RESPONSE_JSON_ERROR",
                    "PDF 분석 서버가 올바른 JSON을 반환하지 않았습니다."
            );
        }
    }

    private ApiException invalidResponse(
            String reason
    ) {
        // reason에는 코드에서 정한 구조 설명만 전달한다.
        log.error(
                "Python PDF 응답 구조 오류. reason={}",
                reason
        );

        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "PDF_RESPONSE_STRUCTURE_ERROR",
                "PDF 분석 응답 구조가 예상 형식과 다릅니다."
        );
    }

    private String mappingPath(
            JsonMappingException exception
    ) {
        StringBuilder path = new StringBuilder();

        for (JsonMappingException.Reference reference
                : exception.getPath()) {

            if (reference.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append(".");
                }

                path.append(reference.getFieldName());

            } else if (reference.getIndex() >= 0) {
                path.append("[")
                        .append(reference.getIndex())
                        .append("]");
            }
        }

        return path.isEmpty()
                ? "(경로 확인 불가)"
                : path.toString();
    }

    private MultiValueMap<String, Object> createParts(
            byte[] pdfBytes,
            String fileName,
            List<PageOcrResultDto> pages
    ) {
        ByteArrayResource resource =
                new ByteArrayResource(pdfBytes) {

                    @Override
                    public String getFilename() {
                        return fileName;
                    }
                };

        MultiValueMap<String, Object> parts =
                new LinkedMultiValueMap<>();

        parts.add("file", resource);

        List<PageOcrResultDto> ocrPages =
                pages == null
                        ? List.of()
                        : pages.stream()
                        .filter(Objects::nonNull)
                        .filter(PageOcrResultDto::isOcrUsed)
                        .toList();

        try {
            // OCR 결과는 기존 Python API의 Form 필드 이름으로 전달한다.
            parts.add(
                    "ocrPages",
                    objectMapper.writeValueAsString(ocrPages)
            );

        } catch (JsonProcessingException exception) {
            log.error(
                    "OCR 결과 직렬화 실패. exceptionType={}",
                    exception.getClass().getName()
            );

            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "OCR_SERIALIZATION_FAILED",
                    "OCR 결과를 전송할 수 없습니다."
            );
        }

        return parts;
    }

    private boolean hasTimeoutCause(
            Throwable exception
    ) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}