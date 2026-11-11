package com.kh.back.taxupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxTransactionDto {

    private Long transactionId;
    private Long taxInputId;

    private String transactionType;
    private LocalDate transactionDate;
    private String description;
    private String category;

    private BigDecimal preTaxIncome;
    @JsonDeserialize(using = PlainDecimalDeserializer.class)
    private BigDecimal deductedTaxRate;
    @JsonDeserialize(using = PlainDecimalDeserializer.class)
    private BigDecimal amount;

    // PDF 분석 결과 전달용이며 거래 테이블에는 저장하지 않는다.
    private BigDecimal taxDeducted;
    @JsonDeserialize(using = PlainDecimalDeserializer.class)
    private BigDecimal actualAmount;

    private Integer pageNumber;
    private String rawText;

    private Boolean parseSuccess;
    private Boolean excluded;
    private String failureReason;

    private String transactionMonth;
    private Integer documentYear;

    private String valueKind;
    private String amountBasis;
    private Boolean needsReview;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxInputRequest {

        // 식별자와 소유자는 서비스에서 결정한다.
        private Long taxInputId;
        private Long userId;

        private Integer taxYear;

        private Boolean businessRegistered;

        @JsonDeserialize(using = UnsignedIntegerDeserializer.class)
        private Integer dependentCount;
        @JsonDeserialize(using = PlainDecimalDeserializer.class)
        private BigDecimal generalDonationAmount; // 종교단체 외 일반기부금 입력액
        @JsonDeserialize(using = PlainDecimalDeserializer.class)
        private BigDecimal specialDonationAmount; // 영수증상 특례기부금 입력액
        private String saveStatus;
    }

    /*
     * 서비스에서 로그인 정보로 생성한다.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessContext {

        private Long userId;
        private boolean admin;
    }

    /*
     * 통합 서비스 호출용 요청이다.
     * Controller가 작업 대상과 필요한 값을 builder로 구성한다.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxServiceRequest {

        // INPUT / SAVED_INPUTS / TRANSACTIONS / RESULT / CATEGORY / TRANSACTION
        private String target;

        private Long taxInputId;
        private Long transactionId;
        private java.util.List<Long> transactionIds;
        private Integer taxYear;

        private TaxInputRequest input;
        private List<TaxCategoryChange> changes;
        // 최초 저장할 신규 거래 목록
        private Integer expectedTransactionCount;
        private List<TaxTransactionDto> transactions;
    }

    private static final Pattern PLAIN_DECIMAL = Pattern.compile("[0-9]+(?:[.][0-9]+)?");

    public static class UnsignedIntegerDeserializer extends ValueDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT || parser.getText().startsWith("-")) {
                return context.reportInputMismatch(this, "부호 없는 정수를 입력해주세요.");
            }
            return parser.getIntValue();
        }
    }

    public static class PlainDecimalDeserializer extends ValueDeserializer<BigDecimal> {
        @Override
        public BigDecimal deserialize(JsonParser parser, DeserializationContext context) {
            // 숫자로 변환하기 전에 문자, 음수, 지수 표기를 거부한다.
            if (!parser.currentToken().isNumeric() || !PLAIN_DECIMAL.matcher(parser.getText()).matches()) {
                return context.reportInputMismatch(this, "지수 표기 없이 0 이상의 숫자를 입력해주세요.");
            }
            return parser.getDecimalValue();
        }
    }
}
