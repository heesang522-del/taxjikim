package com.kh.back.tax;

import com.kh.back.tax.dto.TaxBaseResultDto;
import com.kh.back.tax.dto.TaxCalculationInputDto;
import com.kh.back.tax.service.TaxCalculationService;
import com.kh.back.tax.service.TaxCalculationServiceImpl;

/**
 * 화면·DB·TaxUpload에 연결하지 않은 예시 실행용 코드.
 * 입력·결과는 dto, 실제 계산은 service 패키지에서 관리한다.
 */
public class TaxCalculatorDraft {

    public static void main(String[] args) {
        // 예시값: 본인·적격 가족 각 1명의 공제를 포함한 총액을 한 번만 적용한다.
        TaxCalculationInputDto example = new TaxCalculationInputDto(50_000_000, 20_000_000, 0, 3_000_000);
        // main에서만 직접 생성한다. 서버 연결 시 호출자는 서비스 인터페이스를 주입받는다.
        TaxCalculationService service = new TaxCalculationServiceImpl();
        TaxBaseResultDto result = service.calculateTaxBase(example);
        System.out.printf("예시 과세표준: %,d원%n", result.taxableIncome());
        System.out.println("최종세액 계산 전 중간 결과입니다.");
    }
}
