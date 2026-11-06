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

        SimpleClientHttpRequestFactory factory =  new SimpleClientHttpRequestFactory(); // python 서버와 http 통신할떄 사용할 연결처리 객체 생성

        factory.setConnectTimeout(connectTimeout); // python 서버에 요청할 http 클라이언트 를 만들고 서버 주소와 대기 시간을 설정하는 부분
        factory.setReadTimeout(readTimeout); // 연결후 응답 데이터를 읽을떄 기다리는 시간 제한을 설정

        this.client = RestClient.builder() // restclient 생성 시작
                .baseUrl(baseUrl) // 요청할 기본 서버 주소 지정
                .requestFactory(factory) // 앞에서 설정한 연결 처리 방식과 대기시간 제한 적용
                .build(); // 설정한 내용으로 클라이언트 객체 완성

        this.objectMapper = createObjectMapper();
    }  // json 변한 규칙이 설정된 객체를 만들어 클래스 필드에ㅔ 저장하는 부분

    private ObjectMapper createObjectMapper() { // json 의 날짜 값을 java의 localdate 로 바꾸는 규칙을 등록하기 시작
        SimpleModule dateModule = new SimpleModule(); // 사용자 정의 변환 규칙을 담을 모듈을 만들고 datemodule 에 저장

        dateModule.addDeserializer( // java 필드 타입이 localdate 라면 아래 변환 규칙을 사용
                LocalDate.class,
                new JsonDeserializer<LocalDate>() { // 날짜 변환을 담당하는 객체를 익명 클래스로 만듬 , 별도 클래스 파일을 만들지 않고, 이어지는 { } 안에 deserialize() 메서드 구현

                    @Override
                    public LocalDate deserialize( // json 날짜 값을 변환하기 전에, 값이 문자열인지 검사하는 부분
                            JsonParser parser,
                            DeserializationContext context
                    ) throws IOException {
                        if (!parser.hasToken(JsonToken.VALUE_STRING)) { // 현재값이 문자열이 아니면 조건이 참임
                            throw JsonMappingException.from( // 문자열이 아니면 json 변환 오류를 발생시킴 , 날짜 형식과 실제 존재하는 날짜인지는 뒤의 localDate.parse() 에서 검사
                                    parser,
                                    "거래 날짜는 YYYY-MM-DD 문자열 또는 null이어야 합니다."
                            );
                        }

                        String value = parser.getText().trim();  // 현재 json 문자열을 가져와 앞뒤 공백을 제거한뒤 value 에 저장하는 코드

                        try {
                            return LocalDate.parse(value); // 문자열을 날짜 객체로 변환하고 성공하면 반환하는 코드

                        } catch (DateTimeParseException exception) { // 문자열을 날짜로 변환하지 못했을떄 json 변환 오류로 바꿔 전달하는 부분
                            throw JsonMappingException.from(
                                    parser,
                                    "거래 날짜의 형식이 올바르지 않습니다."
                            );
                        }
                    }
                }
        );

        ObjectMapper mapper = new ObjectMapper(); // json 과 java 객체를 서로 변환하는 objectmapper 객체를 만들어 mapper 변수에 저장하는 부분

        mapper.registerModule(dateModule); // 앞에서 만든 날짜 변환 규칙을 objectmapper에 등록


        mapper.disable(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
        ); // json 에 java dto에 없는 필드가있어도 오류를 내지 않고 무시하도록 설정하는 부분

        mapper.enable(
                DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS
        ); // json의 소수를 읽을떄 타입이 정해지지 않은 값은 double 대신 bigdecimal 로 변환하도록 설정

        mapper.enable(
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS
        ); // 정상 json 값뒤에 다른 값이 추가로 붙어 있으면 변환 오류 발생시키는 설정

        return mapper;
    } // mapper 로 반환

    @Override
    public PdfParseResponseDto analyze( // pdf 원본과 ocr 결과를 python 서버에 보내 분석 결과를 받는 메서드 선언
            byte[] pdfBytes, // pdf 파일 원본을 바이트 배열로 읽은 데이터
            String fileName, // 전송할 pdf 파일 이름
            List<PageOcrResultDto> pages // java에서 추출한 페이지 별 텍스트 , ocr 결과 목록
    ) {

        if (fileName == null || fileName.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "분석할 PDF 데이터와 파일명이 필요합니다."
            );
        } // 파일이름이 비거나 없으면 예외처리

        MultiValueMap<String, Object> parts = createParts(
                pdfBytes,
                fileName,
                pages
        ); // python 서버에 보낼 pdf 파일과 ocr 결과를 요청 데이터로 묶는 부분 , 변환된 데이터를 parts에 저장

        String responseBody; // 응답 본문을 저장할 문자열 변수를 선언

        try {
            responseBody = client.post() // http post 요청 준비
                    .uri("/api/pdf/analyze") // 분석 api 경로를 지정, 앞서 설정한 baseurl 뒤에 붙음
                    .contentType(MediaType.MULTIPART_FORM_DATA) // 보내는 데이터가 파일과 일반 데이터를 함께 담는 multipart 형식임을 지정
                    .accept(MediaType.APPLICATION_JSON) // 서버에 json 형식의 응답을 받고 싶다고 알림
                    .body(parts) // 앞서 만든 pdf 파일과 ocr 결과를 요청 본문에 넣음
                    .retrieve() // 요청을 실행해 응답을 일고, 응답본문을 문자열로 받아 responseBody 에 저장 , 이후 이 json 문자열을 java dto로 변환
                    .body(String.class);

        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();

            log.warn(
                    "Python PDF 서버 HTTP 오류. status={}",
                    status
            ); // python 서버가 http 오류 응답을 보냈을떄 상태 코드를 확인하고 로그에 기록하는 부분

            if (status == 400 || status == 422) {
                String code = null; // python 서버의 http 상태가 400 또는 422 이면 아래코드를 실행

                try {
                    JsonNode root = objectMapper.readTree(
                            exception.getResponseBodyAsString()
                    ); // python 서버가 보낸 오류 응답을 문자열로 꺼내 , json 구조로 읽는 부분

                    if (root != null) { // python 오류 응답에서 detail.code를 꺼내고, json을 읽지 못하면 공통 오류 처리로 넘어가는 부분
                        code = root.path("detail")
                                .path("code")
                                .asText(null);
                    }

                } catch (JsonProcessingException ignored) {
                  // 오류 응답이 올바른지 json이 아니어서 해석에 실패하면 여기서 잡고 계속 진행,
                    // ignored 는 이 예외 객체를 사용하지 않겠다는 의도를 나타낸 변수이름
                }

                if ("PDF_DAILY_DETAILS_REQUIRED".equals(code)) { // python 에서 받은 오류 코드가 pdf_dailt_details_required 인지 확인, 문자열 상수를 앞에 두어 code가 null 이어도 오류 없이 비교
                    throw new ApiException( // 공통 예외처리
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            code,
                            "날짜별 상세 내역을 업로드해주세요. "
                                    + "월별·지출처별 합계는 일별 거래로 저장하지 않습니다."
                    );
                }

                if ("PDF_OCR_REQUIRED".equals(code)) { // python 에서 받은 오류 코드가 pdf_ocr_required 인지 확인
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

    private PdfParseResponseDto parseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PDF_EMPTY_RESPONSE",
                    "PDF 분석 서버가 빈 응답을 반환했습니다."
            );
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);

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

            if (response.getTransactions()
                    .stream()
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

            return response;

        } catch (JsonMappingException exception) {
            // 거래 원문 대신 필드 경로와 예외 종류만 기록한다.
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

    private ApiException invalidResponse(String reason) {
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

    private String mappingPath(JsonMappingException exception) {
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
        ByteArrayResource resource = new ByteArrayResource(pdfBytes) {

            @Override
            public String getFilename() {
                return fileName;
            }
        };

        MultiValueMap<String, Object> parts =
                new LinkedMultiValueMap<>();

        parts.add("file", resource);

        // 기존 동작과 동일하게 실제 OCR을 수행한 페이지만 전송한다.
        List<PageOcrResultDto> ocrPages = pages == null
                ? List.of()
                : pages.stream()
                .filter(Objects::nonNull)
                .filter(PageOcrResultDto::isOcrUsed)
                .toList();

        try {
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

    private boolean hasTimeoutCause(Throwable exception) {
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